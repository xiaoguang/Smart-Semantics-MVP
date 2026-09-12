# Progress: jdtls-harness

- Status: COMPLETE
- Agent role: Isolated JDT LS feasibility harness implementer (Tasks 2–5)
- Model: GPT-5
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: Implement and run only the approved research harness under `research/jdtls-source-navigation-feasibility/`; materialize and measure only the ignored JDT LS trial workspace. No production-agent, customer-build, legacy, shared-contract, commit, or REPORT.md changes.
- Approved inputs: Frozen jshERP commit `8c30ce7861570458920175e200bb2a6442713580`; pinned JDT LS 1.61.0 already selected, SHA-verified, and extracted; approved RED contract (13 tests, 12 assertion failures, 0 errors); task briefs 2–5.
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read the four approved task briefs, the approved RED report, research README, and source-scoped instructions.
- Recorded pre-existing untracked worktree entries without modifying them.
- Reproduced the approved direct RED: `NavigationProbeTest` ran 13 tests with 12 expected assertion failures and 0 errors. Each failure names an intentionally absent harness seam.
- Verified the selected archive SHA-256 and the unique expected Equinox launcher. The JDT LS core manifest carries the pinned release source commit.
- Added the approved exact JavaParser, LSP4J, Gson, and Jackson BOM dependency setup. Maven resolved the two absent LSP4J artifacts and the pinned shade plugin once; the effective tree now uses only Gson 2.14.0.
- Implemented the isolated frozen-object materializer, generic syntax-only request planner, independent packet checker, and the one-session LSP4J runner.
- The direct test contract is GREEN: 13 tests, 0 failures, 0 errors.
- Packaged the executable shaded jar offline with the command-stable required filename and verified that it contains the probe, runner, LSP4J, and JavaParser classes.
- Materialized the exact frozen archive and full unmanaged Java projection into the ignored trial workspace.
- Independently verified the materialized archive digest equals a fresh `git archive` digest, the manifest has 273 matching entries, and the projection has 273 Java files with no prohibited inputs.
- Started one measured sandbox session and recorded its exact `initialize`, `initialized`, and `workspace/didChangeConfiguration` requests. The host command cutoff interrupted it before workspace-ready and no packet was written.
- Ran the one preserved measured session to `ServiceReady`. It wrote and hashed the registration packet, whose independent primary gate failed; the financial phase was not entered.
- Detected and preserved a Task 3 stop-condition diagnostic: the JDT LS Buildship component attempted to obtain Gradle version metadata from `services.gradle.org`; sandbox networking returned `UnknownHostException`.
- Added the bounded generic-protocol correction for empty client capabilities: `textDocument/documentSymbol` flat `SymbolInformation.location.range` is now accepted alongside hierarchical `DocumentSymbol.range`. The runner also rejects the observed forbidden Gradle-download diagnostic before navigation can continue.
- Re-ran only the direct contract after that correction: 13 tests, 0 failures, 0 errors.

## Current state

- The research POM, independent checks, and direct GREEN tests are complete; no production integration was added.
- The measured runtime/results are preserved under the ignored trial tree. Their verdict is `FAIL`; the network-attempt stop condition is a feasibility blocker, not a static-test failure.

## Changed files

