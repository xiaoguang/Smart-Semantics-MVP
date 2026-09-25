# Source-preparation contracts GREEN progress

## Scope

Task 1 production contracts only: immutable preparation values plus readiness and summary
assessment. Directory/Git reading, publication, CLI wiring, downstream guards, runtime
configuration, JDT, model execution, customer scans, and historical rewrites are out of scope.

## Current state

- Read the task brief, source-code `AGENTS.md`, implementation-plan task 1, source-preparation
  contracts sections 1--5, module README sections 1--2, and the Step 01 target design.
- Preserved the existing dirty worktree, Activity work, historical outputs, and
  `more-findings.md`; no production Java, tests, or protected files have been edited.
- Confirmed existing reusable values: `ArtifactId`, `Sha256Digest`, `ArtifactReference`,
  `AnalysisRunId`, `AnalysisStepPublicationReference`, and
  `ArtifactPolicyRegistryReference`.
- Confirmed there is no existing `SourceInventoryReference` or `SelectedSourceBasis`.
  The historical `VerifiedSourceInventoryReference` is Git-only/legacy and its
  `VerifiedSourceFile` requires Git mode and a line index, so neither is suitable for the
  new directory-or-Git partial-entry contract.
- Coordinating the smallest assessment/summary test surface with the Luna test worker before
  writing production classes. Awaiting the root-designated compile RED and exact test API.
- Recorded the root-approved shared contract shape in
  `.workspace/source-preparation-implementation-20260925/task-1-api.md`: raw immutable result,
  explicit enumeration completeness, and a single readiness evaluator producing the summary and
  readiness assessment. The note explicitly prohibits test-only path helpers and records the
  current reusable typed references.
- Luna's first direct contract test is now aligned to the shared entry/issue types: real fixture
  bytes form file/blob/hash identities; observations, origin attributes and exclusions use typed
  values; and the assessment cases cover unresolved and excluded unknown subtrees, resolved
  refreshes, resource-limit aborts, and non-excludable failures. The root has not yet assigned a
  direct-test slot or reported its compile RED, so production code remains intentionally absent.

## First GREEN implementation

- Root then reported the direct `SourcePreparationContractsTest` compile RED, limited to the new
  missing preparation symbols. Implemented only that approved assessment/entry/issue slice:
  `SourcePreparationResult`, `SourcePreparationReadinessEvaluator`, summary/assessment/readiness
  values, typed entry metadata/observations/inheritance/exclusion, typed issues, and the typed
  preparation operation.
- Validation rejects malformed source paths by segments (`.`, `..`, empty, backslash, or absolute)
  while preserving literal inventory names containing glob characters; it rejects false verified
  metadata, fake Git attributes, invalid entry/disposition combinations, dangling entry issue IDs,
  and exclusion of source-wide/request/output/internal failures.
- Did not create source readers, scanners, publishers, CLI/configuration/request parsing, version
  calculators, or downstream guards. Did not run Maven; root owns the direct GREEN command.
- Root's first direct GREEN run (`SourcePreparationContractsTest`) reached nine tests but had eight
  `SourceIssue` construction errors: querying `contains(null)` on the immutable set returned by
  `Set.copyOf` throws on this JDK. The same constructor also needed to avoid using a compact
  record instance method before its component fields are assigned. Removed the redundant query and
  changed the exclusion eligibility test to a static parameter check (with explicit FILE/DIRECTORY
  grouping). `src/main` is frozen again awaiting root's second direct GREEN run.
- Root then ran the expanded 18-case boundary RED: the initial 13 passed and five contract
  boundaries failed. The focused correction keeps unaccounted unavailable/unchecked/unsupported
  entries unusable, treats incomplete enumeration without a named accountable range as a decision,
  recognizes an excluded entry's covered path as sufficient coverage for an unknown subtree even
  with an informational issue, permits parent-only directory/failed-entry inheritance, and counts
  a regular `.git` metadata path as a skipped/excluded discovered file. No new module or later
  source-preparation phase was added; root owns the next direct GREEN run.

## First GREEN verification

