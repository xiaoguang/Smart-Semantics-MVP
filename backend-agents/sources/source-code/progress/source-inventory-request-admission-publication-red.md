# Progress: source-inventory-request-admission-publication-red

- Status: COMPLETE
- Agent role: source-inventory M1 publication test author
- Model: gpt-5.6-luna / xhigh test design under the published Sol/ultra design
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Add the first public-seam RED for M1: persist an already-admitted `AdmittedSourceRequest` as the exact receipt-last `admitted-source-request.json` module artifact. This excludes M2, M3, capture, AST, model calls, and the top-level executor.
- Approved inputs: `docs/analysis-steps/01-verified-source-inventory.md` §8.1 M1 and the existing `FrozenRequestAdmission` pure seam.
- Current branch/worktree: `codex/source-analysis-verified-inventory` at `/private/tmp/linguan-source-analysis-verified-inventory`

## Completed

- Confirmed M1 must publish before M2 is allowed to consume the admitted request; in-memory `AdmittedSourceRequest` alone is not a valid cross-module input.
- Observed the initial public seam RED for the absent M1 publisher.
- Added the path-free `AdmittedSourceRequestModulePublisher` and its typed input. The publisher validates the exact M1 upstream closure, preserves the admitted request's source facts, writes the canonical `admitted-source-request.json` envelope, and delegates receipt-last installation and fresh reopen to `CanonicalModuleArtifactStore`.
- Added a complete-capture text/media fixture that verifies the persisted payload and upstream references. Its M1 input explicitly carries the verification-policy and capability-profile references required by the published M1 contract.

## Current state

- M1 now has a receipt-last module-publication writer. M2 still does not fresh-reopen this publication or install its own verified-source-index result, so the overall source-inventory step remains incomplete.

## Changed files

- `progress/source-inventory-request-admission-publication-red.md`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/AdmittedSourceRequestModulePublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/inventory/AdmittedSourceRequestPublicationInput.java`
- `src/test/java/org/sourceanalysis/app/analysis/inventory/AdmittedSourceRequestModulePublisherTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| preflight | PASS | M1 is a separate receipt-last module publication and is the next dependency for a real M1 → M2 → M3 execution. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=AdmittedSourceRequestModulePublisherTest test` | PASS | 2 tests, 0 failures/errors/skips; persisted canonical M1 request fresh-reopens through the real module store. |
| `mvn -t .mvn/toolchains.xml -o spotless:check` | PASS | Source and test conform to project formatting. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- M1's module upstream closure is exactly the eight references already fixed by the stage design: run request, frozen request, capture receipt, snapshot manifest, source registration, verification policy, capability profile, and resource budget. The profile/toolchain/schema/prompt/policy controls remain bound through the admitted request and envelope controls, rather than being silently added as extra upstream artifacts.

## Blockers

- None.

## Exact next action

- Build M2's public writer/reader seam: it must fresh-reopen M1 rather than receive an `AdmittedSourceRequest` Java object.

## Resume checks

- Read this file, verify the M1 selector, then begin a separate M2 progress file and public RED.
