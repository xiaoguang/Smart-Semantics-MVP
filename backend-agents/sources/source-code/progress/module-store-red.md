# Progress: module-store-red

- Status: BLOCKED
- Agent role: Luna/xhigh RED-test author for the canonical module-artifact store
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Strict RED for two `CanonicalModuleArtifactStore` public behaviors: real MODULE_ARTIFACT_JSON installation/reopen/idempotence and missing-receipt fail-closed reopen.
- Approved inputs: `backend-agents/sources/source-code/AGENTS.md`; `docs/DESIGN.md` §13.3, §13.3.1, §13.7; `docs/analysis-steps/01-verified-source-inventory.md` M1; both implementation plans; existing foundation artifact value types and policy registry.
- Current branch/worktree: `codex/source-analysis-module-store` at `/private/tmp/linguan-source-analysis-module-store`

## Completed

- Read the source-scoped instructions, both implementation plans, the module-record/identity/atomic-publication contract, the §13.7 stable codes, the complete VerifiedSourceInventory analysis-step document, and the existing module-store progress.
- Confirmed the requested production store seam/records are absent while foundation codec, typed IDs, and policy registry are present.
- Created this progress file before modifying the test source.

## Current state

- The orchestrator has taken over before the test source was produced. No test class or production code was added in this handoff.
- The intended test scope remains bounded to a real `@TempDir`, `RunStoreBootstrap.openForTest`, and `FileSystemCanonicalModuleArtifactStore`, with no filesystem/atomic-publication/canonicalization/identity mocks.

## Changed files

- `progress/module-store-red.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only the pre-existing orchestrator progress file was present before this progress file. |
| Required source/design/plan reads | PASS | M1 request schema/identity, module records, receipt self-exclusion, public seams, and stable failure codes read. |
| Target selector | NOT RUN | Orchestrator took over before test creation; no selector was run. |

## Decisions

- Keep only the requested two JUnit behaviors; do not test JSONL, standalone/RAW, validation address, analysis-step/run store, production `open(Path)`, crash, collision, symlink, extra files, or validation address.
- Use reflection only as a staged RED bridge for missing public production types; once present, the same test executes the real typed public seam. No test substitute or private implementation access is introduced.
- Assert that no filesystem `Path` is returned by the public result through the returned public object graph, while using the `@TempDir` path only to verify exact directory contents and to delete the receipt for the negative behavior.

## Blockers

- The production `CanonicalModuleArtifactStore` seam and its request/result records are not yet present, so direct typed test compilation cannot proceed. Reflection is intentionally used to turn that absence into an explicit public-seam RED while retaining independent identity assertions.

## Exact next action

- 无

## Resume checks

- Re-read this file, run `git status --short`, inspect only the owned test/progress diff, and do not modify production, POM, design, or another progress file.
