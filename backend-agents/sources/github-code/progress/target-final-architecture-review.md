# Progress: Target final architecture review

- Status: COMPLETE
- Verdict: REJECT
- Severity: P0 = 0; P1 = 6; P2 = 0
- Agent role: Independent final P0/P1 architecture documentation reviewer
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-01T03:21:31Z
- Last updated: 2026-09-01T03:47:05Z
- Scope: Read-only final review of the GitHub Code Agent target design corpus after Round-3 corrections; create and update only this progress file.
- Approved inputs: `AGENTS.md`; `backend-agents/AGENTS.md`; scoped `AGENTS.md`; `README.md`; `docs/DESIGN.md`; stages 01-08; `docs/plans/target-standards-and-toolchain-plan.md`; `progress/target-persistence-exact-fixtures-review.md`.
- Current branch/worktree: `codex/github-code-target-implementation` at initial review HEAD `eefab1a`; `/private/tmp/linguan-github-code-target-implementation`.

## Completed

- Read all three applicable `AGENTS.md` instruction files before creating this file.
- Read the complete approved review corpus: README, overall DESIGN, Stages 01-08, the approved standards/toolchain plan, and the prior exact-fixture review.
- Reviewed all ten fixed dimensions against the approved architecture without substituting a new architecture.
- Independently checked local Markdown links/anchors, JSON/JSONL examples, the strict renderer golden, semantic/run artifact counts, module numbering, example taxonomy, exact module envelopes, and the named public/persistence records implicated by the findings.
- Preserved the pre-existing dirty worktree and changed only this progress file.
- Invoked no Maven, network, Provider, customer code, generative product-content run, commit, or push.

## Current state

REJECT. The corpus is materially stronger after Round-3 and has no P0 defect, but six P1 contract gaps still force incompatible implementation choices in repository completion, Stage06 recovery, Stage07 admission, or Stage08 public inspection. These are bounded closure defects within the approved architecture, not requests for a replacement architecture.

## Findings

### P1-1 — The final repository-completion ledger has no acyclic producer or publication contract

- Evidence: `docs/DESIGN.md:189` makes a closed ledger a Stage08 precondition. `docs/DESIGN.md:257-297` requires that same ledger to contain Stage08-created reader item IDs, section ownership, eight stage coverage roots, and a content ID. `docs/stages/08-build-nine-section-document-and-archive.md:11-15` says Stage08 closes the ledger but receives the Stage01-07 ledger draft. The exact M1 row at `docs/stages/08-build-nine-section-document-and-archive.md:268-275` already consumes a `repository-coverage` reference and emits only a plan draft, while the exhaustive output table at `docs/stages/08-build-nine-section-document-and-archive.md:283-300` publishes no ledger artifact. Across the required corpus, `RepositoryCoverageLedger` has no schema version, standalone filename, or output row.
- Why P1: the completed result is required to use a final closed ledger, but an implementation has no defined acyclic point at which that ledger is created and published. It must either reuse a stale Stage01-07 draft, introduce a hidden artifact, or create a Stage08-root dependency cycle.
- Fix boundary: distinguish draft and final ledger contracts; name the Stage08 module/store boundary that writes the final content-addressed ledger after reader ownership is known; define its exact schema/version/ID; state whether `stageCoverageRoots[8]` are pre-publication coverage roots; and bind M1/M3/M4, the stage manifest, validator, and resumer to one final reference. Keep the approved artifact-count architecture authoritative when assigning the producer.

### P1-2 — Stage06 has no exact R0 input schema or evidence-complete projection

- Evidence: `docs/stages/06-interpret-one-flow-at-a-time.md:39-41` and `:341` say R0 receives the canonical Capsule/source evidence. The exact M1 row at `docs/stages/06-interpret-one-flow-at-a-time.md:204-214` leaves the task inputs opaque. `RegistryProposalTask` at `docs/stages/06-interpret-one-flow-at-a-time.md:240-245` names `inputJson` without a type. R1/R2, by contrast, close `FlowModelInputV1` and its full capsule view at `docs/stages/06-interpret-one-flow-at-a-time.md:266-286`. The core task record at `docs/DESIGN.md:923-932` also omits the input body. The only R0-shaped structural example at `docs/stages/06-interpret-one-flow-at-a-time.md:222` contains only `basisAtomIds`, `basisGapIds`, and `outcomePathIds`, not facts, spans, or obligations.
- Why P1: independent implementations can send different R0 bytes and evidence projections while still claiming schema v1. That changes proposal grounding, task identity, receipt matching, and replay behavior.
- Fix boundary: define one exact versioned R0 input record or sealed input union containing the required full Capsule/evidence view plus any optional seed projection; bind its canonical bytes/hash to `RegistryProposalTask`; add fresh-reopen and exact-byte fixtures; bump the schema if already consumed.

