import rawFixture from './fixtures/semantic-workbench-seed.json' with { type: 'json' };
import type { SemanticFixture } from './types.ts';

// The generated fixture is validated by scripts/generate-ai-modeling-fixtures.mjs.
// Keep the unchecked JSON conversion at this single import boundary.
export const semanticFixture = rawFixture as unknown as SemanticFixture;

export function findFixtureDocument(version: SemanticFixture['documents'][number]['documentVersion']) {
  const document = semanticFixture.documents.find((item) => item.documentVersion === version);
  if (!document) throw new Error(`资料版本不存在：${version}`);
  return document;
}
