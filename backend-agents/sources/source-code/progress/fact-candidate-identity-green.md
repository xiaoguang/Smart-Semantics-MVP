# Progress: Fact candidate identity GREEN

- Status: COMPLETE
- Agent role: Terra/xhigh production implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-03T12:08:35Z
- Last updated: 2026-09-03T12:11:01Z
- Scope: Make `FactCandidateSet` identity sensitive to every wire-visible candidate semantic, using framed versioned bytes only.
- Approved inputs: Scoped AGENTS.md; published proven-code-facts M1 contract; existing `FactCandidateIdentityTest` RED.
- Current branch/worktree: `codex/source-analysis-proven-code-facts` at `/private/tmp/linguan-source-analysis-proven-code-facts/backend-agents/sources/source-code`

## Completed

- Read scoped instructions, both implementation plans, the M1 contract, the existing identity RED, and the current implementation.
- Replaced delimiter-based candidate-set identity material with a versioned, length-framed binary encoding.
- Included every wire-visible candidate field: roots, full candidate fields, ordered argument bindings and Java-local origins, evidence bindings, required atoms, and not-applicable dispositions.
- Preserved canonical root/candidate/disposition ordering and all validation behavior.

## Current state

- `FactCandidateSet` now computes identity only from versioned, unambiguous length-framed bytes. No registry, enumeration, external-boundary, fixture, or schema behavior changed.

## Changed files

- `src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java`
- `progress/fact-candidate-identity-green.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=FactCandidateIdentityTest test` | PASS | 1 test; 0 failures, errors, or skips. The initial run was the expected RED with 3 missing semantic-identity assertions. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/fact/candidates/FactCandidateSet.java spotless:check` | PASS | Owned production file conforms to formatting. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- Preserve canonical sort behavior and all existing candidate validation.
- Do not change registry, enumerator, test/fixture, design, POM, or any external-boundary semantics.
- Identity uses an explicit identity-format version, count-prefixed UTF-8 strings, fixed-width integers, and a null presence byte. It does not concatenate delimiter-separated fields.

## Blockers

- None.

## Exact next action

- Return the bounded GREEN result to the parent agent for inclusion in the M1 gate.

## Resume checks

- Re-read this file; inspect `FactCandidateSet`; rerun only `FactCandidateIdentityTest` if later edits touch its identity behavior.
