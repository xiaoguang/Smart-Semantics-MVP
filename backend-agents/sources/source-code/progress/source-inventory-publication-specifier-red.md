# Progress: source-inventory-publication-specifier-red

- Status: COMPLETE
- Agent role: source-inventory publication test author
- Model: gpt-5.6-luna / xhigh test design under the published Sol/ultra design
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Establish the public M3 source-inventory publication seam. The test will use real canonical module and analysis-step stores, reopen M1/M2 publications, and require exactly the three semantic source-inventory files plus the receipt-last analysis-step publication. It does not implement M1/M2 serialization, capture, discovery, runtime recovery, or AST analysis.
- Approved inputs: `docs/DESIGN.md` and `docs/analysis-steps/01-verified-source-inventory.md` §§3, 8.1–8.1.1; the published shared artifact store commit `0091764`.
- Current branch/worktree: `codex/source-analysis-verified-inventory` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Read the source-inventory module contract and confirmed M3 must fresh-reopen M1/M2 module publications, produce exactly `source-input.json`, `verified-snapshot.json`, and `source-inventory.jsonl`, then delegate receipt-last publication to `CanonicalAnalysisStepArtifactStore`.
- Established the expected RED first: the missing M3 seam failed before any source, model, or build action.
- Implemented the path-free input/reference seam and the M3 projection. It fresh-reopens M1/M2, validates basic request/frozen-source closure and source-file/shard closure, installs exactly three M3 payloads, then delegates the public receipt-last publication.

## Current state

- The bounded M3 projection is green. Its synthetic upstream fixture is deliberate; M1/M2 production writers and the top-level inventory executor remain separate work.

## Changed files

- `progress/source-inventory-publication-specifier-red.md`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryPublicationSpecifierTest.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/AnalysisInputArtifactReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/VerifiedSourceInventoryPublication*.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| preflight | PASS | Design requires a real M1 → M2 → M3 → public-step path and exactly three semantic payloads. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=VerifiedSourceInventoryPublicationSpecifierTest test` | RED → PASS | Initial missing seam failed as expected; final selector has 2 passing tests. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=VerifiedSourceInventoryPublicationSpecifierTest,CanonicalAnalysisStepArtifactStoreTest test` | PASS | 4 direct tests, no failures/errors/skips. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | 121 Java files are formatted. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The test constructs small canonical M1/M2 module publications as frozen upstream inputs. This isolates M3 projection from the later M1/M2 writers while still proving fresh-reopen and receipt-last storage behavior.

## Blockers

- None.

## Exact next action

- Begin the M1 module-publication RED. Do not make a complete-step claim until M1/M2 are written and one executor connects the real registered capture to M1 → M2 → M3.

## Resume checks

- Read this file, confirm `origin/main` contains the matching M3 implementation commit, and run only the source-inventory selectors before changing production code.
