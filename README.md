# Spring Boot Auth with Passkey Example

Spring Security 6.5 の Passkey（WebAuthn）サポートを使った認証の実装例です。
フォームログインと Passkey 認証のハイブリッド構成を採用しています。

## Passkey 有効化（デフォルト実装）

Spring Security で Passkey を有効にするには `SecurityFilterChain` に `webAuthn()` を追加します。

```java
@Bean
SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    return http
        .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
        .formLogin(Customizer.withDefaults())
        .webAuthn(webAuthn -> webAuthn
            .rpName("Passkey Demo")               // サービス名（認証器に表示される）
            .rpId("localhost")                     // Relying Party ID（ドメイン名）
            .allowedOrigins("http://localhost:8080")
        )
        .build();
}
```

`rpId` は `allowedOrigins` のホスト名部分と一致させる必要があります。

WebAuthn のクレデンシャルを JDBC で永続化するために、以下の Bean を登録します。

```java
@Bean
UserDetailsService userDetailsService(DataSource dataSource) {
    return new JdbcUserDetailsManager(dataSource);
}

@Bean
JdbcPublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(JdbcOperations jdbc) {
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
```

この構成で Spring Security は以下を自動生成します。

- ログインページ（フォーム + Passkey ボタン + WebAuthn 認証 JS）
- Passkey 登録ページ（`/webauthn/register`、ラベル入力・一覧・削除）

依存ライブラリとして `webauthn4j-core` が必要です。Spring Security が WebAuthn の検証処理で内部的に
使用するため、アプリケーションコードから直接呼び出すことはありません。

```xml
<dependency>
    <groupId>com.webauthn4j</groupId>
    <artifactId>webauthn4j-core</artifactId>
    <version>0.29.7.RELEASE</version>
</dependency>
```

**ここまでがデフォルト実装です。**
デフォルト実装では Spring Security が自動生成するログインページ・登録ページの HTML が固定されており、
デザインや文言の変更ができません。
本実装例ではアプリケーション独自のページで Passkey 認証・登録を行っています。

### spring-security-webauthn.js が使えない理由

デフォルト実装では `DefaultLoginPageGeneratingFilter` が WebAuthn 認証の JavaScript
（`spring-security-webauthn.js`）を含むログインページを自動生成します。
しかし `loginPage("/login")` でカスタムログインページを指定すると、このフィルタ自体が無効化されます。
`spring-security-webauthn.js` はフィルタ経由でのみ提供されるため、
カスタムログインページでは利用できません。

そのため本実装例では、`spring-security-webauthn.js` のリクエスト形式に準拠した
WebAuthn JavaScript を自前で実装しています。以降はその実装内容を説明します。

---

## Passkey 認証（WebAuthn Authentication）

カスタムログインページから Passkey 認証を行うには、Spring Security の WebAuthn エンドポイントを
JavaScript で呼び出します。

### 認証フロー

```text
[ブラウザ]                                [Spring Security]
    │                                           │
    ├── POST /webauthn/authenticate/options ──▶  │  認証オプション生成
    │◀── { challenge, allowCredentials, ... } ──┤
    │                                           │
    ├── navigator.credentials.get() ──▶ [認証器] │  ユーザーが認証操作
    │◀── credential ────────────────────────────┤
    │                                           │
    ├── POST /login/webauthn ─────────────────▶  │  アサーション検証
    │◀── { authenticated: true, redirectUrl } ──┤
    │                                           │
    └── window.location.href = redirectUrl       │
```

### リクエスト形式

`/login/webauthn` に送信するリクエストボディは、Spring Security JAR 内の
`spring-security-webauthn.js` と同じ形式に準拠する必要があります。

```javascript
var body = {
    id: cred.id,
    rawId: base64urlEncode(cred.rawId),
    response: {
        authenticatorData: base64urlEncode(cred.response.authenticatorData),
        clientDataJSON: base64urlEncode(cred.response.clientDataJSON),
        signature: base64urlEncode(cred.response.signature),
        userHandle: userHandle
    },
    credType: cred.type,                                  // ※ "type" ではなく "credType"
    clientExtensionResults: cred.getClientExtensionResults(),
    authenticatorAttachment: cred.authenticatorAttachment  // ※ 必須フィールド
};
```

