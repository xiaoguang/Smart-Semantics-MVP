package org.sourceanalysis.app.analysis.discovery.frontend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;
import org.sourceanalysis.app.capture.localgit.SourceRegistrationReference;
import org.sourceanalysis.app.runtime.SelectedSourceBasis;

/** Saves and fresh-reopens the one canonical, source-bound frontend HTTP index for module 6. */
public final class FrontendHttpIndexModulePublisher {

  public static final String FILE_NAME = "frontend-http-index.jsonl";
  public static final String ARTIFACT_TYPE = "APPLICATION_DISCOVERY_FRONTEND_HTTP_INDEX";
  public static final String SCHEMA_VERSION = "frontend-http-index-v1";
  public static final String V2_SCHEMA_VERSION = "frontend-http-index-v2";
  private static final String MODULE_VERSION = "v1";
  private static final String V2_MODULE_VERSION = "v2";
  private static final Set<String> RECORD_TYPES =
      Set.of(
          "HEADER",
          "FILE",
          "CONFIGURATION_FILE",
          "SOURCE_UNIT",
          "COMPONENT_USE",
          "HTTP_REQUEST",
          "ENTRY_LINK",
          "DIAGNOSTIC");
  private static final ObjectMapper MAPPER =
      new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final CanonicalModuleArtifactStore modules;
  private final CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();

  /** Creates the publisher with the sole module-artifact storage dependency. */
  public FrontendHttpIndexModulePublisher(CanonicalModuleArtifactStore modules) {
    this.modules = Objects.requireNonNull(modules, "module artifact store");
  }

  /** Installs one complete module-6 index owned by R1 while retaining its exact R0 basis. */
  public ModulePublicationReference publish(
      AnalysisStepModuleAddress module6R1,
      SelectedSourceBasis r0Basis,
      ArtifactControls r1Controls,
      FrontendHttpIndex index) {
    return publish(
        module6R1, r0Basis, r1Controls, index, SCHEMA_VERSION, MODULE_VERSION, true, false);
  }

  /** Installs the independent R1 index without v1's backend-derived {@code ENTRY_LINK} records. */
  public ModulePublicationReference publishV2(
      AnalysisStepModuleAddress module6R1,
      SelectedSourceBasis r0Basis,
      ArtifactControls r1Controls,
      FrontendHttpIndex index) {
    return publish(
        module6R1, r0Basis, r1Controls, index, V2_SCHEMA_VERSION, V2_MODULE_VERSION, false, true);
  }

  private ModulePublicationReference publish(
      AnalysisStepModuleAddress module6R1,
      SelectedSourceBasis r0Basis,
      ArtifactControls r1Controls,
      FrontendHttpIndex index,
      String schemaVersion,
      String moduleVersion,
      boolean includeEntryLinks,
      boolean includeSupportingSourceUnits) {
    try {
      requireDestination(module6R1);
      Objects.requireNonNull(r0Basis, "R0 source basis");
      Objects.requireNonNull(r1Controls, "R1 artifact controls");
      Objects.requireNonNull(index, "frontend HTTP index");
      CanonicalModulePayload payload =
          payload(
              module6R1,
              r0Basis,
              r1Controls,
              index,
              schemaVersion,
              includeEntryLinks,
              includeSupportingSourceUnits);
      if (!parse(
              payload.canonicalUtf8(),
              module6R1,
              r0Basis,
              r1Controls,
              schemaVersion,
              includeEntryLinks,
              includeSupportingSourceUnits)
          .equals(includeEntryLinks ? index : withoutEntryLinks(index))) {
        throw invalid();
      }
      var installed =
          modules.install(
              new ModuleInstallRequest(
                  module6R1,
                  moduleVersion,
                  upstream(r0Basis, r1Controls),
                  r1Controls,
                  ModuleCompletionStatus.SUCCEEDED,
                  List.of(),
                  List.of(payload)));
      if (!installed.reference().address().equals(module6R1)) {
        throw invalid();
      }
      return installed.reference();
    } catch (FrontendHttpDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw invalid();
    }
  }

