# Gate A persistence-tool feasibility progress

## Scope

Own only the independent `research/persistence-tool-feasibility/` harness and this
progress record for approved Gate A. It is not a production persistence plugin, a
store/CLI integration, a JDT runner, a Provider, or a customer build runner.

## Plan

1. Create an independent Java 17 Maven harness with fixed MyBatis 3.5.19 and
   JSqlParser 5.3 dependencies.
2. Receive an independently written failing focused test before creating
   `src/main`.
3. Implement a safe frozen-XML reader that preserves originals, uses the
   official MyBatis XPath/XNode and work-copy include components, projects
   dynamic DOM structure without OGNL evaluation, and uses JSqlParser only for
   bounded AST projections.
4. Run the focused Gate A test serially, inspect a fixed immutable snapshot
   through a small driver, and record the measured limits in the research
   README.

## Recorded evidence

- POM created first at `research/persistence-tool-feasibility/pom.xml` with
  Java 17, MyBatis `3.5.19`, JSqlParser `5.3`, Jackson `2.21.4`, and JUnit
  Jupiter `5.13.4`.
- The local Maven cache initially contained MyBatis, Jackson, and JUnit but not
  JSqlParser 5.3. The serialized RED execution resolved it; its jar SHA-256 is
  `41bcb5b00488231db179cb5a375690830a59aba521dfa303daa94dcb9dcc8e88`.
- MyBatis 3.5.19 public APIs confirmed locally: `XPathParser(Document, boolean,
  Properties, EntityResolver)`, `XNode`, and
  `XMLIncludeTransformer(Configuration, MapperBuilderAssistant)`.
- RED confirmed by the main coordinator: `mvn -B -ntp -f
  research/persistence-tool-feasibility/pom.xml -Dtest=PersistenceToolProbeTest
  test` ran seven tests with seven expected missing-probe failures and zero
  errors. Main implementation is now authorized.

## Current state

Gate A is complete and passed. The 14 focused tests are GREEN (0 failures, 0
errors; 4.184 seconds in the final recorded run). The fixed-snapshot driver
verified 65 analyzable XML blobs, recognized 61 Mapper resources, and returned
573 statements, 572 candidate namespace/id bindings, and 573 SQL analyses in
3.879 seconds. The analysis status split was 112 `PARSED`, 237 `PARTIAL`, and
224 `UNSUPPORTED`; incomplete dynamic/dialect cases retain source plus a
limitation rather than claiming executable SQL.

The immutable result is
`.workspace/persistence-tool-feasibility-20260917/snapshot-probe-result.json`
with SHA-256
`869c35a21ee1826c391648b7aea4492801165f842e91b0988807042e46278905`.
Its `getFinishNumber` projection retains all three conditional tests and the
real `DepotItemMapper.insertSelective` projection retains 52 dynamic DOM
conditions. The complete Java METHOD index intentionally generated unmatched
declaration diagnostics; the next production module must consume an explicit
Mapper catalog and must not turn that observation into a broad new parser.

No production files, customer POMs, JDT runs, runtime OGNL, `getBoundSql`,
customer class loading, database access, Provider use, or external XML access
occurred in Gate A.
