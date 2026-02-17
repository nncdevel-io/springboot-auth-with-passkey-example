# SpringBoot Passkey 実装例 - 設計書

## 1. アーキテクチャ

### 1.1 全体構成

```text
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

```text
アプリケーションコード
  ├── spring-boot-starter-web
  ├── spring-boot-starter-security
  │     └── spring-security-web
  │           └── (内部で使用) webauthn4j-core  ← アプリから直接参照しない
  ├── spring-boot-starter-jdbc
  ├── spring-boot-starter-thymeleaf
  ├── thymeleaf-extras-springsecurity6      ← 認証情報のテンプレート参照用
  └── h2 (runtime)
```

Spring Security は WebAuthn のアテステーション・アサーション検証に `webauthn4j-core` を内部で使用する。
Spring Security の自動構成により適切に組み込まれるため、アプリケーションコードから `webauthn4j-core` の
API を直接呼び出すことはない。

---

## 2. プロジェクト構成

```text
springboot-auth-with-passkey-example/
├── pom.xml
├── README.md
├── CLAUDE.md
├── .markdownlint-cli2.yaml
├── cspell.json
├── .github/workflows/ci.yml
└── src/
    ├── main/
    │   ├── java/io/nncdevel/example/auth/
    │   │   ├── Application.java
    │   │   ├── package-info.java
    │   │   ├── config/
    │   │   │   ├── SecurityConfig.java
    │   │   │   └── package-info.java
    │   │   ├── controller/
    │   │   │   ├── HomeController.java
    │   │   │   ├── ProfileController.java
    │   │   │   └── package-info.java
    │   │   └── repository/
    │   │       ├── JdbcCustomUserEntityRepository.java
    │   │       └── package-info.java
    │   └── resources/
    │       ├── application.properties
    │       ├── schema.sql
    │       ├── data.sql
    │       ├── static/css/style.css
    │       └── templates/
    │           ├── login.html
    │           ├── home.html
    │           ├── profile.html
    │           ├── error.html
    │           └── fragments/layout.html
    └── test/
        ├── java/io/nncdevel/example/auth/
        │   ├── ApplicationTests.java
        │   ├── package-info.java
        │   ├── config/
        │   │   └── SecurityConfigTests.java
        │   └── controller/
        │       └── HomeControllerTests.java
        └── resources/
            └── application.properties
```

---

## 3. データベース設計

### 3.1 ER 図

```text
users 1───* authorities

users 1···1 public_key_credential_user_entity 1───* user_credentials
            (name = username で論理関連)
