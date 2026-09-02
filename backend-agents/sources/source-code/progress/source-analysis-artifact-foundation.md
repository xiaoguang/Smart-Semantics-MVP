# Progress: source-analysis-artifact-foundation

- Status: IN_PROGRESS
- Agent role: Delivery orchestrator
- Model: gpt-5.6-sol / ultra (design authority), gpt-5.6-luna / xhigh (RED), gpt-5.6-terra / xhigh (GREEN)
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Delivery 3 — semantic artifact identity, canonical JSON and durable analysis-step/run storage only.
- Approved inputs: User-approved Source Code Analysis Agent naming refactor and complete implementation plan; `origin/main` at `a1297f4`.
- Current branch/worktree: `codex/source-analysis-artifact-foundation` at `/private/tmp/linguan-source-analysis-artifact-foundation`

## Completed

- Created a clean delivery branch from the merged semantic Wire Reset on `origin/main`.
- Started the Sol/ultra design-authority brief before implementation.
- Published the required docs-only seam clarification and fast-forwarded this
  branch to `origin/main` commit `44c7f7c` before implementation.
- Established and closed the first canonical JSON RED/GREEN vertical behavior:
  exact UTF-8 byte-ordered object encoding through `CanonicalJsonCodec` and
  defensive immutable byte transport through `ImmutableBytes`.
- Established and closed the second codec behavior: `parseCanonical` rejects
  valid-but-noncanonical bytes rather than creating another content identity.
- Established and closed the first typed-identity behavior: `ArtifactId`
  accepts only canonical safe content IDs and preserves their exact wire form.
- Established and closed the first fixed-prefix identity behavior:
  `AnalysisRunId` rejects other content-ID prefixes.
- Established and closed the closed-registry behavior: `AnalysisStepKey` maps
  semantic keys to their immutable order, directory and receipt names while
  rejecting historical numeric aliases.

## Current state

- Canonical encoding/parse identity, generic `ArtifactId`, and fixed-prefix
  `AnalysisRunId` and the `AnalysisStepKey` directory registry are green.
  Remaining digests/fixed-prefix IDs, step addresses, policy loading and all
  stores remain unimplemented.

## Changed files

- `backend-agents/sources/source-code/progress/source-analysis-artifact-foundation.md`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/CanonicalJsonCodec.java`
- `backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/ImmutableBytes.java`
- `backend-agents/sources/source-code/src/test/java/org/sourceanalysis/app/artifact/CanonicalJsonCodecTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git -C /private/tmp/linguan-source-analysis-artifact-foundation status --short` | PASS | Clean branch created from `a1297f4`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalJsonCodecTest test` | PASS | 2 tests, 0 failures/errors after encoding and parser GREENs. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ArtifactIdTest test` | PASS | 1 test, 0 failures/errors after typed-ID GREEN. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AnalysisRunIdTest test` | PASS | 1 test, 0 failures/errors after fixed-prefix ID GREEN. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AnalysisStepKeyTest test` | PASS | 1 test, 0 failures/errors after closed step-registry GREEN. |
| `mvn -t .mvn/toolchains.xml spotless:apply` | PASS | First-slice source/test formatting applied. |
| `git diff --check` | PASS | No whitespace errors after the first GREEN. |

## Decisions

- Preserve the published eight-step design; this delivery may implement only the reusable artifact foundation it already specifies.
- Do not restore, import, wrap, or translate the deleted POC/numbered-stage implementation.

## Blockers

- None.

## Exact next action

- Add the next single RED for raw `Sha256Digest` validation, then finish the
  remaining fixed-prefix IDs and composite step address.

## Resume checks

- Read this file, inspect `git status --short`, confirm the base is `origin/main` commit `a1297f4` or its descendant, then read the artifact-foundation design brief before editing code or tests.
