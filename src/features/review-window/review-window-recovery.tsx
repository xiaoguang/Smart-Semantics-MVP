import { projectReviewWindowError, type ReviewWindowError } from './index.ts';

export function ReviewWindowRecovery({
  error,
  onRecover,
}: {
  error: ReviewWindowError;
  onRecover?(): void;
}) {
  const model = projectReviewWindowError(error);
  return <section
    className="review-window-recovery"
    role="alert"
    data-error-code={model.code}
    data-blocks-mutation={String(model.blocksMutation)}
  >
    <strong>{model.title}</strong>
    <p>{model.detail}</p>
    {model.technicalDetail && <code>{model.technicalDetail}</code>}
    {model.action && <button type="button" onClick={onRecover}>{model.action.label}</button>}
  </section>;
}
