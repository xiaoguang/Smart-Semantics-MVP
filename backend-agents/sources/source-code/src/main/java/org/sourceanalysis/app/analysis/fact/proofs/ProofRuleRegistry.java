package org.sourceanalysis.app.analysis.fact.proofs;

import java.util.List;
import java.util.Objects;

/** Closed, versioned registry of source-rule pairs that can close a frozen-Java Fact atom. */
public record ProofRuleRegistry(String schemaVersion, List<RuleAllowance> allowances) {

  private static final String SCHEMA_VERSION = "proven-code-facts-proof-rules-v1";

  public ProofRuleRegistry {
    if (!SCHEMA_VERSION.equals(schemaVersion)) {
      throw new IllegalArgumentException("PROOF_RULE_REGISTRY_INVALID");
    }
    allowances = List.copyOf(Objects.requireNonNull(allowances, "rule allowances"));
    if (allowances.isEmpty()
        || allowances.stream().map(RuleAllowance::subjectCategory).distinct().count()
            != allowances.size()) {
      throw new IllegalArgumentException("PROOF_RULE_REGISTRY_INVALID");
    }
  }

  /** Returns the complete v0 allowlist for generic Java boundary invocation candidates. */
  public static ProofRuleRegistry standardJavaBoundary() {
    return new ProofRuleRegistry(
        SCHEMA_VERSION,
        List.of(
            allowance(SubjectCategory.CALL_SITE, List.of("java-static-field-receiver-call-v1")),
            allowance(SubjectCategory.CALL_TARGET, List.of("java-static-field-receiver-call-v1")),
            allowance(SubjectCategory.BOUNDARY_INVOCATION, List.of("java-boundary-invocation-v1")),
            allowance(SubjectCategory.ARGUMENT_TO_BOUNDARY, List.of("java-boundary-argument-v1")),
            allowance(
                SubjectCategory.JAVA_LOCAL_ORIGIN,
                List.of(
                    "java-argument-binding-v1",
                    "java-direct-field-assignment-v1",
                    "java-direct-local-assignment-v1",
                    "java-direct-setter-property-v1",
                    "java-local-read-v1",
                    "java-parameter-symbol-v1",
                    "java-single-reaching-definition-v1",
                    "source-element-parser-v1")),
            allowance(SubjectCategory.BASIC_BLOCK, List.of("control-flow-method-body-v1")),
            allowance(SubjectCategory.GUARD, List.of("control-flow-if-guard-v1"))));
  }

  /** Returns whether this exact rule/version may support the subject category. */
  public boolean permits(SubjectCategory category, String ruleId, String ruleVersion) {
    if (category == null || ruleId == null || ruleVersion == null) return false;
    return allowances.stream()
        .filter(allowance -> allowance.subjectCategory() == category)
        .anyMatch(
            allowance ->
                allowance.ruleVersion().equals(ruleVersion)
                    && allowance.ruleIds().contains(ruleId));
  }

  private static RuleAllowance allowance(SubjectCategory category, List<String> ruleIds) {
    return new RuleAllowance(category, ruleIds, "v1");
  }

  /** Closed category for a program subject; never inferred from display text. */
  public enum SubjectCategory {
    CALL_SITE,
    CALL_TARGET,
    BOUNDARY_INVOCATION,
    ARGUMENT_TO_BOUNDARY,
    JAVA_LOCAL_ORIGIN,
    BASIC_BLOCK,
    GUARD
  }

  /** Exact rule IDs and version that may support one subject category. */
  public record RuleAllowance(
      SubjectCategory subjectCategory, List<String> ruleIds, String ruleVersion) {

    public RuleAllowance {
      subjectCategory = Objects.requireNonNull(subjectCategory, "subject category");
      ruleIds = List.copyOf(Objects.requireNonNull(ruleIds, "rule IDs"));
      if (ruleIds.isEmpty() || ruleIds.stream().anyMatch(value -> value == null || value.isBlank())) {
        throw new IllegalArgumentException("PROOF_RULE_REGISTRY_INVALID");
      }
      if (ruleIds.size() != ruleIds.stream().distinct().count()
          || ruleVersion == null
          || ruleVersion.isBlank()) {
        throw new IllegalArgumentException("PROOF_RULE_REGISTRY_INVALID");
      }
    }
  }
}