> **注意:** フィールド名は `type` ではなく `credType` です。`authenticatorAttachment` も必須です。
> これらは `spring-security-webauthn.js` の実装に準拠した形式で、形式が異なると認証に失敗します。

### CSRF トークンの受け渡し

CSRF トークンを `<meta>` タグで埋め込み、JavaScript の `fetch` リクエストに付与します。

```html
<meta name="_csrf" th:content="${_csrf.token}"/>
<meta name="_csrf_header" th:content="${_csrf.headerName}"/>
```

---

## Passkey 登録（WebAuthn Registration）

デフォルトの登録ページ（`/webauthn/register`）は HTML が固定されており、
アプリケーション独自の画面に統合できません。
本実装例ではプロファイル画面（`/profile`）内に登録・一覧・削除を実装しています。

### 登録フロー

Spring Security の WebAuthn エンドポイント（`/webauthn/register/options`, `/webauthn/register`）は
そのまま利用し、フロントエンドの JavaScript から呼び出します。

```text
[ブラウザ]                                [Spring Security]
    │                                           │
    ├── POST /webauthn/register/options ──────▶  │  登録オプション生成
    │◀── { challenge, user, rp, ... } ─────────┤
    │                                           │
    ├── navigator.credentials.create() ──▶ [認証器]  鍵ペア生成
    │◀── credential ────────────────────────────┤
    │                                           │
    ├── POST /webauthn/register ─────────────▶  │  アテステーション検証・保存
    │◀── { success: true } ────────────────────┤
```

### リクエスト形式

```javascript
var registrationRequest = {
    publicKey: {
        credential: {
            id: cred.id,
            rawId: base64urlEncode(cred.rawId),
            response: {
                attestationObject: base64urlEncode(cred.response.attestationObject),
                clientDataJSON: base64urlEncode(cred.response.clientDataJSON),
                transports: cred.response.getTransports ? cred.response.getTransports() : []
            },
            type: cred.type,
            clientExtensionResults: cred.getClientExtensionResults(),
            authenticatorAttachment: cred.authenticatorAttachment
        },
        label: label
    }
};
```

### サーバーサイドの Passkey 管理

Passkey の一覧表示・削除はコントローラーで処理しています。

```java
@GetMapping("/profile")
public String profile(Authentication authentication, Model model) {
    var userEntity = userEntityRepository.findByUsername(authentication.getName());
    if (userEntity != null) {
        model.addAttribute("passkeys", credentialRepository.findByUserId(userEntity.getId()));
    } else {
        model.addAttribute("passkeys", List.of());
    }
    return "profile";
}

@PostMapping("/profile/passkeys/delete")
public String deletePasskey(@RequestParam String credentialId) {
    credentialRepository.delete(Bytes.fromBase64(credentialId));
    return "redirect:/profile";
}
```

---

## Passkey クレデンシャルの永続化

### デフォルト実装の制約

Spring Security 組み込みの `JdbcPublicKeyCredentialUserEntityRepository` は
テーブル名 `user_entities` をハードコードしています。
このクラスは `final` で継承できず、テーブル名や SQL を外部から変更する手段も提供されていません。

### 本実装例での対応

`PublicKeyCredentialUserEntityRepository` インターフェースを直接実装し、
独自のテーブル名 `public_key_credential_user_entity` を使用しています。

```java
public class JdbcCustomUserEntityRepository
        implements PublicKeyCredentialUserEntityRepository {

    private static final String TABLE = "public_key_credential_user_entity";

    private static final String FIND_BY_ID =
        "SELECT id, name, display_name FROM " + TABLE + " WHERE id = ?";

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        var results = this.jdbc.query(FIND_BY_ID, this::mapRow,
            id.toBase64UrlString());  // ID は Base64URL 文字列で格納
        return results.isEmpty() ? null : results.get(0);
    }

    // findByUsername, save, delete も同様に実装
}
```

> `JdbcUserCredentialRepository`（user_credentials テーブル）は Spring Security 組み込みを
> そのまま利用できます。カスタム実装が必要なのは `PublicKeyCredentialUserEntityRepository` のみです。

---

## データベーススキーマ

