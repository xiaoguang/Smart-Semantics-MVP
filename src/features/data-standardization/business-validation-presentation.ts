export type ValidationIssuePresentationInput = {
  severity: 'BLOCKER' | 'WARNING' | string;
  checkCode?: string;
  message: string;
};

export type BusinessValidationPresentation = {
  label: '需要修正' | '请确认';
  tone: 'error' | 'warning';
  message: string;
};

/**
 * Validation codes route readers to the correct editor internally. They are
 * not part of the business explanation shown on the page.
 */
export function presentValidationIssue(input: ValidationIssuePresentationInput): BusinessValidationPresentation {
  if (input.severity === 'BLOCKER') {
    return { label: '需要修正', tone: 'error', message: input.message };
  }
  return { label: '请确认', tone: 'warning', message: input.message };
}
