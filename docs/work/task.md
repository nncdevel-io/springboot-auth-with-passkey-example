---
title: Spring Boot 4.1 系と Java 25 への移行
priority: medium
created: 2026-10-19
owner: t-izuno
related:
  - TASK.md
  - docs/DESIGN.md
  - docs/SPECIFICATIONS.md
---

<!-- markdownlint-disable-next-line MD025 -->
# TASKS

マイルストーン: M2 - Spring Boot 4.1 系と Java 25 への移行
ゴール: Spring Boot 4.1.1（Spring Security 7.1 系）と Java 25 でビルド・テスト・CI が通り、フォームログインと Passkey の登録・認証がこれまでどおり動く状態にする

## ワークフロールール

- タスク着手時にステータスを 🚧 に更新する
- 必要な検証をエージェント単独で完遂できた場合は ✅、人の確認が残る場合は 🧪 に更新し、未確認事項を明記する。判断がつかない場合は 🧪 に倒す
- 🧪 から ✅ への更新はユーザーのレビュー承認をもって行う
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
| ---- | ---- | ---- | ---- |
| TASK-001 | ✅ | 公式移行ガイドと照合した影響箇所一覧を作成する | - |
| TASK-002 | ✅ | Maven Wrapper を 3.3.4、Maven を 3.9.16 に更新する | - |
| TASK-003 | ✅ | pom.xml を Boot 4.1.1・Java 25・Boot 4 のスターター構成に更新する | TASK-001,TASK-002 |
| TASK-004 | ✅ | SecurityConfig.java を Spring Security 7.1 の API に合わせて修正する | TASK-003 |
| TASK-005 | ✅ | WebAuthn のリポジトリ実装と schema.sql を Security 7.1 に合わせる | TASK-003 |
| TASK-006 | ✅ | テストコードを Boot 4 のテスト API に合わせ、全テストを通す | TASK-004,TASK-005 |
| TASK-007 | ✅ | CI ワークフローを JDK 25 と最新の Actions に更新する | TASK-006 |
| TASK-008 | 🧪 | フォームログインと Passkey の登録・認証を実機で動作確認する | TASK-006 |
| TASK-009 | ✅ | README.md のバージョン表記・コード例・参照リンクを更新する | TASK-008 |
| TASK-010 | ✅ | DESIGN.md・SPECIFICATIONS.md・CLAUDE.md のバージョン記述を更新する | TASK-008 |

## タスク詳細（補足が必要な場合のみ）

### TASK-001

- 補足: 照合先は Spring Boot 4.0 / 4.1 の移行ガイドとリリースノート、Spring Security 7.0 / 7.1 の移行ドキュメント、webauthn4j の対応表とする
- 補足: 確認対象は次のとおり
  - スターター名の変更、`@AutoConfigureMockMvc` のパッケージ、H2 コンソールの設定
  - `AntPathRequestMatcher` の扱い、WebAuthn のテーブル定義と API
  - Security 7.1 が要求する webauthn4j のバージョン
- 補足: 成果物は `docs/work/` 配下に置き、各項目に参照 URL を付ける

### TASK-002

- 補足: `mvnw` と `mvnw.cmd` も wrapper 3.3.4 のものに作り直す
- 注意: Maven 4 系は正式版が出ていないため対象外とする

### TASK-003

- 補足: 依存の変更内容は `docs/work/migration-impact.md` の No.1〜6 に従う
- 注意: このタスク単体ではコンパイルが通らない前提とする。コンパイルは TASK-004 と TASK-005 の完了時点で確認する

### TASK-005

- 補足: 対象は `JdbcCustomUserEntityRepository.java`、`schema.sql`、`data.sql`

### TASK-007

- 補足: CI と同じ3つのコマンド（コンパイル、テスト、テストを省いたパッケージ作成）をローカルで実行し、すべて成功した

### TASK-008

- 補足: ブラウザーの認証器を使う操作は人の確認が必要なため、完了時は 🧪 とする
- 補足: 確認項目は、フォームログイン、Passkey の登録、Passkey でのログイン、Passkey の削除、ログアウト、H2 コンソールの表示
- 補足: Chrome の仮想認証器（DevTools Protocol の WebAuthn ドメイン）を使い、確認項目をすべて実行して動作を確かめた
- 補足: ログイン後に `/favicon.ico` へ移動する、ログアウト後にメッセージが出ない、の2点を見つけたが、移行前の 3.5.9 でも同じ挙動のため対象外とした
- 未確認: Touch ID などの実機の認証器での登録とログイン

### TASK-009

- 補足: README.md の SecurityConfig のコード例と webauthn4j のバージョン例も対象に含める

### TASK-010

- 補足: DESIGN.md の構成図とコード例（`AntPathRequestMatcher` を使った部分）も対象に含める
- 注意: ルートの `TASK.md`（M1 の記録）は書き換えない
