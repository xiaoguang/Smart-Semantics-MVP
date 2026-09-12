# Progress: JDT LS startup/debug feasibility

- Status: COMPLETE
- Agent role: Task 4 Sol/xhigh feasibility debugger
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-12T10:05:13-02:30
- Last updated: 2026-09-12T10:24:14-02:30
- Scope: Diagnose the retained Buildship Gradle-version metadata network attempt; apply at most one named release/runtime/source-root/trusted-JAR configuration correction; if safe, run direct harness checks, package, preserve the failed run, and execute exactly one fresh combined registration-to-financial sandboxed JDT session.
- Approved inputs: JDT LS 1.61.0 / source commit 08eafe6ff60c7159ef88571d47b6a9ef82fef94e; frozen jshERP commit 8c30ce7861570458920175e200bb2a6442713580; retained Task 3 runtime/results; no oracle/check input before packet/hash.
- Current branch/worktree: codex/jdtls-source-navigation-feasibility / /private/tmp/linguan-source-analysis-process-design

## Completed

- Read repository, backend, and source-code instructions; the feasibility plan; Task 3 and Task 4 briefs; Tasks 2–5 report; current navigator/session implementation; retained request/stderr/process logs; and retained verdict.
- Confirmed the first initialize request contains the prescribed nested Gradle/Maven disablement, while the server log independently records Buildship `PublishedGradleVersionsWrapper$LoadVersionsJob` attempting `services.gradle.org` before `ServiceReady`.
- Traced the pinned Buildship bytecode: `CorePlugin.registerServices` constructs `PublishedGradleVersionsWrapper`; its constructor unconditionally schedules `LoadVersionsJob`; the job calls `PublishedGradleVersions.create(REMOTE_IF_NOT_CACHED)` without consulting JDT LS import preferences; a missing/freshness-expired cache calls `https://services.gradle.org/versions/all`.
- Confirmed the checksum-verified pinned JDT LS core bundle contains `gradle/checksums/versions.json` in the same service response shape, so no remote or target-specific data is needed for an isolated cache.
- Added and observed the focused RED (`isolated Buildship runtime preparation is missing`), then implemented the single correction and observed its focused GREEN.
- Ran the complete directly covering harness contract after the correction: all 14 tests passed.
- Packaged the corrected shaded harness offline successfully.
- Preserved the complete retained failure by moving its `runtime/` and `results/` plus a copied tool manifest under `.workspace/jdtls-source-navigation-feasibility/failed-runs/task3-buildship-network-attempt-20260912T095556-0230/`; recreated empty runtime/results directories only after matching the four key SHA-256 values.
- Executed exactly one fresh sandboxed combined `probe --cases registration,financial` JDT session with the corrected shaded harness. The server reached `ServiceReady`; the runtime log, stderr, and packet diagnostics contain no Buildship version-download error, `UnknownHostException`, or reference to `services.gradle.org`.
- Wrote and hashed the registration packet before its checker ran. Packet SHA-256 is `3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424`; the packet has zero methods and calls and the checker returned `FAIL` for `FULL_BODY_NOT_FOUND:jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java:357:367`. The financial phase was not entered because registration is a hard gate.
- Traced the remaining failure to the exact pinned server implementation: prescribed empty client capabilities select flat `SymbolInformation`; `DocumentSymbolHandler.getOutline()` constructs its location via `JDTUtils.toLocation(IJavaElement)`, which uses `LocationType.NAME_RANGE`. The returned range therefore cannot enclose the configured method-body span. This is a `JDT_NAVIGATION_CAPABILITY` result qualified as `DOCUMENT_SYMBOL_FULL_RANGE_UNAVAILABLE_UNDER_PRESCRIBED_EMPTY_CAPABILITIES`, not an environment failure and not evidence for or against declaration/definition binding.
- Updated the durable research README with the isolated cache boundary and measured corrected-session result. Recorded the active ignored verdict with the precise reason class/detail, packet/checker facts, and financial hard-gate outcome.
- Re-ran the full directly covering test and offline package commands from the final source, verified packet and cache hashes, verified the retained failure hashes, confirmed the active evidence has no forbidden Buildship activity, confirmed the financial result directory is empty, and ran both staged and unstaged diff whitespace checks.

## Current state

