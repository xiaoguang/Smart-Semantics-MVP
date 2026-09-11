# ActivityExplainer implementation

Status: COMPLETE (bounded first vertical slice).

Scope: production-only first vertical slice for `ActivityExplainer`, using the
existing `ActivityExplainerTest` as the RED contract. No test, legacy
interpretation, provider call, or unrelated worktree changes will be modified.

## Completed

- Added the shared injected `StructuredModelProvider` seam and its immutable
  request/response records.
- Added the activity request/profile/result/activity records and the
  one-argument `ActivityExplainer` constructor.
- Implemented canonical clean-packet DRAFT, strict parse/validation, canonical
  `actualDraft` REVIEW, and strict final reviewed-activity projection. Material
  and entry identities are assigned only outside model JSON.
- Enforced the complete open-vocabulary activity shape, response size/profile
  limits, source-reference validation, prohibited program-identity fields, and
  fatal no-retry Provider handling. An invalid DRAFT stops before REVIEW with
  `ACTIVITY_SOURCE_SCOPE_INVALID` for an injected or unknown source reference.
- The supplied fixture correction now supplies refs from the selected material;
  no production persistence or real Provider adapter was introduced.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t /private/tmp/sourceanalysis-jdk17/toolchains.xml -Dtest=ActivityExplainerTest test` | PASS | 1 test, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. |
| Scoped Spotless for activity package, provider records, and `ActivityExplainerTest` | PASS | Each bounded `spotless:apply` invocation completed with BUILD SUCCESS. |
| `git diff --check` for the bounded paths | PASS | No whitespace errors. |

## Files added

- `src/main/java/org/sourceanalysis/app/adapter/provider/StructuredModelProvider.java`
- `src/main/java/org/sourceanalysis/app/adapter/provider/StructuredModelRequest.java`
- `src/main/java/org/sourceanalysis/app/adapter/provider/StructuredModelResponse.java`
- `src/main/java/org/sourceanalysis/app/analysis/interpretation/activity/`

## Deferred

The deliberately bounded slice does not add per-material checkpoints, aggregate
publication/coverage, prompt resources, a live Provider adapter, retries, or
any legacy interpretation registry integration.

## Handoff

The bounded ActivityExplainer production slice is handed off after the direct
GREEN selector and scoped Spotless/diff checks recorded above. No persistence,
live Provider, retry, or follow-on feature work was performed.
