/**
 * Generated only by `npm run evidence:guanyijia:admit`.
 * Inputs: the two approved local frozen Guanyijia snapshots.
 * This public candidate contains redacted excerpts and relative locators only.
 */

export const generatedCandidateEvidence = {
  "schemaVersion": 1,
  "kind": "GUANYIJIA_REAL_EVIDENCE_CANDIDATE",
  "fragments": [
    {
      "evidenceRef": "mysql:jsh_system_config:minus_stock_flag",
      "sourceId": "guanyijia_mysql",
      "snapshotId": "20260813T032528Z-abb0502c7d79",
      "topic": "NEGATIVE_STOCK",
      "evidenceClass": "OBSERVED",
      "title": "负库存配置字段",
      "excerpt": "  `minus_stock_flag` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT '0' COMMENT '负库存启用标记，0未启用，1启用',",
      "locationLabel": "已保存 DDL 行",
      "locationValue": "ddl/tables/jsh_system_config.sql:L12",
      "sourceName": "MySQL 已保存快照",
      "artifactDigest": "sha256:d51ebb60e812414ab50daf8f66eb95c3a2cbf0bd118e4b7dbbff4e9d46edd783"
    },
    {
      "evidenceRef": "github:jsh_system_config:minus_stock_flag",
      "sourceId": "guanyijia_github",
      "snapshotId": "20260813032126Z-5821d0ece9b1",
      "topic": "NEGATIVE_STOCK",
      "evidenceClass": "FROZEN_RECORD",
      "title": "负库存配置记录",
      "excerpt": "  `minus_stock_flag` varchar(1) CHARACTER SET utf8 COLLATE utf8_general_ci NULL DEFAULT '0' COMMENT '负库存启用标记，0未启用，1启用',",
      "locationLabel": "冻结结构化记录行",
      "locationValue": "schema/tables/jsh_system_config.sql:L12",
      "sourceName": "GitHub 冻结结构化记录（未保留完整原文）",
      "artifactDigest": "sha256:f4a8d4368b4c3ea322d52ad0baf1b002f4b93e69c71a431f41e4dd0fade5d12c"
    },
    {
      "evidenceRef": "mysql:jsh_depot_head:debt_fields_absent",
      "sourceId": "guanyijia_mysql",
      "snapshotId": "20260813T032528Z-abb0502c7d79",
      "topic": "DEBT_FIELDS",
      "evidenceClass": "OBSERVED",
      "title": "已部署单据字段边界",
      "excerpt": "  `discount_last_money` decimal(24,6) DEFAULT NULL COMMENT '优惠后金额',\n  `other_money` decimal(24,6) DEFAULT NULL COMMENT '销售或采购费用合计',\n  `deposit` decimal(24,6) DEFAULT NULL COMMENT '订金',\n  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，0未审核、1已审核、2完成采购|销售、3部分采购|销售、9审核中',",
      "locationLabel": "已保存 DDL 行",
      "locationValue": "ddl/tables/jsh_depot_head.sql:L24-L27",
      "sourceName": "MySQL 已保存快照",
      "artifactDigest": "sha256:ac7db8eb49c09c67cf9cd650da7cb4c6b3896ef8650cca801f9cdc406045ecde"
    },
    {
      "evidenceRef": "github:migration:jsh_depot_head:debt_fields",
      "sourceId": "guanyijia_github",
      "snapshotId": "20260813032126Z-5821d0ece9b1",
      "topic": "DEBT_FIELDS",
      "evidenceClass": "FROZEN_RECORD",
      "title": "欠款字段迁移记录",
      "excerpt": "alter table jsh_depot_head add debt decimal(24,6) DEFAULT NULL COMMENT '本次欠款' after deposit;\nalter table jsh_depot_head add last_debt decimal(24,6) DEFAULT NULL COMMENT '最终欠款' after debt;",
      "locationLabel": "冻结迁移记录行",
      "locationValue": "migration-history/数据库更新记录:L1842-L1843",
      "sourceName": "GitHub 冻结结构化记录（未保留完整原文）",
      "artifactDigest": "sha256:d2e93166b0935166b86661061fa902324c0dbaa728b73eb26ddb677d022a4754"
    },
    {
      "evidenceRef": "mysql:jsh_depot_head:document_status",
      "sourceId": "guanyijia_mysql",
      "snapshotId": "20260813T032528Z-abb0502c7d79",
      "topic": "DOCUMENT_STATUS",
      "evidenceClass": "OBSERVED",
      "title": "已部署单据状态字段",
      "excerpt": "  `status` varchar(1) CHARACTER SET utf8mb3 COLLATE utf8mb3_general_ci DEFAULT NULL COMMENT '状态，0未审核、1已审核、2完成采购|销售、3部分采购|销售、9审核中',",
      "locationLabel": "已保存 DDL 行",
      "locationValue": "ddl/tables/jsh_depot_head.sql:L27",
      "sourceName": "MySQL 已保存快照",
      "artifactDigest": "sha256:ac7db8eb49c09c67cf9cd650da7cb4c6b3896ef8650cca801f9cdc406045ecde"
    },
    {
      "evidenceRef": "github:migration:jsh_depot_head:historical_status",
      "sourceId": "guanyijia_github",
      "snapshotId": "20260813032126Z-5821d0ece9b1",
      "topic": "DOCUMENT_STATUS",
      "evidenceClass": "FROZEN_RECORD",
      "title": "历史单据状态迁移记录",
      "excerpt": "alter table jsh_depot_head change Status Status varchar(1) DEFAULT '0' COMMENT '状态，0未审核、1已审核、2已转采购|销售';",
      "locationLabel": "冻结迁移记录行",
      "locationValue": "migration-history/数据库更新记录:L312",
      "sourceName": "GitHub 冻结结构化记录（未保留完整原文）",
      "artifactDigest": "sha256:d2e93166b0935166b86661061fa902324c0dbaa728b73eb26ddb677d022a4754"
    },
    {
      "evidenceRef": "gap:official:document_status_9",
      "sourceId": "guanyijia_official_docs",
      "snapshotId": "gyjerp-official-docs-20260813T031656Z",
      "topic": "DOCUMENT_STATUS",
      "evidenceClass": "GAP",
      "title": "状态 9 的官方资料缺口",
      "excerpt": "完整官方文本未被保留；不以任何引文补全状态 9 的官方含义。",
      "locationLabel": "保留状态",
      "locationValue": "完整官方文本未保留",
      "sourceName": "官方资料",
      "artifactDigest": "sha256:af5bfb71c71afffbf24192e63fe512696e547db8d19bd65b0655a0baffc28738"
    }
  ],
  "claims": [
    {
      "claimId": "mysql-negative-stock-control",
      "topic": "NEGATIVE_STOCK",
      "subjectRef": "guanyijia:jsh_system_config.minus_stock_flag",
      "predicate": "TENANT_CONFIG_CONTROLS",
      "normalizedValue": "minus_stock_flag",
      "scope": "guanyijia:system-config-schema",
      "effectiveTime": "2026-08-13",
      "evidenceClass": "OBSERVED",
      "evidenceRefs": [
        "mysql:jsh_system_config:minus_stock_flag"
      ]
    },
    {
      "claimId": "github-negative-stock-control",
      "topic": "NEGATIVE_STOCK",
      "subjectRef": "guanyijia:jsh_system_config.minus_stock_flag",
      "predicate": "TENANT_CONFIG_CONTROLS",
      "normalizedValue": "minus_stock_flag",
      "scope": "guanyijia:system-config-schema",
      "effectiveTime": "2026-08-13",
      "evidenceClass": "FROZEN_RECORD",
      "evidenceRefs": [
        "github:jsh_system_config:minus_stock_flag"
      ]
    },
    {
      "claimId": "mysql-debt-fields-absent",
      "topic": "DEBT_FIELDS",
      "subjectRef": "guanyijia:jsh_depot_head",
      "predicate": "DEBT_FIELDS",
      "normalizedValue": "absent",
      "scope": "guanyijia:deployed-schema",
      "effectiveTime": "2026-08-13",
      "evidenceClass": "OBSERVED",
      "evidenceRefs": [
        "mysql:jsh_depot_head:debt_fields_absent"
      ]
    },
    {
      "claimId": "github-debt-fields-present",
      "topic": "DEBT_FIELDS",
      "subjectRef": "guanyijia:jsh_depot_head",
      "predicate": "DEBT_FIELDS",
      "normalizedValue": "debt,last_debt",
      "scope": "guanyijia:deployed-schema",
      "effectiveTime": "2026-08-13",
      "evidenceClass": "FROZEN_RECORD",
      "evidenceRefs": [
        "github:migration:jsh_depot_head:debt_fields"
      ]
    },
    {
      "claimId": "mysql-document-status-current",
      "topic": "DOCUMENT_STATUS",
      "subjectRef": "guanyijia:jsh_depot_head.status",
      "predicate": "DOCUMENT_STATUS_CODES",
      "normalizedValue": "0,1,2,3,9",
      "scope": "guanyijia:deployed-schema",
      "effectiveTime": "2026-08-13T03:25:28Z",
      "evidenceClass": "OBSERVED",
      "evidenceRefs": [
        "mysql:jsh_depot_head:document_status"
      ]
    },
    {
      "claimId": "github-document-status-historical",
      "topic": "DOCUMENT_STATUS",
      "subjectRef": "guanyijia:jsh_depot_head.status",
      "predicate": "DOCUMENT_STATUS_CODES",
      "normalizedValue": "0,1,2",
      "scope": "guanyijia:deployed-schema",
      "effectiveTime": "2026-08-13T03:21:26Z",
      "evidenceClass": "FROZEN_RECORD",
      "evidenceRefs": [
        "github:migration:jsh_depot_head:historical_status"
      ]
    },
    {
      "claimId": "official-document-status-9-gap",
      "topic": "DOCUMENT_STATUS",
      "subjectRef": "guanyijia:jsh_depot_head.status",
      "predicate": "OFFICIAL_STATUS_9_MEANING",
      "normalizedValue": "unknown",
      "scope": "official-documentation",
      "evidenceClass": "GAP",
      "evidenceRefs": [
        "gap:official:document_status_9"
      ]
    }
  ],
  "relations": [
    {
      "leftClaimId": "mysql-negative-stock-control",
      "rightClaimId": "github-negative-stock-control",
      "relation": "COMPLEMENTS",
      "explanation": "已部署 DDL 与冻结结构化记录都涉及 minus_stock_flag；后者不是第二份独立观察，只补充待审阅的来源上下文。"
    },
    {
      "leftClaimId": "mysql-debt-fields-absent",
      "rightClaimId": "github-debt-fields-present",
      "relation": "CONFLICTS",
      "explanation": "已部署 DDL 的字段边界未见 debt 或 last_debt，而冻结迁移记录声明了这两个字段。"
    },
    {
      "leftClaimId": "mysql-document-status-current",
      "rightClaimId": "github-document-status-historical",
      "relation": "TEMPORAL_DRIFT",
      "explanation": "已部署快照记录 0/1/2/3/9；冻结历史迁移记录只描述 0/1/2，两个保留快照时刻不同。"
    }
  ]
} as const;

