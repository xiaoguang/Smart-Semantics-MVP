# Progress: target persistence exact-fixtures review

- Status: COMPLETE
- Verdict: REJECT
- Severity: P0 = 0; P1 = 16
- Role/model: independent reviewer A; gpt-5.6-sol / ultra
- Date: 2026-08-31
- Worktree/scope: `/private/tmp/linguan-github-code-target-implementation`, read-only audit of `docs/DESIGN.md` and stages 01–08; this is the only reviewer-owned writable file
- Constraints observed: no docs/code/tests/POM/ignore edits; no Maven, network, model, source, commit, or push

## Basis and evidence

- Read root/backend/scoped `AGENTS.md` completely (278 + 84 + 232 lines), then the current DESIGN and eight stage documents directly; no earlier review report was used.
- Parsed all 73 fences: 33 text, 12 java, 12 json, 16 jsonl. All 76 JSON documents/records parsed; zero malformed/unclosed fences.
- Structurally checked all 27 exact ModuleArtifact envelopes (25 STAGE, 1 VALIDATION, 1 RESUME): zero errors for exact envelope keys, exactly five ArtifactControls keys, address-variant fields, ID/reference grammar, upstream sort/dedup, producer/module registry, or exterior producer address.
- Rechecked DESIGN identity/framing/ordering/self-exclusion at lines 1379–1449: internally complete and non-circular. Stage08 M4's eight stage refs/order/singular-equals-item-8 and seven-STAGE_DIRECTORY/one-RUN_ROOT location split also pass.
- `git diff --check` before final write: exit 0, no output.
- Renderer literal recomputation: `"# 文档说明\n...\n"` is 19 UTF-8 bytes, SHA-256 `40764b14be1191a8e88b5a36bdb1e045bf6cc3b4ee000e1117c755e388a05d4c`.

## Complete P1 list

1. **Stage03 receipt loses direct stage lineage.** DESIGN's exact `stage-receipt-v1` fixture has `upstreamStageReferences: []` (`docs/DESIGN.md:627-675`, specifically 653), although Stage03 consumes Stage01/02 (`docs/stages/03-build-five-program-graphs.md:265-268`). This violates `StageInstallRequest` and receipt ordering (`docs/DESIGN.md:1275-1287,1449`). Smallest fix: insert complete typed Stage01 and Stage02 publication references in stage order.

2. **Stage01's five-line exact fixture is reference-incoherent and M3 has hidden preimage.** M3 is declared to bind M1 `source-request:111.../sha=999...` plus M2 `source-index:222.../sha=222...` (`docs/stages/01-freeze-source.md:262`), but M2 binds that M1 at SHA `111...` (line 266); M1 binds frozen request `bbbb...` while M3 emits `aaaa...` (lines 265,267). M3 also emits profile/resource/toolchain/schema/prompt refs absent from its two direct inputs. Transitive run-request reads are forbidden (`docs/DESIGN.md:1300`). Smallest fix: use one reference set and directly bind the run request (or a direct artifact containing every emitted ref) in M3, then update its receipt declaration.

3. **Typed SourceLocator is replaced by incompatible strings and synthetic excerpts.** The required shape is path + UTF-8 bytes + 1-based/exclusive line/columns (`docs/stages/01-freeze-source.md:302`; `docs/DESIGN.md:1108`). Exact Stage02 profile/entry/site/evidence fields use path strings (`docs/stages/02-discover-application-and-entries.md:207-212,219-221`); Stage03 SOURCE_SPAN is a `canonicalValue` string (`docs/stages/03-build-five-program-graphs.md:244,260`); Stage05 uses discontiguous strings and inserts ` + ` / `...` despite requiring exact raw locator bytes (`docs/stages/05-compile-business-flows.md:192,197,203`); Stage08 repeats path strings as hop IDs (`docs/stages/08-build-nine-section-document-and-archive.md:265,270`). Smallest fix: one typed locator record everywhere, split discontinuous spans, preserve literal bytes, and bump affected schemas.

4. **Stage02 capability/profile key typing is open and ambiguous.** `frameworkSignals[].kind`, `configSignals[].kind`, locator, and `CapabilitySite.evidenceRefs[]` element type are not closed/typed (`docs/stages/02-discover-application-and-entries.md:207,212,232-271`), though fixtures use them to enable parsers (lines 219-221) and schema evolution is fail-closed (`docs/DESIGN.md:1472`). Smallest fix: closed distinct framework/config enums plus a typed evidence-reference/locator union and schema bumps. Stage03 `graphProfileRef` itself correctly uses a full ArtifactReference (`docs/stages/03-build-five-program-graphs.md:247`).

5. **The exact fixtures do not form one semantic-ID chain.** Stage02 emits profile `application-profile:111...` and entry `entry:222...` (lines 219-221); Stage03 substitutes story IDs (lines 256-260); Stage04 uses unrelated graph/entry/node/evidence/program-edge IDs not present in those exact inputs (lines 191-192); Stage05 switches to another Fact/Proof universe (lines 202-203); Stage08 Trace relies on that incompatible universe (line 270). This violates direct-preimage closure/no implicit remap (`docs/DESIGN.md:1300,1472`). Smallest fix: propagate one ID set byte-for-byte, or persist a declared deterministic remapping artifact/formula.

