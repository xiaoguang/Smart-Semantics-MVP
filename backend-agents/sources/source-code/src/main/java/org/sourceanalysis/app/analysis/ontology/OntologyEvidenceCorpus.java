package org.sourceanalysis.app.analysis.ontology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.analysis.material.publish.EntryEvidenceReader;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepArtifactRoot;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationAddress;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.AnalysisStepReceiptId;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** A bounded reading surface over one already verified R4 entry-evidence publication. */
public final class OntologyEvidenceCorpus {
  private final CanonicalJsonCodec json = new CanonicalJsonCodec();
  private final String sourceIdentity;
  private final JsonNode sourceInventoryWire;
  private final String sourceSnapshotId;
  private final Map<String, EntryEvidenceReader.EntryDocument> documents;
  private final List<EntrySummary> navigation;
  private final Map<String, List<UnitHandle>> unitsByEntry;
  private final Map<String, List<UnitHandle>> methodsByIdentity;
  private final Map<String, List<UnitHandle>> bindingsByStatement;
  private final Map<String, List<String>> statementVariantsByReference;
  private final Map<String, List<UnitHandle>> statementsByTable;
  private final Map<String, List<UnitHandle>> statementsByColumn;
  private final Map<String, List<UnitHandle>> controlsByExpression;
  private final boolean businessLinkNavigation;
  private final Map<UnitHandle, EvidenceUnit> evidenceByUse;
  private final Map<String, FrontendCoverageRequest> frontendCoverageByRequest;
  private final Map<String, JsonNode> frontendContextCoverage;
  private final int frontendRequestCount;
  private final int schemaSourceCount;
  private final Statistics statistics;
  private final AliasCatalog aliases;
  private VerifiedSourceTextSet preparedSource;

  private OntologyEvidenceCorpus(EntryEvidenceReader.Directory directory) {
    businessLinkNavigation = false;
    sourceIdentity =
        "entry-evidence-index:"
            + OntologyReadingPacket.sha256(directory.indexCanonicalJson().copyToByteArray());
    JsonNode index = json.parseCanonical(directory.indexCanonicalJson());
    sourceInventoryWire = index.path("header").path("sourceInventory");
    sourceSnapshotId = index.path("header").path("sourceSnapshotId").asText();
    JsonNode sourceBasis = index.path("header").path("sourceBasis");
    if (!"PREPARED_V1".equals(sourceBasis.path("kind").asText())) {
      throw new IllegalArgumentException("ONTOLOGY_SOURCE_BASIS_INVALID");
    }
    FrontendCoverageIndex frontendCoverage =
        frontendCoverageByRequest(directory.frontendCoverageCanonicalJsonl());
    frontendCoverageByRequest = frontendCoverage.requestsWithPageContext();
    frontendContextCoverage = savedPageContexts(directory.frontendCoverageCanonicalJsonl());
    frontendRequestCount = frontendCoverage.requestCount();
    schemaSourceCount = 0;
    Map<String, EntryEvidenceReader.EntryDocument> byId = new LinkedHashMap<>();
    Map<String, List<UnitHandle>> entryUnits = new LinkedHashMap<>();
    List<EntrySummary> summaries = new ArrayList<>();
    Map<String, LinkedHashSet<UnitHandle>> methods = new LinkedHashMap<>();
    Map<String, LinkedHashSet<UnitHandle>> bindings = new LinkedHashMap<>();
    Map<String, LinkedHashSet<String>> statementVariants = new LinkedHashMap<>();
    Map<String, LinkedHashSet<UnitHandle>> tables = new LinkedHashMap<>();
    Map<String, LinkedHashSet<UnitHandle>> columns = new LinkedHashMap<>();
    Map<UnitHandle, EvidenceUnit> parsedEvidence = new LinkedHashMap<>();
    Set<String> uniqueUnits = new HashSet<>();
    int unitUses = 0;
    int largestUnitBytes = 0;
    for (EntryEvidenceReader.EntryDocument document : directory.entries()) {
      JsonNode entry = json.parseCanonical(document.canonicalJson());
      String entryId = document.entryId();
      if (!entryId.equals(entry.path("entryId").asText())
          || !sourceBasis.equals(entry.path("sourceBasis"))) {
        throw new IllegalArgumentException("ONTOLOGY_ENTRY_IDENTITY_INVALID");
      }
      JsonNode http = entry.path("entry");
      summaries.add(
          new EntrySummary(
              entryId,
              http.path("method").asText(),
              http.path("route").asText(),
              http.path("handlerFqn").asText(),
              http.path("methodKey").asText(),
              entry.path("assemblyStatus").asText(),
              entry.path("limitations").size()));
      if (byId.putIfAbsent(entryId, document) != null) {
        throw new IllegalArgumentException("ONTOLOGY_ENTRY_IDENTITY_INVALID");
      }
      Map<String, Integer> limitations = new TreeMap<>();
      for (JsonNode limitation : entry.path("limitations")) {
        String code = limitation.path("code").asText();
        if (!code.isBlank()) {
          limitations.merge(code, 1, Integer::sum);
        }
      }
      EntryDescriptor descriptor =
          new EntryDescriptor(
              entry.path("entry").path("method").asText(),
              entry.path("entry").path("route").asText(),
              entry.path("entry").path("handlerFqn").asText());
      LinkedHashSet<UnitHandle> handles = new LinkedHashSet<>();
      for (UnitKind kind : UnitKind.values()) {
        for (JsonNode item : array(entry, kind)) {
          String id = unitId(item, kind);
          if (id != null && !id.isBlank()) {
            UnitHandle handle = new UnitHandle(entryId, kind, id);
            handles.add(handle);
            ImmutableBytes canonicalUnit = json.encodeCanonical(item);
            if (parsedEvidence.putIfAbsent(
                    handle,
                    new EvidenceUnit(entryId, kind, id, canonicalUnit, limitations, descriptor))
                != null) {
              throw new IllegalArgumentException("ONTOLOGY_UNIT_AMBIGUOUS");
            }
            unitUses++;
            largestUnitBytes = Math.max(largestUnitBytes, canonicalUnit.size());
            uniqueUnits.add(
                kind.name()
                    + ":"
                    + id
                    + ":"
                    + OntologyReadingPacket.sha256(canonicalUnit.copyToByteArray()));
          }
        }
      }
      entryUnits.put(entryId, List.copyOf(handles));
      for (JsonNode method : entry.path("java").path("methods")) {
        String methodKey = method.path("methodKey").asText();
        add(methods, methodKey, new UnitHandle(entryId, UnitKind.JAVA_METHOD, methodKey));
      }
      for (JsonNode statement : entry.path("persistence").path("statements")) {
        addVariant(statementVariants, statement);
      }
      for (JsonNode binding : entry.path("persistence").path("bindings")) {
        String methodKey = binding.path("methodKey").asText();
        for (JsonNode ref : binding.path("statementRefs")) {
          addVariant(statementVariants, ref);
          add(
              bindings,
              statementVariantId(ref),
              new UnitHandle(entryId, UnitKind.PERSISTENCE_BINDING, methodKey));
        }
      }
      for (JsonNode analysis : entry.path("persistence").path("sqlAnalyses")) {
        UnitHandle handle =
            new UnitHandle(entryId, UnitKind.SQL_ANALYSIS, unitId(analysis, UnitKind.SQL_ANALYSIS));
        indexSqlAst(analysis.path("ast"), handle, tables, columns);
      }
    }
    documents = Map.copyOf(byId);
    unitsByEntry = Map.copyOf(entryUnits);
    navigation = List.copyOf(summaries);
    methodsByIdentity = freeze(methods);
    bindingsByStatement = freeze(bindings);
    statementVariantsByReference = freezeStrings(statementVariants);
    statementsByTable = freeze(tables);
    statementsByColumn = freeze(columns);
    evidenceByUse = Map.copyOf(parsedEvidence);
    controlsByExpression = controlIndex(evidenceByUse);
    statistics = new Statistics(unitUses, uniqueUnits.size(), largestUnitBytes);
    aliases = AliasCatalog.create(this);
  }

  private OntologyEvidenceCorpus(
      OntologyEvidenceCorpus base,
      String augmentedSourceIdentity,
      Map<String, List<UnitHandle>> augmentedUnitsByEntry,
      Map<UnitHandle, EvidenceUnit> augmentedEvidenceByUse,
      int selectedSchemaSourceCount) {
    this(
        base,
        augmentedSourceIdentity,
        augmentedUnitsByEntry,
        augmentedEvidenceByUse,
        selectedSchemaSourceCount,
        base.businessLinkNavigation);
  }

  private OntologyEvidenceCorpus(
      OntologyEvidenceCorpus base,
      String augmentedSourceIdentity,
      Map<String, List<UnitHandle>> augmentedUnitsByEntry,
      Map<UnitHandle, EvidenceUnit> augmentedEvidenceByUse,
      int selectedSchemaSourceCount,
      boolean businessLinkNavigation) {
    this.businessLinkNavigation = businessLinkNavigation;
    sourceIdentity = augmentedSourceIdentity;
    sourceInventoryWire = base.sourceInventoryWire;
    sourceSnapshotId = base.sourceSnapshotId;
    documents = base.documents;
    navigation = base.navigation;
    unitsByEntry = Map.copyOf(augmentedUnitsByEntry);
    methodsByIdentity = methodIndex(augmentedUnitsByEntry);
    bindingsByStatement = base.bindingsByStatement;
    statementVariantsByReference = base.statementVariantsByReference;
    statementsByTable = base.statementsByTable;
    statementsByColumn = base.statementsByColumn;
    evidenceByUse = Map.copyOf(augmentedEvidenceByUse);
    controlsByExpression = controlIndex(evidenceByUse);
    frontendCoverageByRequest = base.frontendCoverageByRequest;
    frontendContextCoverage = base.frontendContextCoverage;
    frontendRequestCount = base.frontendRequestCount;
    schemaSourceCount = selectedSchemaSourceCount;
    Set<String> unique = new HashSet<>();
    int largest = 0;
    for (EvidenceUnit evidence : evidenceByUse.values()) {
      unique.add(
          evidence.kind().name()
              + ":"
              + evidence.originalId()
              + ":"
              + OntologyReadingPacket.sha256(evidence.canonicalJson().copyToByteArray()));
      largest = Math.max(largest, evidence.canonicalJson().size());
    }
    statistics = new Statistics(evidenceByUse.size(), unique.size(), largest);
    aliases = AliasCatalog.create(this);
    preparedSource = base.preparedSource;
  }

