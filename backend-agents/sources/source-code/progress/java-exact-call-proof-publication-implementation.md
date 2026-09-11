# Progress: exact-call M2 proof publication implementation

- Status: COMPLETE
- Agent role: Terra/xhigh implementation
- Model: gpt-5.6-terra/xhigh
- Started: 2026-09-08
- Last updated: 2026-09-08
- Scope: M2 v3 proof-decision publisher, typed reader, and strictly necessary M2 artifact-policy registration replacement.
- Approved inputs: Published Step04 v3 contract, existing M2 writer/reader/policy seams, and Luna's forthcoming frozen public-seam RED.
- Current branch/worktree: `codex/source-analysis-proof-and-flow-materials`; `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`.

## Completed

- Created this progress record before any eventual production edit.
- Read the current M2 writer, typed fresh reader, engine policy registration, and Step04 §8.0.2 v3 replacement contract.
- Mapped the bounded replacement: both M2 seams move their schema/module constants to v3; both M1 descriptor gates require `fact-candidate-set-v3`; the engine accepts only the M2 v3 proof-decision payload. Their existing generic full-union JSON serialization/parsing and candidate/atom/Fact/Proof/disposition/gap closure validation already carry the exact candidate kind without a second wire shape.
- Reconfirmed the exact M2 v3 RED, then replaced only the prepared v2 constants/gates and M2 engine registration. The exact v3 publication/fresh-reader selector is GREEN.
- Finished the exact three-file formatter/check and the direct five-class postformat regression after Luna corrected the stale v3 aggregate accounting assertion.

## Current state

- `ProofDecisionSetModulePublisher` and `PersistedProofDecisionSetReader` now accept and produce only `proven-code-facts-proof-decision-set-v3`, require M1 `fact-candidate-set-v3`, and use module version `v3`. The engine installs only the M2 v3 artifact policy.
- The existing generic serialization/typed closure validation fresh-reopens the complete v3 union: exact candidates participate in Facts, Proofs, and dispositions; only boundary candidates retain external-effect gaps. No M3/Step05 behavior or compatibility reader was added.

## Changed files

- `progress/java-exact-call-proof-publication-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/proofs/PersistedProofDecisionSetReader.java`
- `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java` (M2 registration only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -t .mvn/toolchains.xml -Dtest=ProofDecisionSetModuleArtifactTest#publishesAndFreshReopensTheV3ExactCallProofDecisionSet test` | RED (reconfirmed) | 1 test, 0 failures, 1 error: publisher rejects v3 M1 candidate at its old v2 schema gate. |
| Same exact selector after the bounded v3 replacement | PASS | 1 test, 0 failures, 0 errors, 0 skips; three exact Facts and twelve exact CLOSED Proofs fresh-reopen through the typed M2 seam. |
| Initial five-class selector | Expected test-only RED | 8 tests, 1 failure, 0 errors: the old v3 artifact test kept a boundary-only `codeFacts=2` assertion while the complete v3 union contained 6; root approved Luna's bounded oracle correction. |
| `mvn -o -t .mvn/toolchains.xml -DspotlessFiles=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/proofs/ProofDecisionSetModulePublisher.java,/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/analysis/fact/proofs/PersistedProofDecisionSetReader.java,/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java spotless:apply` | PASS | Exactly 3 production files selected; 0 changed and 3 already clean. |
| Same exact absolute three-file `spotless:check` | PASS | Exactly 3 production files selected; 0 need changes. |
| Same five-class selector after formatting and corrected frozen test | PASS | 8 tests, 0 failures, 0 errors, 0 skips. |
| `git diff --check` on the three owned production files; trailing-whitespace scan including this progress file | PASS | No whitespace errors. |

## Decisions

- Read and prepare against v3 only. No v2 reader, alias, compatibility path, M3, Step05, or additional publication behavior is in scope.
- Reuse existing generic model serialization only after verifying it represents the full v3 proof-decision union and fresh-reader validation exactly.

## Blockers

- None for this bounded M2 v3 publication slice.

## Exact next action

- Release Maven. Do not modify M3, Step05, schemas beyond this M2 v3 replacement, frozen tests, or compatibility behavior without a separate approved RED/GREEN brief.

## Resume checks

- Preserve the frozen source, candidate, Fact/Proof/disposition/gap closure and no-compatibility requirements; do not edit production or run Maven until the gate opens.
