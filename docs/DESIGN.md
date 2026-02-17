# SpringBoot Passkey 実装例 - 設計書

## 1. アーキテクチャ

### 1.1 全体構成

```
┌─────────────┐     ┌──────────────────────────────────────────┐
│   Browser    │────▶│            Spring Boot App                │
│  (WebAuthn   │     │                                          │
│   API)       │     │  ┌──────────────────────────────────┐    │
│              │◀────│  │      Spring Security 6.5          │    │
│              │     │  │                                  │    │
│              │     │  │  ┌──────────┐  ┌──────────────┐  │    │
│              │     │  │  │FormLogin │  │ WebAuthn     │  │    │
│              │     │  │  │Filter    │  │ Filter       │  │    │
│              │     │  │  └────┬─────┘  └──────┬───────┘  │    │
│              │     │  │       └───────┬────────┘          │    │
│              │     │  │               ▼                   │    │
│              │     │  │  ┌────────────────────────┐      │    │
│              │     │  │  │ AuthenticationManager   │      │    │
│              │     │  │  └────────────────────────┘      │    │
│              │     │  └──────────────────────────────────┘    │
│              │     │              │                            │
│              │     │              ▼                            │
│              │     │  ┌───────────────────────────────────┐   │
│              │     │  │             RDBMS (H2)             │   │
│              │     │  │  users / authorities               │   │
│              │     │  │  public_key_credential_user_entity │   │
│              │     │  │  user_credentials                  │   │
│              │     │  └───────────────────────────────────┘   │
└─────────────┘     └──────────────────────────────────────────┘
```

### 1.2 依存ライブラリの関係

```
アプリケーションコード
  ├── spring-boot-starter-web
  ├── spring-boot-starter-security
  │     └── spring-security-web
  │           └── (内部で使用) webauthn4j-core  ← アプリから直接参照しない
  ├── spring-boot-starter-jdbc
  ├── spring-boot-starter-thymeleaf
  └── h2 (runtime)
```

Spring Security は WebAuthn のアテステーション・アサーション検証に `webauthn4j-core` を内部で使用する。Spring Security の自動構成により適切に組み込まれるため、アプリケーションコードから `webauthn4j-core` の API を直接呼び出すことはない。

---

## 2. プロジェクト構成

```
springboot-passkey-example/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── SPEC.md
├── DESIGN.md
└── src/
    ├── main/
    │   ├── java/com/example/passkey/
    │   │   ├── Application.java
    │   │   ├── config/
    │   │   │   └── SecurityConfig.java
    │   │   └── controller/
    │   │       └── HomeController.java
    │   └── resources/
    │       ├── application.yml
    │       ├── schema.sql
    │       ├── data.sql
    │       └── templates/
    │           └── home.html
    └── test/
        └── java/com/example/passkey/
            └── ApplicationTests.java
```

---

## 3. データベース設計

### 3.1 ER 図

```
users 1───* authorities

users 1···1 public_key_credential_user_entity 1───* user_credentials
            (name = username で論理関連)
```

### 3.2 DDL（schema.sql）

```sql
-- Spring Security 標準
CREATE TABLE users (
    username VARCHAR(50) NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled  BOOLEAN NOT NULL
);

CREATE TABLE authorities (
    username  VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users(username)
);
CREATE UNIQUE INDEX ix_auth_username ON authorities (username, authority);

-- WebAuthn
CREATE TABLE public_key_credential_user_entity (
    id           BLOB         NOT NULL PRIMARY KEY,
    name         VARCHAR(200) NOT NULL UNIQUE,
    display_name VARCHAR(200) NOT NULL
);

CREATE TABLE user_credentials (
    credential_id                BLOB         NOT NULL PRIMARY KEY,
    user_entity_id               BLOB         NOT NULL,
    public_key                   BLOB         NOT NULL,
    signature_count              BIGINT       NOT NULL DEFAULT 0,
    uv_initialized               BOOLEAN      NOT NULL DEFAULT FALSE,
    backup_eligible              BOOLEAN      NOT NULL DEFAULT FALSE,
    authenticator_transports     VARCHAR(256),
    public_key_algorithm         BIGINT       NOT NULL,
    attestation_object           BLOB,
    attestation_client_data_json BLOB,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(200) NOT NULL,
    CONSTRAINT fk_user_credentials FOREIGN KEY (user_entity_id)
        REFERENCES public_key_credential_user_entity(id)
);
```

