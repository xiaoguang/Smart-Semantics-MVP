import type { ModelingDocumentArtifact, ModelingDocumentRuntime } from '../modeling-document-bridge/types.ts';
import type { DocumentAlignmentSession } from './types.ts';

export function projectAlignmentDecisionsToModelingDocument(session: DocumentAlignmentSession) {
  return session.issues.flatMap((issue) => {
    if (!issue.decision) return [];
    const selected = session.claims.find((claim) => claim.claimId === issue.decision!.selectedClaimId);
    if (!selected) return [];
    if (issue.topicRef === 'metric:net_sales:refund_timing') return [{
      issueId: 'finding_net_sales_refund',
      resolutionId: selected.normalizedValue === 'REFUND_COMPLETED' ? 'adopt_completed_refund' : 'adopt_return_started',
      reason: issue.decision.reason,
    }];
    if (issue.topicRef === 'metric:search_conversion:traffic_filter') return [{
      issueId: 'finding_bot_filter',
      resolutionId: selected.normalizedValue === 'EXCLUDE_BOT_AND_INTERNAL_TEST' ? 'exclude_bot_and_internal_test' : 'exclude_bot_only',
      reason: issue.decision.reason,
    }];
    if (issue.topicRef === 'time:business_day:assignment') return [{
      issueId: 'finding_business_day',
      resolutionId: selected.normalizedValue === 'STORE_BUSINESS_DAY' ? 'adopt_store_business_day' : 'adopt_calendar_day',
      reason: issue.decision.reason,
    }];
    if (issue.topicRef === 'field:guanyijia:debt_schema') return [{
      issueId: 'finding_guanyijia_debt_fields', resolutionId: 'keep_debt_metric_blocked', reason: issue.decision.reason,
    }];
    return [];
  });
}

export async function prepareModelingArtifactForDeliverable(input: {
  runtime: ModelingDocumentRuntime;
  artifact: ModelingDocumentArtifact;
  alignment: DocumentAlignmentSession;
  actorUserId: string;
}) {
  if (input.artifact.projectId === 'guanyijia_erp' && input.artifact.status === 'FROZEN') {
    const debtIssue = input.alignment.issues.find((issue) => issue.topicRef === 'field:guanyijia:debt_schema');
    const selected = debtIssue?.decision
      ? input.alignment.claims.find((claim) => claim.claimId === debtIssue.decision!.selectedClaimId) : undefined;
    if (selected && selected.normalizedValue !== 'DEPLOYED_SCHEMA_NO_DEBT') {
      throw new Error('管伊佳正式V1是黄金快照；采用源码字段必须先形成未来草稿并补齐部署证据');
    }
  }
  if (input.artifact.status === 'FROZEN' || input.artifact.delta?.kind === 'DECISION') return input.artifact;
  const decisions = projectAlignmentDecisionsToModelingDocument(input.alignment);
  const openBlocking = input.alignment.issues.filter((issue) => issue.severity === 'BLOCKER' && issue.status === 'OPEN');
  if (openBlocking.length) throw new Error('仍有未解决的阻断问题');
  if (!decisions.length) return input.artifact;
  return (await input.runtime.execute({
    type: 'RECORD_DECISIONS', artifactId: input.artifact.artifactId, expectedRevision: input.artifact.revision,
    actorUserId: input.actorUserId, decisions,
  })).artifact;
}
