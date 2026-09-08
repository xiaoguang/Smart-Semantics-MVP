package org.sourceanalysis.app.analysis.interpretation.registry;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.flow.publish.BusinessFlowsReference;
import org.sourceanalysis.app.analysis.interpretation.proposal.BusinessRegistryProposal;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalExecutionSet;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalFlowDisposition;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTask;
import org.sourceanalysis.app.analysis.interpretation.proposal.RegistryProposalTaskSet;

/** Freezes every closed R0 Flow outcome into the one repository-local finite-key registry. */
public final class RepositoryInterpretationRegistryFreezer {

  private static final Comparator<String> UTF8_ORDER =
      RepositoryInterpretationRegistryFreezer::compareUtf8;

  /** Creates one registry only after the complete eligible R0 denominator is closed. */
  public RepositoryInterpretationRegistry freeze(
      RegistryProposalTaskSet taskSet,
      RegistryProposalExecutionSet executionSet,
      BusinessFlowsReference businessFlows) {
    try {
      Objects.requireNonNull(taskSet, "registry proposal task set");
      Objects.requireNonNull(executionSet, "registry proposal execution set");
      Objects.requireNonNull(businessFlows, "business Flows");
      if (!businessFlows.publication().equals(taskSet.businessFlowsPublicationRef())
          || !taskSet.businessFlowsPublicationRef().equals(businessFlows.publication())
          || !taskSet
              .businessFlowsPublicationRef()
              .address()
              .runId()
              .equals(executionSet.taskSetPublicationRef().address().runId())) {
        throw failure("REGISTRY_FREEZE_INCOMPLETE");
      }
      return freezeClosed(taskSet, executionSet, businessFlows);
    } catch (RegistryFreezeException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new RegistryFreezeException("REGISTRY_FREEZE_INCOMPLETE", exception);
    }
  }

  private RepositoryInterpretationRegistry freezeClosed(
      RegistryProposalTaskSet taskSet,
      RegistryProposalExecutionSet executionSet,
      BusinessFlowsReference businessFlows) {
    Map<String, RegistryProposalTask> taskById = new HashMap<>();
    Map<String, RegistryProposalTask> taskByFlow = new HashMap<>();
    for (RegistryProposalTask task : taskSet.tasks()) {
      if (taskById.put(task.taskSpecId(), task) != null
          || taskByFlow.put(task.flowSliceId(), task) != null) {
        throw failure("REGISTRY_FREEZE_INCOMPLETE");
      }
    }
    if (executionSet.rounds().size() != taskSet.tasks().size()
        || executionSet.generationReceipts().size() != taskSet.tasks().size()
        || executionSet.flowDispositions().size() != taskSet.tasks().size()) {
      throw failure("REGISTRY_FREEZE_INCOMPLETE");
    }
    Map<String, String> roundIdByTask = new HashMap<>();
    executionSet
        .rounds()
        .forEach(
            value -> {
              if (roundIdByTask.put(value.taskSpecId(), value.registryProposalRoundId()) != null) {
                throw failure("REGISTRY_FREEZE_INCOMPLETE");
              }
            });
    Map<String, String> receiptIdByTask = new HashMap<>();
    executionSet
        .generationReceipts()
        .forEach(
            value -> {
              if (receiptIdByTask.put(value.taskSpecId(), value.generationReceiptId()) != null) {
                throw failure("REGISTRY_FREEZE_INCOMPLETE");
              }
            });
    if (!taskById.keySet().equals(roundIdByTask.keySet())
        || !taskById.keySet().equals(receiptIdByTask.keySet())) {
      throw failure("REGISTRY_FREEZE_INCOMPLETE");
    }

    Map<String, List<BusinessRegistryProposal>> proposalsByTask = new HashMap<>();
    Set<String> proposalIds = new HashSet<>();
    for (BusinessRegistryProposal proposal : executionSet.validatedProposals()) {
      RegistryProposalTask task = taskById.get(proposal.taskSpecId());
      if (task == null
          || !task.flowSliceId().equals(proposal.flowSliceId())
          || !task.evidenceCapsuleId().equals(proposal.evidenceCapsuleId())
          || !proposalIds.add(proposal.registryProposalId())) {
        throw failure("REGISTRY_FREEZE_INCOMPLETE");
      }
      proposalsByTask
          .computeIfAbsent(proposal.taskSpecId(), ignored -> new ArrayList<>())
          .add(proposal);
    }

    List<RepositoryInterpretationRegistryItem> items = new ArrayList<>();
    List<RepositoryInterpretationRegistryFlowDisposition> dispositions = new ArrayList<>();
    Set<String> flowIds = new HashSet<>();
    Set<String> provisionalKeys = new HashSet<>();
    for (RegistryProposalFlowDisposition disposition : executionSet.flowDispositions()) {
      RegistryProposalTask task = taskByFlow.get(disposition.flowSliceId());
      if (task == null
          || !flowIds.add(disposition.flowSliceId())
          || !task.taskSpecId().equals(disposition.taskSpecId())
          || !roundIdByTask.get(task.taskSpecId()).equals(disposition.registryProposalRoundId())
          || !receiptIdByTask.get(task.taskSpecId()).equals(disposition.generationReceiptId())) {
        throw failure("REGISTRY_FREEZE_INCOMPLETE");
      }
      List<BusinessRegistryProposal> flowProposals =
          proposalsByTask.getOrDefault(task.taskSpecId(), List.of()).stream()
              .sorted(
                  Comparator.comparing(BusinessRegistryProposal::registryProposalId, UTF8_ORDER))
              .toList();
      List<String> actualProposalIds =
          flowProposals.stream().map(BusinessRegistryProposal::registryProposalId).toList();
      if (!actualProposalIds.equals(disposition.registryProposalIds())) {
        throw failure("REGISTRY_FREEZE_INCOMPLETE");
      }
      if ("READY_FOR_FREEZE".equals(disposition.disposition())) {
        for (BusinessRegistryProposal proposal : flowProposals) {
          RepositoryInterpretationRegistryItem item = item(proposal);
          if (!provisionalKeys.add(item.provisionalKey())) {
            throw failure("REGISTRY_IDENTITY_COLLISION");
          }
          items.add(item);
        }
      }
      dispositions.add(
          new RepositoryInterpretationRegistryFlowDisposition(
              contentId(
                  "registry-flow-disposition",
                  List.of(
                      disposition.flowSliceId(),
                      disposition.disposition(),
                      disposition.taskSpecId(),
                      disposition.registryProposalRoundId(),
                      disposition.generationReceiptId(),
                      String.join("|", disposition.registryProposalIds()),
                      String.join("|", disposition.gapIds()),
                      disposition.reasonCode() == null ? "" : disposition.reasonCode())),
              disposition.flowSliceId(),
              disposition.disposition(),
              disposition.taskSpecId(),
              disposition.registryProposalRoundId(),
              disposition.generationReceiptId(),
              disposition.registryProposalIds(),
              disposition.gapIds(),
              disposition.reasonCode()));
    }
    if (!flowIds.equals(taskByFlow.keySet())) throw failure("REGISTRY_FREEZE_INCOMPLETE");

    items.sort(
        Comparator.comparing(RepositoryInterpretationRegistryItem::provisionalKey, UTF8_ORDER));
    dispositions.sort(
        Comparator.comparing(
            RepositoryInterpretationRegistryFlowDisposition::flowSliceId, UTF8_ORDER));
    RegistryProposalAccounting accounting =
        new RegistryProposalAccounting(
            taskSet.eligibleFlowSliceIds(),
            dispositions.stream()
                .filter(value -> "READY_FOR_FREEZE".equals(value.disposition()))
                .map(RepositoryInterpretationRegistryFlowDisposition::flowSliceId)
                .toList(),
            dispositions.stream()
                .filter(value -> "GAP".equals(value.disposition()))
                .map(RepositoryInterpretationRegistryFlowDisposition::flowSliceId)
                .toList(),
            dispositions.stream()
                .filter(value -> "FAILED".equals(value.disposition()))
                .map(RepositoryInterpretationRegistryFlowDisposition::flowSliceId)
                .toList(),
            items.stream().map(RepositoryInterpretationRegistryItem::registryProposalId).toList(),
            items.stream().map(RepositoryInterpretationRegistryItem::provisionalKey).toList());
    return new RepositoryInterpretationRegistry(
        contentId(
            "repository-interpretation-registry",
            List.of(
                businessFlows.publication().analysisStepArtifactRoot().value(),
                String.join("|", taskSet.eligibleFlowSliceIds()),
                String.join(
                    "|",
                    items.stream()
                        .map(RepositoryInterpretationRegistryItem::provisionalKey)
                        .toList()),
                String.join(
                    "|",
                    dispositions.stream()
                        .map(
                            RepositoryInterpretationRegistryFlowDisposition
                                ::registryFlowDispositionId)
                        .toList()))),
        businessFlows.publication(),
        taskSet.eligibleFlowSliceIds(),
        items,
        dispositions,
        accounting,
        true);
  }

