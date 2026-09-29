package org.sourceanalysis.app.analysis.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryKind;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.HttpMethodCondition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Direct behavior RED contract for Step05 ownership, denominator, and closure projection. */
class EntryEvidenceAssemblerBehaviorRedContractTest {

  private static final String SNAPSHOT = "snapshot:" + "a".repeat(64);
  private static final String SOURCE_HASH = "b".repeat(64);
  private static final AnalysisRunId RUN = AnalysisRunId.parse("analysis-run:" + "c".repeat(64));
  private static final String PORTABLE_SOURCE_PATH =
      "jshERP-boot/src/main/java/fixture/DepotItemVo4WithInfoEx.java";
  private static final String HOST_WORKSPACE_IDENTITY =
      "file:///private/var/folders/xy/source-analysis-jdt-workspace-123/projects/"
          + "source-analysis-9f6e4z/"
          + PORTABLE_SOURCE_PATH
          + "#96:18";
  private static final String SECOND_HOST_WORKSPACE_IDENTITY =
      "file:///private/var/folders/zz/source-analysis-jdt-workspace-456/projects/other-project/"
          + PORTABLE_SOURCE_PATH
          + "#96:18";
  private static final String PORTABLE_SOURCE_IDENTITY = PORTABLE_SOURCE_PATH + "#96:18";

  @Test
  void rejectsFrontendCoverageWhenIncludedEntryIsNotAmongRequestCandidates() {
    FrontendHttpRequestRecord request =
        request(
            "request:invalid-inclusion", "/orders/invalid", "web/Invalid.vue", SOURCE_HASH, "GET");

    assertThatThrownBy(
            () ->
                new EntryEvidenceSet.FrontendCoverage(
                    request.requestId(),
                    request,
                    FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE,
                    List.of("entry:" + "1".repeat(64)),
                    List.of("entry:" + "2".repeat(64)),
                    List.of(unit("unit:invalid", "web/Invalid.vue", SOURCE_HASH)),
                    "fixture must reject an included non-candidate entry"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("entry-evidence included frontend entries must be request candidates");
  }

  @Test
  void keepsCandidatesUnresolvedWhenConfiguredMappingDoesNotApplyToDynamicBaseUrl() {
    HttpEntryPoint first = entry("entry:" + "1".repeat(64), "/orders/first", "method:first");
    ProgramGraphsReference navigation =
        new ProgramGraphsReference(step(AnalysisStepKey.PROGRAM_GRAPHS, 'd'));
    VerifiedSourceInventoryReference source =
        new VerifiedSourceInventoryReference(step(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'e'));
    ApplicationDiscoveryReference discovery =
        new ApplicationDiscoveryReference(step(AnalysisStepKey.APPLICATION_DISCOVERY, 'f'));
    AnalysisStepPublicationReference persistencePublication =
        step(AnalysisStepKey.PROVEN_CODE_FACTS, '0');
    ModulePublicationReference frontendPublication = module(6, "frontend-http-discovery", '1');
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.AVAILABLE,
            null,
            List.of("graph:shared"),
            List.of("fact:shared"),
            null);
    FrontendHttpRequestRecord request =
        requestWithBaseFallback(
            "request:dynamic-base",
            "/orders/first",
            "web/First.vue",
            SOURCE_HASH,
            "GET",
            "window._CONFIG.apiBase",
            "https://api.example.test");
    FrontendHttpIndex frontendIndex =
        new FrontendHttpIndex(
            List.of(),
            List.of(request),
            List.of(),
            List.of(),
            FrontendHttpIndex.Status.ENABLED,
            List.of(),
            List.of());
    FrontendSourceUnits sourceUnits =
        new FrontendSourceUnits(
            source, frontendPublication, List.of(unit("unit:first", "web/First.vue", SOURCE_HASH)));
    JavaCodeIndex javaIndex =
        new JavaCodeIndex(
            new EngineDescriptor("jdt", "test", Map.of("jdt", "test"), "17", List.of()),
            SNAPSHOT,
            new ArtifactReference(ArtifactId.parse("snapshot:" + "a".repeat(64)), digest('a')),
            new JavaDeclarationCatalog(
                SNAPSHOT, List.of(), List.of(), List.of(), List.of(), List.of(), Map.of()),
            List.of(
                JavaCodeIndex.EntryCollection.collected(
                    new EntrySeed(
                        first.entryId().value(), first.methodKey(), first.methodRange(), "HTTP"),
                    context(first, "src/main/java/First.java", enhancements))),
            enhancements);
    EntryEvidenceHttpMapping unrelatedMapping =
        new EntryEvidenceHttpMapping(
            "client:other",
            "https://other.example.test",
            "/other",
            "backend:other",
            null,
            null,
            null,
            "fixture mapping that is not applicable to this request");

    EntryEvidenceSet assembled =
        new EntryEvidenceAssembler()
            .assemble(
                new EntryEvidenceRequest(
                    source,
                    discovery,
                    navigation,
                    persistencePublication,
                    frontendPublication,
                    List.of(first),
                    javaIndex,
                    persistenceIndex(navigation),
                    frontendIndex,
                    sourceUnits,
                    List.of(unrelatedMapping),
                    new EntryEvidenceProfile(1_000_000, 4_000_000, 1)));

    assertThat(assembled.frontendCoverage())
        .singleElement()
        .satisfies(
            coverage -> {
              assertThat(coverage.resolution())
                  .isEqualTo(FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST);
              assertThat(coverage.entryIds()).containsExactly(first.entryId().value());
              assertThat(coverage.includedEntryIds()).isEmpty();
              assertThat(coverage.reason()).isNotBlank();
            });
    assertThat(assembled.entries().get(0).frontend().requestUses()).isEmpty();
    assertThat(assembled.entries().get(0).frontend().candidateRequestUses())
        .singleElement()
        .satisfies(
            use -> {
              assertThat(use.request().requestId()).isEqualTo("request:dynamic-base");
              assertThat(use.resolution())
                  .isEqualTo(FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST);
              assertThat(use.candidateEntryIds()).containsExactly(first.entryId().value());
              assertThat(use.reason()).isNotBlank();
            });
  }

  @Test
  void doesNotGuessOrLeakAnUncataloguedWorkspaceUri() {
    HttpEntryPoint first = entry("entry:" + "1".repeat(64), "/orders/first", "method:first");
    ProgramGraphsReference navigation =
        new ProgramGraphsReference(step(AnalysisStepKey.PROGRAM_GRAPHS, 'd'));
    VerifiedSourceInventoryReference source =
        new VerifiedSourceInventoryReference(step(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'e'));
    ApplicationDiscoveryReference discovery =
        new ApplicationDiscoveryReference(step(AnalysisStepKey.APPLICATION_DISCOVERY, 'f'));
    AnalysisStepPublicationReference persistencePublication =
        step(AnalysisStepKey.PROVEN_CODE_FACTS, '0');
    ModulePublicationReference frontendPublication = module(6, "frontend-http-discovery", '1');
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.AVAILABLE,
            null,
            List.of("graph:shared"),
            List.of("fact:shared"),
            null);
    FrontendHttpRequestRecord matched =
        request("request:first", "/orders/first", "web/First.vue", SOURCE_HASH, "GET");
    FrontendHttpIndex frontendIndex =
        new FrontendHttpIndex(
            List.of(),
            List.of(matched),
            List.of(),
            List.of(),
            FrontendHttpIndex.Status.ENABLED,
            List.of(),
            List.of());
    FrontendSourceUnits sourceUnits =
        new FrontendSourceUnits(
            source, frontendPublication, List.of(unit("unit:first", "web/First.vue", SOURCE_HASH)));

    for (List<String> catalogFiles :
        List.of(
            List.of(PORTABLE_SOURCE_PATH + ".missing"),
            List.of(PORTABLE_SOURCE_PATH, PORTABLE_SOURCE_PATH))) {
      EntryCodeContext context =
          contextWithUnconfirmedWorkspaceIdentity(first, "src/main/java/First.java", enhancements);
      JavaCodeIndex javaIndex =
          new JavaCodeIndex(
              new EngineDescriptor("jdt", "test", Map.of("jdt", "test"), "17", List.of()),
              SNAPSHOT,
              new ArtifactReference(ArtifactId.parse("snapshot:" + "a".repeat(64)), digest('a')),
              new JavaDeclarationCatalog(
                  SNAPSHOT, catalogFiles, List.of(), List.of(), List.of(), List.of(), Map.of()),
              List.of(
                  JavaCodeIndex.EntryCollection.collected(
                      new EntrySeed(
                          first.entryId().value(), first.methodKey(), first.methodRange(), "HTTP"),
                      context)),
              enhancements);

      EntryEvidenceSet.Entry evidence =
          new EntryEvidenceAssembler()
              .assemble(
                  new EntryEvidenceRequest(
                      source,
                      discovery,
                      navigation,
                      persistencePublication,
                      frontendPublication,
                      List.of(first),
                      javaIndex,
                      persistenceIndex(navigation),
                      frontendIndex,
                      sourceUnits,
                      List.of(),
                      new EntryEvidenceProfile(1_000_000, 4_000_000, 1)))
              .entries()
              .get(0);

      assertThat(evidence.java().calls()).hasSize(2);
      EntryCodeContext.CallSite unresolved = evidence.java().calls().get(0);
      assertThat(unresolved.callKey()).isEqualTo("call:method:first");
      assertThat(unresolved.resolution()).isEqualTo("NAVIGATION_CONFLICT");
      assertThat(unresolved.targets()).hasSize(1);
      assertThat(unresolved.targets().get(0).methodKey()).isNull();
      assertThat(unresolved.targets().get(0).displayName())
          .doesNotContain("file:///private/var/", PORTABLE_SOURCE_PATH);
      assertThat(unresolved.observations())
          .singleElement()
          .satisfies(
              observation -> {
                assertThat(observation.association()).isEqualTo("UNCONFIRMED");
                assertThat(observation.displayIdentity())
                    .doesNotContain("file:///private/var/", PORTABLE_SOURCE_PATH);
              });
      assertThat(evidence.java().observations()).hasSize(2);
      assertThat(evidence.limitations())
          .anySatisfy(
              limitation -> {
                assertThat(limitation.code()).isEqualTo("NAVIGATION_CONFLICT");
                assertThat(limitation.detail())
                    .doesNotContain("file:///private/var/", PORTABLE_SOURCE_PATH);
              });
      assertThat(evidence.java().calls().get(1).callKey()).isEqualTo("call:method:first-external");
      assertThat(evidence.java().calls().get(1).resolution()).isEqualTo("EXTERNAL");
    }
  }

