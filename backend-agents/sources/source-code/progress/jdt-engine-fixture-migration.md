# JDT Engine Fixture Migration

Status: COMPLETE

Scope: migrate the JavaCodeEngineFactoryTest fake-Java fixture and the
JdtProjectSessionTest fixture to the current verified-project/session seams.

Constraints: test fixtures only; no production or design compatibility changes.

Completed:

- Updated JavaCodeEngineFactoryTest fake Java executables to report
  `openjdk version "21"` for `-version` with exit 0; JDT startup still marks
  the process and exits 71.
- Replaced JdtProjectSessionTest's reflective/raw-path fixture with
  `VerifiedSourceTextSet`/`VerifiedSourceTextDocument` construction and the
  package-private `JdtProcessIsolation`, `JdtLanguageServerClient`, and
  `JdtProjectSession.open` seam. The fake LSP responds to initialize,
  declaration readiness, shutdown, and exit messages in memory.
- Migrated the shared EngineTestReflection project helper to the verified-text
  seam, removing stale raw-Path constructor probing.

Verification:

- `mvn -t .mvn/toolchains.xml -Dtest=EngineConfigurationLoaderTest,JavaCodeEngineFactoryTest,JdtProjectSessionTest,JavaCodeEngineContractTest test` — PASS (31 tests, 0 failures/errors).
- Scoped `spotless:apply` and `spotless:check` — PASS.
- `git diff --check` — PASS.
