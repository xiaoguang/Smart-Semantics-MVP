import type { ObjectAttribute } from '../../types';
import type { SemanticFieldRole } from '../../components/semantic-object-visuals.ts';

export type AttributePresentation = {
  name: string;
  code: string;
  fieldRole: SemanticFieldRole;
  usageLabels: string[];
};

export function projectAttributePresentation(
  attribute: ObjectAttribute,
  participatesInAttribution: boolean,
): AttributePresentation {
  return {
    name: attribute.attributeName,
    code: attribute.attributeCode,
    fieldRole: attribute.isMetric ? 'MEASURE' : attribute.isGroupable ? 'DIMENSION' : 'PLAIN',
    usageLabels: [
      ...(attribute.isFilterable ? ['可筛选'] : []),
      ...(participatesInAttribution ? ['可归因'] : []),
    ],
  };
}
