# Progress: activity explanation prompts

- Status: COMPLETE
- Agent role: Root coordinator; TDD continuation for Step 06
- Model: gpt-6-astra / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Replace the placeholder ActivityExplainer instruction with versioned Chinese DRAFT and REVIEW instructions derived from the approved prompt reference. The model remains limited to the clean packet and returns JSON only. No live Provider, source scan, or process reconstruction.
- Approved inputs: Scoped `AGENTS.md`; `docs/references/semantic-interpretation-prompts.md` sections 1–3; Step 06 M2 contract; deterministic scripted Provider only.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` in `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Verified that the existing implementation uses a single generic placeholder instruction for both DRAFT and REVIEW; that is insufficient for a real Luna/high business-language quality check.
- Added one public-seam RED proving the placeholder English instruction fails to deliver the approved Chinese untrusted-input, short-ref and business-explanation tasks.
- Added two versioned classpath text resources and a small package-local catalog. The Provider now receives distinct DRAFT and REVIEW Chinese instructions while the model data remains the existing clean packet.

## Current state

- The prompt boundary is green. This work intentionally does not invoke a real Provider or change the activity response schema.

## Changed files

- `progress/activity-explanation-prompts.md`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPromptCatalog.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainer.java`
- `src/main/resources/org/sourceanalysis/app/analysis/interpretation/activity/activity-draft-v1.txt`
- `src/main/resources/org/sourceanalysis/app/analysis/interpretation/activity/activity-review-v1.txt`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityPromptContractTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| Activity explanation checkpoint selectors | PASS | 3 tests; 0 failures/errors/skips. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityPromptContractTest test` | EXPECTED RED | 1 assertion failure: both calls carried the placeholder English instruction. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityPromptContractTest,ActivityExplainerTest,ActivityExplanationBudgetTest,ActivityExplanationCheckpointTest test` | PASS | 4 tests; 0 failures/errors/skips. |
| Scoped Spotless and `git diff --check` | PASS | No formatting or whitespace findings on this slice. |

## Decisions

- Keep prompt content as versioned classpath resources rather than spreading opaque string constants through Java.
- Do not embed paths, hashes, artifact/run identities, credentials, byte budgets, or raw source bindings in either prompt.

## Blockers

- None.

## Exact next action

- A future real-Luna preflight can use the resource version in its input fingerprint; it must first receive explicit product-call authorization.

## Resume checks

- Re-read this file, check shared status, and run only the prompt selector before further changes.
