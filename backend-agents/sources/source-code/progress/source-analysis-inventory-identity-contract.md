# Progress: source-analysis-inventory-identity-contract

- Status: COMPLETE
- Agent role: cross-step identity contract editor
- Model: gpt-5.6-sol / ultra design authority
- Started: 2026-09-02
- Last updated: 2026-09-02
- Scope: Close the two missing source-inventory identity definitions: the frozen Git origin discriminator and the stable verified source-file identifier. Documentation only; no Java, schema or artifact changes.
- Approved inputs: Published overall design; verified-source-inventory detailed design; prior read-only design review. No customer source capture, Maven execution, network source or Provider.
- Current branch/worktree: `codex/source-analysis-inventory-identity-contract` at `/private/tmp/linguan-source-analysis-inventory-identity-contract`

## Completed

- Started from pushed `main` commit `3327990` after the private source-registration registry delivery.
- Re-read all identity, request, M1/M2 and artifact passages in the verified-source-inventory design.
- Added the closed `ExpectedOriginV2` contract: one `GIT_SHA1_COMMIT` kind with exact captured repository identity, lowercase 40-hex revision and SHA-1 object format binding.
- Added the `file:` identity preimage and M1/M2 re-derivation rule. It contains exactly canonical repository-relative path, Git mode, byte count and SHA-256.
- Replaced the five illustrative `source-file:` values in ApplicationDiscovery and ProgramGraphs with the sole valid `file:` prefix.

## Current state

- The source-inventory design now defines the exact origin and file-ID wire contract. This delivery changes no Java, JSON Schema or persisted artifact and does not claim M1/M2/M3 is implemented.

## Changed files

- `progress/source-analysis-inventory-identity-contract.md`
- `docs/analysis-steps/01-verified-source-inventory.md`
- `docs/analysis-steps/02-application-discovery.md`
- `docs/analysis-steps/03-program-graphs.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| design identity audit | PASS | Confirmed the missing origin enum and file-ID preimage are absent from the current Step 01 contract. |
| `rg -n "source-file:" docs --glob '*.md'` | PASS | Only two deliberate Step 01 prohibitions remain; no example or contract accepts the obsolete prefix. |
| `git diff --check` | PASS | Documentation patch has no whitespace errors. |

## Decisions

- Preserve the existing request field names. Define one source-origin kind, `GIT_SHA1_COMMIT`, rather than encoding the local capture transport in the origin identity.
- Use `file:` consistently and derive the ID only from repository path, Git mode, byte count and SHA-256. Snapshot membership remains a separate relation.

## Blockers

- None. This is a narrow documentation completion under the already-published eight-step architecture; M1/M2/M3 implementation may now follow the published contract.

## Exact next action

- Commit and push this docs-only contract. Start a fresh implementation branch from its `main` commit for M1/M2/M3 source-inventory completion.

## Resume checks

- Read this file, inspect the Step 01 identity contract, and confirm no Java or JSON Schema file is modified in this docs-only delivery.