  /** Fresh-reopens a module-6 index after proving its R1 owner, R0 source basis, and controls. */
  public FrontendHttpIndex reopen(
      ModulePublicationReference reference,
      AnalysisRunId expectedR1,
      SelectedSourceBasis expectedR0Basis,
      ArtifactControls expectedR1Controls) {
    return reopen(
        reference,
        expectedR1,
        expectedR0Basis,
        expectedR1Controls,
        SCHEMA_VERSION,
        MODULE_VERSION,
        true,
        false);
  }

  /** Reopens only the independent v2 index; it never synthesizes historical entry links. */
  public FrontendHttpIndex reopenV2(
      ModulePublicationReference reference,
      AnalysisRunId expectedR1,
      SelectedSourceBasis expectedR0Basis,
      ArtifactControls expectedR1Controls) {
    return reopen(
        reference,
        expectedR1,
        expectedR0Basis,
        expectedR1Controls,
        V2_SCHEMA_VERSION,
        V2_MODULE_VERSION,
        false,
        true);
  }

  private FrontendHttpIndex reopen(
      ModulePublicationReference reference,
      AnalysisRunId expectedR1,
      SelectedSourceBasis expectedR0Basis,
      ArtifactControls expectedR1Controls,
      String schemaVersion,
      String moduleVersion,
      boolean includeEntryLinks,
      boolean includeSupportingSourceUnits) {
    try {
      Objects.requireNonNull(reference, "frontend HTTP index publication");
      Objects.requireNonNull(expectedR1, "expected R1 run");
      Objects.requireNonNull(expectedR0Basis, "expected R0 source basis");
      Objects.requireNonNull(expectedR1Controls, "expected R1 artifact controls");
      if (!(reference.address() instanceof AnalysisStepModuleAddress address)) {
        throw invalid();
      }
      requireDestination(address);
      if (!address.runId().equals(expectedR1)) {
        throw invalid();
      }
      ReopenedModulePublication reopened = modules.reopen(reference);
      if (!reopened.reference().equals(reference)
          || !reopened.receipt().address().equals(address)
          || !moduleVersion.equals(reopened.receipt().moduleVersion())
          || !reopened.receipt().controls().equals(expectedR1Controls)
          || reopened.receipt().status() != ModuleCompletionStatus.SUCCEEDED
          || !reopened.receipt().gapRefs().isEmpty()
          || !reopened
              .receipt()
              .upstreamArtifacts()
              .equals(upstream(expectedR0Basis, expectedR1Controls))) {
        throw invalid();
      }
      VerifiedCanonicalPayload payload = onlyPayload(reopened, schemaVersion);
      return parse(
          payload.canonicalUtf8(),
          address,
          expectedR0Basis,
          expectedR1Controls,
          schemaVersion,
          includeEntryLinks,
          includeSupportingSourceUnits);
    } catch (FrontendHttpDiscoveryException failure) {
      throw failure;
    } catch (RuntimeException failure) {
      throw invalid();
    }
  }

  private CanonicalModulePayload payload(
      AnalysisStepModuleAddress address,
      SelectedSourceBasis basis,
      ArtifactControls controls,
      FrontendHttpIndex index,
      String schemaVersion,
      boolean includeEntryLinks,
      boolean includeSupportingSourceUnits) {
    StringBuilder content = new StringBuilder();
    for (RecordLine record :
        records(address, basis, controls, index, includeEntryLinks, includeSupportingSourceUnits)) {
      ObjectNode line = MAPPER.createObjectNode();
      line.put("schemaVersion", schemaVersion);
      line.put("recordType", record.type());
      line.put("key", record.key());
      line.set("payload", record.payload());
      content.append(
          new String(
              canonicalJson.encodeCanonical(line).copyToByteArray(), StandardCharsets.UTF_8));
      content.append('\n');
    }
    ImmutableBytes bytes =
        ImmutableBytes.copyOf(content.toString().getBytes(StandardCharsets.UTF_8));
    String prefix =
        modules
            .resolveArtifactPolicy(new ArtifactPolicyKey(ARTIFACT_TYPE, schemaVersion))
            .artifactIdPrefix();
    ArtifactId id =
        ArtifactId.parse(
            prefix
                + ":"
                + sha256(
                    concatenate(
                        frame("canonical-jsonl-artifact-id-v1"),
                        frame(schemaVersion),
                        frame(ARTIFACT_TYPE),
                        frame(bytes.copyToByteArray()))));
    return new CanonicalModulePayload(
        FILE_NAME,
        ARTIFACT_TYPE,
        schemaVersion,
        id,
        CanonicalMediaType.APPLICATION_X_NDJSON,
        bytes);
  }

