# Progress: M2 physical call-site and owner identity GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Repair M2.1 physical call-site identity and owner-sensitive call-graph identity only.
- Approved inputs: Scoped `AGENTS.md`; M2.1 design; ProgramGraphs backlog P3; M2 owner-union review; M2 physical-identity RED tests.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Started after Luna established two expected public-seam RED assertions.
- Reproduced the expected selector result: 18 tests, 2 assertion failures, 0 errors/skips.
- Implemented the minimal M2.1 identity correction in `CallGraphBuilder.java`: a call-site node now commits to its caller signature and complete exact source locator; the graph ID commits to sorted full node and edge material as well as Gap material.
- Applied Spotless and reran the same public selector: all 18 tests pass.

## Current state

- The corrected identity material is deterministic: call-site identity includes caller signature plus the full exact source locator; graph identity serializes sorted entry IDs, node content, edge content, and Gap content. This leaves all public payload fields unchanged while making all of that existing reader-visible content identity-relevant.

## Changed files

- `progress/m2-physical-identity-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java` (Spotless-only formatting of the existing Luna-owned RED file; no test assertion was changed by this work item)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | EXPECTED RED | 18 tests; 2 assertion failures: same-shaped physical call collision and owner-insensitive graph ID. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | Formatter changed only the already-dirty Luna test file's layout; production file was already formatted. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CallGraphBuilderTest test` | PASS | 18 tests, 0 failures/errors/skips. |
| `git diff --check -- src/main/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilder.java src/test/java/org/sourceanalysis/app/analysis/graph/CallGraphBuilderTest.java progress/m2-physical-identity-green.md` | PASS | No whitespace errors. |

## Decisions

- No public node/edge fields, candidate-resolution behavior, Gap union, M3--M6, design, POM, or tests will be changed in this slice.

## Blockers

- None.

## Exact next action

- Parent may run the M2 dependent selectors, then integrate this completed slice into the ProgramGraphs stage worktree.

## Resume checks

- Re-read the M2.1 design, two M2 progress records, and the target test before changing production code.
- Run only `CallGraphBuilderTest` plus formatting and diff checks.
