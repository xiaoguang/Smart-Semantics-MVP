import type { SemanticFixture } from './types.ts';
import { groupRetailDocument } from './group-retail-model-fixture.ts';

export {
  createGroupRetailFindings, groupRetailBundleFingerprint, groupRetailEvidenceClaims,
  groupRetailEvidenceLocators, groupRetailSourceAssets,
} from './group-retail-source-fixture.ts';
export { groupRetailConnectorCatalog } from './group-retail-connectors.ts';

export const groupRetailScenarioFixture: SemanticFixture = {
  schemaVersion: 1,
  system: { code: 'group_retail_ops', name: '集团零售经营语义模型' },
  documents: [groupRetailDocument],
};
