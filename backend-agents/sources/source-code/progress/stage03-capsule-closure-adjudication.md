# Progress: Stage03 capsule closure adjudication

- Status: COMPLETE
- Agent role: Architecture adjudicator (read-only)
- Model: gpt-5.6-sol / ultra
- Started: 2026-08-30T10:59:27-02:30
- Last updated: 2026-08-30T11:05:14-02:30
- Scope: Adjudicate the four P1 findings in `progress/stage03-capsule-closure-review.md` against the Stage03 external replay seam, internal Capsule validator, and Stage01/Stage02 gates.
- Approved inputs: Scoped AGENTS; Stage03 design; review progress; Stage03Generator and Stage03CapsuleClosureValidator; Stage01Analyzer and Stage02Compiler plus directly required model/test evidence.
- Current branch/worktree: `codex/github-code-design-walkthrough` / `/Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2`

## Completed

- Read the repository, prototype, backend-agent, and GitHub-code-agent rules relevant to this read-only adjudication.
- Read the deep-module seam and code-review reception guidance.
- Confirmed the worktree contains extensive pre-existing Stage01–03 work; this task owns only this progress file.
- Read the four findings, the Stage03 external-interface and M5 fatal contracts, the Generator call order,
  the internal Capsule validator, and the Stage01/Stage02 identity, graph/proof, source-reopen, path, and
  expectation-Gap gates.
- Adjudicated all four findings at the public deep-module interface rather than treating the package-private
  validator as a second public M1–M4 implementation.

## Current state

- For the current replay-only `Stage03Generator.generate(Stage03Request, StructuredModelProvider)` interface,
  findings 1, 2, and 4 are already closed by the composed replay boundary. Finding 3 identifies a real weaker
  duplicate filesystem read and a residual concurrent-mutation/TOCTOU concern, but not a bounded Stage03
  correctness escape: static invalid paths/symlinks fail in fresh Stage01/02 replay, and different bytes fail
  the Stage03 file hash before Provider admission. Treat it as cross-stage source-reader hardening, not a
  Stage03 M5–M7 fatal for the frozen, non-concurrently-mutated input profile.

### Finding dispositions

| Review finding | Disposition | Exact interface-level evidence | Required now |
| --- | --- | --- | --- |
| Proof-edge and graph provenance is not independently closed inside `Stage03CapsuleClosureValidator` | **ALREADY CLOSED BY COMPOSED REPLAY** | The public request contains a replayable `Stage02Request`, never a caller-supplied `Stage01Result`/`Stage02Result` (`Stage03Request.java`). `Stage03Generator.java:89-95` freshly runs Stage01 and Stage02 and checks both identities. `Stage02Compiler.java:73-84,146-147,1007-1039` requires every proof node in the fresh graph and every required proof edge to match repository-edge ID, endpoints, and rule. The package validator is internal and receives only those freshly produced objects at `Stage03Generator.java:104-114`. | None. Revalidate here only if a future external interface accepts deserialized/cached Stage01/02 records. |
| Committed identity/profile closure is incomplete inside the validator | **ALREADY CLOSED BY COMPOSED REPLAY** | `Stage01Analyzer.java:27-37` recomputes Stage01 identity from fresh snapshot/model/capability/fact/proof/Gap identities; `SnapshotVerifier.java:62-67` and `ProvenFactCompiler.java:80-92,605-607` content-address snapshot, Fact/Gap, and ProofPack. `Stage02Compiler.java:77-79,113-119,710-725` validates expected Stage01 and exact profiles, then recomputes the Stage02 identity over profiles, budget, flows, capsules, dispositions, and gaps. `Stage03Generator.java:93-95` requires the caller's expected Stage02 identity to equal this fresh result before Provider work. | None. A future M8 archive reader must validate its persisted result/receipt at that new seam; do not expand this internal validator pre-emptively. |
| Source path and symlink policy is delegated rather than independently enforced | **FUTURE CROSS-STAGE HARDENING; NOT BOUNDED STAGE03 FATAL** | Static absolute/noncanonical paths are rejected at `SnapshotVerifier.java:425-444`; root, every segment, and final files are checked with `NOFOLLOW_LINKS` and opened no-follow at `:217-296`. Stage02 immediately replays that gate and again checks path segments/hash at `Stage02Compiler.java:735-775`. The review is correct that `Stage03CapsuleClosureValidator.java:483-509` performs a weaker third `Files.readAllBytes`: a symlink installed after replay could be followed, and size is checked only after reading. Different bytes still fail SHA before the first Provider call, so this does not admit different source semantics. A same-content escape or oversized concurrent target is a residual confinement/resource risk under a concurrently mutable root, which is outside the current frozen-input assumption and affects the shared source-reader design rather than Capsule semantics alone. | No Stage03 acceptance blocker. Before allowing concurrently mutable/untrusted roots, remove the third filesystem reopen or route all reopening through one no-follow, resource-bounded verified-source module; then add deterministic ancestor/final-symlink replacement and oversized-target tests at that module's interface. |
| Expectation-Gap scope is membership-only inside the validator | **ALREADY CLOSED BY COMPOSED REPLAY** | `Stage02Compiler.warningGaps` at `:331-337` admits an expectation Gap only when all observation nodes and searched-scope roots lie in the current entry-owned node closure. The resulting gap IDs are committed into `flowSliceId` (`:164-171`), Capsule identity/content (`:492-501`), and `stage02ResultId` (`:113-119`). Fresh replay plus the expected Stage02-ID comparison prevents copying a foreign Gap into Flow/Capsule through the public Stage03 interface. Existing multi-flow/FlowGap tests additionally verify no foreign task projection. | None. Repeat scope proof only if a later seam accepts naked or persisted Flow/Capsule records without replay. |

