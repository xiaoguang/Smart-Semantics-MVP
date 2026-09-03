# Progress: Fact candidate exact path GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementer
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03
- Last updated: 2026-09-03
- Scope: Replace the temporary raw-JSON Fact M1 candidate seam with the formal persisted ApplicationDiscovery and complete ProgramGraphs reader, exact Java-boundary join, registry-governed candidates, and scoped not-applicable dispositions. No Proof, publication, SQL/external-effect inference, test, POM, or design changes.
- Approved inputs: `docs/DESIGN.md`; `docs/analysis-steps/04-proven-code-facts.md`; both implementation plans; published ProgramGraphs v3/v2 boundary wire; `FactCandidateExactPathTest`; `ProgramGraphsPublicFixture`.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`.

## Completed

- Read the scoped rules, target architecture, Fact M1 contract, implementation plans, prior delivery/RED/retirement progress, typed public fixture, current RED test, and temporary raw-JSON candidate implementation.
- Re-ran the required public M1 selector. It reaches test compilation and fails only because the formal typed M1 records, reader, registry, disposition collection, and boundary accessor are absent.
- Replaced the raw-JSON candidate enumerator/set with the typed `PersistedFactCandidateInputReader`, `FactCandidateInputs`, `FactRegistry`, `FactCandidateEnumerator`, `FactCandidateSet`, and stable reference failure type. The implementation fresh-reopens the source/discovery/five-graph lineage, performs only exact Java-boundary path joins, and creates scoped dispositions rather than cross-product candidates.

## Current state

- Sol/debug located the constructor failure in this reader's controls comparison: the persisted JSON was parsed as generic `ArtifactReference` while the controls hold `ArtifactPolicyRegistryReference`, so equal two-field identities compared as distinct record types. The reader now parses the field as the matching typed reference while retaining exact two-field JSON validation.
- The required selector is GREEN after that smallest typed correction: the canonical two-entry/two-boundary fixture reaches the public seam and produces two exact candidates with no scoped failures.
- Ran the required changed-file formatting and whitespace checks. No format or whitespace errors remain in the owned production files or this progress record.

## Changed files

- `progress/fact-candidate-exact-path-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateReferenceException.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactRegistry.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateInputs.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/PersistedFactCandidateInputReader.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateEnumerator.java`
- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Confirmed pre-existing Fact M1 work is uncommitted and preserved. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest test` | RED (expected) | Test compilation reports only absent `FactCandidateInputs`, `PersistedFactCandidateInputReader`, `FactRegistry`, `notApplicableDispositions()`, and `boundaryNodeId()`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest test` | BLOCKED | Main and test compilation succeed (250 main / 55 test sources); the sole test errors before M1 at `ProgramGraphsPublicFixture.create`: `test store root must be an existing empty non-symlink directory` from `FileSystemRunStoreHandle.openEmptyTemporaryDirectory`. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateExactPathTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped. The typed persisted reader and exact entry-boundary join both execute. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/fact/candidates/... spotless:check` | PASS | Spotless 3.10.1 accepted all six owned candidate production files. |
| `git diff --check` plus no-index checks for owned untracked files | PASS | No whitespace errors. |

## Decisions

- Candidate enumeration proves only a frozen-Java boundary invocation path. It must not infer Mapper/XML/SQL or any external effect.
- Formal inputs are fresh-reopened public publications; drafts, raw JSON inputs, filesystem paths, and canonical-value parsing remain prohibited.

## Blockers

- None. The initial fixture symptom was traced to this reader's typed controls comparison and corrected without weakening validation.

## Exact next action

- Parent may integrate this M1 GREEN work. Do not start Proof building, candidate persistence/publication, or mutation testing from this work unit.

## Resume checks

- Re-read this file, confirm the formal Fact M1 types remain isolated to `analysis/fact/candidates`, and re-run only the focused selector before any further candidate work.
