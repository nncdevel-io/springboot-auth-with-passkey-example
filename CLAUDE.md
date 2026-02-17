# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## プロジェクト概要

Passkey（WebAuthn）によるパスワードレス認証を実装したSpring Boot Webアプリケーションのサンプル。Java 21、Spring Boot 3.5.x、Spring Security 6.5.x を使用。フォームログインとPasskey認証のハイブリッド構成。

## ビルド・テストコマンド

```bash
# ビルド（コンパイルのみ）
./mvnw clean compile

# テスト実行
./mvnw test

# 単一テストクラス実行
./mvnw test -Dtest=SecurityConfigTests

# 単一テストメソッド実行
./mvnw test -Dtest=SecurityConfigTests#authenticatedEndpointsRequireLogin

# パッケージング（JAR生成）
./mvnw clean package

# ローカル実行
./mvnw spring-boot:run
```

## アーキテクチャ

### パッケージ構成

```
io.nncdevel.example.auth/
├── Application.java                 # エントリポイント
├── config/
│   └── SecurityConfig.java          # Spring Security + WebAuthn/Passkey設定
└── controller/
    └── HomeController.java          # "/" - 認証必須エンドポイント
```

### 認証フロー

1. 未認証ユーザーがアクセス → Spring Security がログインページにリダイレクト
2. フォームログイン: Username/Password で認証
3. Passkey ログイン: 「Sign in with a passkey」→ ブラウザの認証器で本人確認
4. 認証成功後、ホーム画面（`/`）にリダイレクト

### エンドポイントのアクセス制御（SecurityConfig.java）

- **認証必須**: すべてのパス（`anyRequest().authenticated()`）
- Spring Security がログインページ（`/login`）とWebAuthnエンドポイントを自動公開

### ビュー層

Thymeleaf テンプレート (`src/main/resources/templates/`) + 静的リソース (`src/main/resources/static/css/`)。ログインページとPasskey登録ページはSpring Securityが提供。

### データ永続化

H2 Database（In-Memory）。`schema.sql` でテーブル作成、`data.sql` で初期ユーザー投入。アプリケーション再起動でデータ消失。

- users / authorities: Spring Security 標準テーブル
- public_key_credential_user_entity / user_credentials: WebAuthn クレデンシャル

### テスト構成

`@SpringBootTest` + `@AutoConfigureMockMvc` による統合テスト。

## CI/CD

GitHub Actions (`.github/workflows/ci.yml`)。`develop` ブランチへのpush/PRおよび `claude/**` ブランチへのpushで起動。JDK 21 (Temurin) を使用。