### P1-3 — The recoverable R0/R1/R2 slot lifecycle has no durable exact journal

- Evidence: `docs/stages/06-interpret-one-flow-at-a-time.md:134-145` and `:178-187` require a `RoundSlotLedger`/event sink that distinguishes confirmed-no-start, started, terminal, and ambiguous attempts. The exact Stage06 rows at `docs/stages/06-interpret-one-flow-at-a-time.md:204-217` leave `slotStates[]` opaque and explicitly say the ledger is not an `ArtifactReference`. The closed ten-variant run-event union at `docs/DESIGN.md:724-787` contains no model-attempt/`THREAD_STARTED`/confirmed-no-start/ambiguous event. Stage08 recovery at `docs/stages/08-build-nine-section-document-and-archive.md:221-232` and `:603-613` nevertheless requires a fresh process to read and fold the round-slot ledger, while the exact resume preimage at `docs/stages/08-build-nine-section-document-and-archive.md:268-281` names no typed ledger reference or record. No exact `RoundSlotLedger` record/store exists in the required corpus.
- Why P1: after a crash before the M2/M5 receipt, a fresh process cannot prove whether a slot was never started, was started, or is ambiguous. Retrying can violate the no-replay rule; refusing every retry defeats the required confirmed-no-start recovery path.
- Fix boundary: define exact durable slot-journal records, states, attempt/start acknowledgement identity, ordering, atomic append/CAS behavior, fresh-reopen lookup, and typed bindings into Stage06 and the resumer; alternatively version the closed run-event union to carry the same data. Close `slotStates[]` and add crash-boundary fixtures for every state transition.

### P1-4 — Stage07 cannot encode the approved model-ineligible Flow branch

- Evidence: `docs/DESIGN.md:214-237` defines `compiledFlowSlices = modelEligibleFlows + modelIneligibleFlowGaps`. `docs/DESIGN.md:315-319` gives only model-eligible Flows a Stage06 disposition but requires Stage07 to decide every Flow. Stage07 repeats that Stage05 Flow slices equal Stage07 decisions and Stage06 eligibility is a subset at `docs/stages/07-admit-and-merge-business-knowledge.md:338-351`. The exact `FlowAdmissionDecision` contract at `docs/stages/07-admit-and-merge-business-knowledge.md:196-206` and `:228-237` requires a non-null `stage06DispositionId` and `stage06Disposition`; its `decision` enum is not closed.
- Why P1: a Stage05 model-ineligible Flow has no legal v3 Stage07 decision record. An implementation must drop it, fabricate a Stage06 disposition, or redefine the completeness denominator.
- Fix boundary: define a sealed/versioned decision variant or explicit nullability/ID rule covering Stage06 READY/GAP/FAILED and Stage05 model-ineligible Gap/fallback branches, or authoritatively remove the model-ineligible public Flow branch and update the accounting equations. Provide exact fixtures for every variant.

### P1-5 — The public path-free `ArtifactView` address cannot represent stage-owned or root artifacts

- Evidence: `docs/DESIGN.md:833-840` promises public inspection of the run manifest plus stage/module roots. The only public location union, `ModulePublicationAddress`, requires module identity even for its STAGE variant at `docs/DESIGN.md:1177-1200`; Stage08 mirrors it at `docs/stages/08-build-nine-section-document-and-archive.md:467-506`. Persistence separately defines a module address, a pure stage address, and a run-manifest address at `docs/DESIGN.md:1411-1457`. Yet Stage08 plan, document, trace, validation candidate, and baseline are stage-store artifacts, and the root manifest is run-store owned, with no truthful module address.
- Why P1: the query layer can locate these required public artifacts, but the promised path-free view cannot return a truthful typed location. Implementations must forge a module owner, hide artifacts, or silently weaken the view contract.
- Fix boundary: replace or extend the public location field with a sealed artifact-location union reusing typed stage-module, pure-stage, validation, resume, run-manifest, and any explicitly queryable run-root variants; update `RunInspection`, queries/views, CLI/HTTP mappings, and exact conformance fixtures.

### P1-6 — The public trace seam has no return record and incomplete adapter inputs

