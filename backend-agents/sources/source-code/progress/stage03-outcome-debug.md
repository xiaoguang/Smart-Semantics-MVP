# Progress: Stage 03 outcome reader invariant debug

- Status: COMPLETE
- Agent role: Stage03 systematic debug agent
- Model: gpt-5.6-sol / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Diagnose and repair the Stage03 production-only `READER_INVARIANT_BROKEN` regression exposed by the migrated semantic/completeness Outcome fixtures; preserve terminal/branch semantics and reader body cleanliness.
- Approved inputs: scoped AGENTS, Stage03 design, Stage03 outcome/semantic/completeness/proof-density progress, current Stage03 production and tests, frozen scripted fixtures.
- Current branch/worktree: shared worktree with extensive pre-existing uncommitted Stage01/02/03 work; preserve all unrelated changes.

## Completed

- Read repository, prototype, backend, and GitHub-code scoped guidance.
- Read the systematic-debugging and root-cause-tracing procedures.
- Read the complete Stage03 design and the Outcome, semantic, completeness, and proof-density handoffs.
- Confirmed the assigned work invokes no generative model, customer runtime, or network source.
- Re-ran the exact reported semantic/completeness selector from a fresh main/test compilation; all four tests passed.
- Traced every synthetic `OutcomePath` branch value from Stage02 and compared the internal identity-bearing representation with the reader-safe representation.
- Confirmed with the parent that there is no other pending Stage03 production version; the reported RED occurred while the shared worktree's production and fixture agents were compiling/editing concurrently.
- Ran the requested ProofDensity + Semantic + Completeness selector together from a fresh compile; all five tests passed.
- Completed the whitespace check without errors.

## Current state

- The failure is not reproducible from the settled shared worktree. Current production already contains the minimal correct boundary: `outcomeSemantics` emits only polarity plus normalized condition, while `readerValue` continues to reject any internal ID, SHA, path, or other forbidden reader content.
- No production edit is required or justified; making a no-op rewrite would risk overwriting the already-correct concurrent implementation.
- Debug assignment is complete; only this owned progress file was added.

## Changed files

- `progress/stage03-outcome-debug.md` (this file)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03SemanticTest,Stage03CompletenessTest test` | GREEN | Fresh compile; 4 tests, 0 failures, 0 errors. |
| Ephemeral `/private/tmp/Stage03OutcomeTrace.java` diagnostic over the frozen scripted fixture | ROOT CAUSE CONFIRMED | Internal branch text contains `guard=node:<64hex>` and `condition=atom:<64hex>`; reader-safe text contains only `TRUE/FALSE expression=<normalized condition>`. |
| `mvn -Dtest=Stage03ProofDensityTest,Stage03SemanticTest,Stage03CompletenessTest test` | GREEN | Fresh compile; 5 tests, 0 failures, 0 errors, 0 skipped. |
| `git diff --check` | GREEN | Exit 0; no whitespace errors in tracked changes. |

## Decisions

- Do not weaken body-cleanliness validation or edit tests/design.
- Do not modify production until the exact rejected value, rejection rule, source data flow, and working built-in comparison are recorded below.
- Preserve the settled production serializer and do not manufacture a redundant edit after the exact selector is green.

## Root-cause evidence

- Source data: each `BranchDecision` carries reader semantics (`polarity`, `normalizedCondition`) and internal lineage (`guardNodeId`, `conditionAtomId`). The frozen fixture includes exact internal values such as `node:f742ea208d9ad3bd9e39c557538ea6417dbad03c10e6d088f9b33e2b8db7883e` and `atom:54b84bc23fc0b9fe16d011a23f05b795a5b36a19256e0180d7968b5e37e07993`.
- Rejection path: `readerValue` calls `safeReaderText`; `safeReaderText` rejects any value for which the `[0-9a-f]{64}` `SHA256` matcher finds a digest. Therefore serializing the internal guard/condition IDs into `outcome-semantics` deterministically throws `READER_INVARIANT_BROKEN` at the observed call site. The gate is behaving correctly and must not be weakened.
- Working representation: the settled `outcomeSemantics` serializes `TRUE/FALSE expression=<normalizedCondition>` only (for example, `TRUE expression=quantity <= 0`). `path.terminalKind()` remains the separate `outcome-terminal` slot. This retains terminal, polarity, and guard expression semantics without exposing node IDs, atom IDs, SHA material, or source paths.
- Built-in/custom comparison: the built-in fixture selects `ReaderContracts.builtIns()` through empty template/ownership registries; the stricter fixtures supply the same exact `READER_OUTCOME_V1` slot signature and `OUTCOME -> section-4` ownership with different literal text. Both paths call the same Outcome slot builder. Once the identity-bearing values are excluded at their source, both contracts pass without changing cleanliness.
- Timing evidence: `Stage03Generator.java` reached its settled reader-safe form at 06:48:29; the semantic/completeness migration files and their handoff were written at 06:49:34. A fresh compilation at 06:52 passed 4/4. The earlier three errors are therefore attributable to the shared-worktree compile/edit race rather than the settled source state.

## Blockers

- None.

## Exact next action

- None; return the root-cause evidence and fresh verification result to the parent.

## Resume checks

- Re-read this file and `git status --short`; keep production writes inside `src/main/java/com/linguan/codemd/stage03/` and progress writes inside this file.
