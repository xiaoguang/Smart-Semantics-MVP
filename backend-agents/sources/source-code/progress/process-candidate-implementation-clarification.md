# Progress: Process-candidate implementation clarification

- Status: COMPLETE
- Agent role: Sol/ultra design authority for the bounded executable-contract clarification
- Model: gpt-5.6-sol, ultra reasoning
- Started: 2026-09-08 19:41 NDT
- Last updated: 2026-09-08 20:05 NDT
- Scope: Clarify only the existing Step 04/05/06 contracts for repository-exact Java calls, R0-backed process cues, and Flow-scoped shared evidence; no implementation, tests, schema JSON, POM, Git, provider, customer, or recovery work.
- Approved inputs: docs/DESIGN.md; Steps 04/05/06; .superpowers/flow-signals-implementation-handoff.md; the completed process-signal capability audit; narrowly relevant persisted ProgramGraphs, Fact/Proof, Registry, Flow, and Capsule structures.
- Current branch/worktree: codex/source-analysis-flow-signals-implementation at /private/tmp/linguan-source-analysis-process-design (baseline 21bba98638dd37386829f14bd1c8cb8176908ff1)

## Completed

- Reused the completed capability audit and its exact class/contract evidence; no broad reread or new architecture review.
- Frozen scope to three seams: exact repository-Java call admission and entry matching, deterministic R0 cue normalization/profile control, and per-Flow span identity for shared source evidence.
- Added Step 04 §8.0.2: exact `JAVA_EXACT_CALL` candidate/atom/Proof fields and joins, interface/unresolved behavior, accounting, exact v3 cascade, and Luna/Terra public seams.
- Added Step 05 §§8.1.2/8.4/8.6: deterministic exact-call-over-boundary precedence with boundary fallback, precise v3/v6/public v3/v4 cascade, Flow-scoped span identity without a new field, and positive/negative migration fixtures.
- Added Step 06 §§5.1/5.2: exact `ProcessCueProfileV1`, NFC-exact R0 cue enumeration, entry-target extraction from the root `NEXT` edge, direct-call candidate formation, domain-scope boundary, stable failure codes, and incremental owner/hour handoff.
- Final wording freeze makes v3 a coordinated replacement rather than a compatibility path, and assigns raw-label NFC normalization/identity to R0 M2 `RegistryProposalRunner` with Luna test/Terra production ownership; M6 only verifies the frozen NFC result.

## Current state

- Before the coordinated cutover, current v2 Step 04 and v2/v5 Step 05 work proceeds unchanged; at cutover v3/v6 registrations/readers and all dependent fixtures replace the old path together, with no dual reader or executable compatibility suite.
- The next coordinated cutover is now exact: Step 04 v3; Step 05 M1 v3/M2 v6/public flow v3/capsule v4; Step 06 keeps its already-designed first wire versions and binds rule/profile controls through the existing profile bundle reference.
- Domain classification remains genuinely absent. It gates only `SHARED_ANCHOR` and the named full Step 05 §8.6 domain acceptance, not direct exact-call `PROVEN_HANDOFF` or R0 `SEMANTIC_CUE/PENDING_ONLY`.
- M2 publisher code is GREEN and active zero-Flow work remains owned elsewhere; this clarification will not edit or retest it.

## Changed files

- progress/process-candidate-implementation-clarification.md (this owned progress record)
- docs/analysis-steps/04-proven-code-facts.md (new bounded v3 Fact/Proof contract only)
- docs/analysis-steps/05-business-flows.md (new bounded v3/v6 projection, compatibility, span identity, and tests only)
- docs/analysis-steps/06-flow-interpretation.md (new bounded M6 entry/cue algorithms and handoff only)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git diff --check -- <owned Step04/05/06 docs>` | PASS | No whitespace errors. |
| `rg` exact-version/control/failure tokens across owned docs | PASS | No `vNext`; all Step 04/05 versions, span domain, cue profile, and failure codes present. |
| Maven/JVM/tests | NOT RUN | Explicitly out of scope for this contract-only task. |

## Decisions

- The user-approved bounded seams are the design approval gate; no broader brainstorming or architecture choice is being reopened.
- The minimum nontrivial candidate route remains direct exact call; domain classification is not a blanket v0 prerequisite.
- `JAVA_EXACT_CALL` proves an EXACT edge to a persisted repository `METHOD`, including interface/abstract declarations; it deliberately does not claim a concrete body or effect.
- Step 05 prefers one admitted exact-call Fact but retains the current boundary Fact as a unique fallback, so next-contract migration changes basis/identity without silently dropping existing boundary-only calls.
- That boundary rule is evidence-basis fallback inside v3, not wire compatibility with v2.
- `normalizedCueKey` is the frozen R0 `normalizedLabel` under `R0_NFC_EXACT_V1`; there is no trim/case-fold/token/name inference. v0 needs only `REGISTRY_BUSINESS_TERM`; specialized entry/state cues remain gated by their already-designed upstream bases.
- R0 M2 normalizes accepted raw `label/purpose` to NFC before constructing `BusinessRegistryProposal` and computing proposal identity; M3 revalidates, while M6 treats non-NFC frozen registry data as fatal.
- Span identity is the hash of `(flowSliceId,evidenceNodeId)` under `business-flows-model-evidence-span-id-v2`; the existing `ModelEvidenceSpanV4` fields are sufficient when every span ID has exactly one Capsule owner.
- Approximate incremental effort is 18–26 continuous hours beyond already-planned counter/M2 publisher/zero-Flow work; it excludes the full M6–M9 implementation.

## Blockers

- No blocker for the minimum direct-call/cue slice.
- A Proof-backed domain classifier is genuinely absent and remains separate scope for full Step 05 §8.6/`SHARED_ANCHOR` acceptance; no current rule was repurposed.

## Exact next action

- Luna/xhigh may write only the next-contract public REDs named in the three sections; Terra/xhigh then implements the Step 04 v3 slice, Step 05 v3/v6 migration, Flow-scoped span identity, and M6 exact-call/R0-cue rules in that order. Current M2 publisher/zero-Flow work need not wait.

## Resume checks

- Recheck `git status --short`; shared code/test/progress changes remain owned by other roles.
- Do not implement v3 with dual registrations/readers, mixed Step 04/05 schema sets, or retained executable v2 schema fixtures; preserve their behavior assertions in migrated v3 fixtures. Do not modify Step 03, Provider responsibilities, public artifact counts, eight-step order, or nine chapters.
