# Progress: activity direct-entry context checkpoint

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-11
- Last updated: 2026-09-11
- Scope: Verify the active ActivityExplainer receives and preserves a direct-entry material packet
  containing an HTTP handler plus one safe direct service-method context. Use a scripted Provider;
  no customer model call or business inference is added here.
- Approved inputs: direct-entry BusinessMaterialBuilder output, existing ActivityExplainer public
  seam, guarded Java fixture and current business-first design.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at
  `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Confirmed the existing ActivityExplainer already owns complete DRAFT+REVIEW parsing, short-ref
  validation and checkpoint persistence. The quality test must exercise that seam rather than add
  another activity format.
- Added and passed the direct-entry checkpoint: a scripted DRAFT and REVIEW each received the two
  clean snippets, while the reviewed activity retained purpose, objects, conditions, steps,
  results, questions, limitations and both permitted short refs.

## Current state

- Complete. The boundary is now guarded by one focused public-seam test.

## Changed files

- `progress/activity-direct-entry-context-checkpoint.md`
- `src/test/java/org/sourceanalysis/app/analysis/interpretation/activity/ActivityExplainerDirectEntryContextTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| existing fixed jshERP material planner | PASS | 281 of 337 packets now have direct same-repository context. |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplainerDirectEntryContextTest test` | PASS | One test, zero failures; DRAFT and REVIEW each saw two clean source snippets and retained the full reviewed activity. |

## Decisions

- This test verifies the boundary between material selection and local interpretation. It does not
  attempt to decide whether any real handler represents a particular business domain.

## Blockers

- None. Product Luna/high is intentionally not invoked by this scripted regression.

## Exact next action

- Write the public-seam test and run its selector once before making any production change.

## Resume checks

- DRAFT and REVIEW must each receive no paths, lines, hashes, Proof or Flow identities, and each
  reviewed activity may cite only the packet's two short refs.
