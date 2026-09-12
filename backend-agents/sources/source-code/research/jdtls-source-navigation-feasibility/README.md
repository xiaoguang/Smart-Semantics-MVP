# JDT LS source-navigation feasibility research

This directory is an isolated, throwaway research harness for the approved JDT LS source-navigation feasibility trial. It is not a production package, does not join the Source Code Analysis Agent reactor, and must never import or execute a customer build. The trial input and all installed tools, projections, runtime data, logs, and measured results stay under the ignored `.workspace/jdtls-source-navigation-feasibility/` tree.

## Current interpretation and navigation

The [historical report](REPORT.md) remains the exact record of the completed empty-capabilities attempt: the client received a flat name range and the walker stopped before any definition request. That measurement invalidates the prescribed client/body-recovery contract; because no binding request ran and no response body was preserved, it is **not** a JDT navigation-capability verdict.

The approved next study is defined by the [goal-driven design](../../docs/plans/jdtls-source-navigation-feasibility-design.md) and [implementation plan](../../docs/plans/jdtls-source-navigation-feasibility-plan.md): given only a Controller entry, automatically collect all navigable repository call bodies, arguments, parameters, conditions and returns, with explicit boundaries. It reuses this harness and its frozen evidence; no new measurement, production integration, model call, nine-chapter output or second tool has run yet.

## Tool decision (Task 1)

Status on 2026-09-12: **APPROVED_PINNED_INSTALLED_AND_VERIFIED**. The official archive is present under the ignored selected-tool directory, its SHA-256 matches the published value, extraction and the single expected launcher were verified, and the pinned LSP4J client dependencies were resolved for this research POM only. The machine-readable installation/runtime facts are in the ignored `.workspace/jdtls-source-navigation-feasibility/tool-manifest.json`.

