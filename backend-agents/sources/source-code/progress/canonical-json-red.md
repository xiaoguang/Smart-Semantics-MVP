# Progress: canonical-json-red

- Status: COMPLETE
- Agent role: Luna RED test owner
- Model: gpt-5 / current Codex agent
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Add exactly the published first foundation RED behavior test for canonical JSON encoding; no production, POM, docs, CI, store, policy, runtime, or business-analysis changes.
- Approved inputs: Published `docs/DESIGN.md` artifact-foundation sections; both implementation plans; `progress/artifact-foundation-design-publication.md`; `progress/source-analysis-artifact-foundation.md`; `progress/artifact-foundation-design-brief.md`.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Read the applicable root, backend, and source-code `AGENTS.md` files.
- Read the published artifact-foundation design sections and both implementation plans.
- Read the artifact-foundation publication, orchestrator, and design-brief progress records.
- Checked Git status; preserved the two pre-existing untracked progress files.

## Current state

- The target source tree has no `CanonicalJsonCodec` or `ImmutableBytes` production type.
- Added the single test named `encodesCanonicalObjectWithUtf8ByteOrderedKeys` with an independent hand-authored UTF-8 golden.

## Changed files

- `backend-agents/sources/source-code/progress/canonical-json-red.md`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalJsonCodecTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` before edits | PASS | Only the two pre-existing untracked publication/orchestrator progress files were present. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` | EXPECTED RED | JDK 17 toolchain selected; main compilation passed; test compilation failed only because `ImmutableBytes` and `CanonicalJsonCodec` are not implemented. |

## Decisions

- Use only the exact public seam `new CanonicalJsonCodec().encodeCanonical(JsonNode)` and compare returned `ImmutableBytes` content to a hand-authored compact UTF-8 byte sequence.
- Include deliberate insertion-order reversal, nested object, ordered array, non-ASCII key, boolean, null, and signed/unsigned in-range integers.
- Do not add parse, identity, policy, store, filesystem, JSONL, runtime, validation, or business-analysis tests.

## Blockers

- None.

## Exact next action

- Parent may now hand the single RED test to the Terra GREEN owner, who owns only `CanonicalJsonCodec` and `ImmutableBytes` for the next slice.

## Resume checks

- Re-read this file, run `git status --short`, verify only this progress file and the single authorized test are new, and preserve the two pre-existing untracked progress files.
