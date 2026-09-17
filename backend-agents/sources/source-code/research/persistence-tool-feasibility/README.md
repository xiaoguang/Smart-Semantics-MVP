# Persistence-tool feasibility: Gate A

This is the isolated, throwaway Gate A harness for assessing whether frozen
MyBatis Mapper XML can add reading material to an already saved Java index. It
is not part of the Source Code Analysis Agent reactor and does not implement a
production plugin, store, Step 05 material writer, Provider, JDT invocation,
customer build, database access, OGNL evaluation, `getBoundSql`, or customer
class loading.

## Pinned runtime

The Java 17 harness pins MyBatis `3.5.19`, JSqlParser `5.3`, Jackson `2.21.4`,
JUnit Jupiter `5.13.4`, and `exec-maven-plugin` `3.6.3`. The locally resolved
library jar SHA-256 values are:

| Artifact | SHA-256 | License declared by its POM |
| --- | --- | --- |
| `org.mybatis:mybatis:3.5.19` | `93eea616ae355751bd5fbabb57f0732713fbe79f3196f33c51a0aeeb4255862a` | Apache-2.0 |
| `com.github.jsqlparser:jsqlparser:5.3` | `41bcb5b00488231db179cb5a375690830a59aba521dfa303daa94dcb9dcc8e88` | LGPL-2.1 or Apache-2.0 |
| `com.fasterxml.jackson.core:jackson-databind:2.21.4` | `3888e9e69ab66fbacaacc9aea0e9ffbf15368288e4aca468b024dba11c09fbf9` | Apache-2.0 |
| `org.junit.jupiter:junit-jupiter:5.13.4` | `b960f79217dd01c863031b678f07df4730bbf1eac650c74ad6b0c61faad78379` | Eclipse Public License 2.0 |

JSqlParser's published POM includes a non-test `jmh-core` dependency. That is
acceptable only in this independent experiment; any later production adoption
must make an explicit dependency-tree and licensing decision rather than
copying this POM.

## Probe API and safety boundary

`PersistenceToolProbe.analyze(List<MapperResource>,
List<MapperMethodDescriptor>)` returns original XML resources, structured DOM
statement projections, candidate Java bindings, SQL analysis, and diagnostics.
The caller supplies mapper declarations already derived from saved JDT index
`METHOD.payload.declaration`; it does not pass expected mapper answers.

The reader uses secure JAXP DOM, MyBatis `XPathParser`/`XNode`, and the official
`XMLIncludeTransformer` only on a cloned statement after a static,
unambiguous, acyclic frozen-fragment check. Original XML and include references
remain untouched. Dynamic tags and `#{...}`/`${...}` are retained as DOM/token
metadata. The tool neither executes their OGNL nor constructs executable SQL.

The standard MyBatis Mapper DOCTYPE is accepted without loading its DTD.
External DTDs, general/parameter entity declarations or references, and
XInclude are rejected without external access. If a required JAXP security
control cannot be configured, the probe fails closed rather than returning a
partial resource result.

JSqlParser is an AST enhancement only. It currently projects `PlainSelect`
and `Insert`; dynamic scripts, dynamic identifiers, failed include expansion,
set operations, and unsupported dialect syntax are reported as `PARTIAL` or
`UNSUPPORTED` while the source stays available. The DOM projection preserves
conditional column/value correspondence for `insertSelective`; it never
combines optional branches into confirmed SQL.

`rawSource` intentionally holds the whole frozen resource. `structuredSource`
is a labeled DOM serialization, which may normalize formatting; this harness
does not add a lexical XML slicer merely to produce a statement line range.

## Focused tests

Run only the Gate A tests:

```bash
mvn -B -ntp -f research/persistence-tool-feasibility/pom.xml \
  -Dtest=PersistenceToolProbeTest test
```

## Gate A result

Gate A passed on 2026-09-17. The direct red/green suite ended at **14 tests,
0 failures, 0 errors**; its final Maven execution took 4.184 seconds. The
read-only driver then consumed the approved frozen jshERP snapshot
`snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c`
and its saved Java index in 3.879 seconds, without JDT, a customer build, a
Provider, or a database.

The manifest contained 65 analyzable XML blobs (all size/SHA verified). XPath
recognized 61 mapper resources and retained four non-mapper/security-rejected
resources as diagnostics; it produced 573 statements, 572 candidate
namespace/id bindings, and 573 SQL analyses: 112 `PARSED`, 237 `PARTIAL`, and
224 `UNSUPPORTED`. The raw driver projection is private experiment output at
`.workspace/persistence-tool-feasibility-20260917/snapshot-probe-result.json`
with SHA-256 `869c35a21ee1826c391648b7aea4492801165f842e91b0988807042e46278905`.

The real `DepotItemMapperEx.getFinishNumber` statement retained its three
conditional tests; `DepotItemMapper.insertSelective` retained 52 dynamic DOM
conditions. The pass demonstrates readable source/projection and candidate
association, not runtime SQL correctness or a business conclusion. Supplying
the entire Java `METHOD` index also produces unmatched-declaration diagnostics;
production must consume its explicit Mapper catalog rather than treat every
Java method as a Mapper candidate.

## Fixed-snapshot driver

The CLI accepts only an explicit manifest, matching blob directory, saved Java
index, and a new output path. It verifies every selected XML blob against its
manifest size and SHA-256 before reading it, passes every verified analyzable
XML resource to the probe (XPath decides whether it is a mapper), and derives
method descriptors/`@Param` aliases from `METHOD.payload.declaration`. Output
uses `CREATE_NEW`; choose a previously unused path.

```bash
mvn -B -ntp -f research/persistence-tool-feasibility/pom.xml \
  -DskipTests compile exec:java \
  -Dexec.mainClass=org.sourceanalysis.research.persistence.SnapshotProbeCli \
  -Dexec.args="--snapshot-manifest .workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl --blobs-dir .workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/blobs --java-code-index .workspace/jsherp-full-parallel-20260913/stores/runs/analysis-run--4d1b247703c9a89f40a3982fa040094fa7214fef93ebf9fbb41b159131aaab8b/steps/03-program-graphs/modules/07-java-code-index/java-code-index.jsonl --output .workspace/persistence-tool-feasibility-20260917/snapshot-probe.json"
```

The driver is a read-only tool experiment. Its output is not an Evidence Bundle,
canonical artifact, production input, or business-process conclusion.
