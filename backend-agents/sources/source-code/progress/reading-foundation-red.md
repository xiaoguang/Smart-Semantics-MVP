# Progress: Reading foundations RED

- Status: IN_PROGRESS
- Agent role: TDD RED owner for Step07 reading foundations
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Focused tests only for frozen source text access, single-decision result persistence, and complete catalog-pair input reuse
- Owning plan: docs/supplements/cross-object-process-reconstruction/README.md and approved Step07 implementation plan
- Approved inputs: `docs/supplements/cross-object-process-reconstruction/module-design.md`, `prompts.zh-CN.md`, and `acceptance.md`; existing `VerifiedSourceTextSet` and `PrivateModelJobResultStore` contracts
- Current branch/worktree: `codex/cross-object-process-reading`; formal source-code checkout

## Completed

- Read repository, backend, and source-code scoped instructions.
- Read the approved cross-object reading design, prompt contract, and acceptance criteria.
- Confirmed the baseline commit is `414a54b`; no production changes are in scope.
- Defined intended test seams: package-private `FrozenProcessSourceCorpus` constructed from `VerifiedSourceTextSet`, minimal `files/read/search` operations, separate decision write/read, and catalog pair input reuse independent of a new prompt fingerprint.

## Current state

- Progress handoff exists before test edits.
- `FrozenProcessSourceCorpusTest` now defines the first direct RED contract; existing production has `VerifiedSourceTextSet`, `VerifiedSourceTextDocument`, `VerifiedSourceTextReader`, and pair-only `PrivateModelJobResultStore`; the new reading seam is not implemented.
- Root confirmed the named Maven command reached test compilation and failed only because the corpus class/API are absent. Result-store/catalog-pair tests are intentionally left to the later RED owner.
- Existing model-job reuse tests cover reviewed pair behavior and must remain green.

## Changed files

- `progress/reading-foundation-red.md`
- `src/test/java/org/sourceanalysis/app/analysis/knowledge/FrozenProcessSourceCorpusTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Formal checkout on `codex/cross-object-process-reading`; only pre-existing `docs/research/` is untracked |
| `JAVA_HOME=/usr/local/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home mvn -q -t .mvn/toolchains.local.xml -Dtest=FrozenProcessSourceCorpusTest test` | EXPECTED RED | Test compilation fails because `FrozenProcessSourceCorpus` and `FrozenProcessSourceCorpus.ReadRange` are not implemented; no unrelated failure observed |
| `git diff --check -- progress/reading-foundation-red.md src/test/java/org/sourceanalysis/app/analysis/knowledge/FrozenProcessSourceCorpusTest.java` | PASS | No whitespace errors |

## Decisions

- Keep `FrozenProcessSourceCorpus` package-private in `analysis.knowledge`; construct it only from a verified, immutable `VerifiedSourceTextSet`.
- Expose only source file metadata and exact operations required by the approved reading contract; avoid a general scanner or live path API.
- Treat `read` ranges as inclusive one-based ranges and support an explicit whole-file operation; literal search reports exact matches and bounded context while preserving raw text.
- Decision records are single immutable records with their own complete-success validation and fingerprint matching; they must not masquerade as DRAFT/REVIEW pairs.
- Catalog input reuse validates a complete saved pair and its source/basis identity but does not compare a newly authored process-reading prompt fingerprint; new process work still uses the pair as input, not as a completed new result.
- Test fixtures carry exact path, mode, media type, byte length, SHA-256, and raw UTF-8 metadata so tests cannot bypass source identity checks.

## Blockers

- The first targeted RED is established and handed to Terra. Further Maven commands are paused for this subtask; result-store decision and catalog-input seams remain a later RED task.

## Exact next action

Return the corpus RED signature and evidence to the coordinator, then release this execution slot. Do not touch production code or run live models, JDT, network access, or the full suite.

## Resume checks

- Re-read this file and run `git status --short` before test edits.
- Preserve unrelated worktree changes.
- If RED cannot be established due to a contract mismatch, stop and report the exact missing upstream data or signature instead of adding production code.

## Plan closeout destinations

- Durable decisions: `docs/supplements/cross-object-process-reconstruction/module-design.md`
- Remaining issues: `docs/supplements/cross-object-process-reconstruction/acceptance.md`
- Verification and output references: `docs/supplements/cross-object-process-reconstruction/delivery.md`
