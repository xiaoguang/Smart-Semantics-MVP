package org.sourceanalysis.app.analysis.fact.candidates;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
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
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/**
 * Fresh-reopens M1's only candidate-set module artifact and validates its persisted basis, shape,
 * and canonical publication identity.
 *
 * <p>The normal seam deliberately accepts neither paths nor untyped JSON. The module store first
 * validates the receipt-last publication; this reader then validates the M1-specific envelope and
 * its saved graph roots. Explicit audit callers can additionally re-enumerate the denominator.
 */
public final class PersistedFactCandidateSetReader {

  private static final String ARTIFACT_TYPE = "PROVEN_CODE_FACTS_FACT_CANDIDATE_SET";
  private static final String SCHEMA_VERSION = "proven-code-facts-fact-candidate-set-v3";
  private static final String FILE_NAME = "fact-candidate-set.json";
  private static final String MODULE_VERSION = "v3";
  private static final Set<String> ENVELOPE_FIELDS =
      Set.of(
          "artifactId",
          "artifactType",
          "completion",
          "controls",
          "payload",
          "producer",
          "schemaVersion",
          "upstreamArtifacts");
  private static final Set<String> PRODUCER_FIELDS = Set.of("address", "moduleVersion");
  private static final Set<String> PRODUCER_ADDRESS_FIELDS =
      Set.of("analysisStepKey", "kind", "moduleKey", "moduleNumber", "runId");
  private static final Set<String> REFERENCE_FIELDS = Set.of("artifactId", "sha256");
  private static final Set<String> CONTROLS_FIELDS =
      Set.of(
          "artifactPolicyRegistryRef",
          "profileSha256",
          "promptBundleSha256",
          "schemaBundleSha256",
          "toolchainSha256");
  private static final Set<String> COMPLETION_FIELDS = Set.of("failureRef", "gapRefs", "status");
  private static final Set<String> BODY_FIELDS =
      Set.of(
          "candidateSetId",
          "candidates",
          "denominator",
          "notApplicableDispositions",
          "sourceGraphRoots");
  private static final Set<String> BOUNDARY_CANDIDATE_FIELDS =
      Set.of(
          "boundaryNodeId",
          "callTargetEdgeId",
          "candidateFactKey",
          "controlBlockId",
          "entryId",
          "evidenceNodeIdsBySubject",
          "guardId",
          "invocationCallId",
          "kind",
          "orderedArgumentEdgeIds",
          "orderedArguments",
          "requiredAtoms",
          "staticTargetMethod",
          "staticTargetSignature",
          "staticTargetType");
  private static final Set<String> GUARD_CANDIDATE_FIELDS =
      Set.of(
          "branchEdgeIds",
          "candidateFactKey",
          "entryId",
          "evidenceNodeIdsBySubject",
          "guardNodeId",
          "kind",
          "normalizedCondition",
          "requiredAtoms");
  private static final Set<String> EXACT_CALL_CANDIDATE_FIELDS =
      Set.of(
          "callSiteNodeId",
          "callTargetEdgeId",
          "candidateFactKey",
          "entryId",
          "evidenceNodeIdsBySubject",
          "kind",
          "requiredAtoms",
          "targetCanonicalMethod",
          "targetMethodNodeId");
  private static final Set<String> ARGUMENT_FIELDS =
      Set.of("argumentEdgeId", "argumentNodeId", "javaLocalOriginNodeIds", "ordinal");
  private static final Set<String> EVIDENCE_FIELDS =
      Set.of("ruleApplicationEvidenceNodeIds", "sourceEvidenceNodeIds", "subjectElementId");
  private static final Set<String> ATOM_FIELDS =
      Set.of("atomKey", "expectedEvidenceKinds", "role", "valueType");
  private static final Set<String> DISPOSITION_FIELDS =
      Set.of("entryId", "missingRoles", "reasonCode", "subjectNodeId", "templateKey");
  private static final Set<String> DENOMINATOR_FIELDS =
      Set.of("applicableKeys", "notApplicableKeys");

  private final CanonicalModuleArtifactStore moduleArtifacts;
  private final CanonicalJsonCodec canonicalJson;

