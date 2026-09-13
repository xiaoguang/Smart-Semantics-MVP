# Progress: JDT index collision diagnosis

- Status: COMPLETE
- Agent role: Sol/xhigh read-only root-cause diagnosis
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-13 07:30:38 -0230
- Last updated: 2026-09-13 07:35:36 -0230
- Scope: Diagnose the `JAVA_CODE_INDEX_INVALID` collision in the completed real-repository JDT run; inspect publication deduplication keys and per-entry `EntryCodeCollector` projections; separate code proof from unknown runtime values; recommend one minimal targeted RED and the narrow fix boundary.
- Approved inputs: Current source and tests; `.workspace/jsherp-jdt-luna-run.5Oqj9Y/materials-with-classpath-run.log`; persisted run `analysis-run:75a8beaf1baefa893cbf6f7df66ae67f4a4516e2b2c83048850bdf1b49b77984` if present locally.
- Current branch/worktree: `codex/jsherp-jdt-luna-repository-run` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read repository `AGENTS.md` and the systematic debugging/root-cause tracing instructions.
- Captured the pre-existing dirty worktree and identified the publisher and collector source/test locations.
- Traced the failing runtime frame through `JavaCodeIndexPublicationSpecifier.records`/`putSame`, the `java-code-index-v1` writer/reader contract, `ProgramGraphsExecution`, `JdtProjectSession`, and `EntryCodeCollector` call construction.
- Verified the failed run persisted only Step 01 and Step 02, with 326 discovered entries and no Step 03 `java-code-index.jsonl`; no customer-content model journal files were present.
- Distinguished the fields proven context-dependent by code from values not captured by the runtime log, and sent the diagnosis plus minimal RED/fix boundary to the root agent.

## Current state

- Runtime proof: the stack at `records:272 -> putSame:437` means at least two collected entry contexts contained the same `callKey` with unequal full `CallSite` records. The log does not contain the physical call ID or a field diff, so both remain unknown.
- `callKey` is a physical-site identity over source path plus source-range start/length. Each `collect(entry)` nevertheless creates a fresh entry-local method closure and then projects targets against that entry's `includedKeys`.
- Code-proven context-dependent target fields are `methodKey`, `expansion`, `reason`, and `argumentAssociations`; the resulting `targets` list and, when target collapsing changes its size, `resolution`/`resolutionDetail` can also differ. Source-syntax fields are derived from the cached project syntax and should remain invariant for one physical site. Navigation candidates/roles/kinds are queried again per entry and could also vary, but the supplied run does not prove that occurred.
- The publisher globally deduplicates the entire `CallSite` by physical `callKey`; the reader stores exactly one global call per `callKey` and reuses it for all entry memberships. This is the contract defect: an entry-context projection is incorrectly required to be globally identical.
- `MethodCode` does not have the same defect: its fields are projected solely from the cached syntax declaration, not from the entry-local closure, so global strict equality by `methodKey` is consistent with current construction.

## Changed files

- `progress/jdt-index-collision-diagnosis.md` only.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Pre-existing edits/untracked files recorded; no task source/test/design changes made. |
| `rg -n ...` targeted symbol search | PASS | Located `records` at lines 247+, call insertion at line 272, `putSame` at line 434, and `EntryCodeCollector`. |
| `nl -ba ...JavaCodeIndexPublicationSpecifier.java` and `...JavaCodeIndexReader.java` | PASS | Writer requires full-call equality under global physical key; reader reconstructs every entry from the same global call map. |
| `nl -ba ...EntryCodeCollector.java` and `...JdtProjectSession.java` | PASS | One cached syntax index is reused, but each entry gets fresh closure/navigation and target expansion is calculated from entry-local `includedKeys`. |
| `wc -l .../entry-points.jsonl` | PASS | 326 discovered entries. |
| Targeted artifact listing | PASS | Failed run contains only Step 01/02; no Step 03 index and no model journal file. |
| Timestamp inspection | PASS | Step 02 receipt at 05:40:03 and terminal failure at 07:28:30, about 108 minutes later. |

## Decisions

- Perform diagnosis only: no production, test, design, scan, Maven, or model actions.
- Treat source semantics and persisted/runtime evidence separately; do not infer the concrete collision identity from the exception alone.
- Recommend a versioned wire correction at the publisher/reader seam rather than changing parser/navigation behavior: METHOD remains global; CALL records are owned by entry and deterministically addressed by framed `(entryId, physicalCallKey)`, while the payload carries both owner and exact `CallSite`. Membership retains ordered physical call keys and the reader resolves them within its current owner.
- Across entry-owned variants of one physical call, validate source-intrinsic fields for equality. Preserve entry-local target/navigation projections exactly; do not use first/last win, union, or most/least-expanded selection.
- Minimal targeted RED belongs in `JavaCodeIndexPublicationSpecifierTest`: two valid contexts share one physical call and caller; one contains a `BODY_INCLUDED` target with method key/associations, the other contains the same target as `NOT_EXPANDED` with null method key, `COLLECTION_LIMIT`, and no associations. Publishing and reopening must preserve each exact context. Current code fails at `putSame` before installation.

## Blockers

- Exact conflicting `callKey` and exact unequal fields cannot be recovered from the supplied artifacts because the exception logged neither operand and failure occurred before Step 03 bytes were installed. This does not block the contract-level root-cause diagnosis.

## Exact next action

- Root agent may add the single publisher/readback RED, update the owner/reader/policy/design to a new schema version, implement the entry-owned CALL wire, and run only the directly covering test.

## Resume checks

- If implementation resumes, re-run `git status --short`, confirm overlapping root-agent edits, and do not touch the collector/parser unless a separate RED disproves the publisher/reader-only boundary.
