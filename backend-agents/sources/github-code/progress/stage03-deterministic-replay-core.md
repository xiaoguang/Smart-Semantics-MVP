# Progress: Stage03 deterministic replay core

- Status: COMPLETE
- Agent role: Stage03 production implementation
- Model: gpt-5.6-terra xhigh
- Scope: Stage03-only canonical generation transcripts and deterministic no-Provider replay.
- Approved inputs: scoped `AGENTS.md`; `DESIGN.md` deterministic replay contract; Stage03 §3.5; deterministic replay RED test/progress; existing Stage03 generation records.
- Current branch/worktree: shared and pre-existing dirty; unrelated work is preserved.

## Completed

- Read scoped guidance, design replay boundary, Stage03 replay test/progress, and current Stage03 generation entrypoint.
- Confirmed the intended seam: replay reconstructs tasks from frozen Stage02/registry/profile/runtime/budget inputs and uses the same strict admission/M6/M7/render result path as generation; it cannot receive a Provider.
- Reproduced the intentional RED with `mvn -Dtest=Stage03DeterministicReplayTest test`: test compilation reports 32 missing-symbol errors for the replay records/class and replay failure codes; no test executed.
- Added the public replay records/seam and four stable replay codes. Generation and replay now meet through the same private task builder, strict R1/R2 parser, program admission, M6/M7, renderer, and result assembler; only their round source differs.
- Generation captures sorted canonical R1/R2 transcripts after strict parsing; replay validates the exact Flow/Capsule/R1/R2 set, task equality, canonical response bytes/digest, and semantic digest without receiving or constructing a Provider.
- `mvn -Dtest=Stage03DeterministicReplayTest test` is GREEN: 10 tests, 0 failures, 0 errors, 0 skipped.
- Archive-v2 replay integration found that the shared task builder and output-schema helper wrote JSON object keys in construction order while archive artifacts are recursively canonical JSON. The result was a semantic-equivalent archive task that failed the existing strict replay task-byte equality gate. Parent approved a minimal producer correction: canonicalize the shared Stage03 task/schema JSON recursively at generation, so generation, sealed transcript, archive, and no-Provider replay share one byte representation.

## Current state

- The approved canonical task/schema byte correction is complete. `mvn -Dtest=Stage03DeterministicReplayTest test` is green: 10 tests, 0 failures, 0 errors, 0 skipped.
- `mvn -Dtest='Stage03*Test' test` is green after the correction: 69 tests, 0 failures, 0 errors, 0 skipped.

## Changed files

- `progress/stage03-deterministic-replay-core.md`
- `src/main/java/com/linguan/codemd/stage03/CanonicalFlowRound.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03ReplayRequest.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03DeterministicReplay.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03FailureCode.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03Generator.java`
- `src/main/java/com/linguan/codemd/stage03/Stage03Result.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03DeterministicReplayTest test` | RED | Test compilation has 32 missing-symbol errors for replay records/class and replay failure codes; no test executed. |
| `mvn -Dtest=Stage03DeterministicReplayTest test` | PASS | 10 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -Dtest='Stage03*Test' test` | PASS | 69 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | PASS | No tracked whitespace diagnostics. |
| scoped `git diff --no-index --check /dev/null <owned Stage03 source>` | PASS | No whitespace diagnostics; exit 1 is the expected content-difference status for untracked files. |

## Decisions

- No replay-specific result subtype, identity field, Provider invocation, or M8 dependency.
- Registry drift remains Stage03 registry revalidation, not transcript trust.
- Stage04 must not reconstruct the generator's private task builder to compensate for noncanonical producer bytes. The common Stage03 task builder is the sole canonical-byte authority.

## Blockers

- None.

## Exact next action

- Complete; archive-v2 consumers may rely on exact canonical task and schema bytes.
