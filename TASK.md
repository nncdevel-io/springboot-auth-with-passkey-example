# TASKS

マイルストーン: M1 - SpringBoot Passkey認証アプリケーションの構築
ゴール: 仕様書・設計書に基づき、参照リポジトリ（springboot-auth-with-microsoft-entra-id-example）と同等の構成でSpring Security 6.5のPasskey（WebAuthn）認証アプリケーションを構築し、ビルド・テストが通る状態にする

## ワークフロールール

- タスク着手時にステータスを 🚧 に更新する
- タスク完了時にステータスを ✅ に更新する
- DependsOn のタスクがすべて ✅ でないタスクには着手しない

## ステータス表記ルール

| Status | 意味 |
| ---- | ----- |
| ⏳ | 未着手、TODO |
| 🚧 | 作業中、IN_PROGRESS |
| 🧪 | 確認待ち、REVIEW |
| ✅ | 完了、DONE |
| 🚫 | 中止、CANCELLED |

## タスク一覧

| ID | Status | Summary | DependsOn |
|----|--------|---------|-----------|
| TASK-001 | ✅ | Mavenプロジェクト基盤を構築する（pom.xml・Maven Wrapper・.gitignore） | - |
| TASK-002 | ✅ | プロジェクト共通設定ファイルを作成する（CLAUDE.md・lint設定・cspell） | - |
| TASK-003 | ✅ | アプリケーション基盤を作成する（Application.java・properties・package-info） | TASK-001 |
| TASK-004 | ✅ | データベーススキーマ定義と初期データを作成する（schema.sql・data.sql） | TASK-001 |
| TASK-005 | ✅ | Spring Security/WebAuthn認証設定を実装する（SecurityConfig.java） | TASK-003 |
| TASK-006 | ✅ | ホーム画面を実装する（HomeController.java・テンプレート・CSS） | TASK-003 |
| TASK-007 | ✅ | テストクラスを作成する（各クラス単位のテスト・テスト用設定） | TASK-005,TASK-006 |
| TASK-008 | ✅ | GitHub Actions CIワークフローを作成する（ci.yml） | TASK-001 |
| TASK-009 | ✅ | ビルド・テスト実行による検証を行う | TASK-007,TASK-008 |

## タスク詳細（補足が必要な場合のみ）

### TASK-001

- 補足: pom.xml は参照リポジトリの構成に準拠する（spring-boot-starter-parent 3.5.x）
- 補足: 依存は設計書 §1.2 に基づき starter-web, starter-security, starter-jdbc, starter-thymeleaf, webauthn4j-core, h2 を設定する
- 補足: Maven Wrapper（mvnw / mvnw.cmd / .mvn/wrapper/*）を生成する
- 補足: .gitignore を参照リポジトリと同等（Maven/IDE/OS対応）に更新する
- 補足: src/main/java, src/main/resources, src/test/java, src/test/resources のディレクトリ構造を作成する

### TASK-002

- 補足: CLAUDE.md にプロジェクト概要・ビルドコマンド・アーキテクチャ情報を記載する
- 補足: .markdownlint-cli2.yaml を参照リポジトリと同等の設定で作成する
- 補足: cspell.json を本プロジェクト用の辞書付きで作成する

### TASK-003

- 補足: パッケージは `io.nncdevel.example.auth`（参照リポジトリと同一）
- 補足: Application.java を設計書 §4.1 に基づき作成する
- 補足: application.properties を設計書 §4.5 に基づき作成する（YAML ではなく properties 形式）
- 補足: 各パッケージに package-info.java を配置する（main: auth, config, controller）
- 補足: src/main/resources/static/css/.gitkeep、src/main/resources/templates/.gitkeep を配置する

### TASK-004

- 補足: schema.sql を設計書 §3.2 に基づき作成する
- 補足: data.sql を設計書 §3.3 に基づき作成する
- 注意: 配置先は src/main/resources/

### TASK-005

- 補足: SecurityConfig.java を設計書 §4.2 に基づき作成する
- 注意: WebAuthn DSL（rpName, rpId, allowedOrigins）の設定を含む
- 注意: JdbcPublicKeyCredentialUserEntityRepository と JdbcUserCredentialRepository の Bean 登録が必要
- 注意: パッケージは `io.nncdevel.example.auth.config`

### TASK-006

- 補足: HomeController.java を設計書 §4.3 に基づき作成する
- 補足: home.html を設計書 §4.4 に基づき作成する
- 補足: error.html を参照リポジトリのパターンに準じて作成する
- 補足: style.css を参照リポジトリのパターンに準じて作成する（Passkey用にカスタマイズ）

### TASK-007

- 補足: ApplicationTests.java（コンテキスト読み込みテスト）を作成する
- 補足: SecurityConfigTests.java（認可設定テスト）を作成する
- 補足: HomeControllerTests.java（MockMvc によるコントローラーテスト）を作成する
- 補足: src/test/resources/application.properties（テスト用設定）を作成する
- 補足: src/test/java に package-info.java を配置する
- 注意: WebAuthn の E2E テストはブラウザ操作が必要なためスコープ外

### TASK-008

- 補足: .github/workflows/ci.yml を参照リポジトリと同等の構成で作成する
- 補足: トリガーは develop ブランチへの push/PR および claude/** ブランチへの push
- 注意: 参照リポジトリは main ブランチだが、本リポジトリのデフォルトブランチは develop

### TASK-009

- 補足: `./mvnw clean compile` でコンパイルが通ることを検証する
- 補足: `./mvnw test` でテストが通ることを検証する
- 補足: `./mvnw package -DskipTests` でパッケージングできることを検証する
