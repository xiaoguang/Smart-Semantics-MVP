# Source preparation contract tests — Luna/xhigh

## Scope

Task 1 direct tests only: immutable preparation origin/request/target/entry/issue/result/reference contracts, readiness and summary evaluation, source-version/file identity calculation, and `runtime.SelectedSourceBasis`. No source scanning, artifact publication, CLI wiring, JDT, business model, customer build, or production implementation.

## Baseline

- Read the task brief, source-scoped `AGENTS.md`, source-preparation README, contracts/storage design, and implementation plan task 1.
- Worktree already contains unrelated frontend and prototype changes; no existing Task 1 Java test or production symbols were present at inspection time.
- The brief's relative `.workspace` path resolves under `backend-agents/sources/source-code/.workspace/source-preparation-implementation-20260925/`.

## Proposed test seam (pending root adjudication)

Tests should exercise public value contracts and named production evaluators rather than source text or reflection. The smallest useful seam is:

- `SourcePreparationRequest`, `SourcePreparationOrigin`, `SourcePreparationTarget`, `SourcePreparationLimits`, and typed enums for operation/kind;
- `SourceEntry`, `SourceIssue`, `SourcePreparationResult`, and a named readiness/summary evaluator;
- `PreparedSourceReference` and `SelectedSourceBasis` (with complete source reference, snapshot ID, effective-scope digest, and kind);
- a named source-version calculator exposing the approved `source-file-v2` and `source-preparation-basis-v1` recipes.

## Waiting point

Per the task brief, root is resolving a baseline `target/classes` versus source mismatch. Do not add `SourcePreparationContractsTest.java` or run Maven until root explicitly grants the test-writing/build slot.

## First batch handoff

The assessment/summary test was subsequently written and frozen after root's RED slot
opened. It covers the requested current-scope cases and is reported in
`.workspace/source-preparation-implementation-20260925/task-1-tests-report.md`.

## Second batch draft and handoff

Root confirmed the first batch GREEN and granted the next compile slot. The second batch is
now present as `SourcePreparationRequestVersionBasisTest.java`; its noncompiled draft remains
under `.workspace` as the design record. It covers request operation/base/target/exclusion
invariants, typed directory/Git origin, complete prepared and legacy selected-basis references,
and pure source-file/source-basis identity recipes. No production code, Maven run, source scan,
JDT, model, or customer build is part of this phase.

## First-batch regression additions

After root's first GREEN exposed a production `SourceIssue` immutable-set construction bug,
the current test adds frozen regressions for unaccounted `UNAVAILABLE`/`UNCHECKED`/
`UNSUPPORTED`, initially excluded unknown directories, incomplete enumeration with no named
unknown, nullable directory inheritance file identity, and a regular-file `.git` policy
exclusion. Root owns the next targeted build after Terra's fix. The second-batch request/
version/basis test is now frozen for that build.

## Planned behavior coverage

1. 99 verified text files plus one unreadable file remains `NEEDS_DECISION` and retains all entries.
2. An unknown/unlistable subtree leaves total regular-file count unknown and records a named unknown subtree.
3. Local exclusion of a resolvable blocker yields `READY_WITH_EXCLUSIONS` when valid text remains; all media/all excluded yields `NO_ANALYZABLE_TEXT`.
4. Request/root/identity/output failures remain unusable despite exclusion-like entries.
5. Immutable defensive copies, request operation/target rules, directory versus Git-origin identity, and public/private path separation.
6. Distinct derived source versions even when bytes are unchanged; diagnostic text, timestamps, host root, and model/runtime details do not affect the basis ID.
7. Selected basis preserves complete preparation/legacy references and effective scope rather than inferring from consumed materials.

## Second-batch handoff details

The landed second batch keeps invalid target strings inside the assertion lambda so target
construction itself is part of the observed contract. It compares prepared basis snapshots to
`prepared.sourceVersionId()` and legacy snapshots to `ArtifactId.parse(legacy.snapshotId())`,
requires EXCLUDE effective exclusions to contain its targets, rejects derived policy/limit
replacement, permits cumulative parent-directory exclusion over a previously excluded child,
and exercises source-origin construction with an absent absolute root without touching the
filesystem. The identity cases separately vary path, size, digest, real origin attributes,
parent, operation, diagnostics, and canonical host root.

## Step 2 reader test proposal

After root assigned the next work unit, I read `task-2-notes.md`, the source-preparation README
sections 3–4, the Step 01 contract and plan, and the existing `capture/localgit` adapter,
registry, snapshot and direct tests. The bounded proposal is in
`.workspace/source-preparation-implementation-20260925/task-2-test-proposal.md`. It proposes
one internal `SourceOriginReader` seam plus a streaming blob sink, a deterministic no-follow
directory-access adapter for local failures/mid-read changes, and reuse/extraction of the
existing restricted Git plumbing rather than invoking old all-or-nothing `capture()`.
The matrix covers default hidden/build/dependency inclusion, `.git` policy skipping, link
zero-read, local/root failures, unknown subtrees, read changes, resource limits, exact UTF-8/
media bytes, and fixed-commit Git tree/blob behavior. No source test or production file was
changed for this proposal; no Maven or customer/JDT/model operation was run.

Root review corrections are incorporated: ordinary-directory NEW only compares before/after
stat attributes plus the hash calculated from the current stream, so deterministic tests use
size/mtime/fileKey changes and do not claim to detect an unseen same-size content edit;
`SOURCE_HASH_MISMATCH` belongs to saved-content/expected-hash verification. Compression is
classified from actual bytes rather than the filename extension. The proposed streaming sink
now separates source-input read failures (local file issue, siblings continue) from writer/output
failures (`SOURCE_OUTPUT_FAILED`, global abort/block), and root/output containment is tested with
real temporary paths and a real output writer rather than an empty sink.

## Step 2 directory draft

After root provided `task-2-api-decisions.md`, I added the ignored workspace-only
`DirectorySourcePreparationTest.java.draft` and `task-2-test-api.md`. The draft groups the
directory contract into five behavior flows: default inclusion plus `.git` unknown-policy scope
and byte fidelity; no-follow links plus source/output overlap; local sibling failures versus
root listing failure; observed stat changes plus resource limits; and source-read versus output
writer failure. It uses JDK `DirectoryStream<Path>`, no-follow `BasicFileAttributes`, and
`InputStream` access adapters, with a real temporary output-root writer. No `src/test` or
production source was modified and no Maven command was run.

## Task 1 review-fix regression freeze

Root assigned the five findings in `task-1-review.md`/`task-1-fix-1.md`. I updated only the two
existing contract test classes. All raw `SourcePreparationResult` fixtures now use the complete
constructor with an explicit unmatched-exclusions list. The assessment test adds policy-skipped
`.git` directory coverage while retaining an unknown total, an aborted-inspection readiness
guard (accepting either constructor rejection or `NEEDS_DECISION`), separate directory/symlink/
submodule summary dimensions, typed unmatched exclusion reporting without a fake entry, and
excluded-entry text-encoding rejection. The request/version test adds bracket shell-glob
rejection while retaining the allowed literal `literal[]` path. Production files and Step 2
`src/test` remain untouched; Maven has not been run. The review-fix tests are frozen for root's
targeted RED and Terra's GREEN implementation.

## Task 1 fix-2 freeze

Root's scoped review left only the original bracket-class edge. I added the two required exact
target regressions `src/[]A].java` and `src/[]].java` to the existing request/version contract
test while preserving acceptance of literal `literal[]`. No API, production, new test class, or
Step 2 source test changed; Maven was not run. The file is frozen for root's assertion RED and
Terra's lexical bracket-detection fix.
