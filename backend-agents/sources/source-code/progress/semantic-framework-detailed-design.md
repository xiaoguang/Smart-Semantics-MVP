# Progress: semantic framework detailed design

- Status: BLOCKED
- Agent role: Sole Design Authority and documentation author
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-10
- Last updated: 2026-09-10
- Scope: Documentation-only redesign of the reusable source-code semantic-analysis framework across the overall design, all eight analysis-step designs, scoped instructions, README, two existing implementation plans, and the program-graphs responsibility backlog when needed.
- Approved inputs: Current source-code Agent implementation and designs; the user-approved enterprise-ontology-inspired semantic direction; the frozen-source/evidence contracts already in this worktree. No live source, customer scan, runtime data, or product-model call is approved.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Confirmed the worktree has extensive pre-existing code, test, design, and progress changes; they are outside this task unless explicitly listed in scope.
- Confirmed this task is documentation-only and must preserve all source, test, schema, Maven, runtime, historical progress, and recovery files.
- Read the repository, backend-Agent, and source-code-Agent instructions; both scoped implementation plans; the enterprise-ontology-builder skill and its four references; backend shared navigation/context; and the ProgramGraphs implementation backlog.
- Preserved the exact before-state of all 16 active documents in `/private/tmp/linguan-semantic-design-before-20260910.tar` (`sha256:57f712cde553f24253e5cb539d371b53c27255f078d692338a711490be766658`).

## Current state

- The substantive design pass and bounded consistency pass for ordinary documentation are complete. The overall design and Steps 01–08 now agree on the six-module Step 06 framework, whole-record DRAFT/REVIEW, exact 52-output target, noFlow material/Trace path, qualitative uncertainty, deterministic knowledge admission, and nine-section projection.
- Step 05 retains its six-file strict Flow/Capsule exit but no longer acts as a domain-classification gate. Steps 01–04 retain their stable source, entry, five-graph, Fact/Proof algorithms and state how they feed semantic material without becoming business parsers.
- Four ACTIVE references now separate publication/trust rules, inherited canonical identity/store mechanics, public/request/source/envelope contracts, and Chinese semantic prompts. A synthetic three-entry worked example follows identical IDs from frozen source through every Step 06–08 module into all nine chapters.
- Source README, backend navigation/context, and both implementation plans are synchronized. The protected scoped `AGENTS.md` is the sole incomplete target; two edit attempts were rejected by the environment safety reviewer and no workaround was attempted.

## Changed files

- `progress/semantic-framework-detailed-design.md`
- `docs/DESIGN.md`
- `README.md`
- `docs/references/foundation-and-publication-contracts.md`
- `docs/references/canonical-persistence-identity-contracts.md`
- `docs/references/inherited-public-and-module-contracts.md`
- `docs/references/semantic-interpretation-prompts.md`
- `docs/examples/semantic-framework-walkthrough.md`
- `docs/analysis-steps/01-verified-source-inventory.md`
- `docs/analysis-steps/02-application-discovery.md`
- `docs/analysis-steps/03-program-graphs.md`
- `docs/analysis-steps/04-proven-code-facts.md`
- `docs/analysis-steps/05-business-flows.md`
- `docs/analysis-steps/06-flow-interpretation.md`
- `docs/analysis-steps/07-repository-knowledge.md`
- `docs/analysis-steps/08-nine-section-document.md`
- `docs/plans/source-analysis-naming-and-delivery-plan.md`
- `docs/plans/target-standards-and-toolchain-plan.md`
- `docs/supplements/program-graphs-implementation-backlog.md`
- `../../README.md`
- `../../CONTEXT.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Captured extensive pre-existing dirty state before this task; no Git mutation performed. |
| `wc -l` over scoped designs/plans | PASS | Active documentation surface is approximately 11,000 lines and requires bounded consolidation. |
| `tar -tf` and `shasum -a 256 /private/tmp/linguan-semantic-design-before-20260910.tar` | PASS | All 16 scoped before-state documents are present; archive SHA-256 is recorded above. |
| Parent mechanical parse/link/fence audit during writing | PASS | 44 JSON/JSONL examples parse; no unclosed fences; mainline/reference/example Markdown links resolved at the last reported checkpoint. |
| Production/source/test/schema/POM/recovery baseline comparison | PASS (parent-reported checkpoint) | No task-owned changes to Java, tests, `.mvn`, POM, JSON Schema, runtime artifacts, or `runtime-recovery-todo.md`. |

## Decisions

- Preserve before-state copies of every substantially replaced active document outside the active documentation tree before the writing pass.
- Keep existing graph, source-evidence, persistence, and recovery design; redesign the semantic interpretation seam rather than adding domain-specific Java inference.
- Any new artifact count or call topology is target design only and must be marked `NOT IMPLEMENTED` until production and schema work is separately approved.
- Keep eight public analysis steps. Replace Step 06 with six modules: semantic material compilation, DRY packet compilation, local DRAFT/REVIEW, high-recall process retrieval, process/reconciliation DRAFT/REVIEW, and publication.
- Target exactly 42 semantic payloads (3+4+7+4+5+9+5+5), eight semantic receipts, one archive manifest, and one root manifest: 52 reader-visible run outputs.
- Replace mandatory R0/finite-key selection with model-authored, evidence-linked semantic objects; terminology is mergeable data rather than a gate.

## Blockers

- Scoped `AGENTS.md` still states the old R0/P1/P2 and 57-output semantic target. The environment safety reviewer rejected both a broad replacement and a narrower semantic-only patch because `AGENTS.md` is a trusted instruction file. The parent must report this and obtain explicit permission before synchronizing it. Existing safety, authorization, publication, and approval rules were not changed.

## Incidents

- Replacing `docs/DESIGN.md` briefly left the path absent after the first add patch failed on a JavaScript string escape. The preserved tar remained intact; the add was immediately retried with a raw `apply_patch` payload and the complete active document is present again. Future large replacements use one direct update/add patch without a long delete interval.
- Two attempted `AGENTS.md` updates were rejected by environment policy. Work stopped on that file after the second rejection; no alternate editor, indirect write, or overriding instruction was used.

## Exact next action

- Parent performs the already-planned final mechanical diff/link/fingerprint check and reports the protected `AGENTS.md` blocker to the user. If explicit permission is later granted, make only the narrow R0→DRAFT/REVIEW, 57→52, DRY/noFlow and model-role synchronization while preserving every existing safety/authorization/publication rule.

## Resume checks

- Re-run `git status --short` and confirm no source, test, schema, Maven, runtime, old progress, or recovery file was changed by this task.
- Read this progress file and the recorded before-state manifest before continuing.
- Do not run Maven, customer source analysis, product-model calls, Git index operations, commits, or pushes.