  private List<RecordLine> records(
      AnalysisStepModuleAddress address,
      SelectedSourceBasis basis,
      ArtifactControls controls,
      FrontendHttpIndex index,
      boolean includeEntryLinks,
      boolean includeSupportingSourceUnits) {
    validateIndex(index, includeEntryLinks, includeSupportingSourceUnits);
    List<RecordLine> records = new ArrayList<>();
    ObjectNode header = MAPPER.createObjectNode();
    header.set("moduleAddress", addressNode(address));
    header.set("sourceBasis", sourceBasisNode(basis));
    header.set("controls", controlsNode(controls));
    header.put("status", index.status().name());
    records.add(new RecordLine("HEADER", "header", header));

    for (FrontendSourceFileDisposition file : index.files()) {
      records.add(new RecordLine("FILE", file.path(), MAPPER.valueToTree(file)));
    }
    for (FrontendConfigurationFileRecord file : index.configurationFiles()) {
      records.add(new RecordLine("CONFIGURATION_FILE", file.path(), MAPPER.valueToTree(file)));
    }
    for (SourceUnit unit : sourceUnits(index, includeSupportingSourceUnits)) {
      records.add(new RecordLine("SOURCE_UNIT", unit.key(), unit.node()));
    }
    for (ComponentUse use : componentUses(index)) {
      records.add(new RecordLine("COMPONENT_USE", use.key(), use.node()));
    }
    for (FrontendHttpRequestRecord request : index.requests()) {
      records.add(new RecordLine("HTTP_REQUEST", request.requestId(), MAPPER.valueToTree(request)));
    }
    if (includeEntryLinks) {
      for (FrontendEntryLinkRecord link : index.entryLinks()) {
        records.add(new RecordLine("ENTRY_LINK", link.requestId(), MAPPER.valueToTree(link)));
      }
    }
    for (int indexPosition = 0; indexPosition < index.diagnostics().size(); indexPosition++) {
      records.add(
          new RecordLine(
              "DIAGNOSTIC",
              "diagnostic:" + indexPosition,
              MAPPER.valueToTree(index.diagnostics().get(indexPosition))));
    }
    return List.copyOf(records);
  }

  private FrontendHttpIndex parse(
      ImmutableBytes bytes,
      AnalysisStepModuleAddress expectedAddress,
      SelectedSourceBasis expectedBasis,
      ArtifactControls expectedControls,
      String schemaVersion,
      boolean includeEntryLinks,
      boolean includeSupportingSourceUnits) {
    List<RecordLine> lines = lines(bytes, schemaVersion, includeEntryLinks);
    RecordLine header = only(lines, "HEADER");
    requireHeader(header.payload(), expectedAddress, expectedBasis, expectedControls);
    List<FrontendSourceFileDisposition> files =
        typed(lines, "FILE", FrontendSourceFileDisposition.class);
    List<FrontendConfigurationFileRecord> configurationFiles =
        typed(lines, "CONFIGURATION_FILE", FrontendConfigurationFileRecord.class);
    List<FrontendHttpRequestRecord> requests =
        typed(lines, "HTTP_REQUEST", FrontendHttpRequestRecord.class);
    List<FrontendEntryLinkRecord> links =
        includeEntryLinks ? typed(lines, "ENTRY_LINK", FrontendEntryLinkRecord.class) : List.of();
    List<FrontendDiagnosticRecord> diagnostics =
        typed(lines, "DIAGNOSTIC", FrontendDiagnosticRecord.class);
    List<FrontendSupportingSourceUnit> supportingSourceUnits =
        supportingSourceUnits(lines, requests, includeSupportingSourceUnits);
    FrontendHttpIndex result =
        new FrontendHttpIndex(
            files,
            requests,
            links,
            diagnostics,
            status(header.payload()),
            configurationFiles,
            supportingSourceUnits);
    validateIndex(result, includeEntryLinks, includeSupportingSourceUnits);
    requireDerivedRecords(lines, result, includeSupportingSourceUnits);
    return result;
  }

