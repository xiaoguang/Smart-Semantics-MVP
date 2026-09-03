package org.sourceanalysis.app.analysis.graph;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Fresh-reopens M5 evidence only against the exact M1--M4 graph lineage it supports. */
public final class PersistedEvidenceGraphReader {

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public PersistedEvidenceGraphReader(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  /**
   * Rejects altered evidence bytes, predecessor identity, or source/provenance drift before M6 use.
   */
  public ReopenedEvidenceGraph reopen(
      EvidenceGraphDraftReference reference,
      ReopenedProgramGraphInputs inputs,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls,
      ReopenedControlFlowGraph controlFlow,
      ReopenedDataFlowGraph dataFlow,
      ArtifactReference expectedGraphProfileRef) {
    try {
      Objects.requireNonNull(reference, "evidence graph reference");
      Objects.requireNonNull(inputs, "reopened program graph inputs");
      Objects.requireNonNull(structure, "reopened code structure graph");
      Objects.requireNonNull(calls, "reopened call graph");
      Objects.requireNonNull(controlFlow, "reopened control-flow graph");
      Objects.requireNonNull(dataFlow, "reopened data-flow graph");
      Objects.requireNonNull(expectedGraphProfileRef, "expected graph profile reference");
      ProgramGraphInputBasis basis =
          ProgramGraphInputBasis.from(
              inputs.source(),
              inputs.discovery().codeStructureDiscovery(),
              expectedGraphProfileRef);
      if (!basis.equals(structure.basis())
          || !basis.equals(calls.basis())
          || !basis.equals(controlFlow.basis())
          || !basis.equals(dataFlow.basis())
          || !calls.codeStructurePayloadRef().equals(structure.payloadRef())
          || !controlFlow.codeStructurePayloadRef().equals(structure.payloadRef())
          || !controlFlow.callGraphPayloadRef().equals(calls.payloadRef())
          || !dataFlow.codeStructurePayloadRef().equals(structure.payloadRef())
          || !dataFlow.callGraphPayloadRef().equals(calls.payloadRef())
          || !dataFlow.controlFlowPayloadRef().equals(controlFlow.payloadRef())) throw broken();
      ReopenedModulePublication publication = moduleArtifacts.reopen(reference.publication());
      requireAddress(publication, reference);
      VerifiedCanonicalPayload payload = requirePayload(publication);
      List<ArtifactReference> upstream =
          EvidenceGraphModulePublisher.expectedUpstream(
              basis,
              structure.payloadRef(),
              calls.payloadRef(),
              controlFlow.payloadRef(),
              dataFlow.payloadRef());
      if (!publication.receipt().controls().equals(basis.controls())
          || !publication.receipt().upstreamArtifacts().equals(upstream)) throw broken();
      JsonNode envelope = canonicalJson.parseCanonical(payload.canonicalUtf8());
      requireEnvelope(envelope, publication, payload, upstream, basis);
      EvidenceGraphDraft draft = EvidenceGraphWire.parse(envelope.get("payload"));
      if (!basis.snapshotId().equals(draft.snapshotId())
          || !basis.applicationProfileId().equals(draft.applicationProfileId())
          || !basis.entryIds().equals(draft.entryIds())
          || !basis.graphProfileRef().equals(draft.graphProfileRef())) throw broken();
      requireIndependentRebuild(draft, inputs, structure, calls, controlFlow, dataFlow);
      return new VerifiedReopenedEvidenceGraph(
          reference,
          new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()),
          structure.payloadRef(),
          calls.payloadRef(),
          controlFlow.payloadRef(),
          dataFlow.payloadRef(),
          draft,
          basis);
    } catch (GraphReferenceException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private static void requireAddress(
      ReopenedModulePublication publication, EvidenceGraphDraftReference reference) {
    if (!publication.reference().equals(reference.publication())
        || !publication.receipt().address().equals(reference.publication().address())
        || !"v1".equals(publication.receipt().moduleVersion())
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != 5
        || !"evidence-graph".equals(address.moduleKey())
        || publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !publication.receipt().gapRefs().isEmpty()) throw broken();
  }

  private static VerifiedCanonicalPayload requirePayload(ReopenedModulePublication publication) {
    if (publication.payloads().size() != 1) throw broken();
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!EvidenceGraphModulePublisher.FILE_NAME.equals(payload.descriptor().fileName())
        || !EvidenceGraphModulePublisher.ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !EvidenceGraphDraft.SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON
        || !publication.receipt().payloadArtifacts().equals(List.of(payload.descriptor()))) {
      throw broken();
    }
    return payload;
  }

  private static void requireEnvelope(
      JsonNode envelope,
      ReopenedModulePublication publication,
      VerifiedCanonicalPayload payload,
      List<ArtifactReference> upstream,
      ProgramGraphInputBasis basis) {
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
    if (!EvidenceGraphDraft.SCHEMA_VERSION.equals(text(envelope, "schemaVersion"))
        || !EvidenceGraphModulePublisher.ARTIFACT_TYPE.equals(text(envelope, "artifactType"))
        || !payload.descriptor().artifactId().value().equals(text(envelope, "artifactId"))
        || !references(envelope.get("upstreamArtifacts")).equals(upstream)
        || !envelope
            .get("controls")
            .equals(EvidenceGraphModulePublisher.controls(basis.controls()))) {
      throw broken();
    }
    fields(envelope.get("producer"), Set.of("address", "moduleVersion"));
    JsonNode address = envelope.get("producer").get("address");
    fields(address, Set.of("kind", "runId", "analysisStepKey", "moduleNumber", "moduleKey"));
    AnalysisStepModuleAddress expected =
        (AnalysisStepModuleAddress) publication.receipt().address();
    if (!"v1".equals(text(envelope.get("producer"), "moduleVersion"))
        || !"ANALYSIS_STEP".equals(text(address, "kind"))
        || !expected.runId().value().equals(text(address, "runId"))
        || !expected.analysisStepKey().wireValue().equals(text(address, "analysisStepKey"))
        || expected.moduleNumber() != integer(address, "moduleNumber")
        || !expected.moduleKey().equals(text(address, "moduleKey"))) throw broken();
    fields(envelope.get("completion"), Set.of("status", "gapRefs", "failureRef"));
    if (!ModuleCompletionStatus.SUCCEEDED.name().equals(text(envelope.get("completion"), "status"))
        || !strings(envelope.get("completion").get("gapRefs")).isEmpty()
        || !envelope.get("completion").get("failureRef").isNull()) throw broken();
  }

  private static void requireIndependentRebuild(
      EvidenceGraphDraft persisted,
      ReopenedProgramGraphInputs inputs,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls,
      ReopenedControlFlowGraph controlFlow,
      ReopenedDataFlowGraph dataFlow) {
    EvidenceGraphDraft rebuilt =
        new EvidenceGraphBuilder()
            .buildEvidence(
                List.of(structure.draft(), calls.draft(), controlFlow.draft(), dataFlow.draft()),
                inputs.source());
    if (!persisted.equals(rebuilt)) throw broken();
  }

  private static List<ArtifactReference> references(JsonNode values) {
    List<ArtifactReference> result = new ArrayList<>();
    objects(values).forEach(value -> result.add(reference(value)));
    return List.copyOf(result);
  }

  private static ArtifactReference reference(JsonNode value) {
    fields(value, Set.of("artifactId", "sha256"));
    return new ArtifactReference(
        org.sourceanalysis.app.artifact.ArtifactId.parse(text(value, "artifactId")),
        new org.sourceanalysis.app.artifact.Sha256Digest(text(value, "sha256")));
  }

  private static List<String> strings(JsonNode values) {
    if (values == null || !values.isArray()) throw broken();
    List<String> result = new ArrayList<>();
    values.forEach(value -> result.add(text(value)));
    return List.copyOf(result);
  }

  private static List<JsonNode> objects(JsonNode values) {
    if (values == null || !values.isArray()) throw broken();
    List<JsonNode> result = new ArrayList<>();
    values.forEach(
        value -> {
          if (!value.isObject()) throw broken();
          result.add(value);
        });
    return List.copyOf(result);
  }

  private static String text(JsonNode value, String field) {
    return text(value.get(field));
  }

  private static String text(JsonNode value) {
    if (value == null || !value.isTextual()) throw broken();
    return value.textValue();
  }

  private static int integer(JsonNode value, String field) {
    JsonNode item = value.get(field);
    if (item == null || !item.canConvertToInt()) throw broken();
    return item.intValue();
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

  static final class VerifiedReopenedEvidenceGraph implements ReopenedEvidenceGraph {
    private final EvidenceGraphDraftReference reference;
    private final ArtifactReference payloadRef;
    private final ArtifactReference codeStructurePayloadRef;
    private final ArtifactReference callGraphPayloadRef;
    private final ArtifactReference controlFlowPayloadRef;
    private final ArtifactReference dataFlowPayloadRef;
    private final EvidenceGraphDraft draft;
    private final ProgramGraphInputBasis basis;

    private VerifiedReopenedEvidenceGraph(
        EvidenceGraphDraftReference reference,
        ArtifactReference payloadRef,
        ArtifactReference codeStructurePayloadRef,
        ArtifactReference callGraphPayloadRef,
        ArtifactReference controlFlowPayloadRef,
        ArtifactReference dataFlowPayloadRef,
        EvidenceGraphDraft draft,
        ProgramGraphInputBasis basis) {
      this.reference = reference;
      this.payloadRef = payloadRef;
      this.codeStructurePayloadRef = codeStructurePayloadRef;
      this.callGraphPayloadRef = callGraphPayloadRef;
      this.controlFlowPayloadRef = controlFlowPayloadRef;
      this.dataFlowPayloadRef = dataFlowPayloadRef;
      this.draft = draft;
      this.basis = basis;
    }

    @Override
    public EvidenceGraphDraftReference reference() {
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
    public ArtifactReference callGraphPayloadRef() {
      return callGraphPayloadRef;
    }

    @Override
    public ArtifactReference controlFlowPayloadRef() {
      return controlFlowPayloadRef;
    }

    @Override
    public ArtifactReference dataFlowPayloadRef() {
      return dataFlowPayloadRef;
    }

    @Override
    public EvidenceGraphDraft draft() {
      return draft;
    }

    @Override
    public ProgramGraphInputBasis basis() {
      return basis;
    }
  }
}
