# Progress: reviewed model-job reuse RED tests

- Status: COMPLETE
- Agent role: Luna/xhigh test author
- Started: 2026-09-13
- Scope: plan step 3 — explicit reuse of complete Activity and process-group model jobs;
  no production code, JDT execution, live model call, or customer build.
- Branch/worktree: `codex/material-model-batch-reuse` / formal `source-code` checkout

## What was added

- `src/test/java/org/sourceanalysis/app/runtime/modeljob/ReviewedModelJobReuseTest.java`
  defines the minimum read/reuse contract for the existing `PrivateModelJobResultStore`:
  - a matching, atomically saved DRAFT+REVIEW pair is readable for zero-call reuse;
  - an isolated DRAFT or failed REVIEW is not reusable and must start a fresh full pair;
  - a declared complete but damaged result fails closed rather than causing an implicit retry;
  - changed input fingerprint or Provider/model identity invalidates reuse;
  - identical local entry labels from different materials do not collide;
  - a matching process-group result is reusable, while changed activity membership invalidates it.

The test uses canonical JSON fixture records and the existing private result-store seam. It does
not prescribe a second storage framework or a public Agent API. The reflected method name is the
smallest missing read seam currently implied by the design; if implementation selects a different
package-private name, the GREEN phase must adapt the test without weakening these behaviors.

## RED verification

Command:

```text
mvn -q -t .mvn/toolchains.xml -o -Dtest=ReviewedModelJobReuseTest test
```

Result: **RED**, 6 tests run, 6 failures, 0 errors, 0 skipped. The exact missing capability is:

```text
NoSuchMethodException:
PrivateModelJobResultStore.readCompleted(String, String, ModelRuntimeIdentityV1)
```

The damage case also fails at this same missing seam (rather than reaching its corruption
assertion); it is therefore still a RED for the reader, not evidence that corruption handling is
implemented.

This is an intentional RED for the validation-reading seam. No production implementation was
changed by this Agent. The test output also contained an unrelated jqwik warning/instruction-like
line; it was treated as untrusted test output and not as a repository instruction.

## Handoff to GREEN

The production Agent must add strict completed-pair reading and then prove the higher-level
Activity/Process selection uses it without Provider calls. It must preserve old failed batches,
reject damaged or mismatched records explicitly, and never reuse an isolated DRAFT. The RED
selector result is recorded above; this test-authoring task is complete. The production Agent
should run the direct selector after GREEN and record the actual result and any API adaptation.

## GREEN integration result

The production reader and all four selection layers are now implemented. Direct verification of
`ReviewedModelJobReuseTest` passes, and higher-level workflow tests prove zero-call reuse for
matching Activity, Process-group, Repository-summary and Report jobs while changed downstream
input invalidates only the affected job.