| Item | Pinned decision |
| --- | --- |
| JDT LS distribution | Official milestone `1.61.0`, the highest version in the [official milestone directory](https://download.eclipse.org/jdtls/milestones/) when checked on 2026-09-12 |
| Archive | `jdt-language-server-1.61.0-202609031315.tar.gz`, from the [official 1.61.0 directory](https://download.eclipse.org/jdtls/milestones/1.61.0/) |
| Exact download URL | `https://download.eclipse.org/jdtls/milestones/1.61.0/jdt-language-server-1.61.0-202609031315.tar.gz` |
| Published SHA-256 | `338e7e73d61836651ba2453919a0d34fa763eb4e7c03342092309bffb8934c64`, from the [adjacent official checksum](https://download.eclipse.org/jdtls/milestones/1.61.0/jdt-language-server-1.61.0-202609031315.tar.gz.sha256) |
| Release source identity | Tag [`v1.61.0`](https://github.com/eclipse-jdtls/eclipse.jdt.ls/tree/v1.61.0), commit [`08eafe6ff60c7159ef88571d47b6a9ef82fef94e`](https://github.com/eclipse-jdtls/eclipse.jdt.ls/commit/08eafe6ff60c7159ef88571d47b6a9ef82fef94e); the extracted core bundle manifest was verified to contain the same commit |
| Extraction target | `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/tools/selected` |
| Server executable | `/private/tmp/linguan-source-analysis-process-design/backend-agents/sources/source-code/.workspace/jdtls-source-navigation-feasibility/tools/selected/bin/jdtls` |
| Expected launcher | `plugins/org.eclipse.equinox.launcher_1.8.0.v20260804-1928.jar`, as listed by the [official 1.61.0 p2 repository](https://download.eclipse.org/jdtls/milestones/1.61.0/repository/plugins/) |
| Launch JDK | Oracle JDK `26.0.1+8-34`, `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home`, executable `/Library/Java/JavaVirtualMachines/jdk-26.jdk/Contents/Home/bin/java` |
| Wrapper interpreter | `/usr/bin/python3` `3.9.6`; ensure `/usr/bin` precedes `/usr/local/bin` because the latter currently selects unsupported Python `3.7.3` |
| Java LSP client | Eclipse LSP4J `1.0.0`: `org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0` and transitive `org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:1.0.0` |
| Syntax-only AST | `com.github.javaparser:javaparser-core:3.28.2`; no Symbol Solver in this harness |
| JSON | Jackson BOM/databind/core `2.21.4` with the BOM's `jackson-annotations:2.21` |
| LSP4J JSON codec | Explicitly pin `com.google.code.gson:gson:2.14.0`; do not inherit LSP4J's published `[2.9.1,3.0)` range |

### Why these versions are compatible

The tagged JDT LS README states that Java 21 is the minimum runtime. The tagged official wrapper implements that contract by rejecting only a major version below 21 and adds the JDK XML-limit arguments for Java 24 and newer; therefore the installed JDK 26 is suitable as the **server runtime** and JDK 17 is not. This does not claim Java 26 source-language support for the analyzed repository. See the [`v1.61.0` README](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/v1.61.0/README.md) and [`jdtls.py`](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/v1.61.0/org.eclipse.jdt.ls.product/scripts/jdtls.py).

JDT LS `v1.61.0` selects the official LSP4J `1.0.0` update site in its [target definition](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/v1.61.0/org.eclipse.jdt.ls.target/org.eclipse.jdt.ls.tp.target). The released server repository contains `org.eclipse.lsp4j_1.0.0.v20260209-1721.jar`, `org.eclipse.lsp4j.jsonrpc_1.0.0.v20260209-1721.jar`, and `com.google.gson_2.14.0.jar`. The independent Java client therefore uses Maven LSP4J `1.0.0` and pins Gson `2.14.0`; the [published LSP4J POM](https://repo.maven.apache.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j/1.0.0/org.eclipse.lsp4j-1.0.0.pom) supplies JSON-RPC `1.0.0`, while the [JSON-RPC POM](https://repo.maven.apache.org/maven2/org/eclipse/lsp4j/org.eclipse.lsp4j.jsonrpc/1.0.0/org.eclipse.lsp4j.jsonrpc-1.0.0.pom) otherwise leaves Gson as an open range.

### Offline initialization boundary

The later harness must pass the approved nested initialization settings before import/index starts and repeat the same settings in `workspace/didChangeConfiguration`. The fixed settings disable Maven and Gradle import, Gradle annotation processing, autobuild, source downloads, and build-configuration updates; they set `java.project.sourcePaths=["src"]` and `java.project.referencedLibraries=[]`. These keys are defined by the tagged [JDT LS `Preferences.java`](https://github.com/eclipse-jdtls/eclipse.jdt.ls/blob/v1.61.0/org.eclipse.jdt.ls.core/src/org/eclipse/jdt/ls/core/internal/preferences/Preferences.java). Buildship schedules its published-Gradle-versions job when its bundle activates, before those project-import preferences can suppress it. The harness therefore gives the child an isolated `XDG_CACHE_HOME` and seeds `tooling/gradle/versions.json` byte-for-byte from the selected JDT LS core bundle's embedded `gradle/checksums/versions.json`; this keeps the pinned distribution self-contained without customer build execution or a network fetch. The settings and cache are defense in depth: the trial must still enforce no network and stop on any attempted Maven, Gradle, annotation-processor, customer script, test, plugin, or application execution.

The wrapper runs with `JAVA_HOME` fixed to the selected JDK and `/usr/bin/python3` selected by its `/usr/bin/env python3` shebang. The installed archive, extraction, single expected launcher, and embedded release identity were verified before the offline trial. Do not use GitHub `releases/latest`, a snapshot, `LATEST`, `RELEASE`, a version range, a customer POM, or the product reactor.

### Measured Tasks 2–5 result

The isolated harness materialized a byte-verified projection of all 273 selected main-Java sources at frozen commit `8c30ce7861570458920175e200bb2a6442713580`, and the direct test contract is GREEN (14 tests, 0 failures, 0 errors). The retained Task 3 run reached `ServiceReady` but recorded Buildship's Gradle-version metadata attempt and sandbox `UnknownHostException`; that complete runtime and result set is preserved under `failed-runs/task3-buildship-network-attempt-20260912T095556-0230/`. After the single runtime correction above, exactly one fresh combined registration-to-financial session reached `ServiceReady` without the forbidden Buildship network attempt.

The corrected run still has status **FAIL**. Its registration packet SHA-256 is `3c11aa2030be491b31025181def23ff097c291836f812e7c309c56247ed02424`, contains zero methods and calls, and fails the primary checker with `FULL_BODY_NOT_FOUND:jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java:357:367`; financial is therefore `NOT_ENTERED`. Under the prescribed empty client capabilities, the pinned server returns flat `SymbolInformation` whose `location.range` is a Java-element name range rather than the full method range required by the walker. Classify this measured result as `JDT_NAVIGATION_CAPABILITY` with detail `DOCUMENT_SYMBOL_FULL_RANGE_UNAVAILABLE_UNDER_PRESCRIBED_EMPTY_CAPABILITIES`. It does not reach the declaration/definition binding check and is not evidence about binding correctness.

### Local offline cache preflight

The existing Maven cache was read without resolution or network. Computed SHA-1 values match each local `.sha1` sidecar; the SHA-256 values below identify the bytes used by the final independent harness.

| Artifact | Cache state | Local SHA-256 |
| --- | --- | --- |
| `com.github.javaparser:javaparser-core:3.28.2` | present, SHA-1 sidecar match | `b5499a3b1c40b16c0671fabe478c9aafeab38160c6fde74a6c13f42d86716ecd` |
| `com.fasterxml.jackson.core:jackson-databind:2.21.4` | present, SHA-1 sidecar match | `3888e9e69ab66fbacaacc9aea0e9ffbf15368288e4aca468b024dba11c09fbf9` |
| `com.fasterxml.jackson.core:jackson-core:2.21.4` | present, SHA-1 sidecar match | `4b40a06396f239f8de2da57419adde6e94e5edc18a2171d471ea05eeed4e5c2d` |
| `com.fasterxml.jackson.core:jackson-annotations:2.21` | present, SHA-1 sidecar match | `53ca085f4a150f703f49e1aabd935bd03b43e1ea3d55d135438292af22cef56b` |
| `com.google.code.gson:gson:2.14.0` | present, SHA-1 sidecar match | `2cbd119bf1961c28788310963dc80ba65f58cdeec1dd139c8bdb1240faa2c36f` |
| `org.eclipse.lsp4j:org.eclipse.lsp4j:1.0.0` | present after the authorized fixed resolution | `ccd78893facc6bfcc359d56cba05d3d5b85eb41e4c40d4b4215ca45db5f416d9` |
| `org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:1.0.0` | present after the authorized fixed resolution | `9647feb0524bf763c878e12ab878a684102b81cccb3f77feecbec709d54f9bbb` |

The ignored `.workspace/jdtls-source-navigation-feasibility/tool-manifest.json` retains most machine-readable tool identity. Its `lastTrial.forbiddenActivity` and client `cacheState` were not refreshed consistently after the corrected run, so those two fields are not current evidence; the final report records the conflict and uses the active packet, verdict, diagnostics, request journal, and runtime log for the measured outcome.
