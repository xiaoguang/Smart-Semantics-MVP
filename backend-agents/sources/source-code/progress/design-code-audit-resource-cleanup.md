# Progress: design-code-audit-resource-cleanup

- Status: COMPLETE
- Agent role: mechanical resource cleanup
- Model: requested configuration `gpt-5.6-terra` / `xhigh`; actual execution model is recorded by the backend.
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: remove six unused v1 Step07 main resources; move two fingerprint-only v1 resources to test resources without changing Java, v2 resources, tests, or design.
- Approved inputs: user-confirmed audit; main baseline `e8c40ea2f250da55d6b8797c32c380461061c3c9`.
- Current branch/worktree: formal `backend-agents/sources/source-code` checkout; existing unrelated audit/research files preserved.

## Completed

- Read applicable repository, prototype, backend-agent, and source-code instructions.
- Confirmed `BusinessProcessPromptCatalog` has a fixed ten-task map to eight v2 resources and no directory scan.
- Recorded and preserved the two v1 fingerprint fixtures byte-for-byte in the matching test-resource package:
  - `business-catalog-draft-v1.txt`: `0137c7169413d4e1255f81fd38b8e2505821476e8794850a4329414a157e04a4`
  - `business-catalog-review-v1.txt`: `9209146aacf90e8ca2fa03a27ac2ae618915aa0e0928f3dfabccdac75f98e93c`
- Removed the six audited unused v1 main resources and the two moved v1 main copies.
- Confirmed only `process-group-*-v1` remains in main resources; the fingerprint test is the sole Java reader of the retained v1 names.
- Ran the approved direct Maven verification under the JDK 17 toolchain: 5 tests passed with no failures, errors, or skips.

## Current state

- Cleanup is complete. Main resources contain only the preserved `process-group-*-v1` files, while the two fingerprint fixtures resolve from test resources.
- `target/classes` was deliberately not cleaned. It may retain removed files from earlier resource copies and is not evidence of the current main-resource inventory.

## Changed files

- `progress/design-code-audit-resource-cleanup.md`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/` (eight v1 files removed: six unused and two moved)
- `src/test/resources/org/sourceanalysis/app/analysis/knowledge/business-catalog-draft-v1.txt`
- `src/test/resources/org/sourceanalysis/app/analysis/knowledge/business-catalog-review-v1.txt`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Static resource/read-path audit | PASS | The only v1 readers are the legacy fingerprint fixture methods; production catalog names only v2 resources. |
| Post-move SHA-256 comparison | PASS | Both test resources equal their recorded pre-move hashes. |
| Main-resource inventory | PASS | The six unused files and two moved files are absent; only `process-group-*-v1` remains. |
| `JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessPromptV2ContractTest,BusinessProcessSemanticFingerprintV2Test test` | PASS | `BusinessProcessPromptV2ContractTest`: 4; `BusinessProcessSemanticFingerprintV2Test`: 1; total 5, 0 failures, 0 errors, 0 skipped. |

## Decisions

- Preserve the existing v1 fingerprint fixture semantics by retaining the exact original bytes under `src/test/resources`.
- Do not alter v2 resources, `process-group-*-v1`, Java, test assertions, or design documentation.
- A non-clean targeted Maven run verifies fixed-v2 prompt routing and v1 fingerprint invalidation, but does not remove stale copies that may already exist in `target/classes`.

## Blockers

- None.

## Exact next action

- None; hand off the completed mechanical cleanup to the parent task.

## Resume checks

- Confirm the two test-resource files match the recorded pre-move SHA-256 values.
- If work resumes, preserve existing `target/classes` contents unless an explicitly authorized clean build is requested.
