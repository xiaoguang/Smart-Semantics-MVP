package org.sourceanalysis.app.analysis.code.publish;

import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.EngineDescriptor;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.artifact.ArtifactReference;

/** The complete, engine-neutral Java navigation index reopened from the Step 03 artifact. */
public record JavaCodeIndex(
    EngineDescriptor engine,
    String snapshotId,
    ArtifactReference snapshotRef,
    JavaDeclarationCatalog catalog,
    List<EntryCollection> entries,
    EntryCodeContext.TechnicalEnhancements technicalEnhancements) {

  public JavaCodeIndex {
    engine = Objects.requireNonNull(engine, "code engine descriptor");
    if (snapshotId == null || snapshotId.isBlank()) {
      throw new IllegalArgumentException("code index snapshot ID is required");
    }
    snapshotRef = Objects.requireNonNull(snapshotRef, "code index snapshot reference");
    catalog = Objects.requireNonNull(catalog, "code index catalog");
    entries = List.copyOf(Objects.requireNonNull(entries, "code index entries"));
    technicalEnhancements = Objects.requireNonNull(technicalEnhancements, "technical enhancements");
    if (!snapshotId.equals(catalog.snapshotId())) {
      throw new IllegalArgumentException("code index catalog must use the same snapshot");
    }
    java.util.Set<String> entryIds = new java.util.HashSet<>();
    for (EntryCollection entry : entries) {
      if (entry == null || !entryIds.add(entry.seed().entryId())) {
        throw new IllegalArgumentException("code index entries must be non-null and unique");
      }
      if (entry.context() != null
          && !entry.context().technicalEnhancements().equals(technicalEnhancements)) {
        throw new IllegalArgumentException("entry enhancement state must match the index");
      }
    }
  }

  /** One discovered entry and either its collected context or its explicit stop reason. */
  public record EntryCollection(EntrySeed seed, EntryCodeContext context, String reason) {

    public EntryCollection {
      seed = Objects.requireNonNull(seed, "entry seed");
      if (context == null) {
        if (reason == null || reason.isBlank()) {
          throw new IllegalArgumentException("uncollected entry requires a reason");
        }
      } else {
        if (reason != null
            || !seed.entryId().equals(context.entryId())
            || !seed.methodKey().equals(context.entryMethodKey())) {
          throw new IllegalArgumentException("collected entry must match its seed");
        }
      }
    }

    public static EntryCollection collected(EntrySeed seed, EntryCodeContext context) {
      return new EntryCollection(seed, context, null);
    }

    public static EntryCollection notCollected(EntrySeed seed, String reason) {
      return new EntryCollection(seed, null, reason);
    }

    public String collectionStatus() {
      return context == null ? "NOT_COLLECTED" : "COLLECTED";
    }
  }
}
