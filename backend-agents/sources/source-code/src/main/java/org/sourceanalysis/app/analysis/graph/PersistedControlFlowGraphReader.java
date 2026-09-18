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

/** Fresh-reopens the sealed M3 graph only against its same frozen M1/M2 predecessors. */
public final class PersistedControlFlowGraphReader {

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  public PersistedControlFlowGraphReader(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
  }

  public ReopenedControlFlowGraph reopen(
      ControlFlowGraphDraftReference reference,
      ReopenedProgramGraphInputs inputs,
      ReopenedCodeStructureGraph structure,
      ReopenedCallGraph calls,
      ArtifactReference expectedGraphProfileRef) {
    try {
      Objects.requireNonNull(reference, "control-flow graph reference");
      Objects.requireNonNull(inputs, "reopened program graph inputs");
      Objects.requireNonNull(structure, "reopened code structure graph");
      Objects.requireNonNull(calls, "reopened call graph");
      Objects.requireNonNull(expectedGraphProfileRef, "expected graph profile reference");
      ProgramGraphInputBasis basis =
          ProgramGraphInputBasis.from(
              inputs.source(),
              inputs.discovery().codeStructureDiscovery(),
              expectedGraphProfileRef);
      if (!basis.equals(structure.basis())
          || !basis.equals(calls.basis())
          || !calls.codeStructurePayloadRef().equals(structure.payloadRef())) throw broken();
      ReopenedModulePublication publication = moduleArtifacts.reopen(reference.publication());
      requireAddress(publication, reference);
      VerifiedCanonicalPayload payload = requirePayload(publication);
      List<ArtifactReference> upstream =
          HistoricalProgramGraphPayloads.controlFlowUpstream(
              basis, structure.payloadRef(), calls.payloadRef());
      if (!publication.receipt().controls().equals(basis.controls())
          || !publication.receipt().upstreamArtifacts().equals(upstream)) throw broken();
      JsonNode envelope = canonicalJson.parseCanonical(payload.canonicalUtf8());
      requireEnvelope(envelope, publication, payload, upstream, basis);
      ControlFlowGraphDraft draft = ControlFlowGraphWire.parse(envelope.get("payload"));
      if (!basis.snapshotId().equals(draft.snapshotId())
          || !basis.applicationProfileId().equals(draft.applicationProfileId())
          || !basis.entryIds().equals(draft.entryIds())
          || !basis.graphProfileRef().equals(draft.graphProfileRef())
          || !publication
              .receipt()
              .gapRefs()
              .equals(HistoricalProgramGraphPayloads.gapReferences(draft))) {
        throw broken();
      }
      return new VerifiedReopenedControlFlowGraph(
          reference,
          new ArtifactReference(payload.descriptor().artifactId(), payload.descriptor().sha256()),
          structure.payloadRef(),
          calls.payloadRef(),
          draft,
          basis);
    } catch (GraphReferenceException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw broken();
    }
  }

  private static void requireAddress(
      ReopenedModulePublication publication, ControlFlowGraphDraftReference reference) {
    if (!publication.reference().equals(reference.publication())
        || !publication.receipt().address().equals(reference.publication().address())
        || !publication.receipt().moduleVersion().equals("v1")
        || !(publication.receipt().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || address.moduleNumber() != 3
        || !"control-flow".equals(address.moduleKey())
        || (publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
            && publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED_WITH_GAPS))
      throw broken();
  }

  private static VerifiedCanonicalPayload requirePayload(ReopenedModulePublication publication) {
    if (publication.payloads().size() != 1) throw broken();
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!HistoricalProgramGraphPayloads.CONTROL_FLOW_FILE.equals(payload.descriptor().fileName())
        || !HistoricalProgramGraphPayloads.CONTROL_FLOW_TYPE.equals(
            payload.descriptor().artifactType())
        || !ControlFlowGraphDraft.SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON
        || !publication.receipt().payloadArtifacts().equals(List.of(payload.descriptor())))
      throw broken();
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
    if (!ControlFlowGraphDraft.SCHEMA_VERSION.equals(text(envelope, "schemaVersion"))
        || !HistoricalProgramGraphPayloads.CONTROL_FLOW_TYPE.equals(text(envelope, "artifactType"))
        || !payload.descriptor().artifactId().value().equals(text(envelope, "artifactId"))
        || !references(envelope.get("upstreamArtifacts")).equals(upstream)
        || !envelope
            .get("controls")
            .equals(HistoricalProgramGraphPayloads.controls(basis.controls()))) throw broken();
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
    if (!publication.receipt().status().name().equals(text(envelope.get("completion"), "status"))
        || !strings(envelope.get("completion").get("gapRefs"))
            .equals(publication.receipt().gapRefs())
        || !envelope.get("completion").get("failureRef").isNull()) throw broken();
  }

  private static List<ArtifactReference> references(JsonNode values) {
    List<ArtifactReference> result = new ArrayList<>();
    objects(values).forEach(item -> result.add(ControlFlowGraphWire.reference(item)));
    return List.copyOf(result);
  }

  private static List<String> strings(JsonNode values) {
    if (values == null || !values.isArray()) throw broken();
    List<String> result = new ArrayList<>();
    values.forEach(item -> result.add(text(item)));
    return List.copyOf(result);
  }

  private static List<JsonNode> objects(JsonNode values) {
    if (values == null || !values.isArray()) throw broken();
    List<JsonNode> result = new ArrayList<>();
    values.forEach(
        item -> {
          if (!item.isObject()) throw broken();
          result.add(item);
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

  static final class VerifiedReopenedControlFlowGraph implements ReopenedControlFlowGraph {
    private final ControlFlowGraphDraftReference reference;
    private final ArtifactReference payloadRef;
    private final ArtifactReference codeStructurePayloadRef;
    private final ArtifactReference callGraphPayloadRef;
    private final ControlFlowGraphDraft draft;
    private final ProgramGraphInputBasis basis;

    private VerifiedReopenedControlFlowGraph(
        ControlFlowGraphDraftReference reference,
        ArtifactReference payloadRef,
        ArtifactReference codeStructurePayloadRef,
        ArtifactReference callGraphPayloadRef,
        ControlFlowGraphDraft draft,
        ProgramGraphInputBasis basis) {
      this.reference = reference;
      this.payloadRef = payloadRef;
      this.codeStructurePayloadRef = codeStructurePayloadRef;
      this.callGraphPayloadRef = callGraphPayloadRef;
      this.draft = draft;
      this.basis = basis;
    }

    @Override
    public ControlFlowGraphDraftReference reference() {
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
    public ControlFlowGraphDraft draft() {
      return draft;
    }

    @Override
    public ProgramGraphInputBasis basis() {
      return basis;
    }
  }
}