  @Test
  void preservesDistinctPortableObservationsWhenWorkspaceUrisCollapseToUnconfirmed() {
    HttpEntryPoint first = entry("entry:" + "1".repeat(64), "/orders/first", "method:first");
    ProgramGraphsReference navigation =
        new ProgramGraphsReference(step(AnalysisStepKey.PROGRAM_GRAPHS, 'd'));
    VerifiedSourceInventoryReference source =
        new VerifiedSourceInventoryReference(step(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'e'));
    ApplicationDiscoveryReference discovery =
        new ApplicationDiscoveryReference(step(AnalysisStepKey.APPLICATION_DISCOVERY, 'f'));
    AnalysisStepPublicationReference persistencePublication =
        step(AnalysisStepKey.PROVEN_CODE_FACTS, '0');
    ModulePublicationReference frontendPublication = module(6, "frontend-http-discovery", '1');
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.AVAILABLE,
            null,
            List.of("graph:shared"),
            List.of("fact:shared"),
            null);
    FrontendHttpRequestRecord matched =
        request("request:first", "/orders/first", "web/First.vue", SOURCE_HASH, "GET");
    FrontendHttpIndex frontendIndex =
        new FrontendHttpIndex(
            List.of(),
            List.of(matched),
            List.of(),
            List.of(),
            FrontendHttpIndex.Status.ENABLED,
            List.of(),
            List.of());
    FrontendSourceUnits sourceUnits =
        new FrontendSourceUnits(
            source, frontendPublication, List.of(unit("unit:first", "web/First.vue", SOURCE_HASH)));
    EntryCodeContext context =
        contextWithTwoUnconfirmedWorkspaceIdentities(
            first, "src/main/java/First.java", enhancements);
    JavaCodeIndex javaIndex =
        new JavaCodeIndex(
            new EngineDescriptor("jdt", "test", Map.of("jdt", "test"), "17", List.of()),
            SNAPSHOT,
            new ArtifactReference(ArtifactId.parse("snapshot:" + "a".repeat(64)), digest('a')),
            new JavaDeclarationCatalog(
                SNAPSHOT,
                List.of(PORTABLE_SOURCE_PATH + ".missing"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()),
            List.of(
                JavaCodeIndex.EntryCollection.collected(
                    new EntrySeed(
                        first.entryId().value(), first.methodKey(), first.methodRange(), "HTTP"),
                    context)),
            enhancements);

    EntryEvidenceSet.Entry evidence =
        new EntryEvidenceAssembler()
            .assemble(
                new EntryEvidenceRequest(
                    source,
                    discovery,
                    navigation,
                    persistencePublication,
                    frontendPublication,
                    List.of(first),
                    javaIndex,
                    persistenceIndex(navigation),
                    frontendIndex,
                    sourceUnits,
                    List.of(),
                    new EntryEvidenceProfile(1_000_000, 4_000_000, 1)))
            .entries()
            .get(0);

    EntryCodeContext.CallSite call = evidence.java().calls().get(0);
    assertThat(evidence.java().calls()).hasSize(2);
    assertThat(call.callKey()).isEqualTo("call:method:first");
    assertThat(call.callerMethodKey()).isEqualTo("method:first");
    assertThat(call.resolution()).isEqualTo("NAVIGATION_CONFLICT");
    assertThat(call.observations()).hasSize(2);
    List<String> portableIdentities =
        call.observations().stream()
            .map(EntryCodeContext.CallObservation::displayIdentity)
            .toList();
    assertThat(portableIdentities)
        .doesNotHaveDuplicates()
        .allSatisfy(
            identity ->
                assertThat(identity).doesNotContain("file:///private/var/", PORTABLE_SOURCE_PATH));
    assertThat(evidence.java().observations()).hasSize(3);
    assertThat(evidence.limitations())
        .anySatisfy(limitation -> assertThat(limitation.code()).isEqualTo("NAVIGATION_CONFLICT"));
  }

