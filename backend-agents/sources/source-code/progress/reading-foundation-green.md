# Progress: Reading foundations GREEN

- Status: READY_FOR_COORDINATOR_VERIFICATION
- Agent role: Frozen process-source and reading-runtime implementation owner
- Model: gpt-5.6-terra / xhigh
- Started: 2026-09-16
- Last updated: 2026-09-16
- Scope: Package-private `FrozenProcessSourceCorpus`, approved internal reading inputs, v3 process publication/history reader, shared CLI request assembly, prompt-v3 contract correction, and later pair-reuse validation after RED
- Owning plan: `docs/supplements/cross-object-process-reconstruction/README.md`
- Approved inputs: `docs/supplements/cross-object-process-reconstruction/module-design.md`, `FrozenProcessSourceCorpusTest`, and `VerifiedSourceTextSet`
- Current branch/worktree: `codex/cross-object-process-reading`; formal source-code checkout

## Completed

- Read root, backend, and source-code scoped instructions.
- Read the approved cross-object reading module design and the RED fixture.
- Confirmed the RED fixture failed only because the package-private corpus type and its API were absent.
- Received the coordinator's explicit, narrow extension to carry the four optional reading inputs on `ProcessDiscoveryRequest`; pipeline behavior remains out of scope.
- Received the coordinator's subsequent scope for the shared CLI/runtime reading path, receipt producer v3, and historical v2/v3 checkpoint reopen.
- Coordinator direct group established two prompt-contract failures: the v3 instructions must name the narrative, activity-use-local-ID, and `UNRESOLVED` fields, while REVIEW must not seed a domain term. Pair-reuse storage validation remains deferred until Luna supplies its RED result.
- Luna's storage RED now establishes the narrow reuse failure: `reopenProcessPair` accepted a saved pair with a missing packet, missing mapping, or wrong source run. The approved GREEN is limited to saved run/phase/job/provider identity plus `process-reading-packet-v1` object and mapping-array checks.

## Current state

- The implementation is a self-contained, package-private in-memory view over the already verified text set: sorted file directory, exact one-based inclusive reads, literal search with bounded line context, and explicit source-scope errors.
- It will not read the working tree, invoke a parser, execute source, access the network, or modify inventory contracts.
- `ProcessDiscoveryRequest` will carry the optional verified-inventory reference and reader only as an all-or-nothing pair, plus optional saved catalog input and focus question; its existing three- and four-argument forms continue to supply null optional inputs.
- The configured process route now assembles Activity, M10, same-basis inventory, frozen reader, optional catalog pair, and exact focus question only after its output run is queued and before it writes the v3 execution configuration or starts a Provider. The package-visible assembly function is shared with the fixed acceptance driver rather than creating a second command path.
- `CanonicalBusinessProcessPublisher` now emits receipt producer `v3` without changing the five public payload schemas. `BusinessProcessCheckpointReader` accepts only historical receipt `v2` or current `v3`, while retaining its exact five-file, descriptor, closure, and deterministic rendering checks.
- The DRAFT and REVIEW v3 resources now name the exact `narrative`, `activityUseLocalIds`, and `UNRESOLVED` fields and require a concrete condition; REVIEW uses only generic object-progress/state/persistence/external-success wording. The same instructions are synchronized in `prompts.zh-CN.md`.
- Process-pair reuse now performs its reading-specific validation at `reopenProcessPair`: only the source batch's matching run/phase/job/provider record with a `process-reading-packet-v1` object and a mapping array may enter the existing generic reviewed-pair reader result.
- Frozen process inputs now reopen Step01/Step05 through the package-visible `inputStepArtifacts` factory backed by `inputPolicyRegistry`. The normal `stepArtifacts` factory remains bound to the current output policy for production publication; no global registry substitution occurred.
- The all-candidate navigation projection now sends only `statementCount`; full statement handles remain in complete Activity records and each packet's `statementDirectory`. Reading-check `name`, `purpose`, and `scope` are required nullable schema fields so the existing null-to-prior-candidate fallback is explicit.
- Coordinator verification confirmed the input-policy factory test passed and the formal 326-source scripted offline CLI reached FINISHED. This establishes routing/lifecycle behavior only, not business-quality acceptance.
- Candidate DRAFT/REVIEW completion now finalizes and persists each valid pair through the existing executor completion sink, using source normalization already computed before the jobs start. On a candidate fatal, the executor drains started jobs so their independently valid pairs persist, then rethrows without consolidation or publication; serial reconstruction applies the same immediate finalization and output aggregation remains source-catalog ordinal order.
- The final PMD RED is limited to `SourceAnalysisExecution.Arguments.parse` cyclomatic complexity (105). The PMD 7.17 count confirms that extracting only the four repeated `AnalysisRunId` conversions reaches the target while preserving every switch label, validation order, mode rule, and `ARGUMENTS_INVALID` cause behavior; `--max-bytes` remains unchanged.
- The ignored real acceptance harness at `.workspace/cross-object-reading-v3-20260916/inspection/BusinessProcessRealSampleDriver.java` shares the package-visible request assembly and sample seams. It starts a distinct v3 batch for `selection` or a 2–3-candidate `selected` follow-up, preserves full-catalog ordinals, records a create-new report, and transitions the allocated run to FINISHED or FAILED without adding a CLI command or duplicating discovery logic.
- Its colocated `real-config.json` is a strict current-schema copy of the fixed v2 run: it removes only retired `business.process` and `business.report`, redirects output to `.workspace/cross-object-reading-v3-20260916/output`, and retains the fixed catalog journal, source, material, profile, and provider binding.