  private static RepositoryInterpretationRegistryItem item(BusinessRegistryProposal proposal) {
    return new RepositoryInterpretationRegistryItem(
        provisionalKey(proposal),
        proposal.registryProposalId(),
        proposal.flowSliceId(),
        proposal.evidenceCapsuleId(),
        proposal.proposalKind(),
        proposal.normalizedLabel(),
        proposal.normalizedPurpose(),
        proposal.basisAtomIds(),
        proposal.basisGapIds(),
        proposal.sourceSeedKey());
  }

  private static String provisionalKey(BusinessRegistryProposal proposal) {
    String prefix =
        switch (proposal.proposalKind()) {
          case "BUSINESS_TERM" -> "TERM_P_";
          case "CLAIM" -> "CLAIM_P_";
          case "QUESTION" -> "QUESTION_P_";
          default -> throw failure("REGISTRY_FREEZE_INCOMPLETE");
        };
    return prefix
        + sha256(
            frame("repository-interpretation-provisional-key-v1"),
            frame(proposal.registryProposalId()),
            frame(proposal.flowSliceId()),
            frame(proposal.evidenceCapsuleId()),
            frame(proposal.proposalKind()),
            frame(proposal.normalizedLabel()),
            frame(proposal.normalizedPurpose()),
            frame(String.join("|", proposal.basisAtomIds())),
            frame(String.join("|", proposal.basisGapIds())),
            frame(proposal.sourceSeedKey() == null ? "" : proposal.sourceSeedKey()));
  }

  private static String contentId(String prefix, List<String> values) {
    byte[][] frames = new byte[values.size() + 1][];
    frames[0] = frame(prefix);
    for (int index = 0; index < values.size(); index++)
      frames[index + 1] = frame(values.get(index));
    return prefix + ":" + sha256(frames);
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

  private static String sha256(byte[]... values) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      for (byte[] value : values) digest.update(value);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static int compareUtf8(String left, String right) {
    byte[] first = left.getBytes(StandardCharsets.UTF_8);
    byte[] second = right.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(first.length, second.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(first[index]), Byte.toUnsignedInt(second[index]));
      if (comparison != 0) return comparison;
    }
    return Integer.compare(first.length, second.length);
  }

  private static RegistryFreezeException failure(String code) {
    return new RegistryFreezeException(code);
  }
}