  private List<RecordLine> lines(
      ImmutableBytes bytes, String schemaVersion, boolean includeEntryLinks) {
    String content = strictUtf8(bytes.copyToByteArray());
    if (content.isEmpty() || !content.endsWith("\n")) {
      throw invalid();
    }
    List<RecordLine> lines = new ArrayList<>();
    Set<String> identities = new HashSet<>();
    String[] textLines = content.substring(0, content.length() - 1).split("\n", -1);
    for (String text : textLines) {
      if (text.isEmpty()) {
        throw invalid();
      }
      JsonNode parsed =
          canonicalJson.parseCanonical(
              ImmutableBytes.copyOf(text.getBytes(StandardCharsets.UTF_8)));
      if (!(parsed instanceof ObjectNode line)) {
        throw invalid();
      }
      requireFields(line, Set.of("schemaVersion", "recordType", "key", "payload"));
      String type = text(line, "recordType");
      String key = text(line, "key");
      if (!schemaVersion.equals(text(line, "schemaVersion"))
          || !recordTypes(includeEntryLinks).contains(type)
          || !(line.get("payload") instanceof ObjectNode payload)
          || !identities.add(type + "\u0000" + key)) {
        throw invalid();
      }
      lines.add(new RecordLine(type, key, payload));
    }
    return List.copyOf(lines);
  }

  private static void requireHeader(
      ObjectNode header,
      AnalysisStepModuleAddress expectedAddress,
      SelectedSourceBasis expectedBasis,
      ArtifactControls expectedControls) {
    requireFields(header, Set.of("moduleAddress", "sourceBasis", "controls", "status"));
    if (!addressNode(expectedAddress).equals(header.get("moduleAddress"))
        || !sourceBasisNode(expectedBasis).equals(header.get("sourceBasis"))
        || !controlsNode(expectedControls).equals(header.get("controls"))) {
      throw invalid();
    }
  }

  private static FrontendHttpIndex.Status status(ObjectNode header) {
    try {
      return FrontendHttpIndex.Status.valueOf(text(header, "status"));
    } catch (IllegalArgumentException invalidStatus) {
      throw invalid();
    }
  }

  private static void requireDerivedRecords(
      List<RecordLine> lines, FrontendHttpIndex index, boolean includeSupportingSourceUnits) {
    List<RecordLine> actualUnits =
        lines.stream().filter(line -> line.type().equals("SOURCE_UNIT")).toList();
    List<RecordLine> expectedUnits =
        sourceUnits(index, includeSupportingSourceUnits).stream()
            .map(unit -> new RecordLine("SOURCE_UNIT", unit.key(), unit.node()))
            .toList();
    List<RecordLine> actualUses =
        lines.stream().filter(line -> line.type().equals("COMPONENT_USE")).toList();
    List<RecordLine> expectedUses =
        componentUses(index).stream()
            .map(use -> new RecordLine("COMPONENT_USE", use.key(), use.node()))
            .toList();
    if (!actualUnits.equals(expectedUnits) || !actualUses.equals(expectedUses)) {
      throw invalid();
    }
  }

