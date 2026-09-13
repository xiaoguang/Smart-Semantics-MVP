# Progress: report source-reference schema RED

- Status: RED TEST WRITTEN (verification deferred)
- Agent role: bounded Step 08 publisher contract test
- Model: gpt-5.6-luna / xhigh
- Date: 2026-09-13
- Scope: guard the public `BusinessReportPublisher.publish` seam against
  Structured Outputs enum expansion when the program-owned source-reference
  map exceeds 1,000 entries. No production code, report output, model call,
  network access, or customer-source execution is in scope.

## Contract under test

- `PublishBusinessReportRequest` may contain 1,001 legal, unique
  `SourceReference` values.
- DRAFT and REVIEW request schemas keep the fixed nine chapter numbers and
  nine chapter titles as enums, for an independently expected total of 18
  enum values.
- Each report `refs.items` transport node is a bounded non-empty string
  (`minLength: 1`, profile-derived `maxLength`) without the full allowlist
  copied into an enum. The complete allowlist remains in the input and Java's
  existing `allowedRefs.containsAll(...)` validation remains authoritative.
- The existing public publisher test
  `rejectsAReportCitationThatIsOutsideTheProgramOwnedReferenceMap` continues
  to assert that an unknown returned reference fails program-side with
  `BUSINESS_REPORT_SOURCE_SCOPE_INVALID`.

## Completed

- Added `boundsSourceReferenceSchemaWithoutRepeatingLargeAllowlist` to
  `src/test/java/org/sourceanalysis/app/analysis/document/BusinessReportPublisherTest.java`.
- The test uses the existing public publisher and scripted in-memory provider,
  submits 1,001 generated-but-valid source references, asserts that all 1,001
  names remain in the DRAFT input allowlist, captures both public request
  schemas, counts all enum values, and checks every chapter's
  `paragraphs.items.refs.items` and `items.items.refs.items` transport node.
- The test is intentionally independent of the publisher's schema builder in
  its expected value: 9 fixed numbers + 9 fixed titles = 18.

## Verification

| Selector | Result | Expected failure before implementation |
| --- | --- | --- |
| `mvn -q -t .mvn/toolchains.xml -o -DargLine=-Xmx256m -Dtest=BusinessReportPublisherTest#boundsSourceReferenceSchemaWithoutRepeatingLargeAllowlist test` | DEFERRED | Current publisher repeats the 1,001-value source-ref enum in 18 content positions; the enum-value count exceeds 18 and `refs.items` lacks the bounded string constraints. |
| Existing unknown-reference publisher test | PRESERVED, not rerun | Program-side validation remains required; no schema change may weaken it. |
| Maven/JDT/model/customer-source execution | NOT RUN | Explicitly out of scope while the coordinator owns the active run. |

## Decisions and handoff

- Keep the test on the public `BusinessReportPublisher`/`publish` seam and
  reuse `BusinessReportPublisherTest`'s existing knowledge/provider fixtures.
- Do not reduce material/source references, add schema-lowering machinery, or
  change the report's nine-section business fields.
- The production owner may run the one exact method selector after the active
  JDT lane permits Maven, then implement only the bounded transport-schema
  change while preserving Java membership checks.
