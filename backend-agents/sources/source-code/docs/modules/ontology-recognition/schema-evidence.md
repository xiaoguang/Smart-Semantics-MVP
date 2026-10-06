# 可选 schema-evidence：首版字段与消费边界

状态：第8步的有限实现已通过21项正式运行直接测试，包含保存、重开、类型化消费及来源反查；最终同状态质量/旧路径回归仍待完成。没有要求固定客户dump解析成功，也没有实际DDL模型样例。依据本模块 material-preparation.md §8 和 contracts.md §11.3。 The installed JSqlParser 5.3 is the only parser; same-R0 verified source is the only text source. No model, database, checkout, semicolon splitting, dialect repair, new entry or interpretation engine.

## Boundaries

- O0 only reads explicitly configured schemaSources. Disabled is an explicit corpus field with the one-file set, zero parser calls, unchanged content-source/alias/packet identity. The formal O0 artifact's bytes/ID may change as already ruled; historical bytes do not.
- A configured file must first be a verified text member of the actual admitted R0/exclusion basis. Absent/excluded/nontext input is not substituted or parsed. Existing input-error behavior applies, with no imagined public receipt.
- Parse the complete original file once via the existing JSqlParser parseStatements. No partial statement salvage. Complete supported CREATE TABLE-only files may become complete source units; mixed INSERT/other statements and whole parse failures remain queryable reports but not model-readable schema units. Optional unsupported DDL does not block otherwise valid tasks.
- Associate a supported file with real R4 entries only by exact literal TABLE values from actual SQL AST observations. No case/quote/schema normalization, table resolver, synthetic HTTP entry or business-object inference. Status is always TABLE_MATCH_CANDIDATE, retaining actual SQL status and qualification/duplicate ambiguity. Unmatched files have no U/E source use.
- O1/O2 validate the actual saved O0/schema payload, same-R0 original bytes and aliases; never parse again or use the current YAML's schemaSources to reconstruct the upstream. Use the existing Corpus, U/E/S packet and source-index seams.

## Public schema-evidence-v1 body

The existing standalone canonical envelope adds artifactId/artifactType/schemaVersion. The body records exact admitted evidence/source references and parser identity; `files[]` sorted by relative path. Per-file required values:

| Field | Meaning |
| --- | --- |
| path / fileId / sha256 / byteLength / range | Actual same-R0 relative original file identity and full-file range; no host path or invented column range |
| parseStatus | PARSED for complete supported CREATE-only parse; UNSUPPORTED for parsed but unsupported/mixed statements; UNKNOWN for failed whole-file parsing |
| structuralCoverage | COMPLETE only for supported projected declarations; otherwise UNKNOWN, not a table count guessed from an error line |
| declarations | Actual supported AST fields, or empty when complete supported projection cannot be made |
| diagnostics | Tool diagnostic data; never instructions or semantic facts |
| limitations | Explicit qualification, full-file-only position and unsupported scope conditions |
| entryUses | Actual entryId, matchedTableValue, SQL unit/observation identity, SQL status and TABLE_MATCH_CANDIDATE; none for an unmatchable file |
| modelEligible | True only for a complete CREATE-only source with at least one real entry use and no unsupported projection; size remains checked by existing complete-unit gates |

Each table declaration has tableName, columns, constraints and evidenceNature=DDL_DECLARED. A column carries name, actual type text and declaredNullability=NOT_NULL/NULLABLE/UNKNOWN. A constraint carries kind=PRIMARY_KEY/UNIQUE/FOREIGN_KEY/INDEX, actual name if supplied or null, actual columns, and referencedTable/referencedColumns only for a real ForeignKeyIndex; missing fields stay unknown. Ordinary INDEX is not a foreign key. No runtime-enforcement, entity identity or implied default nullability claim.

Declared nullability must come from the actual column declaration tokens, not words inside a quoted DEFAULT value. Datatype arguments such as VARCHAR(64) remain part of the observed type. Inline PRIMARY KEY/UNIQUE must either survive the existing AST projection or make the file explicitly unsupported; omitted constraints cannot coexist with a COMPLETE declaration-coverage claim.

Parser identity is actual library name/version plus finite projection-rule version. It is not a new parser registry. Metadata reflects observations, not a claim to understand all SQL dialects.

## Corpus and reversibility

Add only one schema-file unit kind to the existing Corpus. Its original ID derives deterministically from the actual saved R0 snapshot/path/SHA and projection version. The full original text is stored once as sourceText with structural declarations, DDL_DECLARED and candidate entry uses; aliases U/E and reading references S use the existing mapping. Disabled preparation adds no empty units. Mapping is deterministic under configuration/input order changes.

One file may match several SQL observations and several declared tables in one entry. Preserve every distinct (entryId, SQL observation identity, matchedTableValue) candidate in entryUses, but contribute exactly one complete-file U/E use for that entry. Neither a second matching query nor a JOIN is a duplicate source error. Content identity uses the same semantic schema payload before and after installation; canonical transport artifactId/artifactType are validated through the actual descriptor/reference, not added only during reopening to change that identity.

Model projection omits long private IDs and receipts, but keeps complete original text, projected structural observations, candidate nature, SQL status and limitations. The private unit/packet mapping retains actual R0 binding. Source-index rows for such selected sources additionally retain that physical source binding, so S→U/E→original same-R0 full file can be reversed. No AST rendering is labeled original source.

## Installed seam and direct tests

Align `SCHEMA_EVIDENCE` with schema-evidence.json, its explicit policy and exact two-file O0 module/step dispatch. Corpus binds the real schema ArtifactReference, not only a filename; downstream validates it without requiring the temporary client workspace. O0's actual installed payload is the authority for source/alias admission.

Finite tests: disabled zero parser/one file and stable E/U/K/packet; supported neutral CREATE-only file plus actual saved SQL TABLE reaches a complete formal request and reversible source index; two-file install/reopen with no second parse; key/index/nullability distinctions; whole parse failure, mixed INSERT, no literal table match, excluded source and oversized complete unit. No test requires the fixed customer dump to parse. No real model or customer tool call.
