# Progress: structured provider response normalization

- Status: COMPLETE
- Agent role: primary implementation agent
- Model: gpt-5.6-sol / xhigh for root-cause analysis; gpt-5.6-terra / xhigh for the minimal production correction
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Correct only the boundary between external Codex JSON and internal canonical JSON. Valid external JSON must be strictly parsed and canonicalized before the activity/process modules validate it; malformed/duplicate/trailing JSON remains rejected.
- Approved inputs: Existing local provider command contract and the completed bounded process sample failure. No additional model call is authorized or needed for this correction.
- Current branch/worktree: `codex/source-analysis-business-flows-closeout` at `/private/tmp/linguan-source-analysis-process-design`.

## Completed

- Reproduced the failure once through `LiveLunaReplenishmentProcessIT`: P1 completed, P2 reached `PROCESS_GROUP_REVIEW_INVALID` because `CanonicalJsonCodec.parseCanonical` rejected valid external JSON whose bytes were not in canonical key order.
- Traced the data path: `ProcessCodexSubscriptionCommand` returns raw bytes → `CodexSubscriptionStructuredProvider` forwards them unchanged → `ProcessExplainer` requires canonical bytes. The existing successful activity sample was a coincidence of model key ordering, not a valid provider contract.
- Compared the working scripted provider: its fixture returns already canonical JSON. It therefore does not exercise the real external-response normalization boundary.

## Current state

- Hypothesis: `CodexSubscriptionStructuredProvider` must normalize a strictly valid external JSON value to canonical bytes immediately after the command returns it. This separates transport syntax from the internal content-addressed format and fixes the precise P2 failure without relaxing semantic schema validation.
- Added the direct provider regression first. It failed exactly because the Provider returned pretty, noncanonical JSON unchanged; it now passes after the Provider strictly parses and canonicalizes one external response before returning it. Internal artifact parsing remains canonical-only.

## Changed files

- `progress/structured-provider-normalization.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| opt-in `LiveLunaReplenishmentProcessIT` | RED | P1 returned; P2 failed only at `CanonicalJsonCodec.parseCanonical` with `JSON bytes are not canonical`. |
| provider / ProcessExplainer source trace | PASS | Raw external response is forwarded unchanged before the canonical-only consumer. |
| `CodexSubscriptionStructuredProviderTest#canonicalizesAValidButNoncanonicalExternalJsonResponseBeforeReturningIt` | RED → GREEN | First failed with the original pretty JSON; passes after the minimal external-response normalization. |
| `CodexSubscriptionStructuredProviderTest` | PASS | 2 tests, 0 failures/errors/skips. |
| scoped Spotless and `git diff --check` | PASS | The three changed Java files and progress file are clean. |

## Decisions

- Do not weaken canonical persistence or model-schema validation.
- The correction is intentionally at the external Provider boundary. It neither loosens persisted-artifact validation nor asks the process/activity modules to accept noncanonical bytes.

## Blockers

- None.

## Exact next action

- Re-run the same bounded synthetic process sample once. It will make the same two calls only because the first run's review response was rejected before a durable semantic result could be saved.

## Resume checks

- Ensure malformed JSON, duplicate keys and trailing tokens remain rejected by the strict external parser.
