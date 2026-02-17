# springboot-passkey-example

Spring Security 6.5 の Passkey（WebAuthn）サポートを使った認証の実装例です。

## 技術スタック

- Java 21
- Spring Boot 3.4.x
- Spring Security 6.5.x（WebAuthn DSL）
- webauthn4j-core 0.29.7（Spring Security が内部で使用）
- Thymeleaf
- H2 Database
- Gradle (Kotlin DSL)

> Spring Security は WebAuthn の検証処理に `webauthn4j-core` を内部で使用するため、依存に含めています。`webauthn4j-spring-security`（別プロジェクト）は使用しません。

## 前提条件

- JDK 21 以上
- WebAuthn 対応ブラウザ（Chrome / Edge / Safari / Firefox の最新版）

## 起動方法

```bash
./gradlew bootRun
```

http://localhost:8080 にアクセスしてください。

## 初期ユーザー

| Username | Password |
|----------|----------|
| `user` | `password` |

## 使い方

### 1. フォームログイン

http://localhost:8080 にアクセスするとログインページが表示されます。初期ユーザーでログインしてください。

### 2. Passkey 登録

ログイン後のホーム画面から「Register Passkey」をクリックし、ラベルを入力して「Register」を押してください。ブラウザが認証器の操作を求めます。

### 3. Passkey でログイン

ログアウト後、ログインページの「Sign in with a passkey」をクリックしてください。

## プロジェクト構成

```
src/main/
├── java/com/example/passkey/
│   ├── Application.java
│   ├── config/
│   │   └── SecurityConfig.java
│   └── controller/
│       └── HomeController.java
└── resources/
    ├── application.yml
    ├── schema.sql
    ├── data.sql
    └── templates/
        └── home.html
```

## Spring Security が提供する画面・エンドポイント

| パス | 説明 |
|------|------|
| `/login` | ログインページ（フォーム + Passkey ボタン） |
| `/webauthn/register` | Passkey 登録ページ（ラベル入力 + 登録済み一覧 + 削除） |
| `/logout` | ログアウト |

## H2 Console

開発用のデータ確認に利用できます。

- URL: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:passkeydb`
- User: `sa` / Password: （空欄）

## ドキュメント

- [SPEC.md](./SPEC.md) — 仕様書（機能要件・ユースケース・データ仕様）
- [DESIGN.md](./DESIGN.md) — 設計書（アーキテクチャ・実装・ビルド設定）

## 参考資料

- [Spring Security 6.5 - Passkeys](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passkeys.html)
- [Baeldung - Integrating Passkeys into Spring Security](https://www.baeldung.com/spring-security-integrate-passkeys)

## License

Apache License 2.0
