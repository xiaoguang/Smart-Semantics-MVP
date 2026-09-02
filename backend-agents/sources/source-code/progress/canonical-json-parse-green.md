# Progress: canonical-json-parse-green

- Status: COMPLETE
- Agent role: Terra GREEN implementation owner
- Model: gpt-5 / current Codex agent
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Implement only the smallest GREEN for `CanonicalJsonCodecTest#parseCanonicalRejectsOtherwiseValidJsonWithInsignificantWhitespace`; modify only `CanonicalJsonCodec` and this task-owned progress record. No test, POM, design, policy, store, typed-ID, runtime, validation, JSONL, or business-analysis change.
- Approved inputs: Repository/root/backend/source-code instructions; published `docs/DESIGN.md` artifact-foundation codec contract; both implementation plans; prior artifact-foundation status; and the existing canonical JSON RED/GREEN progress records and test.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read all applicable instructions, the published artifact codec contract, both implementation plans, the progress template, relevant artifact-foundation status and prior RED/GREEN records, and the existing codec/immutable-bytes/test sources.
- Confirmed the direct RED is established because the public `parseCanonical(ImmutableBytes)` method is absent, while the existing encoder selector was green before that RED.

## Current state

- The requested GREEN must strictly reject byte-different valid JSON by parsing the whole document and requiring the existing canonical encoder to reproduce the exact original bytes.
- Added `parseCanonical(ImmutableBytes)` to the codec only. It rejects a UTF-8 BOM, rejects malformed UTF-8, enables strict duplicate-key detection, rejects trailing JSON content, and requires exact bytes from the existing encoder before returning the parsed node.
- The exact direct selector is GREEN with both the existing encoding behavior and the new whitespace-rejection behavior passing.
- No commit was created.

## Changed files

- `backend-agents/sources/source-code/progress/canonical-json-parse-green.md`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalJsonCodec.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Preserved all pre-existing untracked foundation progress, production, and test files. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` | PASS | JDK 17 toolchain selected; 2 tests ran with 0 failures, 0 errors, and 0 skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless reformatted only `CanonicalJsonCodec.java`; 23 Java files remained clean. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` after Spotless | PASS | JDK 17 toolchain selected; 2 tests ran with 0 failures, 0 errors, and 0 skips. |
| `git diff --check` | PASS | No whitespace-error output; exit status 0. |

## Decisions

- Use Jackson strict duplicate-key detection plus an explicit strict UTF-8/BOM gate, full-document parsing, and the existing encoder for the byte-identity check.
- Do not perform owner schema validation or introduce policy/store/identity behavior.

## Blockers

- None.

## Exact next action

- Parent may review only this task-owned progress record and `CanonicalJsonCodec.java`; do not commit from this task.

## Resume checks

- Re-read this record and `canonical-json-parse-red.md`, inspect Git status, and ensure only this record and `CanonicalJsonCodec.java` are changed by this task.
