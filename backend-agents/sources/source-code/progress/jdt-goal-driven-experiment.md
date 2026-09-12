# Progress: JDT goal-driven source-navigation experiment

- Status: COMPLETE
- Agent role: experiment implementation and evidence capture
- Model: GPT-5
- Started: 2026-09-12
- Last updated: 2026-09-12
- Scope: isolated JDT LS feasibility experiment only; no production source, source-agent schemas, customer build, model call, commit, or push
- Approved inputs: frozen jshERP commit `8c30ce7861570458920175e200bb2a6442713580`, existing verified local projection, installed JDT LS `1.61.0`, JDK/LSP client dependencies
- Current branch/worktree: `codex/jdtls-source-navigation-feasibility` at `/private/tmp/linguan-source-analysis-process-design`

## Completed

- Read scoped instructions, the approved feasibility plan, the existing isolated research harness, and the JDT call-hierarchy protocol.
- Confirmed the old trial stopped before any definition request and cannot answer the goal-driven question.
- Added a research-only, JavaParser syntax reader that turns a JDT location into one complete
  method body, formal parameters, lexical calls and actual arguments. It performs no target
  resolution.
- Added raw LSP request/response journalling and a `goal-driven` research command that uses
  call hierarchy first, then definition/implementation navigation for individual lexical calls.
- Ran one actual isolated JDT LS session. Its raw call-hierarchy response names
  `UserService.validateCaptcha(String, String)` and `UserService.checkLoginName(UserEx)` with
  repository locations, and the financial path reaches `AccountHeadService` and
  `AccountHeadMapperEx`.
- Found and corrected a source-reader defect: a JDT full method range may start on a Javadoc
  line. The reader now accepts attached Javadoc or annotation anchors, while the walker prefers
  the LSP `selectionRange` when one is present.
- Corrected LSP4J `Either` location handling. The final session used JDT's definition response
  for the previously omitted `UserService.registerUser` call and read its complete body rather
  than treating a valid `{left: [...]}` response as a navigation failure.
- Ran the final immutable two-entry experiment in
  `.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230`.
  Registration produced 76 method bodies and 228 recorded calls, including all three target
  `UserService` bodies. Financial query produced Controller, Service and Mapper interface
  bodies. Raw request/response pairs and both packet hashes are retained with that run.
- Wrote `research/jdtls-source-navigation-feasibility/GOAL-DRIVEN-REPORT.md`. It reports the
  evidence, distinctions from the old inconclusive trial, boundaries, and an adoption
  recommendation without changing production code or claiming a business-model result.

## Current state

- The goal-driven experiment is complete. Its conclusion is scoped to the two measured
  repository entry points: JDT can provide the missing source-navigation material, but external
  dependencies, proxy/mapper behavior and runtime execution remain explicit boundaries.

## Changed files

- `progress/jdt-goal-driven-experiment.md`
- `research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/GoalDrivenSourceReader.java`
- `research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/GoalDrivenExperiment.java`
- `research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/NavigationProbe.java`
- `research/jdtls-source-navigation-feasibility/src/main/java/org/sourceanalysis/research/jdtls/TrialRunner.java`
- `research/jdtls-source-navigation-feasibility/src/test/java/org/sourceanalysis/research/jdtls/GoalDrivenLspPayloadTest.java`
- `research/jdtls-source-navigation-feasibility/src/test/java/org/sourceanalysis/research/jdtls/GoalDrivenSourceReaderTest.java`
- `research/jdtls-source-navigation-feasibility/GOAL-DRIVEN-REPORT.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `mvn -o -Dtest=GoalDrivenSourceReaderTest test` | PASS | 2 tests; complete-method and intersecting-anchor reader behavior verified. |
| `java -jar ... goal-driven ...run-20260912T114000-0230` | PARTIAL | JDT session launched and raw replies show Service/Mapper locations; packet needs one rerun after source-anchor correction. |
| `mvn -o -Dtest=GoalDrivenSourceReaderTest test` | PASS | 3 tests; Javadoc-start JDT range regression is now covered. |
| `mvn -o -Dtest=GoalDrivenSourceReaderTest,GoalDrivenLspPayloadTest test` | PASS | 4 tests; source slicing and LSP4J `Either` location unwrapping are covered. |
| `mvn -o -DskipTests package` | PASS | Isolated research JAR packaged offline before the final JDT session. |
| `java -jar ... goal-driven ...run-20260912T115800-0230` | PASS | Two actual packets and paired raw JDT exchanges written; no customer build or product-model call. |

## Decisions

- Existing production and old packet contracts are not correctness gates.
- The experiment preserves raw request/response pairs and classifies JDT location success separately from experiment source-body extraction.
- The old `REPORT.md` remains historical and inconclusive. `GOAL-DRIVEN-REPORT.md` is the
  new, scoped verdict; it does not overwrite old artifacts.

## Blockers

- None. The approved research scope ends with the report and user review.

## Exact next action

- Await user review of the two real packets and the report before any production integration or
  further tool evaluation.

## Resume checks

- Read this file, inspect `git status --short`, and preserve all earlier staged research artifacts.
