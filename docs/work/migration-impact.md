# Spring Boot 4.1 と Java 25 への移行の影響箇所

Spring Boot 3.5.9（Spring Security 6.5.7）から Spring Boot 4.1.1（Spring Security 7.1.1）と Java 25 へ移行するときに、このリポジトリで変更が必要な箇所を公式ドキュメントと照合した結果である。
照合日は 2026-10-19。

## 影響箇所一覧

「確認状況」の「確認済み」は公式ドキュメントで変更内容を確認したもの、「未確認」は対応タスクで実物を見て確かめるものを示す。

| No | 対象ファイル | 変更内容 | 確認状況 | 対応タスク |
| --- | --- | --- | --- | --- |
| 1 | `pom.xml` | 親 POM を 4.1.1、`java.version` を 25 にする | 確認済み（根拠 1、2） | TASK-003 |
| 2 | `pom.xml` | `spring-boot-starter-web` を `spring-boot-starter-webmvc` に置き換える。前者は 4.x で非推奨 | 確認済み（根拠 3） | TASK-003 |
| 3 | `pom.xml` | `spring-security-webauthn` を追加する。7.x では Passkey の実装が `spring-security-web` から独立したモジュールに移った | 確認済み（根拠 4） | TASK-003 |
| 4 | `pom.xml` | `webauthn4j-core` の直書き依存を外す。`spring-security-webauthn` 7.1.1 から 0.31.9.RELEASE が推移的に入ることを `dependency:tree` で確かめた | 確認済み | TASK-003 |
| 5 | `pom.xml` | H2 コンソールを使うため `spring-boot-h2console` を追加する。4.x では H2 コンソールの自動構成がこのモジュールに分かれた | 確認済み（根拠 5） | TASK-003 |
| 6 | `pom.xml` | テスト依存を `spring-boot-starter-security-test` と `spring-boot-starter-webmvc-test` に置き換える。`@WithMockUser` は `spring-boot-starter-security-test` が必要になった | 確認済み（根拠 6、7、14） | TASK-003 |
| 7 | `SecurityConfig.java` | `AntPathRequestMatcher` を `PathPatternRequestMatcher` に置き換える。7.0 で `AntPathRequestMatcher` は削除された | 確認済み（根拠 8、9） | TASK-004 |
| 8 | `SecurityConfig.java` | `JdbcUserCredentialRepository` と `.webAuthn()` の設定はそのまま使える | 確認済み（根拠 4、10） | TASK-004 |
| 9 | `JdbcCustomUserEntityRepository.java`、`ProfileController.java` | `org.springframework.security.web.webauthn` パッケージの型（`Bytes`、`CredentialRecord` など）はパッケージ名が変わらない | 確認済み（根拠 11） | TASK-005 |
| 10 | `schema.sql` | 変更不要。`spring-security-web` 6.5.7 と 7.1.1 に同梱された `user-credentials-schema.sql` と `user-entities-schema.sql` を比べ、差分がないことを確かめた | 確認済み | TASK-005 |
| 11 | テスト3ファイル | `@AutoConfigureMockMvc` の import を `org.springframework.boot.webmvc.test.autoconfigure` に変える | 確認済み（根拠 7） | TASK-006 |
| 12 | `SecurityConfigTests.java` | 7.0 でログインページへのリダイレクトが相対 URL 優先に変わり、リダイレクト先が `/login` になる。期待値を `redirectedUrl("/login")` に変える | 確認済み（根拠 8、テスト実行で再現） | TASK-006 |
| 13 | `login.html`、`profile.html` | Passkey のエンドポイント（`/webauthn/register/options`、`/webauthn/register`、`/webauthn/authenticate/options`、`/login/webauthn`）は変わらない | 確認済み（根拠 4） | TASK-008 |
| 14 | `.mvn/wrapper/maven-wrapper.properties` | Maven 3.9.16 は Boot 4.1 の要件（Maven 3.6.3 以上）を満たす | 確認済み（根拠 2） | TASK-002 |
| 15 | `application.properties` | 変更不要。`spring-boot-properties-migrator` を一時的に入れて起動し、名前が変わったプロパティーの警告が出ないことを確かめた。確認後に依存から外した | 確認済み（根拠 14） | TASK-008 |
| 16 | `ci.yml` | 4.1 から `-DskipTests` ではテストの AOT 処理を止められない。このプロジェクトは AOT 処理を使っていないため、`package` が通ることだけを確かめる | 確認済み（根拠 15） | TASK-007 |

