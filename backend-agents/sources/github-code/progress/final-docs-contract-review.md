# Progress: final docs contract review

- Status: COMPLETE
- Review round: 2, independent fresh review of current revised bytes
- Agent role: read-only final documentation architecture reviewer
- Completed: 2026-09-01
- Scope: `docs/DESIGN.md` and `docs/stages/01-*.md` through `08-*.md`, with focused checks for Stage06 slot/outcome accounting, R0 registry readiness, stage receipt boundaries, public artifact/Trace safety, Stage08 M1 preimages and final-ledger DAG/equality, artifact policy v2, typed views, stable failure codes, public-output/module counts, and exact/example syntax.
- Execution limits observed: no Maven, network, model, source-repository, customer-build, or product-runtime call; no design, stage, code, test, or POM edit.
- Changed file: `progress/final-docs-contract-review.md` only.

## Verdict

- P0: none.
- P1: 2 current contract contradictions.
- P2: 2 current specification synchronization defects.

## P1 findings

1. **A planned R2 slot can be skipped after R1 failure, but the exact lifecycle cannot encode that promised terminal outcome.**
   - Stage06 fixes the planning denominator at `E+2R` and requires a durable terminal outcome for every slot (`docs/stages/06-interpret-one-flow-at-a-time.md:11,43,63,184-186`). M5 executes R1 before R2 (`:196-201`), and the failure contract explicitly allows R1/R2 to stop or be `skipped` while still requiring the skipped task to retain a terminal outcome (`:114,396`). It also describes a mere confirmed-no-start as one of the terminal cases (`:41`).
   - The exact lifecycle has no dependency-skip terminal variant: `MODEL_SLOT_TERMINAL` is only `SUCCEEDED | GAP | FAILED | PRESTART_EXHAUSTED` (`docs/DESIGN.md:882-887`; `docs/stages/08-build-nine-section-document-and-archive.md:740-744`); `RoundSlotOutcomeV1.startedEventId` may be null only for `PRESTART_EXHAUSTED`, with the same four-value outcome enum (`docs/DESIGN.md:918-928`; Stage08 `:749-767`); and the folded-state closure has no skipped state (`docs/DESIGN.md:942`; Stage08 `:769`). A confirmed-no-start is retryable until exhaustion, not itself terminal (`docs/DESIGN.md:870-874,942`).
   - Impact: after an isolated R1 GAP/FAILED, the already planned R2 task can neither be called legitimately nor receive any schema-valid no-call terminal outcome, so the required `E+2R` closure cannot be built.
   - Minimal correction: add one explicit dependency-skip terminal outcome/state (including null-start/attempt rules and a stable reason code) to the event, `RoundSlotOutcomeV1`, ledger fold, Stage06 disposition/accounting, and Stage08 mirror; alternatively remove precompiled R2 from the denominator and revise every `2R/E+2R` assertion. The first option preserves the stated plan.

2. **Stage08 M1 simultaneously forbids and requires reopening a Stage06 semantic artifact.**
   - Stage08 says M1 does not reopen Stage01-06 original stage bytes and only validates their typed publication references (`docs/stages/08-build-nine-section-document-and-archive.md:55`); the M1 exact input paragraph repeats that boundary (`:156`). DESIGN states the same division of responsibility (`docs/DESIGN.md:694`).
   - Yet the same exact M1 input paragraph includes `repository-interpretation-registry` among the content-addressed refs M1 reads (`docs/stages/08-build-nine-section-document-and-archive.md:156`), and the authoritative direct-preimage row includes that Stage06 semantic `ArtifactReference` (`:371`). DESIGN requires every semantic byte actually read to be named in the direct preimage and forbids substituting a transitive root/receipt (`docs/DESIGN.md:1826`).
   - Impact: two conforming implementations can choose different direct byte sets and therefore different module identities; the claimed external-validator boundary is not implementable uniquely.
   - Minimal correction: if M1 only needs lineage and the typed registry ref already carried by the Stage07 draft/preparation, remove `repository-interpretation-registry` from M1's read set/direct preimage. Otherwise state explicitly that this one Stage06 semantic artifact is the sole Stage01-06 byte M1 reopens, and narrow the no-reopen claim accordingly.

## P2 findings

1. **The duplicated-ledger mismatch code is omitted from both consolidated stable-code lists.** The equality rules emit `REPOSITORY_COVERAGE_LEDGER_INVALID` (`docs/DESIGN.md:362-367`; `docs/stages/08-build-nine-section-document-and-archive.md:357-365`), but that token is absent from the DESIGN runtime/08 family (`docs/DESIGN.md:2044-2059`) and Stage08's stable-code list (`docs/stages/08-build-nine-section-document-and-archive.md:802-804`). DESIGN permits finer codes (`docs/DESIGN.md:2059`), so the behavior is not undefined, but the stable registry is out of sync. Minimal correction: add the code to both lists (or replace it consistently with one already declared integrity code).

2. **Stage08's public `ArtifactView` mirror drops two closed types present in DESIGN.** DESIGN types `mediaType` and `validationState` as closed enums (`docs/DESIGN.md:1543-1544`), while Stage08's public record leaves both bare (`docs/stages/08-build-nine-section-document-and-archive.md:612-613`). `immutableReference: ArtifactReference` and `publicContentExposure` now agree (`docs/DESIGN.md:1545-1546`; Stage08 `:614-615`). Minimal correction: copy the DESIGN enum annotations into the Stage08 record.

