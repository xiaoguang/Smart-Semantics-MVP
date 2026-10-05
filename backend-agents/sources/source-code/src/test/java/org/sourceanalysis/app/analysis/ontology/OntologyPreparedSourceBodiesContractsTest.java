package org.sourceanalysis.app.analysis.ontology;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitHandle;
import org.sourceanalysis.app.analysis.ontology.OntologyEvidenceCorpus.UnitKind;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Direct regression contracts for adding exact saved R0 bodies to a new ontology corpus rule. */
final class OntologyPreparedSourceBodiesContractsTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final CanonicalJsonCodec CANONICAL = new CanonicalJsonCodec();
  private static final String ENTRY_ID = "entry:" + "a".repeat(64);
  private static final String SNAPSHOT_ID = "snapshot:" + "b".repeat(64);
  private static final String SOURCE_PATH = "src/main/java/fixture/Handler.java";

  @Test
  void historicalSourceReferenceProjectionRemainsMetadataOnly() {
    String source = "package fixture;\nfinal class Handler {}\n";
    OntologyEvidenceCorpus historical =
        corpus(SNAPSHOT_ID, wholeFileReference(source), new ObjectNode[0]);
    UnitHandle reference = new UnitHandle(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "source:route");

    JsonNode oldContent =
        historical.read(ENTRY_ID, reference.kind(), reference.originalId()).content();
    JsonNode oldProjection = OntologyModelProjection.project(UnitKind.SOURCE_REFERENCE, oldContent);

    assertThat(fieldNames(oldProjection)).containsExactlyInAnyOrder("kind", "path", "range");
    assertThat(oldProjection.path("sourceText").isMissingNode()).isTrue();
    assertThat(oldProjection.path("path").asText()).isEqualTo(SOURCE_PATH);
  }

  @Test
  void fullFileReferenceReadsExactR0TextWithoutRequiringSavedMethodDeclarations() throws Exception {
    String source =
        "package fixture;\n"
            + "final class Handler {\n"
            + "  public void process() { audit(); }\n"
            + "}\n";
    OntologyEvidenceCorpus base =
        corpus(SNAPSHOT_ID, wholeFileReference(source), new ObjectNode[0]);
    UnitHandle sourceReference =
        new UnitHandle(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "source:route");
    ImmutableBytes priorAliases = base.aliases().canonicalMapping();
    String priorIdentity = base.sourceIdentity();

    OntologyEvidenceCorpus supplemented =
        withPreparedSourceBodies(base, sourceSet(source), List.of());
    JsonNode completedReference =
        supplemented.read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "source:route").content();

    assertThat(completedReference.path("kind").asText()).isEqualTo("HTTP_ROUTE");
    assertThat(completedReference.path("path").asText()).isEqualTo(SOURCE_PATH);
    assertThat(completedReference.path("range").isNull()).isTrue();
    assertThat(completedReference.path("sourceText").asText()).isEqualTo(source);
    assertThat(supplemented.sourceIdentity()).isNotEqualTo(priorIdentity);
    assertThat(base.sourceIdentity()).isEqualTo(priorIdentity);
    assertThat(base.aliases().canonicalMapping()).isEqualTo(priorAliases);
    assertThat(
            base.read(ENTRY_ID, sourceReference.kind(), sourceReference.originalId())
                .content()
                .path("sourceText")
                .isMissingNode())
        .isTrue();
  }

  @Test
  void nullableSavedSourceDigestAndIdentityMeanUnboundNotLiteralNull() throws Exception {
    String source = "package fixture;\nfinal class Handler { void route() {} }\n";
    ObjectNode reference = wholeFileReference(source);
    reference.putNull("sourceSha256");
    reference.putNull("sourceIdentity");
    OntologyEvidenceCorpus base = corpus(SNAPSHOT_ID, reference, new ObjectNode[0]);

    OntologyEvidenceCorpus supplemented =
        withPreparedSourceBodies(base, sourceSet(source), List.of());
    JsonNode completed =
        supplemented.read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "source:route").content();

    assertThat(completed.path("sourceSha256").isNull()).isTrue();
    assertThat(completed.path("sourceIdentity").isNull()).isTrue();
    assertThat(completed.path("sourceText").asText()).isEqualTo(source);
    assertThat(completed.path("preparedBody").path("kind").asText()).isEqualTo("FULL_FILE");
  }

  @Test
  void sameFileReferencesKeepTheirOwnSavedRangeAndNullRangeRemainsWholeFile() throws Exception {
    String routeText = "public void route() { invokeHelper(); }";
    String helperText = "private void helper() { audit(); }";
    String source =
        "package fixture;\n"
            + "final class Handler {\n"
            + "  "
            + routeText
            + "\n"
            + "  "
            + helperText
            + "\n"
            + "}\n";
    int routeStart = source.indexOf(routeText);
    int helperStart = source.indexOf(helperText);
    SourceRange routeRange = new SourceRange(routeStart, routeText.length(), 3, 3);
    SourceRange helperRange = new SourceRange(helperStart, helperText.length(), 4, 4);
    ObjectNode routeMethodRef = sourceReference("http-route:0", "HTTP_ROUTE", source, null);
    ObjectNode helperMethodRef =
        sourceReference("java-method:method:helper", "JAVA_METHOD", source, helperRange);
    OntologyEvidenceCorpus base =
        corpusWithSources(
            SNAPSHOT_ID,
            new ObjectNode[] {routeMethodRef, helperMethodRef},
            new ObjectNode[0],
            "method:route");
    List<JavaDeclarationCatalog.MethodDeclarationView> declarations =
        List.of(
            savedMethod("method:route", "fixture.Handler", "route", routeRange),
            savedMethod("method:helper", "fixture.Handler", "helper", helperRange));

    OntologyEvidenceCorpus supplemented =
        withPreparedSourceBodies(base, sourceSet(source), declarations);
    JsonNode routeContent =
        supplemented.read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "http-route:0").content();
    JsonNode helperContent =
        supplemented
            .read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "java-method:method:helper")
            .content();

    assertThat(routeContent.path("range").isNull()).isTrue();
    assertThat(routeContent.path("sourceText").asText()).isEqualTo(source);
    assertThat(routeContent.path("preparedBody").path("kind").asText()).isEqualTo("FULL_FILE");
    assertThat(routeContent.path("preparedBody").path("range").isNull()).isTrue();
    JsonNode routeModelInput =
        OntologyModelProjection.project(UnitKind.SOURCE_REFERENCE, routeContent);
    assertThat(routeModelInput.path("sourceText").asText()).isEqualTo(source);
    assertThat(helperContent.path("range").path("startOffsetUtf16").asInt())
        .isEqualTo(helperRange.startOffsetUtf16());
    assertThat(helperContent.path("sourceText").asText()).isEqualTo(helperText);
    assertThat(helperContent.path("preparedBody").path("kind").asText())
        .isEqualTo("METHOD_DECLARATION");
    assertThat(helperContent.path("preparedBody").path("range").path("lengthUtf16").asInt())
        .isEqualTo(helperRange.lengthUtf16());
  }

  @Test
  void preparedSourceSupplementPreservesEntryLimitationContextAcrossReplacedAndAddedUnits()
      throws Exception {
    String routeText = "public void route() { process(); }";
    String candidateText = "private void process() { audit(); }";
    String source =
        "package fixture;\n"
            + "final class Handler {\n"
            + "  "
            + routeText
            + "\n"
            + "  "
            + candidateText
            + "\n"
            + "}\n";
    int candidateStart = source.indexOf(candidateText);
    SourceRange candidateRange = new SourceRange(candidateStart, candidateText.length(), 4, 4);
    int nameStart = source.indexOf("process", candidateStart);
    SourceRange observedNameRange = new SourceRange(nameStart, "process".length(), 4, 4);
    ObjectNode call = unconfirmedCall("call:entry-process", observedNameRange);
    call.put("callerMethodKey", "method:route");
    ObjectNode entryMethod = JSON.createObjectNode();
    entryMethod.put("methodKey", "method:route");
    entryMethod.putObject("source").put("text", routeText);
    OntologyEvidenceCorpus base =
        corpusWithSourcesAndLimitations(
            SNAPSHOT_ID,
            new ObjectNode[] {wholeFileReference(source)},
            new ObjectNode[] {call},
            "method:route",
            List.of("NAVIGATION_CONFLICT", "NAVIGATION_CONFLICT"),
            new ObjectNode[] {entryMethod});
    JavaDeclarationCatalog.MethodDeclarationView savedCandidate =
        savedMethod("method:process", "fixture.Handler", "process", candidateRange);

    OntologyEvidenceCorpus supplemented =
        withPreparedSourceBodies(base, sourceSet(source), List.of(savedCandidate));
    OntologyEvidenceCorpus.EvidenceUnit completedRoute =
        supplemented.read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "source:route");
    OntologyEvidenceCorpus.EvidenceUnit originalEntryMethod =
        supplemented.read(ENTRY_ID, UnitKind.JAVA_METHOD, "method:route");
    OntologyEvidenceCorpus.EvidenceUnit addedCandidateMethod =
        supplemented.read(ENTRY_ID, UnitKind.JAVA_METHOD, "method:process");
    OntologyEvidenceCorpus.EvidenceUnit unresolvedCall =
        supplemented.read(ENTRY_ID, UnitKind.JAVA_CALL, "call:entry-process");
    Map<String, Integer> expectedLimitations = Map.of("NAVIGATION_CONFLICT", 2);

    assertThat(base.read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "source:route").limitationCounts())
        .isEqualTo(expectedLimitations);
    assertThat(completedRoute.limitationCounts()).isEqualTo(expectedLimitations);
    assertThat(originalEntryMethod.limitationCounts()).isEqualTo(expectedLimitations);
    assertThat(addedCandidateMethod.limitationCounts()).isEqualTo(expectedLimitations);
    assertThat(
            addedCandidateMethod
                .content()
                .path("sourceSupplement")
                .path("observations")
                .get(0)
                .path("association")
                .asText())
        .isEqualTo("UNCONFIRMED");
    assertThat(
            addedCandidateMethod
                .content()
                .path("sourceSupplement")
                .path("doesNotConfirmCallEdge")
                .asBoolean())
        .isTrue();
    assertThat(unresolvedCall.content().path("resolution").asText())
        .isEqualTo("NAVIGATION_CONFLICT");
    assertThat(unresolvedCall.content().path("targets")).isEmpty();

    OntologyReadingPacket combined =
        OntologyReadingPacket.of(
            supplemented.sourceIdentity(),
            List.of(completedRoute, originalEntryMethod, addedCandidateMethod, unresolvedCall));
    JsonNode context = CANONICAL.parseCanonical(combined.modelInput()).path("entryContexts").get(0);
    assertThat(context.path("limitationCounts").path("NAVIGATION_CONFLICT").asInt()).isEqualTo(2);

    OntologyEvidenceCorpus.EvidenceUnit differentCounts =
        new OntologyEvidenceCorpus.EvidenceUnit(
            addedCandidateMethod.entryId(),
            addedCandidateMethod.kind(),
            addedCandidateMethod.originalId(),
            addedCandidateMethod.canonicalJson(),
            Map.of("NAVIGATION_CONFLICT", 1),
            addedCandidateMethod.entryDescriptor());
    assertThatThrownBy(
            () ->
                OntologyReadingPacket.of(
                    supplemented.sourceIdentity(),
                    List.of(completedRoute, originalEntryMethod, differentCounts)))
        .hasMessage("ONTOLOGY_ENTRY_LIMITATIONS_CONFLICT");
  }

  @Test
  void nullRangeHttpRouteRetainsClassAndMethodRouteMappingsAsFullFileEvidence() throws Exception {
    String routeText = "@GetMapping(\"/orders\")\n  public void route() { invokeHelper(); }";
    String source =
        "package fixture;\n"
            + "@RequestMapping(\"/api\")\n"
            + "final class Handler {\n"
            + "  "
            + routeText
            + "\n"
            + "}\n";
    int routeStart = source.indexOf("public void route()");
    SourceRange routeRange =
        new SourceRange(routeStart, "public void route() { invokeHelper(); }".length(), 5, 5);
    ObjectNode routeReference =
        sourceReference("http-route:class-and-method", "HTTP_ROUTE", source, null);
    OntologyEvidenceCorpus base =
        corpusWithSources(
            SNAPSHOT_ID, new ObjectNode[] {routeReference}, new ObjectNode[0], "method:route");

    OntologyEvidenceCorpus supplemented =
        withPreparedSourceBodies(
            base,
            sourceSet(source),
            List.of(savedMethod("method:route", "fixture.Handler", "route", routeRange)));
    JsonNode routeContent =
        supplemented
            .read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "http-route:class-and-method")
            .content();
    JsonNode routeModelInput =
        OntologyModelProjection.project(UnitKind.SOURCE_REFERENCE, routeContent);

    assertThat(routeContent.path("kind").asText()).isEqualTo("HTTP_ROUTE");
    assertThat(routeContent.path("path").asText()).isEqualTo(SOURCE_PATH);
    assertThat(routeContent.path("range").isNull()).isTrue();
    assertThat(routeContent.path("preparedBody").path("kind").asText()).isEqualTo("FULL_FILE");
    assertThat(routeContent.path("preparedBody").path("range").isNull()).isTrue();
    assertThat(routeModelInput.path("sourceText").asText()).isEqualTo(source);
    assertThat(routeModelInput.path("sourceText").asText())
        .contains("@RequestMapping(\"/api\")", "@GetMapping(\"/orders\")");
    assertThat(routeModelInput.path("sourceText").asText()).isNotEqualTo(routeText);
  }

  @Test
  void missingR0SourceCannotFallBackToMetadataOnlyForTheNewCorpusRule() throws Exception {
    String source = "package fixture;\nfinal class Handler { void route() {} }\n";
    OntologyEvidenceCorpus base =
        corpus(SNAPSHOT_ID, wholeFileReference(source), new ObjectNode[0]);

    assertThatThrownBy(
            () ->
                withPreparedSourceBodies(
                    base, sourceSetAt("src/main/java/fixture/Other.java", source), List.of()))
        .hasMessage("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
    assertThat(
            base.read(ENTRY_ID, UnitKind.SOURCE_REFERENCE, "source:route")
                .content()
                .path("sourceText")
                .isMissingNode())
        .isTrue();
  }

  @Test
  void uniqueUnconfirmedRepositoryLocationCompletesMethodWithoutPromotingCallEdge()
      throws Exception {
    String declarationText = "public void process() { audit(); }";
    String source =
        "package fixture;\n" + "final class Handler {\n" + "  " + declarationText + "\n}\n";
    int start = source.indexOf(declarationText);
    SourceRange methodRange = new SourceRange(start, declarationText.length(), 3, 3);
    int nameStart = source.indexOf("process", start);
    SourceRange observedNameRange = new SourceRange(nameStart, "process".length(), 3, 3);
    ObjectNode call = JSON.createObjectNode();
    call.put("callKey", "call:unconfirmed-process");
    call.put("resolution", "NAVIGATION_CONFLICT");
    call.put("resolutionDetail", "CALL_SITE_ASSOCIATION_UNCONFIRMED");
    call.putArray("targets");
    ObjectNode observation = call.putArray("observations").addObject();
    observation.put("uriKind", "REPOSITORY_SOURCE");
    observation.put("association", "UNCONFIRMED");
    observation.put("code", "UNCONFIRMED_NAVIGATION_LOCATION");
    observation.put("detail", "NAVIGATION_CONFLICT_NOT_EXPANDED");
    observation.put("displayIdentity", "fixture.Handler.process() : void");
    observation.putNull("declarationKey");
    observation.put("typeOrigin", "SOURCE");
    putRange(observation.putObject("sourceRange"), observedNameRange);
    OntologyEvidenceCorpus base = corpus(SNAPSHOT_ID, null, new ObjectNode[] {call});
    JavaDeclarationCatalog.MethodDeclarationView savedDeclaration =
        new JavaDeclarationCatalog.MethodDeclarationView(
            "method:process",
            "fixture.Handler",
            "process",
            "METHOD",
            List.of("public"),
            List.of(),
            "void",
            List.of(),
            SOURCE_PATH,
            methodRange,
            true);

    OntologyEvidenceCorpus supplemented =
        withPreparedSourceBodies(base, sourceSet(source), List.of(savedDeclaration));
    JsonNode completedMethod =
        supplemented.read(ENTRY_ID, UnitKind.JAVA_METHOD, "method:process").content();
    JsonNode preservedCall =
        supplemented.read(ENTRY_ID, UnitKind.JAVA_CALL, "call:unconfirmed-process").content();

    assertThat(textValues(completedMethod)).contains(declarationText);
    assertThat(preservedCall.path("resolution").asText()).isEqualTo("NAVIGATION_CONFLICT");
    assertThat(preservedCall.path("targets")).isEmpty();
    assertThat(preservedCall.path("observations").get(0).path("association").asText())
        .isEqualTo("UNCONFIRMED");
    assertThat(preservedCall.path("observations").get(0).path("detail").asText())
        .isEqualTo("NAVIGATION_CONFLICT_NOT_EXPANDED");
    assertThat(supplemented.entryUnits(ENTRY_ID, 0, 10).items())
        .contains(new UnitHandle(ENTRY_ID, UnitKind.JAVA_METHOD, "method:process"));
  }

  @Test
  void ambiguousOrOutOfBoundsSavedCandidatesDoNotCreateSupplementUnits() throws Exception {
    String declarationText = "public void process() { audit(); }";
    String source =
        "package fixture;\n" + "final class Handler {\n" + "  " + declarationText + "\n}\n";
    int start = source.indexOf(declarationText);
    int nameStart = source.indexOf("process", start);
    SourceRange nameRange = new SourceRange(nameStart, "process".length(), 3, 3);
    ObjectNode call = unconfirmedCall("call:ambiguous-process", nameRange);
    OntologyEvidenceCorpus base = corpus(SNAPSHOT_ID, null, new ObjectNode[] {call});
    JavaDeclarationCatalog.MethodDeclarationView first =
        savedMethod("method:process-first", start, declarationText.length());
    JavaDeclarationCatalog.MethodDeclarationView second =
        savedMethod("method:process-second", start, declarationText.length());

    OntologyEvidenceCorpus ambiguous =
        withPreparedSourceBodies(base, sourceSet(source), List.of(first, second));

    assertThat(ambiguous.entryUnits(ENTRY_ID, 0, 10).items())
        .noneMatch(unit -> unit.kind() == UnitKind.JAVA_METHOD);
    assertThat(
            ambiguous
                .read(ENTRY_ID, UnitKind.JAVA_CALL, "call:ambiguous-process")
                .content()
                .path("resolution")
                .asText())
        .isEqualTo("NAVIGATION_CONFLICT");

    SourceRange outOfBoundsRange = new SourceRange(source.length() + 3, 7, 9, 9);
    ObjectNode outOfBoundsCall = unconfirmedCall("call:out-of-bounds-process", outOfBoundsRange);
    OntologyEvidenceCorpus outOfBoundsBase =
        corpus(SNAPSHOT_ID, null, new ObjectNode[] {outOfBoundsCall});
    JavaDeclarationCatalog.MethodDeclarationView outOfBounds =
        new JavaDeclarationCatalog.MethodDeclarationView(
            "method:process-out-of-bounds",
            "fixture.Handler",
            "process",
            "METHOD",
            List.of("public"),
            List.of(),
            "void",
            List.of(),
            SOURCE_PATH,
            outOfBoundsRange,
            true);

    OntologyEvidenceCorpus rejectedRange =
        withPreparedSourceBodies(outOfBoundsBase, sourceSet(source), List.of(outOfBounds));

    assertThat(rejectedRange.entryUnits(ENTRY_ID, 0, 10).items())
        .noneMatch(unit -> unit.kind() == UnitKind.JAVA_METHOD);
    assertThat(
            rejectedRange
                .read(ENTRY_ID, UnitKind.JAVA_CALL, "call:out-of-bounds-process")
                .content()
                .path("targets"))
        .isEmpty();
  }

  private static OntologyEvidenceCorpus withPreparedSourceBodies(
      OntologyEvidenceCorpus corpus,
      VerifiedSourceTextSet source,
      List<JavaDeclarationCatalog.MethodDeclarationView> declarations)
      throws Exception {
    Optional<Method> factory =
        Arrays.stream(OntologyEvidenceCorpus.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("withPreparedSourceBodies"))
            .filter(parameters -> parameters.getParameterCount() == 2)
            .filter(
                method ->
                    method.getParameterTypes()[0].equals(VerifiedSourceTextSet.class)
                        && method.getParameterTypes()[1].equals(List.class))
            .findFirst();
    assertThat(factory)
        .as("new corpus rule should expose the exact saved-source supplement seam")
        .isPresent();
    Method selected = factory.orElseThrow();
    selected.setAccessible(true);
    try {
      return (OntologyEvidenceCorpus) selected.invoke(corpus, source, declarations);
    } catch (InvocationTargetException invalidSource) {
      if (invalidSource.getCause() instanceof Exception exception) {
        throw exception;
      }
      throw invalidSource;
    }
  }

  private static OntologyEvidenceCorpus corpus(
      String snapshotId, ObjectNode sourceReference, ObjectNode[] calls) {
    ObjectNode[] references =
        sourceReference == null ? new ObjectNode[0] : new ObjectNode[] {sourceReference};
    return corpusWithSources(snapshotId, references, calls, null);
  }

  private static OntologyEvidenceCorpus corpusWithSources(
      String snapshotId, ObjectNode[] sourceReferences, ObjectNode[] calls, String entryMethodKey) {
    return corpusWithSourcesAndLimitations(
        snapshotId, sourceReferences, calls, entryMethodKey, List.of(), new ObjectNode[0]);
  }

  private static OntologyEvidenceCorpus corpusWithSourcesAndLimitations(
      String snapshotId,
      ObjectNode[] sourceReferences,
      ObjectNode[] calls,
      String entryMethodKey,
      List<String> limitationCodes,
      ObjectNode[] methodUnits) {
    ObjectNode index = JSON.createObjectNode();
    ObjectNode header = index.putObject("header");
    header.put("sourceSnapshotId", snapshotId);
    header.putObject("sourceBasis").put("kind", "PREPARED_V1");

    ObjectNode entry = JSON.createObjectNode();
    entry.put("entryId", ENTRY_ID);
    entry.put("assemblyStatus", "ASSEMBLED");
    entry.putObject("sourceBasis").put("kind", "PREPARED_V1");
    ObjectNode httpEntry = entry.putObject("entry");
    httpEntry.put("method", "GET");
    httpEntry.put("route", "/neutral");
    httpEntry.put("handlerFqn", "fixture.Handler");
    if (entryMethodKey != null) {
      httpEntry.put("methodKey", entryMethodKey);
    }
    ObjectNode java = entry.putObject("java");
    var savedMethods = java.putArray("methods");
    for (ObjectNode method : methodUnits) {
      savedMethods.add(method.deepCopy());
    }
    var savedCalls = java.putArray("calls");
    for (ObjectNode call : calls) {
      savedCalls.add(call);
    }
    java.putArray("observations");
    java.putArray("supportingSources");
    entry.putObject("frontend").putArray("units");
    entry.putObject("persistence");
    var limitations = entry.putArray("limitations");
    for (String code : limitationCodes) {
      limitations.addObject().put("code", code).put("subjectRef", ENTRY_ID);
    }
    var references = entry.putArray("sourceRefs");
    for (ObjectNode sourceReference : sourceReferences) {
      references.add(sourceReference);
    }
    EntryEvidenceReader.Directory directory =
        new EntryEvidenceReader.Directory(
            CANONICAL.encodeCanonical(index),
            ImmutableBytes.copyOf(new byte[0]),
            List.of(
                new EntryEvidenceReader.EntryDocument(ENTRY_ID, CANONICAL.encodeCanonical(entry))));
    return OntologyEvidenceCorpus.fromVerifiedDirectory(directory);
  }

  private static ObjectNode unconfirmedCall(String callKey, SourceRange observationRange) {
    ObjectNode call = JSON.createObjectNode();
    call.put("callKey", callKey);
    call.put("resolution", "NAVIGATION_CONFLICT");
    call.put("resolutionDetail", "CALL_SITE_ASSOCIATION_UNCONFIRMED");
    call.putArray("targets");
    ObjectNode observation = call.putArray("observations").addObject();
    observation.put("uriKind", "REPOSITORY_SOURCE");
    observation.put("association", "UNCONFIRMED");
    observation.put("code", "UNCONFIRMED_NAVIGATION_LOCATION");
    observation.put("detail", "NAVIGATION_CONFLICT_NOT_EXPANDED");
    observation.put("displayIdentity", "fixture.Handler.process() : void");
    observation.putNull("declarationKey");
    observation.put("typeOrigin", "SOURCE");
    putRange(observation.putObject("sourceRange"), observationRange);
    return call;
  }

  private static JavaDeclarationCatalog.MethodDeclarationView savedMethod(
      String methodKey, int start, int length) {
    return savedMethod(
        methodKey, "fixture.Handler", "process", new SourceRange(start, length, 3, 3));
  }

  private static JavaDeclarationCatalog.MethodDeclarationView savedMethod(
      String methodKey, String declaringType, String name, SourceRange range) {
    return new JavaDeclarationCatalog.MethodDeclarationView(
        methodKey,
        declaringType,
        name,
        "METHOD",
        List.of("public"),
        List.of(),
        "void",
        List.of(),
        SOURCE_PATH,
        range,
        true);
  }

  private static ObjectNode wholeFileReference(String source) {
    return sourceReference("source:route", "HTTP_ROUTE", source, null);
  }

  private static ObjectNode sourceReference(
      String referenceId, String kind, String source, SourceRange range) {
    ObjectNode reference = JSON.createObjectNode();
    String sourceDigest = sha256(source.getBytes(StandardCharsets.UTF_8));
    reference.put("reference", referenceId);
    reference.put("kind", kind);
    reference.put("path", SOURCE_PATH);
    if (range == null) {
      reference.putNull("range");
    } else {
      putRange(reference.putObject("range"), range);
    }
    reference.put("sourceSha256", sourceDigest);
    reference.put(
        "sourceIdentity",
        "file:" + sha256((SOURCE_PATH + sourceDigest).getBytes(StandardCharsets.UTF_8)));
    return reference;
  }

  private static VerifiedSourceTextSet sourceSet(String text) {
    return sourceSetAt(SOURCE_PATH, text);
  }

  private static VerifiedSourceTextSet sourceSetAt(String sourcePath, String text) {
    byte[] content = text.getBytes(StandardCharsets.UTF_8);
    String digest = sha256(content);
    VerifiedSourceTextDocument document =
        new VerifiedSourceTextDocument(
            ArtifactId.parse(
                "file:" + sha256((sourcePath + digest).getBytes(StandardCharsets.UTF_8))),
            sourcePath,
            "100644",
            "text/x-java",
            content.length,
            new Sha256Digest(digest),
            ImmutableBytes.copyOf(content));
    ArtifactReference capability = reference("capability-profile", '1');
    ArtifactReference inventory = reference("source-inventory", '2');
    ArtifactReference snapshot = reference("verified-snapshot", '3');
    ArtifactControls controls =
        new ArtifactControls(
            new Sha256Digest("4".repeat(64)),
            new Sha256Digest("5".repeat(64)),
            new Sha256Digest("6".repeat(64)),
            null,
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "7".repeat(64)),
                new Sha256Digest("7".repeat(64))));
    return new VerifiedSourceTextSet(
        SNAPSHOT_ID,
        "COMPLETE_CAPTURE",
        true,
        capability,
        inventory,
        snapshot,
        controls,
        List.of(document));
  }

  private static ArtifactReference reference(String prefix, char digest) {
    String value = String.valueOf(digest).repeat(64);
    return new ArtifactReference(ArtifactId.parse(prefix + ":" + value), new Sha256Digest(value));
  }

  private static List<String> fieldNames(JsonNode value) {
    List<String> names = new ArrayList<>();
    value.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private static List<String> textValues(JsonNode value) {
    List<String> values = new ArrayList<>();
    collectText(value, values);
    return values;
  }

  private static void collectText(JsonNode value, List<String> values) {
    if (value.isTextual()) {
      values.add(value.asText());
      return;
    }
    value.elements().forEachRemaining(child -> collectText(child, values));
  }

  private static void putRange(ObjectNode target, SourceRange range) {
    target.put("startOffsetUtf16", range.startOffsetUtf16());
    target.put("lengthUtf16", range.lengthUtf16());
    target.put("startLine", range.startLine());
    target.put("endLine", range.endLine());
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
