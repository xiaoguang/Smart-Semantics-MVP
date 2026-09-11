# Progress: program-graphs discovery fixture alignment

- Status: COMPLETE (fixture alignment only; downstream reader contract mismatch remains)
- Agent role: Luna/xhigh bounded test-fixture migration owner
- Model: gpt-5.6-luna / xhigh
- Started: 2026-09-09
- Last updated: 2026-09-09
- Scope: Align only the `ProgramGraphsPublicFixture` constructed discovery predecessor with the real ApplicationDiscovery v2 profile, capability, site, shard, and source-reference wire consumed by the strict Fact reader; preserve graph execution, source snippets, entry IDs, Fact/Flow algorithms, and assertions.
- Approved inputs: Existing `ProgramGraphsPublicFixture`, real ApplicationDiscovery publishers/readers and wire contracts, Terra's bounded Fact-reader handoff, and existing source-scoped plans/design. The sole Maven lease was granted for the final scoped validation only.
- Current branch/worktree: `/private/tmp/linguan-source-analysis-process-design`, branch `codex/source-analysis-business-flows-closeout`; preserve unrelated shared-worktree changes.

## Completed

- Read the source-scoped instructions, fixture, real discovery publication code, and `progress/discovery-to-fact-handoff-implementation.md`.
- Confirmed the current fixture's discovery predecessor is test-constructed rather than a real Capture/Inventory/Discovery integration.
- Created this progress file and marked it intent-to-add before any fixture Java edit.
- Replaced the handcrafted discovery-module payload path with the existing public ApplicationDiscovery M1–M4 publishers over synthetic `ApplicationProfile`, `HttpEntryDiscovery`, and `MapperCatalogDiscovery` values derived from the fixture's existing source/entry/mapper basis.
- Added only the three discovery draft policies required by those existing publishers; no source snippets, entry IDs, graph algorithms, Fact/Flow assertions, production code, or design files were changed.
- Corrected the synthetic source predecessor's inventory/snapshot references to the exact artifact IDs and SHA-256 values emitted by its own persisted source payloads; this removed the fixture-only `APPLICATION_DISCOVERY_UPSTREAM_INVALID` bootstrap error.
- Applied and checked Spotless on the owned Java file after the final helper correction. The fixture now reaches the real Fact input reader and all four FactCandidateEnumerator tests pass.

## Current state

- The fixture now obtains the exact profile/capability/site/shard/source-publication wire from the existing publishers. Synthetic sites are derived from each existing entry's method excerpt and mapper candidate; all are supported, gap-free, and closed by one shard per discovery kind.
- The profile is built from the existing `VerifiedSourceTextSet` snapshot, scope, eligibility, capability-profile, source-inventory, verified-snapshot, and controls values. No new upstream identity or source behavior was invented.
- The real ApplicationDiscovery publisher emits singular JSONL line headers (`application-discovery-entry-point-v2` and the corresponding mapper-entry schema), while `PersistedFlowCompilationInputReader` still requires the legacy plural entry-line schema. The seven EntryRootedFlowCompiler failures therefore occur in the existing downstream reader at `requireJsonLineHeader`, not in fixture setup; no compatibility fallback or fixture reversion was added.

## Changed files

- `progress/program-graphs-discovery-fixture-alignment.md` (owned; intent-to-add staged before Java edit)
- `src/test/java/org/sourceanalysis/app/analysis/graph/ProgramGraphsPublicFixture.java` (owned; discovery-predecessor helper/policy alignment only)

## Verification

| Command | Result | Key output |
|---|---|---|
| Read-only fixture/writer inspection | PASS | Mapped the real profile, capability, site, shard, source-publication, and draft policy contracts before editing. |
| Fixture alignment edit | PASS | Uses existing public M1–M4 publishers and synthetic discovery domain values; source/entry/graph semantics remain unchanged. |
| Absolute one-file Spotless apply/check (sessions `65799`/`54910`) | PASS | One owned Java file selected; apply and check exited 0. |
| First exact aggregate (session `87195`) | RED: fixture bootstrap only | Numeric exit 1; `11` run, `0` failures, `11` errors, `0` skipped. All errors were `APPLICATION_DISCOVERY_UPSTREAM_INVALID` from the old synthetic source references at `ApplicationDiscoveryPublicationSpecifier.requireInventoryClosure:384`; fixed by the owned reference helper below. |
| Final absolute one-file Spotless apply/check (sessions `66382`/`49727`) | PASS | One owned Java file selected; apply exited 0 with the file already clean, check exited 0. |
| Final exact aggregate (session `33463`) | RED: downstream consumer contract | Numeric exit 1; `11` run, `7` failures, `0` errors, `0` skipped. `FactCandidateEnumeratorTest`: `4/0/0/0` PASS. `EntryRootedFlowCompilerTest`: `7/7/0/0` fail with `UPSTREAM_ARTIFACT_REPLAY_MISMATCH` at `PersistedFlowCompilationInputReader.requireJsonLineHeader:911`, called from `parseDiscovery:261`; the actual publisher's singular JSONL line header is rejected by the existing plural-header reader. |
| Scoped worktree review | PASS | Only the owned fixture and this progress file are changed by this slice; no production/design/other-test/source/provider/commit changes. |

## Decisions

- Keep this predecessor explicitly synthetic and preserve the existing source-reader basis, entry IDs, graph publication, Fact/Flow algorithms, and assertions.
- Derive every added profile/capability/site/shard/reference value from the existing fixture source and actual ApplicationDiscovery producer contract; stop if truthful alignment requires invented source behavior or unsupported fields.
- Use the public publisher seam rather than duplicating its JSON encoding; this keeps descriptor IDs, source-publication references, controls, site/shard closure, and per-line schemas on the published contract.
- Keep the final singular-vs-plural JSONL header failure as evidence for the downstream consumer migration owner; changing the fixture back to handcrafted plural lines would conceal the real producer/reader contract mismatch and violate this slice's scope.

## Blockers

- The direct Flow dependent tests remain RED because the existing `PersistedFlowCompilationInputReader` rejects the real publisher's singular entry-line header. This is outside the owned fixture-only scope and must not be papered over here. The Fact reader path is verified GREEN (`4/0/0/0`).

## Exact next action

- Release the Maven lease. Root/another owner may use the precise `UPSTREAM_ARTIFACT_REPLAY_MISMATCH` evidence for the downstream reader contract repair; this fixture slice is complete and must not edit production or consumer assertions.

## Resume checks

- The final exact aggregate was run under the sole lease and exited numerically `1`; no further Java changes are needed or authorized for this slice.
- No production, design, schema, other test, source, network, Provider, commit, or push changes are authorized.