  private static void validateIndex(
      FrontendHttpIndex index, boolean includeEntryLinks, boolean includeSupportingSourceUnits) {
    Map<String, String> files = new HashMap<>();
    for (FrontendSourceFileDisposition file : index.files()) {
      if (files.put(file.path(), file.sourceSha256()) != null) {
        throw invalid();
      }
    }
    Map<String, String> sources = new HashMap<>(files);
    Map<String, String> configurationFiles = new HashMap<>();
    for (FrontendConfigurationFileRecord file : index.configurationFiles()) {
      if (configurationFiles.put(file.path(), file.sourceSha256()) != null) {
        throw invalid();
      }
      String previous = sources.putIfAbsent(file.path(), file.sourceSha256());
      if (previous != null && !previous.equals(file.sourceSha256())) {
        throw invalid();
      }
    }
    Set<String> requestIds = new HashSet<>();
    for (FrontendHttpRequestRecord request : index.requests()) {
      if (!requestIds.add(request.requestId())
          || !request.sourceSha256().equals(files.get(request.pagePath()))) {
        throw invalid();
      }
      for (FrontendWrapperCall wrapper : request.wrapperPath()) {
        if (!wrapper.sourceSha256().equals(files.get(wrapper.sourcePath()))
            || !contains(wrapper.sourceUnitRange(), wrapper.callRange())) {
          throw invalid();
        }
      }
    }
    Set<String> linkRequestIds = new HashSet<>();
    for (FrontendEntryLinkRecord link : index.entryLinks()) {
      if (!requestIds.contains(link.requestId()) || !linkRequestIds.add(link.requestId())) {
        throw invalid();
      }
    }
    if ((includeEntryLinks && !linkRequestIds.equals(requestIds))
        || (!includeEntryLinks && !linkRequestIds.isEmpty())) {
      throw invalid();
    }
    if (!includeSupportingSourceUnits && !index.supportingSourceUnits().isEmpty()) {
      throw invalid();
    }
    Map<String, SourceUnit> wrapperUnits = sourceUnitMap(index.requests());
    Set<String> supportingUnitKeys = new HashSet<>();
    for (FrontendSupportingSourceUnit supportingUnit : index.supportingSourceUnits()) {
      if (!supportingUnit.sourceSha256().equals(files.get(supportingUnit.sourcePath()))) {
        throw invalid();
      }
      SourceUnit sourceUnit = SourceUnit.from(supportingUnit);
      if (!supportingUnitKeys.add(sourceUnit.key()) || wrapperUnits.containsKey(sourceUnit.key())) {
        throw invalid();
      }
    }
    for (FrontendDiagnosticRecord diagnostic : index.diagnostics()) {
      if (!diagnostic.sourceSha256().equals(sources.get(diagnostic.sourcePath()))
          || (diagnostic.requestId() != null && !requestIds.contains(diagnostic.requestId()))) {
        throw invalid();
      }
    }
  }

  private static List<SourceUnit> sourceUnits(
      FrontendHttpIndex index, boolean includeSupportingSourceUnits) {
    Map<String, SourceUnit> units = sourceUnitMap(index.requests());
    if (includeSupportingSourceUnits) {
      for (FrontendSupportingSourceUnit supportingUnit : index.supportingSourceUnits()) {
        SourceUnit unit = SourceUnit.from(supportingUnit);
        SourceUnit previous = units.putIfAbsent(unit.key(), unit);
        if (previous != null && !previous.equals(unit)) {
          throw invalid();
        }
      }
    }
    return List.copyOf(units.values());
  }

  private static Map<String, SourceUnit> sourceUnitMap(List<FrontendHttpRequestRecord> requests) {
    Map<String, SourceUnit> units = new LinkedHashMap<>();
    for (FrontendHttpRequestRecord request : requests) {
      for (FrontendWrapperCall wrapper : request.wrapperPath()) {
        SourceUnit unit = SourceUnit.from(wrapper);
        SourceUnit previous = units.putIfAbsent(unit.key(), unit);
        if (previous != null && !previous.equals(unit)) {
          throw invalid();
        }
      }
    }
    return units;
  }

  private static List<FrontendSupportingSourceUnit> supportingSourceUnits(
      List<RecordLine> lines,
      List<FrontendHttpRequestRecord> requests,
      boolean includeSupportingSourceUnits) {
    Map<String, SourceUnit> wrapperUnits = sourceUnitMap(requests);
    Map<String, FrontendSupportingSourceUnit> supportingUnits = new LinkedHashMap<>();
    for (RecordLine line : lines) {
      if (!line.type().equals("SOURCE_UNIT")) {
        continue;
      }
      FrontendSupportingSourceUnit supportingUnit = readSupportingSourceUnit(line.payload());
      SourceUnit sourceUnit = SourceUnit.from(supportingUnit);
      if (!line.key().equals(sourceUnit.key())) {
        throw invalid();
      }
      SourceUnit wrapper = wrapperUnits.get(sourceUnit.key());
      if (wrapper != null) {
        if (!wrapper.equals(sourceUnit)) {
          throw invalid();
        }
        continue;
      }
      if (!includeSupportingSourceUnits
          || supportingUnits.putIfAbsent(sourceUnit.key(), supportingUnit) != null) {
        throw invalid();
      }
    }
    return List.copyOf(supportingUnits.values());
  }