## 影響がないと判断したもの

- `thymeleaf-extras-springsecurity6`：Boot 4.1.1 の依存管理でも `springsecurity6` のまま 3.1.5.RELEASE が管理されている（根拠 12）
- `spring.sql.init.*`：4.1 でも `schema.sql` と `data.sql` の場所をプロパティーで指定できる（根拠 13）
- Jackson 3：アプリケーションのコードは Jackson を直接使っていない

## 移行ガイドで対象外と確かめたもの

Spring Boot 4.0 Migration Guide と 4.1 Release Notes（根拠 14、15）には、H2 コンソール、`spring.sql.init.*`、Passkey、Java 25 に関する項目はなかった。
`@MockBean` の削除、Jackson 3 のプロパティー変更、WAR 配備の変更は、このプロジェクトでは使っていない機能なので影響しない。

## 根拠

いずれも公式の資料である。

1. [spring-boot-dependencies 4.1.1（Maven Central）](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.1/spring-boot-dependencies-4.1.1.pom)
2. [Spring Boot 4.1.0 System Requirements](https://github.com/spring-projects/spring-boot/blob/v4.1.0/documentation/spring-boot-docs/src/docs/antora/modules/ROOT/pages/system-requirements.adoc)
3. [spring-boot-starter-web の build.gradle（main ブランチ）](https://github.com/spring-projects/spring-boot/blob/main/starter/spring-boot-starter-web/build.gradle)
4. [Spring Security 7.1.1 Passkeys](https://github.com/spring-projects/spring-security/blob/7.1.1/docs/modules/ROOT/pages/servlet/authentication/passkeys.adoc)
5. [Spring Boot 4.1.0 SQL Databases（Using H2's Web Console）](https://github.com/spring-projects/spring-boot/blob/v4.1.0/documentation/spring-boot-docs/src/docs/antora/modules/reference/pages/data/sql.adoc)
6. [spring-boot-starter-security-test の build.gradle（main ブランチ）](https://github.com/spring-projects/spring-boot/blob/main/starter/spring-boot-starter-security-test/build.gradle)
7. [AutoConfigureMockMvc.java（main ブランチ）](https://github.com/spring-projects/spring-boot/blob/main/module/spring-boot-webmvc-test/src/main/java/org/springframework/boot/webmvc/test/autoconfigure/AutoConfigureMockMvc.java)
8. [Spring Security 7.0 What's New](https://docs.spring.io/spring-security/reference/7.0/whats-new.html)
9. [Spring Security 7.0 Authorize HttpServletRequests](https://docs.spring.io/spring-security/reference/7.0/servlet/authorization/authorize-http-requests.html)
10. [Spring Security 7.0 Passkeys](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/passkeys.html)
11. [Webauthn4JRelyingPartyOperations.java（main ブランチ）](https://github.com/spring-projects/spring-security/blob/main/webauthn/src/main/java/org/springframework/security/web/webauthn/management/Webauthn4JRelyingPartyOperations.java)
12. 根拠 1 と同じ POM の `thymeleaf-extras-springsecurity.version`
13. [Spring Boot 4.1.0 Database Initialization](https://github.com/spring-projects/spring-boot/blob/v4.1.0/documentation/spring-boot-docs/src/docs/antora/modules/how-to/pages/data-initialization.adoc)
14. [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
15. [Spring Boot 4.1 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.1-Release-Notes)
