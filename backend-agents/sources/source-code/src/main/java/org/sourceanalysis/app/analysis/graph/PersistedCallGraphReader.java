package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/** Reopens the only M2 module artifact that may provide call targets to M3. */
public final class PersistedCallGraphReader {

  private static final String FILE_NAME = "call-graph-draft.json";
  private static final String ARTIFACT_TYPE = "PROGRAM_GRAPHS_CALL_GRAPH_DRAFT";
  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public PersistedCallGraphReader(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /** Fresh-reopens M2 and proves it belongs to the same M1 and frozen inputs that M3 will use. */
  public ReopenedCallGraph reopen(
      CallGraphDraftReference reference,
      ReopenedProgramGraphInputs sameInputs,
      ReopenedCodeStructureGraph sameStructure,
      ArtifactReference expectedGraphProfileRef) {
    try {
      Objects.requireNonNull(reference, "call graph reference");
      Objects.requireNonNull(sameInputs, "reopened program graph inputs");
      Objects.requireNonNull(sameStructure, "reopened code structure graph");
      Objects.requireNonNull(expectedGraphProfileRef, "expected graph profile reference");
      ProgramGraphInputBasis basis =
          ProgramGraphInputBasis.from(
              sameInputs.source(),
              sameInputs.discovery().codeStructureDiscovery(),
              expectedGraphProfileRef);
      if (!sameStructure.basis().equals(basis)) {
        throw broken();
      }
      ReopenedModulePublication publication = moduleArtifacts.reopen(reference.publication());
      requireAddress(publication, reference);
      VerifiedCanonicalPayload payload = requirePayload(publication);
      requireReceiptLineage(publication, basis, sameStructure.payloadRef());
      JsonNode envelope = canonicalJson.parseCanonical(payload.canonicalUtf8());
      requireEnvelope(envelope, publication, payload, basis, sameStructure.payloadRef());
      CallGraphDraft draft = parseDraft(envelope.get("payload"));
      if (!draft.snapshotId().equals(basis.snapshotId())
          || !draft.applicationProfileId().equals(basis.applicationProfileId())
          || !draft.entryIds().equals(basis.entryIds())
          || !draft.graphProfileRef().equals(basis.graphProfileRef())
          || !publication
              .receipt()
              .gapRefs()
              .equals(HistoricalProgramGraphPayloads.gapReferences(draft))
          || publication.receipt().status()
              != (publication.receipt().gapRefs().isEmpty()
                  ? ModuleCompletionStatus.SUCCEEDED
                  : ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)) {
        throw broken();
      }
      return new VerifiedReopenedCallGraph(
          reference,
          new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()),
          sameStructure.payloadRef(),
          draft,
          basis);
    } catch (GraphReferenceException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private static void requireAddress(
      ReopenedModulePublication publication, CallGraphDraftReference reference) {
    if (!publication.reference().equals(reference.publication())
        || !publication.receipt().address().equals(reference.publication().address())
        || !publication.receipt().moduleVersion().equals("v1")
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != 2
        || !address.moduleKey().equals("call-graph")
        || (publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
            && publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED_WITH_GAPS)) {
      throw broken();
    }
  }

  private static VerifiedCanonicalPayload requirePayload(ReopenedModulePublication publication) {
    if (publication.payloads().size() != 1) throw broken();
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!payload.descriptor().fileName().equals(FILE_NAME)
        || !payload.descriptor().artifactType().equals(ARTIFACT_TYPE)
        || !payload.descriptor().schemaVersion().equals(CallGraphDraft.SCHEMA_VERSION)
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON
        || !publication.receipt().payloadArtifacts().equals(List.of(payload.descriptor())))
      throw broken();
    return payload;
  }

  private static void requireReceiptLineage(
      ReopenedModulePublication publication,
      ProgramGraphInputBasis basis,
      ArtifactReference structurePayload) {
    if (!publication.receipt().controls().equals(basis.controls())
        || !publication
            .receipt()
            .upstreamArtifacts()
            .equals(expectedUpstream(basis, structurePayload))) {
      throw broken();
    }
  }

  private static void requireEnvelope(
      JsonNode envelope,
      ReopenedModulePublication publication,
      VerifiedCanonicalPayload payload,
      ProgramGraphInputBasis basis,
      ArtifactReference structurePayload) {
    fields(
        envelope,
        Set.of(
            "schemaVersion",
            "artifactType",
            "artifactId",
            "producer",
            "upstreamArtifacts",
            "controls",
            "completion",
            "payload"));
    if (!text(envelope, "schemaVersion").equals(CallGraphDraft.SCHEMA_VERSION)
        || !text(envelope, "artifactType").equals(ARTIFACT_TYPE)
        || !text(envelope, "artifactId").equals(payload.descriptor().artifactId().value())
        || !references(envelope.get("upstreamArtifacts"))
            .equals(expectedUpstream(basis, structurePayload))
        || !controls(envelope.get("controls")).equals(basis.controls())) throw broken();
    fields(envelope.get("producer"), Set.of("address", "moduleVersion"));
    JsonNode address = envelope.get("producer").get("address");
    fields(address, Set.of("kind", "runId", "analysisStepKey", "moduleNumber", "moduleKey"));
    AnalysisStepModuleAddress expected =
        (AnalysisStepModuleAddress) publication.receipt().address();
    if (!text(envelope.get("producer"), "moduleVersion").equals("v1")
        || !text(address, "kind").equals("ANALYSIS_STEP")
        || !text(address, "runId").equals(expected.runId().value())
        || !text(address, "analysisStepKey").equals(expected.analysisStepKey().wireValue())
        || integer(address, "moduleNumber") != expected.moduleNumber()
        || !text(address, "moduleKey").equals(expected.moduleKey())) throw broken();
    fields(envelope.get("completion"), Set.of("status", "gapRefs", "failureRef"));
    if (!text(envelope.get("completion"), "status").equals(publication.receipt().status().name())
        || !strings(envelope.get("completion").get("gapRefs"))
            .equals(publication.receipt().gapRefs())
        || !envelope.get("completion").get("failureRef").isNull()) throw broken();
  }

  private static CallGraphDraft parseDraft(JsonNode body) {
    fields(
        body,
        Set.of(
            "graphKind",
            "graphId",
            "snapshotId",
            "applicationProfileId",
            "graphProfileRef",
            "entryIds",
            "nodes",
            "edges",
            "gapDrafts",
            "provenanceDrafts",
            "coverage"));
    return new CallGraphDraft(
        CallGraphDraft.SCHEMA_VERSION,
        ProgramGraphKind.valueOf(text(body, "graphKind")),
        id(body, "graphId"),
        text(body, "snapshotId"),
        id(body, "applicationProfileId"),
        reference(body.get("graphProfileRef")),
        ids(body.get("entryIds")),
        nodes(body.get("nodes")),
        edges(body.get("edges")),
        gaps(body.get("gapDrafts")),
        provenance(body.get("provenanceDrafts")),
        coverage(body.get("coverage")));
  }

  private static List<GraphGapDraft> gaps(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "gapId",
                      "reasonCode",
                      "affectedEntryIds",
                      "candidateElementIds",
                      "sourceLocator"));
              return new GraphGapDraft(
                  id(value, "gapId"),
                  text(value, "reasonCode"),
                  ids(value.get("affectedEntryIds")),
                  ids(value.get("candidateElementIds")),
                  locator(value.get("sourceLocator")));
            })
        .toList();
  }

  private static List<CallGraphNode> nodes(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "nodeId", "kind", "canonicalValue", "owningEntryIds", "evidenceDraftRefs"));
              return new CallGraphNode(
                  id(value, "nodeId"),
                  CallGraphNodeKind.valueOf(text(value, "kind")),
                  text(value, "canonicalValue"),
                  ids(value.get("owningEntryIds")),
                  ids(value.get("evidenceDraftRefs")));
            })
        .toList();
  }

  private static List<CallGraphEdge> edges(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "edgeId",
                      "kind",
                      "fromNodeId",
                      "toNodeId",
                      "ruleId",
                      "resolution",
                      "evidenceDraftRefs"));
              return new CallGraphEdge(
                  id(value, "edgeId"),
                  CallGraphEdgeKind.valueOf(text(value, "kind")),
                  id(value, "fromNodeId"),
                  id(value, "toNodeId"),
                  text(value, "ruleId"),
                  ProgramResolution.valueOf(text(value, "resolution")),
                  ids(value.get("evidenceDraftRefs")));
            })
        .toList();
  }

  private static List<ProvenanceDraftV1> provenance(JsonNode values) {
    return objects(values).stream()
        .map(
            value -> {
              fields(
                  value,
                  Set.of(
                      "provenanceDraftId",
                      "ruleId",
                      "sourceLocator",
                      "sourceFileSha256",
                      "excerptSha256"));
              return new ProvenanceDraftV1(
                  id(value, "provenanceDraftId"),
                  text(value, "ruleId"),
                  locator(value.get("sourceLocator")),
                  new Sha256Digest(text(value, "sourceFileSha256")),
                  new Sha256Digest(text(value, "excerptSha256")));
            })
        .toList();
  }

  private static GraphCoverage coverage(JsonNode value) {
    fields(
        value,
        Set.of(
            "candidateElementIds",
            "exactElementIds",
            "gapDispositions",
            "exclusionDispositions",
            "scopeGapIds",
            "closed"));
    List<GraphGapDisposition> gaps =
        objects(value.get("gapDispositions")).stream()
            .map(
                v -> {
                  fields(v, Set.of("candidateElementId", "gapId"));
                  return new GraphGapDisposition(id(v, "candidateElementId"), id(v, "gapId"));
                })
            .toList();
    List<GraphExclusionDisposition> exclusions =
        objects(value.get("exclusionDispositions")).stream()
            .map(
                v -> {
                  fields(v, Set.of("candidateElementId", "reasonCode", "evidenceDraftRefs"));
                  return new GraphExclusionDisposition(
                      id(v, "candidateElementId"),
                      text(v, "reasonCode"),
                      ids(v.get("evidenceDraftRefs")));
                })
            .toList();
    if (!value.get("closed").isBoolean()) throw broken();
    return new GraphCoverage(
        ids(value.get("candidateElementIds")),
        ids(value.get("exactElementIds")),
        gaps,
        exclusions,
        ids(value.get("scopeGapIds")),
        value.get("closed").booleanValue());
  }

  private static SourceLocatorV1 locator(JsonNode value) {
    fields(
        value,
        Set.of(
            "fileId",
            "path",
            "startByte",
            "endByteExclusive",
            "startLine",
            "startColumn",
            "endLine",
            "endColumn"));
    return new SourceLocatorV1(
        id(value, "fileId"),
        text(value, "path"),
        longValue(value, "startByte"),
        longValue(value, "endByteExclusive"),
        integer(value, "startLine"),
        integer(value, "startColumn"),
        integer(value, "endLine"),
        integer(value, "endColumn"));
  }

  private static ArtifactControls controls(JsonNode value) {
    fields(
        value,
        Set.of(
            "toolchainSha256",
            "profileSha256",
            "schemaBundleSha256",
            "promptBundleSha256",
            "artifactPolicyRegistryRef"));
    JsonNode prompt = value.get("promptBundleSha256");
    if (!(prompt.isNull() || prompt.isTextual())) throw broken();
    JsonNode policy = value.get("artifactPolicyRegistryRef");
    fields(policy, Set.of("artifactId", "sha256"));
    return new ArtifactControls(
        new Sha256Digest(text(value, "toolchainSha256")),
        new Sha256Digest(text(value, "profileSha256")),
        new Sha256Digest(text(value, "schemaBundleSha256")),
        prompt.isNull() ? null : new Sha256Digest(text(prompt)),
        new ArtifactPolicyRegistryReference(
            id(policy, "artifactId"), new Sha256Digest(text(policy, "sha256"))));
  }

  private static List<ArtifactReference> expectedUpstream(
      ProgramGraphInputBasis basis, ArtifactReference structurePayload) {
    List<ArtifactReference> values =
        new ArrayList<>(
            List.of(
                basis.sourceInventoryRef(),
                basis.verifiedSnapshotRef(),
                basis.applicationProfileRef(),
                basis.capabilityReportRef(),
                basis.entryPointsRef(),
                basis.mapperCatalogRef(),
                basis.graphProfileRef(),
                structurePayload));
    values.sort(Comparator.comparing(value -> value.artifactId().value()));
    return List.copyOf(values);
  }

  private static List<ArtifactReference> references(JsonNode value) {
    return objects(value).stream().map(PersistedCallGraphReader::reference).toList();
  }

  private static ArtifactReference reference(JsonNode value) {
    fields(value, Set.of("artifactId", "sha256"));
    return new ArtifactReference(id(value, "artifactId"), new Sha256Digest(text(value, "sha256")));
  }

  private static List<ArtifactId> ids(JsonNode value) {
    if (value == null || !value.isArray()) throw broken();
    List<ArtifactId> result = new ArrayList<>();
    value.forEach(item -> result.add(ArtifactId.parse(text(item))));
    return List.copyOf(result);
  }

  private static List<String> strings(JsonNode value) {
    if (value == null || !value.isArray()) throw broken();
    List<String> result = new ArrayList<>();
    value.forEach(item -> result.add(text(item)));
    return List.copyOf(result);
  }

  private static List<JsonNode> objects(JsonNode value) {
    if (value == null || !value.isArray()) throw broken();
    List<JsonNode> result = new ArrayList<>();
    value.forEach(
        item -> {
          if (!item.isObject()) throw broken();
          result.add(item);
        });
    return List.copyOf(result);
  }

  private static ArtifactId id(JsonNode value, String field) {
    return ArtifactId.parse(text(value, field));
  }

  private static String text(JsonNode value, String field) {
    return text(value.get(field));
  }

  private static String text(JsonNode value) {
    if (value == null || !value.isTextual()) throw broken();
    return value.textValue();
  }

  private static int integer(JsonNode value, String field) {
    JsonNode node = value.get(field);
    if (node == null || !node.canConvertToInt()) throw broken();
    return node.intValue();
  }

  private static long longValue(JsonNode value, String field) {
    JsonNode node = value.get(field);
    if (node == null || !node.canConvertToLong()) throw broken();
    return node.longValue();
  }

  private static void fields(JsonNode value, Set<String> expected) {
    if (value == null || !value.isObject()) throw broken();
    Set<String> actual = new LinkedHashSet<>();
    Iterator<String> iterator = value.fieldNames();
    iterator.forEachRemaining(actual::add);
    if (!actual.equals(expected)) throw broken();
  }

  private static GraphReferenceException broken() {
    return new GraphReferenceException();
  }

  static final class VerifiedReopenedCallGraph implements ReopenedCallGraph {
    private final CallGraphDraftReference reference;
    private final ArtifactReference payloadRef;
    private final ArtifactReference codeStructurePayloadRef;
    private final CallGraphDraft draft;
    private final ProgramGraphInputBasis basis;

    private VerifiedReopenedCallGraph(
        CallGraphDraftReference reference,
        ArtifactReference payloadRef,
        ArtifactReference codeStructurePayloadRef,
        CallGraphDraft draft,
        ProgramGraphInputBasis basis) {
      this.reference = reference;
      this.payloadRef = payloadRef;
      this.codeStructurePayloadRef = codeStructurePayloadRef;
      this.draft = draft;
      this.basis = basis;
    }

    @Override
    public CallGraphDraftReference reference() {
      return reference;
    }

    @Override
    public ArtifactReference payloadRef() {
      return payloadRef;
    }

    @Override
    public ArtifactReference codeStructurePayloadRef() {
      return codeStructurePayloadRef;
    }

    @Override
    public CallGraphDraft draft() {
      return draft;
    }

    @Override
    public ProgramGraphInputBasis basis() {
      return basis;
    }
  }
}
