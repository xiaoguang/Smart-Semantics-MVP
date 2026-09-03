# Progress: Fact candidate proof-evidence GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: M1 proof-evidence handoff only: preserve public evidence payloads and require complete candidate evidence closure.
- Approved inputs: Published `04-proven-code-facts.md` M1/M2 contract; existing `FactCandidateProofEvidenceHandoffTest` RED.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Read the scoped instructions, published M1/M2 contract, both implementation plans, RED test, and progress template.
- Confirmed the worktree contains unrelated pre-existing changes; they will be preserved.
- Reproduced the required RED: `FactCandidateProofEvidenceHandoffTest` ran 1 test with 1 assertion failure.
- Traced the failure to two local M1 omissions: `EvidenceNode` discards validated source/rule payloads, and `FactCandidateEnumerator` excludes call-site and local-origin subjects from its evidence requests.
- Implemented the minimal correction: Evidence nodes now retain mutually exclusive `SourceExcerptV1` or immutable rule-application payloads, and candidate closure binds call-site plus every local origin.
- Verified that local origins may belong to either the code-structure or data-flow graph; the enumerator resolves their unique public node owner before requesting evidence. The existing fixture already has the required public evidence.
- Verified that a shared local origin produces one subject-evidence binding, preserving the exact subject set and existing candidate identity framing.

## Current state

- The M1 proof-evidence handoff correction is ready for parent review; no commit or push was made.

## Changed files

- `progress/fact-candidate-proof-evidence-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateProofEvidenceHandoffTest test` | RED | 1 test, 1 failure: missing `call-node` and `java-parameter-symbol` evidence subjects. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateProofEvidenceHandoffTest test` | GREEN | 1 test, 0 failures. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=<15 exact FactCandidate source classes> test` | GREEN | 17 tests, 0 failures/errors. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | BLOCKED_OUTSIDE_SCOPE | 26 pre-existing formatting violations in unrelated files. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=<3 changed M1 Java files> spotless:check` | GREEN | Scoped formatter check passed. |
| `git diff --check` plus no-index checks for each owned untracked file | GREEN | No whitespace errors. |

## Decisions

- Keep the implementation confined to `analysis/fact/candidates/`; do not begin M2 Proof work or alter public artifact wire/schema.
- Replace the EvidenceNode boolean projection with exclusive nullable `SourceExcerptV1` / public rule-application record branches; existing candidate identity already serializes all evidence bindings.
- Resolve a local-origin subject through its unique persisted public graph owner instead of guessing it is always a data-flow node.
- Deduplicate repeated local-origin subjects before closing Evidence so a candidate has exactly one binding per subject.

## Blockers

- The repository-wide Spotless check remains red for unrelated pre-existing formatting violations; the scoped check for this slice is green.

## Exact next action

- Parent task reviews the owned uncommitted files and decides the integration/commit path.

## Resume checks

- Preserve all pre-existing worktree changes and run Maven commands serially with the project toolchain and offline mode.