  /** Creates the only M1 persisted-reader seam with an explicit canonical module store. */
  public PersistedFactCandidateSetReader(CanonicalModuleArtifactStore moduleArtifacts) {
    this.moduleArtifacts = Objects.requireNonNull(moduleArtifacts, "module artifacts");
    canonicalJson = new CanonicalJsonCodec();
  }

  /** Reopens a normal M1 candidate artifact without rerunning its expensive enumeration. */
  public FactCandidateSet reopen(
      ModulePublicationReference publicationReference, FactCandidateInputs inputs) {
    try {
      if (publicationReference == null || inputs == null) {
        throw failure();
      }
      ReopenedModulePublication publication = moduleArtifacts.reopen(publicationReference);
      requirePublication(publication, publicationReference, inputs);
      return parse(requiredPayload(publication), publication, inputs);
    } catch (FactCandidateReferenceException failure) {
      throw failure;
    } catch (RuntimeException invalid) {
      throw failure();
    }
  }

  /**
   * Reopens and independently audits one M1 candidate artifact against a supplied frozen registry.
   * This costly replay is for validation and tests, not ordinary downstream consumption.
   */
  public FactCandidateSet reopen(
      ModulePublicationReference publicationReference,
      FactCandidateInputs inputs,
      FactRegistry registry) {
    try {
      if (registry == null) {
        throw failure();
      }
      FactCandidateSet persisted = reopen(publicationReference, inputs);
      FactCandidateSet expected = new FactCandidateEnumerator().enumerate(inputs, registry);
      if (!persisted.equals(expected)) {
        throw failure();
      }
      return persisted;
    } catch (FactCandidateReferenceException failure) {
      throw failure;
    } catch (RuntimeException invalid) {
      throw failure();
    }
  }

  private void requirePublication(
      ReopenedModulePublication publication,
      ModulePublicationReference requested,
      FactCandidateInputs inputs) {
    if (publication == null
        || !requested.equals(publication.reference())
        || !(publication.reference().address() instanceof AnalysisStepModuleAddress address)
        || address.analysisStepKey() != AnalysisStepKey.PROVEN_CODE_FACTS
        || address.moduleNumber() != 1
        || !"candidates".equals(address.moduleKey())
        || publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !publication.receipt().gapRefs().isEmpty()
        || !inputs.controls().equals(publication.receipt().controls())) {
      throw failure();
    }
    List<ArtifactReference> upstream = publication.receipt().upstreamArtifacts();
    if (!upstream.equals(inputs.candidateModuleUpstreamArtifacts())) {
      throw failure();
    }
  }

  private static VerifiedCanonicalPayload requiredPayload(ReopenedModulePublication publication) {
    if (publication.payloads().size() != 1
        || publication.receipt().payloadArtifacts().size() != 1) {
      throw failure();
    }
    VerifiedCanonicalPayload payload = publication.payloads().get(0);
    if (!payload.descriptor().equals(publication.receipt().payloadArtifacts().get(0))
        || !FILE_NAME.equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON) {
      throw failure();
    }
    return payload;
  }

