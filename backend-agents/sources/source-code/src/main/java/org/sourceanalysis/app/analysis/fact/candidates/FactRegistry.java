package org.sourceanalysis.app.analysis.fact.candidates;

import java.util.List;
import java.util.Objects;

/** Closed, versioned templates that determine which frozen-Java facts M1 must enumerate. */
public record FactRegistry(String schemaVersion, List<FactTemplate> templates) {

  private static final String SCHEMA_VERSION = "proven-code-facts-fact-registry-v3";
  private static final String JAVA_BOUNDARY_KIND = "JAVA_BOUNDARY_INVOCATION";
  private static final String JAVA_GUARD_KIND = "JAVA_GUARD_CONDITION";
  private static final String JAVA_EXACT_CALL_KIND = "JAVA_EXACT_CALL";

  public FactRegistry {
    if (!SCHEMA_VERSION.equals(schemaVersion)) {
      throw new IllegalArgumentException(
          "FACT_PROFILE_INVALID: registry schema version is invalid");
    }
    templates = List.copyOf(Objects.requireNonNull(templates, "fact templates"));
    if (templates.isEmpty()
        || templates.stream().map(FactTemplate::candidateFactKey).distinct().count()
            != templates.size()) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: registry templates are invalid");
    }
  }

  /** Returns the closed frozen-Java templates for boundaries, guards, and exact Java calls. */
  public static FactRegistry standardJavaFacts() {
    return new FactRegistry(
        SCHEMA_VERSION,
        List.of(
            new FactTemplate(
                JAVA_BOUNDARY_KIND,
                JAVA_BOUNDARY_KIND,
                List.of(
                    atom("INVOCATION_CALL_ID", "RELATIONSHIP", "SYMBOL_REF"),
                    atom("STATIC_TARGET_TYPE", "ATTRIBUTE", "STRING"),
                    atom("STATIC_TARGET_METHOD", "ATTRIBUTE", "STRING"),
                    atom("STATIC_TARGET_SIGNATURE", "ATTRIBUTE", "STRING"),
                    atom("ORDERED_ARGUMENTS", "RELATIONSHIP", "SYMBOL_REF"),
                    atom("JAVA_LOCAL_ORIGINS", "RELATIONSHIP", "SYMBOL_REF"),
                    atom("CONTROL_CONTEXT", "CONDITION", "SYMBOL_REF"),
                    atom("INVOCATION_EVIDENCE", "RELATIONSHIP", "SYMBOL_REF"))),
            new FactTemplate(
                JAVA_GUARD_KIND,
                JAVA_GUARD_KIND,
                List.of(atom("CONTROL_CONDITION", "CONDITION", "STRING"))),
            new FactTemplate(
                JAVA_EXACT_CALL_KIND,
                JAVA_EXACT_CALL_KIND,
                List.of(
                    atom("INVOCATION_CALL_ID", "RELATIONSHIP", "SYMBOL_REF"),
                    atom("STATIC_TARGET_TYPE", "ATTRIBUTE", "STRING"),
                    atom("STATIC_TARGET_METHOD", "ATTRIBUTE", "STRING"),
                    atom("STATIC_TARGET_SIGNATURE", "ATTRIBUTE", "STRING")))));
  }

  /** Internal v0 callers receive the complete v3 frozen-Java registry. */
  public static FactRegistry standardJavaBoundary() {
    return standardJavaFacts();
  }

  private static RequiredAtomTemplate atom(String key, String role, String valueType) {
    return new RequiredAtomTemplate(
        key, role, valueType, List.of("SOURCE_EXCERPT", "RULE_APPLICATION"));
  }

  /** One explicit template in the frozen registry. */
  public record FactTemplate(
      String candidateFactKey, String kind, List<RequiredAtomTemplate> requiredAtoms) {

    public FactTemplate {
      requireText(candidateFactKey, "candidate fact key");
      if (!(JAVA_BOUNDARY_KIND.equals(kind)
          || JAVA_GUARD_KIND.equals(kind)
          || JAVA_EXACT_CALL_KIND.equals(kind))) {
        throw new IllegalArgumentException("FACT_KIND_UNSUPPORTED");
      }
      requiredAtoms = List.copyOf(Objects.requireNonNull(requiredAtoms, "required atoms"));
      if (requiredAtoms.isEmpty()
          || requiredAtoms.stream().map(RequiredAtomTemplate::atomKey).distinct().count()
              != requiredAtoms.size()) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: required atoms are invalid");
      }
    }
  }

  /** One atom copied, without interpretation, into each candidate instantiated from a template. */
  public record RequiredAtomTemplate(
      String atomKey, String role, String valueType, List<String> expectedEvidenceKinds) {

    public RequiredAtomTemplate {
      requireText(atomKey, "atom key");
      if (!("ATTRIBUTE".equals(role) || "RELATIONSHIP".equals(role) || "CONDITION".equals(role))) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: atom role is invalid");
      }
      if (!("STRING".equals(valueType) || "SYMBOL_REF".equals(valueType))) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: atom value type is invalid");
      }
      expectedEvidenceKinds =
          List.copyOf(Objects.requireNonNull(expectedEvidenceKinds, "atom evidence kinds"));
      if (!expectedEvidenceKinds.equals(List.of("SOURCE_EXCERPT", "RULE_APPLICATION"))) {
        throw new IllegalArgumentException("FACT_PROFILE_INVALID: atom evidence kinds are invalid");
      }
    }
  }

  private static void requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("FACT_PROFILE_INVALID: " + label + " is required");
    }
  }
}