## Changed files

- `progress/reading-foundation-green.md`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/FrozenProcessSourceCorpus.java`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/ProcessDiscoveryRequest.java`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/CanonicalBusinessProcessPublisher.java`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/BusinessProcessCheckpointReader.java`
- `src/main/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisExecution.java`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/business-process-draft-v3.txt`
- `src/main/resources/org/sourceanalysis/app/analysis/knowledge/business-process-review-v3.txt`
- `src/main/java/org/sourceanalysis/app/analysis/knowledge/DefaultBusinessProcessDiscovery.java`
- `docs/supplements/cross-object-process-reconstruction/module-design.md`
- `docs/supplements/cross-object-process-reconstruction/prompts.zh-CN.md`
- `.workspace/cross-object-reading-v3-20260916/inspection/BusinessProcessRealSampleDriver.java` (ignored acceptance-only harness)
- `.workspace/cross-object-reading-v3-20260916/inspection/real-config.json` (ignored strict current-schema config)

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `JAVA_HOME=... mvn -q -t .mvn/toolchains.local.xml -Dtest=FrozenProcessSourceCorpusTest test` | EXPECTED RED (coordinator) | Compilation identified only the missing corpus class/API. |
| Java 17 `FrozenProcessSourceCorpusTest` (coordinator) | PASS | 2/2 tests pass. |
| `git diff --check` | PASS | No whitespace errors in the source-code checkout. |
| Ignored real config static comparison | PASS | Apart from the two retired business sections, its only delta from the frozen v2 config is the new v3 output directory; the original catalog journal remains fixed. |
| `SourceAnalysisExecutionFrozenSourceInputTest` (coordinator) | EXPECTED RED | Compilation named only the missing `inputStepArtifacts` input-policy reader factory. |
| `SourceAnalysisExecutionFrozenSourceInputTest` (coordinator) | PASS | Frozen Step01/Step05 reopening now uses the input registry without changing output publication policy. |
| Formal 326-source scripted offline CLI (coordinator) | FINISHED | Routing and run lifecycle completed without JDT or a live model; not a business-quality result. |
| Completion-persistence pipeline test (coordinator) | EXPECTED RED | One successful candidate pair was lost when another concurrent candidate failed: expected 1 saved pair, observed 0. |
| PMD `SourceAnalysisExecution.Arguments.parse` (coordinator) | EXPECTED RED | Cyclomatic complexity 105; requested minimal helper extraction targets 99 without suppressions or threshold changes. |

The coordinator's expanded Maven stopped during test compilation with missing foundational
artifact/run classes that are present and valid under `javap`; it is being isolated as a
concurrent compiler-output issue, not treated as a reading-path failure. This agent did not run
or restart that build.

## Decisions

- File ordering is stable repository-relative UTF-8 byte order; the fixture's ASCII paths produce the same visible order.
- Returned text preserves the exact decoded UTF-8 bytes, including `LF` or `CRLF` line endings and a final unterminated line.
- A read is `complete` only when it returns the file's complete text; an exact bounded range remains partial.
- Unknown file keys and invalid source ranges use `PROCESS_SOURCE_TEXT_` errors; path traversal and absolute paths are rejected as invalid arguments.

## Blockers

- None.

## Exact next action

- The parser-helper GREEN is ready for coordinator-controlled formatting, CLI verification, and
  full CI. Do not start Maven, javac, a driver, a model, JDT, or network work here.

## Resume checks

- Re-read this file and run `git status --short` before further edits.
- Preserve all unrelated worktree changes and run only the named targeted Maven test after requesting the coordinator's required Maven-command approval.

## Plan closeout destinations

- Durable decisions: `docs/supplements/cross-object-process-reconstruction/module-design.md`
- Remaining issues: `docs/supplements/cross-object-process-reconstruction/acceptance.md`
- Verification and output references: `docs/supplements/cross-object-process-reconstruction/delivery.md`
