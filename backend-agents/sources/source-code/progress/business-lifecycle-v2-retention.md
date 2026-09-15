# Business lifecycle v2 retained results

These runtime artifacts are the fixed comparison baseline for later performance or semantic-quality work. Do not delete or overwrite them unless the user explicitly replaces this baseline.

## Fixed runs

- JDT/material run: `analysis-run:4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b`
- Reviewed Activity run: `analysis-run:6b510bbeb4abf89635a2b8cd11cc2366b6cf056f8a7604da2af0bc1c5542305e`
- Complete 24-candidate run: `analysis-run:23519d8392ab2db82e3ac781e26b5d0bf96f9347be48af1fa27fdf9e1dcea298`
- Compact consolidation run: `analysis-run:41d2a7a80c951726395d4c35603a141bf5b81c67175ed1c05daf644fefef185e`
- Published Step07 run: `analysis-run:c589a5e62f1327d1991899949a5678b267f3da04ec0eea821bb466c37c6ac035`

## User-reviewed financial sample

- File: `.workspace/jsherp-business-lifecycle-v2-20260915/journal/model-jobs/4125a702ec8489a65792e93d5d77cd1630b7933c3e34f928b93c9dba85ac3224/business-process/business-process-211dd7bed308a716191a262e57b525e93eb901ce09813c431e8166ef507f29d4/reviewed-result.json`
- SHA-256: `94932c3632d12a7234054492c73fa222c03cf19649a26f7d80be40ed8de65105`

## Canonical reviewed inputs and outputs

- `activity-explanations.jsonl`: `6b63f48276cd6b772ba7c46ece7399ac3a3aeb90f70c7d7a81efab22be698052`
- `activity-coverage.json`: `8695043b4beffa1130ee081fdfc433705dc8a501c3d5bbb78189a9b00fa71ece`
- 24 candidate `reviewed-result.json` files are retained under the complete-candidate run.
- Consolidation `reviewed-result.json`: `17b4d1d839756272a6d008690cdf55b1b75ae0b45f022b15e6a2b1a0258ec49e`
- `business-processes.md`: `9a9a0d6d72fa17b32a28b4f720e8bfca606a456123fedbfb986a1281be9c8f85`
- `repository-business-process-catalog.json`: `b0eb8abd12a7483c55c886156433bbc783a3206ecdb762eb2af04de07808c511`
- `process-coverage.json`: `90cf4119e590b5e498c12bd27c8d36955ff85bf2b2520f37eb2e9becacbf66f7`
- `source-refs.jsonl`: `320b35d01c3b876ac04c1ba303cb94357b714aa6c14d601fde195c4852792484`
- `sources.md`: `fa2257e72f46256d2d186a9d69877c45aa3d0067c3dd584e37c702038653a772`

## Recovery archive

- File: `.workspace/retained/jsherp-business-lifecycle-v2-c589a5e6.tar.gz`
- Size: approximately 45 MiB
- SHA-256: `7cd89b661a4601488504102885915efcb78804eef86671ec169eb35367b449dc`
- Contents: the complete material run, 326 reviewed Activities, both real sample REVIEWs, all 24 final candidate REVIEWs, the completed consolidation DRAFT/REVIEW pair, and all five Step07 publication artifacts plus receipts.

The archive is an ignored runtime artifact and is not committed to Git. This tracked manifest records its identity; the canonical stores and original model-job directories remain in place as the primary reusable inputs.