```

テーブル間の外部キー制約は設定していない。

### 3.2 DDL（schema.sql）

```sql
-- Spring Security 標準
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled  BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS authorities (
    username  VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS ix_auth_username
    ON authorities (username, authority);

-- WebAuthn（独自テーブル名）
CREATE TABLE IF NOT EXISTS public_key_credential_user_entity (
    id           VARCHAR(1000) NOT NULL PRIMARY KEY,
    name         VARCHAR(200)  NOT NULL UNIQUE,
    display_name VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS user_credentials (
    credential_id                VARCHAR(1000) NOT NULL PRIMARY KEY,
    user_entity_user_id          VARCHAR(1000) NOT NULL,
    public_key                   BLOB          NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN       NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN       NOT NULL,
    attestation_object           BLOB,
    attestation_client_data_json BLOB,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000) NOT NULL
);
```

**設計判断:**

- `public_key_credential_user_entity` の `id` および `user_credentials` の `credential_id`,
  `user_entity_user_id` は Base64URL エンコードされた文字列として格納するため `VARCHAR` を使用
  （Spring Security 内部の処理に合わせた型選択）
- Spring Security 組み込みの `JdbcPublicKeyCredentialUserEntityRepository` はデフォルトテーブル名
  `user_entities` を使用するが、本実装例では独自テーブル名 `public_key_credential_user_entity` を
  採用し、カスタムリポジトリで対応

### 3.3 初期データ（data.sql）

```sql
INSERT INTO users (username, password, enabled)
VALUES ('user',
    '{bcrypt}$2a$10$GRLdNijSQMUvl/au9ofL.eDwmoohzzS7.rmNSJZ.0FxO/BTk76klW',
    TRUE);

INSERT INTO authorities (username, authority)
VALUES ('user', 'ROLE_USER');
```

---

## 4. 実装設計

### 4.1 SecurityConfig.java

```java
package io.nncdevel.example.auth.config;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**", "/error", "/.well-known/**")
                    .permitAll()
                .anyRequest().authenticated()
            )
            .requestCache(cache -> cache
                .requestCache(requestCache())
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/")
                .permitAll()
            )
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
    PublicKeyCredentialUserEntityRepository
            publicKeyCredentialUserEntityRepository(
                    JdbcOperations jdbc) {
        return new JdbcCustomUserEntityRepository(jdbc);
    }

    @Bean
    JdbcUserCredentialRepository userCredentialRepository(
            JdbcOperations jdbc) {
        return new JdbcUserCredentialRepository(jdbc);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories
            .createDelegatingPasswordEncoder();
    }

    private RequestCache requestCache() {
        HttpSessionRequestCache cache = new HttpSessionRequestCache();
        cache.setRequestMatcher(
            new NegatedRequestMatcher(new OrRequestMatcher(
                new AntPathRequestMatcher("/login"),
                new AntPathRequestMatcher("/login/**"),
                new AntPathRequestMatcher("/logout"),
                new AntPathRequestMatcher("/logout/**"),
                new AntPathRequestMatcher("/error"),
                new AntPathRequestMatcher("/.well-known/**")
            ))
        );
        return cache;
    }
}
```

**設計判断:**

- `webAuthn()` DSL に `rpId`, `rpName`, `allowedOrigins` の 3 パラメータを設定するだけで
  Passkey 認証が有効化される
- `formLogin()` と `webAuthn()` の両方を設定することでハイブリッド認証を実現
- `loginPage("/login")` でカスタムログインページを使用。
  Spring Security デフォルトのログインページ生成を無効化
- `PublicKeyCredentialUserEntityRepository` に `JdbcCustomUserEntityRepository` を登録し、
  独自テーブル名に対応
- `JdbcUserCredentialRepository` は Spring Security 組み込みをそのまま使用
  （テーブル名 `user_credentials` がデフォルトと一致するため）
- `RequestCache` をカスタマイズし、`/login`, `/logout`, `/error`, `/.well-known/**` を
  リダイレクト先の保存対象から除外。ブラウザの自動リクエスト（Chrome DevTools 等）が
  ログイン後のリダイレクト先になる問題を防止
- `rpId` は `allowedOrigins` のホスト名部分と一致させる必要がある

### 4.2 JdbcCustomUserEntityRepository.java

```java
package io.nncdevel.example.auth.repository;

public class JdbcCustomUserEntityRepository
        implements PublicKeyCredentialUserEntityRepository {

    private static final String TABLE =
        "public_key_credential_user_entity";

    // findById, findByUsername, save, delete を JDBC で直接実装
    // ID は Bytes.toBase64UrlString() で文字列化して格納
    // 読み取り時は Bytes.fromBase64() でデコード
    // エンティティは ImmutablePublicKeyCredentialUserEntity.builder()
    // で構築
}
```

**設計判断:**

- Spring Security 組み込みの `JdbcPublicKeyCredentialUserEntityRepository` は `final` クラスで
  テーブル名を変更する手段がない
- `PublicKeyCredentialUserEntityRepository` インターフェースを直接実装し、
  テーブル名 `public_key_credential_user_entity` を使用

### 4.3 HomeController.java

```java
package io.nncdevel.example.auth.controller;

@Controller
public class HomeController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    public String home(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        return "home";
    }
}
```

### 4.4 ProfileController.java

```java
package io.nncdevel.example.auth.controller;

@Controller
public class ProfileController {

    private final PublicKeyCredentialUserEntityRepository
        userEntityRepository;
    private final UserCredentialRepository credentialRepository;

    // コンストラクタインジェクション

    @GetMapping("/profile")
    public String profile(Authentication auth, Model model) {
        // ユーザー名から WebAuthn ユーザーエンティティを検索
        // 関連するクレデンシャル一覧を取得してモデルに設定
    }

    @PostMapping("/profile/passkeys/delete")
    public String deletePasskey(@RequestParam String credentialId) {
        credentialRepository.delete(Bytes.fromBase64(credentialId));
        return "redirect:/profile";
    }
}
```

**設計判断:**

- Spring Security デフォルトの `/webauthn/register` ページを使わず、
  プロファイル画面内に Passkey 管理機能を統合
- Passkey の一覧表示・削除はサーバーサイドで処理し、
  登録は JavaScript（WebAuthn API）で実装
- `credentialId` は Base64URL エンコード文字列として
  フォームの hidden フィールドで受け渡し

### 4.5 テンプレート設計

#### 共通レイアウト（fragments/layout.html）

- ヘッダー: ロゴ（トップページリンク）+ 認証済み時のユーザーメニュー
- フッター: アプリケーション名
- `thymeleaf-extras-springsecurity6` の `#authorization.expression()` で認証状態を判定

#### ログインページ（login.html）

- フォームログイン（Username / Password）
- Passkey ログインボタン（WebAuthn 認証 JavaScript 埋め込み）
- デモアカウント情報の表示
- CSRF トークンを `<meta>` タグで埋め込み、JavaScript から参照

#### プロファイル画面（profile.html）

- ユーザー名表示
- Passkey 登録フォーム（ラベル入力 + Register ボタン）
- 登録済み Passkey テーブル（ラベル / 作成日時 / 最終使用日時 / 署名カウント / Delete）
- WebAuthn Registration API の JavaScript を埋め込み

### 4.6 application.properties

```properties
spring.application.name=springboot-auth-with-passkey-example

server.port=8080

spring.datasource.url=jdbc:h2:mem:passkeydb
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=

spring.h2.console.enabled=true
spring.h2.console.path=/h2-console

spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema.sql
spring.sql.init.data-locations=classpath:data.sql

logging.level.root=INFO
logging.level.io.nncdevel.example.auth=DEBUG
logging.level.org.springframework.security=DEBUG
```

---

## 5. ビルド設定

### pom.xml（主要部分）

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.9</version>
</parent>

<groupId>io.nncdevel.example</groupId>
<artifactId>springboot-auth-with-passkey-example</artifactId>
<version>0.0.1-SNAPSHOT</version>

<properties>
    <java.version>21</java.version>
</properties>

<dependencies>
    <!-- Spring Boot -->
    spring-boot-starter-web
    spring-boot-starter-security
    spring-boot-starter-jdbc
    spring-boot-starter-thymeleaf

    <!-- Thymeleaf Spring Security Integration -->
    thymeleaf-extras-springsecurity6

    <!-- WebAuthn4J (Spring Security が内部で使用) -->
    webauthn4j-core:0.29.7.RELEASE

    <!-- H2 Database (runtime) -->
    h2

    <!-- Test -->
    spring-boot-starter-test (test)
    spring-security-test (test)
</dependencies>
```

---

## 6. 動作確認手順

```bash
./mvnw spring-boot:run
```

1. <http://localhost:8080> → ログインページにリダイレクト
2. `user` / `password` でフォームログイン → ホーム画面
3. ヘッダーのユーザーメニュー →「Profile」→ ラベル入力 →「Register」→ 認証器操作 → 登録完了
4. ログアウト →「Sign in with a passkey」→ Passkey 選択 → 認証完了

H2 Console: <http://localhost:8080/h2-console>
（JDBC URL: `jdbc:h2:mem:passkeydb`, User: `sa`）

---

## 7. 実際の利用時の考慮事項

- **HTTPS**: WebAuthn は localhost 以外で HTTPS 必須。
  `rpId` と `allowedOrigins` を本番ドメインに変更
- **DB**: H2 → PostgreSQL 等。BLOB → BYTEA、TIMESTAMP → TIMESTAMP WITH TIME ZONE に変更
- **セッション**: 分散環境では Spring Session + Redis 等を検討
- **複数 Passkey**: デバイス紛失時のため、1 ユーザーに複数登録を推奨
- **フォールバック**: Passkey が使えない場合のフォームログインを維持
