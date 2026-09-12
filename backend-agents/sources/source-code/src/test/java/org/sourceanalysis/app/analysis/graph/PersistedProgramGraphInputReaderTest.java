package org.sourceanalysis.app.analysis.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepInstallRequest;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepPublisherModuleProvenance;
import org.sourceanalysis.app.artifact.AnalysisStepReceipt;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledAnalysisStepPublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

class PersistedProgramGraphInputReaderTest {

  @Test
  void exposesAPersistedAndPathFreeProgramGraphInputBoundary() {
    Class<?> reader =
        typeOrNull("org.sourceanalysis.app.analysis.graph.PersistedProgramGraphInputReader");

    assertThat(reader)
        .as(
            "program graphs must reopen their source and application inputs rather than accept test objects")
        .isNotNull();
    assertThat(reader.getDeclaredConstructors())
        .as("the internal reader receives stores and verified-source services, never a caller path")
        .allSatisfy(
            constructor -> assertThat(constructor.getParameterTypes()).doesNotContain(Path.class));
  }

  @Test
  void reopensPublishedDiscoveryArtifactsIntoClosedProgramGraphInputs() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ArtifactControls controls = controls();
    Fixture fixture = fixture(canonicalJson, controls);
    AtomicInteger sourceReopenCount = new AtomicInteger();
    VerifiedSourceTextReader sourceReader =
        reference -> {
          assertThat(reference).isEqualTo(fixture.sourceReference());
          sourceReopenCount.incrementAndGet();
          return fixture.sourceTexts();
        };

    ReopenedProgramGraphInputs reopened =
        new PersistedProgramGraphInputReader(
                new FixedStepStore(fixture.discoveryPublication()), sourceReader)
            .reopen(fixture.sourceReference(), fixture.discoveryReference());

