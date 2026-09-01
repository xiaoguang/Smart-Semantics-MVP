# Progress: Target architecture implementation

- Status: COMPLETE
- Agent role: Root implementation orchestrator and integration owner
- Model: gpt-5.6-sol / ultra design coordination; delegated tests use Luna/xhigh and production code uses Terra/xhigh
- Started: 2026-08-30T00:23:48-02:30
- Last updated: 2026-08-31
- Scope: Implement stages 01–04 of the approved frozen Java/Spring MVC/MyBatis source-to-nine-section Agent plan.
- Approved inputs: Existing target design, fixed local jSHERP commit 8c30ce7861570458920175e200bb2a6442713580, Maven Central fixed JavaParser dependencies, scripted providers; live Luna only after its required preflight.
- Current branch/worktree: codex/github-code-design-walkthrough in /Users/yexiaoguang/Documents/ErpMock/linguan-prototype-v2

## Completed

- Reviewed the approved four-stage plan against the real POC implementation and scoped instructions.
- Verified the existing baseline: 28 targeted tests pass.
- Confirmed this is a dedicated non-main branch; unrelated dirty design/handoff files are outside this task and remain untouched.
- Added and verified the Sol/ultra Stage 01 detailed design for M1–M3.
- Completed the first M1 RED→GREEN slice: seven frozen-snapshot contract tests and a fail-closed `Stage01Analyzer.verify` implementation.
- Corrected one independently detected fixture SHA transposition; production hash verification remained strict.
- Completed M2 RED→GREEN with 18 tests, including bounded-inventory isolation, secure MyBatis parsing, conservative call binding, capability accounting and reviewed P1 regressions.
- Completed M3 RED→GREEN and review hardening: 8 admitted Facts, 20 admitted atoms, Proof dependency closure, explicit rejection accounting and five searched-scope expectation Gaps.
- Converted independent M2/M3 review findings into 17 additional regression tests before production fixes.
- Completed the fixed-commit, eight-file jshERP bounded acceptance without network access or customer build execution.
- Closed Stage 01 documentation as IMPLEMENTED/ACCEPTED only for the profile-bounded, in-process M1–M3 core; M4–M8 remain unimplemented.
- Completed Stage 02 M4 design, Luna RED tests, Terra implementation, independent Luna review, review-finding RED→GREEN hardening, and fixed-commit jshERP bounded acceptance.
- Stage 02 now compiles the synthetic fixture into one entry-root Flow, four path-specific Outcomes, eight uniquely owned Facts, twenty atoms, five warning Gaps, one Capsule and six frozen spans.
- Closed review findings for path-specific atom/proof closure, Gap provenance, shared-service ownership, proof-root evidence projection, CFG/Proof linkage, edge budgets and non-shrinking coverage denominators.
- Completed the initial Stage 03 M5--M7 design, scripted-provider implementation, fourteen direct tests and the fixed jshERP zero-Flow/zero-Capsule boundary check.
- Ran an independent Luna/xhigh Stage 03 review. The review found one P0: the renderer bypasses the typed `NineSectionPlan`, so current green tests do not prove semantic conservation. Fourteen P1/P2 integrity findings remain actionable.
- Closed the Stage 03 P0 by making typed `ReaderItem` ownership the renderer source of truth and enforcing atom/meaning/Gap conservation.
- Hardened Stage 03 registry identity, task payloads, R1/R2 closure, canonical receipts, formula basis, typed OutcomePath content, proven anchors, reader-body cleanliness and resource budgets through public-seam RED→GREEN slices.
- Independently re-ran all current Stage 03 direct selectors: 46 tests pass; independently re-ran the Stage 01/02 regression selectors: 79 tests pass.
- Added a non-vacuous two-entry public fixture and first exposed an upstream blocker: Stage 01 generated one global reservation Fact set, so Stage 02 admitted neither independent entry.
- Generalized Stage 01 M3 to compile per-entry reachable Fact/Atom/Proof closures while preserving deterministic single ownership for a shared Service; the fixture now produces two FlowSlices and two EvidenceCapsules without fabricating Stage 02 records.
- Added and hardened Stage 03 multi-Flow task isolation, Flow-local Gap provenance, same-table hard-anchor merge, distinct request anchors, merged object references and proof-basis directional relations.
- Executed frozen technical-display templates through fallback resolution, M6, typed ReaderItems and Markdown; missing or malformed bindings now fail before Provider work.
- Proved and fixed non-vacuous repository FlowGap isolation with two compiled Flows plus one incomplete third entry; foreign Gap references now fail closed while repository pending/accounting retains the Gap.
- Closed the reader-density hole by projecting every M6 object, activity, relation and answerable question into fixed sections 3, 4, 6 and 8 as reference-only typed ReaderItems.
- Completed the pre-Provider Capsule closure vertical: Stage 03 now reopens frozen bytes and closes Flow, Fact/atom, Proof, EvidenceSpan, projection-obligation, budget and Capsule identities before any Flow can reach the Provider.
- Independently re-ran 59 Stage 03 tests and 79 Stage 01/02 regressions with zero failures or errors.
- Adjudicated the final Capsule review at the public deep-module boundary: Stage01/02 replay already owns graph, identity/profile and expectation-Gap provenance; the remaining symlink/TOCTOU concern is future shared source-reader hardening for concurrently mutable roots, outside the accepted frozen-immutable profile.
- Closed Stage 03 durable documentation against the accepted bounded profile; target architecture, current maturity and residual capability gaps are now separated in DESIGN, README and the Stage 03 detailed design.
- Added the Sol/ultra Stage 04 M8 detailed design for runtime lifecycle, immutable Candidate archive, deterministic validate/Trace, recovery and shared Java/CLI/loopback HTTP orchestration.
- Completed Stage 04 Slice A RED→GREEN: rootless canonical request/series/Candidate lineage identities and the immutable Reader Round slot event fold are implemented in an in-memory seam; three direct tests pass.
- Completed Stage 04 Slice B RED→GREEN: the lifecycle Provider bridge persists/ACKs `thread.started` before content delivery, permits only two explicitly proven no-start retries, and makes ambiguous/post-start failures terminal.
- Completed Stage 04 Slice C1/C2 RED→GREEN: the immutable store installs the exact 17 sidecars plus canonical manifest atomically, and the assembler projects an honest Stage01→Stage03 result into a rootless, content-addressed Candidate without trimming the complete ProofPack.
- Completed Stage 04 Slice D1 RED→GREEN: fresh validation reopens the registered source with zero Provider calls, writes idempotent append-only receipts outside the Candidate, and factual Trace follows ReaderItem→atom→Fact→Proof→ProofNode→exact reopened source span.
- Completed Stage 04 Slice D2 RED→GREEN for the current archive-v1 boundary: all five ReaderItem trace kinds close over archived IDs; Fact-backed paths reopen exact source, while Gap and built-in empty-section fallback paths cannot fabricate Fact/Proof/source evidence.
- Audited the archive replay preimage and found the current exact 17-file archive insufficient for zero-Provider Stage 03 replay or complete term/fallback proof. The Sol/ultra Stage 04 contract is now corrected to archive-v2 exact 19 non-manifest artifacts, including full registries and per-Flow canonical model rounds, before persisted recovery or Adapter work proceeds.
- Implemented the Stage 03-owned deterministic replay seam: generation captures canonical R1/R2 rounds, replay has no Provider parameter, and both routes share the strict parser/admission/M6/M7/renderer/result core. The narrow replay selector is 10/10 green and all Stage 03 selectors are 69/69 green.
- Implemented the Stage 04 per-model-round lifecycle transcript seam: the first started event consumes the Reader Candidate slot, later R1/R2/Flow starts append same-state audit events, and sealed transcripts retain the adapter's real preflight, attempt, started-event, task, response and runtime fields. No lifecycle receipt IDs are inferred by the archive assembler.
- Completed archive-v2 RED→GREEN: the Candidate is now exactly 19 non-manifest artifacts plus a v2 manifest, binds full rootless Stage01/02/03 controls, frozen registries, canonical model rounds, real lifecycle receipts and five archive roots, and fresh validation calls Stage03 deterministic replay with zero Provider calls. Legacy 17-artifact archives and every tested preimage drift fail closed.
- Completed persisted Candidate series and crash recovery: canonical slot/event state survives a new process, begun ambiguity never replays a Provider, and a fully valid installed archive can close through `RECOVERED_COMPLETION`.
- Completed the public Java core: one `CodeToMarkdownAgent` path now owns registered-source generation, Stage01→Stage03 orchestration, lifecycle sealing, archive-v2 installation, fresh validation and typed Trace. Repeated completed generation is content-idempotent and the Round-2 seam remains explicitly unavailable rather than silently falling back.
- Added public-seam RED contracts for the target CLI and loopback HTTP Adapter. The expected RED is limited to the absent injected `CodeMdCli(CodeToMarkdownAgent)` and `LoopbackHttpServer` seams; production implementation is in progress.
- Completed the target CLI and authenticated loopback HTTP Adapter GREEN: mutation uses the four-method Agent, immutable reads use the explicit `CandidateArtifactReader`, config is finite/exact, HTTP has one bounded generation worker, idempotent runs, bearer auth, body limits and no CORS.
- Diagnosed and fixed the HTTP transport-status collision: Candidate status no longer overwrites `QUEUED/RUNNING/COMPLETED/FAILED` in run responses.
- Closed the missing Round-2 review-input design with canonical append-only review findings and a finite per-Flow improvement overlay; the first public type-seam RED is active.
- Completed the canonical public review-finding records and exact `ReviewFindingSet` identity.
- Completed the append-only filesystem review store: real Round-1 validation, Flow/reader/section closure, canonical atomic finding persistence, idempotent record and exact approved-set resolution are GREEN.
- Added the final Round-2 behavioral RED covering frozen basis, finite per-Flow overlay, two new model rounds for one affected Flow, lineage, idempotency and unique-slot conflict.
- Completed the bounded Round-2 implementation and corrected the test to read archive-v2 `task.inputJson` as its contractual JSON object; the direct Round-2 selector is now GREEN.
- Closed the Stage 04 path/registry/recovery security slice: exhaustive input-path symlink rejection, ambiguous registration rejection and completed-Candidate deterministic readmission are GREEN without Provider calls.
- Re-ran the complete Stage 04 selector (67 tests), Stage 03 selector (69 tests) and Stage 01/02 selector (79 tests), all with zero failures, errors or skips.
- Completed an independent Sol/xhigh source review. It found no P0 and seven P1 gaps not covered by the green suite: Candidate address redirection, coherent archive/identity tamper, incomplete typed Trace closure, missing public recovery dispatch, unaffected-Flow Round-2 replay, unverified fatal addendum and pre-allocation resource limits.
- Converted all seven P1 groups into public-seam Luna RED tests and implemented Terra GREEN fixes: exact Candidate addressing, replay byte comparison and audit identity closure, deeper Trace, public retry/recovery, unaffected-Flow reuse, typed addendum fail-closed seam and bounded no-follow readers.
- Systematically debugged a final rootless-content regression caused by series-local started-event/receipt IDs entering `candidateContentId`; content now uses a stable model projection while full audit roots remain independently manifest- and validation-bound.
- Final direct verification is GREEN: Stage 04 80/80, Stage 03 69/69 and Stage 01/02 79/79.
- Converted the second independent-review residuals into final-audit RED tests and closed durable receipt-to-ledger resolution, full fallback/empty/Gap Trace provenance, all remaining bounded reads, Round-2 lifecycle dispatch and receipt-bound filesystem corrective addenda.
- Sol/xhigh debugged two recovery regressions without changing tests: recovery validates but does not persist the deterministic started-event suffix, and reused Round-1 receipts resolve to the exact parent slot.
- Latest final verification is GREEN: Stage 04 90/90, Stage 03 69/69 and Stage 01/02 79/79.
- Completed the third independent Stage 04 source review. It found no P0, but kept four bounded-v0 P1 groups open or partial: exact ledger/receipt closure, complete typed Trace provenance, fatal corrective-addendum trust, and source/directory cardinality bounds.
- Converted the ledger/addendum findings into five direct tests. Four are intentional REDs (missing ledger, task-only receipt rewrite, missing real `THREAD_STARTED`, and caller-self-signed fatal addendum); provider preflight/attempt/policy mutation is already rejected by the existing closure.
- Corrected the persisted-recovery fixture to persist every real `THREAD_STARTED` event and omit only completion; the nine direct recovery tests remain GREEN.
- Closed the ledger/addendum fourth-audit production slice: ordinary Candidate validation now requires verified durable lifecycle evidence, generation receipts bind task/provider policy identity, recovery cannot synthesize a missing started-event suffix, and bounded-v0 fatal improvement remains disabled even when a filesystem addendum store is injected.
- Corrected the shared Candidate test fixture so `create()` does not pre-reserve a foreign archive series, while `install()` replays the exact real slot events into the durable archive ledger before installation.
- Stabilized the remaining Trace/resource tests: three exact Trace-reference assertions are RED, candidate-directory cardinality is RED, and source-reopen/source-registry/ledger cardinality limits are already GREEN.
- Closed the final Trace/resource slice: term, fallback and all three Gap families now have explicit frozen-reference provenance; Candidate directories are bounded at 256 entries before collection.
- Migrated the remaining historical Stage04 fixtures to the strict durable-ledger contract without changing production admission: valid archive-v2 fixtures persist the real ledger, while missing-ledger tests install the Candidate directly and deliberately omit it.
- Final direct verification is GREEN across Stage04 104/104, Stage03 69/69 and Stage01/02 79/79. A final independent Sol/xhigh acceptance review is now the only remaining engineering gate before Sol/ultra documentation closeout.
- Completed the fourth independent Sol/xhigh acceptance review. It found no P0 but rejected Stage 04 with four source-level P1s not covered by the 104 green tests: duplicate receipt/model fields are not closed to the nested task; persisted Trace records are not self-describing; several untrusted source/directory paths still allocate before bounds rejection; and post-start assembly/install failure can leave the durable slot `STARTED_CONSUMED`.
- Started a fifth TDD hardening cycle. Luna is converting those four P1s into bounded adversarial RED tests before any further production change.
- Closed the fifth- and sixth-round findings through public-seam RED→GREEN slices: nested task/model/receipt closure, self-describing persisted Trace including exact `EMPTY_SECTION` refs, shared/inline pre-allocation resource bounds, and Round-1/Round-2 post-start terminalization.
- Completed an exclusive clean rebuild after discarding one contended Maven run: Stage 04 111/111, Stage 03 69/69 and Stage 01/02 79/79 are GREEN with zero failures/errors/skips.
- Completed the sixth independent Sol/xhigh acceptance review: Stage 04 bounded v0 is ACCEPTED with P0=0, P1=0 and one non-blocking documentation-precision P2.
- Sol/ultra synchronized `DESIGN.md`, `README.md` and the Stage 04 detailed design to the accepted state, corrected the sole P2, validated 63 local links and passed `git diff --check`.

