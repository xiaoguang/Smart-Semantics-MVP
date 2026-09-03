# Progress: Program graph input identity debug

- Status: COMPLETE
- Agent role: Sol/xhigh read-only Debug Agent
- Model: gpt-5.6-sol / xhigh
- Started: 2026-09-03T06:11:40Z
- Last updated: 2026-09-03T06:21:00Z
- Scope: Diagnose the application-profile domain-ID versus artifact-ID mismatch at the ProgramGraphsExecution persisted-input boundary; no production or test changes.
- Approved inputs: Scoped AGENTS.md, program-graphs M1/M2/M3 identity contract, ProgramGraphsExecutionTest, persisted input reader, ApplicationDiscovery publisher/executor/reader, and fixture artifact JSON.
- Current branch/worktree: codex/source-analysis-program-graphs / /private/tmp/linguan-source-analysis-verified-inventory/backend-agents/sources/source-code

## Completed

- Read the scoped repository rules and systematic debugging workflow.
- Recorded the pre-existing dirty worktree without modifying or cleaning it.
- Traced the identity from `ApplicationProfileDetector` through the M1 draft publisher, the ApplicationDiscovery M4 publication, and the ProgramGraphs persisted-input reader.
- Compared the failing synthetic reader fixture with the integration fixture and the real publication algorithm.
- Identified the root cause and the smallest strict correction surface; no production or test file was changed by this task.

## Current state

- Root cause: the prior `PersistedProgramGraphInputReader` compared the domain `applicationProfileId` from the profile body to `application-profile.json.artifactId`. Those are intentionally distinct identities with distinct preimages.
- `ApplicationProfileDetector.applicationProfileId(...)` computes the domain identity with the `application-discovery-application-profile-id-v2` frame over the semantic application-profile material.
- `ApplicationDiscoveryPublicationSpecifier.standalone(...)` computes the final semantic file artifact identity with `canonical-standalone-json-artifact-id-v1` over schema, type, and canonical document bytes excluding `artifactId`, then writes that identity to the document's `artifactId` and descriptor.
- The publisher correctly preserves the domain identity in both `application-profile.json.applicationProfileId` and `capability-report.json.applicationProfileId`; it does not write the wrong ID.
- The strict reader must therefore make two different checks: descriptor artifact ID equals `application-profile.json.artifactId`, and profile domain `applicationProfileId` equals capability domain `applicationProfileId`.
- The shared WIP currently already contains exactly that production correction in `PersistedProgramGraphInputReader.java`; this debug task did not author or modify it. The latest existing Surefire report shows `ProgramGraphsExecutionTest` green after that change.
- The older `PersistedProgramGraphInputReaderTest` fixture encoded the same invalid alias by setting `application-profile.json.artifactId = applicationProfileId`, while its descriptor carries a different synthetic `artifact:*` ID. That fixture must be corrected to construct a canonical standalone payload whose JSON `artifactId` equals its descriptor, while leaving `applicationProfileId` unchanged. Reader validation must not be weakened.

## Changed files

- `progress/program-graph-input-identity-debug.md`

## Verification

| Command | Result | Key output |
| --- | --- | --- |
| `git status --short --branch` | PASS | Confirmed the assigned branch and preserved all pre-existing Stage03 work. |
| `git diff -- PersistedProgramGraphInputReader.java` | PASS | The shared WIP changes the invalid domain-ID/file-ID comparison to descriptor-ID/file-ID plus domain-ID/domain-ID checks. |
| Read existing `target/surefire-reports/TEST-org.sourceanalysis.app.analysis.graph.ProgramGraphsExecutionTest.xml` | PASS | Latest stored result is 1 test, 0 failures/errors after the shared production correction. |
| Read existing `target/surefire-reports/TEST-org.sourceanalysis.app.analysis.graph.PersistedProgramGraphInputReaderTest.xml` | EXPECTED FIXTURE FAILURE | Its old fixture now fails the newly correct descriptor-versus-document identity check at the reader boundary. |

## Decisions

- The diagnosis will not propose reader tolerance or a test bypass; any correction must preserve exact identity validation.
- The approved M1/M2/M3 handoff is not ambiguous: `ProgramGraphInputBasis` and `CodeStructureDiscovery` deliberately carry both `applicationProfileId` and `applicationProfileRef`.
- Minimal production surface is only `PersistedProgramGraphInputReader.reopen`: compare the profile JSON's `artifactId` with `reference(profilePayload).artifactId`, compare the two domain `applicationProfileId` fields with each other, and pass the descriptor-derived `applicationProfileRef` downstream.
- Minimal test surface is the standalone profile payload construction in `PersistedProgramGraphInputReaderTest`; it must stop self-aliasing the domain ID as the file artifact ID.

## Blockers

- None.

## Exact next action

- Return the diagnosis to the parent. The production correction already present in shared WIP should be retained; assign the reader-fixture correction to its test owner and rerun the two direct selectors serially.

## Resume checks

- Re-read this progress file and verify the two strict equalities remain separate. Do not accept any change that makes the reader compare or alias the domain ID with the final file Artifact ID.
