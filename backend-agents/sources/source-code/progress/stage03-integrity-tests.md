# Progress: Stage 03 integrity hardening RED tests

- Status: COMPLETE
- Agent role: Stage03 M5–M7 TDD integrity test agent
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-30
- Last updated: 2026-08-30
- Scope: Add only public-seam Stage03 integrity tests/fixture helpers under `src/test/java/com/linguan/codemd/stage03/` and this progress file; no production, design, or prior progress changes.
- Approved inputs: scoped AGENTS instructions, `docs/stages/03-nine-section-generation.md`, `progress/stage03-code-review.md`, current public `Stage03Generator.generate(Stage03Request, StructuredModelProvider)` seam and `Stage03Result` records.
- Current branch/worktree: shared worktree; preserve unrelated parent/agent changes.

## Completed

- Read root/prototype/backend/source-scoped AGENTS instructions, `progress/TEMPLATE.md`, Stage03 design, Stage03 code-review findings, existing Stage03 tests/fixtures, and current public production seam.
- Created this progress file before modifying test sources.
- Reopened for the fixture-only registry contract migration requested by the parent implementation agent.
- Reopened for the second fixture-only contract correction: normal empty-selection R2 closure and real Capsule gap provenance.

## Current state

Existing Stage03 smoke tests are green but do not exercise the review findings. The next change adds public-seam RED regressions for plan/Markdown coupling, registry content identity, complete task payload isolation, eligibility/pattern enforcement, strict R2/question closure, reader budget, and Provider exception normalization.

The integrity RED suite is present. This cycle preserves the canonical registry freeze and corrects only fixture transport/provenance: the normal empty-selection provider emits empty R2 reviews, while its explicit mutation overload preserves the negative unexpected-review path; question proposals use actual Capsule gaps and are omitted when none are available.

The requested narrow rerun is currently blocked by the parallel production compile error at `Stage03Generator.java:703`, where `CapsuleContext.gapReason(String)` is referenced but not yet defined. This fixture-only cycle therefore has no new executable test count.

## Changed files

- `progress/stage03-integrity-tests.md` (this file)
- `src/test/java/com/linguan/codemd/stage03/Stage03IntegrityTest.java`
- `src/test/java/com/linguan/codemd/stage03/Stage03Fixtures.java` (canonical registry freeze wiring and the existing overloaded empty-selection provider helper)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=Stage03IntegrityTest test` (before correction) | **RED** | Test compile succeeded; `Tests run: 10, Failures: 6, Errors: 0, Skipped: 0` |
| `mvn -Dtest=Stage03IntegrityTest test` (after correction) | **BLOCKED in production compile** | `Stage03Generator.java:[703,34] cannot find symbol: method gapReason(String)`; tests did not execute, so no new failure count is available |

Before the correction, six failures were intentional assertion failures proving missing production behavior: empty `ReaderItem` typed ownership/render coupling; normalized Flow/Capsule facts/outcomes/gaps/spans and finite registry admission keys absent from task input; substituted R2 question gap silently closed; non-empty R2 discarded after empty R1; `maxReaderItems=1` ignored; and a provider `IllegalStateException` escapes instead of becoming `Stage03Exception`. Four integrity assertions passed because the positive and empty-term fixtures use `Stage03Registries.freeze(...)`, which supplies canonical content digests and enclosing bundle identity. After the correction, the requested rerun was blocked before test execution by the parallel production compile error above; there were no fixture errors or raw test NPEs. Existing Stage03 smoke tests were not rerun in this cycle.

## Decisions

- Use only `Stage03Generator.generate(...)` and public result/task/registry records; no reflection, private module calls, production test hooks, live model, network, or customer build.
- Mutations preserve replay identity only where the contract requires content drift to be rejected; malformed registry/content cases are constructed through public records.
- Stop after a narrow selector demonstrates the intended missing behavior; do not repair production code in this task.
- Registry semantic mutation tests retain the original registry IDs and SHA fields so the same public input identity cannot authorize changed content; the production implementation must reject the stale digest with `REGISTRY_INVALID`.
- The empty-R1 test uses the existing scripted provider's public two-round transport and asserts that R2 cannot carry unexpected reviews when there are no R1 proposals.
- Positive and empty-term registry fixtures are frozen through the public `Stage03Registries.freeze(...)` seam; the helper computes canonical child digests and bundle identity instead of carrying placeholder `digest(registryId)` values.
- Normal empty selection is now a genuinely closed two-round scripted exchange (`R1.proposals=[]`, `R2.reviews=[]`); only the explicit mutation overload emits unexpected reviews for the negative contract.
- Question fixtures never synthesize `gap:missing-row-policy`; they use a matching Capsule's actual allowed gap IDs or omit P09 when none exists.

## Blockers

- Production compile is currently blocked by parallel implementation work: `Stage03Generator.java:703` references missing `CapsuleContext.gapReason(String)`. The fixture correction itself has no test-side blocker; the rerun cannot report a post-correction failure count until that production symbol exists.

## Exact next action

Production implementation may now consume this fixture correction. Once `CapsuleContext.gapReason(String)` is supplied by the parallel production work, re-run `mvn -Dtest=Stage03IntegrityTest test` and require the six remaining assertions to turn green without weakening the public-seam contracts.

## Resume checks

- Re-read this file, run `git status --short`, and verify subsequent modifications remain under `src/test/java/com/linguan/codemd/stage03/` or this progress file.
- Final scope check: `git status --short -- src/test/java/com/linguan/codemd/stage03 progress/stage03-integrity-tests.md` reports only the shared Stage03 test directory and this progress file; no `src/main` or `docs` path was edited by this cycle.
