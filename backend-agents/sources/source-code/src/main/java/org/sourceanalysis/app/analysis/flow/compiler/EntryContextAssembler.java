package org.sourceanalysis.app.analysis.flow.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndexReader;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.fact.publish.ProvenCodeFactsReference;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalAnalysisStepArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ReopenedAnalysisStepPublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Assembles Step05 entry contexts from the persisted neutral Java index without navigation. */
public final class EntryContextAssembler {

  private static final Comparator<String> UTF8_ORDER =
      Comparator.comparing(
          value -> value.getBytes(StandardCharsets.UTF_8), EntryContextAssembler::compare);
  private final CanonicalAnalysisStepArtifactStore steps;
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();

  public EntryContextAssembler(CanonicalAnalysisStepArtifactStore steps) {
    this.steps = Objects.requireNonNull(steps, "analysis step artifact store");
  }

  /**
   * Accounts for every discovered entry. Collected source becomes a context-only disposition;
   * navigation failures become explicit entry Gaps. No strict Flow or Proof is fabricated.
   */
  public FlowCompilation assemble(
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts,
      FlowCompilationProfile profile) {
    Objects.requireNonNull(discovery, "application discovery");
    Objects.requireNonNull(graphs, "program graphs");
    Objects.requireNonNull(facts, "proven code facts");
    Objects.requireNonNull(profile, "flow compilation profile");
    JavaCodeIndex index = new JavaCodeIndexReader(steps).reopen(graphs);
    requireAccountingOnlyLineage(discovery, graphs, facts);
    if (index.entries().size() > profile.maxFlows()) {
      throw new IllegalArgumentException("BUSINESS_FLOWS_RESOURCE_LIMIT_EXCEEDED");
    }

    List<FlowCompilation.EntryDisposition> dispositions = new ArrayList<>();
    List<FlowCompilation.EntryContext> contexts = new ArrayList<>();
    List<FlowCompilation.FlowGap> gaps = new ArrayList<>();
    for (JavaCodeIndex.EntryCollection entry : index.entries()) {
      if (entry.context() != null) {
        dispositions.add(
            new FlowCompilation.EntryDisposition(
                entry.seed().entryId(),
                "CONTEXT_ONLY",
                null,
                List.of(),
                "STRICT_FLOW_NOT_PRODUCED"));
        contexts.add(
            new FlowCompilation.EntryContext(
                contextId(entry),
                entry.seed().entryId(),
                null,
                entry.seed().trigger(),
                "COLLECTED",
                null,
                entry.context(),
                null,
                List.of(),
                List.of(),
                entry.context().limitations().stream()
                    .map(value -> value.code() + ":" + value.detail())
                    .sorted(UTF8_ORDER)
                    .toList()));
      } else {
        String reason = entry.reason();
        String gapId = contentId("flow-gap", entry.seed().entryId(), reason);
        gaps.add(
            new FlowCompilation.FlowGap(
                gapId, "ENTRY", reason, List.of(entry.seed().entryId()), List.of()));
        dispositions.add(
            new FlowCompilation.EntryDisposition(
                entry.seed().entryId(), "GAP", null, List.of(gapId), reason));
        contexts.add(
            new FlowCompilation.EntryContext(
                contextId(entry),
                entry.seed().entryId(),
                null,
                entry.seed().trigger(),
                "NOT_COLLECTED",
                reason,
                null,
                null,
                List.of(),
                List.of(gapId),
                List.of(reason)));
      }
    }
    return new FlowCompilation(profile, dispositions, List.of(), contexts, gaps);
  }

  private void requireAccountingOnlyLineage(
      ApplicationDiscoveryReference discovery,
      ProgramGraphsReference graphs,
      ProvenCodeFactsReference facts) {
    ReopenedAnalysisStepPublication discoveryStep = steps.reopen(discovery.publication());
    ReopenedAnalysisStepPublication graphStep = steps.reopen(graphs.publication());
    ReopenedAnalysisStepPublication factStep = steps.reopen(facts.publication());
    if (discoveryStep.reference().address().analysisStepKey()
            != AnalysisStepKey.APPLICATION_DISCOVERY
        || graphStep.reference().address().analysisStepKey() != AnalysisStepKey.PROGRAM_GRAPHS
        || factStep.reference().address().analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
        || !discoveryStep
            .reference()
            .address()
            .runId()
            .equals(graphStep.reference().address().runId())
        || !discoveryStep
            .reference()
            .address()
            .runId()
            .equals(factStep.reference().address().runId())
        || !discoveryStep.receipt().controls().equals(graphStep.receipt().controls())
        || !discoveryStep.receipt().controls().equals(factStep.receipt().controls())
        || !factStep.receipt().upstreamAnalysisStepReferences().contains(graphStep.reference())
        || factStep.semanticPayloads().size() != 1) {
      throw invalid();
    }
    VerifiedCanonicalPayload accounting = factStep.semanticPayloads().get(0);
    if (!"fact-accounting.json".equals(accounting.descriptor().fileName())
        || !"PROVEN_CODE_FACTS_FACT_ACCOUNTING".equals(accounting.descriptor().artifactType())
        || !"proven-code-facts-fact-accounting-v4"
            .equals(accounting.descriptor().schemaVersion())) {
      throw invalid();
    }
    JsonNode document = json.parseCanonical(accounting.canonicalUtf8());
    ArtifactReference indexRef =
        new ArtifactReference(
            graphStep.semanticPayloads().get(0).descriptor().artifactId(),
            graphStep.semanticPayloads().get(0).descriptor().sha256());
    if (!"NOT_PRODUCED".equals(document.path("availability").textValue())
        || !indexRef
            .artifactId()
            .value()
            .equals(document.path("navigationInputRef").path("artifactId").textValue())
        || !indexRef
            .sha256()
            .value()
            .equals(document.path("navigationInputRef").path("sha256").textValue())) {
      throw invalid();
    }
  }

  private static String contextId(JavaCodeIndex.EntryCollection entry) {
    return contentId(
        "entry-context",
        entry.seed().entryId(),
        entry.seed().methodKey(),
        entry.collectionStatus(),
        entry.reason() == null ? "" : entry.reason());
  }

  private static String contentId(String prefix, String... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      digest.update(frame(prefix));
      for (String value : values) digest.update(frame(value));
      return prefix + ":" + HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static byte[] frame(String value) {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    return ByteBuffer.allocate(Long.BYTES + bytes.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(bytes.length)
        .put(bytes)
        .array();
  }

  private static int compare(byte[] left, byte[] right) {
    return java.util.Arrays.compareUnsigned(left, right);
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("BUSINESS_FLOWS_NAVIGATION_INPUT_INVALID");
  }
}