  private FactCandidateSet parse(
      VerifiedCanonicalPayload persisted,
      ReopenedModulePublication publication,
      FactCandidateInputs inputs) {
    if (persisted == null
        || !FILE_NAME.equals(persisted.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(persisted.descriptor().artifactType())
        || !SCHEMA_VERSION.equals(persisted.descriptor().schemaVersion())
        || persisted.descriptor().mediaType() != CanonicalMediaType.APPLICATION_JSON) {
      throw failure();
    }
    JsonNode raw = canonicalJson.parseCanonical(persisted.canonicalUtf8());
    if (!(raw instanceof ObjectNode envelope)) {
      throw failure();
    }
    requireExactFields(envelope, ENVELOPE_FIELDS);
    requireText(envelope, "schemaVersion", SCHEMA_VERSION);
    requireText(envelope, "artifactType", ARTIFACT_TYPE);
    if (!persisted.descriptor().artifactId().equals(artifactId(envelope, "artifactId"))) {
      throw failure();
    }
    requireProducer(requiredObject(envelope, "producer"), publication);
    List<ArtifactReference> upstream = references(requiredArray(envelope, "upstreamArtifacts"));
    if (!upstream.equals(publication.receipt().upstreamArtifacts())
        || !upstream.equals(inputs.candidateModuleUpstreamArtifacts())) {
      throw failure();
    }
    if (!controls(requiredObject(envelope, "controls")).equals(publication.receipt().controls())) {
      throw failure();
    }
    requireCompletion(requiredObject(envelope, "completion"), publication);
    return candidateSet(requiredObject(envelope, "payload"), inputs);
  }

  private static void requireProducer(ObjectNode producer, ReopenedModulePublication publication) {
    requireExactFields(producer, PRODUCER_FIELDS);
    requireText(producer, "moduleVersion", MODULE_VERSION);
    ObjectNode address = requiredObject(producer, "address");
    requireExactFields(address, PRODUCER_ADDRESS_FIELDS);
    if (!(publication.reference().address() instanceof AnalysisStepModuleAddress expected)
        || !"ANALYSIS_STEP".equals(requiredText(address, "kind"))
        || !expected.runId().value().equals(requiredText(address, "runId"))
        || !expected.analysisStepKey().wireValue().equals(requiredText(address, "analysisStepKey"))
        || expected.moduleNumber() != requiredInt(address, "moduleNumber")
        || !expected.moduleKey().equals(requiredText(address, "moduleKey"))) {
      throw failure();
    }
  }

  private static void requireCompletion(
      ObjectNode completion, ReopenedModulePublication publication) {
    requireExactFields(completion, COMPLETION_FIELDS);
    if (!ModuleCompletionStatus.SUCCEEDED.name().equals(requiredText(completion, "status"))
        || !(completion.get("gapRefs") instanceof ArrayNode gaps)
        || !gaps.isEmpty()
        || completion.get("failureRef") == null
        || !completion.get("failureRef").isNull()
        || publication.receipt().status() != ModuleCompletionStatus.SUCCEEDED
        || !publication.receipt().gapRefs().isEmpty()) {
      throw failure();
    }
  }

  private static FactCandidateSet candidateSet(ObjectNode body, FactCandidateInputs inputs) {
    requireExactFields(body, BODY_FIELDS);
    ArtifactId candidateSetId = artifactId(body, "candidateSetId");
    List<ArtifactReference> roots = references(requiredArray(body, "sourceGraphRoots"));
    if (!roots.equals(inputs.sourceGraphRoots())) {
      throw failure();
    }
    List<FactCandidateSet.FactCandidate> candidates = candidates(requiredArray(body, "candidates"));
    List<FactCandidateSet.NotApplicableDisposition> dispositions =
        dispositions(requiredArray(body, "notApplicableDispositions"));
    FactCandidateSet.CandidateDenominator denominator =
        denominator(requiredObject(body, "denominator"));
    FactCandidateSet result =
        new FactCandidateSet(
            SCHEMA_VERSION, candidateSetId, roots, candidates, dispositions, denominator);
    if (!candidates.equals(result.candidates())
        || !dispositions.equals(result.notApplicableDispositions())
        || !roots.equals(result.sourceGraphRoots())) {
      throw failure();
    }
    return result;
  }

  private static List<FactCandidateSet.FactCandidate> candidates(ArrayNode values) {
    List<FactCandidateSet.FactCandidate> result = new ArrayList<>(values.size());
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode candidate)) {
        throw failure();
      }
      String kind = requiredText(candidate, "kind");
      if ("JAVA_BOUNDARY_INVOCATION".equals(kind)) {
        requireExactFields(candidate, BOUNDARY_CANDIDATE_FIELDS);
        result.add(
            new FactCandidateSet.FactCandidate(
                requiredText(candidate, "candidateFactKey"),
                requiredText(candidate, "entryId"),
                kind,
                requiredText(candidate, "boundaryNodeId"),
                requiredText(candidate, "invocationCallId"),
                requiredText(candidate, "callTargetEdgeId"),
                requiredText(candidate, "staticTargetType"),
                requiredText(candidate, "staticTargetMethod"),
                requiredText(candidate, "staticTargetSignature"),
                texts(requiredArray(candidate, "orderedArgumentEdgeIds")),
                arguments(requiredArray(candidate, "orderedArguments")),
                requiredText(candidate, "controlBlockId"),
                nullableText(candidate, "guardId"),
                evidence(requiredArray(candidate, "evidenceNodeIdsBySubject")),
                atoms(requiredArray(candidate, "requiredAtoms"))));
      } else if ("JAVA_GUARD_CONDITION".equals(kind)) {
        requireExactFields(candidate, GUARD_CANDIDATE_FIELDS);
        result.add(
            new FactCandidateSet.FactCandidate(
                requiredText(candidate, "candidateFactKey"),
                requiredText(candidate, "entryId"),
                kind,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                null,
                null,
                evidence(requiredArray(candidate, "evidenceNodeIdsBySubject")),
                atoms(requiredArray(candidate, "requiredAtoms")),
                requiredText(candidate, "guardNodeId"),
                requiredText(candidate, "normalizedCondition"),
                texts(requiredArray(candidate, "branchEdgeIds"))));
      } else if ("JAVA_EXACT_CALL".equals(kind)) {
        requireExactFields(candidate, EXACT_CALL_CANDIDATE_FIELDS);
        result.add(
            new FactCandidateSet.FactCandidate(
                requiredText(candidate, "candidateFactKey"),
                requiredText(candidate, "entryId"),
                kind,
                requiredText(candidate, "callSiteNodeId"),
                requiredText(candidate, "callTargetEdgeId"),
                requiredText(candidate, "targetMethodNodeId"),
                requiredText(candidate, "targetCanonicalMethod"),
                evidence(requiredArray(candidate, "evidenceNodeIdsBySubject")),
                atoms(requiredArray(candidate, "requiredAtoms"))));
      } else {
        throw failure();
      }
    }
    return List.copyOf(result);
  }

  private static List<FactCandidateSet.BoundaryArgumentBinding> arguments(ArrayNode values) {
    List<FactCandidateSet.BoundaryArgumentBinding> result = new ArrayList<>(values.size());
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode argument)) {
        throw failure();
      }
      requireExactFields(argument, ARGUMENT_FIELDS);
      result.add(
          new FactCandidateSet.BoundaryArgumentBinding(
              requiredInt(argument, "ordinal"),
              requiredText(argument, "argumentNodeId"),
              requiredText(argument, "argumentEdgeId"),
              texts(requiredArray(argument, "javaLocalOriginNodeIds"))));
    }
    return List.copyOf(result);
  }

  private static List<FactCandidateSet.SubjectEvidenceBinding> evidence(ArrayNode values) {
    List<FactCandidateSet.SubjectEvidenceBinding> result = new ArrayList<>(values.size());
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode binding)) {
        throw failure();
      }
      requireExactFields(binding, EVIDENCE_FIELDS);
      result.add(
          new FactCandidateSet.SubjectEvidenceBinding(
              requiredText(binding, "subjectElementId"),
              texts(requiredArray(binding, "sourceEvidenceNodeIds")),
              texts(requiredArray(binding, "ruleApplicationEvidenceNodeIds"))));
    }
    return List.copyOf(result);
  }

  private static List<FactCandidateSet.RequiredAtom> atoms(ArrayNode values) {
    List<FactCandidateSet.RequiredAtom> result = new ArrayList<>(values.size());
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode atom)) {
        throw failure();
      }
      requireExactFields(atom, ATOM_FIELDS);
      result.add(
          new FactCandidateSet.RequiredAtom(
              requiredText(atom, "atomKey"),
              requiredText(atom, "role"),
              requiredText(atom, "valueType"),
              texts(requiredArray(atom, "expectedEvidenceKinds"))));
    }
    return List.copyOf(result);
  }

  private static List<FactCandidateSet.NotApplicableDisposition> dispositions(ArrayNode values) {
    List<FactCandidateSet.NotApplicableDisposition> result = new ArrayList<>(values.size());
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode disposition)) {
        throw failure();
      }
      requireExactFields(disposition, DISPOSITION_FIELDS);
      result.add(
          new FactCandidateSet.NotApplicableDisposition(
              requiredText(disposition, "entryId"),
              requiredText(disposition, "subjectNodeId"),
              requiredText(disposition, "templateKey"),
              texts(requiredArray(disposition, "missingRoles")),
              requiredText(disposition, "reasonCode")));
    }
    return List.copyOf(result);
  }

  private static FactCandidateSet.CandidateDenominator denominator(ObjectNode value) {
    requireExactFields(value, DENOMINATOR_FIELDS);
    return new FactCandidateSet.CandidateDenominator(
        texts(requiredArray(value, "applicableKeys")),
        texts(requiredArray(value, "notApplicableKeys")));
  }

  private static ArtifactControls controls(ObjectNode value) {
    requireExactFields(value, CONTROLS_FIELDS);
    JsonNode prompt = value.get("promptBundleSha256");
    if (prompt == null || (!prompt.isNull() && !prompt.isTextual())) {
      throw failure();
    }
    ObjectNode policy = requiredObject(value, "artifactPolicyRegistryRef");
    requireExactFields(policy, REFERENCE_FIELDS);
    return new ArtifactControls(
        Sha256Digest.parse(requiredText(value, "toolchainSha256")),
        Sha256Digest.parse(requiredText(value, "profileSha256")),
        Sha256Digest.parse(requiredText(value, "schemaBundleSha256")),
        prompt.isNull() ? null : Sha256Digest.parse(prompt.textValue()),
        new ArtifactPolicyRegistryReference(
            artifactId(policy, "artifactId"), Sha256Digest.parse(requiredText(policy, "sha256"))));
  }

  private static List<ArtifactReference> references(ArrayNode values) {
    List<ArtifactReference> result = new ArrayList<>(values.size());
    for (JsonNode value : values) {
      if (!(value instanceof ObjectNode reference)) {
        throw failure();
      }
      requireExactFields(reference, REFERENCE_FIELDS);
      result.add(
          new ArtifactReference(
              artifactId(reference, "artifactId"),
              Sha256Digest.parse(requiredText(reference, "sha256"))));
    }
    requireOrderedUniqueReferences(result);
    return List.copyOf(result);
  }

  private static void requireOrderedUniqueReferences(List<ArtifactReference> values) {
    if (values == null || values.isEmpty()) {
      throw failure();
    }
    List<ArtifactReference> ordered =
        values.stream().sorted(Comparator.comparing(value -> value.artifactId().value())).toList();
    if (!ordered.equals(values)
        || values.size() != values.stream().map(ArtifactReference::artifactId).distinct().count()) {
      throw failure();
    }
  }

  private static List<String> texts(ArrayNode values) {
    List<String> result = new ArrayList<>(values.size());
    for (JsonNode value : values) {
      if (!value.isTextual()) {
        throw failure();
      }
      result.add(value.textValue());
    }
    return List.copyOf(result);
  }

  private static ObjectNode requiredObject(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (!(value instanceof ObjectNode object)) {
      throw failure();
    }
    return object;
  }

  private static ArrayNode requiredArray(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (!(value instanceof ArrayNode array)) {
      throw failure();
    }
    return array;
  }

  private static ArtifactId artifactId(ObjectNode parent, String fieldName) {
    return ArtifactId.parse(requiredText(parent, fieldName));
  }

  private static String requiredText(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || !value.isTextual()) {
      throw failure();
    }
    return value.textValue();
  }

  private static void requireText(ObjectNode parent, String fieldName, String expected) {
    if (!expected.equals(requiredText(parent, fieldName))) {
      throw failure();
    }
  }

  private static String nullableText(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || (!value.isNull() && !value.isTextual())) {
      throw failure();
    }
    return value.isNull() ? null : value.textValue();
  }

  private static int requiredInt(ObjectNode parent, String fieldName) {
    JsonNode value = parent.get(fieldName);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
      throw failure();
    }
    return value.intValue();
  }

  private static void requireExactFields(ObjectNode value, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw failure();
    }
  }

  private static FactCandidateReferenceException failure() {
    return new FactCandidateReferenceException();
  }
}
