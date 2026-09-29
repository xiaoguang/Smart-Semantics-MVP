package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.artifact.ModulePublicationReference;

/** Complete, source-bound frontend units made available to Step05 without a second parser pass. */
public record FrontendSourceUnits(
    VerifiedSourceInventoryReference sourceInventory,
    ModulePublicationReference frontendPublication,
    List<Unit> units) {

  public FrontendSourceUnits {
    sourceInventory = Objects.requireNonNull(sourceInventory, "frontend source inventory");
    frontendPublication = Objects.requireNonNull(frontendPublication, "frontend publication");
    units = List.copyOf(Objects.requireNonNull(units, "frontend source units"));
    if (units.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("frontend source units cannot contain null values");
    }
    Set<String> identifiers = new HashSet<>();
    for (Unit unit : units) {
      if (!identifiers.add(unit.sourceUnitId())) {
        throw new IllegalArgumentException("frontend source unit IDs must be unique");
      }
    }
  }

  /**
   * One complete saved frontend unit, identified by its R0 file identity and saved source range.
   */
  public record Unit(
      String sourceUnitId,
      String path,
      String sourceSha256,
      SourceRange sourceUnitRange,
      FrontendWrapperCall.SourceUnitKind sourceUnitKind,
      String text) {

    public Unit {
      if (sourceUnitId == null
          || sourceUnitId.isBlank()
          || path == null
          || path.isBlank()
          || path.startsWith("/")
          || path.contains("..")
          || sourceSha256 == null
          || !sourceSha256.matches("[0-9a-f]{64}")
          || text == null) {
        throw new IllegalArgumentException("frontend source unit identity is invalid");
      }
      sourceUnitRange = Objects.requireNonNull(sourceUnitRange, "frontend source unit range");
      sourceUnitKind = Objects.requireNonNull(sourceUnitKind, "frontend source unit kind");
    }
  }
}