- Root cause is established. The single named correction is implemented: the harness locates the selected distribution's sole JDT LS core bundle, validates its embedded Gradle catalog is a non-empty JSON array, atomically seeds the isolated Buildship cache, sets child `XDG_CACHE_HOME`, and records the value in the command receipt.
- The single authorized correction is proven to prevent the unconditional Buildship metadata-network attempt in the fresh sandboxed session.
- The fresh combined session is complete and no second session may be started. Registration failed its primary gate because flat document symbols provide name-only ranges; financial is `NOT_ENTERED` by contract.
- The original failed runtime/results remain frozen under the explicit failed-run directory. Active runtime/results contain the sole corrected run.

## Changed files

- `backend-agents/sources/source-code/progress/jdtls-debug.md`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/TrialRunner.java`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/src/test/java/org/sourceanalysis/research/jdtls/NavigationProbeTest.java`
- `backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/README.md`
- Ignored measured artifact: `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/results/verdict/verdict.json`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing Task 2–5 research/progress work preserved; this file is the Task 4-owned progress artifact. |
| retained log/verdict inspection | PASS | Gradle disablement is present in initialize; Buildship version metadata job still attempts network; `ServiceReady=true`; prior packet was pre-flat-symbol correction. |
| `mvn -f .../pom.xml -o -Dtest=NavigationProbeTest#preparesIsolatedBuildshipCacheFromTheSelectedJdtDistribution test` (RED) | EXPECTED FAIL | 1 test, 1 failure, 0 errors: `isolated Buildship runtime preparation is missing`. |
| same focused command after implementation | PASS | 1 test, 0 failures, 0 errors. |
| `mvn -f .../pom.xml -o -Dtest=NavigationProbeTest test` | PASS | Fresh final-source run: 14 tests, 0 failures, 0 errors; test elapsed 54.54 s, build elapsed 57.291 s. |
| `mvn -f .../pom.xml -o -DskipTests package` | PASS | Fresh final-source offline package in 3.518 s; shaded JAR SHA-256 `ace06783d07f4a791e416da18a926be1c1b236a6de0d31862a3cfe031fdd60eb`. |
| failed-run preservation SHA-256 | PASS | Requests `3f3d9c3c...`; registration packet `3c11aa20...`; verdict `6fd54b70...`; manifest `ca8a48ff...`; no open file under the retained runtime before move. |
| `java -jar .../jdtls-source-navigation-feasibility.jar probe --cases registration,financial --trial-root .../.workspace/jdtls-source-navigation-feasibility` | FAIL (measured feasibility) | Exactly one JDT session; `ServiceReady`; no forbidden Buildship network attempt; registration SHA `3c11aa20...`; primary gate `FAIL`; financial `NOT_ENTERED`. |
| `shasum -a 256 -c packet.sha256` | PASS | `packet.json: OK`. |
| embedded-catalog and isolated-cache SHA-256 | PASS | Both are `c0d581d312d4073916fd2aafe1fc8f297f8f1dac886179a21f70b0cd7fe57b83`. |
| active log/stderr/request/diagnostic/verdict scan | PASS | `NO_FORBIDDEN_BUILDSHIP_ACTIVITY_IN_ACTIVE_EVIDENCE`. |
| financial result file count | PASS | `FINANCIAL_FILE_COUNT=0`, consistent with the registration hard gate. |
| `git diff --check` and `git diff --cached --check` | PASS | No whitespace errors. |

## Decisions

- The Buildship failure was an environment/runtime-configuration issue and is resolved by the one allowed correction. Classify the remaining measured failure as `JDT_NAVIGATION_CAPABILITY` / `DOCUMENT_SYMBOL_FULL_RANGE_UNAVAILABLE_UNDER_PRESCRIBED_EMPTY_CAPABILITIES`.
- Do not inspect oracles/check inputs from the navigator. Invoke the checker only after any fresh packet is written and hashed.
- Single hypothesis: if the child process receives a dedicated `XDG_CACHE_HOME` whose fresh Buildship cache is populated from the checksum-verified pinned JDT LS bundle, the unconditional `REMOTE_IF_NOT_CACHED` job will read locally and make no network attempt; removing either isolation or seeding is a regression.

## Blockers

- None.

## Exact next action

- None; hand the measured FAIL result and artifacts to Task 5. Do not start another JDT LS session.

## Resume checks

- Re-read this file, verify branch/worktree and Git status, confirm retained failed runtime/results are unchanged, and do not start another JDT LS session.