    assertThat(sourceReopenCount.get()).isEqualTo(1);
    assertThat(reopened.source().snapshotId()).isEqualTo(fixture.sourceTexts().snapshotId());
    assertThat(reopened.source().documents())
        .extracting(CodeStructureSourceDocument::path)
        .containsExactly("src/main/java/com/example/DepotHeadController.java");
    assertThat(reopened.discovery().codeStructureDiscovery().entryIds())
        .containsExactly(fixture.entryId());
    assertThat(reopened.discovery().entries())
        .singleElement()
        .satisfies(
            entry ->
                assertThat(entry.handlerFqn())
                    .isEqualTo("com.example.DepotHeadController#batchSetStatus(java.lang.String)"));
    assertThat(reopened.discovery().mapperCatalog())
        .singleElement()
        .satisfies(
            mapper -> {
              assertThat(mapper.javaInterfaceFqn()).isEqualTo("com.example.DepotHeadMapper");
              assertThat(mapper.xmlStatementCandidates())
                  .extracting(candidate -> candidate.statementId())
                  .containsExactly("updateStatus");
            });
  }

  @Test
  void rejectsDiscoveryThatDoesNotDeclareTheExactVerifiedSourcePredecessor() {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    Fixture fixture = fixture(canonicalJson, controls());
    AnalysisStepReceipt original = fixture.discoveryPublication().receipt();
    AnalysisStepReceipt brokenReceipt =
        new AnalysisStepReceipt(
            original.schemaVersion(),
            original.analysisStepReceiptId(),
            original.address(),
            original.publicationProvenance(),
            List.of(),
            original.controls(),
            original.status(),
            original.semanticArtifacts(),
            original.archiveManifest(),
            original.analysisStepArtifactRoot(),
            original.gapRefs());
    ReopenedAnalysisStepPublication brokenPublication =
        new ReopenedAnalysisStepPublication(
            fixture.discoveryPublication().reference(),
            brokenReceipt,
            fixture.discoveryPublication().semanticPayloads(),
            fixture.discoveryPublication().archiveManifestPayload());

    assertThatThrownBy(
            () ->
                new PersistedProgramGraphInputReader(
                        new FixedStepStore(brokenPublication), reference -> fixture.sourceTexts())
                    .reopen(fixture.sourceReference(), fixture.discoveryReference()))
        .isInstanceOf(ProgramGraphInputException.class)
        .hasMessage("PROGRAM_GRAPH_INPUT_INVALID");
  }

  private static Class<?> typeOrNull(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException missing) {
      return null;
    }
  }

  private static Fixture fixture(CanonicalJsonCodec canonicalJson, ArtifactControls controls) {
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + digest("program-graph-input-run"));
    ArtifactId fileId = id("file", "controller");
    String controller =
        "package com.example; class DepotHeadController { String batchSetStatus(String status) { return status; } }\n";
    ImmutableBytes controllerBytes =
        ImmutableBytes.copyOf(controller.getBytes(StandardCharsets.UTF_8));
    VerifiedSourceTextDocument document =
        new VerifiedSourceTextDocument(
            fileId,
            "src/main/java/com/example/DepotHeadController.java",
            "100644",
            "text/plain",
            controllerBytes.size(),
            new Sha256Digest(digest(controllerBytes.copyToByteArray())),
            controllerBytes);
    ArtifactReference inventory = reference("source-inventory", "graph-input");
    ArtifactReference snapshot = reference("verified-snapshot", "graph-input");
    VerifiedSourceTextSet sourceTexts =
        new VerifiedSourceTextSet(
            "snapshot:" + digest("program-graph-input-snapshot"),
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", "graph-input"),
            inventory,
            snapshot,
            controls,
            List.of(document));
    AnalysisStepPublicationReference sourcePublication =
        analysisStepReference(runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, "source");
    VerifiedSourceInventoryReference sourceReference =
        new VerifiedSourceInventoryReference(sourcePublication);
    AnalysisStepPublicationReference discoveryPublicationReference =
        analysisStepReference(runId, AnalysisStepKey.APPLICATION_DISCOVERY, "discovery");
    ArtifactId applicationProfileId = id("application-profile", "graph-input");
    ArtifactId entryId = id("entry", "batch-set-status");
    ArtifactId mapperCatalogEntryId = id("mapper-catalog-entry", "depot-head");

    ObjectNode profile = JsonNodeFactory.instance.objectNode();
    profile.put("schemaVersion", "application-discovery-application-profile-v2");
    profile.put("artifactType", "APPLICATION_DISCOVERY_APPLICATION_PROFILE");
    profile.put("applicationProfileId", applicationProfileId.value());
    profile.put(
        "artifactId",
        standaloneArtifactId(
                canonicalJson,
                profile,
                "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                "application-discovery-application-profile-v2",
                "application-profile")
            .value());
    ObjectNode capability = JsonNodeFactory.instance.objectNode();
    capability.put("schemaVersion", "application-discovery-capability-report-v2");
    capability.put("artifactType", "APPLICATION_DISCOVERY_CAPABILITY_REPORT");
    capability.put("artifactId", id("capability-report", "graph-input").value());
    capability.put("applicationProfileId", applicationProfileId.value());
    ObjectNode coverage = capability.putObject("repositoryEntryCoverage");
    coverage.putArray("entryIds").add(entryId.value());
    coverage.putArray("mapperCatalogEntryIds").add(mapperCatalogEntryId.value());

    ObjectNode entry = JsonNodeFactory.instance.objectNode();
    entry.put("schemaVersion", "application-discovery-entry-point-v3");
    entry.put("entryId", entryId.value());
    entry.put("kind", "SPRING_MVC_HTTP");
    entry.put("protocol", "HTTP");
    entry.put("method", "POST");
    entry.putObject("methodCondition").put("kind", "EXPLICIT").putArray("methods").add("POST");
    entry.put("route", "/depotHead/batchSetStatus");
    entry.putArray("routeParts").add("/depotHead").add("/batchSetStatus");
    entry.put("handlerFqn", "com.example.DepotHeadController#batchSetStatus(java.lang.String)");
    entry.put("methodKey", "method:batch-set-status");
    ObjectNode methodRange = entry.putObject("methodRange");
    methodRange.put("startOffsetUtf16", 0);
    methodRange.put("lengthUtf16", 1);
    methodRange.put("startLine", 1);
    methodRange.put("endLine", 1);
    entry.putArray("parameterNames").add("status");
    ArrayNode routeEvidence = entry.putArray("routeSourceExcerpts");
    routeEvidence.add(excerpt(fileId, document.path(), "@RequestMapping(\"/depotHead\")"));
    routeEvidence.add(excerpt(fileId, document.path(), "@PostMapping(\"/batchSetStatus\")"));

    ObjectNode mapper = JsonNodeFactory.instance.objectNode();
    mapper.put("schemaVersion", "application-discovery-mapper-catalog-entry-v2");
    mapper.put("catalogEntryId", mapperCatalogEntryId.value());
    mapper.put("javaInterfaceFqn", "com.example.DepotHeadMapper");
    ObjectNode method = mapper.putArray("javaMethodCandidates").addObject();
    method.put("methodCandidateId", id("mapper-method", "update-status").value());
    method.put("signature", "updateStatus(java.lang.String)");
    method.set("declarationExcerpt", excerpt(fileId, document.path(), "updateStatus"));
    mapper.put("xmlResourcePath", "src/main/resources/mapper/DepotHeadMapper.xml");
    mapper.put("xmlNamespace", "com.example.DepotHeadMapper");
    ObjectNode statement = mapper.putArray("xmlStatementCandidates").addObject();
    statement.put("statementCandidateId", id("mapper-statement", "update-status").value());
    statement.put("statementId", "updateStatus");
    statement.put("statementKind", "update");
    statement.set(
        "declarationExcerpt", excerpt(fileId, document.path(), "<update id=\"updateStatus\">"));
    mapper.put("bindingState", "CANDIDATE_NOT_YET_BOUND");

    List<VerifiedCanonicalPayload> payloads =
        List.of(
            jsonPayload(
                canonicalJson,
                "application-profile.json",
                "APPLICATION_DISCOVERY_APPLICATION_PROFILE",
                "application-discovery-application-profile-v2",
                profile),
            jsonPayload(
                canonicalJson,
                "capability-report.json",
                "APPLICATION_DISCOVERY_CAPABILITY_REPORT",
                "application-discovery-capability-report-v2",
                capability),
            jsonlPayload(
                canonicalJson,
                "entry-points.jsonl",
                "APPLICATION_DISCOVERY_ENTRY_POINTS",
                "application-discovery-entry-points-v3",
                List.of(entry)),
            jsonlPayload(
                canonicalJson,
                "mapper-catalog.jsonl",
                "APPLICATION_DISCOVERY_MAPPER_CATALOG",
                "application-discovery-mapper-catalog-v2",
                List.of(mapper)));
    AnalysisStepReceipt receipt =
        new AnalysisStepReceipt(
            "analysis-step-receipt-v1",
            discoveryPublicationReference.analysisStepReceiptId(),
            discoveryPublicationReference.address(),
            new AnalysisStepPublisherModuleProvenance(
                new ModulePublicationReference(
                    new org.sourceanalysis.app.artifact.AnalysisStepModuleAddress(
                        runId, AnalysisStepKey.APPLICATION_DISCOVERY, 4, "publish"),
                    ModuleArtifactRoot.parse("module-root:" + digest("graph-input-module-root")),
                    ModuleReceiptId.parse("module-receipt:" + digest("graph-input-module-receipt")),
                    new Sha256Digest(digest("graph-input-module-publication")))),
            List.of(sourcePublication),
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList(),
            null,
            discoveryPublicationReference.analysisStepArtifactRoot(),
            List.of());
    return new Fixture(
        sourceReference,
        sourceTexts,
        new ApplicationDiscoveryReference(discoveryPublicationReference),
        new ReopenedAnalysisStepPublication(discoveryPublicationReference, receipt, payloads, null),
        entryId);
  }

  private static ObjectNode excerpt(ArtifactId fileId, String path, String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    ObjectNode excerpt = JsonNodeFactory.instance.objectNode();
    ObjectNode locator = excerpt.putObject("locator");
    locator.put("fileId", fileId.value());
    locator.put("path", path);
    locator.put("startByte", 0);
    locator.put("endByteExclusive", bytes.length);
    locator.put("startLine", 1);
    locator.put("startColumn", 1);
    locator.put("endLine", 1);
    locator.put("endColumn", bytes.length + 1);
    excerpt.put("rawUtf8", value);
    excerpt.put("rawUtf8Sha256", digest(bytes));
    return excerpt;
  }

  private static VerifiedCanonicalPayload jsonPayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      ObjectNode document) {
    ImmutableBytes bytes = canonicalJson.encodeCanonical(document);
    return payload(
        fileName, artifactType, schemaVersion, CanonicalMediaType.APPLICATION_JSON, bytes);
  }

  private static VerifiedCanonicalPayload jsonlPayload(
      CanonicalJsonCodec canonicalJson,
      String fileName,
      String artifactType,
      String schemaVersion,
      List<ObjectNode> entries) {
    StringBuilder lines = new StringBuilder();
    entries.forEach(
        entry ->
            lines
                .append(
                    new String(
                        canonicalJson.encodeCanonical(entry).copyToByteArray(),
                        StandardCharsets.UTF_8))
                .append('\n'));
    return payload(
        fileName,
        artifactType,
        schemaVersion,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        ImmutableBytes.copyOf(lines.toString().getBytes(StandardCharsets.UTF_8)));
  }

  private static VerifiedCanonicalPayload payload(
      String fileName,
      String artifactType,
      String schemaVersion,
      CanonicalMediaType mediaType,
      ImmutableBytes bytes) {
    ArtifactId artifactId = id("artifact", fileName + artifactType);
    if (mediaType == CanonicalMediaType.APPLICATION_JSON) {
      JsonNode document = new CanonicalJsonCodec().parseCanonical(bytes);
      if (document.hasNonNull("artifactId")) {
        artifactId = ArtifactId.parse(document.get("artifactId").textValue());
      }
    }
    return new VerifiedCanonicalPayload(
        new ArtifactDescriptor(
            fileName,
            artifactType,
            schemaVersion,
            artifactId,
            mediaType,
            bytes.size(),
            new Sha256Digest(digest(bytes.copyToByteArray()))),
        bytes);
  }

  private static ArtifactId standaloneArtifactId(
      CanonicalJsonCodec canonicalJson,
      ObjectNode document,
      String artifactType,
      String schemaVersion,
      String prefix) {
    ObjectNode withoutArtifactId = document.deepCopy();
    withoutArtifactId.remove("artifactId");
    withoutArtifactId.put("schemaVersion", schemaVersion);
    withoutArtifactId.put("artifactType", artifactType);
    return ArtifactId.parse(
        prefix
            + ":"
            + digest(
                concatenate(
                    frame("canonical-standalone-json-artifact-id-v1"),
                    frame(schemaVersion),
                    frame(artifactType),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray()))));
  }

  private static AnalysisStepPublicationReference analysisStepReference(
      AnalysisRunId runId, AnalysisStepKey key, String value) {
    return new AnalysisStepPublicationReference(
        new AnalysisStepPublicationAddress(runId, key),
        AnalysisStepArtifactRoot.parse("analysis-step-root:" + digest(value + "-root")),
        AnalysisStepReceiptId.parse("analysis-step-receipt:" + digest(value + "-receipt")),
        new Sha256Digest(digest(value + "-publication")));
  }

  private static ArtifactControls controls() {
    return new ArtifactControls(
        new Sha256Digest(digest("toolchain")),
        new Sha256Digest(digest("profile")),
        new Sha256Digest(digest("schema")),
        null,
        new ArtifactPolicyRegistryReference(
            id("artifact-policy-registry", "graph-input"), new Sha256Digest(digest("policy"))));
  }

  private static ArtifactReference reference(String prefix, String value) {
    return new ArtifactReference(id(prefix, value), new Sha256Digest(digest(value)));
  }

  private static ArtifactId id(String prefix, String value) {
    return ArtifactId.parse(prefix + ":" + digest(value));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Integer.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... parts) {
    int length = 0;
    for (byte[] part : parts) {
      length += part.length;
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] part : parts) {
      System.arraycopy(part, 0, result, offset, part.length);
      offset += part.length;
    }
    return result;
  }

  private record Fixture(
      VerifiedSourceInventoryReference sourceReference,
      VerifiedSourceTextSet sourceTexts,
      ApplicationDiscoveryReference discoveryReference,
      ReopenedAnalysisStepPublication discoveryPublication,
      ArtifactId entryId) {}

  private record FixedStepStore(ReopenedAnalysisStepPublication publication)
      implements CanonicalAnalysisStepArtifactStore {

    @Override
    public InstalledAnalysisStepPublication install(AnalysisStepInstallRequest request) {
      throw new UnsupportedOperationException("test reader never installs analysis steps");
    }

    @Override
    public ReopenedAnalysisStepPublication reopen(AnalysisStepPublicationReference reference) {
      assertThat(reference).isEqualTo(publication.reference());
      return publication;
    }
  }
}