  @Test
  void preservesDistinctObservationsWhenRelativeAndWorkspaceIdentitiesCollide() {
    HttpEntryPoint first = entry("entry:" + "1".repeat(64), "/orders/first", "method:first");
    ProgramGraphsReference navigation =
        new ProgramGraphsReference(step(AnalysisStepKey.PROGRAM_GRAPHS, 'd'));
    VerifiedSourceInventoryReference source =
        new VerifiedSourceInventoryReference(step(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'e'));
    ApplicationDiscoveryReference discovery =
        new ApplicationDiscoveryReference(step(AnalysisStepKey.APPLICATION_DISCOVERY, 'f'));
    AnalysisStepPublicationReference persistencePublication =
        step(AnalysisStepKey.PROVEN_CODE_FACTS, '0');
    ModulePublicationReference frontendPublication = module(6, "frontend-http-discovery", '1');
    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.AVAILABLE,
            null,
            List.of("graph:shared"),
            List.of("fact:shared"),
            null);
    FrontendHttpRequestRecord matched =
        request("request:first", "/orders/first", "web/First.vue", SOURCE_HASH, "GET");
    FrontendHttpIndex frontendIndex =
        new FrontendHttpIndex(
            List.of(),
            List.of(matched),
            List.of(),
            List.of(),
            FrontendHttpIndex.Status.ENABLED,
            List.of(),
            List.of());
    FrontendSourceUnits sourceUnits =
        new FrontendSourceUnits(
            source, frontendPublication, List.of(unit("unit:first", "web/First.vue", SOURCE_HASH)));
    EntryCodeContext context =
        contextWithRelativeAndWorkspaceIdentities(first, "src/main/java/First.java", enhancements);
    JavaCodeIndex javaIndex =
        new JavaCodeIndex(
            new EngineDescriptor("jdt", "test", Map.of("jdt", "test"), "17", List.of()),
            SNAPSHOT,
            new ArtifactReference(ArtifactId.parse("snapshot:" + "a".repeat(64)), digest('a')),
            new JavaDeclarationCatalog(
                SNAPSHOT,
                List.of(PORTABLE_SOURCE_PATH),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()),
            List.of(
                JavaCodeIndex.EntryCollection.collected(
                    new EntrySeed(
                        first.entryId().value(), first.methodKey(), first.methodRange(), "HTTP"),
                    context)),
            enhancements);

    EntryEvidenceSet.Entry evidence =
        new EntryEvidenceAssembler()
            .assemble(
                new EntryEvidenceRequest(
                    source,
                    discovery,
                    navigation,
                    persistencePublication,
                    frontendPublication,
                    List.of(first),
                    javaIndex,
                    persistenceIndex(navigation),
                    frontendIndex,
                    sourceUnits,
                    List.of(),
                    new EntryEvidenceProfile(1_000_000, 4_000_000, 1)))
            .entries()
            .get(0);

