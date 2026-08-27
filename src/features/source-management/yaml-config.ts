import type { ConnectionYamlDocument, YamlConnection } from './types.ts';

const forbiddenKey = /(^|_)(password|passwd|token|secret|private[_-]?key)(_|$)/i;

function scalar(value: unknown): string {
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (value === null) return 'null';
  if (Array.isArray(value) || typeof value === 'object') return JSON.stringify(value);
  return JSON.stringify(String(value));
}

function cleanMap(input: Record<string, unknown>, path: string) {
  for (const [key, value] of Object.entries(input)) {
    if (forbiddenKey.test(key)) throw new Error(`${path}.${key} 不允许保存敏感凭据`);
    if (value && typeof value === 'object' && !Array.isArray(value)) cleanMap(value as Record<string, unknown>, `${path}.${key}`);
  }
  return input;
}

export function serializeConnectionYaml(document: ConnectionYamlDocument): string {
  const lines = [`schemaVersion: ${document.schemaVersion}`, `workspaceId: ${document.workspaceId}`, 'connections:'];
  for (const connection of document.connections) {
    cleanMap(connection.config, `${connection.connectionId}.config`);
    lines.push(`  - connectionId: ${connection.connectionId}`);
    lines.push(`    connectorType: ${connection.connectorType}`);
    lines.push(`    displayName: ${scalar(connection.displayName)}`);
    lines.push(`    environment: ${scalar(connection.environment)}`);
    lines.push('    config:');
    for (const [key, value] of Object.entries(connection.config)) lines.push(`      ${key}: ${scalar(value)}`);
    if (connection.credentialRef) lines.push(`    credentialRef: ${connection.credentialRef}`);
    lines.push('    readScope:');
    for (const [key, value] of Object.entries(connection.readScope)) lines.push(`      ${key}: ${scalar(value)}`);
  }
  return `${lines.join('\n')}\n`;
}

function parseScalar(raw: string): unknown {
  const value = raw.trim();
  if (!value) return '';
  if (value.startsWith('[') || value.startsWith('{') || value.startsWith('"')) {
    try { return JSON.parse(value); } catch { throw new Error(`无法解析 YAML 值：${value}`); }
  }
  if (value === 'true') return true;
  if (value === 'false') return false;
  if (value === 'null') return null;
  if (/^-?\d+(\.\d+)?$/.test(value)) return Number(value);
  return value;
}

export function parseConnectionYaml(content: string): ConnectionYamlDocument {
  let schemaVersion: number | undefined;
  let workspaceId = '';
  const connections: YamlConnection[] = [];
  let current: YamlConnection | null = null;
  let map: 'config' | 'readScope' | null = null;
  for (const [index, original] of content.replace(/\r/g, '').split('\n').entries()) {
    const line = original.replace(/\s+#.*$/, '');
    if (!line.trim()) continue;
    const top = line.match(/^([A-Za-z][\w-]*):\s*(.*)$/);
    if (top) {
      if (top[1] === 'schemaVersion') schemaVersion = Number(parseScalar(top[2]));
      else if (top[1] === 'workspaceId') workspaceId = String(parseScalar(top[2]));
      else if (top[1] !== 'connections') throw new Error(`第 ${index + 1} 行存在未知顶层字段：${top[1]}`);
      continue;
    }
    const start = line.match(/^\s{2}-\sconnectionId:\s*(.+)$/);
    if (start) {
      current = { connectionId: String(parseScalar(start[1])), connectorType: '', displayName: '', environment: '', config: {}, readScope: {} };
      connections.push(current); map = null; continue;
    }
    if (!current) throw new Error(`第 ${index + 1} 行必须位于 connection 下`);
    const nestedStart = line.match(/^\s{4}(config|readScope):\s*$/);
    if (nestedStart) { map = nestedStart[1] as 'config' | 'readScope'; continue; }
    const nested = line.match(/^\s{6}([A-Za-z][\w-]*):\s*(.*)$/);
    if (nested && map) {
      if (forbiddenKey.test(nested[1])) throw new Error(`${current.connectionId}.${map}.${nested[1]} 不允许保存敏感凭据`);
      current[map][nested[1]] = parseScalar(nested[2]); continue;
    }
    const property = line.match(/^\s{4}([A-Za-z][\w-]*):\s*(.*)$/);
    if (!property) throw new Error(`第 ${index + 1} 行格式不受支持`);
    map = null;
    const value = String(parseScalar(property[2]));
    if (property[1] === 'connectorType') current.connectorType = value;
    else if (property[1] === 'displayName') current.displayName = value;
    else if (property[1] === 'environment') current.environment = value;
    else if (property[1] === 'credentialRef') current.credentialRef = value;
    else throw new Error(`第 ${index + 1} 行存在未知连接字段：${property[1]}`);
  }
  if (schemaVersion !== 1) throw new Error('仅支持 schemaVersion: 1');
  if (!workspaceId) throw new Error('缺少 workspaceId');
  if (!connections.length) throw new Error('YAML 中没有连接配置');
  for (const connection of connections) {
    if (!connection.connectionId || !connection.connectorType || !connection.displayName || !connection.environment) throw new Error(`连接 ${connection.connectionId || '(未命名)'} 缺少必填字段`);
    cleanMap(connection.config, `${connection.connectionId}.config`);
  }
  return { schemaVersion: 1, workspaceId, connections };
}
