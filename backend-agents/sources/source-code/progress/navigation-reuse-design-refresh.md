# Progress: navigation reuse and readable report design

- Status: COMPLETE
- Agent role: main designer and integration auditor
- Model: gpt-6-astra / ultra
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: design/documentation only; current-code inspection is read-only
- Approved inputs: user-approved repository JDT reuse, source deduplication, one-pass local CI, separate report source references
- Current branch/worktree: formal linguan-prototype-v2; appmod/java-upgrade-20260912113141 at db28f8d

## Completed

- Confirmed current delivered code and design baseline is 080a86db04c4917b27c5a48c88ca136d11bd0f2b, not the older formal checkout.
- Isolated two pre-existing uncommitted Java 25 build changes; neither is in this documentation work unit.

## Current state

Approved optimization design is complete in the formal directory. Only delivered source-code design/navigation documents were synchronized from 080a86d, then the overall, JDT submodules, context/capsule contract, Builder, report, local CI and scoped guidance were updated. Full design/code alignment audit is DEFERRED per the user's latest scope reduction. Limited relevant read-only checks informed the design; no production implementation was performed.

## Changed files

- docs/plans/navigation-reuse-and-readable-report-design.md (single optimization decision document).
- docs/DESIGN.md; docs/modules/java-code-engines/{README,contracts-and-configuration,jdt-engine,integration-and-javaparser}.md.
- docs/analysis-steps/{03-program-graphs,05-business-flows,06-flow-interpretation,08-nine-section-document}.md.
- README.md, scoped AGENTS.md, toolchain plan, inherited interface reference and walkthrough source-display guidance.
- Other source-code design files synchronized unchanged from the delivered baseline so the formal design does not predate JDT; historical links retain exact source locations/commit.
- This progress only; the two bounded reviewers own their separate progress files.

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| git status --short | inspected before editing | only pre-existing pom.xml and .mvn/toolchains.xml modifications |
| git rev-parse / merge-base / diff | inspected | delivered baseline 080a86d; formal HEAD is its ancestor |
| Read-only Markdown path and whitespace check | PASS | 29 changed/new documentation files; 162 local links; zero missing links or trailing whitespace |
| git diff --check | PASS | no whitespace errors |
| git diff --name-only -- src/tools/config/.github | PASS | no production, test, tool, configuration or workflow edits; two initial build-file changes preserved |

## Decisions

- Edit only the formal directory. Read current implementation through git show of the pinned delivered commit.
- Preserve app Java 17 / independent JDT tool JVM; do not incorporate blanket Java 25 changes.
- Cache tool facts, not entry-dependent expansion decisions. No new parser, cache service or recovery subsystem.
- Index v2 remains; Step05 compilation/slices target v6, projection v11, capsule v9 only for reference storage. Full model request content remains self-contained.
- Report source references remain in existing source-refs.jsonl; remove source blocks and dead anchor links from Markdown only, without new model calls.
- Keep unit skipUTs and IT skipITs independent; target one verify lifecycle. Current workflow skipTests does not match POM skipUTs.
- Existing session/collector reuse is already implemented; the missing reuse is raw navigation queries, not repeated collector construction.
- No Java/test/POM/toolchains changes, no build/model/JDT/customer scan, no Git commit/push/checkout.

## Blockers

None for design work. Formal source synchronization is a separate follow-up, not silently performed here.

## Exact next action

Return the design links. Implementation and the deferred full design/code audit are separate follow-ups; do not start either automatically.

## Resume checks

Recheck Git status and the pinned baseline; preserve unrelated build changes and previous run artifacts; do not rerun Maven, JDT or product models for this documentation-only task.
