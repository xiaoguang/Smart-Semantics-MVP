/**
 * A display-only projection for saved MySQL schema evidence.
 *
 * The raw DDL is deliberately kept outside the structured representation: a
 * consumer can render fields responsively without accidentally turning a DDL
 * dump into the default reading experience.
 */
export type MysqlSchemaEvidenceColumn = {
  name: string;
  dataType: string;
  nullable: boolean;
  defaultValue?: string;
  comment?: string;
  keyRole?: 'PRIMARY' | 'INDEX' | 'FOREIGN';
  highlighted: boolean;
};

export type MysqlSchemaEvidence = {
  objectName: string;
  objectComment?: string;
  columns: readonly MysqlSchemaEvidenceColumn[];
  rawDdl: string;
  locationValue: string;
};

export type MysqlSchemaEvidenceLayout = 'WIDE' | 'COMPACT' | 'NARROW';

export type MysqlSchemaEvidenceProjection = {
  layout: MysqlSchemaEvidenceLayout;
  structured: {
    columns?: readonly { key: string; label: string }[];
    rows: readonly Record<string, unknown>[];
  };
  rawDdl: {
    text: string;
    location: string;
  };
};

const WIDE_COLUMNS = [
  { key: 'field', label: '字段' },
  { key: 'type', label: '类型' },
  { key: 'nullable', label: '可空' },
  { key: 'defaultValue', label: '默认值' },
  { key: 'comment', label: '说明' },
  { key: 'keyRole', label: '键' },
] as const;

const COMPACT_COLUMNS = [
  { key: 'field', label: '字段' },
  { key: 'type', label: '类型' },
  { key: 'constraints', label: '约束' },
  { key: 'comment', label: '说明' },
] as const;

function layoutFor(containerWidth: number): MysqlSchemaEvidenceLayout {
  if (containerWidth >= 960) {
    return 'WIDE';
  }
  if (containerWidth >= 640) {
    return 'COMPACT';
  }
  return 'NARROW';
}

function keyRoleLabel(keyRole: MysqlSchemaEvidenceColumn['keyRole']): string | undefined {
  switch (keyRole) {
    case 'PRIMARY':
      return '主键';
    case 'INDEX':
      return '索引';
    case 'FOREIGN':
      return '外键';
    default:
      return undefined;
  }
}

function isAutoIncrement(column: MysqlSchemaEvidenceColumn): boolean {
  return /AUTO_INCREMENT/iu.test(column.defaultValue ?? '');
}

function defaultValue(column: MysqlSchemaEvidenceColumn): string | undefined {
  const value = column.defaultValue?.trim();
  return value && !isAutoIncrement(column) ? value : undefined;
}

function constraintsFor(column: MysqlSchemaEvidenceColumn): string[] {
  const constraints = [column.nullable ? '可空' : '不可为空'];
  if (isAutoIncrement(column)) {
    constraints.push('自增');
  }
  const defaultText = defaultValue(column);
  if (defaultText) {
    constraints.push(`默认 ${defaultText}`);
  }
  const keyText = keyRoleLabel(column.keyRole);
  if (keyText) {
    constraints.push(keyText);
  }
  return constraints;
}

function wideRow(column: MysqlSchemaEvidenceColumn): Record<string, unknown> {
  const row: Record<string, unknown> = {
    field: column.name,
    type: column.dataType,
    nullable: column.nullable ? '是' : '否',
  };
  const value = defaultValue(column);
  const comment = column.comment?.trim();
  const keyRole = keyRoleLabel(column.keyRole);
  if (value) {
    row.defaultValue = value;
  }
  if (comment) {
    row.comment = comment;
  }
  if (keyRole) {
    row.keyRole = keyRole;
  }
  return row;
}

function compactRow(column: MysqlSchemaEvidenceColumn): Record<string, unknown> {
  const row: Record<string, unknown> = {
    field: column.name,
    type: column.dataType,
    constraints: constraintsFor(column),
  };
  const comment = column.comment?.trim();
  if (comment) {
    row.comment = comment;
  }
  return row;
}

function narrowRow(column: MysqlSchemaEvidenceColumn): Record<string, unknown> {
  const row: Record<string, unknown> = {
    field: column.name,
    type: column.dataType,
  };
  const comment = column.comment?.trim();
  if (comment) {
    row.description = comment;
  }
  const constraints = constraintsFor(column);
  if (constraints.length > 0) {
    row.constraints = constraints;
  }
  return row;
}

/**
 * Selects the structural representation using the component's available
 * width, rather than a browser viewport breakpoint.
 */
export function projectMysqlSchemaEvidence(
  evidence: MysqlSchemaEvidence,
  input: { containerWidth: number },
): MysqlSchemaEvidenceProjection {
  const layout = layoutFor(input.containerWidth);
  const rows = evidence.columns.map((column) => {
    if (layout === 'WIDE') {
      return wideRow(column);
    }
    if (layout === 'COMPACT') {
      return compactRow(column);
    }
    return narrowRow(column);
  });

  return {
    layout,
    structured: {
      ...(layout === 'WIDE' ? { columns: WIDE_COLUMNS } : {}),
      ...(layout === 'COMPACT' ? { columns: COMPACT_COLUMNS } : {}),
      rows,
    },
    rawDdl: {
      text: evidence.rawDdl,
      location: evidence.locationValue,
    },
  };
}
