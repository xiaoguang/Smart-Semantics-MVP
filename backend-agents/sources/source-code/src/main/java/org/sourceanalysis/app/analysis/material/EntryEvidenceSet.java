package org.sourceanalysis.app.analysis.material;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.ApplicationDiscoveryReference;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendDiagnosticRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceFileDisposition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/**
 * Immutable, one-file-per-entry Step05 material. This is deliberately separate from the retained
 * code-reading packet contract.
 */
public record EntryEvidenceSet(
    Header header, List<Entry> entries, List<FrontendCoverage> frontendCoverage) {

  public static final String SCHEMA_VERSION = "entry-evidence-v1";

  public EntryEvidenceSet {
    header = Objects.requireNonNull(header, "entry-evidence header");
    entries = immutable(entries, "entry-evidence entries");
    frontendCoverage = immutable(frontendCoverage, "entry-evidence frontend coverage");
    distinct(entries, Entry::entryId, "entry-evidence entry IDs");
    distinct(frontendCoverage, FrontendCoverage::requestId, "entry-evidence request IDs");
  }

  /** Exact reopened upstream identity and the actual evidence resource budget. */
  public record Header(
      VerifiedSourceInventoryReference sourceInventory,
      ApplicationDiscoveryReference applicationDiscovery,
      ProgramGraphsReference navigationPublication,
      AnalysisStepPublicationReference persistencePublication,
      ModulePublicationReference frontendPublication,
      String sourceSnapshotId,
      FrontendHttpIndex.Status frontendStatus,
      List<FrontendSourceFileDisposition> frontendFiles,
      List<FrontendDiagnosticRecord> frontendDiagnostics,
      EntryEvidenceProfile profile) {

    public Header {
      sourceInventory = Objects.requireNonNull(sourceInventory, "entry-evidence source inventory");
      applicationDiscovery =
          Objects.requireNonNull(applicationDiscovery, "entry-evidence application discovery");
      navigationPublication =
          Objects.requireNonNull(navigationPublication, "entry-evidence navigation publication");
      persistencePublication =
          Objects.requireNonNull(persistencePublication, "entry-evidence persistence publication");
      frontendPublication =
          Objects.requireNonNull(frontendPublication, "entry-evidence frontend publication");
      required(sourceSnapshotId, "entry-evidence source snapshot");
      frontendStatus = Objects.requireNonNull(frontendStatus, "entry-evidence frontend status");
      frontendFiles = immutable(frontendFiles, "entry-evidence frontend files");
      frontendDiagnostics = immutable(frontendDiagnostics, "entry-evidence frontend diagnostics");
      profile = Objects.requireNonNull(profile, "entry-evidence profile");
    }
  }

  /** One backend denominator entry and every already-collected material selected for it. */
  public record Entry(
      String entryId,
      HttpEntryPoint entry,
      AssemblyStatus assemblyStatus,
      Coverage coverage,
      Frontend frontend,
      Java java,
      Persistence persistence,
      List<SourceReference> sourceRefs,
      List<Limitation> limitations) {

    public Entry {
      required(entryId, "entry-evidence entry ID");
      entry = Objects.requireNonNull(entry, "entry-evidence HTTP entry");
      if (!entryId.equals(entry.entryId().value())) {
        throw new IllegalArgumentException(
            "entry-evidence entry identity does not match HTTP entry");
      }
      assemblyStatus = Objects.requireNonNull(assemblyStatus, "entry-evidence assembly status");
      coverage = Objects.requireNonNull(coverage, "entry-evidence coverage");
      frontend = Objects.requireNonNull(frontend, "entry-evidence frontend material");
      java = Objects.requireNonNull(java, "entry-evidence Java material");
      persistence = Objects.requireNonNull(persistence, "entry-evidence persistence material");
      sourceRefs = immutable(sourceRefs, "entry-evidence source references");
      limitations = immutable(limitations, "entry-evidence limitations");
      distinct(sourceRefs, SourceReference::reference, "entry-evidence source references");
      if (assemblyStatus == AssemblyStatus.NOT_ASSEMBLED && coverage.javaCollected()) {
        throw new IllegalArgumentException(
            "unassembled entry-evidence cannot claim collected Java");
      }
    }
  }

  public enum AssemblyStatus {
    ASSEMBLED,
    ASSEMBLED_WITH_LIMITATIONS,
    NOT_ASSEMBLED
  }

  /** Separates backend collection and frontend/persistence availability instead of one gap flag. */
  public record Coverage(
      boolean javaCollected,
      FrontendStatus frontendStatus,
      PersistenceMaterialIndex.Status persistenceStatus) {

    public Coverage {
      frontendStatus = Objects.requireNonNull(frontendStatus, "entry-evidence frontend status");
      persistenceStatus =
          Objects.requireNonNull(persistenceStatus, "entry-evidence persistence status");
    }
  }

  public enum FrontendStatus {
    DISABLED,
    MATCHED,
    NO_MATCHED_REQUEST,
    MATCHING_LIMITATIONS
  }

  /** Confirmed and candidate frontend uses remain separate. */
  public record Frontend(
      List<RequestUse> requestUses,
      List<RequestUse> candidateRequestUses,
      List<FrontendSourceUnits.Unit> units) {

    public Frontend {
      requestUses = immutable(requestUses, "entry-evidence frontend request uses");
      candidateRequestUses =
          immutable(candidateRequestUses, "entry-evidence frontend candidate request uses");
      units = immutable(units, "entry-evidence frontend units");
      distinct(units, FrontendSourceUnits.Unit::sourceUnitId, "entry-evidence frontend unit IDs");
      Set<String> unitIds =
          units.stream()
              .map(FrontendSourceUnits.Unit::sourceUnitId)
              .collect(java.util.stream.Collectors.toSet());
      if (requestUses.stream().anyMatch(use -> !unitIds.containsAll(use.sourceUnitIds()))
          || candidateRequestUses.stream()
              .anyMatch(use -> !unitIds.containsAll(use.sourceUnitIds()))) {
        throw new IllegalArgumentException("entry-evidence frontend use has an unavailable unit");
      }
    }
  }

  /**
   * One saved request, its material-match result, and all complete source units used to support it.
   */
  public record RequestUse(
      FrontendHttpRequestRecord request,
      FrontendEntryLinkRecord.Resolution resolution,
      List<String> candidateEntryIds,
      List<String> sourceUnitIds,
      String reason) {

    public RequestUse {
      request = Objects.requireNonNull(request, "entry-evidence frontend request");
      resolution = Objects.requireNonNull(resolution, "entry-evidence frontend resolution");
      candidateEntryIds = immutable(candidateEntryIds, "entry-evidence candidate entry IDs");
      sourceUnitIds = immutable(sourceUnitIds, "entry-evidence source-unit IDs");
      distinctStrings(candidateEntryIds, "entry-evidence candidate entry IDs");
      distinctStrings(sourceUnitIds, "entry-evidence source-unit IDs");
      if (reason != null && reason.isBlank()) {
        throw new IllegalArgumentException("entry-evidence frontend reason cannot be blank");
      }
    }
  }

  /**
   * Complete calls retain their structured observations; this field is a compact entry-local index.
   */
  public record Java(
      List<EntryCodeContext.MethodCode> methods,
      List<EntryCodeContext.CallSite> calls,
      List<CallObservation> observations,
      List<EntryCodeContext.SupportingSource> supportingSources,
      EntryCodeContext.TechnicalEnhancements technicalEnhancements) {

    public Java {
      methods = immutable(methods, "entry-evidence Java methods");
      calls = immutable(calls, "entry-evidence Java calls");
      observations = immutable(observations, "entry-evidence Java observations");
      supportingSources = immutable(supportingSources, "entry-evidence Java supporting sources");
      technicalEnhancements =
          Objects.requireNonNull(technicalEnhancements, "entry-evidence technical enhancements");
      distinct(methods, EntryCodeContext.MethodCode::methodKey, "entry-evidence Java method keys");
      distinct(calls, EntryCodeContext.CallSite::callKey, "entry-evidence Java call keys");
      distinct(observations, CallObservation::observationId, "entry-evidence Java observation IDs");
    }
  }

  /**
   * A compact index into {@link Java#calls()} observations, without duplicating their full values.
   */
  public record CallObservation(String observationId, String callKey, String code, String detail) {

    public CallObservation {
      required(observationId, "entry-evidence observation ID");
      required(callKey, "entry-evidence observation call key");
      required(code, "entry-evidence observation code");
      required(detail, "entry-evidence observation detail");
    }
  }

  /** Full selected persistence closure, including dependency resources and their diagnostics. */
  public record Persistence(
      List<PersistenceMaterialIndex.JavaBinding> bindings,
      List<PersistenceMaterialIndex.Statement> statements,
      List<PersistenceMaterialIndex.Resource> resources,
      List<PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses,
      List<PersistenceMaterialIndex.Diagnostic> diagnostics) {

    public Persistence {
      bindings = immutable(bindings, "entry-evidence persistence bindings");
      statements = immutable(statements, "entry-evidence persistence statements");
      resources = immutable(resources, "entry-evidence persistence resources");
      sqlAnalyses = immutable(sqlAnalyses, "entry-evidence persistence SQL analyses");
      diagnostics = immutable(diagnostics, "entry-evidence persistence diagnostics");
      distinct(
          bindings,
          PersistenceMaterialIndex.JavaBinding::methodKey,
          "entry-evidence binding methods");
      distinct(
          statements,
          PersistenceMaterialIndex.Statement::statementRef,
          "entry-evidence statements");
      distinct(
          resources, PersistenceMaterialIndex.Resource::resourcePath, "entry-evidence resources");
      distinct(
          sqlAnalyses,
          PersistenceMaterialIndex.SqlAnalysis::statementRef,
          "entry-evidence SQL analyses");
    }
  }

  /**
   * A preserved upstream limitation or an assembly-specific reason, never an invented conclusion.
   */
  public record Limitation(String code, String subjectRef, String detail) {

    public Limitation {
      required(code, "entry-evidence limitation code");
      required(subjectRef, "entry-evidence limitation subject");
      required(detail, "entry-evidence limitation detail");
    }
  }

  /**
   * A source-local identity already present in an entry file. {@code sourceSha256} is null only
   * where the upstream typed record did not persist a file digest; {@code sourceIdentity} then
   * retains its existing file/artifact identity instead of inventing one.
   */
  public record SourceReference(
      String reference,
      String kind,
      String path,
      SourceRange range,
      String sourceSha256,
      String sourceIdentity) {

    public SourceReference {
      required(reference, "entry-evidence source reference");
      required(kind, "entry-evidence source-reference kind");
      required(path, "entry-evidence source-reference path");
      if (path.startsWith("/") || path.contains("..")) {
        throw new IllegalArgumentException("entry-evidence source-reference path must be relative");
      }
      if (sourceSha256 != null && !sourceSha256.matches("[0-9a-f]{64}")) {
        throw new IllegalArgumentException("entry-evidence source-reference hash is invalid");
      }
      if (sourceIdentity != null && sourceIdentity.isBlank()) {
        throw new IllegalArgumentException(
            "entry-evidence source-reference identity cannot be blank");
      }
    }
  }

  /** One frontend denominator disposition, including unmatched material where no entry owns it. */
  public record FrontendCoverage(
      String requestId,
      FrontendHttpRequestRecord request,
      FrontendEntryLinkRecord.Resolution resolution,
      List<String> entryIds,
      List<String> includedEntryIds,
      List<FrontendSourceUnits.Unit> units,
      String reason) {

    public FrontendCoverage {
      required(requestId, "entry-evidence frontend coverage request ID");
      request = Objects.requireNonNull(request, "entry-evidence frontend coverage request");
      if (!requestId.equals(request.requestId())) {
        throw new IllegalArgumentException(
            "entry-evidence coverage request identity is inconsistent");
      }
      resolution =
          Objects.requireNonNull(resolution, "entry-evidence frontend coverage resolution");
      entryIds = immutable(entryIds, "entry-evidence frontend coverage entries");
      includedEntryIds = immutable(includedEntryIds, "entry-evidence frontend included entries");
      units = immutable(units, "entry-evidence frontend coverage units");
      distinctStrings(entryIds, "entry-evidence frontend coverage entries");
      distinctStrings(includedEntryIds, "entry-evidence frontend included entries");
      distinct(
          units,
          FrontendSourceUnits.Unit::sourceUnitId,
          "entry-evidence frontend coverage unit IDs");
      if (!entryIds.containsAll(includedEntryIds)) {
        throw new IllegalArgumentException(
            "entry-evidence included frontend entries must be request candidates");
      }
      if (reason != null && reason.isBlank()) {
        throw new IllegalArgumentException(
            "entry-evidence frontend coverage reason cannot be blank");
      }
    }
  }

  private static <T> List<T> immutable(List<T> values, String label) {
    List<T> copy = List.copyOf(Objects.requireNonNull(values, label));
    if (copy.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(label + " cannot contain null values");
    }
    return copy;
  }

  private static <T> void distinct(
      List<T> values, java.util.function.Function<T, String> key, String label) {
    Set<String> valuesByKey = new HashSet<>();
    for (T value : values) {
      if (!valuesByKey.add(key.apply(value))) {
        throw new IllegalArgumentException(label + " must be unique");
      }
    }
  }

  private static void distinctStrings(List<String> values, String label) {
    distinct(values, value -> value, label);
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