6. **R1/R2 Provider requests are not recoverable.** `FlowModelTask` persists only `inputJsonSha256`, not `inputJson` (`docs/stages/06-interpret-one-flow-at-a-time.md:209,262-265`); exact M4 has only the hash and M5 consumes only M2/M3/M4 (lines 221-222). A fresh M5 cannot send or verify request bytes. Smallest fix: persist canonical `inputJson` and bump `stage06-flow-task-set-v2`, or bind a direct canonical-input artifact.

7. **`ModelRound.startedReceiptId` points to events, not receipts.** The record distinguishes it from GenerationReceipt `receiptId` and `startedEventId` (`docs/stages/06-interpret-one-flow-at-a-time.md:207,210,267-273`), yet exact lines 219/222 use `started:...` while receipts are `receipt:...`. Smallest fix: use matching receipt IDs, or rename/version the field as `startedEventId`.

8. **FlowInterpretationDisposition has no identity required by coverage.** Both declarations and exact line 222 omit `flowInterpretationDispositionId` (`docs/DESIGN.md:968-974`; stage06:283-285), while RepositoryCoverageLedger requires `flowInterpretationDispositionIds[]` (`docs/DESIGN.md:1036`). Smallest fix: add a deterministic ID/formula with schema bumps, or version the ledger as an explicit flowSlice-key map.

9. **ReaderItem/EMPTY_SECTION is not executable as a typed union.** `readerItemKind` is not closed and only `activity-with-anchor-v1` has a slot schema (`docs/stages/08-build-nine-section-document-and-archive.md:265,346-361`), while line 268 uses six templates. EMPTY must persist sectionKey/effective profileId/templateKey (line 482), but its items only hold reasonCode and the plan drops the nine-section-profile ref; Trace line 270 invents `nine-section-profile:v1` instead of upstream `nine-section-profile:aaaa...`. The two `NO_*` reason codes have no closed registry. Smallest fix: closed ReaderItemKind/template slot union, explicit EMPTY section/profile identity and reason enum, existing profile reference in Trace, then plan/trace version bumps.

10. **Renderer fixture is byte/hash/shape impossible.** The schema says exact `documentUtf8` determines size/SHA (`stage08:238`), but line 269's 19 bytes claim size 2400 and SHA `22...`; it also has only one of nine mandatory headings (`stage08:470-482,492`). Smallest fix: store exact nine-section bytes and their actual size/SHA.

11. **Coverage-ledger identity changes inside one six-record Stage08 fixture.** M1 uses `repository-coverage:eeee...` (`stage08:268`), while M4/validator/resumer use `repository-coverage:aaaa...` (lines 271-273), contrary to the one-ledger plan/manifest/exterior chain (`stage08:237,240-242,265`). Smallest fix: one full ledger reference throughout.

12. **Validator preimage contradicts its algorithm and the global direct-read rule.** It promises fresh Stage01→08/source/ledger/rerender/Trace recomputation (`stage08:203-215`) but binds only six artifacts (`stage08:241,244,272`). DESIGN 1300 forbids replacing read semantic bytes with manifests/roots/transitive refs. Smallest fix: enumerate every artifact actually read, or version a global closure-reference exception and its identity semantics.

13. **ALREADY_FINISHED resumer has the same preimage contradiction.** Its stated algorithm reads/validates module/stage/shard artifacts, events/failures, slots, controls, and ledger (`stage08:219-229`), but the exact variant binds only five artifacts (`stage08:242,244,273`). Smallest fix: bind every read artifact, or explicitly version an early variant that does not claim those validations.

14. **Validation receipt reports roots outside its exact run.** M4 line 271 carries stage roots `111...` through `888...`; validator line 272 reports `0101...` and `0808...` while returning VALID. This violates root/check consistency (`stage08:209,241`). Smallest fix: report the actual validated roots (normally all eight, stage ordered) byte-identical to M4/run manifest.

15. **Validator uses an undeclared failure code.** `REPOSITORY_SCOPE_NOT_COMPLETE` appears only at stage08 line 272 and is absent from the Stage08 list (`stage08:554-556`) and DESIGN runtime/08 family (`docs/DESIGN.md:1531`). Smallest fix: declare it in a closed validation-check code registry with version treatment, or use a declared code.

16. **Public artifact inspection cannot represent exterior validator/resumer addresses.** Exact envelopes correctly use ValidationModuleAddress/ResumeModuleAddress (`stage08:145,272-273`; `docs/DESIGN.md:1246-1249`), but RunInspection assumes stage/module and StageArtifactView requires `stageNumber` (`docs/DESIGN.md:1048-1084`; `stage08:405-441`), conflicting with “do not hide” exterior modules (`stage08:466`). Smallest fix: expose the sealed STAGE/VALIDATION/RESUME publication address in inspection descriptors, query discriminators, and artifact views/adapters.

## Passing areas / residual

- Passing: artifact/reference safe grammar for exact artifact/publication/root/receipt values; all address variants; five-field controls; producer/module registry; upstream lexical order/dedup; explicit completion/lifecycle/result enums other than findings; Stage08 M4 ordering/location cardinality; binary identity/root/receipt/manifest self-exclusion formulas.
- Residual: none intentionally deferred. This report is the complete severity-bearing list from the required corpus; P2/editorial observations were excluded.

## Changed files

- `progress/target-persistence-exact-fixtures-review.md` only.
