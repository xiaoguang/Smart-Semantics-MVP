# Progress: Model job parallel execution design

- Status: COMPLETE
- Agent role: Sole design author; documentation only
- Model: gpt-6-astra / ultra
- Started: 2026-09-13
- Last updated: 2026-09-13
- Scope: Active source-code design documents, scoped instructions and navigation
- Approved inputs: User-approved bounded job design; global and per-provider YAML concurrency; read-only current implementation and official auth/pricing/limits verification supplied by coordinator
- Current branch/worktree: Existing source-code checkout; preserve all pre-existing changes

## Completed

- Read repository, prototype, backend-agent and source-code instructions and progress template.
- Checked initial Git status; unrelated root/web-next changes and untracked project directories are pre-existing.
- Wrote the focused approved model-job contract and exact YAML/defaults/validation/Provider routing and authentication rules.
- Synchronized active architecture, steps, persistence/public/prompt contracts, engine configuration owner, scoped instructions, navigation and current CLI/run-plan guidance.
- Added the synthetic A–E / overlapping G1/G2 queue-to-summary-to-whole-report walkthrough; Task 0 aligned the single-activity B group with existing unmatched/zero-Provider behavior and corrected the normal maximum to 18 calls.
- Preserved completed historical progress and actual run results; corrected only adjacent stale current-maturity statements using coordinator-verified delivered facts.

## Current state

Task 0 documentation correction is complete. Walkthrough §12 now has only G1/G2 as process jobs, preserves B as an independent reviewed/unmatched activity with zero process calls, and counts at most 18 calls. This correction changed only the example and this owned progress file; other design contracts, code and tests were untouched. Prior broader documentation work remains listed below.

## Changed files

- progress/model-job-parallel-design.md
- docs/modules/model-job-execution.md (new authoritative module design)
- AGENTS.md; README.md; docs/DESIGN.md
- docs/analysis-steps/06-flow-interpretation.md; 07-repository-knowledge.md; 08-nine-section-document.md
- docs/references/foundation-and-publication-contracts.md; canonical-persistence-identity-contracts.md; inherited-public-and-module-contracts.md; semantic-interpretation-prompts.md
- docs/modules/java-code-engines/contracts-and-configuration.md
- docs/examples/semantic-framework-walkthrough.md
- docs/plans/coherent-code-context-implementation-plan.md; jsherp-jdt-luna-repository-run-plan.md; navigation-reuse-and-readable-report-design.md
- tools/repository-run/README.md

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | Checked | Existing unrelated changes preserved |
| Scoped `git diff --stat` / `git status --short` | Passed | Only approved Markdown surfaces changed; coordinator owns its separate progress file |
| `git diff --check` | Passed | No whitespace errors |
| Task 0 source/readback check and `git diff --check` | Passed | ProcessExplainer skips groups smaller than two; example has two effective groups and 18 calls |
| Targeted `rg` of model-job references and maturity statements | Checked | Active consumers route to the focused contract; current vs target distinguished |
| Tests/builds/scans/models | Not run | Outside this documentation-only scope |

## Decisions

- A job owns one complete DRAFT/REVIEW pair on one resolved provider/model binding.
- Exactly two concurrency controls: global and each configured provider/account service; admission/packing limits remain distinct.
- Preserve history and completed progress; synchronize affected active designs in place.
- Single v2 --config YAML owner at sourceAnalysis.modelJobs, default global/Pro 4; explicit API has explicit cap/model/effort and never receives failed-job fallback.
- Exactly defined baseConfigurationSha256 excludes only sourceAnalysis.modelJobs from the normalized v2 document, with direct local state-v2 mapping; separately hash/store nonsecret effective modelJobs, preserving technical/material reuse when concurrency changes.
- Summary remains at most one job under existing eligible/explicit-skip gates; knowledge publication waits for that outcome. Report remains one complete nine-chapter pair.
- Same-account/project aliases cannot manufacture quotas; observable duplicate credentials/context are rejected, unknown shared scopes require truthful operator declarations.
- One Pro auth context may support independent ephemeral conversations; no cap-many login homes, global login switching, credential copying or billing-guarantee flag.

## Blockers

None. Runtime implementation, tests/builds/scans, model calls, commits and publication are outside this docs-only scope.

## Exact next action

Coordinator verifies Task 0 and continues the approved implementation plan. This design author's bounded correction is complete; no commit was created.

## Resume checks

Read this file, check Git status, and verify only approved source-code Markdown files changed.
