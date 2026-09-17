# Progress: Legacy Step07 reading-path test migration RED

- Status: READY_FOR_COORDINATOR_GREEN
- Agent role: Luna/xhigh tests-only owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-16
- Scope: Acceptance sample, pipeline, semantic fingerprint, prompt-contract, and affected reviewed-job reuse tests
- Owning design: `docs/supplements/cross-object-process-reconstruction/module-design.md`

## Confirmed seams

- Acceptance samples must retain full-catalog candidate ordinals while reading checks run only for
  the explicitly selected candidate IDs. The formal run may reuse only complete old DRAFT/REVIEW
  pairs and matching single reading decisions.
- Process DRAFT/REVIEW fixtures must consume `readingPacket.reviewedActivities` and
  `readingPacket.sourceExcerpts`; they must not emit or depend on `requestedSourceRefs`.
- Local source IDs are packet-local. Equal-looking local IDs from different candidate packets must
  remain distinct until deterministic publication remaps them; raw Provider response bytes are not
  rewritten by reuse or packet assembly.
- Existing catalog/process fingerprint contracts remain versioned; the new selection/check
  decisions use their own `process-reading-decision-v1` fingerprint inputs.

## Completed bounded edits

- `BusinessProcessAcceptanceSampleTest` now has deterministic selection/check fixture branches,
  selected-only reading-check assertions, explicit source reads, packet-based process input, and a
  formal-run expectation that reuses selected pairs/checks while executing only the remaining
  candidate check plus its pair and consolidation.
- `BusinessProcessPromptV2ContractTest` now covers the configured v3 process resources and the two
  v1 one-shot reading resources without editing prompt files.
- `BusinessProcessSemanticFingerprintV2Test` was clarified as the legacy catalog-pair invalidation
  contract; its v1-versus-v2 fingerprint and non-reuse assertion remain intact.
- `ReviewedModelJobReuseTest` now also asserts reading a reusable pair does not rewrite its raw
  `reviewed-result.json` bytes, while preserving all old-pair non-reuse checks.
- `CrossObjectProcessExecutionConfigurationTest` now uses a complete canonical v2 catalog pair
  fixture (including fingerprint, identity, draft and review), matching the private config input
  contract without changing production.
- `BusinessProcessReadingPipelineTest` asserts explicit M1/M2 source reads coexist with generated
  local S1 XML content, so same-package local references cannot overwrite earlier packet material.
- Its bounded second scenario now verifies an M10 source fragment that is present only in saved
  material (not any `ReviewedActivity.sourceRefs`) can be selected by `SOURCE_REF`, reaches the
  DRAFT packet/response, and remains readable. It also drives two candidates whose local `S1`
  refers to different whole files, asserting deterministic final `S1`/`S2` mapping by actual path;
  the prior batch's reviewed-pair bytes remain unchanged after reuse.
- The existing cross-activity reference contract now follows the new packet allowlist: an
  allowlisted S3 reference remains valid beside the owning activity's S1 reference; it is not
  filtered by the old activity-owner rule. The pipeline context knowledge fixture uses the public
  `OBJECT_RELATION` kind while retaining context-only membership and evidence assertions.
- No `BusinessProcessPromptV2Test` file exists under the current source tree; the actual prompt
  contract seam is `BusinessProcessPromptV2ContractTest`.

## Changed files

- This progress record.
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessAcceptanceSampleTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessPromptV2ContractTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessSemanticFingerprintV2Test.java`
- `src/test/java/org/sourceanalysis/app/runtime/modeljob/ReviewedModelJobReuseTest.java`
- `src/test/java/org/sourceanalysis/app/adapter/cli/CrossObjectProcessExecutionConfigurationTest.java`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessReadingPipelineTest.java`

## Verification

- No Maven/build/test/model/JDT/network/commit will be run by this agent. Coordinator owns all
  targeted Maven runs.
- Use static inspection and whitespace checks only; report any compile/API uncertainty before
  inventing a new seam.

## Blockers

- None. Static diff/whitespace checks are clean; coordinator owns compilation and targeted tests.