  /**
   * Adds only model-eligible, already saved DDL files to the existing entry-owned Corpus. A file
   * remains one complete physical source body and receives a use only for its saved literal SQL
   * TABLE observations; no synthetic entry is created.
   */
  public OntologyEvidenceCorpus withSchemaEvidence(
      JsonNode schemaEvidence, VerifiedSourceTextSet sourceTexts) {
    if (!(schemaEvidence instanceof ObjectNode evidence)
        || !OntologySchemaEvidence.SCHEMA_VERSION.equals(evidence.path("schemaVersion").asText())
        || !evidence.path("files").isArray()
        || sourceTexts == null
        || !sourceSnapshotId.equals(sourceTexts.snapshotId())) {
      throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
    }
    Map<String, VerifiedSourceTextDocument> sources = new LinkedHashMap<>();
    sourceTexts.documents().forEach(source -> sources.put(source.path(), source));
    Map<String, List<UnitHandle>> augmentedUnits = new LinkedHashMap<>();
    unitsByEntry.forEach(
        (entryId, handles) -> augmentedUnits.put(entryId, new ArrayList<>(handles)));
    Map<UnitHandle, EvidenceUnit> augmentedEvidence = new LinkedHashMap<>(evidenceByUse);
    for (JsonNode file : evidence.path("files")) {
      if (!file.path("modelEligible").asBoolean(false)) {
        continue;
      }
      String path = file.path("path").asText();
      String fileId = file.path("fileId").asText();
      VerifiedSourceTextDocument source = sources.get(path);
      if (source == null
          || !fileId.equals(source.fileId().value())
          || !file.path("sha256").asText().equals(source.sha256().value())
          || file.path("byteLength").asLong(-1) != source.sizeBytes()
          || !completeRange(file.path("range"), source.rawUtf8())) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
      }
      List<SchemaEntryUse> expectedUses = expectedSchemaEntryUses(file.path("declarations"));
      List<SchemaEntryUse> savedUses = savedSchemaEntryUses(file.path("entryUses"));
      if (savedUses.isEmpty() || !savedUses.equals(expectedUses)) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
      }
      ObjectNode unit = schemaUnit(file, source);
      ImmutableBytes canonical = json.encodeCanonical(unit);
      LinkedHashSet<String> entries = new LinkedHashSet<>();
      savedUses.forEach(use -> entries.add(use.entryId()));
      for (String entryId : entries) {
        EntryEvidenceReader.EntryDocument entry = documents.get(entryId);
        if (entry == null) {
          throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
        }
        UnitHandle handle = new UnitHandle(entryId, UnitKind.SCHEMA_SOURCE, fileId);
        EntryDescriptor descriptor = descriptor(entry);
        EvidenceUnit prior =
            augmentedEvidence.putIfAbsent(
                handle,
                new EvidenceUnit(
                    entryId, UnitKind.SCHEMA_SOURCE, fileId, canonical, Map.of(), descriptor));
        if (prior != null) {
          throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
        }
        List<UnitHandle> handles = augmentedUnits.get(entryId);
        if (handles == null || handles.contains(handle)) {
          throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
        }
        handles.add(handle);
      }
    }
    Map<String, List<UnitHandle>> frozen = new LinkedHashMap<>();
    augmentedUnits.forEach(
        (entryId, handles) -> {
          handles.sort(AliasCatalog.unitHandleOrder());
          frozen.put(entryId, List.copyOf(handles));
        });
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("baseSourceIdentity", sourceIdentity);
    // The installed standalone artifact adds only these transport-envelope fields.  The Corpus
    // identity is formed from the schema evidence content, so an exact reopened O0 must remove
    // that envelope before comparing it with the pre-install projection.
    ObjectNode identityEvidence = evidence.deepCopy();
    identityEvidence.remove("artifactType");
    identityEvidence.remove("artifactId");
    identity.set("schemaEvidence", identityEvidence);
    String augmentedIdentity =
        "ontology-schema-corpus:"
            + OntologyReadingPacket.sha256(json.encodeCanonical(identity).copyToByteArray());
    return new OntologyEvidenceCorpus(
        this, augmentedIdentity, frozen, augmentedEvidence, evidence.path("files").size());
  }

  /**
   * Adds exact R0 bodies for this new, source-prepared Corpus rule.
   *
   * <p>The caller persists only the resulting Corpus identity and aliases; bodies reach a model
   * packet only when their U/S units are selected. The supplied declaration catalog is an already
   * saved R2 result. This seam deliberately does not parse Java, derive a path from a type name, or
   * change an R4 navigation conclusion: an unconfirmed call remains an unconfirmed call even where
   * its saved physical location can be matched to one complete declaration.
   */
  public OntologyEvidenceCorpus withPreparedSourceBodies(
      VerifiedSourceTextSet sourceTexts,
      List<JavaDeclarationCatalog.MethodDeclarationView> savedDeclarations) {
    if (sourceTexts == null
        || savedDeclarations == null
        || !sourceSnapshotId.equals(sourceTexts.snapshotId())) {
      throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
    }
    Map<String, VerifiedSourceTextDocument> sources = preparedSourceDocuments(sourceTexts);
    Map<String, JavaDeclarationCatalog.MethodDeclarationView> declarations =
        savedDeclarationsByMethodKey(savedDeclarations);
    Map<String, List<UnitHandle>> augmentedUnits = mutableUnitsByEntry();
    Map<UnitHandle, EvidenceUnit> augmentedEvidence = new LinkedHashMap<>(evidenceByUse);
    List<UnitHandle> added = new ArrayList<>();

    for (Map.Entry<String, EntryEvidenceReader.EntryDocument> document : documents.entrySet()) {
      String entryId = document.getKey();
      JsonNode entry = json.parseCanonical(document.getValue().canonicalJson());
      for (JsonNode reference : entry.path("sourceRefs")) {
        String referenceId = reference.path("reference").asText();
        if (referenceId.isBlank()) {
          throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
        }
        VerifiedSourceTextDocument source = sourceForReference(reference, sources);
        UnitHandle handle = new UnitHandle(entryId, UnitKind.SOURCE_REFERENCE, referenceId);
        ObjectNode completed = reference.deepCopy();
        JavaDeclarationCatalog.MethodDeclarationView declaration =
            sourceReferenceDeclaration(reference, source, declarations);
        if (declaration == null) {
          completed.put("sourceText", utf8(source));
          preparedBody(completed, "FULL_FILE", null);
        } else {
          SourceRange range = declaration.sourceRange();
          String text = utf8(source);
          completed.put(
              "sourceText",
              text.substring(
                  range.startOffsetUtf16(), range.startOffsetUtf16() + range.lengthUtf16()));
          preparedBody(completed, "METHOD_DECLARATION", range);
        }
        replacePreparedEvidence(
            augmentedEvidence, added, handle, completed, "ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }

      for (JsonNode call : entry.path("java").path("calls")) {
        for (JsonNode observation : call.path("observations")) {
          JavaDeclarationCatalog.MethodDeclarationView declaration =
              uniquelyLocatedDeclaration(observation, declarations, sources);
          if (declaration == null) {
            continue;
          }
          UnitHandle handle =
              new UnitHandle(entryId, UnitKind.JAVA_METHOD, declaration.methodKey());
          if (evidenceByUse.containsKey(handle)) {
            // An exact R4 method remains the owner of its original unit. A saved R2 observation
            // may supplement only a genuinely absent method, never replace an R4 body.
            continue;
          }
          VerifiedSourceTextDocument source = sources.get(declaration.sourcePath());
          if (augmentedEvidence.containsKey(handle)) {
            appendPreparedCandidateObservation(
                augmentedEvidence, handle, observation, "ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
          } else {
            ObjectNode completed = preparedMethod(declaration, source, observation);
            addPreparedEvidence(
                augmentedUnits,
                augmentedEvidence,
                added,
                handle,
                completed,
                "ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
          }
        }
      }
    }
    return augmentedPreparedSourceCorpus(augmentedUnits, augmentedEvidence, added);
  }

  private Map<String, List<UnitHandle>> mutableUnitsByEntry() {
    Map<String, List<UnitHandle>> result = new LinkedHashMap<>();
    unitsByEntry.forEach((entryId, handles) -> result.put(entryId, new ArrayList<>(handles)));
    return result;
  }

  private static Map<String, VerifiedSourceTextDocument> preparedSourceDocuments(
      VerifiedSourceTextSet sourceTexts) {
    Map<String, VerifiedSourceTextDocument> sources = new LinkedHashMap<>();
    for (VerifiedSourceTextDocument source : sourceTexts.documents()) {
      if (sources.putIfAbsent(source.path(), source) != null) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
    }
    return Map.copyOf(sources);
  }

  private static Map<String, JavaDeclarationCatalog.MethodDeclarationView>
      savedDeclarationsByMethodKey(
          List<JavaDeclarationCatalog.MethodDeclarationView> savedDeclarations) {
    Map<String, JavaDeclarationCatalog.MethodDeclarationView> declarations = new LinkedHashMap<>();
    for (JavaDeclarationCatalog.MethodDeclarationView declaration : savedDeclarations) {
      if (declaration == null
          || declarations.putIfAbsent(declaration.methodKey(), declaration) != null) {
        throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
      }
    }
    return Map.copyOf(declarations);
  }

  private static VerifiedSourceTextDocument sourceForReference(
      JsonNode reference, Map<String, VerifiedSourceTextDocument> sources) {
    String path = reference.path("path").asText();
    VerifiedSourceTextDocument source = sources.get(path);
    if (path.isBlank() || source == null) {
      throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
    }
    String digest = nullableText(reference, "sourceSha256");
    String identity = nullableText(reference, "sourceIdentity");
    if ((digest != null && !digest.equals(source.sha256().value()))
        || (identity != null && !identity.equals(source.fileId().value()))) {
      throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
    }
    return source;
  }

  private static String nullableText(JsonNode value, String field) {
    JsonNode text = value.get(field);
    if (text == null || text.isNull()) {
      return null;
    }
    if (!text.isTextual() || text.textValue().isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
    }
    return text.textValue();
  }

  private static JavaDeclarationCatalog.MethodDeclarationView sourceReferenceDeclaration(
      JsonNode reference,
      VerifiedSourceTextDocument source,
      Map<String, JavaDeclarationCatalog.MethodDeclarationView> declarations) {
    SourceRange referenceRange = savedSourceRange(reference.path("range"));
    if (referenceRange == null) {
      return null;
    }
    String methodKey = null;
    if ("JAVA_METHOD".equals(reference.path("kind").asText())) {
      String referenceId = reference.path("reference").asText();
      if (!referenceId.startsWith("java-method:")
          || referenceId.length() == "java-method:".length()) {
        return null;
      }
      methodKey = referenceId.substring("java-method:".length());
    }
    List<JavaDeclarationCatalog.MethodDeclarationView> matches = new ArrayList<>();
    for (JavaDeclarationCatalog.MethodDeclarationView declaration : declarations.values()) {
      if ((methodKey == null || methodKey.equals(declaration.methodKey()))
          && sameRange(referenceRange, declaration.sourceRange())
          && source.path().equals(declaration.sourcePath())
          && declaration.hasBody()) {
        matches.add(declaration);
      }
    }
    if (matches.size() != 1) {
      return null;
    }
    String sourceText = utf8(source);
    if (!validSourceRange(referenceRange, sourceText)) {
      return null;
    }
    return completeDeclaration(matches.get(0), source, sourceText);
  }

  private static JavaDeclarationCatalog.MethodDeclarationView completeDeclaration(
      JavaDeclarationCatalog.MethodDeclarationView declaration,
      VerifiedSourceTextDocument source,
      String sourceText) {
    if (declaration == null
        || !source.path().equals(declaration.sourcePath())
        || !declaration.hasBody()
        || !validSourceRange(declaration.sourceRange(), sourceText)) {
      return null;
    }
    return declaration;
  }

  private static boolean sameRange(SourceRange left, SourceRange right) {
    return left.startOffsetUtf16() == right.startOffsetUtf16()
        && left.lengthUtf16() == right.lengthUtf16()
        && left.startLine() == right.startLine()
        && left.endLine() == right.endLine();
  }

  private static void preparedBody(ObjectNode unit, String kind, SourceRange range) {
    ObjectNode boundary = unit.putObject("preparedBody");
    boundary.put("kind", kind);
    if (range == null) {
      boundary.putNull("range");
      return;
    }
    ObjectNode savedRange = boundary.putObject("range");
    savedRange.put("startOffsetUtf16", range.startOffsetUtf16());
    savedRange.put("lengthUtf16", range.lengthUtf16());
    savedRange.put("startLine", range.startLine());
    savedRange.put("endLine", range.endLine());
  }

  private static JavaDeclarationCatalog.MethodDeclarationView uniquelyLocatedDeclaration(
      JsonNode observation,
      Map<String, JavaDeclarationCatalog.MethodDeclarationView> declarations,
      Map<String, VerifiedSourceTextDocument> sources) {
    if (!"REPOSITORY_SOURCE".equals(observation.path("uriKind").asText())
        || !"UNCONFIRMED".equals(observation.path("association").asText())
        || !"UNCONFIRMED_NAVIGATION_LOCATION".equals(observation.path("code").asText())
        || !"NAVIGATION_CONFLICT_NOT_EXPANDED".equals(observation.path("detail").asText())) {
      return null;
    }
    JsonNode savedRange = observation.path("sourceRange");
    String declarationKey = observation.path("declarationKey").asText();
    String displayIdentity = observation.path("displayIdentity").asText();
    List<JavaDeclarationCatalog.MethodDeclarationView> matches = new ArrayList<>();
    for (JavaDeclarationCatalog.MethodDeclarationView declaration : declarations.values()) {
      boolean exactDeclarationKey =
          !declarationKey.isBlank() && declarationKey.equals(declaration.methodKey());
      boolean exactDisplayIdentity = declarationDisplay(declaration).equals(displayIdentity);
      if (!exactDeclarationKey && !exactDisplayIdentity) {
        continue;
      }
      VerifiedSourceTextDocument source = sources.get(declaration.sourcePath());
      if (!declaration.hasBody()
          || source == null
          || !containedSavedRange(savedRange, declaration.sourceRange(), utf8(source))) {
        continue;
      }
      matches.add(declaration);
    }
    if (matches.size() != 1) {
      return null;
    }
    JavaDeclarationCatalog.MethodDeclarationView match = matches.get(0);
    return validSourceRange(match.sourceRange(), utf8(sources.get(match.sourcePath())))
        ? match
        : null;
  }

  /**
   * An R4 navigation observation identifies a physical site, while R2 supplies the complete
   * declaration boundary. The site must be an exactly serialized, fully validated UTF-16 range
   * contained by that declaration; it is not itself expected to equal the declaration range.
   */
  private static boolean containedSavedRange(
      JsonNode saved, SourceRange declaration, String source) {
    SourceRange observation = savedSourceRange(saved);
    if (observation == null
        || !validSourceRange(observation, source)
        || !validSourceRange(declaration, source)) {
      return false;
    }
    long observationEnd = (long) observation.startOffsetUtf16() + observation.lengthUtf16();
    long declarationEnd = (long) declaration.startOffsetUtf16() + declaration.lengthUtf16();
    return observation.startOffsetUtf16() >= declaration.startOffsetUtf16()
        && observationEnd <= declarationEnd;
  }

  private static SourceRange savedSourceRange(JsonNode saved) {
    if (saved == null || !saved.isObject()) {
      return null;
    }
    Integer start = integralInt(saved.get("startOffsetUtf16"));
    Integer length = integralInt(saved.get("lengthUtf16"));
    Integer startLine = integralInt(saved.get("startLine"));
    Integer endLine = integralInt(saved.get("endLine"));
    if (start == null
        || length == null
        || startLine == null
        || endLine == null
        || start < 0
        || length < 0
        || startLine < 1
        || endLine < startLine) {
      return null;
    }
    return new SourceRange(start, length, startLine, endLine);
  }

  private static Integer integralInt(JsonNode value) {
    return value != null && value.isIntegralNumber() && value.canConvertToInt()
        ? value.intValue()
        : null;
  }

  private static boolean validSourceRange(SourceRange range, String source) {
    int start = range.startOffsetUtf16();
    long end = (long) start + range.lengthUtf16();
    if (start < 0
        || range.lengthUtf16() < 0
        || range.startLine() < 1
        || range.endLine() < range.startLine()
        || end > source.length()) {
      return false;
    }
    int startLine = lineAt(source, start);
    int endLine = lineAt(source, range.lengthUtf16() == 0 ? start : (int) end - 1);
    return startLine == range.startLine() && endLine == range.endLine();
  }

  private static int lineAt(String source, int offset) {
    int line = 1;
    for (int index = 0; index < offset; index++) {
      if (source.charAt(index) == '\n') {
        line++;
      }
    }
    return line;
  }

  private static String declarationDisplay(
      JavaDeclarationCatalog.MethodDeclarationView declaration) {
    String parameters =
        declaration.parameters().stream()
            .map(parameter -> parameter.typeText() + (parameter.varArgs() ? "..." : ""))
            .collect(java.util.stream.Collectors.joining(", "));
    return declaration.declaringType()
        + "."
        + declaration.name()
        + "("
        + parameters
        + ") : "
        + declaration.returnTypeText();
  }

  private static ObjectNode preparedMethod(
      JavaDeclarationCatalog.MethodDeclarationView declaration,
      VerifiedSourceTextDocument source,
      JsonNode observation) {
    String text = utf8(source);
    SourceRange range = declaration.sourceRange();
    ObjectNode method = JsonNodeFactory.instance.objectNode();
    method.put("methodKey", declaration.methodKey());
    method.put("declaringType", declaration.declaringType());
    method.put("name", declaration.name());
    method.put("signature", declarationDisplay(declaration));
    method.put("returnTypeText", declaration.returnTypeText());
    ArrayNode modifiers = method.putArray("modifiers");
    declaration.modifiers().forEach(modifiers::add);
    ArrayNode annotations = method.putArray("annotations");
    declaration.annotationKeys().forEach(annotations::add);
    ArrayNode parameters = method.putArray("parameters");
    for (JavaDeclarationCatalog.ParameterView parameter : declaration.parameters()) {
      ObjectNode item = parameters.addObject();
      item.put("ordinal", parameter.ordinal());
      item.put("name", parameter.name());
      item.put("typeText", parameter.typeText());
      item.put("varArgs", parameter.varArgs());
      ArrayNode parameterAnnotations = item.putArray("annotationTexts");
      parameter.annotationTexts().forEach(parameterAnnotations::add);
    }
    method.putArray("controls");
    method.putArray("exits");
    ObjectNode methodSource = method.putObject("source");
    methodSource.put("path", declaration.sourcePath());
    methodSource.put("sha256", source.sha256().value());
    methodSource.put("sourceIdentity", source.fileId().value());
    methodSource.put("startOffsetUtf16", range.startOffsetUtf16());
    methodSource.put("lengthUtf16", range.lengthUtf16());
    methodSource.put("startLine", range.startLine());
    methodSource.put("endLine", range.endLine());
    methodSource.put(
        "text",
        text.substring(range.startOffsetUtf16(), range.startOffsetUtf16() + range.lengthUtf16()));
    ObjectNode supplement = method.putObject("sourceSupplement");
    supplement.put("evidenceNature", "UNCONFIRMED_COMPLETE_DECLARATION_CANDIDATE");
    supplement.put("doesNotConfirmCallEdge", true);
    appendCandidateObservation(supplement.putArray("observations"), observation);
    return method;
  }

  private void appendPreparedCandidateObservation(
      Map<UnitHandle, EvidenceUnit> augmentedEvidence,
      UnitHandle handle,
      JsonNode observation,
      String failureCode) {
    EvidenceUnit prior = augmentedEvidence.get(handle);
    if (prior == null) {
      throw new IllegalArgumentException(failureCode);
    }
    JsonNode parsed = json.parseCanonical(prior.canonicalJson());
    if (!(parsed instanceof ObjectNode method)
        || !(method.get("sourceSupplement") instanceof ObjectNode supplement)
        || !"UNCONFIRMED_COMPLETE_DECLARATION_CANDIDATE"
            .equals(supplement.path("evidenceNature").asText())
        || !supplement.path("doesNotConfirmCallEdge").asBoolean(false)
        || !(supplement.get("observations") instanceof ArrayNode observations)) {
      throw new IllegalArgumentException(failureCode);
    }
    ObjectNode candidate = candidateObservation(observation);
    for (JsonNode existing : observations) {
      if (existing.equals(candidate)) {
        return;
      }
    }
    observations.add(candidate);
    augmentedEvidence.put(
        handle,
        new EvidenceUnit(
            prior.entryId(),
            prior.kind(),
            prior.originalId(),
            json.encodeCanonical(method),
            prior.limitationCounts(),
            prior.entryDescriptor()));
  }

  private static void appendCandidateObservation(ArrayNode observations, JsonNode observation) {
    observations.add(candidateObservation(observation));
  }

  private static ObjectNode candidateObservation(JsonNode observation) {
    ObjectNode result = JsonNodeFactory.instance.objectNode();
    result.put("uriKind", observation.path("uriKind").asText());
    result.put("association", observation.path("association").asText());
    result.put("code", observation.path("code").asText());
    result.put("detail", observation.path("detail").asText());
    copyNullableText(observation, result, "declarationKey");
    copyNullableText(observation, result, "displayIdentity");
    copyNullableText(observation, result, "typeOrigin");
    result.set("sourceRange", observation.path("sourceRange").deepCopy());
    return result;
  }

  private static void copyNullableText(JsonNode source, ObjectNode target, String field) {
    JsonNode value = source.get(field);
    if (value == null || value.isNull()) {
      target.putNull(field);
    } else if (value.isTextual()) {
      target.put(field, value.textValue());
    } else {
      throw new IllegalArgumentException("ONTOLOGY_PREPARED_SOURCE_BODY_INVALID");
    }
  }

  private void addPreparedEvidence(
      Map<String, List<UnitHandle>> augmentedUnits,
      Map<UnitHandle, EvidenceUnit> augmentedEvidence,
      List<UnitHandle> added,
      UnitHandle handle,
      ObjectNode content,
      String failureCode) {
    List<UnitHandle> handles = augmentedUnits.get(handle.entryId());
    if (handles == null || handles.contains(handle)) {
      throw new IllegalArgumentException(failureCode);
    }
    EvidenceUnit entryMetadata =
        exactEntryMetadata(augmentedEvidence, handle.entryId(), handles, failureCode);
    ImmutableBytes canonical = json.encodeCanonical(content);
    if (augmentedEvidence.putIfAbsent(
            handle,
            new EvidenceUnit(
                handle.entryId(),
                handle.kind(),
                handle.originalId(),
                canonical,
                entryMetadata.limitationCounts(),
                entryMetadata.entryDescriptor()))
        != null) {
      throw new IllegalArgumentException(failureCode);
    }
    handles.add(handle);
    added.add(handle);
  }

  private void replacePreparedEvidence(
      Map<UnitHandle, EvidenceUnit> augmentedEvidence,
      List<UnitHandle> added,
      UnitHandle handle,
      ObjectNode content,
      String failureCode) {
    EvidenceUnit prior = augmentedEvidence.get(handle);
    if (prior == null) {
      throw new IllegalArgumentException(failureCode);
    }
    ImmutableBytes canonical = json.encodeCanonical(content);
    augmentedEvidence.put(
        handle,
        new EvidenceUnit(
            prior.entryId(),
            prior.kind(),
            prior.originalId(),
            canonical,
            prior.limitationCounts(),
            prior.entryDescriptor()));
    added.add(handle);
  }

  private static EvidenceUnit exactEntryMetadata(
      Map<UnitHandle, EvidenceUnit> evidenceByUse,
      String entryId,
      List<UnitHandle> entryHandles,
      String failureCode) {
    EvidenceUnit metadata = null;
    for (UnitHandle entryHandle : entryHandles) {
      EvidenceUnit candidate = evidenceByUse.get(entryHandle);
      if (!entryId.equals(entryHandle.entryId())
          || candidate == null
          || !entryId.equals(candidate.entryId())) {
        throw new IllegalArgumentException(failureCode);
      }
      if (metadata == null) {
        metadata = candidate;
      } else if (!metadata.limitationCounts().equals(candidate.limitationCounts())
          || !metadata.entryDescriptor().equals(candidate.entryDescriptor())) {
        throw new IllegalArgumentException(failureCode);
      }
    }
    if (metadata == null) {
      throw new IllegalArgumentException(failureCode);
    }
    return metadata;
  }

  private OntologyEvidenceCorpus augmentedPreparedSourceCorpus(
      Map<String, List<UnitHandle>> augmentedUnits,
      Map<UnitHandle, EvidenceUnit> augmentedEvidence,
      List<UnitHandle> added) {
    Map<String, List<UnitHandle>> frozen = freezeUnits(augmentedUnits);
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("baseSourceIdentity", sourceIdentity);
    ArrayNode additions = identity.putArray("preparedSourceBodies");
    added.stream()
        .sorted(AliasCatalog.unitHandleOrder())
        .forEach(
            handle -> {
              EvidenceUnit evidence = augmentedEvidence.get(handle);
              ObjectNode item = additions.addObject();
              item.put("entryId", handle.entryId());
              item.put("kind", handle.kind().name());
              item.put("originalId", handle.originalId());
              item.put(
                  "contentSha256",
                  OntologyReadingPacket.sha256(evidence.canonicalJson().copyToByteArray()));
            });
    String augmentedIdentity =
        "ontology-prepared-source-corpus:"
            + OntologyReadingPacket.sha256(json.encodeCanonical(identity).copyToByteArray());
    return new OntologyEvidenceCorpus(
        this, augmentedIdentity, frozen, augmentedEvidence, schemaSourceCount);
  }

  private static Map<String, List<UnitHandle>> freezeUnits(
      Map<String, List<UnitHandle>> augmentedUnits) {
    Map<String, List<UnitHandle>> frozen = new LinkedHashMap<>();
    augmentedUnits.forEach(
        (entryId, handles) -> {
          handles.sort(AliasCatalog.unitHandleOrder());
          frozen.put(entryId, List.copyOf(handles));
        });
    return frozen;
  }

  private static String utf8(VerifiedSourceTextDocument source) {
    return new String(source.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
  }

  /** Returns the exact saved R4 SQL observations for literal DDL table names. */
  public List<SchemaEntryUse> schemaEntryUses(String tableName) {
    if (tableName == null || tableName.isBlank()) {
      return List.of();
    }
    List<SchemaEntryUse> uses = new ArrayList<>();
    for (UnitHandle handle : statementsByTable.getOrDefault(tableName, List.of())) {
      EvidenceUnit unit = evidenceByUse.get(handle);
      if (unit == null || handle.kind() != UnitKind.SQL_ANALYSIS) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
      }
      JsonNode analysis = json.parseCanonical(unit.canonicalJson());
      String status = analysis.path("status").asText();
      if (status.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
      }
      uses.add(new SchemaEntryUse(handle.entryId(), tableName, handle.originalId(), status));
    }
    uses.sort(
        Comparator.comparing(SchemaEntryUse::entryId)
            .thenComparing(SchemaEntryUse::sqlUnitId)
            .thenComparing(SchemaEntryUse::matchedTableValue));
    return List.copyOf(uses);
  }

  private List<SchemaEntryUse> expectedSchemaEntryUses(JsonNode declarations) {
    if (!declarations.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
    }
    Map<String, SchemaEntryUse> uses = new LinkedHashMap<>();
    for (JsonNode declaration : declarations) {
      String table = declaration.path("tableName").asText();
      if (table.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
      }
      for (SchemaEntryUse use : schemaEntryUses(table)) {
        uses.putIfAbsent(schemaEntryUseKey(use), use);
      }
    }
    List<SchemaEntryUse> values = new ArrayList<>(uses.values());
    values.sort(
        Comparator.comparing(SchemaEntryUse::entryId)
            .thenComparing(SchemaEntryUse::sqlUnitId)
            .thenComparing(SchemaEntryUse::matchedTableValue));
    return List.copyOf(values);
  }

  private static List<SchemaEntryUse> savedSchemaEntryUses(JsonNode uses) {
    if (!uses.isArray()) {
      throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
    }
    List<SchemaEntryUse> values = new ArrayList<>();
    for (JsonNode use : uses) {
      String entryId = use.path("entryId").asText();
      String table = use.path("matchedTableValue").asText();
      String sqlUnitId = use.path("sqlUnitId").asText();
      String status = use.path("sqlStatus").asText();
      if (entryId.isBlank()
          || table.isBlank()
          || sqlUnitId.isBlank()
          || status.isBlank()
          || !"TABLE_MATCH_CANDIDATE".equals(use.path("associationStatus").asText())) {
        throw new IllegalArgumentException("ONTOLOGY_SCHEMA_EVIDENCE_INVALID");
      }
      values.add(new SchemaEntryUse(entryId, table, sqlUnitId, status));
    }
    values.sort(
        Comparator.comparing(SchemaEntryUse::entryId)
            .thenComparing(SchemaEntryUse::sqlUnitId)
            .thenComparing(SchemaEntryUse::matchedTableValue));
    return List.copyOf(values);
  }

  private static String schemaEntryUseKey(SchemaEntryUse use) {
    return use.entryId() + "\n" + use.sqlUnitId() + "\n" + use.matchedTableValue();
  }

  private static boolean completeRange(JsonNode range, ImmutableBytes source) {
    return range.path("startOffsetUtf16").asInt(-1) == 0
        && range.path("lengthUtf16").asInt(-1)
            == new String(source.copyToByteArray(), StandardCharsets.UTF_8).length();
  }

  private static ObjectNode schemaUnit(JsonNode file, VerifiedSourceTextDocument source) {
    ObjectNode unit = JsonNodeFactory.instance.objectNode();
    unit.put("fileId", source.fileId().value());
    unit.put("path", source.path());
    unit.put("sha256", source.sha256().value());
    unit.set("range", file.path("range").deepCopy());
    unit.put("evidenceNature", "DDL_DECLARED");
    unit.put("associationStatus", "TABLE_MATCH_CANDIDATE");
    unit.put("sourceText", new String(source.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8));
    unit.set("declarations", file.path("declarations").deepCopy());
    unit.set("entryUses", file.path("entryUses").deepCopy());
    unit.set("limitations", file.path("limitations").deepCopy());
    return unit;
  }

  private EntryDescriptor descriptor(EntryEvidenceReader.EntryDocument document) {
    JsonNode entry = json.parseCanonical(document.canonicalJson()).path("entry");
    return new EntryDescriptor(
        entry.path("method").asText(),
        entry.path("route").asText(),
        entry.path("handlerFqn").asText());
  }

  private static void indexSqlAst(
      JsonNode node,
      UnitHandle handle,
      Map<String, LinkedHashSet<UnitHandle>> tables,
      Map<String, LinkedHashSet<UnitHandle>> columns) {
    if (!node.isObject()) {
      return;
    }
    String kind = node.path("kind").asText();
    String value = node.path("value").asText();
    if ("TABLE".equals(kind)) {
      add(tables, value, handle);
    } else if ("COLUMN".equals(kind)) {
      add(columns, value, handle);
    }
    for (JsonNode child : node.path("children")) {
      indexSqlAst(child, handle, tables, columns);
    }
  }

  private static void add(
      Map<String, LinkedHashSet<UnitHandle>> index, String key, UnitHandle handle) {
    if (key != null && !key.isBlank() && !handle.originalId().isBlank()) {
      index.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(handle);
    }
  }

  private static Map<String, List<UnitHandle>> freeze(
      Map<String, LinkedHashSet<UnitHandle>> index) {
    Map<String, List<UnitHandle>> result = new LinkedHashMap<>();
    index.forEach((key, handles) -> result.put(key, List.copyOf(handles)));
    return Map.copyOf(result);
  }

  private static Map<String, List<UnitHandle>> methodIndex(
      Map<String, List<UnitHandle>> unitsByEntry) {
    Map<String, LinkedHashSet<UnitHandle>> methods = new LinkedHashMap<>();
    for (List<UnitHandle> handles : unitsByEntry.values()) {
      for (UnitHandle handle : handles) {
        if (handle.kind() == UnitKind.JAVA_METHOD) {
          add(methods, handle.originalId(), handle);
        }
      }
    }
    return freeze(methods);
  }

  private static void addVariant(Map<String, LinkedHashSet<String>> index, JsonNode value) {
    String statementRef = value.path("statementRef").asText();
    if (!statementRef.isBlank()) {
      index
          .computeIfAbsent(statementRef, ignored -> new LinkedHashSet<>())
          .add(statementVariantId(value));
    }
  }

  private static Map<String, List<String>> freezeStrings(Map<String, LinkedHashSet<String>> index) {
    Map<String, List<String>> result = new LinkedHashMap<>();
    index.forEach((key, values) -> result.put(key, List.copyOf(values)));
    return Map.copyOf(result);
  }

  /** Verifies the complete R4 directory once; subsequent reads use that immutable snapshot. */
  public static OntologyEvidenceCorpus open(
      EntryEvidenceReader reader, AnalysisStepPublicationReference reference) {
    EntryEvidenceReader verifiedReader = Objects.requireNonNull(reader, "entry-evidence reader");
    try {
      // Each reader profile validates the installed module receipt and directory header. This is
      // finite version dispatch, not a missing-field compatibility guess.
      return fromVerifiedDirectory(verifiedReader.reopen(reference));
    } catch (IllegalArgumentException v1Failure) {
      try {
        return fromVerifiedDirectory(verifiedReader.reopenV2(reference));
      } catch (IllegalArgumentException v2Failure) {
        v1Failure.addSuppressed(v2Failure);
        throw v1Failure;
      }
    }
  }

  /** Test seam: the caller must have obtained this directory from the strict R4 reader. */
  static OntologyEvidenceCorpus fromVerifiedDirectory(EntryEvidenceReader.Directory directory) {
    return new OntologyEvidenceCorpus(Objects.requireNonNull(directory, "verified R4 directory"));
  }

  public NavigationPage navigation(int offset, int limit) {
    if (offset < 0 || limit < 1 || offset > navigation.size()) {
      throw new IllegalArgumentException("ONTOLOGY_NAVIGATION_RANGE_INVALID");
    }
    int end = (int) Math.min((long) navigation.size(), (long) offset + limit);
    return new NavigationPage(offset, limit, navigation.size(), navigation.subList(offset, end));
  }

  public EntrySummary entrySummary(String entryId) {
    return navigation.stream()
        .filter(entry -> entry.entryId().equals(entryId))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_ENTRY_NOT_FOUND"));
  }

  /**
   * Compact actual identity and canonical size for navigation; it never returns the full unit body.
   */
  public UnitNavigationMetadata unitMetadata(UnitHandle handle) {
    EvidenceUnit unit = read(handle.entryId(), handle.kind(), handle.originalId());
    JsonNode content = unit.content();
    String keyDisplay =
        switch (handle.kind()) {
          case JAVA_METHOD -> compactMethodDisplay(content);
          case PERSISTENCE_BINDING ->
              content.path("javaInterfaceFqn").asText()
                  + "#"
                  + content.path("methodSignature").asText();
          case XML_STATEMENT ->
              content.path("namespace").asText()
                  + "."
                  + content.path("statementId").asText()
                  + databaseSuffix(content.path("databaseId"));
          case SQL_ANALYSIS -> content.path("statementRef").asText();
          case FRONTEND_PAGE_CONTEXT ->
              content.path("pagePath").asText()
                  + "#"
                  + content.path("instanceKey").asText()
                  + ":PAGE_CONTEXT";
          default -> handle.kind().name() + ":" + handle.originalId();
        };
    return new UnitNavigationMetadata(keyDisplay, unit.canonicalJson().size());
  }

  private static String databaseSuffix(JsonNode databaseId) {
    String value = databaseId.asText();
    return value.isBlank() ? "" : "@" + value;
  }

  public String sourceIdentity() {
    return sourceIdentity;
  }

  public Statistics statistics() {
    return statistics;
  }

  /** Count of distinct saved R4 frontend request coverage records, including unmatched requests. */
  public int frontendRequestCount() {
    return frontendRequestCount;
  }

  /** Count of selected, saved O0 DDL files, including unsupported and unmatched files. */
  public int schemaSourceCount() {
    return schemaSourceCount;
  }

  /** Stable, reversible E/U/K aliases for this immutable Corpus content. */
  public AliasCatalog aliases() {
    return aliases;
  }

  /** Separate navigation rules for new business-link Corpus publications. */
  public OntologyEvidenceCorpus withBusinessLinkNavigation() {
    if (businessLinkNavigation) {
      return this;
    }
    Map<String, List<UnitHandle>> augmentedUnits = new LinkedHashMap<>();
    Map<UnitHandle, EvidenceUnit> augmentedEvidence = new LinkedHashMap<>(evidenceByUse);
    unitsByEntry.forEach(
        (entryId, handles) -> {
          LinkedHashSet<UnitHandle> selected = new LinkedHashSet<>(handles);
          List<EvidenceUnit> frontendSources =
              handles.stream()
                  .filter(handle -> handle.kind() == UnitKind.FRONTEND_UNIT)
                  .map(evidenceByUse::get)
                  .toList();
          for (JsonNode payload : frontendContextCoverage.values()) {
            JsonNode context = payload.path("context");
            boolean related = false;
            for (JsonNode declaredSource : context.path("sourceUnits")) {
              if (frontendSources.stream()
                  .anyMatch(unit -> sameFrontendPhysicalUnit(declaredSource, unit.content()))) {
                related = true;
                break;
              }
            }
            if (!related) {
              continue;
            }
            // A physical-source candidate is not a proven HTTP match. Its original context,
            // request IDs and saved coverage association remain unchanged in the model packet.
            EvidenceUnit basis = frontendSources.get(0);
            addContextEvidence(
                entryId,
                UnitKind.FRONTEND_PAGE_CONTEXT,
                context.path("contextId").asText(),
                context,
                basis,
                selected,
                augmentedEvidence);
            for (JsonNode source : payload.path("units")) {
              if (!hasCompleteFrontendText(source)) {
                throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_MISSING");
              }
              addContextEvidence(
                  entryId,
                  UnitKind.FRONTEND_UNIT,
                  source.path("sourceUnitId").asText(),
                  source,
                  basis,
                  selected,
                  augmentedEvidence);
            }
          }
          augmentedUnits.put(entryId, List.copyOf(selected));
        });
    return new OntologyEvidenceCorpus(
        this, sourceIdentity, augmentedUnits, augmentedEvidence, schemaSourceCount, true);
  }

  /** Explicit new navigation family; historical Corpus instances remain false. */
  public boolean usesBusinessLinkNavigation() {
    return businessLinkNavigation;
  }

  public UnitPage controlUses(String expression, int offset, int limit) {
    if (!businessLinkNavigation) {
      throw new IllegalArgumentException("ONTOLOGY_NAVIGATION_RANGE_INVALID");
    }
    return unitPage(controlsByExpression, expression, offset, limit);
  }

  private void addContextEvidence(
      String entryId,
      UnitKind kind,
      String id,
      JsonNode content,
      EvidenceUnit basis,
      Set<UnitHandle> selected,
      Map<UnitHandle, EvidenceUnit> evidence) {
    if (id.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_MISSING");
    }
    UnitHandle handle = new UnitHandle(entryId, kind, id);
    EvidenceUnit addition =
        new EvidenceUnit(
            entryId,
            kind,
            id,
            json.encodeCanonical(content),
            basis.limitationCounts(),
            basis.entryDescriptor());
    EvidenceUnit previous = evidence.putIfAbsent(handle, addition);
    if (previous != null && !previous.canonicalJson().equals(addition.canonicalJson())) {
      throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_CONFLICT");
    }
    selected.add(handle);
  }

  private Map<String, JsonNode> savedPageContexts(ImmutableBytes coverage) {
    Map<String, JsonNode> contexts = new TreeMap<>();
    for (String line :
        new String(coverage.copyToByteArray(), StandardCharsets.UTF_8).split("\\n")) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode record =
          json.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
      if ("PAGE_CONTEXT_COVERAGE".equals(record.path("recordType").asText())) {
        JsonNode payload = record.path("payload");
        String id = payload.path("contextId").asText();
        if (id.isBlank()
            || !id.equals(payload.path("context").path("contextId").asText())
            || !payload.path("units").isArray()
            || contexts.putIfAbsent(id, payload) != null) {
          throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_CONFLICT");
        }
      }
    }
    return Map.copyOf(contexts);
  }

  /** Exact entry-local page contexts using this physical frontend source unit. */
  public List<UnitHandle> frontendPageContexts(UnitHandle source) {
    Objects.requireNonNull(source, "frontend source unit");
    if (source.kind() != UnitKind.FRONTEND_UNIT || !evidenceByUse.containsKey(source)) {
      return List.of();
    }
    JsonNode physical = evidenceByUse.get(source).content();
    List<UnitHandle> result = new ArrayList<>();
    for (UnitHandle handle : unitsByEntry.getOrDefault(source.entryId(), List.of())) {
      if (handle.kind() != UnitKind.FRONTEND_PAGE_CONTEXT) {
        continue;
      }
      JsonNode context = evidenceByUse.get(handle).content();
      for (JsonNode contextUnit : context.path("sourceUnits")) {
        if (validFrontendContextUnitIdentity(contextUnit)
            && sameFrontendPhysicalUnit(contextUnit, physical)) {
          result.add(handle);
          break;
        }
      }
    }
    result.sort(AliasCatalog.unitHandleOrder());
    return List.copyOf(result);
  }

  private List<ClueKind> navigationClueKinds() {
    return Arrays.stream(ClueKind.values())
        .filter(kind -> businessLinkNavigation || kind != ClueKind.CONTROL_REFERENCE)
        .toList();
  }

  private static Map<String, List<UnitHandle>> controlIndex(
      Map<UnitHandle, EvidenceUnit> evidence) {
    Map<String, LinkedHashSet<UnitHandle>> controls = new LinkedHashMap<>();
    evidence.forEach(
        (handle, unit) -> {
          if (handle.kind() == UnitKind.JAVA_METHOD) {
            for (JsonNode control : unit.content().path("controls")) {
              String expression = savedControlExpression(control);
              if (expression != null) {
                add(controls, expression, handle);
              }
            }
          }
        });
    return freeze(controls);
  }

  private static String savedControlExpression(JsonNode control) {
    if (!Set.of("IF", "SWITCH", "LOOP").contains(control.path("kind").asText())
        || !control.path("expression").isTextual()) {
      return null;
    }
    String expression = control.path("expression").asText().trim();
    return expression.isEmpty() ? null : expression;
  }

  public UnitPage methodUses(String methodKey, int offset, int limit) {
    return unitPage(methodsByIdentity, methodKey, offset, limit);
  }

  public UnitPage entryUnits(String entryId, int offset, int limit) {
    if (!unitsByEntry.containsKey(entryId)) {
      throw new IllegalArgumentException("ONTOLOGY_ENTRY_NOT_FOUND");
    }
    return unitPage(unitsByEntry, entryId, offset, limit);
  }

  public UnitPage statementUses(String statementRef, int offset, int limit) {
    if (bindingsByStatement.containsKey(statementRef)) {
      return unitPage(bindingsByStatement, statementRef, offset, limit);
    }
    List<String> variants = statementVariantsByReference.getOrDefault(statementRef, List.of());
    if (variants.size() > 1) {
      throw new IllegalArgumentException("ONTOLOGY_UNIT_AMBIGUOUS");
    }
    return unitPage(
        bindingsByStatement, variants.isEmpty() ? statementRef : variants.get(0), offset, limit);
  }

  public UnitPage tableStatements(String tableName, int offset, int limit) {
    return unitPage(statementsByTable, tableName, offset, limit);
  }

  public UnitPage columnStatements(String columnName, int offset, int limit) {
    return unitPage(statementsByColumn, columnName, offset, limit);
  }

  /** Bounded, source-backed clues for one entry card; these are navigation only, never facts. */
  public EntryCluePage entryClues(String entryId, int perKindLimit) {
    if (perKindLimit < 1 || !documents.containsKey(entryId)) {
      throw new IllegalArgumentException("ONTOLOGY_ENTRY_NOT_FOUND");
    }
    JsonNode entry = json.parseCanonical(documents.get(entryId).canonicalJson());
    Map<ClueKind, LinkedHashMap<String, NavigationClue>> byKind = new LinkedHashMap<>();
    for (ClueKind kind : navigationClueKinds()) {
      byKind.put(kind, new LinkedHashMap<>());
    }
    for (JsonNode method : entry.path("java").path("methods")) {
      String methodKey = method.path("methodKey").asText();
      String display = compactMethodDisplay(method);
      addClue(
          byKind.get(ClueKind.METHOD),
          methodKey,
          new NavigationClue(
              ClueKind.METHOD,
              display,
              methodKey,
              new UnitHandle(entryId, UnitKind.JAVA_METHOD, methodKey),
              methodsByIdentity.getOrDefault(methodKey, List.of()).size(),
              json.encodeCanonical(method).size()));
      if (businessLinkNavigation) {
        for (JsonNode control : method.path("controls")) {
          String expression = savedControlExpression(control);
          if (expression != null) {
            addClue(
                byKind.get(ClueKind.CONTROL_REFERENCE),
                expression,
                new NavigationClue(
                    ClueKind.CONTROL_REFERENCE,
                    expression,
                    expression,
                    new UnitHandle(entryId, UnitKind.JAVA_METHOD, methodKey),
                    controlsByExpression.getOrDefault(expression, List.of()).size(),
                    json.encodeCanonical(method).size()));
          }
        }
      }
    }
    for (JsonNode statement : entry.path("persistence").path("statements")) {
      String statementRef = originalId(statement, UnitKind.XML_STATEMENT);
      JsonNode databaseIdNode = statement.path("databaseId");
      String databaseId = databaseIdNode.isTextual() ? databaseIdNode.asText() : "";
      String display =
          statement.path("namespace").asText()
              + "."
              + statement.path("statementId").asText()
              + (databaseId.isBlank() ? "" : "@" + databaseId);
      addClue(
          byKind.get(ClueKind.STATEMENT),
          statementRef,
          new NavigationClue(
              ClueKind.STATEMENT,
              display,
              statementRef,
              new UnitHandle(entryId, UnitKind.XML_STATEMENT, statementRef),
              bindingsByStatement.getOrDefault(statementRef, List.of()).size(),
              json.encodeCanonical(statement).size()));
    }
    int sqlAnalysesWithoutAst = 0;
    for (JsonNode analysis : entry.path("persistence").path("sqlAnalyses")) {
      UnitHandle handle =
          new UnitHandle(entryId, UnitKind.SQL_ANALYSIS, unitId(analysis, UnitKind.SQL_ANALYSIS));
      JsonNode ast = analysis.path("ast");
      if (!ast.isObject()) {
        sqlAnalysesWithoutAst++;
        continue;
      }
      collectAstClues(ast, handle, json.encodeCanonical(analysis).size(), byKind);
    }
    List<NavigationClue> shown = new ArrayList<>();
    Map<ClueKind, ClueDisclosure> disclosure = new LinkedHashMap<>();
    for (ClueKind kind : navigationClueKinds()) {
      List<NavigationClue> clues =
          byKind.get(kind).values().stream()
              .sorted(java.util.Comparator.comparing(NavigationClue::lookupKey))
              .toList();
      int count = Math.min(perKindLimit, clues.size());
      shown.addAll(clues.subList(0, count));
      disclosure.put(kind, new ClueDisclosure(clues.size(), count));
    }
    return new EntryCluePage(shown, disclosure, sqlAnalysesWithoutAst);
  }

  private void collectAstClues(
      JsonNode node,
      UnitHandle handle,
      int unitBytes,
      Map<ClueKind, LinkedHashMap<String, NavigationClue>> byKind) {
    if (!node.isObject()) {
      return;
    }
    String nodeKind = node.path("kind").asText();
    String value = node.path("value").asText();
    if ("TABLE".equals(nodeKind)) {
      addClue(
          byKind.get(ClueKind.TABLE),
          value,
          new NavigationClue(
              ClueKind.TABLE,
              value,
              value,
              handle,
              statementsByTable.getOrDefault(value, List.of()).size(),
              unitBytes));
    } else if ("COLUMN".equals(nodeKind)) {
      addClue(
          byKind.get(ClueKind.COLUMN),
          value,
          new NavigationClue(
              ClueKind.COLUMN,
              value,
              value,
              handle,
              statementsByColumn.getOrDefault(value, List.of()).size(),
              unitBytes));
    }
    for (JsonNode child : node.path("children")) {
      collectAstClues(child, handle, unitBytes, byKind);
    }
  }

  private static String compactMethodDisplay(JsonNode method) {
    List<String> parameterTypes = new ArrayList<>();
    for (JsonNode parameter : method.path("parameters")) {
      String type = parameter.path("typeText").asText(parameter.path("type").asText());
      parameterTypes.add(type.isBlank() ? "?" : type);
    }
    return method.path("declaringType").asText()
        + "#"
        + method.path("name").asText()
        + "("
        + String.join(",", parameterTypes)
        + ")";
  }

  private static void addClue(
      Map<String, NavigationClue> clues, String key, NavigationClue candidate) {
    if (key != null && !key.isBlank()) {
      clues.putIfAbsent(key, candidate);
    }
  }

  private static UnitPage unitPage(
      Map<String, List<UnitHandle>> index, String key, int offset, int limit) {
    if (key == null || key.isBlank() || offset < 0 || limit < 1) {
      throw new IllegalArgumentException("ONTOLOGY_NAVIGATION_RANGE_INVALID");
    }
    List<UnitHandle> matches = index.getOrDefault(key, List.of());
    if (offset > matches.size()) {
      throw new IllegalArgumentException("ONTOLOGY_NAVIGATION_RANGE_INVALID");
    }
    int end = (int) Math.min((long) matches.size(), (long) offset + limit);
    return new UnitPage(offset, limit, matches.size(), matches.subList(offset, end));
  }

  /** Reads only a named line range from the same verified R0 source, never from a checkout. */
  public SourceExcerpt readPreparedSource(
      VerifiedSourceTextReader reader,
      String relativePath,
      int startLine,
      int endLine,
      int maxUtf8Bytes) {
    if (relativePath == null
        || relativePath.isBlank()
        || relativePath.startsWith("/")
        || relativePath.contains("..")) {
      throw new IllegalArgumentException("ONTOLOGY_SOURCE_PATH_INVALID");
    }
    VerifiedSourceTextSet source = preparedSource(reader);
    VerifiedSourceTextDocument document =
        source.documents().stream()
            .filter(item -> relativePath.equals(item.path()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("ONTOLOGY_SOURCE_UNAVAILABLE"));
    return new SourceExcerpt(
        relativePath,
        startLine,
        endLine,
        document.sha256().value(),
        extractLines(document.rawUtf8().copyToByteArray(), startLine, endLine, maxUtf8Bytes));
  }

  private synchronized VerifiedSourceTextSet preparedSource(VerifiedSourceTextReader reader) {
    if (preparedSource == null) {
      Objects.requireNonNull(reader, "prepared source reader");
      JsonNode publication = sourceInventoryWire.path("publication");
      try {
        AnalysisStepPublicationReference reference =
            new AnalysisStepPublicationReference(
                new AnalysisStepPublicationAddress(
                    AnalysisRunId.parse(
                        publication.path("address").path("runId").path("value").asText()),
                    AnalysisStepKey.valueOf(
                        publication.path("address").path("analysisStepKey").asText())),
                AnalysisStepArtifactRoot.parse(
                    publication.path("analysisStepArtifactRoot").path("value").asText()),
                AnalysisStepReceiptId.parse(
                    publication.path("analysisStepReceiptId").path("value").asText()),
                Sha256Digest.parse(
                    publication.path("analysisStepReceiptSha256").path("value").asText()));
        VerifiedSourceTextSet reopened =
            reader.reopen(new VerifiedSourceInventoryReference(reference));
        if (!sourceSnapshotId.equals(reopened.snapshotId())) {
          throw new IllegalArgumentException("ONTOLOGY_SOURCE_VERSION_MISMATCH");
        }
        preparedSource = reopened;
      } catch (RuntimeException invalid) {
        if ("ONTOLOGY_SOURCE_VERSION_MISMATCH".equals(invalid.getMessage())) {
          throw invalid;
        }
        throw new IllegalArgumentException("ONTOLOGY_SOURCE_REOPEN_INVALID", invalid);
      }
    }
    return preparedSource;
  }

  static ImmutableBytes extractLines(byte[] source, int startLine, int endLine, int maxUtf8Bytes) {
    if (source == null || startLine < 1 || endLine < startLine || maxUtf8Bytes < 1) {
      throw new IllegalArgumentException("ONTOLOGY_SOURCE_RANGE_INVALID");
    }
    int line = 1;
    int from = startLine == 1 ? 0 : -1;
    int to = -1;
    for (int index = 0; index < source.length; index++) {
      if (source[index] == '\n') {
        if (line == endLine) {
          to = index + 1;
          break;
        }
        line++;
        if (line == startLine) {
          from = index + 1;
        }
      }
    }
    if (to < 0 && line == endLine) {
      to = source.length;
    }
    if (from < 0 || to < from) {
      throw new IllegalArgumentException("ONTOLOGY_SOURCE_RANGE_INVALID");
    }
    if (to - from > maxUtf8Bytes) {
      throw new IllegalArgumentException("ONTOLOGY_UNIT_TOO_LARGE");
    }
    return ImmutableBytes.copyOf(Arrays.copyOfRange(source, from, to));
  }

  public EvidenceUnit read(String entryId, UnitKind kind, String originalId) {
    EntryEvidenceReader.EntryDocument document = documents.get(entryId);
    if (document == null || kind == null || originalId == null || originalId.isBlank()) {
      throw new IllegalArgumentException("ONTOLOGY_UNIT_NOT_FOUND");
    }
    EvidenceUnit direct = evidenceByUse.get(new UnitHandle(entryId, kind, originalId));
    if (direct != null) {
      return direct;
    }
    if (kind == UnitKind.SCHEMA_SOURCE) {
      throw new IllegalArgumentException("ONTOLOGY_UNIT_NOT_FOUND");
    }
    JsonNode entry = json.parseCanonical(document.canonicalJson());
    List<JsonNode> matches = new ArrayList<>();
    for (JsonNode item : array(entry, kind)) {
      if (originalId.equals(unitId(item, kind))
          || originalId.equals(originalId(item, kind))
          || (kind == UnitKind.XML_STATEMENT
              && originalId.equals(item.path("statementRef").asText()))) {
        matches.add(item);
      }
    }
    if (matches.size() > 1
        || (kind == UnitKind.SQL_ANALYSIS
            && !originalId.startsWith("sql:")
            && hasAmbiguousStatementVariant(entry, originalId))) {
      throw new IllegalArgumentException("ONTOLOGY_UNIT_AMBIGUOUS");
    }
    if (matches.size() == 1) {
      JsonNode item = matches.get(0);
      Map<String, Integer> limitations = new java.util.TreeMap<>();
      for (JsonNode limitation : entry.path("limitations")) {
        String code = limitation.path("code").asText();
        if (!code.isBlank()) {
          limitations.merge(code, 1, Integer::sum);
        }
      }
      return new EvidenceUnit(
          entryId,
          kind,
          kind == UnitKind.SQL_ANALYSIS && !originalId.startsWith("sql:")
              ? originalId(item, kind)
              : unitId(item, kind),
          json.encodeCanonical(item),
          limitations,
          new EntryDescriptor(
              entry.path("entry").path("method").asText(),
              entry.path("entry").path("route").asText(),
              entry.path("entry").path("handlerFqn").asText()));
    }
    throw new IllegalArgumentException("ONTOLOGY_UNIT_NOT_FOUND");
  }

  /**
   * Resolves the complete, entry-local bodies required by one saved page context. The saved context
   * is only metadata; every body must be an exact physical R4 frontend unit in its own entry rather
   * than a coverage row or another entry's matching file.
   */
  List<FrontendContextSource> frontendContextSources(UnitHandle contextHandle) {
    Objects.requireNonNull(contextHandle, "frontend context handle");
    if (contextHandle.kind() != UnitKind.FRONTEND_PAGE_CONTEXT) {
      throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_INVALID");
    }
    EntryEvidenceReader.EntryDocument document = documents.get(contextHandle.entryId());
    if (document == null) {
      throw new IllegalArgumentException("ONTOLOGY_UNIT_NOT_FOUND");
    }
    JsonNode context =
        read(contextHandle.entryId(), contextHandle.kind(), contextHandle.originalId()).content();
    List<FrontendContextSource> result = new ArrayList<>();
    for (JsonNode contextUnit : context.path("sourceUnits")) {
      String unitRef = contextUnit.path("unitRef").asText();
      if (unitRef.isBlank() || !validFrontendContextUnitIdentity(contextUnit)) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_MISSING");
      }
      List<JsonNode> matches = new ArrayList<>();
      for (UnitHandle available : unitsByEntry.getOrDefault(contextHandle.entryId(), List.of())) {
        if (available.kind() != UnitKind.FRONTEND_UNIT) {
          continue;
        }
        JsonNode entryUnit = evidenceByUse.get(available).content();
        if (sameFrontendPhysicalUnit(contextUnit, entryUnit)) {
          matches.add(entryUnit);
        }
      }
      if (matches.isEmpty()) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_MISSING");
      }
      if (matches.size() != 1) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_CONFLICT");
      }
      JsonNode entryUnit = matches.get(0);
      String sourceUnitId = entryUnit.path("sourceUnitId").asText();
      if (sourceUnitId.isBlank() || !hasCompleteFrontendText(entryUnit)) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_SOURCE_UNIT_MISSING");
      }
      result.add(
          new FrontendContextSource(
              unitRef,
              new UnitHandle(contextHandle.entryId(), UnitKind.FRONTEND_UNIT, sourceUnitId)));
    }
    return List.copyOf(result);
  }

  /**
   * Returns the factual request association for every request named by a selected context without
   * reading an unrelated request body into the packet.
   */
  List<ContextRequestAssociation> contextRequestAssociations(UnitHandle contextHandle) {
    Objects.requireNonNull(contextHandle, "frontend context handle");
    if (contextHandle.kind() != UnitKind.FRONTEND_PAGE_CONTEXT) {
      throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_INVALID");
    }
    EntryEvidenceReader.EntryDocument document = documents.get(contextHandle.entryId());
    if (document == null) {
      throw new IllegalArgumentException("ONTOLOGY_UNIT_NOT_FOUND");
    }
    JsonNode context =
        read(contextHandle.entryId(), contextHandle.kind(), contextHandle.originalId()).content();
    JsonNode entry = json.parseCanonical(document.canonicalJson());
    Map<String, ContextRequestAssociation> entryAssociations = new LinkedHashMap<>();
    collectContextRequestAssociations(
        entry.path("frontend").path("requestUses"),
        UnitKind.FRONTEND_REQUEST_USE,
        contextHandle.originalId(),
        entryAssociations);
    collectContextRequestAssociations(
        entry.path("frontend").path("candidateRequestUses"),
        UnitKind.FRONTEND_CANDIDATE_REQUEST_USE,
        contextHandle.originalId(),
        entryAssociations);

    List<ContextRequestAssociation> result = new ArrayList<>();
    for (JsonNode requestIdNode : context.path("requestIds")) {
      String requestId = requestIdNode.asText();
      if (requestId.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_MISSING");
      }
      ContextRequestAssociation association = entryAssociations.get(requestId);
      if (association == null) {
        FrontendCoverageRequest coverage = frontendCoverageByRequest.get(requestId);
        if (coverage == null || !coverage.contextIds().contains(contextHandle.originalId())) {
          throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_MISSING");
        }
        association = coverage.asContextAssociation();
      }
      result.add(association);
    }
    return List.copyOf(result);
  }

  private void collectContextRequestAssociations(
      JsonNode requestUses,
      UnitKind kind,
      String contextId,
      Map<String, ContextRequestAssociation> associations) {
    for (JsonNode requestUse : requestUses) {
      if (!containsText(requestUse.path("pageContexts"), contextId)) {
        continue;
      }
      JsonNode request = requestUse.path("request");
      String requestId = request.path("requestId").asText();
      if (requestId.isBlank() || !request.isObject()) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_MISSING");
      }
      String status = kind == UnitKind.FRONTEND_REQUEST_USE ? "MATCHED" : "CANDIDATE";
      ContextRequestAssociation association =
          new ContextRequestAssociation(
              requestId,
              status,
              requestUse.path("resolution").asText(),
              requestUse.path("reason").isNull() ? null : requestUse.path("reason").asText(),
              requestUse.path("candidateEntryIds").size(),
              json.encodeCanonical(request));
      if (associations.putIfAbsent(requestId, association) != null) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_CONFLICT");
      }
    }
  }

  private FrontendCoverageIndex frontendCoverageByRequest(ImmutableBytes coverageJsonl) {
    Map<String, FrontendCoverageRequest> result = new LinkedHashMap<>();
    Set<String> requestIds = new LinkedHashSet<>();
    String jsonl = new String(coverageJsonl.copyToByteArray(), StandardCharsets.UTF_8);
    for (String line : jsonl.split("\\n")) {
      if (line.isBlank()) {
        continue;
      }
      JsonNode record =
          json.parseCanonical(ImmutableBytes.copyOf(line.getBytes(StandardCharsets.UTF_8)));
      if (!"REQUEST_COVERAGE".equals(record.path("recordType").asText())) {
        continue;
      }
      JsonNode payload = record.path("payload");
      JsonNode request = payload.path("request");
      String requestId = payload.path("requestId").asText();
      if (requestId.isBlank() || !requestIds.add(requestId)) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_CONFLICT");
      }
      // R4 v1 has request coverage but no page contexts; MATCHED_UNIQUE also omits the
      // nested request. Count these admitted rows without inventing a context association.
      if (!request.isObject()
          || !requestId.equals(request.path("requestId").asText())
          || !payload.path("pageContexts").isArray()) {
        continue;
      }
      Set<String> contextIds = new LinkedHashSet<>();
      for (JsonNode contextId : payload.path("pageContexts")) {
        if (!contextId.isTextual()
            || contextId.asText().isBlank()
            || !contextIds.add(contextId.asText())) {
          throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_CONFLICT");
        }
      }
      FrontendCoverageRequest coverage =
          new FrontendCoverageRequest(
              requestId,
              payload.path("resolution").asText(),
              payload.path("reason").isNull() ? null : payload.path("reason").asText(),
              payload.path("entryIds").size(),
              Set.copyOf(contextIds),
              json.encodeCanonical(request));
      if (result.putIfAbsent(requestId, coverage) != null) {
        throw new IllegalArgumentException("ONTOLOGY_PAGE_CONTEXT_REQUEST_ASSOCIATION_CONFLICT");
      }
    }
    return new FrontendCoverageIndex(Map.copyOf(result), requestIds.size());
  }

  private static boolean validFrontendContextUnitIdentity(JsonNode unit) {
    return unit.path("sourcePath").isTextual()
        && !unit.path("sourcePath").asText().isBlank()
        && unit.path("sourceSha256").asText().matches("[0-9a-f]{64}")
        && unit.path("sourceUnitRange").isObject()
        && unit.path("sourceUnitKind").isTextual()
        && !unit.path("sourceUnitKind").asText().isBlank();
  }

  private static boolean sameFrontendPhysicalUnit(JsonNode contextUnit, JsonNode entryUnit) {
    return contextUnit.path("sourcePath").asText().equals(entryUnit.path("path").asText())
        && contextUnit.path("sourceSha256").asText().equals(entryUnit.path("sourceSha256").asText())
        && contextUnit.path("sourceUnitRange").equals(entryUnit.path("sourceUnitRange"))
        && contextUnit
            .path("sourceUnitKind")
            .asText()
            .equals(entryUnit.path("sourceUnitKind").asText());
  }

  private static boolean hasCompleteFrontendText(JsonNode entryUnit) {
    JsonNode range = entryUnit.path("sourceUnitRange");
    JsonNode text = entryUnit.path("text");
    return range.path("lengthUtf16").isIntegralNumber()
        && range.path("lengthUtf16").canConvertToInt()
        && range.path("lengthUtf16").intValue() >= 0
        && text.isTextual()
        && text.asText().length() == range.path("lengthUtf16").intValue();
  }

  private static boolean containsText(JsonNode values, String expected) {
    for (JsonNode value : values) {
      if (expected.equals(value.asText())) {
        return true;
      }
    }
    return false;
  }

  private static boolean hasAmbiguousStatementVariant(JsonNode entry, String statementRef) {
    Set<String> variants = new LinkedHashSet<>();
    for (JsonNode statement : entry.path("persistence").path("statements")) {
      if (statementRef.equals(statement.path("statementRef").asText())) {
        variants.add(statementVariantId(statement));
      }
    }
    return variants.size() > 1;
  }

  /**
   * Literal-only search over stored units; pages retain source order and never repeat the first
   * page.
   */
  public SearchResult searchLiteral(String query, int offset, int limit) {
    if (query == null || query.isBlank() || offset < 0 || limit < 1) {
      throw new IllegalArgumentException("ONTOLOGY_SEARCH_INVALID");
    }
    int total = 0;
    List<SearchMatch> shown = new ArrayList<>();
    for (EntrySummary summary : navigation) {
      JsonNode entry = json.parseCanonical(documents.get(summary.entryId()).canonicalJson());
      for (UnitKind kind : UnitKind.values()) {
        for (JsonNode item : array(entry, kind)) {
          String text = searchableText(kind, item);
          int foundAt = text.indexOf(query);
          if (foundAt >= 0) {
            total++;
            if (total > offset && shown.size() < limit) {
              int start = Math.max(0, foundAt - 80);
              int end = Math.min(text.length(), foundAt + query.length() + 80);
              shown.add(
                  new SearchMatch(
                      summary.entryId(), kind, unitId(item, kind), text.substring(start, end)));
            }
          }
        }
      }
    }
    if (offset > total) {
      throw new IllegalArgumentException("ONTOLOGY_SEARCH_INVALID");
    }
    return new SearchResult(offset, limit, total, shown);
  }

  /** The existing first-page convenience remains an explicit offset-zero request. */
  public SearchResult searchLiteral(String query, int limit) {
    return searchLiteral(query, 0, limit);
  }

  private static JsonNode array(JsonNode entry, UnitKind kind) {
    return switch (kind) {
      case JAVA_METHOD -> entry.path("java").path("methods");
      case JAVA_CALL -> entry.path("java").path("calls");
      case FRONTEND_UNIT -> entry.path("frontend").path("units");
      case FRONTEND_PAGE_CONTEXT -> entry.path("frontend").path("pageContexts");
      case FRONTEND_REQUEST_USE -> entry.path("frontend").path("requestUses");
      case FRONTEND_CANDIDATE_REQUEST_USE -> entry.path("frontend").path("candidateRequestUses");
      case PERSISTENCE_BINDING -> entry.path("persistence").path("bindings");
      case XML_STATEMENT -> entry.path("persistence").path("statements");
      case XML_RESOURCE -> entry.path("persistence").path("resources");
      case SQL_ANALYSIS -> entry.path("persistence").path("sqlAnalyses");
      case SOURCE_REFERENCE -> entry.path("sourceRefs");
      case SCHEMA_SOURCE -> JsonNodeFactory.instance.arrayNode();
    };
  }

  private static String originalId(JsonNode item, UnitKind kind) {
    return switch (kind) {
      case JAVA_METHOD, PERSISTENCE_BINDING -> item.path("methodKey").asText();
      case JAVA_CALL -> item.path("callKey").asText();
      case FRONTEND_UNIT -> item.path("sourceUnitId").asText();
      case FRONTEND_PAGE_CONTEXT -> item.path("contextId").asText();
      case FRONTEND_REQUEST_USE, FRONTEND_CANDIDATE_REQUEST_USE ->
          item.path("request").path("requestId").asText();
      case XML_STATEMENT -> statementVariantId(item);
      case SQL_ANALYSIS -> item.path("statementRef").asText();
      case XML_RESOURCE -> item.path("resourcePath").asText();
      case SOURCE_REFERENCE -> item.path("reference").asText();
      case SCHEMA_SOURCE -> item.path("fileId").asText();
    };
  }

  private static String unitId(JsonNode item, UnitKind kind) {
    if (kind != UnitKind.SQL_ANALYSIS) {
      return originalId(item, kind);
    }
    String statementRef = item.path("statementRef").asText();
    if (statementRef.isBlank()) {
      return "";
    }
    ImmutableBytes canonical = new CanonicalJsonCodec().encodeCanonical(item);
    return "sql:" + OntologyReadingPacket.sha256(canonical.copyToByteArray());
  }

  /**
   * A statement variant remains addressable only by the two persisted identity fields. The opaque
   * value is reversible through the saved navigation mapping; it never picks an arbitrary variant.
   */
  private static String statementVariantId(JsonNode value) {
    String statementRef = value.path("statementRef").asText();
    if (statementRef.isBlank()) {
      return "";
    }
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("statementRef", statementRef);
    if (value.path("databaseId").isNull() || value.path("databaseId").isMissingNode()) {
      identity.putNull("databaseId");
    } else {
      identity.put("databaseId", value.path("databaseId").asText());
    }
    ImmutableBytes canonical = new CanonicalJsonCodec().encodeCanonical(identity);
    return "variant:" + OntologyReadingPacket.sha256(canonical.copyToByteArray());
  }

  private static String searchableText(UnitKind kind, JsonNode value) {
    return switch (kind) {
      case JAVA_METHOD -> value.path("source").path("text").asText();
      case JAVA_CALL -> value.path("expression").asText();
      case FRONTEND_UNIT -> value.path("text").asText();
      case FRONTEND_PAGE_CONTEXT -> value.path("pagePath").asText();
      case FRONTEND_REQUEST_USE, FRONTEND_CANDIDATE_REQUEST_USE, PERSISTENCE_BINDING ->
          value.toString();
      case XML_STATEMENT -> value.path("xmlSubtree").toString();
      case XML_RESOURCE -> value.path("rawSource").asText();
      case SQL_ANALYSIS -> value.path("analysisCopy").asText();
      case SOURCE_REFERENCE -> value.path("path").asText();
      case SCHEMA_SOURCE -> value.path("sourceText").asText();
    };
  }

  private List<UnitHandle> clueUses(ClueKind kind, String lookupKey) {
    List<UnitHandle> uses;
    switch (kind) {
      case METHOD -> uses = methodsByIdentity.getOrDefault(lookupKey, List.of());
      case TABLE -> uses = statementsByTable.getOrDefault(lookupKey, List.of());
      case COLUMN -> uses = statementsByColumn.getOrDefault(lookupKey, List.of());
      case CONTROL_REFERENCE -> uses = controlsByExpression.getOrDefault(lookupKey, List.of());
      case STATEMENT -> {
        uses = new ArrayList<>();
        for (List<UnitHandle> entryUnits : unitsByEntry.values()) {
          for (UnitHandle handle : entryUnits) {
            if (handle.kind() == UnitKind.XML_STATEMENT && lookupKey.equals(handle.originalId())) {
              uses.add(handle);
            }
          }
        }
      }
      default -> throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
    }
    List<UnitHandle> ordered = new ArrayList<>(uses);
    ordered.sort(AliasCatalog.unitHandleOrder());
    return List.copyOf(ordered);
  }

  /**
   * Formal, corpus-scoped aliases. Their canonical mapping is private but reversible; model packets
   * may expose only the corresponding E/U/K or packet-local S names.
   */
  public static final class AliasCatalog {
    private final Map<String, String> entryRefs;
    private final Map<String, EntrySummary> entries;
    private final Map<UnitHandle, String> unitRefs;
    private final Map<String, UnitHandle> units;
    private final Map<String, List<UnitHandle>> unitUses;
    private final Map<UnitHandle, EvidenceUnit> evidenceByUse;
    private final Map<ClueIdentity, String> clueRefs;
    private final Map<String, NavigationClue> clues;
    private final Map<String, List<UnitHandle>> clueUses;
    private final ImmutableBytes canonicalMapping;

    private AliasCatalog(
        Map<String, String> entryRefs,
        Map<String, EntrySummary> entries,
        Map<UnitHandle, String> unitRefs,
        Map<String, UnitHandle> units,
        Map<String, List<UnitHandle>> unitUses,
        Map<UnitHandle, EvidenceUnit> evidenceByUse,
        Map<ClueIdentity, String> clueRefs,
        Map<String, NavigationClue> clues,
        Map<String, List<UnitHandle>> clueUses,
        ImmutableBytes canonicalMapping) {
      this.entryRefs = Map.copyOf(entryRefs);
      this.entries = Map.copyOf(entries);
      this.unitRefs = Map.copyOf(unitRefs);
      this.units = Map.copyOf(units);
      this.unitUses = Map.copyOf(unitUses);
      this.evidenceByUse = Map.copyOf(evidenceByUse);
      this.clueRefs = Map.copyOf(clueRefs);
      this.clues = Map.copyOf(clues);
      this.clueUses = Map.copyOf(clueUses);
      this.canonicalMapping = canonicalMapping;
    }

    private static AliasCatalog create(OntologyEvidenceCorpus corpus) {
      List<EntrySummary> orderedEntries =
          corpus.navigation.stream().sorted(Comparator.comparing(EntrySummary::entryId)).toList();
      Map<String, String> entryRefs = new LinkedHashMap<>();
      Map<String, EntrySummary> entries = new LinkedHashMap<>();
      for (EntrySummary entry : orderedEntries) {
        String ref = "E" + (entryRefs.size() + 1);
        entryRefs.put(entry.entryId(), ref);
        entries.put(ref, entry);
      }

      Map<UnitHandle, EvidenceUnit> evidenceByUse = new LinkedHashMap<>();
      Map<UnitIdentity, List<UnitHandle>> usesByIdentity = new TreeMap<>();
      for (EntrySummary entry : orderedEntries) {
        for (UnitHandle handle : corpus.unitsByEntry.getOrDefault(entry.entryId(), List.of())) {
          EvidenceUnit evidence = corpus.evidenceByUse.get(handle);
          if (evidence == null) {
            throw new IllegalArgumentException("ONTOLOGY_UNIT_NOT_FOUND");
          }
          evidenceByUse.put(handle, evidence);
          usesByIdentity
              .computeIfAbsent(
                  new UnitIdentity(
                      handle.kind(),
                      handle.originalId(),
                      OntologyReadingPacket.sha256(evidence.canonicalJson().copyToByteArray())),
                  ignored -> new ArrayList<>())
              .add(handle);
        }
      }

      Map<UnitHandle, String> unitRefs = new LinkedHashMap<>();
      Map<String, UnitHandle> units = new LinkedHashMap<>();
      Map<String, List<UnitHandle>> unitUses = new LinkedHashMap<>();
      for (Map.Entry<UnitIdentity, List<UnitHandle>> entry : usesByIdentity.entrySet()) {
        List<UnitHandle> uses = new ArrayList<>(entry.getValue());
        uses.sort(unitHandleOrder());
        String ref = "U" + (units.size() + 1);
        units.put(ref, uses.get(0));
        unitUses.put(ref, List.copyOf(uses));
        uses.forEach(use -> unitRefs.put(use, ref));
      }

      Map<ClueIdentity, NavigationClue> cluesByIdentity = new TreeMap<>();
      for (EntrySummary entry : orderedEntries) {
        for (NavigationClue clue : corpus.entryClues(entry.entryId(), Integer.MAX_VALUE).clues()) {
          ClueIdentity identity = new ClueIdentity(clue.kind(), clue.lookupKey());
          cluesByIdentity.putIfAbsent(identity, clue);
        }
      }
      Map<ClueIdentity, String> clueRefs = new LinkedHashMap<>();
      Map<String, NavigationClue> clues = new LinkedHashMap<>();
      Map<String, List<UnitHandle>> clueUses = new LinkedHashMap<>();
      for (Map.Entry<ClueIdentity, NavigationClue> entry : cluesByIdentity.entrySet()) {
        String ref = "K" + (clueRefs.size() + 1);
        clueRefs.put(entry.getKey(), ref);
        clues.put(ref, entry.getValue());
        clueUses.put(ref, corpus.clueUses(entry.getKey().kind(), entry.getKey().lookupKey()));
      }

      return new AliasCatalog(
          entryRefs,
          entries,
          unitRefs,
          units,
          unitUses,
          evidenceByUse,
          clueRefs,
          clues,
          clueUses,
          mapping(entryRefs, units, unitUses, evidenceByUse, clueRefs, clueUses, unitRefs));
    }

    /** The private deterministic mapping used by packet identity and later saved observations. */
    public ImmutableBytes canonicalMapping() {
      return canonicalMapping;
    }

    public String entryRef(String entryId) {
      String ref = entryRefs.get(entryId);
      if (ref == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return ref;
    }

    public EntrySummary entry(String entryRef) {
      EntrySummary entry = entries.get(entryRef);
      if (entry == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return entry;
    }

    public String unitRef(UnitHandle handle) {
      String ref = unitRefs.get(handle);
      if (ref == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return ref;
    }

    public UnitHandle unit(String unitRef) {
      UnitHandle unit = units.get(unitRef);
      if (unit == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return unit;
    }

    public List<UnitHandle> unitUses(String unitRef) {
      List<UnitHandle> uses = unitUses.get(unitRef);
      if (uses == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return uses;
    }

    /** Reads the exact U body for one of its permitted E uses; never selects a sibling variant. */
    public EvidenceUnit read(String unitRef, String entryRef) {
      EntrySummary entry = entry(entryRef);
      UnitHandle canonical = unit(unitRef);
      UnitHandle use = new UnitHandle(entry.entryId(), canonical.kind(), canonical.originalId());
      if (!unitUses(unitRef).contains(use)) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      EvidenceUnit evidence = evidenceByUse.get(use);
      if (evidence == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return evidence;
    }

    public String clueRef(ClueKind kind, String lookupKey) {
      String ref = clueRefs.get(new ClueIdentity(kind, lookupKey));
      if (ref == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return ref;
    }

    public NavigationClue clue(String clueRef) {
      NavigationClue clue = clues.get(clueRef);
      if (clue == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return clue;
    }

    /** Every exact U/E use reachable from one K; callers must not treat clue() as a READ target. */
    public List<UnitHandle> clueUses(String clueRef) {
      List<UnitHandle> uses = clueUses.get(clueRef);
      if (uses == null) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return uses;
    }

    /** Existing K references whose exact U/E use was returned by a bounded formal query. */
    List<String> clueRefsFor(UnitHandle use) {
      if (!evidenceByUse.containsKey(use)) {
        throw new IllegalArgumentException("ONTOLOGY_ALIAS_REFERENCE_INVALID");
      }
      return clueUses.entrySet().stream()
          .filter(entry -> entry.getValue().contains(use))
          .map(java.util.Map.Entry::getKey)
          .sorted()
          .toList();
    }

    private static ImmutableBytes mapping(
        Map<String, String> entryRefs,
        Map<String, UnitHandle> units,
        Map<String, List<UnitHandle>> unitUses,
        Map<UnitHandle, EvidenceUnit> evidenceByUse,
        Map<ClueIdentity, String> clueRefs,
        Map<String, List<UnitHandle>> clueUses,
        Map<UnitHandle, String> unitRefs) {
      ObjectNode root = JsonNodeFactory.instance.objectNode();
      root.put("schemaVersion", "ontology-evidence-aliases-v1");
      ArrayNode entries = root.putArray("entries");
      entryRefs.forEach(
          (entryId, ref) -> {
            ObjectNode item = entries.addObject();
            item.put("ref", ref);
            item.put("entryId", entryId);
          });
      ArrayNode unitsNode = root.putArray("units");
      units.forEach(
          (ref, canonical) -> {
            ObjectNode item = unitsNode.addObject();
            item.put("ref", ref);
            item.put("kind", canonical.kind().name());
            item.put("originalId", canonical.originalId());
            EvidenceUnit evidence = evidenceByUse.get(canonical);
            item.put(
                "contentSha256",
                OntologyReadingPacket.sha256(evidence.canonicalJson().copyToByteArray()));
            ArrayNode uses = item.putArray("uses");
            unitUses
                .get(ref)
                .forEach(
                    use -> {
                      ObjectNode useNode = uses.addObject();
                      useNode.put("entryId", use.entryId());
                      useNode.put("kind", use.kind().name());
                      useNode.put("originalId", use.originalId());
                    });
          });
      ArrayNode clues = root.putArray("clues");
      clueRefs.forEach(
          (identity, ref) -> {
            ObjectNode item = clues.addObject();
            item.put("ref", ref);
            item.put("kind", identity.kind().name());
            item.put("lookupKey", identity.lookupKey());
            ArrayNode readableUnitRefs = item.putArray("readableUnitRefs");
            LinkedHashSet<String> refs = new LinkedHashSet<>();
            clueUses.get(ref).forEach(use -> refs.add(unitRefs.get(use)));
            refs.forEach(readableUnitRefs::add);
            ArrayNode readableUses = item.putArray("readableUses");
            clueUses
                .get(ref)
                .forEach(
                    use -> {
                      ObjectNode useNode = readableUses.addObject();
                      useNode.put("unitRef", unitRefs.get(use));
                      useNode.put("entryId", use.entryId());
                      useNode.put("kind", use.kind().name());
                      useNode.put("originalId", use.originalId());
                    });
          });
      return new CanonicalJsonCodec().encodeCanonical(root);
    }

    private static Comparator<UnitHandle> unitHandleOrder() {
      return Comparator.comparing(UnitHandle::entryId)
          .thenComparing(handle -> handle.kind().name())
          .thenComparing(UnitHandle::originalId);
    }

    private record UnitIdentity(UnitKind kind, String originalId, String contentSha256)
        implements Comparable<UnitIdentity> {
      @Override
      public int compareTo(UnitIdentity other) {
        int kindOrder = kind.name().compareTo(other.kind.name());
        if (kindOrder != 0) {
          return kindOrder;
        }
        int originalOrder = originalId.compareTo(other.originalId);
        return originalOrder != 0 ? originalOrder : contentSha256.compareTo(other.contentSha256);
      }
    }

    private record ClueIdentity(ClueKind kind, String lookupKey)
        implements Comparable<ClueIdentity> {
      @Override
      public int compareTo(ClueIdentity other) {
        int kindOrder = kind.name().compareTo(other.kind.name());
        return kindOrder != 0 ? kindOrder : lookupKey.compareTo(other.lookupKey);
      }
    }
  }

  /** Compact call information derived from a selected method without exposing a call as a link. */
  List<FormalCallSite> formalCallSites(Set<UnitHandle> selected) {
    List<FormalCallSite> result = new ArrayList<>();
    for (UnitHandle method :
        selected.stream().filter(handle -> handle.kind() == UnitKind.JAVA_METHOD).toList()) {
      JsonNode entry = json.parseCanonical(documents.get(method.entryId()).canonicalJson());
      for (JsonNode call : entry.path("java").path("calls")) {
        if (!method.originalId().equals(call.path("callerMethodKey").asText())) {
          continue;
        }
        String status = call.path("resolution").asText();
        if (!CallSiteStatus.isLegal(status)) {
          throw new IllegalArgumentException("ONTOLOGY_CALL_SITE_STATUS_INVALID");
        }
        UnitHandle callHandle =
            new UnitHandle(method.entryId(), UnitKind.JAVA_CALL, unitId(call, UnitKind.JAVA_CALL));
        List<UnitHandle> selectedTargets = selectedLocatedTargets(call, method.entryId(), selected);
        boolean selectedCall = selected.contains(callHandle);
        if (!selectedCall
            && selectedTargets.isEmpty()
            && !CallSiteStatus.requiresLocationDetail(status)) {
          continue;
        }
        result.add(
            new FormalCallSite(
                method, callHandle, status, selectedTargets, json.encodeCanonical(call)));
      }
    }
    result.sort(
        Comparator.comparing((FormalCallSite site) -> site.caller().entryId())
            .thenComparing(site -> site.caller().originalId())
            .thenComparing(site -> site.call().originalId()));
    return List.copyOf(result);
  }

  private static List<UnitHandle> selectedLocatedTargets(
      JsonNode call, String callerEntryId, Set<UnitHandle> selected) {
    if (!"LOCATED".equals(call.path("resolution").asText())) {
      return List.of();
    }
    List<UnitHandle> result = new ArrayList<>();
    for (JsonNode target : call.path("targets")) {
      String methodKey = target.path("methodKey").asText();
      UnitHandle candidate = new UnitHandle(callerEntryId, UnitKind.JAVA_METHOD, methodKey);
      if (!methodKey.isBlank() && selected.contains(candidate)) {
        result.add(candidate);
      }
    }
    return List.copyOf(result);
  }

  record FormalCallSite(
      UnitHandle caller,
      UnitHandle call,
      String status,
      List<UnitHandle> selectedTargets,
      ImmutableBytes canonicalCall) {
    FormalCallSite {
      selectedTargets = List.copyOf(selectedTargets);
    }
  }

  private enum CallSiteStatus {
    LOCATED,
    CANDIDATES,
    UNRESOLVED,
    EXTERNAL,
    QUERY_FAILED,
    NAVIGATION_CONFLICT;

    private static boolean isLegal(String value) {
      try {
        valueOf(value);
        return true;
      } catch (IllegalArgumentException invalid) {
        return false;
      }
    }

    private static boolean requiresLocationDetail(String value) {
      return CANDIDATES.name().equals(value)
          || UNRESOLVED.name().equals(value)
          || QUERY_FAILED.name().equals(value)
          || NAVIGATION_CONFLICT.name().equals(value);
    }
  }

  public enum UnitKind {
    JAVA_METHOD,
    JAVA_CALL,
    FRONTEND_UNIT,
    FRONTEND_PAGE_CONTEXT,
    FRONTEND_REQUEST_USE,
    FRONTEND_CANDIDATE_REQUEST_USE,
    PERSISTENCE_BINDING,
    XML_STATEMENT,
    XML_RESOURCE,
    SQL_ANALYSIS,
    SOURCE_REFERENCE,
    SCHEMA_SOURCE
  }

  public record EntrySummary(
      String entryId,
      String method,
      String route,
      String handlerFqn,
      String methodKey,
      String assemblyStatus,
      int limitationCount) {}

  public record NavigationPage(
      int offset, int limit, int totalEntries, List<EntrySummary> entries) {
    public NavigationPage {
      entries = List.copyOf(entries);
    }

    public int unreadEntries() {
      return totalEntries - offset - entries.size();
    }
  }

  public record EvidenceUnit(
      String entryId,
      UnitKind kind,
      String originalId,
      ImmutableBytes canonicalJson,
      Map<String, Integer> limitationCounts,
      EntryDescriptor entryDescriptor) {
    public EvidenceUnit(
        String entryId, UnitKind kind, String originalId, ImmutableBytes canonicalJson) {
      this(entryId, kind, originalId, canonicalJson, Map.of(), new EntryDescriptor("", "", ""));
    }

    public EvidenceUnit(
        String entryId,
        UnitKind kind,
        String originalId,
        ImmutableBytes canonicalJson,
        Map<String, Integer> limitationCounts) {
      this(
          entryId,
          kind,
          originalId,
          canonicalJson,
          limitationCounts,
          new EntryDescriptor("", "", ""));
    }

    public EvidenceUnit {
      limitationCounts = Map.copyOf(limitationCounts);
      Objects.requireNonNull(entryDescriptor, "entry descriptor");
    }

    public JsonNode content() {
      return new CanonicalJsonCodec().parseCanonical(canonicalJson);
    }
  }

  public record EntryDescriptor(String method, String route, String handlerFqn) {
    public EntryDescriptor {
      Objects.requireNonNull(method, "entry method");
      Objects.requireNonNull(route, "entry route");
      Objects.requireNonNull(handlerFqn, "entry handler");
    }
  }

  public record SearchMatch(String entryId, UnitKind kind, String originalId, String excerpt) {}

  public record UnitHandle(String entryId, UnitKind kind, String originalId) {}

  /** A literal saved SQL TABLE observation that may use one schema file as candidate evidence. */
  public record SchemaEntryUse(
      String entryId, String matchedTableValue, String sqlUnitId, String sqlStatus) {}

  /** One context-local source reference to the exact entry body that satisfies it. */
  record FrontendContextSource(String unitRef, UnitHandle unit) {}

  /**
   * A compact factual request association retained for a page context without adding that request
   * as a packet body. The actual request ID and envelope remain private packet mapping data.
   */
  record ContextRequestAssociation(
      String requestId,
      String associationStatus,
      String resolution,
      String reason,
      int candidateEntryCount,
      ImmutableBytes requestCanonicalJson) {}

  private record FrontendCoverageRequest(
      String requestId,
      String resolution,
      String reason,
      int candidateEntryCount,
      Set<String> contextIds,
      ImmutableBytes requestCanonicalJson) {
    private ContextRequestAssociation asContextAssociation() {
      return new ContextRequestAssociation(
          requestId,
          "COVERAGE_ONLY",
          resolution,
          reason,
          candidateEntryCount,
          requestCanonicalJson);
    }
  }

  private record FrontendCoverageIndex(
      Map<String, FrontendCoverageRequest> requestsWithPageContext, int requestCount) {}

  public record UnitNavigationMetadata(String keyDisplay, Integer unitBytes) {}

  public enum ClueKind {
    METHOD,
    STATEMENT,
    TABLE,
    COLUMN,
    CONTROL_REFERENCE
  }

  public record NavigationClue(
      ClueKind kind,
      String keyDisplay,
      String lookupKey,
      UnitHandle readableUnit,
      int totalUses,
      int unitBytes) {}

  public record ClueDisclosure(int total, int shown) {
    public int unread() {
      return total - shown;
    }
  }

  public record EntryCluePage(
      List<NavigationClue> clues,
      Map<ClueKind, ClueDisclosure> disclosure,
      int sqlAnalysesWithoutAst) {
    public EntryCluePage {
      clues = List.copyOf(clues);
      disclosure = Map.copyOf(disclosure);
      if (sqlAnalysesWithoutAst < 0) {
        throw new IllegalArgumentException("ONTOLOGY_ENTRY_CLUE_COUNT_INVALID");
      }
    }
  }

  public record UnitPage(int offset, int limit, int total, List<UnitHandle> items) {
    public UnitPage {
      items = List.copyOf(items);
    }

    public int unread() {
      return total - offset - items.size();
    }
  }

  public record SearchResult(int offset, int limit, int totalMatches, List<SearchMatch> matches) {
    public SearchResult {
      matches = List.copyOf(matches);
    }
  }

  public record LiteralSearchObservation(String query, SearchResult result) {
    public LiteralSearchObservation {
      if (query == null || query.isBlank()) {
        throw new IllegalArgumentException("ONTOLOGY_SEARCH_INVALID");
      }
      Objects.requireNonNull(result, "literal search result");
    }
  }

  public record Statistics(int unitUses, int distinctUnitContents, int largestUnitBytes) {}

  public record SourceExcerpt(
      String path, int startLine, int endLine, String sourceSha256, ImmutableBytes rawUtf8) {}
}