- `backend-agents/sources/source-code/progress/jdtls-harness.md`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/NavigationProbe.java`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/PacketOracleCheck.java`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/TrialRunner.java`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/test/java/org/sourceanalysis/research/jdtls/NavigationProbeTest.java`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/README.md`
- `.superpowers/sdd/jdtls-source-navigation-feasibility-plan/tasks-2-5-report.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only pre-existing untracked JDT LS research/progress paths were present before this progress record. |
| Task briefs/report/README/scoped instructions read | PASS | Exact frozen commit, server, one-session, packet/oracle, and no-customer-build constraints recorded. |
| `mvn -f .../pom.xml -o -Dtest=NavigationProbeTest test` | EXPECTED_RED | 13 tests; 12 assertion failures; 0 errors; failures only for absent `NavigationProbe`/`PacketOracleCheck` seams. |
| `mvn -f .../pom.xml dependency:go-offline` | PASS | Resolved the approved LSP4J 1.0.0 artifacts and build plugin exactly once. |
| `mvn -f .../pom.xml -o dependency:tree ...` | PASS | LSP4J 1.0.0/JSON-RPC 1.0.0 and one direct Gson 2.14.0; JSON-RPC's ranged Gson transitively excluded. |
| `shasum -a 256 ...jdt-language-server...tar.gz` | PASS | `338e7e73d61836651ba2453919a0d34fa763eb4e7c03342092309bffb8934c64`. |
| JDT LS core manifest inspection | PASS | Bundle `1.61.0.202609031315`, Eclipse source reference commit `08eafe6ff60c7159ef88571d47b6a9ef82fef94e`; one expected launcher. |
| `mvn -f .../pom.xml -o -Dtest=NavigationProbeTest test` | PASS | 13 tests; 0 failures; 0 errors. |
| `mvn -f .../pom.xml -o -DskipTests package` | PASS | Executable `target/jdtls-source-navigation-feasibility.jar` created with pinned runtime classes. |
| `java -jar ... materialize --trial-root ... --commit 8c30ce7861570458920175e200bb2a6442713580` | PASS | Exact archive, manifest, runtime/results directories, and full main-Java projection created in the ignored trial workspace. |
| Archive/manifest/projection direct verification | PASS | Archive SHA-256 `5e517b68d0365e00dd090211e999913dc85edf9de95983639f25f9162995be2d`; 273 manifest entries and Java files; prohibited inputs absent. |
| `java -jar ... probe --cases registration,financial --trial-root ...` | INTERRUPTED | JDT LS launched in the sandbox and completed the exact three initialization messages, but the host's 30-second command cutoff ended the process before `ServiceReady`; no packet/result was created. |
| `mvn -f .../pom.xml -o -Dtest=NavigationProbeTest test` | PASS | After the flat `SymbolInformation.location.range` and forbidden-activity guard correction: 13 tests; 0 failures; 0 errors. |
| `java -jar ... probe --cases registration,financial --trial-root ...` | MEASURED_FAIL | One JDT LS session reached `ServiceReady`; registration packet SHA-256 `3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424`, primary gate `FAIL`, financial not entered. Client diagnostics record Buildship's blocked `services.gradle.org` request (`UnknownHostException`), a Task 3 stop condition. |

## Decisions

- Treat the approved tasks as a bounded throwaway feasibility harness, not a production integration.
- Keep navigation inputs checker-free; invoke `PacketOracleCheck` only after a packet is written and hashed.
- Registration is the hard gate before financial navigation.
- Exclude JSON-RPC's ranged Gson dependency and use the direct, exact Gson 2.14.0 pin so the harness does not inherit an open range.
- Bounded design-neutral test correction: test execution uses the research-module working directory, so its source guard now resolves the production source from that directory and its manifest assertion resolves `projectedPath` relative to the declared `projection/src` root. The test behavior and asserted source bytes remain unchanged.
- Bounded pinned-API correction: JavaParser 3.28.2 constructs `Range` from `Position` values, not four integers; no navigation behavior changed.
- Bounded packaging correction: Maven's default versioned artifact name did not satisfy the approved `java -jar .../target/jdtls-source-navigation-feasibility.jar` command; `finalName` now enforces that exact file name.
- Bounded executable correction: shaded LSP4J signature resources made the first jar launch fail before materialization; shade now strips standard signature metadata and the rebuilt jar starts normally.
- Bounded protocol correction: the first measured session passed `projection/src` as `rootUri`, despite the approved contract requiring `projection` with `sourcePaths=["src"]`. The runner now uses the exact parent root and waits specifically for JDT LS `ServiceReady`, verified from the pinned server's `ServiceStatus` bytecode.
- Bounded generic-response correction: with the brief-required empty capabilities object, JDT LS can return flat `SymbolInformation` entries whose range is `location.range`; the walker now handles that standard form without changing fixed entry inputs, candidate policy, or using a custom resolver.
- Bounded stop-condition correction: the runner now turns a received `Cannot download published Gradle versions`/`services.gradle.org` diagnostic into a forbidden JDT activity error instead of allowing a subsequent walk.

## Blockers

- The measured server attempted a Gradle metadata network request despite the exact `java.import.gradle.enabled=false` initialization settings. Because the sandbox blocked it, no customer build ran; nevertheless Task 3 requires it to be treated as a terminal failed feasibility run. The preserved registration packet independently also reflects the pre-correction flat-document-symbol handling issue; its failure must not be reinterpreted as an LSP binding conclusion.

## Exact next action

- None in this bounded task. A separately authorized investigation must address the JDT LS/Buildship metadata-download behavior before another measured session is permissible.

## Resume checks

- Re-read this file, run `git status --short`, and verify the exact commit/tool-manifest identities before changing source or trial state.
