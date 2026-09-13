# Progress: navigation-reuse-step05-reference-green

- Status: COMPLETE
- Scope: Implement the approved Step05/Capsule persisted-reference contract
  (Flow v6, Capsule v11/public v9) from the existing RED. Preserve concurrent
  JDT cache work; do not change test assertions, design, or run real JDT/full
  Maven.

## Initial state

- The formal checkout has pre-existing Task 1 CI, JDT cache, and RED changes;
  they are preserved.
- The approved plan requires persisted contexts to be references, while all
  downstream business material reads must hydrate complete immutable contexts
  from the existing Java-code index rather than restart an engine.

## Delivered

- Flow compilation v6 and public flow slices v6 persist `codeContextRef` only;
  collected records bind to the current `java-code-index-v2` artifact and the
  same entry ID, while uncollected records persist a null reference and reason.
- Capsule projection v11 and public evidence capsules v9 persist
  `entryContextRef` only. The projector and material reader reopen the existing
  index to restore complete immutable contexts; no code engine is started.
- Publication closure validates both reference chains. Artifact policy, direct
  fixture policy, and the Step05/06 durable contracts were updated to the four
  new schema versions. No compatibility reader was added.

## Verification

- RED baseline: `mvn -o -t .mvn/toolchains.xml -Dtest=BusinessFlowsExecutionTest test`
  ran 4 tests with 2 expected v5-version failures.
- GREEN: the final `mvn -o -t .mvn/toolchains.xml
  -Dtest=BusinessFlowsExecutionTest test` ran 4 tests with 0 failures and 0
  errors (`BUILD SUCCESS`, 12.651 s). An earlier green run followed a clean
  rebuild after the direct fixture policy version update.
- `git diff --check` passed for the scoped changes. No real-JDT, model, or full
  Maven suite was run.
