# Progress: Frozen Step01/Step05 input policy RED

- Status: READY_FOR_COORDINATOR_RED
- Agent role: Luna/xhigh RED owner
- Started: 2026-09-16
- Scope: one configured process-input artifact-store seam; no production change

## Reproduced contract

`executeBusinessProcesses` builds the process request assembler from the configured analysis-step
store. Reopened Step01/Step05 artifacts belong to the historical/pre-process input policy registry,
while the current output registry owns newly published process outputs. A minimal Step01 publication
installed with `jdt-artifact-policy-set-pre-process-discovery-v1.json` must therefore reopen through
the process input path without `ARTIFACT_POLICY_MISMATCH`; the process output publisher must retain
the current output registry.

## RED test

`src/test/java/org/sourceanalysis/app/adapter/cli/SourceAnalysisExecutionFrozenSourceInputTest.java`
creates a canonical three-file VERIFIED_SOURCE_INVENTORY publication under the input registry and
configures distinct output/input registries. Policy-set files are loaded through the same
`SourceAnalysisExecution.loadPolicies` conversion used by the formal configuration loader. The test
keeps an explicit output-policy reopen rejection as the contrast, then positively calls the new
package-visible `SourceAnalysisExecution.inputStepArtifacts` factory expected by the formal process
assembler. The current production surface has no such factory, so this is the intentional compile
RED; the expected GREEN is an input-policy store while the existing `stepArtifacts` output factory
remains unchanged for the first five steps. No new ledger, identity mechanism, or full-chain fixture
is introduced.

## Verification boundary

- No Maven, javac, model, JDT, network, CLI, or product call was run by this agent.
- Coordinator should run only this named test after the minimal production routing fix.