Spring Security 標準の `users` / `authorities` テーブルに加え、WebAuthn 用の 2 テーブルを定義しています。
テーブル間の外部キー制約は設定していません。

| テーブル                            | 用途                          |
| ----------------------------------- | ----------------------------- |
| `users`                             | ユーザー認証情報              |
| `authorities`                       | ユーザー権限                  |
| `public_key_credential_user_entity` | WebAuthn ユーザーエンティティ |
| `user_credentials`                  | Passkey クレデンシャル        |

> `public_key_credential_user_entity.name` と `users.username` は同じ値を持ちますが、
> 論理的な関連のみで外部キー制約はありません。

## エンドポイント一覧

### Spring Security 提供（WebAuthn）

| メソッド | パス                             | 認証 | 説明               |
| -------- | -------------------------------- | ---- | ------------------ |
| POST     | `/login/webauthn`                | 不要 | Passkey 認証       |
| POST     | `/webauthn/authenticate/options` | 不要 | 認証オプション取得 |
| POST     | `/webauthn/register/options`     | 必要 | 登録オプション取得 |
| POST     | `/webauthn/register`             | 必要 | Passkey 登録       |

### Spring Security 提供（その他）

| メソッド | パス     | 認証 | 説明              |
| -------- | -------- | ---- | ----------------- |
| POST     | `/login` | 不要 | Username/Password |
| POST     | `/logout`| 必要 | ログアウト        |

### アプリケーション固有

| メソッド | パス                       | 認証 | 説明             |
| -------- | -------------------------- | ---- | ---------------- |
| GET      | `/login`                   | 不要 | カスタムログイン |
| GET      | `/`                        | 必要 | ホーム画面       |
| GET      | `/profile`                 | 必要 | プロファイル画面 |
| POST     | `/profile/passkeys/delete` | 必要 | Passkey 削除     |

## プロジェクト構成

```text
src/main/
├── java/io/nncdevel/example/auth/
│   ├── Application.java                         # エントリーポイント
│   ├── config/
│   │   └── SecurityConfig.java                  # セキュリティ設定
│   ├── controller/
│   │   ├── HomeController.java                  # ログイン画面・ホーム画面
│   │   └── ProfileController.java               # プロファイル・Passkey 管理
│   └── repository/
│       └── JdbcCustomUserEntityRepository.java   # カスタムテーブル名対応
└── resources/
    ├── application.properties
    ├── schema.sql / data.sql                     # DDL・初期データ
    ├── static/css/style.css
    └── templates/
        ├── login.html                            # カスタムログインページ
        ├── home.html                             # ホーム画面
        ├── profile.html                          # プロファイル画面（Passkey 管理）
        ├── error.html
        └── fragments/layout.html                 # 共通ヘッダー・フッター
```

## 技術スタック

- Java 21 / Spring Boot 3.5.9 / Spring Security 6.5.x
- webauthn4j-core 0.29.7
- Thymeleaf + thymeleaf-extras-springsecurity6
- H2 Database（In-Memory）/ Maven

## 起動方法

```bash
./mvnw spring-boot:run
```

<http://localhost:8080> にアクセスしてください。

### 初期ユーザー

| Username | Password   |
| -------- | ---------- |
| `user`   | `password` |

### 動作確認手順

1. <http://localhost:8080> → ログインページにリダイレクト
2. `user` / `password` でフォームログイン → ホーム画面
3. ヘッダーのユーザーメニュー →「Profile」→ ラベル入力 →「Register」→ 認証器操作 → 登録完了
4. ログアウト →「Sign in with a passkey」→ Passkey 選択 → 認証完了

### H2 Console

- URL: <http://localhost:8080/h2-console>
- JDBC URL: `jdbc:h2:mem:passkeydb` / User: `sa` / Password: （空欄）

### テスト

```bash
./mvnw test
```

## ドキュメント

- [docs/SPECIFICATIONS.md](./docs/SPECIFICATIONS.md) - 仕様書
- [docs/DESIGN.md](./docs/DESIGN.md) - 設計書

## 参考資料

- [Spring Security 6.5 - Passkeys](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)
- [W3C Web Authentication (Level 3)](https://www.w3.org/TR/webauthn-3/)

## License

Apache License 2.0