  private static FrontendSupportingSourceUnit readSupportingSourceUnit(ObjectNode payload) {
    requireFields(
        payload, Set.of("sourcePath", "sourceSha256", "sourceUnitRange", "sourceUnitKind"));
    FrontendWrapperCall.SourceUnitKind kind;
    try {
      kind = FrontendWrapperCall.SourceUnitKind.valueOf(text(payload, "sourceUnitKind"));
    } catch (IllegalArgumentException invalidKind) {
      throw invalid();
    }
    return new FrontendSupportingSourceUnit(
        text(payload, "sourcePath"),
        text(payload, "sourceSha256"),
        convert(payload.get("sourceUnitRange"), SourceRange.class),
        kind);
  }

  private static List<ComponentUse> componentUses(FrontendHttpIndex index) {
    List<ComponentUse> uses = new ArrayList<>();
    for (FrontendHttpRequestRecord request : index.requests()) {
      for (int position = 0; position < request.wrapperPath().size(); position++) {
        uses.add(ComponentUse.from(request, position, request.wrapperPath().get(position)));
      }
    }
    return List.copyOf(uses);
  }

  private static List<ArtifactReference> upstream(
      SelectedSourceBasis r0Basis, ArtifactControls r1Controls) {
    Map<String, ArtifactReference> references = new LinkedHashMap<>();
    if (r0Basis.kind() == SelectedSourceBasis.Kind.PREPARED_V1) {
      PreparedSourceReference prepared = r0Basis.preparedSource();
      putReference(references, prepared.schemaBundleRef());
      putReference(
          references,
          new ArtifactReference(
              prepared.artifactPolicyRegistryRef().artifactId(),
              prepared.artifactPolicyRegistryRef().sha256()));
    } else {
      SourceRegistrationReference legacy = r0Basis.legacyCapture();
      putReference(references, legacy.snapshotManifestRef());
      putReference(references, legacy.captureReceiptRef());
    }
    putReference(
        references,
        new ArtifactReference(
            r1Controls.artifactPolicyRegistryRef().artifactId(),
            r1Controls.artifactPolicyRegistryRef().sha256()));
    return references.values().stream()
        .sorted(
            java.util.Comparator.comparing(
                reference -> reference.artifactId().value(),
                FrontendHttpIndexModulePublisher::compareUtf8))
        .toList();
  }

  private static void putReference(
      Map<String, ArtifactReference> references, ArtifactReference reference) {
    ArtifactReference previous = references.putIfAbsent(reference.artifactId().value(), reference);
    if (previous != null && !previous.equals(reference)) {
      throw invalid();
    }
  }

  private static VerifiedCanonicalPayload onlyPayload(
      ReopenedModulePublication reopened, String schemaVersion) {
    if (reopened.payloads().size() != 1) {
      throw invalid();
    }
    VerifiedCanonicalPayload payload = reopened.payloads().get(0);
    if (!FILE_NAME.equals(payload.descriptor().fileName())
        || !ARTIFACT_TYPE.equals(payload.descriptor().artifactType())
        || !schemaVersion.equals(payload.descriptor().schemaVersion())
        || payload.descriptor().mediaType() != CanonicalMediaType.APPLICATION_X_NDJSON) {
      throw invalid();
    }
    return payload;
  }

  private static RecordLine only(List<RecordLine> lines, String type) {
    List<RecordLine> matches = lines.stream().filter(line -> line.type().equals(type)).toList();
    if (matches.size() != 1) {
      throw invalid();
    }
    return matches.get(0);
  }

  private static <T> List<T> typed(List<RecordLine> lines, String type, Class<T> valueType) {
    List<T> values = new ArrayList<>();
    for (RecordLine line : lines) {
      if (line.type().equals(type)) {
        values.add(convert(line.payload(), valueType));
      }
    }
    return List.copyOf(values);
  }

  private static <T> T convert(JsonNode value, Class<T> valueType) {
    try {
      return MAPPER.treeToValue(value, valueType);
    } catch (JsonProcessingException failure) {
      throw invalid();
    }
  }

