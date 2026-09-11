# Progress: fixed-repository-offline-tests

- Status: FAIL (single authorized fixed-commit IT stopped before capture)
- Agent role: Luna/xhigh bounded Step01–05 offline acceptance IT/config owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Own `FixedRepositoryBusinessFlowsIT.java`,
  `fixed-repository-acceptance-config.json`, and this progress note only.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`,
  branch `codex/source-analysis-business-flows-closeout`; main gate `dea5c1b`.
- Approved inputs: merged Step05 §8.6 contract, execution-seam ruling,
  offline-acceptance design, current public/package-local APIs, and the approved
  fixed jshERP commit supplied by root.

## Role and scope

## Scope

This work unit owns only `FixedRepositoryBusinessFlowsIT`, its canonical
`fixed-repository-acceptance-config.json` oracle, and this progress note. The
test is opt-in and remains dormant unless the frozen Step05 §8.6 properties
are supplied. It does not add runtime/bootstrap APIs, customer Maven
invocation, source capture, network access, providers, or production changes.

## Feasibility review

The current APIs provide the required real chain without a new helper seam:

`LocalGitCommitCaptureAdapter` → reopened `LocalGitSourceRegistry` → verified
source inventory M1/M2/M3 → `ApplicationDiscoveryExecutor` →
`ProgramGraphsExecution` → persisted fact candidate/proof/ledger M1/M2/M3 →
flow compilation/capsule/public business-flow publication. `RunStoreBootstrap.openForTest`
opens a real empty filesystem store and is permitted for this test. Each
downstream executor accepts typed persisted references and its readers reopen
the predecessor artifacts.

The test oracle must provide exact canonical artifact policies for the union
of Step01–05, exact control/profile/reference bytes and digests, and finite
typed resource limits. No mandatory source/config field was found missing;
the runtime source and workspace are supplied only through the frozen opt-in
properties. The customer repository path and commit remain untouched until an
explicit root GO for the capture run.

## Verification boundary

The config-only Failsafe lease passed. Root then granted one actual offline
capture for the approved fixed commit, using the exact command and empty
ignored workspace recorded below. The run stopped before capture because the
real adapter rejected the source's promisor configuration. No customer Maven
invocation, source script, network access, provider, or active-branch capture
is in scope. The retained acceptance report is an honest `FAIL` with
`captureCompleted=false` and all downstream stages `NOT_RUN`.

## Changed files

- `progress/fixed-repository-offline-tests.md` (owned)
- `src/test/java/org/sourceanalysis/app/analysis/inventory/FixedRepositoryBusinessFlowsIT.java` (owned; authored)
- `src/test/resources/analysis/flow/fixed-repository/fixed-repository-acceptance-config.json` (owned; authored)

## Verification

| Command | Result |
| --- | --- |
| Read-only API/source inspection | PASS; real assembly seams and required fields identified; no new helper required |
| Frozen config-only Failsafe chain, first attempt | EXIT 1; testCompile passed (93 sources), then the parser incorrectly rejected ordinary 3-field refs |
| Frozen config-only Failsafe chain, second attempt | EXIT 1; testCompile passed (93 sources), then the policy registry comparison used incompatible reference record types |
| Frozen config-only Failsafe chain after latest owned fixes | EXIT 0; testCompile passed (93 sources), Failsafe 1 run, 0 failures/errors/skips, BUILD SUCCESS; no source/capture path selected |
| Owned IT exact-file Spotless apply/check (corrected) | Relative property was a no-target false pass; absolute `-DspotlessFiles` selected exactly 1 owned IT, apply changed 1 file, check selected 1 with 0 needing changes; both EXIT 0 |
| Config-only Failsafe after format correction | EXIT 0; exact selector, testCompile recompiled 93 sources, Failsafe 1 run, 0 failures/errors/skips, BUILD SUCCESS; no source/capture path selected |
| Customer capture/IT chain | EXIT 1; Failsafe ran 2 tests: config-only PASS, main ERROR before capture with `LOCAL_GIT_PROMISOR_UNSUPPORTED` at `LocalGitCommitCaptureAdapter.rejectUnsupportedObjectSources:176`; report says `captureCompleted=false`, status `FAIL`, all 11 downstream stages `NOT_RUN`; no auto-rerun |

## Review follow-up resolved in the owned patch

- The canonical policy oracle now uses the exact current publication prefixes:
  admitted-source-request `request-admission`, code-structure draft
  `code-structure`, and call-graph draft schema
  `program-graphs-call-graph-draft-v3`; the config identity and SHA were
  recomputed from the changed canonical registry.
- The report now distinguishes `ATTEMPTED`/`FAILED` stages from `NOT_RUN`,
  records fresh discovery-entry denominator closure, per-ineligible-flow
  non-empty gap mapping, capture registration/receipt/snapshot refs, and
  disposition/Flow/Capsule counts.
- The config reference parser now accepts the policy reference's exact
  `{artifactId,sha256}` shape without weakening unknown-field rejection. A
  compiled entry disposition requires the exact `gapIds` set carried by its
  fresh flow slice, preserving legitimate compiled external-effect gaps.
- Every control/reference content value is now canonical JSON and is parsed
  before any source path is read; profile, registry, policy, and typed values
  remain checked against the actual APIs and config bytes.

## Exact next action

The frozen config-only selector is green after the owned parser, registry
comparison, and report-label fixes. The single actual-capture run was
authorized and stopped before capture. The exact retained report is
`.workspace/fixed-repository-flow-acceptance.HFfWil/fixed-repository-acceptance-report.json`;
it records `FAIL`, `businessFlowPublicationReached=false`,
`captureCompleted=false`, the `LOCAL_GIT_PROMISOR_UNSUPPORTED` error, and all
downstream stages as `NOT_RUN`. Read-only source metadata also showed
`rev-parse --is-shallow-repository=false`, but
`remote.origin.promisor=true` and
`remote.origin.partialclonefilter=blob:none`; no capture artifact was
produced.
Do not retry this capture without a new root decision. The exact command used
from this module was:

`mvn -o -t .mvn/toolchains.xml -DskipUTs=false -Dit.test=FixedRepositoryBusinessFlowsIT -Dsourceanalysis.fixedRepositoryAcceptance=true -Dsourceanalysis.fixedRepositoryPath=/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2/backend-agents/sources/github-code/.workspace/jshERP-8c30ce7861570458920175e200bb2a6442713580 -Dsourceanalysis.fixedRepositoryWorkspace=/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/fixed-repository-flow-acceptance.HFfWil test-compile failsafe:integration-test failsafe:verify`

## Blockers

The fixed input is a Git promisor/partial-clone source rejected by the real
capture adapter before any source capture. There is no downstream stage to
verify and no valid success claim.