### 3.3 初期データ（data.sql）

```sql
INSERT INTO users (username, password, enabled)
VALUES ('user', '{bcrypt}$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW', TRUE);

INSERT INTO authorities (username, authority)
VALUES ('user', 'ROLE_USER');
```

---

## 4. 実装設計

### 4.1 Application.java

```java
package com.example.passkey;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 4.2 SecurityConfig.java

```java
package com.example.passkey.config;

import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            .formLogin(Customizer.withDefaults())
            .webAuthn(webAuthn -> webAuthn
                .rpName("Passkey Demo")
                .rpId("localhost")
                .allowedOrigins("http://localhost:8080")
            )
            .build();
    }

    @Bean
    UserDetailsService userDetailsService(DataSource dataSource) {
        return new JdbcUserDetailsManager(dataSource);
    }

    @Bean
    JdbcPublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcOperations jdbc) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbc);
    }

    @Bean
    JdbcUserCredentialRepository userCredentialRepository(JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
```

**設計判断:**

- `webAuthn()` DSL に `rpId`, `rpName`, `allowedOrigins` の 3 パラメータを設定するだけで Passkey 認証が有効化される
- `JdbcPublicKeyCredentialUserEntityRepository` と `JdbcUserCredentialRepository` を Bean 登録することで、デフォルトの In-Memory 実装が JDBC 永続化に切り替わる
- `formLogin(Customizer.withDefaults())` によりフォームログインと Passkey のハイブリッド認証を実現
- `rpId` は `allowedOrigins` のホスト名部分と一致させる必要がある

### 4.3 HomeController.java

```java
package com.example.passkey.controller;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "home";
    }
}
```

### 4.4 home.html

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <meta charset="UTF-8">
    <title>Passkey Demo</title>
</head>
<body>
    <h1>Hello, <span th:text="${username}">user</span></h1>
    <ul>
        <li><a href="/webauthn/register">Register Passkey</a></li>
        <li>
            <form th:action="@{/logout}" method="post">
                <button type="submit">Logout</button>
            </form>
        </li>
    </ul>
</body>
</html>
```

### 4.5 application.yml

```yaml
spring:
  application:
    name: springboot-passkey-example
  datasource:
    url: jdbc:h2:mem:passkeydb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  h2:
    console:
      enabled: true
      path: /h2-console
  sql:
    init:
      mode: always
      schema-locations: classpath:schema.sql
      data-locations: classpath:data.sql

server:
  port: 8080
```

---

## 5. ビルド設定

### build.gradle.kts

```kotlin
plugins {
    java
    id("org.springframework.boot") version "3.4.3"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("com.webauthn4j:webauthn4j-core:0.29.7.RELEASE")

    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### settings.gradle.kts

```kotlin
rootProject.name = "springboot-passkey-example"
```

---

## 6. 動作確認手順

```bash
./gradlew bootRun
```

1. http://localhost:8080 → ログインページにリダイレクト
2. `user` / `password` でフォームログイン → ホーム画面
3. 「Register Passkey」→ ラベル入力 →「Register」→ 認証器操作 → 登録完了
4. ログアウト →「Sign in with a passkey」→ Passkey 選択 → 認証完了

H2 Console: http://localhost:8080/h2-console（JDBC URL: `jdbc:h2:mem:passkeydb`, User: `sa`）

---

## 7. 本番環境への考慮事項

- **HTTPS**: WebAuthn は localhost 以外で HTTPS 必須。`rpId` と `allowedOrigins` を本番ドメインに変更
- **DB**: H2 → PostgreSQL 等。BLOB → BYTEA、TIMESTAMP → TIMESTAMP WITH TIME ZONE に変更
- **セッション**: 分散環境では Spring Session + Redis 等を検討
- **複数 Passkey**: デバイス紛失時のため、1 ユーザーに複数登録を推奨
- **フォールバック**: Passkey が使えない場合のフォームログインを維持