  private static ObjectNode addressNode(AnalysisStepModuleAddress address) {
    return MAPPER
        .createObjectNode()
        .put("kind", "ANALYSIS_STEP")
        .put("runId", address.runId().value())
        .put("analysisStepKey", address.analysisStepKey().wireValue())
        .put("moduleNumber", address.moduleNumber())
        .put("moduleKey", address.moduleKey());
  }

  private static ObjectNode controlsNode(ArtifactControls controls) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("toolchainSha256", controls.toolchainSha256().value());
    node.put("profileSha256", controls.profileSha256().value());
    node.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      node.putNull("promptBundleSha256");
    } else {
      node.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    node.putObject("artifactPolicyRegistryRef")
        .put("artifactId", controls.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", controls.artifactPolicyRegistryRef().sha256().value());
    return node;
  }

  private static ObjectNode sourceBasisNode(SelectedSourceBasis basis) {
    ObjectNode node = MAPPER.createObjectNode();
    node.put("kind", basis.kind().name());
    node.put("snapshotId", basis.snapshotId().value());
    node.put("effectiveScopeDigest", basis.effectiveScopeDigest().value());
    if (basis.kind() == SelectedSourceBasis.Kind.PREPARED_V1) {
      PreparedSourceReference prepared = basis.preparedSource();
      ObjectNode preparedNode = node.putObject("preparedSource");
      preparedNode.put("sourceVersionId", prepared.sourceVersionId().value());
      preparedNode.set("publication", publicationNode(prepared));
      preparedNode.set("schemaBundleRef", referenceNode(prepared.schemaBundleRef()));
      preparedNode.set("artifactPolicyRegistryRef", policyNode(prepared));
      node.putNull("legacyCapture");
    } else {
      SourceRegistrationReference legacy = basis.legacyCapture();
      ObjectNode legacyNode = node.putObject("legacyCapture");
      legacyNode.put("sourceRegistrationId", legacy.sourceRegistrationId().value());
      legacyNode.put("snapshotId", legacy.snapshotId());
      legacyNode.set("snapshotManifestRef", referenceNode(legacy.snapshotManifestRef()));
      legacyNode.set("captureReceiptRef", referenceNode(legacy.captureReceiptRef()));
      node.putNull("preparedSource");
    }
    return node;
  }

  private static ObjectNode publicationNode(PreparedSourceReference prepared) {
    var publication = prepared.publication();
    ObjectNode node = MAPPER.createObjectNode();
    node.putObject("address")
        .put("runId", publication.address().runId().value())
        .put("analysisStepKey", publication.address().analysisStepKey().wireValue());
    node.put("analysisStepArtifactRoot", publication.analysisStepArtifactRoot().value());
    node.put("analysisStepReceiptId", publication.analysisStepReceiptId().value());
    node.put("analysisStepReceiptSha256", publication.analysisStepReceiptSha256().value());
    return node;
  }

  private static ObjectNode policyNode(PreparedSourceReference prepared) {
    return MAPPER
        .createObjectNode()
        .put("artifactId", prepared.artifactPolicyRegistryRef().artifactId().value())
        .put("sha256", prepared.artifactPolicyRegistryRef().sha256().value());
  }

  private static ObjectNode referenceNode(ArtifactReference reference) {
    return MAPPER
        .createObjectNode()
        .put("artifactId", reference.artifactId().value())
        .put("sha256", reference.sha256().value());
  }

  private static boolean contains(SourceRange outer, SourceRange inner) {
    long outerEnd = (long) outer.startOffsetUtf16() + outer.lengthUtf16();
    long innerEnd = (long) inner.startOffsetUtf16() + inner.lengthUtf16();
    return outer.startOffsetUtf16() <= inner.startOffsetUtf16() && innerEnd <= outerEnd;
  }

  private static void requireDestination(AnalysisStepModuleAddress address) {
    if (address == null
        || address.analysisStepKey() != AnalysisStepKey.APPLICATION_DISCOVERY
        || address.moduleNumber() != 6
        || !"frontend-http-discovery".equals(address.moduleKey())) {
      throw invalid();
    }
  }

  private static Set<String> recordTypes(boolean includeEntryLinks) {
    if (includeEntryLinks) {
      return RECORD_TYPES;
    }
    Set<String> types = new HashSet<>(RECORD_TYPES);
    types.remove("ENTRY_LINK");
    return Set.copyOf(types);
  }

  private static FrontendHttpIndex withoutEntryLinks(FrontendHttpIndex index) {
    return new FrontendHttpIndex(
        index.files(),
        index.requests(),
        List.of(),
        index.diagnostics(),
        index.status(),
        index.configurationFiles(),
        index.supportingSourceUnits());
  }

  private static void requireFields(ObjectNode node, Set<String> expected) {
    Set<String> actual = new HashSet<>();
    node.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(expected)) {
      throw invalid();
    }
  }

  private static String text(ObjectNode node, String name) {
    JsonNode value = node.get(name);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw invalid();
    }
    return value.textValue();
  }

  private static String strictUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8
          .newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException failure) {
      throw invalid();
    }
  }

  private static String sha256(byte[] value) {
    try {
      return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
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
    int size = 0;
    for (byte[] value : values) {
      size = Math.addExact(size, value.length);
    }
    ByteBuffer result = ByteBuffer.allocate(size);
    for (byte[] value : values) {
      result.put(value);
    }
    return result.array();
  }

  private static int compareUtf8(String left, String right) {
    return java.util.Arrays.compareUnsigned(
        left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
  }

  private static FrontendHttpDiscoveryException invalid() {
    return new FrontendHttpDiscoveryException("FRONTEND_HTTP_INDEX_INVALID");
  }

  private record RecordLine(String type, String key, ObjectNode payload) {}

  private record SourceUnit(String key, ObjectNode node) {
    private static SourceUnit from(FrontendWrapperCall wrapper) {
      ObjectNode payload = MAPPER.createObjectNode();
      payload.put("sourcePath", wrapper.sourcePath());
      payload.put("sourceSha256", wrapper.sourceSha256());
      payload.set("sourceUnitRange", MAPPER.valueToTree(wrapper.sourceUnitRange()));
      payload.put("sourceUnitKind", wrapper.sourceUnitKind().name());
      SourceRange range = wrapper.sourceUnitRange();
      String key =
          wrapper.sourcePath()
              + "@"
              + wrapper.sourceSha256()
              + ":"
              + range.startOffsetUtf16()
              + ":"
              + range.lengthUtf16()
              + ":"
              + wrapper.sourceUnitKind().name();
      return new SourceUnit(key, payload);
    }

    private static SourceUnit from(FrontendSupportingSourceUnit supportingUnit) {
      ObjectNode payload = MAPPER.createObjectNode();
      payload.put("sourcePath", supportingUnit.sourcePath());
      payload.put("sourceSha256", supportingUnit.sourceSha256());
      payload.set("sourceUnitRange", MAPPER.valueToTree(supportingUnit.sourceUnitRange()));
      payload.put("sourceUnitKind", supportingUnit.sourceUnitKind().name());
      SourceRange range = supportingUnit.sourceUnitRange();
      String key =
          supportingUnit.sourcePath()
              + "@"
              + supportingUnit.sourceSha256()
              + ":"
              + range.startOffsetUtf16()
              + ":"
              + range.lengthUtf16()
              + ":"
              + supportingUnit.sourceUnitKind().name();
      return new SourceUnit(key, payload);
    }
  }

  private record ComponentUse(String key, ObjectNode node) {
    private static ComponentUse from(
        FrontendHttpRequestRecord request, int position, FrontendWrapperCall wrapper) {
      ObjectNode payload = MAPPER.createObjectNode();
      payload.put("requestId", request.requestId());
      payload.put("pagePath", request.pagePath());
      payload.put("instanceKey", request.instanceKey());
      payload.put("sourcePath", wrapper.sourcePath());
      payload.put("sourceSha256", wrapper.sourceSha256());
      payload.set("callRange", MAPPER.valueToTree(wrapper.callRange()));
      payload.set("sourceUnitRange", MAPPER.valueToTree(wrapper.sourceUnitRange()));
      payload.put("sourceUnitKind", wrapper.sourceUnitKind().name());
      payload.put("fromUnit", wrapper.fromUnit());
      payload.put("toUnit", wrapper.toUnit());
      return new ComponentUse(request.requestId() + ":" + position, payload);
    }
  }
}