export const generatedCandidateEvidenceIntegrity = {
  "schemaVersion": 1,
  "mysqlSnapshotId": "20260813T032528Z-abb0502c7d79",
  "githubSnapshotId": "20260813032126Z-5821d0ece9b1",
  "fragments": [
    {
      "evidenceRef": "mysql:jsh_system_config:minus_stock_flag",
      "artifactDigest": "sha256:d51ebb60e812414ab50daf8f66eb95c3a2cbf0bd118e4b7dbbff4e9d46edd783",
      "excerptDigest": "sha256:4e01ab1664fc539c30e9de3f67ce39fbc7cb164f21a34d18d80cb21d0481d0ed",
      "locationValue": "ddl/tables/jsh_system_config.sql:L12"
    },
    {
      "evidenceRef": "github:jsh_system_config:minus_stock_flag",
      "artifactDigest": "sha256:f4a8d4368b4c3ea322d52ad0baf1b002f4b93e69c71a431f41e4dd0fade5d12c",
      "excerptDigest": "sha256:ea342527568e0c2134666fa9aeca6e22a420a87973cca33699233f0f7f1518af",
      "locationValue": "schema/tables/jsh_system_config.sql:L12"
    },
    {
      "evidenceRef": "mysql:jsh_depot_head:debt_fields_absent",
      "artifactDigest": "sha256:ac7db8eb49c09c67cf9cd650da7cb4c6b3896ef8650cca801f9cdc406045ecde",
      "excerptDigest": "sha256:36382d04e946b4bb2963603bc09bcd297fe7b7419b6fd135482952352122269a",
      "locationValue": "ddl/tables/jsh_depot_head.sql:L24-L27"
    },
    {
      "evidenceRef": "github:migration:jsh_depot_head:debt_fields",
      "artifactDigest": "sha256:d2e93166b0935166b86661061fa902324c0dbaa728b73eb26ddb677d022a4754",
      "excerptDigest": "sha256:2935e274361003f4fda71610516a640eb457fb8ccea32bfd590ecbdcf505b3b5",
      "locationValue": "migration-history/数据库更新记录:L1842-L1843"
    },
    {
      "evidenceRef": "mysql:jsh_depot_head:document_status",
      "artifactDigest": "sha256:ac7db8eb49c09c67cf9cd650da7cb4c6b3896ef8650cca801f9cdc406045ecde",
      "excerptDigest": "sha256:7074029c34286d0ea2b9bca99ff3e42978643d567e92abf6709d4e26214a8cd6",
      "locationValue": "ddl/tables/jsh_depot_head.sql:L27"
    },
    {
      "evidenceRef": "github:migration:jsh_depot_head:historical_status",
      "artifactDigest": "sha256:d2e93166b0935166b86661061fa902324c0dbaa728b73eb26ddb677d022a4754",
      "excerptDigest": "sha256:d66ea0d597ec7571c561944bedb6104f777d00da13a1f71397a9aa469119cd1f",
      "locationValue": "migration-history/数据库更新记录:L312"
    },
    {
      "evidenceRef": "gap:official:document_status_9",
      "artifactDigest": "sha256:af5bfb71c71afffbf24192e63fe512696e547db8d19bd65b0655a0baffc28738",
      "excerptDigest": "sha256:5c16a2de7f2c887d0f2974269d7e4efc2d3fc896161a778e4b0f51d4f6b634e6",
      "locationValue": "完整官方文本未保留"
    }
  ]
} as const;
