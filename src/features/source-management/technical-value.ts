export type ClipboardWriter = { writeText(value: string): Promise<void> };

export type PresentedTechnicalValue = {
  /** A compact, human-facing description suitable for business pages. */
  display: string;
  /** The original value, available only when the user explicitly copies it. */
  copy: string;
};

function copyValue(value: unknown) {
  if (typeof value === 'string') return value;
  if (Array.isArray(value) && value.every((item) => typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean')) {
    return value.join('、');
  }
  if (value && typeof value === 'object') return JSON.stringify(value, null, 2);
  return String(value ?? '—');
}

/**
 * Keeps configuration detail useful without rendering raw object payloads in
 * the normal reading flow. A reviewer can still explicitly copy the precise
 * saved value when they need it for configuration work.
 */
export function presentTechnicalValue(value: unknown): PresentedTechnicalValue {
  const copy = copyValue(value);
  if (Array.isArray(value)) {
    return {
      display: value.every((item) => typeof item === 'string' || typeof item === 'number' || typeof item === 'boolean')
        ? copy
        : `已配置（${value.length} 项）`,
      copy,
    };
  }
  if (value && typeof value === 'object') return { display: '已配置', copy };
  return { display: copy, copy };
}

export async function copyTechnicalText(text: string, clipboard?: ClipboardWriter): Promise<boolean> {
  if (!clipboard) return false;
  try {
    await clipboard.writeText(text);
    return true;
  } catch {
    return false;
  }
}