## PASS evidence

- **E/R planning versus started calls:** except for the dependency-skip hole above, target statements consistently distinguish `E+2R` planned slots/terminal outcomes from started rounds/calls, with `3E` only for all-ready/all-started (`docs/DESIGN.md:164,187,249,621,2010,2069`; Stage06 `:11,63,214,404,412`). No stale `3N` assertion remains.
- **R0 READY closure:** `READY_FOR_FREEZE` iff `registryProposalIds[]` is non-empty and every referenced proposal is accepted, same-Flow, and Capsule-basis-closed; GAP/FAILED requires an empty proposal list (`docs/DESIGN.md:1111-1122`; Stage06 `:286-293`). Only those READY flows enter `R` and receive R1/R2 (`docs/stages/06-interpret-one-flow-at-a-time.md:42-43,182-190`).
- **Receipt/accounting separation:** DESIGN explicitly confines denominator, zero-count, partition, and conservation equations to named semantic coverage/accounting payloads, while receipts carry status, Gap refs/count, provenance, controls, and descriptors (`docs/DESIGN.md:170-174,728-801`). Stage06 follows that split (`docs/stages/06-interpret-one-flow-at-a-time.md:50-63`); Stage07 does likewise (`docs/stages/07-admit-and-merge-business-knowledge.md:61-68`).
- **ArtifactQuery/Trace path boundary:** complete bytes require policy `PATH_FREE_COMPLETE_UTF8`; raw Trace/source/prompt/raw-response artifacts are forced to `METADATA_ONLY` and fail with `ARTIFACT_CONTENT_NOT_PUBLIC`; `TraceView` is the unique fresh-validated path-free source-hop projection (`docs/DESIGN.md:1535-1547,1601-1603`; Stage08 `:604-616,660-662,820`). No public query/view selector contains a filesystem `Path`.
- **Final-ledger DAG and duplicate equality:** draft -> Stage08 preparation -> final ledger -> plan is one-way; future Stage08 identities are excluded (`docs/DESIGN.md:255-266,360`; Stage08 `:351-355`). The three duplicated fields are explicitly compared before identity, including full typed draft ref, ordered reader IDs, and complete owner map (`docs/DESIGN.md:362-367`; Stage08 `:357-365`).
- **Artifact policy v2:** only `artifact-policy-registry-v2` / `canonical-artifact-policy-registry-id-v2` occurs; exact fields include `publicContentExposure`, closed enums, sorting, self-ID exclusion, and reference SHA (`docs/DESIGN.md:1830-1832,1983`). No v1 policy term remains.
- **Public-output/module counts:** stage tables contain `[4,5,8,5,6,10,6,8]` outputs, totaling **52** (`docs/stages/01-freeze-source.md:86-91`; Stage02 `:60-66`; Stage03 `:65-74`; Stage04 `:53-59`; Stage05 `:59-66`; Stage06 `:50-61`; Stage07 `:59-68`; Stage08 `:69-80`). The compiled module registry contains `[3,4,6,3,3,6,3,4]` stage modules = **32**, plus `run-validator` and `run-resumer` = **34** total addresses; the persisted publisher key is consistently `publish` (`docs/DESIGN.md:1855-1870`).
- **Examples and syntax:** all scoped narrative examples explicitly disclaim wire/schema/replay use under the three-label DESIGN taxonomy; no `WIRE_EXACT` fourth label remains. All 27 JSON/JSONL fences (66 JSON values) parse with `jq`; every scoped file has balanced `~~~` fences. The sole strict renderer byte golden recomputes to 150 UTF-8 bytes and SHA-256 `67e742a00672cbd230b230b4c29ef36a0aa9aa5ea2b6c460e286160e58fca4e5` (`docs/stages/08-build-nine-section-document-and-archive.md:402-419`). No schema/example P0/P1 was found.
- **Historical/current audit separation:** the implementation audit is explicitly segregated from target design (`docs/DESIGN.md:2150-2153`) and now describes the Stage06 gap using `E+2R`/started-call-subset terms (`:2161`).

## Command evidence

| Read-only check | Current result |
| --- | --- |
| Complete reads of all three applicable `AGENTS.md` files and the codebase-design skill | PASS |
| Targeted `rg -n` over slot/outcome, preimage, ledger equality, receipt, policy/view, code, Path, taxonomy, module key, and stale-version terms | Findings and PASS lines above |
| JSON/JSONL fence extraction piped to `jq -e -s 'length'` | `66` values; exit 0 |
| Per-file `rg -c '^~~~'` parity | all nine scoped docs balanced |
| Strict renderer `documentUtf8` piped to `wc -c` / `shasum -a 256` | `150`; expected SHA match |
| Public-file table recount | `4+5+8+5+6+10+6+8 = 52` |

## Blockers

- Review blocker: none.
- Architecture release blocker: the two P1 contradictions above should be resolved before implementing their affected Stage06 lifecycle and Stage08 M1 slices.
