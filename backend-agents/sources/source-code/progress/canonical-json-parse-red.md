# Progress: canonical-json-parse-red

- Status: COMPLETE
- Agent role: Luna RED test owner
- Model: gpt-5 / current Codex agent
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Add exactly one RED behavior test for published `CanonicalJsonCodec.parseCanonical(ImmutableBytes)`; preserve the existing encoding test and change no production, POM, durable design, CI, or other test files.
- Approved inputs: Repository/root/backend/source-code instructions; published artifact foundation section in `docs/DESIGN.md`; both implementation plans; `progress/source-analysis-artifact-foundation.md`; `progress/canonical-json-green.md`; related artifact-foundation progress records.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the applicable instructions, artifact foundation design section, both implementation plans, root foundation progress, prior canonical JSON RED/GREEN records, and the existing codec implementation/test.
- Confirmed the existing encoding test is already green and `CanonicalJsonCodec` intentionally has no `parseCanonical` method yet.

## Current state

- Added one `parseCanonicalRejectsOtherwiseValidJsonWithInsignificantWhitespace` test using an otherwise valid object containing insignificant whitespace; it must be rejected because its bytes differ from the canonical re-encoding.
- The public contract permits asserting only the public exception category; no message or private state will be asserted.

## Changed files

- `backend-agents/sources/source-code/progress/canonical-json-parse-red.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalJsonCodecTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Preserved pre-existing untracked foundation records, production classes, and encoding test. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` | EXPECTED RED | Maven selected JDK 17, main compilation was up to date, and test compilation failed only because `CanonicalJsonCodec.parseCanonical(ImmutableBytes)` is absent; no tests executed. |
| `git diff --no-index --check /dev/null backend-agents/sources/source-code/progress/canonical-json-parse-red.md` | PASS | Exit 1 is the expected no-index difference; no whitespace-error output. |
| `git diff --no-index --check /dev/null backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalJsonCodecTest.java` | PASS | Exit 1 is the expected no-index difference; no whitespace-error output. |

## Decisions

- Use one byte-different but otherwise valid JSON object with a single insignificant space after the colon, so the behavior is isolated to canonical-byte rejection.
- Assert `IllegalArgumentException` only, matching the currently published direct codec parameter-error category without depending on messages or implementation details.

## Blockers

- The exact targeted selector intentionally cannot compile until the next GREEN implements `parseCanonical`; this is the expected RED and not a test typo.

## Exact next action

- Parent may review this progress record and the single test-file addition, then hand the RED to the Terra GREEN owner; do not commit from this task.

## Resume checks

- Re-read this record, inspect `git status --short`, verify the original encoding test remains unchanged, and ensure only this progress file plus the one test file are task-owned changes.
