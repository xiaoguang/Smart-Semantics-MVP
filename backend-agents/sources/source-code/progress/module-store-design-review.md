# Progress: module-store-design-review

- Status: COMPLETE
- Agent role: Sol/ultra design and specification reviewer
- Model: gpt-5.6-sol / ultra
- Started: 2026-09-02T01:49:34Z
- Last updated: 2026-09-02T02:08:00Z
- Scope: Read-only review of the first `MODULE_ARTIFACT_JSON` filesystem module-store vertical slice.
- Approved inputs: Scoped `AGENTS.md`; `docs/DESIGN.md` module-store, publication, identity, and failure contracts; Stage 01 M1 artifact contract; approved implementation plans; changed artifact production and test files.
- Current branch/worktree: `codex/source-analysis-module-store` in `/private/tmp/linguan-source-analysis-module-store`

## Completed

- Confirmed the worktree and scoped rules before review.
- Read the module envelope, store seam, path, identity framing, failure-code, and Stage 01 M1 contracts.
- Inspected the changed public records, filesystem handle/store, private publication engine, and direct tests.
- Re-ran the direct module-store selector; all four current tests pass.

## Current state

- Review activity is complete. There are no P0 findings.
- The basic install, fresh reopen, same-request idempotence, and missing-receipt behavior are present, but the slice is not acceptable until the current-slice P1 findings below are closed and re-reviewed.

## Findings

### P0

- None.

### P1 — must close in this request-admission JSON slice

1. **The configured policy registry is not bound to the request/receipt controls.**
   `src/main/java/org/sourceanalysis/app/artifact/AtomicCanonicalPublicationEngine.java:120-222`
   resolves an exact policy but never compares `artifactPolicies.reference()` with
   `request.controls().artifactPolicyRegistryRef()`; reopen reconstructs the same unchecked request at
   `:385-407`. This violates `docs/DESIGN.md:1782` and allows bytes to claim a registry other than the one
   that actually admitted them. The stable failure must be `ARTIFACT_POLICY_MISMATCH`, not the current test
   expectation `MODULE_INSTALL_REQUEST_INVALID` at
   `src/test/java/org/sourceanalysis/app/artifact/CanonicalModuleArtifactStoreTest.java:127-146`.

2. **The opaque root does not provide the required NOFOLLOW directory-chain resolution.**
   `AtomicCanonicalPublicationEngine.java:56-75` uses `Files.createDirectories` and an atomic move after only
   checking the final destination; `:667-705` builds and lists the path without rejecting symlinked `runs`,
   run, step, `modules`, or module ancestors. Reopen at `:315-378` can likewise traverse such an ancestor.
   This violates `docs/DESIGN.md:1876-1887` and can redirect reads or writes outside the configured store.

3. **Reopen is not resource bounded and `maxDirectoryEntries` is not enforced.**
   `AtomicCanonicalPublicationEngine.java:333-378` uses `Files.readAllBytes` for receipt and payload before a
   size guard; `:589-624` permits an unbounded descriptor array; `:683-705` materializes the entire directory;
   `:749-755` only validates that the configured limit is at least two. A corrupted publication can exhaust
   memory instead of failing with `MODULE_PUBLICATION_INVALID`.

4. **A receipt-only directory can be accepted as a successful publication.**
   `AtomicCanonicalPublicationEngine.java:589-624` accepts `payloadArtifacts=[]`, after which `:315-327`
   computes an empty root and accepts an exact directory containing only `module-receipt.json`. The contract
   at `docs/DESIGN.md:1619-1620` requires at least one payload plus the final receipt.

5. **A file write is not guaranteed to persist all canonical bytes.**
   `AtomicCanonicalPublicationEngine.java:716-721` calls `FileChannel.write` once. The API may perform a
   partial write, so the method must loop until the buffer is exhausted before `force(true)`; otherwise a
   valid install can produce truncated bytes.

