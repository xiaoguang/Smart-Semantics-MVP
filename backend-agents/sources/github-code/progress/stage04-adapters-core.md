# Progress: Stage04 adapters

- Status: COMPLETE
- Agent role: Stage04 production implementation
- Model: gpt-5.6-terra / xhigh
- Scope: Target CLI and JDK loopback HTTP translation adapters only. Both invoke only an injected Stage04 `CodeToMarkdownAgent`; no provider, source execution, CLI/HTTP expansion, or legacy POC bypass.
- Approved inputs: scoped `AGENTS.md`; Stage04 adapter RED contracts; Stage04 §§5.2, 13, 14, 16; existing CLI and public core seams.
- Current branch/worktree: shared and pre-existing dirty; all unrelated work is preserved.

## Completed

- Read the scoped rules, adapter RED progress, and target adapter contracts.
- Created this progress record before any production edit.
- Reproduced the focused selector through its intentional test-compilation
  boundary: the injected `CodeMdCli(CodeToMarkdownAgent)` and package-local
  `LoopbackHttpServer` seams are absent.
- Confirmed the original `/markdown` assertion cannot be honestly satisfied
  through the four-method Agent or its original fake: neither exposes document
  bytes, and the configured workspace contains no Candidate archive.
- Received the approved seam correction: retain the four-method Agent and use
  a separate explicit read-only `CandidateArtifactReader` for `candidate(id)`
  and `markdown(id)`. Do not use reflection or a legacy archive bypass.
- Added the explicit read-only reader, injected target CLI constructors, strict
  finite TOML parsing, and package-local JDK loopback adapter. Target mutation
  calls use only `CodeToMarkdownAgent`; candidate and Markdown reads use only
  the explicit reader.
- Kept the legacy CLI command behavior intact while adding target forms for
  `generate`, `validate`, `trace`, and `candidate`.
- Corrected two evidence-backed defects found by the direct selector: the
  target parser treated the numeric `server.port` as a TOML string, and the
  flattened Candidate reference overwrote HTTP transport `status`.

## Current state

- Adapter implementation is complete. The direct adapter contract is green;
  the bounded Stage 04 selector has one independent improvement-seam RED,
  recorded below without expanding this slice.

## Changed files

- `progress/stage04-adapters-core.md`
- `src/main/java/com/linguan/codemd/cli/CodeMdCli.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateArtifactReader.java`
- `src/main/java/com/linguan/codemd/stage04/LoopbackHttpServer.java`
- `src/main/java/com/linguan/codemd/stage04/CandidateSeriesLedger.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04TargetCliAdapterTest,Stage04LoopbackHttpAdapterTest test` | RED (expected) | Production compilation reaches 185 sources; target test compilation is blocked by absent `LoopbackHttpServer` and injected `CodeMdCli` constructor, as specified by the RED contract. |
| `mvn -Dtest=Stage04TargetCliAdapterTest,Stage04LoopbackHttpAdapterTest test` (loopback-enabled) | GREEN | 4 tests, 0 failures, 0 errors: target CLI 2/2 and loopback HTTP 2/2. |
| `mvn -Dtest='Stage04*Test' test` (loopback-enabled) | Scoped regression | 62 tests total: 61 passed; the one failure is independent `Stage04ImprovementTest`, which still requires the absent CandidateReviewStore/finding public seam. Adapter tests remain 4/4 green. |
| `git diff --check` | GREEN | No whitespace errors. |

## Decisions

- Target paths will parse configuration/request bytes only; they do not create a source registry, provider, archive service, or legacy POC generation path.
- Candidate/document reads are an explicit separate `CandidateArtifactReader`;
  neither adapter will discover an archive or reflect into test fakes.
- HTTP's top-level `status` is transport state and is set after flattened
  Candidate-reference fields, so `UNPUBLISHED_CANDIDATE` cannot mask a run's
  `QUEUED`/`RUNNING`/`COMPLETED`/`FAILED` state.
- The sandbox rejects loopback socket binding. HTTP verification therefore ran
  under the narrowly approved local-loopback Maven permission; production
  still rejects any non-loopback bind before opening a socket.

## Blockers

- The only remaining Stage04 selector RED is outside this slice:
  `Stage04ImprovementTest` requires `CandidateReviewStore`,
  `CandidateReviewFinding`, `CandidateReviewFindingDraft`, and
  `ReviewFindingSet`. No adapter behavior is implicated and none was changed.

## Exact next action

- Hand off the completed adapter implementation and the independent review
  seam RED to the parent agent. Do not add review-store behavior in this task.