    assertThat(evidence.java().calls()).hasSize(2);
    EntryCodeContext.CallSite call = evidence.java().calls().get(0);
    assertThat(call.callKey()).isEqualTo("call:method:first");
    assertThat(call.callerMethodKey()).isEqualTo("method:first");
    assertThat(call.resolution()).isEqualTo("NAVIGATION_CONFLICT");
    assertThat(call.observations()).hasSize(2);
    List<String> portableIdentities =
        call.observations().stream()
            .map(EntryCodeContext.CallObservation::displayIdentity)
            .toList();
    assertThat(portableIdentities).hasSize(2).doesNotHaveDuplicates();
    assertThat(portableIdentities.get(0)).isEqualTo(PORTABLE_SOURCE_IDENTITY);
    assertThat(portableIdentities.get(1))
        .isNotEqualTo(PORTABLE_SOURCE_IDENTITY)
        .doesNotContain("file:///private/var/", PORTABLE_SOURCE_PATH);
    assertThat(evidence.java().observations()).hasSize(3);
    assertThat(evidence.java().calls().get(1).resolution()).isEqualTo("EXTERNAL");
  }

  @Test
  void keepsEntryOwnedJavaAndPersistenceClosureAndUnmatchedFrontendCoverage() {
    HttpEntryPoint first = entry("entry:" + "1".repeat(64), "/orders/first", "method:first");
    HttpEntryPoint second =
        unrestrictedEntry("entry:" + "2".repeat(64), "/orders/second", "method:second");
    ProgramGraphsReference navigation =
        new ProgramGraphsReference(step(AnalysisStepKey.PROGRAM_GRAPHS, 'd'));
    VerifiedSourceInventoryReference source =
        new VerifiedSourceInventoryReference(step(AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 'e'));
    ApplicationDiscoveryReference discovery =
        new ApplicationDiscoveryReference(step(AnalysisStepKey.APPLICATION_DISCOVERY, 'f'));
    AnalysisStepPublicationReference persistencePublication =
        step(AnalysisStepKey.PROVEN_CODE_FACTS, '0');
    ModulePublicationReference frontendPublication = module(6, "frontend-http-discovery", '1');

    EntryCodeContext.TechnicalEnhancements enhancements =
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.AVAILABLE,
            null,
            List.of("graph:shared"),
            List.of("fact:shared"),
            null);
    EntryCodeContext firstContext =
        contextWithWorkspaceIdentity(first, "src/main/java/First.java", enhancements);
    EntryCodeContext secondContext = context(second, "src/main/java/Second.java", enhancements);
    JavaCodeIndex javaIndex =
        new JavaCodeIndex(
            new EngineDescriptor("jdt", "test", Map.of("jdt", "test"), "17", List.of()),
            SNAPSHOT,
            new ArtifactReference(ArtifactId.parse("snapshot:" + "a".repeat(64)), digest('a')),
            new JavaDeclarationCatalog(
                SNAPSHOT,
                List.of(PORTABLE_SOURCE_PATH),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                Map.of()),
            List.of(
                JavaCodeIndex.EntryCollection.collected(
                    new EntrySeed(
                        first.entryId().value(), first.methodKey(), first.methodRange(), "HTTP"),
                    firstContext),
                JavaCodeIndex.EntryCollection.collected(
                    new EntrySeed(
                        second.entryId().value(), second.methodKey(), second.methodRange(), "HTTP"),
                    secondContext)),
            enhancements);

    PersistenceMaterialIndex persistence = persistenceIndex(navigation);
    FrontendHttpRequestRecord matched =
        request("request:first", "/orders/first", "web/First.vue", SOURCE_HASH, "GET");
    FrontendHttpRequestRecord unrestrictedMatch =
        request("request:second", "/orders/second", "web/Second.vue", "d".repeat(64), "POST");
    FrontendHttpRequestRecord unmatched =
        request("request:missing", "/orders/missing", "web/Missing.vue", "e".repeat(64), "GET");
    FrontendHttpRequestRecord unknownBase =
        requestWithBaseFallback(
            "request:unknown-base",
            "/orders/second",
            "web/Unknown.vue",
            "f".repeat(64),
            "POST",
            "window._CONFIG.apiBase",
            "https://api.example.test");
    FrontendHttpIndex frontendIndex =
        new FrontendHttpIndex(
            List.of(),
            List.of(matched, unrestrictedMatch, unmatched, unknownBase),
            List.of(),
            List.of(),
            FrontendHttpIndex.Status.ENABLED,
            List.of(),
            List.of());
    FrontendSourceUnits sourceUnits =
        new FrontendSourceUnits(
            source,
            frontendPublication,
            List.of(
                unit("unit:first", "web/First.vue", SOURCE_HASH),
                unit("unit:second", "web/Second.vue", "d".repeat(64)),
                unit("unit:missing", "web/Missing.vue", "e".repeat(64)),
                unit("unit:unknown-base", "web/Unknown.vue", "f".repeat(64))));

    EntryEvidenceSet assembled =
        new EntryEvidenceAssembler()
            .assemble(
                new EntryEvidenceRequest(
                    source,
                    discovery,
                    navigation,
                    persistencePublication,
                    frontendPublication,
                    List.of(first, second),
                    javaIndex,
                    persistence,
                    frontendIndex,
                    sourceUnits,
                    List.of(),
                    new EntryEvidenceProfile(1_000_000, 4_000_000, 8)));

    assertThat(assembled.entries())
        .extracting(EntryEvidenceSet.Entry::entryId)
        .containsExactly(first.entryId().value(), second.entryId().value());
    EntryEvidenceSet.Entry firstEvidence = assembled.entries().get(0);
    assertThat(firstEvidence.java().methods())
        .extracting(EntryCodeContext.MethodCode::methodKey)
        .contains("method:first", "method:shared", "method:mapper:first");
    assertThat(firstEvidence.java().calls())
        .extracting(EntryCodeContext.CallSite::callerMethodKey)
        .containsOnly("method:first");
    assertThat(firstEvidence.java().observations()).hasSize(2);
    EntryEvidenceSet.CallObservation externalObservation =
        firstEvidence.java().observations().stream()
            .filter(observation -> "EXTERNAL_BINARY_BINDING".equals(observation.code()))
            .findFirst()
            .orElseThrow();
    assertThat(externalObservation.observationId()).isNotBlank();
    assertThat(externalObservation.callKey()).isEqualTo("call:method:first-external");
    assertThat(externalObservation.detail())
        .contains("JDT Core resolved a non-recovered binary method binding");
    EntryEvidenceSet.CallObservation unconfirmedObservation =
        firstEvidence.java().observations().stream()
            .filter(observation -> "UNCONFIRMED_BINDING_LOCATION".equals(observation.code()))
            .findFirst()
            .orElseThrow();
    assertThat(unconfirmedObservation.callKey()).isEqualTo("call:method:first");
    assertThat(unconfirmedObservation.detail()).isEqualTo("BINDING_DECLARATION_MISMATCH");
    assertThat(firstEvidence.java().calls()).hasSize(2);
    assertThat(firstEvidence.java().calls().get(0).targets())
        .singleElement()
        .satisfies(
            target -> {
              assertThat(target.methodKey()).isEqualTo("method:shared");
              assertThat(target.displayName()).isEqualTo(PORTABLE_SOURCE_IDENTITY);
            });
    assertThat(firstEvidence.java().calls().get(0).resolution()).isEqualTo("LOCATED");
    assertThat(firstEvidence.java().calls().get(0).observations())
        .singleElement()
        .satisfies(
            observation -> {
              assertThat(observation.displayIdentity()).isEqualTo(PORTABLE_SOURCE_IDENTITY);
              assertThat(observation.detail())
                  .contains(PORTABLE_SOURCE_IDENTITY)
                  .doesNotContain("file:///private/var/", "source-analysis-jdt-workspace-123");
            });
    assertThat(firstEvidence.limitations())
        .singleElement()
        .satisfies(
            limitation -> {
              assertThat(limitation.code()).isEqualTo("NAVIGATION_CONFLICT");
              assertThat(limitation.detail())
                  .contains(PORTABLE_SOURCE_IDENTITY)
                  .doesNotContain("file:///private/var/", "source-analysis-jdt-workspace-123");
            });
    assertThat(firstEvidence.java().supportingSources()).hasSize(1);
    assertThat(firstEvidence.java().technicalEnhancements()).isEqualTo(enhancements);
    assertThat(firstEvidence.persistence().bindings())
        .extracting(PersistenceMaterialIndex.JavaBinding::methodKey)
        .containsExactly("method:mapper:first");
    assertThat(firstEvidence.persistence().resources())
        .extracting(PersistenceMaterialIndex.Resource::resourcePath)
        .containsExactly(
            "src/main/resources/mapper/Common.xml", "src/main/resources/mapper/Orders.xml");
    assertThat(firstEvidence.persistence().sqlAnalyses())
        .extracting(PersistenceMaterialIndex.SqlAnalysis::statementRef)
        .containsExactly("statement:first");
    assertThat(assembled.entries().get(1).java().calls())
        .extracting(EntryCodeContext.CallSite::callerMethodKey)
        .containsOnly("method:second");
    assertThat(assembled.frontendCoverage())
        .extracting(EntryEvidenceSet.FrontendCoverage::requestId)
        .containsExactly(
            "request:first", "request:missing", "request:second", "request:unknown-base");
    assertThat(assembled.frontendCoverage().get(1).resolution())
        .isEqualTo(FrontendEntryLinkRecord.Resolution.NO_MATCH);
    assertThat(assembled.frontendCoverage().get(1).units())
        .extracting(FrontendSourceUnits.Unit::sourceUnitId)
        .containsExactly("unit:missing");
    assertThat(
            assembled.entries().get(1).frontend().requestUses().stream()
                .map(use -> use.request().requestId())
                .toList())
        .containsExactly("request:second");
    assertThat(assembled.frontendCoverage().get(2).resolution())
        .isEqualTo(FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE);
    assertThat(assembled.frontendCoverage().get(3).resolution())
        .isEqualTo(FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST);

    assertThatThrownBy(
            () ->
                new EntryEvidenceAssembler()
                    .assemble(
                        new EntryEvidenceRequest(
                            source,
                            discovery,
                            navigation,
                            persistencePublication,
                            frontendPublication,
                            List.of(first, second),
                            javaIndex,
                            persistence,
                            frontendIndex,
                            sourceUnits,
                            List.of(),
                            new EntryEvidenceProfile(1_000_000, 4_000_000, 1))))
        .isInstanceOf(EntryEvidenceAssembler.EntryLimitExceededException.class)
        .hasMessage("ENTRY_EVIDENCE_ENTRY_COUNT_LIMIT_EXCEEDED");
  }

  private static EntryCodeContext context(
      HttpEntryPoint entry, String path, EntryCodeContext.TechnicalEnhancements enhancements) {
    String methodKey = entry.methodKey();
    EntryCodeContext.MethodCode handler =
        new EntryCodeContext.MethodCode(
            methodKey,
            "METHOD",
            entry.handlerFqn(),
            entry.methodKey().substring(entry.methodKey().indexOf(':') + 1),
            List.of(),
            "void",
            new EntryCodeContext.SourceSource(
                path, entry.methodRange(), "void handler() { shared(); }"),
            true);
    EntryCodeContext.MethodCode shared =
        new EntryCodeContext.MethodCode(
            "method:shared",
            "METHOD",
            "fixture.Shared",
            "shared",
            List.of(),
            "void",
            new EntryCodeContext.SourceSource(
                "src/main/java/Shared.java", range(30, 20), "void shared() { }"),
            true);
    EntryCodeContext.MethodCode mapper =
        new EntryCodeContext.MethodCode(
            "method:mapper:first",
            "METHOD",
            "fixture.OrderMapper",
            "first",
            List.of(),
            "void",
            new EntryCodeContext.SourceSource(
                "src/main/java/OrderMapper.java", range(80, 20), "void first() { }"),
            true);
    EntryCodeContext.CallSite call =
        new EntryCodeContext.CallSite(
            "call:" + methodKey,
            methodKey,
            "METHOD",
            range(20, 8),
            range(20, 8),
            "shared()",
            null,
            List.of(),
            List.of(),
            false,
            List.of(
                new EntryCodeContext.CallTarget(
                    "method:shared",
                    List.of("DECLARATION"),
                    "shared",
                    List.of("ENGINE_BINDING"),
                    "BODY_INCLUDED",
                    null,
                    List.of())),
            "LOCATED",
            null);
    EntryCodeContext.SupportingSource supporting =
        new EntryCodeContext.SupportingSource(
            "CONFIG",
            new EntryCodeContext.SourceSource(path, range(60, 10), "shared-config"),
            List.of(methodKey),
            "saved fixture support");
    List<EntryCodeContext.CallSite> calls = List.of(call);
    if ("method:first".equals(methodKey)) {
      SourceRange externalRange = range(40, 16);
      EntryCodeContext.CallObservation externalObservation =
          new EntryCodeContext.CallObservation(
              "EXTERNAL_BINARY_BINDING",
              "JDT_CORE_BINDING",
              "BINARY",
              externalRange,
              "CONFIRMED",
              "java.base/java.lang.String.valueOf(java.lang.String)",
              "java.lang.String",
              "BINARY",
              "java.lang.String.valueOf",
              "JDT Core resolved a non-recovered binary method binding outside R0");
      calls =
          List.of(
              call,
              new EntryCodeContext.CallSite(
                  "call:method:first-external",
                  methodKey,
                  "METHOD",
                  externalRange,
                  externalRange,
                  "String.valueOf(id)",
                  "String",
                  List.of(new EntryCodeContext.ActualArgument(0, "id")),
                  List.of(),
                  false,
                  List.of(),
                  "EXTERNAL",
                  "JDT Core resolved a binary declaration outside R0",
                  List.of(externalObservation)));
    }
    return new EntryCodeContext(
        EntryCodeContext.SCHEMA_VERSION,
        entry.entryId().value(),
        methodKey,
        List.of(handler, shared, mapper),
        calls,
        List.of(supporting),
        List.of(),
        enhancements);
  }

  private static EntryCodeContext contextWithWorkspaceIdentity(
      HttpEntryPoint entry, String path, EntryCodeContext.TechnicalEnhancements enhancements) {
    EntryCodeContext base = context(entry, path, enhancements);
    EntryCodeContext.CallSite sharedCall = base.calls().get(0);
    EntryCodeContext.CallTarget sharedTarget = sharedCall.targets().get(0);
    EntryCodeContext.CallTarget hostPathTarget =
        new EntryCodeContext.CallTarget(
            sharedTarget.methodKey(),
            sharedTarget.roles(),
            HOST_WORKSPACE_IDENTITY,
            sharedTarget.navigationKinds(),
            sharedTarget.expansion(),
            sharedTarget.reason(),
            sharedTarget.argumentAssociations());
    EntryCodeContext.CallSite portableTargetCall =
        new EntryCodeContext.CallSite(
            sharedCall.callKey(),
            sharedCall.callerMethodKey(),
            sharedCall.kind(),
            sharedCall.site(),
            sharedCall.navigationSite(),
            sharedCall.expression(),
            sharedCall.receiverExpression(),
            sharedCall.actualArguments(),
            sharedCall.enclosingControlIndexes(),
            sharedCall.deferred(),
            List.of(hostPathTarget),
            sharedCall.resolution(),
            sharedCall.resolutionDetail(),
            sharedCall.observations());

    EntryCodeContext.CallObservation hostPathObservation =
        new EntryCodeContext.CallObservation(
            "UNCONFIRMED_BINDING_LOCATION",
            "JDT_DEFINITION",
            "SOURCE",
            sharedCall.site(),
            "UNCONFIRMED",
            null,
            null,
            "SOURCE",
            HOST_WORKSPACE_IDENTITY,
            "BINDING_DECLARATION_MISMATCH");
    EntryCodeContext.CallSite portableTargetCallWithObservation =
        new EntryCodeContext.CallSite(
            portableTargetCall.callKey(),
            portableTargetCall.callerMethodKey(),
            portableTargetCall.kind(),
            portableTargetCall.site(),
            portableTargetCall.navigationSite(),
            portableTargetCall.expression(),
            portableTargetCall.receiverExpression(),
            portableTargetCall.actualArguments(),
            portableTargetCall.enclosingControlIndexes(),
            portableTargetCall.deferred(),
            portableTargetCall.targets(),
            portableTargetCall.resolution(),
            portableTargetCall.resolutionDetail(),
            List.of(hostPathObservation));

    return new EntryCodeContext(
        base.schemaVersion(),
        base.entryId(),
        base.entryMethodKey(),
        base.methods(),
        List.of(portableTargetCallWithObservation, base.calls().get(1)),
        base.supportingSources(),
        List.of(
            new EntryCodeContext.Limitation(
                "NAVIGATION_CONFLICT",
                "JDT retained a workspace candidate at " + HOST_WORKSPACE_IDENTITY,
                List.of(entry.methodKey()),
                List.of(sharedCall.callKey()))),
        base.technicalEnhancements());
  }

  private static EntryCodeContext contextWithUnconfirmedWorkspaceIdentity(
      HttpEntryPoint entry, String path, EntryCodeContext.TechnicalEnhancements enhancements) {
    EntryCodeContext base = context(entry, path, enhancements);
    EntryCodeContext.CallSite sharedCall = base.calls().get(0);
    EntryCodeContext.CallTarget unknownTarget =
        new EntryCodeContext.CallTarget(
            null,
            List.of("DECLARATION"),
            HOST_WORKSPACE_IDENTITY,
            List.of("CALL_HIERARCHY"),
            "NOT_EXPANDED",
            "NAVIGATION_CONFLICT_NOT_EXPANDED",
            List.of());
    EntryCodeContext.CallObservation unknownObservation =
        new EntryCodeContext.CallObservation(
            "UNCONFIRMED_NAVIGATION_LOCATION",
            "CALL_HIERARCHY",
            "SOURCE",
            sharedCall.site(),
            "UNCONFIRMED",
            null,
            null,
            "SOURCE",
            HOST_WORKSPACE_IDENTITY,
            "NAVIGATION_CONFLICT_NOT_EXPANDED");
    EntryCodeContext.CallSite unconfirmedCall =
        new EntryCodeContext.CallSite(
            sharedCall.callKey(),
            sharedCall.callerMethodKey(),
            sharedCall.kind(),
            sharedCall.site(),
            sharedCall.navigationSite(),
            sharedCall.expression(),
            sharedCall.receiverExpression(),
            sharedCall.actualArguments(),
            sharedCall.enclosingControlIndexes(),
            sharedCall.deferred(),
            List.of(unknownTarget),
            "NAVIGATION_CONFLICT",
            "CALL_SITE_ASSOCIATION_UNCONFIRMED:shared",
            List.of(unknownObservation));
    return new EntryCodeContext(
        base.schemaVersion(),
        base.entryId(),
        base.entryMethodKey(),
        base.methods(),
        List.of(unconfirmedCall, base.calls().get(1)),
        base.supportingSources(),
        List.of(
            new EntryCodeContext.Limitation(
                "NAVIGATION_CONFLICT",
                "JDT retained an unconfirmed workspace candidate at " + HOST_WORKSPACE_IDENTITY,
                List.of(entry.methodKey()),
                List.of(sharedCall.callKey()))),
        base.technicalEnhancements());
  }

  private static EntryCodeContext contextWithTwoUnconfirmedWorkspaceIdentities(
      HttpEntryPoint entry, String path, EntryCodeContext.TechnicalEnhancements enhancements) {
    EntryCodeContext base = context(entry, path, enhancements);
    EntryCodeContext.CallSite sharedCall = base.calls().get(0);
    EntryCodeContext.CallTarget unknownTarget =
        new EntryCodeContext.CallTarget(
            null,
            List.of("DECLARATION"),
            HOST_WORKSPACE_IDENTITY,
            List.of("CALL_HIERARCHY"),
            "NOT_EXPANDED",
            "NAVIGATION_CONFLICT_NOT_EXPANDED",
            List.of());
    List<EntryCodeContext.CallObservation> observations =
        List.of(
            new EntryCodeContext.CallObservation(
                "UNCONFIRMED_NAVIGATION_LOCATION",
                "CALL_HIERARCHY",
                "SOURCE",
                sharedCall.site(),
                "UNCONFIRMED",
                null,
                null,
                "SOURCE",
                HOST_WORKSPACE_IDENTITY,
                "NAVIGATION_CONFLICT_NOT_EXPANDED"),
            new EntryCodeContext.CallObservation(
                "UNCONFIRMED_NAVIGATION_LOCATION",
                "CALL_HIERARCHY",
                "SOURCE",
                sharedCall.site(),
                "UNCONFIRMED",
                null,
                null,
                "SOURCE",
                SECOND_HOST_WORKSPACE_IDENTITY,
                "NAVIGATION_CONFLICT_NOT_EXPANDED"));
    EntryCodeContext.CallSite unconfirmedCall =
        new EntryCodeContext.CallSite(
            sharedCall.callKey(),
            sharedCall.callerMethodKey(),
            sharedCall.kind(),
            sharedCall.site(),
            sharedCall.navigationSite(),
            sharedCall.expression(),
            sharedCall.receiverExpression(),
            sharedCall.actualArguments(),
            sharedCall.enclosingControlIndexes(),
            sharedCall.deferred(),
            List.of(unknownTarget),
            "NAVIGATION_CONFLICT",
            "CALL_SITE_ASSOCIATION_UNCONFIRMED:shared",
            observations);
    return new EntryCodeContext(
        base.schemaVersion(),
        base.entryId(),
        base.entryMethodKey(),
        base.methods(),
        List.of(unconfirmedCall, base.calls().get(1)),
        base.supportingSources(),
        List.of(
            new EntryCodeContext.Limitation(
                "NAVIGATION_CONFLICT",
                "JDT retained unconfirmed workspace candidates",
                List.of(entry.methodKey()),
                List.of(sharedCall.callKey()))),
        base.technicalEnhancements());
  }

  private static EntryCodeContext contextWithRelativeAndWorkspaceIdentities(
      HttpEntryPoint entry, String path, EntryCodeContext.TechnicalEnhancements enhancements) {
    EntryCodeContext base = context(entry, path, enhancements);
    EntryCodeContext.CallSite sharedCall = base.calls().get(0);
    EntryCodeContext.CallTarget relativeTarget =
        new EntryCodeContext.CallTarget(
            null,
            List.of("DECLARATION"),
            PORTABLE_SOURCE_IDENTITY,
            List.of("CALL_HIERARCHY"),
            "NOT_EXPANDED",
            "NAVIGATION_CONFLICT_NOT_EXPANDED",
            List.of());
    List<EntryCodeContext.CallObservation> observations =
        List.of(
            new EntryCodeContext.CallObservation(
                "UNCONFIRMED_NAVIGATION_LOCATION",
                "CALL_HIERARCHY",
                "SOURCE",
                sharedCall.site(),
                "UNCONFIRMED",
                null,
                null,
                "SOURCE",
                PORTABLE_SOURCE_IDENTITY,
                "NAVIGATION_CONFLICT_NOT_EXPANDED"),
            new EntryCodeContext.CallObservation(
                "UNCONFIRMED_NAVIGATION_LOCATION",
                "CALL_HIERARCHY",
                "SOURCE",
                sharedCall.site(),
                "UNCONFIRMED",
                null,
                null,
                "SOURCE",
                HOST_WORKSPACE_IDENTITY,
                "NAVIGATION_CONFLICT_NOT_EXPANDED"));
    EntryCodeContext.CallSite mixedCall =
        new EntryCodeContext.CallSite(
            sharedCall.callKey(),
            sharedCall.callerMethodKey(),
            sharedCall.kind(),
            sharedCall.site(),
            sharedCall.navigationSite(),
            sharedCall.expression(),
            sharedCall.receiverExpression(),
            sharedCall.actualArguments(),
            sharedCall.enclosingControlIndexes(),
            sharedCall.deferred(),
            List.of(relativeTarget),
            "NAVIGATION_CONFLICT",
            "CALL_SITE_ASSOCIATION_UNCONFIRMED:shared",
            observations);
    return new EntryCodeContext(
        base.schemaVersion(),
        base.entryId(),
        base.entryMethodKey(),
        base.methods(),
        List.of(mixedCall, base.calls().get(1)),
        base.supportingSources(),
        List.of(
            new EntryCodeContext.Limitation(
                "NAVIGATION_CONFLICT",
                "JDT retained relative and workspace candidates",
                List.of(entry.methodKey()),
                List.of(sharedCall.callKey()))),
        base.technicalEnhancements());
  }

  private static PersistenceMaterialIndex persistenceIndex(ProgramGraphsReference navigation) {
    String rawXml =
        "<mapper namespace=\"fixture.OrderMapper\"><select id=\"first\">SELECT 1</select></mapper>";
    PersistenceMaterialIndex.Resource common =
        new PersistenceMaterialIndex.Resource(
            "src/main/resources/mapper/Common.xml", "fixture.OrderMapper", "<include/>", List.of());
    PersistenceMaterialIndex.Resource mapper =
        new PersistenceMaterialIndex.Resource(
            "src/main/resources/mapper/Orders.xml",
            "fixture.OrderMapper",
            rawXml,
            List.of(common.resourcePath()));
    PersistenceMaterialIndex.Statement statement =
        new PersistenceMaterialIndex.Statement(
            "statement:first",
            mapper.resourcePath(),
            "fixture.OrderMapper",
            "first",
            "select",
            null,
            new PersistenceMaterialIndex.XmlNode(
                PersistenceMaterialIndex.XmlNodeKind.ELEMENT,
                "select",
                Map.of("id", "first"),
                null,
                List.of()),
            List.of());
    PersistenceMaterialIndex.JavaBinding binding =
        new PersistenceMaterialIndex.JavaBinding(
            "fixture.OrderMapper",
            "method:mapper:first",
            "void first()",
            "EXACT",
            List.of(),
            List.of(new PersistenceMaterialIndex.StatementRef("statement:first", null)),
            List.of());
    PersistenceMaterialIndex.SqlAnalysis sql =
        new PersistenceMaterialIndex.SqlAnalysis(
            "statement:first",
            "SELECT 1 ORDER BY id DESC",
            List.of(),
            new PersistenceMaterialIndex.SqlAstNode(
                "SELECT",
                null,
                Map.of(),
                List.of(
                    new PersistenceMaterialIndex.SqlAstNode(
                        "ORDER_BY", "id", Map.of("direction", "DESC"), List.of()))),
            PersistenceMaterialIndex.SqlStatus.PARSED,
            null);
    return new PersistenceMaterialIndex(
        new PersistenceMaterialIndex.Header(
            PersistenceMaterialIndex.Status.ENABLED,
            SNAPSHOT,
            navigation,
            List.of(new PersistenceMaterialIndex.Tool("jsqlparser", "5.3"))),
        List.of(mapper, common),
        List.of(statement),
        List.of(binding),
        List.of(sql),
        List.of());
  }

  private static HttpEntryPoint entry(String id, String route, String methodKey) {
    return new HttpEntryPoint(
        ArtifactId.parse(id),
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        "GET",
        route,
        List.of("orders", route.substring(route.lastIndexOf('/') + 1)),
        "fixture." + methodKey.substring(methodKey.indexOf(':') + 1),
        methodKey,
        range(10, 12),
        List.of(),
        List.of(excerpt("src/main/java/Entry.java"), excerpt("src/main/java/Entry.java")));
  }

  private static HttpEntryPoint unrestrictedEntry(String id, String route, String methodKey) {
    return new HttpEntryPoint(
        ArtifactId.parse(id),
        HttpEntryKind.SPRING_MVC_HTTP,
        "HTTP",
        HttpMethodCondition.unrestricted(),
        route,
        List.of("orders", route.substring(route.lastIndexOf('/') + 1)),
        "fixture." + methodKey.substring(methodKey.indexOf(':') + 1),
        methodKey,
        range(10, 12),
        List.of(),
        List.of(excerpt("src/main/java/Entry.java"), excerpt("src/main/java/Entry.java")));
  }

  private static FrontendHttpRequestRecord request(
      String id, String path, String page, String sourceHash, String method) {
    return new FrontendHttpRequestRecord(
        id,
        page,
        sourceHash,
        page,
        range(0, 10),
        method,
        "'" + path + "'",
        path,
        null,
        List.of(),
        List.of(),
        List.of(),
        null,
        null);
  }

  private static FrontendHttpRequestRecord requestWithBaseFallback(
      String id,
      String path,
      String page,
      String sourceHash,
      String method,
      String baseExpression,
      String staticFallback) {
    return new FrontendHttpRequestRecord(
        id,
        page,
        sourceHash,
        page,
        range(0, 10),
        method,
        baseExpression + " + '" + path + "'",
        path,
        null,
        List.of(),
        List.of(),
        List.of(),
        baseExpression,
        staticFallback);
  }

  private static FrontendSourceUnits.Unit unit(String id, String path, String sourceHash) {
    return new FrontendSourceUnits.Unit(
        id,
        path,
        sourceHash,
        range(0, 100),
        FrontendWrapperCall.SourceUnitKind.FILE_FALLBACK,
        "saved " + path);
  }

  private static SourceExcerptV1 excerpt(String path) {
    byte[] bytes = "@GetMapping".getBytes(StandardCharsets.UTF_8);
    return new SourceExcerptV1(
        new SourceLocatorV1(
            ArtifactId.parse("source:" + "e".repeat(64)),
            path,
            0,
            bytes.length,
            1,
            1,
            1,
            bytes.length),
        ImmutableBytes.copyOf(bytes),
        digest(bytes));
  }

  private static AnalysisStepPublicationReference step(AnalysisStepKey key, char seed) {
    String hex = String.valueOf(seed).repeat(64);
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(RUN, key),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + hex),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + hex),
        digest(seed));
  }

  private static ModulePublicationReference module(int number, String key, char seed) {
    String hex = String.valueOf(seed).repeat(64);
    return new ModulePublicationReference(
        new AnalysisStepModuleAddress(RUN, AnalysisStepKey.APPLICATION_DISCOVERY, number, key),
        ModuleArtifactRoot.parse("module-root:" + hex),
        ModuleReceiptId.parse("module-receipt:" + hex),
        digest(seed));
  }

  private static SourceRange range(int start, int length) {
    return new SourceRange(start, length, 1, 3);
  }

  private static Sha256Digest digest(char seed) {
    return Sha256Digest.parse(String.valueOf(seed).repeat(64));
  }

  private static Sha256Digest digest(byte[] bytes) {
    try {
      return Sha256Digest.parse(
          java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (Exception failure) {
      throw new AssertionError(failure);
    }
  }
}