### Minimal next actions

1. Do **not** add graph, identity, or expectation-scope duplication to the package-private validator; it would
   make the internal seam as wide as Stage01+Stage02 while adding no reachable public protection.
2. Reclassify the four review items as three composed-boundary closures plus one ARCH-12/source-reader
   hardening item. The existing public replay mismatch test (`Stage03GeneratorTest.java:66-78`) already proves
   expected Stage02 drift stops before Provider; Stage02 proof closure and Stage03 multi-flow/Gap isolation tests
   exercise the other current contracts.
3. In a future source-reader hardening work unit, choose one design: carry immutable verified bytes through the
   composed run, or centralize every reopen behind one no-follow/resource-bounded module. Test concurrent
   ancestor/final symlink replacement and an oversized replacement at that module's interface. Do not add a
   flaky sleep/race test to Stage03.

### Acceptance consequence

- **Yes:** the bounded Stage03 M5–M7 profile can be accepted with respect to these four findings without another
  production change. Its interface remains frozen local input + mandatory Stage01/02 replay; it does not accept
  caller-constructed analysis records.
- This adjudication does not close unrelated Stage03 capability limitations or M8 archive/Trace/recovery work.
- Expanding the trust model to concurrently mutable roots, cached results, or persisted naked Stage01/02 records
  reopens the relevant finding and requires a new external seam contract and tests.

## Changed files

- `progress/stage03-capsule-closure-adjudication.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Existing Stage01–03 and documentation changes observed; no unrelated file will be edited by this task. |
| Read-only source/design trace | PASS | Four findings traced from `Stage03Request` through fresh Stage01/02 replay, internal validation, and first Provider call. |
| `git diff --check -- progress/stage03-capsule-closure-adjudication.md` | PASS | No whitespace errors in the only task-owned file. |

## Decisions

- Treat `Stage03Generator.generate(Stage03Request, ModelProvider)` as the external module interface and the Capsule closure validator as an internal seam unless code proves otherwise.
- Classify each review item as Stage03-fatal, already closed by the composed replay boundary, or future hardening; do not implement during this task.
- Internal seams may check Stage03-local assumptions without duplicating the complete upstream Module interface.
- A reviewer-demonstrated mutation of package-private parameters is not a reachable product failure when the
  external Module neither accepts nor deserializes those records.

## Blockers

- None.

## Exact next action

- Parent may update the Stage03 acceptance status from this adjudication; no implementation is required for
  these four findings under the current bounded interface.

## Resume checks

- Re-read this file, run `git status --short`, and verify only this task's progress file is attributed to this adjudication.
