# SpringBoot Passkey 実装例 - 仕様書

## 1. プロジェクト概要

### 1.1 目的

Spring Security 6.5 が提供する Passkey（WebAuthn）認証機能の実装例を提示する。
パスワードレス認証を Spring Boot アプリケーションに統合する際のリファレンスとして利用することを想定する。

### 1.2 位置づけ

本実装例はあくまで実装例であり、本番運用を目的としたアプリケーションではない。
Spring Security の公式ドキュメントに沿った構成をベースに、カスタムログインページ・プロファイル画面での
Passkey 管理を独自実装している。

### 1.3 対象読者

- Spring Boot / Spring Security を用いた Web アプリケーション開発者
- Passkey（WebAuthn）認証の導入を検討しているエンジニア

---

## 2. 機能要件

### 2.1 認証機能

| ID    | 機能             | 説明                                         |
| ----- | ---------------- | -------------------------------------------- |
| F-001 | フォームログイン | Username/Password によるログイン             |
| F-002 | Passkey 認証     | 登録済み Passkey によるパスワードレスログイン |
| F-003 | ログアウト       | セッション破棄によるログアウト               |

### 2.2 Passkey 管理機能

| ID    | 機能             | 説明                                                               |
| ----- | ---------------- | ------------------------------------------------------------------ |
| F-004 | Passkey 登録     | 認証済みユーザーが新しい Passkey を登録する                         |
| F-005 | Passkey 一覧表示 | ラベル・作成日時・最終使用日時・署名カウントを表示する              |
| F-006 | Passkey 削除     | 登録済み Passkey を削除する                                        |

### 2.3 画面

| ID    | 画面             | 説明                                                |
| ----- | ---------------- | --------------------------------------------------- |
| S-001 | ログインページ   | フォーム + Passkey ボタン（独自実装）                |
| S-002 | ホーム画面       | ユーザー名表示、ユーザーメニュー（独自実装）         |
| S-003 | プロファイル画面 | Passkey 登録・一覧・削除（独自実装）                 |

---

## 3. 非機能要件

### 3.1 動作環境

| 項目       | 要件                                                    |
| ---------- | ------------------------------------------------------- |
| Java       | JDK 21 以上                                             |
| ブラウザ   | WebAuthn API 対応（Chrome / Edge / Safari / Firefox）   |
| プロトコル | HTTP（localhost のみ。それ以外は HTTPS 必須）           |

### 3.2 データ永続化

- ユーザー情報および Passkey クレデンシャルは RDBMS に永続化する
- 開発・動作確認用として H2 Database（In-Memory）を使用する
- アプリケーション再起動時にデータは消失する（In-Memory のため）

### 3.3 制約事項

- WebAuthn は Secure Context でのみ動作する。
  localhost は例外として HTTP で動作するが、それ以外のホスト名では HTTPS が必須
- Passkey の登録にはフォームログインによる事前認証が必要
- Spring Security が内部で `webauthn4j-core` ライブラリを使用するため、依存に含める必要がある
  （アプリケーションコードからは直接使用しない）

---

## 4. ユースケース

### UC-001: フォームログイン

**アクター**: 未認証ユーザー

1. ユーザーが <http://localhost:8080> にアクセスする
2. Spring Security がログインページにリダイレクトする
3. ユーザーが Username / Password を入力し「Sign In」をクリックする
4. 認証成功後、ホーム画面（`/`）にリダイレクトされる

### UC-002: Passkey 登録

**アクター**: 認証済みユーザー
**前提**: フォームログイン済み

1. ユーザーがヘッダーのユーザーメニューから「Profile」をクリックする
2. プロファイル画面（`/profile`）が表示される
3. ユーザーがラベル（例: `my-macbook`）を入力する
4. 「Register」ボタンをクリックする
5. ブラウザが認証器の操作を要求する（指紋認証 / 顔認証 / セキュリティキー / パスワードマネージャー）
6. 認証器での操作完了後、画面がリロードされ登録済み一覧に反映される

### UC-003: Passkey でログイン

**アクター**: 未認証ユーザー（Passkey 登録済み）

1. ユーザーがログインページにアクセスする
2. 「Sign in with a passkey」ボタンをクリックする
3. ブラウザが Passkey 選択ダイアログを表示する
4. ユーザーが Passkey を選択し、認証器で本人確認を行う
5. 認証成功後、ホーム画面にリダイレクトされる

### UC-004: Passkey 削除

**アクター**: 認証済みユーザー
**前提**: Passkey 登録済み

1. ユーザーがプロファイル画面（`/profile`）にアクセスする
2. 登録済み一覧から対象の Passkey の「Delete」ボタンをクリックする
3. サーバーがクレデンシャルを削除する
4. 一覧から該当の Passkey が消える

