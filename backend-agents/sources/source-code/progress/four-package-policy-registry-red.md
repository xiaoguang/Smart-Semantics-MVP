# Progress: four-package policy registry RED

- Status: COMPLETE (RED delivered; waiting for Terra's policy JSON GREEN fix)
- Agent role: Bounded test-only policy coverage slice
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Add one direct test proving the shipped repository-run policy template registers exactly the nine current business checkpoint producer policies with their schema, ID prefix, media type, envelope kind, and empty-JSONL setting. No production or policy JSON edits; no recovery-system work.
- Approved inputs: `tools/repository-run/jdt-artifact-policy-set-v1.json`; `src/main/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMain.java` policy-loading seam; current business checkpoint publishers and policy fixtures; `src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMainTest.java`.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code`

## Completed

- Read the applicable TDD skill and its `writing-good-tests.md` reference before changing the test suite.
- Read scoped `AGENTS.md` and ran `git status --short`; preserved all pre-existing user/agent changes.
- Identified the nine expected producer policies: two ActivityExplainer outputs, three ProcessExplainer outputs, and four BusinessReportPublisher outputs. Existing `FLOW_INTERPRETATION_BUSINESS_MATERIAL` is outside this nine-policy slice.
- Derived expected values from the owning publishers and the existing `ProgramGraphsPublicFixture` policy setup.

## Current state

The shipped `tools/repository-run/jdt-artifact-policy-set-v1.json` currently contains the older policy set and omits all nine business checkpoint policies. Added one direct test that invokes the existing `RepositoryRunMain.loadPolicies(Path, CanonicalJsonCodec)` seam against the shipped template and resolves the exact nine hand-derived business keys, asserting ID prefix, media type, envelope kind, and empty-JSONL setting for each. It fails with the current JSON before Terra adds the nine registrations.

## Changed files

- `progress/four-package-policy-registry-red.md`
- `src/test/java/org/sourceanalysis/app/adapter/cli/RepositoryRunMainTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS (read-only) | Existing worktree modifications observed; no unrelated files touched. |
| `sed`/`rg` over policy JSON, launcher, publishers, and fixtures | PASS | Confirmed current policy loader and exact expected nine tuples. |
| TDD RED test | PASS (expected RED) | `mvn -t .mvn/toolchains.xml -Dtest=RepositoryRunMainTest#shippedPolicyTemplateRegistersExactNineBusinessCheckpointPolicies test` compiles the changed test, then fails at the first missing resolution with `ARTIFACT_POLICY_NOT_FOUND` from `LoadedCanonicalArtifactPolicyRegistry.resolve` (line 203). The shipped policy template was not modified. |

## Decisions

- Keep the test in `RepositoryRunMainTest` as requested, because it validates the actual shipped template through the launcher’s policy-loading seam rather than a duplicated in-memory registry.
- Use literal expected policy tuples hand-derived from the current producer code/fixture; do not derive expected values from the policy file under test.
- Resolve each of the nine expected business keys through the production loader and assert all four policy-shape fields exposed by the loaded policy; technical policies remain outside this focused set.

## Blockers

- RED is complete. Terra must add only the nine policy registrations to `tools/repository-run/jdt-artifact-policy-set-v1.json` and rerun this selector for GREEN. No production code or policy JSON was changed in this slice.

## Exact next action

- Stop here and hand the RED evidence to Terra/root; do not alter production code, policy JSON, or broaden verification.

## Resume checks

- Re-read this file, run `git status --short`, inspect the diff, and ensure no production/policy JSON files changed before any further action.
