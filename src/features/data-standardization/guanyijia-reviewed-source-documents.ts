import { projectSourceStandardDocumentRevision, readSourceStandardDocument } from '../guanyijia-evidence-factory/source-review-document.ts';
import type { DemoContentRunBinding } from '../guanyijia-demo-content/demo-content-review.ts';
import { projectReviewedSourceDocument } from '../standardization-deliverable/reviewed-source-documents.ts';
import type { ReviewedSourceDocumentReader, SourceDocumentReader } from '../standardization-deliverable/types.ts';
import type { StandardizationRun } from '../standardization-run/types.ts';

/**
 * Reads the immutable V6 human documents bound to a particular run and
 * applies only the saved structured-review revision. This seam is purposely
 * read-only: it never reaches a live source and never regenerates V6 prose.
 */
export function createGuanyijiaReviewedSourceDocumentReader(input: {
  sourceDocuments: Pick<SourceDocumentReader, 'read'> & Required<Pick<SourceDocumentReader, 'readBlocks'>>;
  contentBindingForRun(run: StandardizationRun): DemoContentRunBinding;
}): ReviewedSourceDocumentReader {
  return {
    async readForRun(run) {
      const contentBinding = input.contentBindingForRun(run);
      if (contentBinding.runId !== run.runId) {
        throw new Error('完整审阅来源文档绑定的运行身份不一致');
      }
      return Promise.all(run.sources.map(async (source, index) => {
        if (!source.snapshotId || !source.documentId || !source.documentRevision) {
          throw new Error('完整审阅来源文档缺少来源快照或文档 Revision');
        }
        const binding = contentBinding.sourceBindings.find((candidate) => (
          candidate.sourceId === source.sourceId && candidate.formalSnapshotId === source.snapshotId
        ));
        if (!binding) throw new Error('完整审阅来源文档没有绑定冻结内容快照');
        const original = readSourceStandardDocument({
          sourceId: source.sourceId,
          snapshotId: source.snapshotId,
          contentBinding,
        });
        if (!original) throw new Error('完整审阅来源文档不可读取或不是固定九章文档');
        const stored = await input.sourceDocuments.read(source.documentId);
        if (!stored
          || stored.documentId !== source.documentId
          || stored.revision !== source.documentRevision
          || stored.sourceSnapshotId !== source.snapshotId
          || stored.sourceName !== source.sourceName) {
          throw new Error('完整审阅来源文档与保存的 Revision 不一致');
        }
        const reviewed = projectSourceStandardDocumentRevision(
          original,
          await input.sourceDocuments.readBlocks(source.documentId),
        );
        return projectReviewedSourceDocument({
          sourceId: source.sourceId,
          sourceName: source.sourceName,
          order: index + 1,
          contentSnapshotId: binding.contentSourceSnapshotId,
          documentId: stored.documentId,
          documentRevision: stored.revision,
          original,
          reviewed,
        });
      }));
    },
  };
}