## Current state

- Stage 00 POC exists with diagnostic discovery/analysis seams and a manual Manifest-to-candidate path.
- Stage 01 M1–M3, Stage 02 M4, Stage 03 M5–M7 and Stage 04 M8 are implemented and accepted within the documented bounded v0 profile. The result remains limited to local registered frozen Java/Spring MVC/MyBatis static-v0 inputs, scripted Provider execution, a single-machine filesystem and unpublished Candidates; live Provider, remote capture, arbitrary repositories, deployment and publication remain outside this acceptance.

## Changed files

- `progress/target-architecture-implementation.md`
- `docs/stages/01-proven-source-facts.md`
- `progress/stage01-proven-source-facts-design.md`
- `progress/stage01-snapshot-tests.md`
- `progress/stage01-snapshot-core.md`
- `src/test/java/com/linguan/codemd/stage01/`
- `src/test/resources/stage01/reservation-v1/`
- `src/main/java/com/linguan/codemd/stage01/`
- `progress/stage01-repository-tests.md`
- `progress/stage01-repository-core.md`
- `progress/stage01-proof-tests.md`
- `progress/stage01-proof-core.md`
- `progress/stage01-integrity-core.md`
- `progress/stage01-proof-integrity-core.md`
- `progress/stage01-jsherp-acceptance-tests.md`
- `progress/stage01-documentation-closeout.md`
- `src/main/java/com/linguan/codemd/stage01/`
- `src/test/java/com/linguan/codemd/stage01/`
- `README.md`
- `DESIGN.md`
- `docs/stages/02-flow-compilation.md`
- `progress/stage02-flow-compilation-design.md`
- `progress/stage02-flow-compilation-tests.md`
- `progress/stage02-flow-compilation-core.md`
- `progress/stage02-code-review.md`
- `src/main/java/com/linguan/codemd/stage02/`
- `src/test/java/com/linguan/codemd/stage02/`
- `docs/stages/03-nine-section-generation.md`
- `progress/stage03-nine-section-generation-design.md`
- `progress/stage03-nine-section-generation-tests.md`
- `progress/stage03-nine-section-generation-core.md`
- `progress/stage03-code-review.md`
- `src/main/java/com/linguan/codemd/stage03/`
- `src/test/java/com/linguan/codemd/stage03/`
- `progress/stage03-*-tests.md`
- `progress/stage03-*-core.md`
- `progress/stage03-*-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn test` | PASS | 28 tests, 0 failures, 0 errors |
| `git status --short --branch` | PASS | On `codex/github-code-design-walkthrough`; two unrelated dirty docs preserved |
| Stage 01 design checks | PASS | 357 lines; links, 22 fences, whitespace and `git diff --check` passed |
| `mvn -Dtest=VerifiedSnapshotContractTest test` | PASS | 7 tests, 0 failures, 0 errors |
| `mvn -Dtest=ManifestEvidenceVerificationTest test` | PASS | 2 tests, 0 failures, 0 errors |
| M2 targeted selector | PASS | 18 tests, 0 failures, 0 errors |
| `mvn -Dtest=RepositoryDiscovererTest test` | PASS | 3 tests, 0 failures, 0 errors |
| Stage 01 combined direct selector | PASS | 51 tests, 0 failures, 0 errors, 0 skipped |
| Fixed jshERP checkout pre/post verification | PASS | HEAD `8c30ce7861570458920175e200bb2a6442713580`; status empty |
| `git diff --check` | PASS | No whitespace errors after Stage 01 closeout |
| Stage 01 + Stage 02 combined direct selector | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| Stage 02 independent review | PASS after hardening | 0 P0; all actionable P1 regressions converted to tests and closed |
| Stage 01 + Stage 02 + initial Stage 03 direct selectors | PASS with review caveat | 93 tests, 0 failures/errors/skips; Stage 03 semantic-conservation behavior was not covered |
| Stage 03 independent review | NOT ACCEPTED | 1 P0, 14 P1 and 2 P2 findings; new RED cycle started |
| Stage 03 hardened direct selector | PASS with scope caveat | 46 tests, 0 failures, 0 errors, 0 skipped; bounded one-Flow and zero-Capsule paths only |
| Stage 01/02 post-hardening regression selector | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| `git diff --check` after Stage 03 task/body hardening | PASS | Exit 0; no whitespace errors |
| Stage 03 two-Flow fixture first run | RED | 1 test; 0 Flow/0 Capsule because both entries were `FLOW_FACT_NOT_ADMITTED` |
| Stage 01 per-entry closure vertical | PASS | Two independent entries now produce 2 FlowSlices/2 EvidenceCapsules; 79 upstream regression tests pass |
| Stage 03 post-M6 direct selector | PASS with contract caveat | 51 tests, 0 failures/errors/skips; `ObjectRelation` still cannot expose relation kind |
| `git diff --check` after multi-Flow hardening | PASS | Exit 0 |
| Stage 03 frozen technical-template selector | PASS | 2 tests; valid template propagates through Markdown and missing key fails pre-Provider |
| Stage 03 repository FlowGap isolation selector | PASS | 3 tests; two compiled Flows exclude a third entry's blocking Gap while repository accounting retains it |
| Stage 03 reader-density selector | PASS | 1 integrated dual-Flow test; sections 3/4/6/8 own typed M6 references without duplicate atom ownership |
| Stage 03 Capsule closure selector | PASS | 2 tests, 0 failures/errors; honest closure plus seven independent mutations |
| Latest Stage 03 direct selector | PASS | 59 tests, 0 failures, 0 errors, 0 skipped |
| Latest Stage 01/02 regression selector | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| Stage 03 Capsule closure adjudication | ACCEPTED for bounded profile | Graph, identity/profile and Gap provenance close through mandatory replay; concurrently mutable-root symlink/TOCTOU remains future shared source-reader hardening |
| Stage 04 detailed design | PASS | 558-line Sol/ultra M8 design; links, fences and whitespace checks pass; M8 remains NOT IMPLEMENTED as a complete stage |
| Stage 04 Slice A selector | PASS | `Stage04IdentityLedgerTest`: 3 tests, 0 failures/errors; canonical identity and Reader Round slot fold only |
| Stage 04 Slices A–D1 selector | PASS | 17 tests, 0 failures/errors; lifecycle, immutable store/assembler, fresh validation and factual Trace included |
| Stage 04 Slices A–D2 selector | PASS with archive-v1 caveat | 19 tests, 0 failures/errors/skips; five typed Trace kinds pass, but registry/task/response preimages are not yet archived and full Stage 03 replay is not claimed |
| Stage 04 archive replay audit | DESIGN CORRECTION REQUIRED | Current 17-file archive cannot support zero-Provider Stage 03 replay; archive-v2 must add full registries and per-Flow canonical task/schema/R1/R2 response records |
| Stage 03 deterministic replay selector | PASS | 10 tests, 0 failures/errors; replay has no Provider and reproduces the complete Stage03Result from frozen canonical rounds |
| Stage 04 lifecycle transcript selector | PASS | 2 tests, 0 failures/errors; real R1/R2 lifecycle receipts close over the generated canonical rounds |
| Stage 04 archive-v2 direct selector | PASS | 39 tests, 0 failures/errors/skips; exact 19-artifact archive, deterministic replay validation, lifecycle and typed Trace |
| Stage 04 persisted recovery selector | PASS | 9 tests, 0 failures/errors/skips; durable slot/event fold, conservative crash recovery and recovered completion |
| Stage 04 public Java core selector | PASS | 9 tests, 0 failures/errors/skips; registered source, generation, idempotency, validation and Trace use one core |
| Latest Stage 04 direct selector | PASS before Adapter RED | 57 tests, 0 failures/errors/skips |
| Stage 04 CLI/HTTP Adapter selector | RED (expected) | Main code compiles; test compilation is blocked only by the two intentionally absent target Adapter seams |
| Stage 04 CLI/HTTP Adapter final selector | PASS | 4 tests, 0 failures/errors; loopback test used the approved local-bind capability only |
| Latest Stage 04 selector | PASS except planned Round-2 RED | 61 of 62 tests green; only `Stage04ImprovementTest` intentionally fails for the absent public review-store seam |
| Review-finding type selector | PASS | 1 test, 0 failures/errors; public canonical review seam exists |
| Filesystem review-store selector | PASS | 1 test, 0 failures/errors; real parent/receipt/reference closure and canonical persistence |
| Round-2 orchestration selector | PASS | 1 test, 0 failures/errors/skips; frozen basis, finite Flow overlay, parent/finding lineage, two new model rounds and idempotency are covered |
| Stage 04 security selector | PASS | 3 tests; path ancestry, registration ambiguity and completed-Candidate tamper recovery fail closed |
| Final Stage 04 direct selector | PASS | 67 tests, 0 failures, 0 errors, 0 skipped |
| Final Stage 03 regression selector | PASS | 69 tests, 0 failures, 0 errors, 0 skipped |
| Final Stage 01/02 regression selector | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| P1 validation/Trace/resource hardening | PASS | 5 direct tests plus 4 residual tests; exact addressing, coherent tamper, Trace and bounded reads |
| P1 lifecycle/Round-2/addendum hardening | PASS | 4 direct tests; retry/recovery, unaffected-Flow reuse and fatal addendum fail closed |
| Post-hardening Stage 04 direct selector | PASS | 80 tests, 0 failures, 0 errors, 0 skipped |
| Post-hardening Stage 03 regression selector | PASS | 69 tests, 0 failures, 0 errors, 0 skipped |
| Post-hardening Stage 01/02 regression selector | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| Final-audit Stage 04 direct selector | PASS | 90 tests, 0 failures, 0 errors, 0 skipped |
| Final-audit Stage 03 regression selector | PASS | 69 tests, 0 failures, 0 errors, 0 skipped |
| Final-audit Stage 01/02 regression selector | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| Third Stage 04 independent source review | NOT ACCEPTED | 0 P0; four P1 groups remain OPEN/PARTIAL across ledger/receipt, Trace, fatal addendum trust and resource bounds |
| Ledger/addendum fourth-audit selector | RED (expected) | 5 tests: 4 failures, 0 errors; one existing receipt-policy mutation check already passes |
| Corrected persisted-recovery selector | PASS | 9 tests, 0 failures, 0 errors, 0 skipped; all real started events are durable before completion recovery |
| Ledger/addendum fourth-audit selector after GREEN | PASS | 5 tests, 0 failures, 0 errors, 0 skipped |
| Ledger/addendum affected regression selector | PASS | 21 tests across FinalAuditTrace, ResidualP1, LifecycleRound2Hardening, Round2, Improvement and PublicCore |
| Typed Trace fixture regression | PASS | 2 tests, 0 failures, 0 errors after exact durable slot replay during fixture install |
| Trace exact-reference selector | RED (expected) | 3 tests, 3 assertion failures: term registry/priority, fallback task/resolution anchor and typed Gap provenance |
| Reopen/directory bounds selector | MIXED (expected) | 3 tests: candidate-directory cardinality RED; source reopen/source-registry/ledger bounds GREEN |
| Final Stage 04 selector after fourth hardening | PASS | 104 tests, 0 failures, 0 errors, 0 skipped; includes loopback HTTP under approved local-bind capability |
| Final Stage 03 regression selector after fourth hardening | PASS | 69 tests, 0 failures, 0 errors, 0 skipped |
| Final Stage 01/02 regression selector after fourth hardening | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| Fourth independent Stage 04 acceptance review | NOT ACCEPTED | 0 P0, 4 P1, 1 P2; green suites remain evidence but do not close the four uncovered source contracts |
| Fifth-audit archive/Trace selector | PASS | 2 tests; coherent duplicate round metadata and persisted self-describing typed Trace are closed |
| Fifth-audit affected archive/Trace regressions | PASS | 29 tests, 0 failures/errors/skips |
| Fifth-audit lifecycle/bounds selector | PASS | 2 tests; Round-1 post-start terminalization and four shared pre-collection directory bounds are GREEN |
| Fifth-audit Round-2 lifecycle selector | PASS | 1 test; a post-start install failure persists `FAILED_AFTER_STARTED`, and fresh re-entry makes zero Provider calls |
| Fifth-audit affected Stage 04 regressions | PASS | 32 tests, 0 failures, 0 errors, 0 skipped |
| Stage 04 full direct selector after fifth hardening | PASS | 109 tests, 0 failures, 0 errors, 0 skipped; loopback HTTP remained local-only |
| Stage 03 regression selector after fifth hardening | PASS | 69 tests, 0 failures, 0 errors, 0 skipped |
| Stage 01/02 regression selector after fifth hardening | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| Fifth independent Stage 04 acceptance review | NOT ACCEPTED | 0 P0, 3 P1, 0 P2: EMPTY_SECTION persisted Trace refs, CandidateStore destination directory bounds/error code, and stale durable Stage04/overall documentation |
| Sixth-audit behavior selector | PASS | 2 tests; addressed Candidate destination stops at the shared entry budget, and EMPTY_SECTION Trace persists and validates exact section/profile/template refs |
| Sixth-audit affected regressions | PASS | CandidateStore/reopen/security 12/12; typed/validation/reference/fifth-audit Trace 9/9 |
| Exclusive Stage 04 clean rebuild after sixth hardening | PASS | 111 tests, 0 failures, 0 errors, 0 skipped; prior contended run was discarded and `target/` rebuilt exclusively |
| Stage 03 regression selector after sixth hardening | PASS | 69 tests, 0 failures, 0 errors, 0 skipped |
| Stage 01/02 regression selector after sixth hardening | PASS | 79 tests, 0 failures, 0 errors, 0 skipped |
| Sixth independent Stage 04 acceptance review | ACCEPT | P0=0, P1=0, P2=1; bounded-v0 acceptance gate satisfied |
| Accepted-state documentation finalization | PASS | Three durable documents synchronized; sole P2 corrected; 63 local links and `git diff --check` pass |

