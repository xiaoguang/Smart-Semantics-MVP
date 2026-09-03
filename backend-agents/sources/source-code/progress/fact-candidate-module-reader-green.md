# Progress: fact candidate module reader green

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Implement only the typed persisted M1 candidate-set reader after the public RED fixture satisfies the M1 same-controls contract.
- Approved inputs: Scoped `AGENTS.md`; `docs/analysis-steps/04-proven-code-facts.md` M1 and module-artifact wire; both implementation plans; current canonical module store, M1 publisher, typed input reader, and Luna RED test.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` / `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Read the required scoped rules, M1/module-artifact contract, implementation plans, M1 publisher/store seams, and Luna `FactCandidateModuleReaderTest` RED.
- Confirmed the reader class is intentionally absent and the RED uses a real canonical store and published module artifact.
- Identified a contract contradiction in the positive test: its publisher uses independently-created policy controls, while `FactCandidateInputs` carries the fixture's controls. The M1 contract requires exact same controls.

## Current state

- Complete. The M1 typed input, publisher, and reader use one strict seven-reference closure: five graph roots plus the exact capability-report and entry-points artifacts. The publisher no longer accepts caller-provided upstream references or controls; the reader reopens and compares both receipt and envelope against that typed closure before re-enumerating the candidate set.

## Changed files

- `progress/fact-candidate-module-reader-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateSetReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateInputs.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSetModulePublisher.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Required-contract/source inspection | PASS | Reader seam absent; RED is present; the initial positive fixture had inconsistent controls. |
| Parent fixture correction confirmation | PASS | Module reader test now reuses fixture controls and policy registry. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateModuleReaderTest test` | PASS | 1 test, 0 failures/errors/skips; fresh reopen succeeds and persisted payload tamper fails closed. |
| M1 combined selector | BLOCKED | `FactCandidateMissingPathTest` errors before reader execution with `PROOF_PACK_REFERENCE_BROKEN` from the upstream input reader at line 109. |
| Scoped Spotless + `git diff --check` | PASS | Owned reader format and current diff whitespace are clean. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactUpstreamTest test` | PASS | 1 test, 0 failures/errors/skips; typed inputs expose the exact seven references, strict publisher derives them, and reader rejects a valid decoy persisted through the lower store. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactUpstreamTest,FactCandidateModuleArtifactTest,FactCandidateModuleReaderTest test` | PASS | 3 tests, 0 failures/errors/skips. |
| Complete M1 direct selector | PASS | 16 tests, 0 failures/errors/skips across identity, inputs, exact joins, integrity, registry, module publication, strict upstream, and persisted reader coverage. |
| Scoped Spotless + `git diff --check` after strict API migration | PASS | Production and directly affected tests format cleanly; no whitespace errors. |

## Decisions

- The reader will fresh-reopen only `ModulePublicationReference`, reconstruct the expected typed set from `FactCandidateInputs + FactRegistry`, and compare all persisted material exactly; it will never accept a path, draft, or raw JSON input.
- Controls drift must fail closed; it cannot be relaxed to make the current fixture green.
- The publisher API is now strict `publish(destination, inputs, candidateSet)`: it has no arbitrary upstream or control parameters and therefore cannot publish a decoy reference.

## Exact next action

- Hand the completed M1 candidate-module reader seam to the parent for the remaining M1 acceptance review; do not begin M2 Proof work from this task.

## Resume checks

- Re-read this file; inspect the strict publisher and reader tests; confirm the module artifact upstream list and controls still equal `FactCandidateInputs` before a future change.