- Evidence: `GitHubCodeAgentQueryService.trace(...)` returns `TraceView` at `docs/DESIGN.md:821-830` and `docs/stages/08-build-nine-section-document-and-archive.md:326-335`, but `TraceView` is never defined in the required corpus. The exact `TraceQuery` includes `expectedCandidateId?` and `maxHops` at `docs/DESIGN.md:1221-1226`. The CLI/HTTP mapping at `docs/DESIGN.md:843-853` and `docs/stages/08-build-nine-section-document-and-archive.md:340-350` exposes only run plus reader key, so neither field has a specified transport/default.
- Why P1: Luna/Terra implementers must invent the returned trace fields, candidate/validation binding, hop-budget semantics, source-validation state, and adapter behavior. That prevents a conformant path-free public seam and can yield incompatible safety limits.
- Fix boundary: define an exact closed path-free `TraceView` record and ordering, including candidate/validation references, typed hops, source-validation state, and all-or-error budget behavior; map `expectedCandidateId` and `maxHops` through CLI/HTTP or specify one authoritative fixed/default budget; add exact adapter and query conformance fixtures.

## Passing areas and residual observations

- P2/non-blocking observations: none. Style preferences were intentionally omitted.
- Complete-repository intent, the 42 semantic/52 run-artifact accounting, stage counts, and M1..Mn numbering are internally consistent.
- All checked local links/anchors resolve, all JSON/JSONL examples parse, every stage declares strict/structural/narrative categories, and every checked example is classified.
- The strict renderer golden has exactly nine ordered headings, declared byte count 150, SHA-256 `67e742a00672cbd230b230b4c29ef36a0aa9aa5ea2b6c460e286160e58fca4e5`, and a final LF.
- Exact `ModuleArtifact` envelopes use the closed top-level/control keys, sorted/deduplicated upstream IDs, and typed addresses.
- Stage07's proven-anchor, ownership, and conflict rules resolve each knowledge identity to one owner or an explicit conflict/gap.
- Stage08's semantic-to-archive-to-receipt-to-root-manifest-to-M4 sequence and self-exclusion rule are coherent apart from the completion-ledger producer defect above.
- The toolchain plan is actionable for Luna/Terra: JDK 17 project toolchains, Maven `[3.9.11,4)`, full `-t` commands, default `-o`, exact selectors, and serial heavy gates are stated.
- README/scoped-AGENTS navigation and checked links are valid.
- No P0 was assigned because each finding admits a bounded contract correction without replacing the approved pipeline or persistence architecture.

## Changed files

- `progress/target-final-architecture-review.md` (this progress record only).

## Verification

| Command/check | Result | Key output |
| --- | --- | --- |
| `git status --short` before review | PASS | Pre-existing dirty design, contract, and artifact-store work recorded and preserved. |
| Complete `sed` reads of the three applicable `AGENTS.md` files and every approved input | PASS | Required corpus covered through EOF before verdict. |
| Local Markdown destination/anchor resolver over the required corpus | PASS | 41 links checked; 0 errors. |
| JSON/JSONL fenced-example parser over DESIGN and Stages 01-08 | PASS | 31 fences and 77 values parsed; 0 errors. |
| Strict-renderer golden recomputation | PASS | 150 declared/actual bytes; declared/actual SHA-256 equal; 9 headings; final LF. |
| Artifact/module accounting script | PASS | Semantic counts `3,4,7,4,5,9,5,5` = 42; run total = 52; all 42 named files occur in stage docs; M1..Mn sequences `3,4,6,3,3,6,3,4`. |
| Example-taxonomy classifier | PASS | All stage JSON blocks classified; 0 unclassified; all eight stages declare strict/structural/narrative categories. |
| Exact `ModuleArtifact` envelope checker | PASS | 11 exact envelopes; closed top-level/control keys; sorted/deduplicated upstream IDs; typed address shape; 0 errors. |
| Targeted Stage06/Stage08 record-presence and structural-input checks | PASS (finding substantiated) | 0 `TraceView` definitions, 0 `RoundSlotLedger` record definitions, 0 R0 input-type declarations, 0 ledger schema versions/output rows; structural R0 task has no facts/spans/obligations. |
| Progress citation path/range validator | PASS | 29 exact cited path/ranges checked; 0 missing files; 0 out-of-range citations; no shorthand stage filenames remain. |
| `git diff --check` | PASS | No tracked whitespace errors. |

## Decisions

- Review only the already-approved architecture and the ten fixed review dimensions; do not propose a replacement architecture.
- P0/P1 requires a concrete contradiction or implementation-forcing ambiguity with exact path/line and bounded fix; style preferences remain P2 or omitted.
- Classify all six defects P1: each blocks deterministic compatible implementation, but none requires abandoning the approved architecture.

## Blockers

- None for completing this review. The six P1 findings block acceptance of the target design as implementation-ready.

## Exact next action

- Architecture owner closes P1-1 through P1-6 in the authoritative design/stage docs and exact fixtures, then requests a bounded re-review of only those corrections plus affected links/accounting.

## Resume checks

- Re-read this file and the three governing `AGENTS.md` files.
- Run `git status --short` and confirm this remains the only review-owned file.
- Re-open the exact cited ranges and any corrected contracts before changing the verdict.
