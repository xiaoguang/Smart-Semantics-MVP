# Progress: Stage 03 integrity core

- Status: COMPLETE
- Owner: Terra/xhigh production implementation agent
- Scope: Turn the Stage 03 integrity selector green by changing only `src/main/java/com/linguan/codemd/stage03/` plus this record.
- Current state: The integrity selector is GREEN: canonical registry identity, per-capsule task packaging, strict R1/R2 closure, pattern-gated admission, typed reader ownership, reader-item budget, and provider exception normalization are implemented.
- Changed files: `Stage03Generator.java`, `Stage03RegistryCanonicalizer.java`, `Stage03Registries.java`, null-tolerant public registry records, and this progress record. The public `Stage03Registries.freeze(...)` seam computes every child content digest and the bundle ID; generation independently recomputes and validates them.
- Verification: `mvn -Dtest=Stage03IntegrityTest test` GREEN: 10 tests, 0 failures, 0 errors. Existing Stage 03 selector `mvn -Dtest=Stage03GeneratorTest,Stage03JshErpBoundaryTest test` GREEN: 14 tests, 0 failures, 0 errors. Direct Stage 01/02 regression selector GREEN: 79 tests, 0 failures, 0 errors. `git diff --check` GREEN.
- Decisions: Preserve strict provider isolation, replay identity, no retries, exactly nine headings, and zero-flow provider-free behavior. No test/design edits.
- Blockers: None.
- Exact next action: None; assigned implementation and verification are complete.
- Resume checks: Preserve the shared worktree's unrelated changes; do not alter other progress files.
