# Progress: canonical-json-green

- Status: COMPLETE
- Agent role: Terra GREEN implementation owner
- Model: gpt-5 / current Codex agent
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Implement only the GREEN for `CanonicalJsonCodecTest#encodesCanonicalObjectWithUtf8ByteOrderedKeys` with `CanonicalJsonCodec` and `ImmutableBytes`; no test, POM, durable-design, CI, parser, typed-ID/address, policy-registry, store, filesystem, runtime, validation, evidence, JSONL/RAW_UTF8 writer, or business-analysis changes.
- Approved inputs: Published `docs/DESIGN.md` artifact foundation seam; both implementation plans; `progress/artifact-foundation-design-publication.md`; `progress/source-analysis-artifact-foundation.md`; `progress/artifact-foundation-design-brief.md`; `progress/canonical-json-red.md`.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read all applicable root, backend, and source-code instructions; the authoritative design and both implementation plans; the artifact-foundation publication/brief/orchestrator records; the Canonical JSON RED record; and the existing RED test.
- Checked Git status before edits and preserved the existing untracked progress records and RED test.

## Current state

- The RED test requires the public `new CanonicalJsonCodec().encodeCanonical(JsonNode)` seam to produce exact compact UTF-8 JSON with recursively UTF-8-byte-ordered object keys and an immutable byte value.
- Added the smallest encoder: recursive object-key sorting by unsigned UTF-8 bytes, array-order retention, integral number output, and the published compact string escapes. It rejects unsupported/non-integral JSON nodes and invalid Unicode surrogate sequences.
- Added `ImmutableBytes` with no public constructor or unsafe accessor; it copies input and returned bytes and compares full byte content by value.
- This GREEN does not add `parseCanonical` or any later foundation capability.
- The final Git status contains only the four pre-existing untracked RED/design/orchestrator records plus this progress record and the two authorized production classes; no tests, POM, durable design, or CI file changed in this GREEN task.

## Changed files

- `backend-agents/sources/source-code/progress/canonical-json-green.md`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/ImmutableBytes.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalJsonCodec.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` before edits | PASS | Preserved the pre-existing untracked foundation progress records and the RED test. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` | PASS | JDK 17 toolchain selected; `CanonicalJsonCodecTest` ran 1 test with 0 failures, 0 errors, and 0 skips; build succeeded. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Spotless completed successfully and reformatted `CanonicalJsonCodec.java`; it kept 23 Java files clean. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` after Spotless | PASS | JDK 17 toolchain selected; `CanonicalJsonCodecTest` again ran 1 test with 0 failures, 0 errors, and 0 skips; build succeeded. |
| `git diff --check` | PASS | No whitespace-error output; exit status 0. |
| Final `git status --short` | PASS | Only the pre-existing `artifact-foundation-design-brief.md`, `canonical-json-red.md`, `source-analysis-artifact-foundation.md`, and RED test plus this task's progress record and the two authorized production classes are untracked. |

## Decisions

- Use a private recursive encoder over `JsonNode`, ordering each object field by unsigned lexicographic UTF-8 bytes and preserving arrays exactly.
- Implement the already-published `ImmutableBytes` defensive-copy and byte-content equality contract without adding an unsafe accessor or public constructor.

## Blockers

- None.

## Exact next action

- Parent integration owner may review the three task-owned paths and continue with the next independently assigned RED slice; do not commit from this task.

## Resume checks

- Re-read this progress record and `progress/canonical-json-red.md`; inspect `git status --short`; preserve the pre-existing RED/design/orchestrator files and the RED test; verify only this record and the two authorized production classes belong to this GREEN task.
