package org.sourceanalysis.app.analysis.material;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.graph.ProgramGraphsReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;

/** Immutable complete-unit reading view assembled from already-read upstream material. */
public record CodeReadingMaterialSet(
    Header header, List<Packet> packets, List<EntryCoverage> coverage) {

  public CodeReadingMaterialSet {
    header = Objects.requireNonNull(header, "reading-material header");
    packets = immutable(packets, "reading-material packets");
    coverage = immutable(coverage, "reading-material coverage");
    requireDistinct(packets, Packet::packetId, "reading-material packet IDs");
    requireDistinct(coverage, EntryCoverage::entryId, "reading-material coverage entry IDs");
  }

  /** Complete upstream identity and actual profile for this material view. */
  public record Header(
      VerifiedSourceInventoryReference sourceInventory,
      ProgramGraphsReference navigationPublication,
      AnalysisStepPublicationReference persistencePublication,
      String sourceSnapshotId,
      CodeReadingMaterialProfile profile) {

    public Header {
      sourceInventory = Objects.requireNonNull(sourceInventory, "verified source inventory");
      navigationPublication =
          Objects.requireNonNull(navigationPublication, "navigation publication");
      persistencePublication =
          Objects.requireNonNull(persistencePublication, "persistence publication");
      required(sourceSnapshotId, "source snapshot ID");
      profile = Objects.requireNonNull(profile, "reading material profile");
    }
  }

  /** A self-contained bounded packet with complete selected units and explicit omissions. */
  public record Packet(
      String packetId,
      List<EntrySeed> entries,
      List<EntryCodeContext.MethodCode> methods,
      List<EntryCall> calls,
      PersistenceSelection persistence,
      List<SourceReference> sourceReferences,
      List<UnselectedUnit> unselectedUnits,
      List<String> limitations,
      long selfContainedUtf8Bytes) {

    public Packet {
      required(packetId, "reading-material packet ID");
      entries = immutable(entries, "packet entries");
      methods = immutable(methods, "packet methods");
      calls = immutable(calls, "packet calls");
      persistence = Objects.requireNonNull(persistence, "packet persistence selection");
      sourceReferences = immutable(sourceReferences, "packet source references");
      unselectedUnits = immutable(unselectedUnits, "packet unselected units");
      limitations = immutable(limitations, "packet limitations");
      if (selfContainedUtf8Bytes < 0L) {
        throw new IllegalArgumentException("packet UTF-8 byte count cannot be negative");
      }
      requireDistinct(entries, EntrySeed::entryId, "packet entry IDs");
      requireDistinct(methods, EntryCodeContext.MethodCode::methodKey, "packet method keys");
      requireDistinct(sourceReferences, SourceReference::sourceRef, "packet source references");
      Set<String> entryIds =
          entries.stream().map(EntrySeed::entryId).collect(java.util.stream.Collectors.toSet());
      if (calls.stream().anyMatch(call -> !entryIds.contains(call.entryId()))
          || unselectedUnits.stream().anyMatch(unit -> !entryIds.contains(unit.entryId()))) {
        throw new IllegalArgumentException(
            "packet entry-owned material must belong to a packet entry");
      }
    }
  }

  /** A physical call retained with the entry that owns that navigation occurrence. */
  public record EntryCall(String entryId, EntryCodeContext.CallSite call) {

    public EntryCall {
      required(entryId, "entry-owned call entry ID");
      call = Objects.requireNonNull(call, "entry-owned call");
    }
  }

  /** The selected already-read persistence records for a packet; no XML or SQL is reparsed. */
  public record PersistenceSelection(
      List<PersistenceMaterialIndex.Resource> resources,
      List<PersistenceMaterialIndex.Statement> statements,
      List<PersistenceMaterialIndex.JavaBinding> bindings,
      List<PersistenceMaterialIndex.SqlAnalysis> sqlAnalyses,
      List<PersistenceMaterialIndex.Diagnostic> diagnostics) {

    public PersistenceSelection {
      resources = immutable(resources, "packet persistence resources");
      statements = immutable(statements, "packet persistence statements");
      bindings = immutable(bindings, "packet persistence bindings");
      sqlAnalyses = immutable(sqlAnalyses, "packet SQL analyses");
      diagnostics = immutable(diagnostics, "packet persistence diagnostics");
    }
  }

  /** One short source reference and its exact selected unit location. */
  public record SourceReference(String sourceRef, UnitLocation location) {

    public SourceReference {
      required(sourceRef, "material source reference");
      location = Objects.requireNonNull(location, "material source location");
    }
  }

  /** A location within one material unit, not an independently reopened source file. */
  public record UnitLocation(
      String unitKind, String unitRef, String path, int startLine, int endLine) {

    public UnitLocation {
      required(unitKind, "material unit kind");
      required(unitRef, "material unit reference");
      required(path, "material unit path");
      if (startLine < 1 || endLine < startLine) {
        throw new IllegalArgumentException("material unit line range is invalid");
      }
    }
  }

  /** An intact unit intentionally left outside a packet, with its entry-specific reason. */
  public record UnselectedUnit(String entryId, String unitKind, String unitRef, String reason) {

    public UnselectedUnit {
      required(entryId, "unselected unit entry ID");
      required(unitKind, "unselected unit kind");
      required(unitRef, "unselected unit reference");
      required(reason, "unselected unit reason");
    }
  }

  /** One complete entry denominator disposition across all material packets. */
  public record EntryCoverage(
      String entryId, List<String> packetIds, CoverageStatus status, List<String> limitations) {

    public EntryCoverage {
      required(entryId, "material coverage entry ID");
      packetIds = immutable(packetIds, "material coverage packet IDs");
      status = Objects.requireNonNull(status, "material coverage status");
      limitations = immutable(limitations, "material coverage limitations");
      requireDistinct(packetIds, value -> value, "material coverage packet IDs");
    }
  }

  public enum CoverageStatus {
    COLLECTED,
    COLLECTED_WITH_LIMITATIONS,
    NOT_COLLECTED
  }

  private static <T> List<T> immutable(List<T> values, String label) {
    List<T> copy = List.copyOf(Objects.requireNonNull(values, label));
    if (copy.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException(label + " cannot contain null values");
    }
    return copy;
  }

  private static <T> void requireDistinct(
      List<T> values, java.util.function.Function<T, String> identifier, String label) {
    Set<String> identifiers = new HashSet<>();
    for (T value : values) {
      String identity = identifier.apply(value);
      if (!identifiers.add(identity)) {
        throw new IllegalArgumentException(label + " must be unique");
      }
    }
  }

  private static void required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
  }
}
