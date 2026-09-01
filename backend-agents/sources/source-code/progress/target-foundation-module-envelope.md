# Progress: target Foundation module envelope

- Status: COMPLETE
- Agent role: Terra/xhigh implementation owner for the shared target module-envelope seam
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-01
- Last updated: 2026-09-01
- Scope: Replace the superseded `target/contracts/ModuleArtifact` design with a target-artifacts module-envelope writer/parser that matches `docs/DESIGN.md` §13.3 and can be used by Stage01 M1 without an in-memory bypass.
- Approved inputs: User-approved F batch; `docs/DESIGN.md` §§3.2, 13.3–13.3.1; scoped `AGENTS.md`; existing Foundation store tests only as implementation reference.
- Current branch/worktree: `codex/github-code-target-implementation` at `/private/tmp/linguan-github-code-target-implementation/backend-agents/sources/github-code`

## Completed

- Read the old target contract and verified it is structurally incompatible with the target design: it uses obsolete control fields, producer shape and non-framed identity.
- Identified the confirmed seam: one writer/parser in `target.artifacts`, which generates or fresh-reopens one canonical `MODULE_ARTIFACT_JSON` envelope. Callers provide only a typed address, controls, sorted upstream references, success status/Gaps and a canonical payload node.

## Current state

- The generic module Store can validate canonical bytes and write receipt-last directories, but it currently treats ordinary module JSON as an opaque document. Stage01 M1 therefore cannot yet install/reopen its required module artifact.
- One public-seam RED now requires the absent writer/draft/result classes to canonicalize a Stage01 M1-shaped envelope and fresh-parse it without accessing a filesystem layout.
- The exact selector has been run after that test was added: test compilation failed with five expected missing-symbol errors for `ModuleArtifactEnvelope` and `ModuleArtifactEnvelopeDraft`; no unrelated failure was observed.
- Implemented the target-artifacts writer/parser. It owns the exact envelope, typed producer address, policy binding, ordered upstream references, controls, success completion, framed identity, strict duplicate-field parsing and fresh canonical-byte validation.
- Bound `FileSystemCanonicalModuleArtifactStore` to that parser whenever the registered policy is `MODULE_ARTIFACT_JSON`; a generic JSON object with a recomputed generic ID can no longer bypass its success-completion contract.

## Changed files

- `progress/target-foundation-module-envelope.md` (this continuity record)
- `src/test/java/com/linguan/codemd/target/artifacts/ModuleArtifactEnvelopeTest.java` (new public-seam RED)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -t .mvn/toolchains.xml -o -Dtest=Stage01FrozenRequestAdmissionTest test` | PASS | The M1 pure seam is GREEN but deliberately has no module publication yet. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModuleArtifactEnvelopeTest test` | RED | Test compilation fails only because the new `ModuleArtifactEnvelope` / `ModuleArtifactEnvelopeDraft` seam is absent. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModuleArtifactEnvelopeTest test` | PASS | 1 test, 0 failures/errors; target writer and fresh parser agree on reference and payload. |
| `mvn -t .mvn/toolchains.xml -o -Dtest=ModuleArtifactEnvelopeTest test` | PASS | 2 tests, 0 failures/errors; malformed success completion is rejected before module installation. |
| `mvn -t .mvn/toolchains.xml -o -DspotlessFiles='src/main/java/com/linguan/codemd/target/.*\\.java|src/test/java/com/linguan/codemd/target/.*\\.java' spotless:check` | PASS | Target-owned Java files conform to the configured formatter. |
| `git diff --check` | PASS | No whitespace errors. |

## Decisions

- The confirmed public test seam is `ModuleArtifactEnvelope.write(...)` and `ModuleArtifactEnvelope.parse(...)`, not the filesystem store's private directory layout.
- The writer/parser is a deep module: it owns exact keys, recursive canonicalization, framed content identity, producer address serialization, sorted upstream references, controls and completion shape. Stages will not repeat that logic.

## Blockers

- None.

## Exact next action

- Complete the Stage01 M1 publisher: install this now-valid module artifact through the generic Store, then fresh-reopen it for M2.

## Resume checks

- Re-read this progress record, `docs/DESIGN.md` §13.3, and Foundation Store contract before modifying code.
- Do not import or adapt `target/contracts/ModuleArtifact`; that file is a deferred deletion target, not a compatibility source.