- Root reran the canonical direct Maven target after the boundary correction and confirmed
  `SourcePreparationContractsTest`: **18/18 GREEN**. Evidence is recorded by root in
  `.workspace/source-preparation-implementation-20260925/task-1-green-2.log`.
- This completes only the first assessment/entry/issue contract slice. Task 1 is not complete:
  origin/request/target validation, prepared references, selected-basis values, and version/file
  identity calculation remain gated on Luna's second-batch RED.

## Verification

- No test or build run by this worker. Root reports the coordinated direct baseline clean at
  12/12 passing before this task's production changes.
- Root also confirmed the second contract RED was limited to the missing second-slice types.
  The second production slice is now frozen for root's targeted GREEN run; this worker did not
  edit its frozen test or run Maven.
- Root's first second-batch GREEN attempt executed 27 tests with zero assertion failures and two
  test-fixture construction errors caused by non-hex policy-reference fills. The original 18 and
  seven new assertions passed; this is not a final 27-pass result. Luna is correcting the fixture
  and root owns the rerun.

## Second GREEN implementation

- Added limits, exact file/directory targets, sealed directory/Git origins, immutable
  NEW/REFRESH/EXCLUDE requests, pure parent-chain validation, and a complete typed prepared
  source reference. Validation copies collection inputs, has no filesystem reads, and does not
  reopen saved receipts.
- Added the mutually exclusive prepared/legacy SelectedSourceBasis value and pure source-file-v2
  plus source-preparation-basis-v1 identity calculators.
- Unified literal source-tree path validation across the new and first-slice contracts: NUL,
  absolute, backslash, empty-segment, and traversal aliases fail; literal a..b and literal[]
  remain valid inventory paths; wildcard and brace rejection applies only to targets.
- Source-version issue ordering uses the retained structured fields in unsigned UTF-8 order,
  rather than diagnostic issue IDs. Canonical roots, observations, timestamps, diagnostic text,
  and IDs do not enter its identity.

## Review fix round 1 implementation

- Root accepted the independent review's five bounded Task 1 findings and its reconciliation of
  the initially incomplete raw/summary record shape. The new regression RED compiled production
  and failed only because the frozen tests required the expanded raw result and summary accessors.
- SourcePreparationResult now records defensive, exact unmatched exclusion targets; the derived
  summary carries that same immutable fact plus directory, symlink, and submodule counts based on
  actual entry kind. Unmatched declarations restrict an otherwise usable scope without inventing
  a source entry or unknown subtree.
- Readiness treats an ABORTED inspection as NEEDS_DECISION after preserving stronger BLOCKED
  failures, and recognizes only a matching DIRECTORY/SKIPPED_GIT_METADATA entry as policy
  coverage for a named unknown subtree.
- Exact operation targets now reject nonempty bracket shell patterns while retaining literal
  empty brackets. Non-VERIFIED_TEXT entries cannot retain a text encoding, although an excluded
  regular file can still retain complete known byte identity metadata.
- The source-version basis now includes unmatched exclusions through the existing UTF-8 target
  ordering. No source reader, publication, CLI, downstream guard, model/JDT, or scan was added;
  root's fix-round targeted GREEN is 32 tests with zero failures and errors in 31.463 seconds
  (task-1-fix-1-green.log). Root then ran scoped Spotless on the eight contract files; only one
  test formatting change resulted. Production remains frozen for scoped re-review.

## Review fix round 2 implementation

- Scoped re-review isolated the remaining original bracket-glob finding to valid shell character
  classes whose first member is ]. Root's targeted RED had one assertion failure for []A] and
  []] paths (task-1-fix-2-red.log); production compiled and no other test failed.
- SourcePreparationTarget now recognizes an initial ] as a character-class member when a later ]
  closes the class. It still accepts literal[] and changes no other target, record, evaluator, or
  version behavior. Production is frozen for root's same-target GREEN and F4-only re-review.

## Concerns

- `sourceVersionId` uses the existing `ArtifactId` `snapshot:<sha256>` grammar; it should not
  be represented by an unvalidated free-form `String`.
- A derived source basis needs `AnalysisStepPublicationReference` as the genuine owner/receipt
  handle, rather than invented receipt or artifact-reference stand-ins.
