# Progress: target-contracts-red-tests

- Status: COMPLETE
- Agent role: Luna/xhigh RED-test infrastructure slice for public target/contracts
- Model: gpt-5.6-luna / xhigh
- Started: 2026-08-31
- Last updated: 2026-08-31
- Scope: Add only public contract RED tests under `src/test/java/com/linguan/codemd/target/contracts/` and optional test fixtures, plus this progress file.
- Approved inputs: `docs/DESIGN.md` §13; Stage 01 and Stage 08 detailed contracts; existing JUnit/Jackson/Maven dependencies.
- Current branch/worktree: isolated `/private/tmp/linguan-github-code-target-implementation`

## Completed

- Read root, backend, and github-code scoped instructions.
- Read `docs/DESIGN.md` §13 and the Stage 01/08 detailed contracts.
- Confirmed the target seam is not yet present in the production tree.

## Current state

- Added one `TargetContractsTest` covering canonical JSON bytes, content-addressed `ModuleArtifact<T>` / `ArtifactReference`, strict wire parsing, source locator path safety, and canonical JSONL identity.
- The test assumes public APIs `CanonicalJson.canonicalize(JsonNode)`, `CanonicalJson.canonicalizeJsonl(List<JsonNode>, String)`, `ModuleArtifact.parse(byte[], Class<T>)`, `ModuleArtifact.canonicalBytes()`, `ModuleArtifact.reference()`, and a seven-coordinate `SourceLocator` constructor as the minimum target seam.
- The hand-written artifact identity golden is `test-artifact:6f58487b9dc58f4e7d0ef46b33c4083bf06cbc7093505e0251d188c8738ca4bf`; its preimage includes the complete envelope without only the top-level `artifactId`, prefixed by `stage01-test-v1` and LF.

## Changed files

- `backend-agents/sources/github-code/progress/target-contracts-red-tests.md`
- `backend-agents/sources/github-code/src/test/java/com/linguan/codemd/target/contracts/TargetContractsTest.java`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -Dtest=TargetContractsTest test` | RED (expected) | Maven reached `testCompile` and failed with 11 `cannot find symbol` errors for the absent `CanonicalJson`, `ModuleArtifact`, `ArtifactReference`, and `SourceLocator` target seam. Production compilation passed; no test execution occurred. |

## Decisions

- Keep the test in one class because the requested verification selector is exactly `mvn -Dtest=TargetContractsTest test`.
- Use only public planned package/type names and public behavior; do not reflect over private implementation details.
- Treat compilation failure from absent planned public target types as the expected RED seam and record its exact output after the mandated Maven command.

## Blockers

- Expected seam blocker: target public contract types are not present, so this RED slice cannot test-run until the GREEN implementation adds them. This is the intended testCompile RED; no production code was changed.

## Exact next action

- Hand off the scoped test and exact RED output to the target-contracts GREEN implementer; do not alter production code in this slice.

## Resume checks

- Run `git status --short`.
- Confirm only this progress file and the scoped target-contract test/fixture paths changed.
