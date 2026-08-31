# Progress: Stage 04 CLI and loopback HTTP adapter RED tests

- Status: COMPLETE
- Agent role: Stage 04 public-seam TDD test author
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add the smallest public adapter contract selectors for the target CLI and JDK loopback HTTP surface. Cover one shared `CodeToMarkdownAgent` seam, canonical JSON/exit-code behavior, HTTP authentication/contract/idempotency/queue rules, and Java/CLI/HTTP Candidate identity parity. Do not add production or design code.
- Approved inputs: `AGENTS.md`; `docs/stages/04-runtime-archive-trace-recovery.md` §§5.2, 13, 14, 16; existing Stage 04 public core and legacy CLI tests; fake/recording `CodeToMarkdownAgent` only.
- Current branch/worktree: Shared dirty worktree; preserve unrelated and parallel changes.

## Completed

- Read the repository, backend-agent, and GitHub-code scoped instructions.
- Read Stage 04 §§5.2, 13, 14, and 16, including the fixed TOML fields, target command names, loopback HTTP routes, auth/limit/status contracts, and adapter parity gate.
- Inspected the existing legacy `CodeMdCli`, Stage 04 public `CodeToMarkdownAgent`, public-core fixture, and direct adapter-related tests. Confirmed no target CLI or HTTP production adapter exists yet.
- Created this progress record before modifying tests.

## Current state

- Added the two bounded selectors. The target CLI test invokes `generate`, `validate`, and `trace` through one injected recording public Agent, and routes read-only `candidate` through a separate injected `CandidateArtifactReader`; it checks canonical JSON, no path/secret leakage, and 0/1/2 exit semantics. The HTTP test uses JDK `HttpClient` against an ephemeral loopback port and checks Java/CLI/HTTP identity parity, bearer auth, exact generation body, idempotency conflict, 202 run lifecycle, candidate/Markdown/Trace GETs, disabled CORS, loopback-only bind, request/media/path/size rejection, and one-worker bounded queue behavior. Reader calls are asserted on the reader fake, never on Agent operations.

## Changed files

- `progress/stage04-adapters-tests.md`
- `src/test/java/com/linguan/codemd/stage04/Stage04TargetCliAdapterTest.java`
- `src/test/java/com/linguan/codemd/stage04/Stage04LoopbackHttpAdapterTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage04TargetCliAdapterTest test` | PASS | 2 tests ran and compiled successfully; both target CLI paths pass, including the corrected reader expectation that validation resolves the Candidate and the subsequent `candidate` command resolves it again (`[candidate,candidate]`). |
| `mvn -Dtest=Stage04LoopbackHttpAdapterTest test` | RED (behavioral) | 2 tests ran and compiled successfully. Java/CLI parity currently returns exit 2 with `M8_REQUEST_INVALID`; the bounded HTTP test also receives `LOOPBACK_BIND_REQUIRED` from the server constructor. |

## Decisions

- The target CLI test invokes the target command names `generate`, `validate`, `trace`, and `candidate` with `--config` plus IDs; it must not use legacy `--manifest`, `--snapshot-root`, or `CandidateArchiveService`.
- Adapter tests inject one fake `CodeToMarkdownAgent` plus one fake `CandidateArtifactReader`; production adapters must translate requests/responses only and never call legacy POC services directly. Candidate/Markdown reads must use the reader seam, while validation still resolves a Candidate through that reader before delegating to the Agent. The corrected operation assertion is exactly Agent `generate/validate/trace`; candidate read is asserted on the reader fake.
- CLI and HTTP output assertions use the Stage 04 package-private public records and canonical JSON checks; tests do not depend on generated archive internals.
- HTTP requests use JDK `HttpClient` against an ephemeral `127.0.0.1` port. The server constructor is expected to reject non-loopback binding before opening a socket, require an env-var name (not a secret value), and expose only the documented typed routes.

## Blockers

- The detailed Stage 04 design specifies wire behavior but does not yet name concrete adapter classes. The tests use `CodeMdCli(CodeToMarkdownAgent, CandidateArtifactReader)` and a package-local `LoopbackHttpServer(Path, CodeToMarkdownAgent, CandidateArtifactReader, Map<String,String>)` as narrow seams. The implementing agent may preserve these names or provide equivalent production seams with the same observable contract, but must keep mutation on the public Agent path and candidate/Markdown reads on the explicit reader seam rather than a legacy archive service.

## Exact next action

- Parent production agent should implement/adapt the two named seams, then run this exact selector. Keep the test-only fake/recording agent and no-live/no-network boundary intact.

## Resume checks

- Re-read this file, run `git status --short`, and ensure only this progress file and the two owned test sources changed by this task. Do not edit production or design files.
