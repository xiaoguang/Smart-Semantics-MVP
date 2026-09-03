# Progress: M6 public graph wire version gates GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Upgrade the approved M4/M5/M6 public wire version bundle and fail-closed canonical readers/registries only.
- Approved inputs: `AGENTS.md`, ProgramGraphs M4/M5/M6 version contract, backlog P5/P7, and Luna's `ProgramGraphPublicWireTest` RED.
- Current branch/worktree: `codex/source-analysis-program-graphs` / `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the scoped operating contract, published program-graph version contract, implementation backlog, and Luna's public-seam RED record.
- Upgraded the M5 draft schema to `program-graphs-evidence-graph-draft-v3`; its publisher, parser, persisted reader, and module registry all bind the same constant and therefore reject v2 without a compatibility path.
- Upgraded M6 public data-flow, evidence, and index schemas to `program-graphs-data-flow-graph-v2`, `program-graphs-evidence-graph-v3`, and `program-graphs-graph-index-v2`.
- Updated the canonical artifact contract registry to accept only this new M4/M5/M6 version bundle.
- Migrated only the four directly affected test-local canonical policy/payload fixtures so valid new artifacts can be installed; no fixture accepts the former public bundle.

## Current state

- The public version gate is GREEN. Old data-flow v1, evidence v2, index v1, and evidence-draft v2 no longer occur in production or valid test registry fixtures; the canonical registry rejects them rather than translating them.

## Changed files

- `progress/m6-version-wire-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/graph/EvidenceGraphDraft.java`
- `src/main/java/org/sourceanalysis/app/analysis/graph/ProgramGraphSetPublicationSpecifier.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphPublicWireTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicationSpecifierTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphGapProjectionTest.java`
- `src/test/java/org/sourceanalysis/app/artifact/ProgramGraphsAnalysisStepArtifactStoreTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | RED | 1 test, 1 expected version mismatch: actual v1/v2/v1 versus required v2/v3/v2. |
| `mvn -t .mvn/toolchains.xml -o spotless:apply` | PASS | No files changed. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphPublicWireTest test` | PASS | 1 test, 0 failures/errors/skips; public values are v2/v3/v2. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=EvidenceGraphBuilderTest test` | PASS | 3 tests, 0 failures/errors/skips; M5 build/persist/reopen remains valid. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsPublicationSpecifierTest test` | PASS | 1 test, 0 failures/errors/skips after direct policy-fixture migration. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphGapProjectionTest test` | PASS | 1 test, 0 failures/errors/skips after direct policy-fixture migration. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ProgramGraphsAnalysisStepArtifactStoreTest test` | PASS | 1 test, 0 failures/errors/skips after direct canonical payload/policy fixture migration. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Keep filenames, graph count, M1--M3 schemas, and graph meaning unchanged.
- Reject old or mixed schema bundles rather than adding compatibility reading or migration.
- Update only direct test-local policy literals from v1/v2/v1 to v2/v3/v2; this aligns the fixture's valid registry and does not make old wire acceptable.

## Blockers

## Exact next action

Return this bounded GREEN slice to the parent; the next M4/M5/M6 capability slice may proceed from the new v3/v2 wire bundle.

## Resume checks

- Re-read this file and `progress/m6-version-wire-tests.md`.
- Preserve all unrelated dirty worktree changes.
- Run only the assigned targeted Maven selectors.