### UC-005: ログアウト

**アクター**: 認証済みユーザー

1. ユーザーがヘッダー右上のユーザーメニューから「Logout」をクリックする
2. セッションが破棄される
3. ログインページにリダイレクトされる（`/login?logout`）

---

## 5. エンドポイント仕様

### 5.1 Spring Security 提供エンドポイント

| メソッド | パス                             | 認証 | 説明                               |
| -------- | -------------------------------- | ---- | ---------------------------------- |
| POST     | `/login`                         | 不要 | Username/Password 認証             |
| POST     | `/login/webauthn`                | 不要 | Passkey 認証（JSON レスポンス）    |
| POST     | `/webauthn/authenticate/options` | 不要 | 認証オプション取得（CSRF 必須）    |
| POST     | `/webauthn/register/options`     | 必要 | 登録オプション取得（CSRF 必須）    |
| POST     | `/webauthn/register`             | 必要 | Passkey 登録実行                   |
| POST     | `/logout`                        | 必要 | ログアウト                         |

### 5.2 アプリケーション固有エンドポイント

| メソッド | パス                       | 認証 | 説明                             |
| -------- | -------------------------- | ---- | -------------------------------- |
| GET      | `/login`                   | 不要 | カスタムログインページ表示       |
| GET      | `/`                        | 必要 | ホーム画面表示                   |
| GET      | `/profile`                 | 必要 | プロファイル画面表示             |
| POST     | `/profile/passkeys/delete` | 必要 | Passkey 削除                     |

---

## 6. データ仕様

### 6.1 ユーザー（users テーブル）

| 項目     | 型           | 必須 | 説明                         |
| -------- | ------------ | ---- | ---------------------------- |
| username | VARCHAR(50)  | ○    | ユーザー名（主キー）         |
| password | VARCHAR(500) | ○    | パスワード（エンコード済み） |
| enabled  | BOOLEAN      | ○    | 有効フラグ                   |

### 6.2 権限（authorities テーブル）

| 項目      | 型          | 必須 | 説明                        |
| --------- | ----------- | ---- | --------------------------- |
| username  | VARCHAR(50) | ○    | ユーザー名                  |
| authority | VARCHAR(50) | ○    | 権限名（例: ROLE_USER）     |

> テーブル間の外部キー制約は設定していない。

### 6.3 WebAuthn ユーザーエンティティ（public_key_credential_user_entity テーブル）

| 項目         | 型            | 必須 | 説明                                      |
| ------------ | ------------- | ---- | ----------------------------------------- |
| id           | VARCHAR(1000) | ○    | WebAuthn ユーザー ID（Base64URL 文字列）  |
| name         | VARCHAR(200)  | ○    | ユーザー名（users.username と一致、一意） |
| display_name | VARCHAR(200)  |      | 表示用ユーザー名                          |

### 6.4 Passkey クレデンシャル（user_credentials テーブル）

| 項目                         | 型            | 必須 | 説明                           |
| ---------------------------- | ------------- | ---- | ------------------------------ |
| credential_id                | VARCHAR(1000) | ○    | クレデンシャル ID（主キー）    |
| user_entity_user_id          | VARCHAR(1000) | ○    | WebAuthn ユーザー ID           |
| public_key                   | BLOB          | ○    | 公開鍵データ                   |
| signature_count              | BIGINT        |      | 署名カウンター                 |
| uv_initialized               | BOOLEAN       |      | ユーザー検証初期化済み         |
| backup_eligible              | BOOLEAN       | ○    | バックアップ対象フラグ         |
| authenticator_transports     | VARCHAR(1000) |      | トランスポート種別             |
| public_key_credential_type   | VARCHAR(100)  |      | クレデンシャルタイプ           |
| backup_state                 | BOOLEAN       | ○    | バックアップ状態フラグ         |
| attestation_object           | BLOB          |      | アテステーションオブジェクト   |
| attestation_client_data_json | BLOB          |      | クライアントデータ JSON        |
| created                      | TIMESTAMP     |      | 作成日時                       |
| last_used                    | TIMESTAMP     |      | 最終使用日時                   |
| label                        | VARCHAR(1000) | ○    | ユーザー設定のラベル           |

> テーブル間の外部キー制約は設定していない。

### 6.5 初期データ

| username | password   | role      |
| -------- | ---------- | --------- |
| `user`   | `password` | ROLE_USER |

---

## 7. 参考資料

- [Spring Security 6.5 - Passkeys](https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html)
- [W3C Web Authentication (Level 3)](https://www.w3.org/TR/webauthn-3/)
