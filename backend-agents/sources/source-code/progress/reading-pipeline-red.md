# Progress: Reading pipeline RED

- Status: IN_PROGRESS
- Agent role: Luna/xhigh RED test owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Two high-value Step07 cross-object reading-pipeline contract tests only
- Owning plan: docs/supplements/cross-object-process-reconstruction/
- Approved inputs: saved catalog 4125a702ec8489a65792e93d5d77cd1630b7933c3e34f928b93c9dba85ac3224; reviewed Activity run 6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e; verified source/M10 run 4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b
- Current branch/worktree: Formal source-code checkout

## Completed

- Read the scoped source-code instructions, approved supplement, and existing discovery/acceptance tests.
- Confirmed this RED slice must exercise the internal Step07 reading path with a deterministic scripted Provider; no live model, JDT, network, or full-suite execution.

## Current state

- Existing production discovery still performs catalog discovery and requests source after process DRAFT; the new tests intentionally specify the approved saved-catalog/global-selection/reading-check/packet seam.
- Foundation owner is defining the exact `FrozenProcessSourceCorpus` test-facing construction and reader API. Test wiring will use that API once visible in the shared checkout.

## Changed files

- This progress record.
- BusinessProcessReadingPipelineTest.java
- FrozenProcessSourceCorpusTest.java (parent-requested CRLF/no-trailing-newline/empty-whole-file fixture correction)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Only pre-existing `docs/research/` is untracked before this RED slice |

## Decisions

- Add only `BusinessProcessReadingPipelineTest.java` and, if unavoidable, a private test helper in the same new test file; do not modify production code, existing tests, schemas, or fixtures.
- First cases cover (1) saved catalog bypass + all-Activity global selection + context-versus-member disposition, and (2) exactly-one reading check with XML/Vue source present in the DRAFT packet and reused unchanged for REVIEW.

## Blockers

- Foundation corpus API is fixed; pipeline RED is ready for the coordinator after the eight-argument ProcessDiscoveryRequest seam lands.

## Decisions added in RED

- The pipeline fixture uses the agreed eight-argument internal request shape: activities, materials, profile, outputRunId, verified source reference, verified source reader, immutable saved catalog pair input, and optional focus question. The public Agent seam is unchanged.
- The provider fixture removes the legacy requestedSourceRefs response field and emits a context-only statement/source citation so the new reading allowlist, not old candidate ownership, is exercised.

## Exact next action

Add the two RED cases against the agreed internal discovery seam, then report the exact signatures to the parent and foundation owner without running Maven.

## Resume checks

- Recheck shared production/test additions before applying the test patch; preserve all other agent changes.
- If the exact API is not yet present, keep the RED contract compile-visible through the agreed type names and report the dependency rather than inventing a parallel reader.

## Plan closeout destinations

- Durable decisions: approved cross-object supplement and implementation plan
- Remaining issues: acceptance.md
- Verification and output references: parent task handoff

## Concurrent pair preservation RED

The existing pipeline test now includes a bounded two-candidate failure case. The scripted provider
is no longer method-synchronized, and the fatal candidate waits on a short `CountDownLatch` until
the other candidate's REVIEW has returned. Discovery is expected to fail with the fixture fatal
marker, while the completed candidate's `business-process/reviewed-result.json` must remain present,
contain the reading packet and source-reference mapping, and reopen through
`PrivateModelJobResultStore.readCompleted`. No sleep or additional persistence framework is used.