## Decisions

- Execute the target M1→M8 path in four stage-sized vertical outcomes; do not turn the POC baseline into a second production route.
- All new behavior follows strict RED→GREEN→REFACTOR TDD.
- Stage 01 and 02 make no model calls.
- A fixture SHA mismatch is repaired only in the independent test manifest; source verification is never weakened to accommodate fixture drift.
- Review findings become failing tests before production fixes; five M2 P1 findings were closed through a second RED→GREEN cycle.
- Stage 01 acceptance is deliberately bounded: it proves the declared eight-file jshERP slice and does not claim whole-repository coverage or zero Gap.
- Stage 02 consumes only `Stage01Result`; it must not rescan source directories, invoke a model, or treat the legacy POC Flow Manifest as a production input.
- Stage 02 replays the exact `Stage01Request` to reopen frozen bytes and compares `expectedStage01ResultId`; it never accepts a naked active directory.
- A shared service cannot cause multiple entry-root flows to duplicate Fact/atom ownership; ambiguous secondary ownership is conservatively GAPed.
- A green heading/SHA smoke test is not Stage 03 acceptance. The final Markdown must be rendered from typed `ReaderItem` ownership and basis; registry contents, model-task allowlists and R1/R2 closure must be deterministically validated.
- Cross-Flow claims must use the two-independent-entry public fixture; fabricated Stage 02 results or single-Flow vacuous assertions remain forbidden.
- A non-empty `ObjectRelation` list does not by itself close the typed-relation design requirement. The current record exposes endpoints and atom basis but no public relation kind.
- The external Stage 03 seam accepts a replayable `Stage02Request`, not caller-constructed Stage01/02 records. Stage 03-local Capsule validation must not duplicate the full upstream identity and repository-graph algorithms; future cached-record seams require their own admission contract.
- The accepted Stage 03 profile requires a frozen source root that is not concurrently mutated during a run. A future mutable/untrusted-root profile must centralize all source reopening behind one no-follow, resource-bounded verified-source module.
- Ruling: target CLI/HTTP command translation keeps the four-method generation `CodeToMarkdownAgent` separate from immutable artifact reads. `candidate` and Markdown GET require an explicit injected read-only `CandidateArtifactReader`; tests may not rely on a private fake extension, reflection, an empty configured workspace or hard-coded document bytes.

## Blockers

- None. The approved four-stage bounded-v0 implementation and documentation closeout are complete.

## Exact next action

- None for the approved plan. Future live Provider, remote capture, broader framework/language coverage, deployment and publication require separately authorized stages.

## Resume checks

- Read this file and `docs/stages/01-proven-source-facts.md`.
- Run `git status --short --branch` and preserve unrelated files.
- Run the 59-test Stage 03 selector and the 79-test Stage 01/02 selector before changing cross-stage ownership contracts.
- Run `Stage04IdentityLedgerTest` before changing Candidate series identity or Reader Round slot transitions.
- Run `mvn -Dtest='Stage04*Test' test` before changing lifecycle, archive, validation or Trace contracts.
