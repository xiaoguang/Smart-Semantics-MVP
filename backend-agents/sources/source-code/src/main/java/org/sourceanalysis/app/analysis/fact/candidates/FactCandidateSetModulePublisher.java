package org.sourceanalysis.app.analysis.fact.candidates;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;

/** Installs M1's complete immutable Fact-candidate denominator as one canonical module artifact. */
public final class FactCandidateSetModulePublisher {

  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-fact-candidate-set-v2";
  private static final String ARTIFACT_PREFIX = "proven-code-facts-fact-candidate-set";
  private static final String MODULE_VERSION = "v2";
  private static final String FILE_NAME = "fact-candidate-set.json";

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the publisher with the one permitted module-artifact storage dependency. */
  public FactCandidateSetModulePublisher(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifact store");
    canonicalJson = new CanonicalJsonCodec();
  }

  /**
   * Installs and independently fresh-reopens the one complete candidate-set publication.
   *
   * <p>The fresh-reopened typed input supplies the closed seven-artifact upstream set and matching
   * controls. Callers cannot replace either set. This seam checks the candidate set's five graph
   * roots agree with that input, but it neither rebuilds graphs nor selects any Proof.
   */
  public ModulePublicationReference publish(
      AnalysisStepModuleAddress destination,
      FactCandidateInputs inputs,
      FactCandidateSet candidateSet) {
    requireDestination(destination);
    Objects.requireNonNull(inputs, "fact candidate inputs");
    Objects.requireNonNull(candidateSet, "fact candidate set");
    if (!candidateSet.sourceGraphRoots().equals(inputs.sourceGraphRoots())) {
      throw new FactCandidateReferenceException();
    }
    List<ArtifactReference> upstream = inputs.candidateModuleUpstreamArtifacts();
    CanonicalModulePayload payload =
        payload(destination, candidateSet, upstream, inputs.controls());
    InstalledModulePublication installed =
        moduleArtifacts.install(
            new ModuleInstallRequest(
                destination,
                MODULE_VERSION,
                upstream,
                inputs.controls(),
                ModuleCompletionStatus.SUCCEEDED,
                List.of(),
                List.of(payload)));
    ModulePublicationReference reference = installed.reference();
    ReopenedModulePublication reopened = moduleArtifacts.reopen(reference);
    if (!reference.equals(reopened.reference())) {
      throw new IllegalStateException(
          "fact candidate module publication reopen identity is invalid");
    }
    return reference;
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress destination,
      FactCandidateSet candidateSet,
      List<ArtifactReference> upstream,
      ArtifactControls controls) {
    ObjectNode withoutArtifactId = JsonNodeFactory.instance.objectNode();
    withoutArtifactId.put("schemaVersion", SCHEMA_VERSION);
    withoutArtifactId.put("artifactType", ARTIFACT_TYPE);
    withoutArtifactId.set("producer", producer(destination));
    withoutArtifactId.set("upstreamArtifacts", references(upstream));
    withoutArtifactId.set("controls", controls(controls));
    withoutArtifactId.set("completion", completion());
    withoutArtifactId.set("payload", candidateSetBody(candidateSet));

    String artifactId =
        ARTIFACT_PREFIX
            + ":"
            + sha256(
                concatenate(
                    frame("canonical-module-artifact-id-v1"),
                    frame(SCHEMA_VERSION),
                    frame(ARTIFACT_TYPE),
                    frame(canonicalJson.encodeCanonical(withoutArtifactId).copyToByteArray())));
    ObjectNode envelope = withoutArtifactId.deepCopy();
    envelope.put("artifactId", artifactId);
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        SCHEMA_VERSION,
        ArtifactId.parse(artifactId),
        CanonicalMediaType.APPLICATION_JSON,
        canonicalJson.encodeCanonical(envelope));
  }

  private static ObjectNode candidateSetBody(FactCandidateSet candidateSet) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("candidateSetId", candidateSet.candidateSetId().value());
    body.set("sourceGraphRoots", references(candidateSet.sourceGraphRoots()));
    ArrayNode candidates = body.putArray("candidates");
    candidateSet.candidates().forEach(candidate -> candidates.add(candidate(candidate)));
    ArrayNode notApplicable = body.putArray("notApplicableDispositions");
    candidateSet
        .notApplicableDispositions()
        .forEach(disposition -> notApplicable.add(notApplicable(disposition)));
    ObjectNode denominator = body.putObject("denominator");
    strings(denominator.putArray("applicableKeys"), candidateSet.denominator().applicableKeys());
    strings(
        denominator.putArray("notApplicableKeys"), candidateSet.denominator().notApplicableKeys());
    return body;
  }

  private static ObjectNode candidate(FactCandidateSet.FactCandidate candidate) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("candidateFactKey", candidate.candidateFactKey());
    body.put("entryId", candidate.entryId());
    body.put("kind", candidate.kind());
    if ("JAVA_BOUNDARY_INVOCATION".equals(candidate.kind())) {
      body.put("boundaryNodeId", candidate.boundaryNodeId());
      body.put("invocationCallId", candidate.invocationCallId());
      body.put("callTargetEdgeId", candidate.callTargetEdgeId());
      body.put("staticTargetType", candidate.staticTargetType());
      body.put("staticTargetMethod", candidate.staticTargetMethod());
      body.put("staticTargetSignature", candidate.staticTargetSignature());
      strings(body.putArray("orderedArgumentEdgeIds"), candidate.orderedArgumentEdgeIds());
      ArrayNode orderedArguments = body.putArray("orderedArguments");
      candidate.orderedArguments().forEach(argument -> orderedArguments.add(argument(argument)));
      body.put("controlBlockId", candidate.controlBlockId());
      if (candidate.guardId() == null) {
        body.putNull("guardId");
      } else {
        body.put("guardId", candidate.guardId());
      }
    } else {
      body.put("guardNodeId", candidate.guardNodeId());
      body.put("normalizedCondition", candidate.normalizedCondition());
      strings(body.putArray("branchEdgeIds"), candidate.branchEdgeIds());
    }
    ArrayNode evidence = body.putArray("evidenceNodeIdsBySubject");
    candidate.evidenceBySubject().forEach(binding -> evidence.add(evidence(binding)));
    ArrayNode atoms = body.putArray("requiredAtoms");
    candidate.requiredAtoms().forEach(atom -> atoms.add(atom(atom)));
    return body;
  }

  private static ObjectNode argument(FactCandidateSet.BoundaryArgumentBinding argument) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("ordinal", argument.ordinal());
    body.put("argumentNodeId", argument.argumentNodeId());
    body.put("argumentEdgeId", argument.argumentEdgeId());
    strings(body.putArray("javaLocalOriginNodeIds"), argument.javaLocalOriginNodeIds());
    return body;
  }

  private static ObjectNode evidence(FactCandidateSet.SubjectEvidenceBinding evidence) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("subjectElementId", evidence.subjectElementId());
    strings(body.putArray("sourceEvidenceNodeIds"), evidence.sourceEvidenceNodeIds());
    strings(
        body.putArray("ruleApplicationEvidenceNodeIds"), evidence.ruleApplicationEvidenceNodeIds());
    return body;
  }

  private static ObjectNode atom(FactCandidateSet.RequiredAtom atom) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("atomKey", atom.atomKey());
    body.put("role", atom.role());
    body.put("valueType", atom.valueType());
    strings(body.putArray("expectedEvidenceKinds"), atom.expectedEvidenceKinds());
    return body;
  }

  private static ObjectNode notApplicable(FactCandidateSet.NotApplicableDisposition disposition) {
    ObjectNode body = JsonNodeFactory.instance.objectNode();
    body.put("entryId", disposition.entryId());
    body.put("subjectNodeId", disposition.subjectNodeId());
    body.put("templateKey", disposition.templateKey());
    strings(body.putArray("missingRoles"), disposition.missingRoles());
    body.put("reasonCode", disposition.reasonCode());
    return body;
  }

  private static ObjectNode producer(AnalysisStepModuleAddress destination) {
    ObjectNode producer = JsonNodeFactory.instance.objectNode();
    ObjectNode address = producer.putObject("address");
    address.put("kind", "ANALYSIS_STEP");
    address.put("runId", destination.runId().value());
    address.put("analysisStepKey", destination.analysisStepKey().wireValue());
    address.put("moduleNumber", destination.moduleNumber());
    address.put("moduleKey", destination.moduleKey());
    producer.put("moduleVersion", MODULE_VERSION);
    return producer;
  }

  private static ArrayNode references(List<ArtifactReference> values) {
    ArrayNode result = JsonNodeFactory.instance.arrayNode();
    values.forEach(value -> result.add(reference(value)));
    return result;
  }

  private static ObjectNode reference(ArtifactReference reference) {
    return JsonNodeFactory.instance
        .objectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static ObjectNode controls(ArtifactControls controls) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("toolchainSha256", controls.toolchainSha256().value());
    value.put("profileSha256", controls.profileSha256().value());
    value.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      value.putNull("promptBundleSha256");
    } else {
      value.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    value
        .putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return value;
  }

  private static ObjectNode completion() {
    ObjectNode completion = JsonNodeFactory.instance.objectNode();
    completion.put("status", ModuleCompletionStatus.SUCCEEDED.name());
    completion.putArray("gapRefs");
    completion.putNull("failureRef");
    return completion;
  }

  private static void strings(ArrayNode destination, List<String> values) {
    values.forEach(destination::add);
  }

  private static void requireDestination(AnalysisStepModuleAddress destination) {
    if (destination == null
        || destination.analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
        || destination.moduleNumber() != 1
        || !"candidates".equals(destination.moduleKey())) {
      throw new IllegalArgumentException("fact candidate module destination is invalid");
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }
}
