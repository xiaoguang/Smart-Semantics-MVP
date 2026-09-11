package org.sourceanalysis.app.analysis.interpretation.process;

import java.util.List;
import java.util.Objects;

/** The small business-understanding packet that M8 may send to a process-model Provider. */
public record ProcessModelPacketV1(
    String schemaVersion,
    String packetKind,
    List<ReaderFlowCardV1> flowCards,
    List<ReaderRelationCardV1> relationCards,
    List<ReaderTermV1> vocabulary,
    List<ReaderLimitationV1> limitations,
    List<ReaderSourceV1> sources) {

  public ProcessModelPacketV1 {
    if (!"flow-interpretation-process-model-packet-v1".equals(schemaVersion)
        || !"DRY_BUSINESS_PROCESS_READER".equals(packetKind)) {
      throw new IllegalArgumentException("process model packet identity is invalid");
    }
    flowCards = List.copyOf(Objects.requireNonNull(flowCards));
    relationCards = List.copyOf(Objects.requireNonNull(relationCards));
    vocabulary = List.copyOf(Objects.requireNonNull(vocabulary));
    limitations = List.copyOf(Objects.requireNonNull(limitations));
    sources = List.copyOf(Objects.requireNonNull(sources));
    if (flowCards.isEmpty() || sources.isEmpty()) {
      throw new IllegalArgumentException("process model packet is incomplete");
    }
  }

  public record ReaderFlowCardV1(
      String flowKey,
      ReaderEntryV1 entry,
      List<ReaderActivityV1> activities,
      List<ReaderValueV1> inputs,
      List<ReaderValueV1> outputs,
      List<ReaderConditionV1> conditions,
      List<ReaderOutcomeV1> outcomes,
      List<String> sourceKeys) {
    public ReaderFlowCardV1 {
      required(flowKey);
      Objects.requireNonNull(entry, "entry");
      activities = List.copyOf(Objects.requireNonNull(activities));
      inputs = List.copyOf(Objects.requireNonNull(inputs));
      outputs = List.copyOf(Objects.requireNonNull(outputs));
      conditions = List.copyOf(Objects.requireNonNull(conditions));
      outcomes = List.copyOf(Objects.requireNonNull(outcomes));
      sourceKeys = List.copyOf(Objects.requireNonNull(sourceKeys));
      if (activities.isEmpty() || outcomes.isEmpty() || sourceKeys.isEmpty()) {
        throw new IllegalArgumentException("reader Flow card is incomplete");
      }
    }
  }

  public record ReaderEntryV1(
      String transport, String method, String route, String technicalOperation) {
    public ReaderEntryV1 {
      required(transport);
      required(technicalOperation);
    }
  }

  public record ReaderActivityV1(
      String activityKey,
      int ordinal,
      String activityKind,
      String technicalOperation,
      List<String> inputKeys,
      List<String> outputKeys,
      List<String> sourceKeys) {
    public ReaderActivityV1 {
      required(activityKey);
      required(activityKind);
      required(technicalOperation);
      inputKeys = List.copyOf(Objects.requireNonNull(inputKeys));
      outputKeys = List.copyOf(Objects.requireNonNull(outputKeys));
      sourceKeys = List.copyOf(Objects.requireNonNull(sourceKeys));
      if (ordinal < 1 || sourceKeys.isEmpty()) {
        throw new IllegalArgumentException("reader activity is invalid");
      }
    }
  }

  public record ReaderValueV1(
      String valueKey,
      String role,
      String technicalName,
      String observedValue,
      List<String> sourceKeys) {
    public ReaderValueV1 {
      required(valueKey);
      required(role);
      required(technicalName);
      sourceKeys = List.copyOf(Objects.requireNonNull(sourceKeys));
    }
  }

  public record ReaderConditionV1(
      String conditionKey, String expression, String polarity, List<String> sourceKeys) {
    public ReaderConditionV1 {
      required(conditionKey);
      required(expression);
      required(polarity);
      sourceKeys = List.copyOf(Objects.requireNonNull(sourceKeys));
    }
  }

  public record ReaderOutcomeV1(
      String outcomeKey, String terminalKind, String resultSummary, List<String> sourceKeys) {
    public ReaderOutcomeV1 {
      required(outcomeKey);
      required(terminalKind);
      required(resultSummary);
      sourceKeys = List.copyOf(Objects.requireNonNull(sourceKeys));
    }
  }

  public record ReaderRelationCardV1(
      String relationKey,
      String leftFlowKey,
      String rightFlowKey,
      String strongestSignalLevel,
      String direction,
      String relationUse,
      String connectionKind,
      String connectionSummary,
      List<String> sourceKeys) {
    public ReaderRelationCardV1 {
      required(relationKey);
      required(leftFlowKey);
      required(rightFlowKey);
      required(strongestSignalLevel);
      required(direction);
      required(relationUse);
      required(connectionKind);
      required(connectionSummary);
      sourceKeys = List.copyOf(Objects.requireNonNull(sourceKeys));
    }
  }

  public record ReaderTermV1(
      String termKey, String proposalKind, String label, String purpose, List<String> flowKeys) {
    public ReaderTermV1 {
      required(termKey);
      required(proposalKind);
      required(label);
      required(purpose);
      flowKeys = List.copyOf(Objects.requireNonNull(flowKeys));
      if (flowKeys.isEmpty()) throw new IllegalArgumentException("reader term requires a Flow");
    }
  }

  public record ReaderLimitationV1(String limitationKey, String summary, List<String> flowKeys) {
    public ReaderLimitationV1 {
      required(limitationKey);
      required(summary);
      flowKeys = List.copyOf(Objects.requireNonNull(flowKeys));
      if (flowKeys.isEmpty())
        throw new IllegalArgumentException("reader limitation requires a Flow");
    }
  }

  public record ReaderSourceV1(
      String sourceKey,
      String repositoryRelativeFile,
      int startLine,
      int endLine,
      String symbol,
      String excerpt) {
    public ReaderSourceV1 {
      required(sourceKey);
      required(repositoryRelativeFile);
      required(excerpt);
      if (repositoryRelativeFile.startsWith("/") || startLine < 1 || endLine < startLine) {
        throw new IllegalArgumentException("reader source is invalid");
      }
    }
  }

  private static void required(String value) {
    if (value == null || value.isBlank())
      throw new IllegalArgumentException("reader packet value is required");
  }
}