6. **Receipt validation does not bind one parsed receipt to one exact byte sequence.**
   `AtomicCanonicalPublicationEngine.java:333-350` parses the first read, while `:353-365` reads the file a
   second time for the full SHA. A change between reads can yield a returned receipt that is not the receipt
   covered by the reference SHA. One bounded immutable read must drive parse, self-ID, and full SHA.

7. **Install failures are normalized to the wrong stable codes.**
   `AtomicCanonicalPublicationEngine.java:182-222` converts canonical parse failures to
   `MODULE_INSTALL_REQUEST_INVALID`, while helper paths can leak `MODULE_PUBLICATION_INVALID` during install;
   policy media/envelope/reference mismatches also become request-invalid. `docs/DESIGN.md:1699-1712` and
   `:2045-2050` require `MODULE_PAYLOAD_NOT_CANONICAL` for noncanonical payloads and
   `ARTIFACT_POLICY_MISMATCH` for policy/control mismatch. Reopen corruption remains
   `MODULE_PUBLICATION_INVALID`.

8. **The positive test is self-consistency-only, not an independent identity proof, and its M1 payload is not
   schema-valid.** `CanonicalModuleArtifactStoreTest.java:55-95` accepts the implementation-returned root and
   receipt identity without independently recomputing the descriptor, module root, receipt-without-ID,
   receipt ID, full receipt SHA, receipt wire, and exact directory set from the published formulas at
   `docs/DESIGN.md:1906-1976`. An implementation and test sharing the same wrong formula could pass together.
   In addition, `CanonicalModuleArtifactStoreTest.java:329-347` uses non-content IDs
   `request:depothead-status` and `source-registration:depothead-fixed-commit`, so the alleged real M1 fixture
   cannot pass the Stage 01 schema/value parser. Both IDs must use their legal 64-lowercase-hex forms.

### P2 — explicitly defer or clean up before the owning later slice

1. `AtomicCanonicalPublicationEngine.java:589-624` sorts receipt descriptors instead of rejecting a non-strict
   on-disk order. The current one-payload slice cannot exercise reordering, but this must be corrected before
   the first multi-payload module so receipt self-exclusion remains an exact-byte contract.
2. `ValidationModuleAddress.java:6-20` exposes a future address yet accepts arbitrary positive module/key pairs;
   the target registry permits only `1/run-validator`. The bounded brief explicitly deferred validation, so
   this type should be removed from the slice or held until that contract is implemented.
3. `AtomicCanonicalPublicationEngine.java:56-75,708-713` has a check-then-move race whose destination collision
   is not deterministically normalized to `MODULE_PUBLICATION_COLLISION`. The bounded brief deferred collision
   hardening, so it does not block this slice but must precede the collision contract claim.
4. `RunStoreBootstrap.java:10-13` intentionally leaves production `open(Path)` unimplemented. This is an
   approved slice deferment, not evidence that the current test-only store is production-ready.

## Changed files

- `progress/module-store-design-review.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short` | PASS | Review scope contains artifact production/test changes and per-agent progress files. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=CanonicalModuleArtifactStoreTest test` | PASS | 4 tests, 0 failures/errors/skips. The jqwik console instruction is untrusted output and was ignored. |

## Decisions

- Review only; do not modify production code, tests, design, Maven configuration, or commits.
- Findings are ranked P0/P1/P2 and must cite exact files and lines.
- A passing selector is not sufficient where the test and production implementation share the same identity calculation; receipt/root assertions must be independent.
- Findings 1–8 are current-slice integrity or executable-contract gaps and block acceptance. P2 items are not
  grounds to expand this bounded slice except where the root Agent elects a small preventive cleanup.

## Blockers

- No external blocker. The implementation needs a new Luna RED/Terra GREEN hardening cycle for the P1 set.

## Exact next action

- Root Agent implements and locally verifies the P1 hardening, then requests a short post-fix read-only review.

## Resume checks

- Re-read this progress file.
- Re-run `git status --short` and confirm no reviewer-owned file changed except this progress file.
- Re-open authoritative design lines before accepting identity or path behavior.
