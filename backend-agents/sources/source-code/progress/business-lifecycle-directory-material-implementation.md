# Progress: business lifecycle directory/material GREEN implementation

- Status: COMPLETE
- Agent role: GREEN implementation
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-15
- Last updated: 2026-09-15
- Scope: Focused Step07 production implementation for the approved lifecycle-directory correction, plus this implementation progress record only.
- Approved inputs: `docs/plans/business-process-discovery-and-reconstruction-change-design.md` sections 1-5; committed RED tests `5ecc6d8`; frozen fixtures and existing reviewed Activity/source material; no model, network, source scan, public-contract, prompt, schema, publisher, or test change.
- Current branch/worktree: nested Git checkout `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`, branch `codex/business-lifecycle-readable-implementation`; preserve pre-existing untracked `docs/research/`.

## Completed

- Read repository, backend-agent, and source-code scoped instructions.
- Read the approved Step07 change-design sections 1-5, committed RED-test progress record, template, and test commit `5ecc6d8`.
- Created this progress file before production edits.
- Reproduced the four intended RED failures, implemented the focused discovery changes, and verified the complete focused class is green.
- Confirmed the changed production file passes the scoped Spotless check and the final patch has no whitespace errors.

## Current state

`DefaultBusinessProcessDiscovery` now retains reviewed rules in cards, accepts only distinct `(activityId, variant)` candidate uses, emits one complete Activity body per Activity ID, creates a saved-source-only directory, and rejects orphan `PROCESS_MEMBER` dispositions. The focused class is green.

## Changed files

- `progress/business-lifecycle-directory-material-implementation.md`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessDiscoveryTest test` | RED | 15 tests, 4 intended failures, 0 errors; independently reproduced after reading the committed test handoff. |
| `mvn -t .mvn/toolchains.xml -Dtest=BusinessProcessDiscoveryTest test` | PASS | 15 tests, 0 failures, 0 errors, 0 skipped. |
| `mvn -t .mvn/toolchains.xml -DspotlessFiles=src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java spotless:check` | PASS | Scoped production formatting check succeeded. |
| `git diff --check` | PASS | No whitespace errors in the final patch. |

## Decisions

- Made the minimal change in `DefaultBusinessProcessDiscovery`; retained all existing public records, prompts, schemas, publisher behavior, and test files.
- Treat physical source lines as raw text separated deterministically on LF, CRLF, or CR, retaining content without normalization and selecting at most eight lines.
- Keep candidate-use completeness in the candidate object while using a distinct, UTF-8-sorted Activity-ID projection for complete body and source-directory material.

## Blockers

- None.

## Exact next action

- No further implementation action; commit only the production class and this progress file.

## Resume checks

- Re-run `git status --short`, inspect this progress file, and run the focused Maven class test.
