import type { SemanticFixture } from './types.ts';
import { omnichannelDocument } from './omnichannel-model-fixture.ts';
export { omnichannelSourceAssets, omnichannelSourcePack, omnichannelEvidenceLocators, createOmnichannelFindings } from './omnichannel-source-fixture.ts';

export const omnichannelScenarioFixture: SemanticFixture = {
  schemaVersion: 1,
  system: { code: 'omnichannel_retail_ops', name: '全渠道零售经营语义模型' },
  documents: [omnichannelDocument],
};
