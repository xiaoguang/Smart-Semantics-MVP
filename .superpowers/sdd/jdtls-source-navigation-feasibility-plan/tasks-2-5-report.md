# Tasks 2–5: JDT LS source-navigation feasibility harness

## Status

**MEASURED_FAIL — do not accept JDT LS source navigation for this constrained trial yet.**

The isolated harness and its direct contract are complete, but the retained JDT LS execution encountered a Task 3 terminal condition: Buildship attempted to fetch Gradle version metadata from `services.gradle.org`. The sandbox prevented that network access (`UnknownHostException`), no customer build ran, and the driver correctly stopped before financial navigation. This is not evidence of a Java declaration/definition/implementation binding limitation; it is an offline-startup/configuration blocker that needs separately authorized investigation.

## Scope and implementation

- Added the research-only POM with exact JavaParser `3.28.2`, LSP4J/JSON-RPC `1.0.0`, Gson `2.14.0`, and Jackson BOM `2.21.4` dependencies.
- Implemented byte-verified frozen-source materialization, the generic JavaParser syntax-only caller enumerator, an LSP4J stdio runner, and an independent post-write `PacketOracleCheck`.
- Kept checks/oracles out of `NavigationProbe`; the checker runs only after a packet is atomically written and SHA-256 hashed.
- Kept JavaParser syntax-only. Declaration, definition, implementation, and call-hierarchy requests are sent to JDT LS. Actual/formal text is retained in packet methods/calls rather than resolved by a custom name resolver.
- Registration is the hard gate for financial. The retained run wrote a registration packet only; financial was not started.
- Applied one bounded generic-protocol correction after the measured packet: with the brief-required empty capabilities object, JDT LS may respond to `textDocument/documentSymbol` with flat `SymbolInformation` (`location.range`) rather than hierarchical `DocumentSymbol` (`range`). The walker now accepts both forms. No JDT rerun followed, by instruction.

## Direct verification

| Command | Result |
| --- | --- |
| `mvn -f backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml -o -Dtest=NavigationProbeTest test` | Initial approved RED: 13 tests, 12 expected assertion failures, 0 errors. |
| `mvn -f backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml dependency:go-offline` | Resolved the missing, fixed research-only LSP4J artifacts and shade plugin once. |
| `mvn -f backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml -o -Dtest=NavigationProbeTest test` | GREEN after implementation and again after the bounded flat-symbol correction: 13 tests, 0 failures, 0 errors. |
| `mvn -f backend-agents/sources/source-code/research/jdtls-source-navigation-feasibility/pom.xml -o -DskipTests package` | PASS before the final source-only flat-symbol correction; produced the prescribed shaded jar. |
| `java -jar .../jdtls-source-navigation-feasibility.jar materialize --trial-root ... --commit 8c30ce7861570458920175e200bb2a6442713580` | PASS. Created the exact archive, manifest, and full unmanaged projection. |
| `java -jar .../jdtls-source-navigation-feasibility.jar probe --cases registration,financial --trial-root ...` | Measured terminal failure described below. |

The last direct test command completed at `2026-09-12T09:58:44-02:30` with `BUILD SUCCESS` and 13/0/0.

## Input and tool evidence

- Frozen source commit: `8c30ce7861570458920175e200bb2a6442713580`.
- Materialized projection: 273 selected `src/main/java` files; each manifest source has a 40-hex Git blob ID and 64-hex SHA-256. No selected source entry was malformed, and the projection contained no Maven, Gradle, Eclipse, or annotation-processor configuration.
- Fresh materialized archive SHA-256: `5e517b68d0365e00dd090211e999913dc85edf9de95983639f25f9162995be2d`.
- JDT LS archive SHA-256: `338e7e73d61836651ba2453919a0d34fa763eb4e7c03342092309bffb8934c64` (published expected value), with exactly one expected Equinox launcher.
- Embedded JDT LS release identity: bundle `1.61.0.202609031315`, Eclipse source commit `08eafe6ff60c7159ef88571d47b6a9ef82fef94e`.
- Research client dependencies were verified as LSP4J/JSON-RPC `1.0.0` and a single direct Gson `2.14.0` pin. The tool manifest now records `downloaded`, `sha256Verified`, `extracted`, and `launched` as true, plus the measured terminal runtime status.

## Retained measured session

The retained session used the prescribed `java -jar ... probe --cases registration,financial` command and one stdio JDT process. Its request journal records the prescribed first `initialize` request with:

- `rootUri` set to the projection root;
- empty capabilities;
- the exact nested Maven/Gradle disabled settings, `sourcePaths=["src"]`, empty referenced libraries, downloads disabled, and build-configuration updates disabled;
- matching `initialized` and `workspace/didChangeConfiguration` notifications.

JDT LS emitted `ServiceReady`. During startup it also emitted a `logMessage` reporting `Could not load Gradle version information`; the recorded stack trace contains `org.gradle...PublishedGradleVersions.downloadVersionInformation` and `UnknownHostException: services.gradle.org`. The network restriction held, but the attempt itself is a Task 3 stop condition.

The preserved packet at `backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/results/registration/packet.json` has SHA-256:

```text
3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424
```

Its primary gate is `FAIL`, with `FULL_BODY_NOT_FOUND` for the registration controller entry and zero methods/calls. This packet was produced before the flat `SymbolInformation.location.range` correction, so it does not establish a JDT binding outcome. Financial is absent by design: `results/verdict/verdict.json` records `registration primary gate failed; financial phase was not entered`.

## Runtime-status update

The ignored trial tool manifest now records:

```text
selectionStatus = APPROVED_PINNED_VERIFIED_RUNTIME_FAIL
runtimeStatus   = MEASURED_FAIL_FORBIDDEN_GRADLE_NETWORK_ATTEMPT
lastTrial       = ServiceReady=true, registrationPrimaryGate=FAIL, financialPhase=NOT_ENTERED
```

This preserves successful download/checksum/extraction/launch evidence while not representing the run as a successful offline feasibility result.

## Verdict candidate

**FAIL (environment/configuration stop condition; binding capability not assessed).**

An authorized follow-up may investigate why Buildship performs the Gradle metadata request before the supplied import settings can suppress it. It must use a new approval and a new bounded session; it should not treat the pre-correction empty registration packet as proof that JDT cannot navigate the selected source.

## Concrete limitations

- The sandbox blocked network access, but its process-descendant API was unavailable (`PROCESS_TREE_UNAVAILABLE_SANDBOX`), so child-process inspection is recorded as an environment limitation rather than claimed as complete enforcement.
- The Buildship metadata request makes the retained session non-compliant with the no Maven/Gradle attempt requirement even though it did not run a customer Gradle build.
- Financial navigation and optional mapper XML handling were not attempted because registration failed its primary gate.
- The corrected flat-document-symbol form has direct-test coverage through compilation/contract tests but has not been measured in another JDT session, by the explicit no-rerun instruction.
- Earlier non-result-producing startup attempts were cleared only after their JDT PIDs were verified stopped; the current ignored runtime/results tree is the preserved measured failure evidence. A strict audit that counts every exploratory launch cannot call the overall history a single launch, even though the retained result-producing run uses one server process.
