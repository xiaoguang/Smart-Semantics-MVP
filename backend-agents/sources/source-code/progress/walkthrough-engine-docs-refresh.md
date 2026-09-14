# Progress: walkthrough and Java engine documentation refresh

- Status: COMPLETE
- Agent role: design documentation editor
- Model: inherited
- Started: 2026-09-14
- Last updated: 2026-09-14
- Scope: Refresh the two walkthroughs and Java code engine module documents for the approved business-process discovery design.
- Approved inputs: Current implementation, saved 326 reviewed Activities/JDT corpus, approved process-discovery change design.
- Current branch/worktree: `main` in the formal `source-code` checkout.

## Completed

- Read scoped repository instructions and the approved change design.
- Replaced the old finance-centered semantic walkthrough with an end-to-end sales-process walkthrough that separates measured current state from the approved target projection.
- Updated the Java-engine walkthrough to end in catalog discovery, detailed process reconstruction, `business-processes.md`, and a downstream nine-section overview.
- Updated all assigned Java-engine design pages to state that JDT and JavaParser adapters, query reuse, referenced persistence, and 326 reviewed Activities already exist.
- Recorded that the current 340 singleton processes and one silently omitted Activity are a Step07 semantic/coverage defect, not an engine defect.

## Current state

- The assigned documents now consistently preserve the implemented engine contracts while directing future semantic work to the approved business-process discovery Modules.

## Changed files

- `progress/walkthrough-engine-docs-refresh.md`
- `docs/examples/semantic-framework-walkthrough.md`
- `docs/examples/java-code-engine-walkthrough.md`
- `docs/modules/java-code-engines/README.md`
- `docs/modules/java-code-engines/integration-and-javaparser.md`
- `docs/modules/java-code-engines/jdt-engine.md`
- `docs/modules/java-code-engines/contracts-and-configuration.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Preserved pre-existing documentation changes and untracked research content. |
| `git diff --check` | PASS | No whitespace errors. |
| targeted stale-term `rg` scan | PASS | No remaining claims that JDT-only or JavaParser adaptation is pending in assigned files. |
| assigned-file existence check | PASS | All six assigned documentation files exist and are non-empty. |

## Decisions

- Describe current observed outputs separately from the approved target projection.
- Do not change engine contracts or claim the new process-discovery implementation exists.
- Keep JDT/JavaParser documents focused on source acquisition; semantic discovery starts from saved ReviewedActivity and SourceRef data without reopening an engine session.
- Make `business-processes.md` the primary process-quality readout and keep the nine-section document as a downstream overview.

## Blockers

- None.

## Exact next action

- Parent agent can integrate these assigned documentation changes with the broader design refresh.

## Resume checks

- None; task complete.
