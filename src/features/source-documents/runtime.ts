import { sha256HexSync } from '../ai-modeling/sha256.ts';
import {
  canonicalModelingJson,
  standardSectionOrder,
} from '../modeling-document-bridge/standard-markdown.ts';
import type { StructuredModelingAssertion } from '../modeling-document-bridge/types.ts';
import { createDefaultSourceDocumentMetadataStore } from './source-document-metadata-store.ts';
import {
  assertStructuredSourceDocumentProjection,
  renderSourceDocumentMarkdown,
} from './structured-projection.ts';
import type {
  ContentAddressedStore,
  ContentReference,
  RegisterSourceDocumentInput,
  SourceDocumentBlock,
  SourceDocumentRuntime,
  SourceDocumentLineDiff,
  SourceDocumentMetadataSnapshot,
  SourceDocumentMetadataStore,
  SourceDocumentRevisionDiff,
  SourceDocumentSections,
  SourceModelingDocument,
} from './types.ts';

type MetadataStorage = Pick<Storage, 'getItem' | 'setItem'>;
type StoredState = { schemaVersion: 1; sequence: number; documents: SourceModelingDocument[] };

const clone = <T,>(value: T): T => structuredClone(value);

function refFor(content: string): ContentReference {
  return `sha256:${sha256HexSync(content)}`;
}

export function createMemoryContentStore(): ContentAddressedStore & { size(): number } {
  const values = new Map<ContentReference, string>();
  return {
    async put(content) {
      const ref = refFor(content);
      if (!values.has(ref)) values.set(ref, content);
      return ref;
    },
    async get(ref) { return values.get(ref) ?? null; },
    size() { return values.size; },
  };
}

function load(raw: string | null): StoredState {
  if (!raw) return { schemaVersion: 1, sequence: 0, documents: [] };
  try {
    const parsed = JSON.parse(raw) as Partial<StoredState>;
    return {
      schemaVersion: 1,
      sequence: Number.isInteger(parsed.sequence) ? parsed.sequence! : 0,
      documents: Array.isArray(parsed.documents) ? parsed.documents : [],
    };
  } catch {
    return { schemaVersion: 1, sequence: 0, documents: [] };
  }
}

function assertSections(sections: SourceDocumentSections) {
  for (const { key, heading } of standardSectionOrder) {
    if (typeof sections[key] !== 'string') throw new Error(`来源文档缺少必需章节：${heading}`);
  }
}

export function diffSourceDocumentLines(before: string, after: string): SourceDocumentLineDiff[] {
  const left = before.split('\n');
  const right = after.split('\n');
  const lengths = Array.from({ length: left.length + 1 }, () => Array<number>(right.length + 1).fill(0));
  for (let leftIndex = left.length - 1; leftIndex >= 0; leftIndex -= 1) {
    for (let rightIndex = right.length - 1; rightIndex >= 0; rightIndex -= 1) {
      lengths[leftIndex]![rightIndex] = left[leftIndex] === right[rightIndex]
        ? lengths[leftIndex + 1]![rightIndex + 1]! + 1
        : Math.max(lengths[leftIndex + 1]![rightIndex]!, lengths[leftIndex]![rightIndex + 1]!);
    }
  }
  const result: SourceDocumentLineDiff[] = [];
  let leftIndex = 0;
  let rightIndex = 0;
  while (leftIndex < left.length || rightIndex < right.length) {
    if (leftIndex < left.length && rightIndex < right.length && left[leftIndex] === right[rightIndex]) {
      result.push({ type: 'UNCHANGED', line: left[leftIndex]! });
      leftIndex += 1;
      rightIndex += 1;
    } else if (leftIndex < left.length
      && (rightIndex >= right.length
        || lengths[leftIndex + 1]![rightIndex]! >= lengths[leftIndex]![rightIndex + 1]!)) {
      result.push({ type: 'REMOVED', line: left[leftIndex]! });
      leftIndex += 1;
    } else {
      result.push({ type: 'ADDED', line: right[rightIndex]! });
      rightIndex += 1;
    }
  }
  return result;
}

async function readRequired(store: ContentAddressedStore, ref: ContentReference, label: string) {
  const content = await store.get(ref);
  if (content === null) throw new Error(`${label}内容不存在或已损坏`);
  if (refFor(content) !== ref) throw new Error(`${label}内容校验和不一致`);
  return content;
}

export function createSourceDocumentRuntime(input: {
  metadataStorage?: MetadataStorage;
  metadataStore?: SourceDocumentMetadataStore;
  contentStore: ContentAddressedStore;
  now?: () => string;
}): SourceDocumentRuntime {
  const now = input.now ?? (() => new Date().toISOString());
  let lastCreatedAt: string | undefined;

  function nextCreatedAt(existingDocuments: readonly SourceModelingDocument[] = []) {
    const candidate = now();
    const latestPersisted = existingDocuments.reduce<string | undefined>((latest, document) => (
      !latest || document.createdAt > latest ? document.createdAt : latest
    ), undefined);
    const baseline = !lastCreatedAt || (latestPersisted && latestPersisted > lastCreatedAt)
      ? latestPersisted
      : lastCreatedAt;
    if (!baseline || candidate > baseline) {
      lastCreatedAt = candidate;
      return candidate;
    }
    const previous = Date.parse(baseline);
    if (!Number.isFinite(previous)) {
      throw new Error('来源文档创建时间无效');
    }
    lastCreatedAt = new Date(previous + 1).toISOString();
    return lastCreatedAt;
  }
  const metadataStore = input.metadataStore ?? createDefaultSourceDocumentMetadataStore({
    metadataStorage: input.metadataStorage,
  });

  async function readSnapshot() {
    const snapshot = await metadataStore.read();
    return { snapshot, state: load(snapshot.raw) };
  }

  async function commit(snapshot: SourceDocumentMetadataSnapshot, state: StoredState) {
    if (!await metadataStore.compareAndSet(snapshot.version, JSON.stringify(state))) {
      throw new Error('来源文档已被其他窗口更新');
    }
  }

  async function storeContent(content: string) {
    const expectedRef = refFor(content);
    const actualRef = await input.contentStore.put(content);
    if (actualRef !== expectedRef) throw new Error('内容存储返回的引用与内容SHA不一致');
    return actualRef;
  }

  async function storeDocumentContent(params: {
    documentId: string;
    projectId: string;
    documentCode: string;
    sourceSnapshotId: string;
    sourceName: string;
    revision: number;
    sections: SourceDocumentSections;
    assertions: StructuredModelingAssertion[];
    blocks?: SourceDocumentBlock[];
  }) {
    assertSections(params.sections);
    if (params.blocks) assertStructuredSourceDocumentProjection({
      blocks: params.blocks, assertions: params.assertions, sections: params.sections,
    });
    const sectionsJson = canonicalModelingJson(params.sections);
    const assertionsJson = canonicalModelingJson(params.assertions);
    const markdown = renderSourceDocumentMarkdown(params);
    const [sectionsRef, assertionsRef, markdownRef, blocksRef] = await Promise.all([
      storeContent(sectionsJson),
      storeContent(assertionsJson),
      storeContent(markdown),
      params.blocks ? storeContent(canonicalModelingJson(params.blocks)) : Promise.resolve(undefined),
    ]);
    return {
      sectionsRef,
      assertionsRef,
      markdownRef,
      ...(blocksRef ? { blocksRef } : {}),
      markdownSha256: markdownRef.slice('sha256:'.length),
    };
  }

  return {
    async list(projectId) {
      const { state } = await readSnapshot();
      return clone(state.documents
        .filter((document) => document.projectId === projectId)
        .sort((left, right) => left.createdAt.localeCompare(right.createdAt) || left.revision - right.revision));
    },
    async read(documentId) {
      const { state } = await readSnapshot();
      return clone(state.documents.find((document) => document.documentId === documentId) ?? null);
    },
    async readMarkdown(documentId) {
      const { state } = await readSnapshot();
      const document = state.documents.find((item) => item.documentId === documentId);
      if (!document) throw new Error('来源文档不存在');
      return readRequired(input.contentStore, document.markdownRef, 'Markdown');
    },
    async readSections(documentId) {
      const { state } = await readSnapshot();
      const document = state.documents.find((item) => item.documentId === documentId);
      if (!document) throw new Error('来源文档不存在');
      return JSON.parse(await readRequired(input.contentStore, document.sectionsRef, '章节')) as SourceDocumentSections;
    },
    async readAssertions(documentId) {
      const { state } = await readSnapshot();
      const document = state.documents.find((item) => item.documentId === documentId);
      if (!document) throw new Error('来源文档不存在');
      return JSON.parse(await readRequired(input.contentStore, document.assertionsRef, '断言')) as StructuredModelingAssertion[];
    },
    async readBlocks(documentId) {
      const { state } = await readSnapshot();
      const document = state.documents.find((item) => item.documentId === documentId);
      if (!document) throw new Error('来源文档不存在');
      if (!document.blocksRef) throw new Error('旧版来源文档没有结构化块，只能只读');
      return JSON.parse(await readRequired(input.contentStore, document.blocksRef, '结构化块')) as SourceDocumentBlock[];
    },
    async getRevisionDiff(beforeDocumentId, afterDocumentId) {
      const { state } = await readSnapshot();
      const beforeDocument = state.documents.find((item) => item.documentId === beforeDocumentId);
      const afterDocument = state.documents.find((item) => item.documentId === afterDocumentId);
      if (!beforeDocument || !afterDocument) throw new Error('来源文档不存在');
      if (beforeDocument.projectId !== afterDocument.projectId
        || beforeDocument.documentCode !== afterDocument.documentCode) {
        throw new Error('只能比较同一来源文档的修订');
      }
      if (!beforeDocument.blocksRef || !afterDocument.blocksRef) {
        throw new Error('旧版来源文档没有结构化块，只能只读');
      }
      const [beforeSections, afterSections, beforeAssertions, afterAssertions, beforeBlocks, afterBlocks,
        beforeMarkdown, afterMarkdown] = await Promise.all([
        readRequired(input.contentStore, beforeDocument.sectionsRef, '章节').then((value) => JSON.parse(value) as SourceDocumentSections),
        readRequired(input.contentStore, afterDocument.sectionsRef, '章节').then((value) => JSON.parse(value) as SourceDocumentSections),
        readRequired(input.contentStore, beforeDocument.assertionsRef, '断言').then((value) => JSON.parse(value) as StructuredModelingAssertion[]),
        readRequired(input.contentStore, afterDocument.assertionsRef, '断言').then((value) => JSON.parse(value) as StructuredModelingAssertion[]),
        readRequired(input.contentStore, beforeDocument.blocksRef, '结构化块').then((value) => JSON.parse(value) as SourceDocumentBlock[]),
        readRequired(input.contentStore, afterDocument.blocksRef, '结构化块').then((value) => JSON.parse(value) as SourceDocumentBlock[]),
        readRequired(input.contentStore, beforeDocument.markdownRef, 'Markdown'),
        readRequired(input.contentStore, afterDocument.markdownRef, 'Markdown'),
      ]);
      const beforeBlockById = new Map(beforeBlocks.map((block) => [block.blockId, block]));
      const afterBlockById = new Map(afterBlocks.map((block) => [block.blockId, block]));
      const blockIds = [...new Set([...beforeBlockById.keys(), ...afterBlockById.keys()])];
      const beforeAssertionById = new Map(beforeAssertions.map((assertion) => [assertion.assertionId, assertion]));
      const afterAssertionById = new Map(afterAssertions.map((assertion) => [assertion.assertionId, assertion]));
      const assertionIds = [...new Set([...beforeAssertionById.keys(), ...afterAssertionById.keys()])];
      return {
        beforeDocumentId,
        afterDocumentId,
        beforeRevision: beforeDocument.revision,
        afterRevision: afterDocument.revision,
        actorUserId: afterDocument.createdBy,
        blockChanges: blockIds.flatMap((blockId) => {
          const before = beforeBlockById.get(blockId);
          const after = afterBlockById.get(blockId);
          return canonicalModelingJson(before) === canonicalModelingJson(after)
            ? []
            : [{ blockId, ...(before ? { before: clone(before) } : {}), ...(after ? { after: clone(after) } : {}) }];
        }),
        assertionChanges: assertionIds.flatMap((assertionId) => {
          const before = beforeAssertionById.get(assertionId);
          const after = afterAssertionById.get(assertionId);
          return canonicalModelingJson(before) === canonicalModelingJson(after)
            ? []
            : [{ assertionId, ...(before ? { before: clone(before) } : {}), ...(after ? { after: clone(after) } : {}) }];
        }),
        sectionChanges: standardSectionOrder.flatMap(({ key }) => beforeSections[key] === afterSections[key]
          ? []
          : [{ section: key, before: beforeSections[key], after: afterSections[key], lines: diffSourceDocumentLines(beforeSections[key], afterSections[key]) }]),
        markdown: { before: beforeMarkdown, after: afterMarkdown, lines: diffSourceDocumentLines(beforeMarkdown, afterMarkdown) },
      } satisfies SourceDocumentRevisionDiff;
    },
    async getReviewSummary(documentId) {
      const { state } = await readSnapshot();
      const document = state.documents.find((item) => item.documentId === documentId);
      if (!document) throw new Error('来源文档不存在');
      const sections = JSON.parse(await readRequired(input.contentStore, document.sectionsRef, '章节')) as SourceDocumentSections;
      const assertions = JSON.parse(await readRequired(input.contentStore, document.assertionsRef, '断言')) as StructuredModelingAssertion[];
      return {
        documentId: document.documentId,
        revision: document.revision,
        sourceName: document.sourceName,
        status: document.status,
        sections: standardSectionOrder.map(({ key }) => {
          const sectionAssertions = assertions.filter((assertion) => assertion.section === key);
          return {
            section: key,
            lineCount: sections[key].split('\n').length,
            characterCount: sections[key].length,
            assertionCount: sectionAssertions.length,
            evidenceCount: new Set(sectionAssertions.flatMap((assertion) => assertion.evidenceRefs)).size,
          };
        }),
        validation: clone(document.validation),
      };
    },
    async getSectionDiff(beforeDocumentId, afterDocumentId, section) {
      const { state } = await readSnapshot();
      const beforeDocument = state.documents.find((item) => item.documentId === beforeDocumentId);
      const afterDocument = state.documents.find((item) => item.documentId === afterDocumentId);
      if (!beforeDocument || !afterDocument) throw new Error('来源文档不存在');
      if (beforeDocument.projectId !== afterDocument.projectId
        || beforeDocument.documentCode !== afterDocument.documentCode) {
        throw new Error('只能比较同一来源文档的修订');
      }
      const [beforeSections, afterSections] = await Promise.all([
        readRequired(input.contentStore, beforeDocument.sectionsRef, '章节'),
        readRequired(input.contentStore, afterDocument.sectionsRef, '章节'),
      ]).then(([before, after]) => [JSON.parse(before), JSON.parse(after)] as [SourceDocumentSections, SourceDocumentSections]);
      return {
        section,
        before: beforeSections[section],
        after: afterSections[section],
        changed: beforeSections[section] !== afterSections[section],
        actorUserId: afterDocument.createdBy,
      };
    },
    async register(registerInput: RegisterSourceDocumentInput) {
      const { snapshot, state } = await readSnapshot();
      const prior = state.documents
        .filter((document) => document.projectId === registerInput.projectId
          && document.documentCode === registerInput.documentCode)
        .sort((left, right) => right.revision - left.revision)[0];
      if (prior?.sourceSnapshotId === registerInput.sourceSnapshotId) return clone(prior);
      state.sequence += 1;
      const documentId = `source-document-${state.sequence}`;
      const createdAt = nextCreatedAt(state.documents);
      const revision = (prior?.revision ?? 0) + 1;
      const refs = await storeDocumentContent({
        documentId,
        projectId: registerInput.projectId,
        documentCode: registerInput.documentCode,
        sourceSnapshotId: registerInput.sourceSnapshotId,
        sourceName: registerInput.sourceName,
        revision,
        sections: registerInput.sections,
        assertions: registerInput.assertions,
        blocks: registerInput.blocks,
      });
      const document: SourceModelingDocument = {
        documentId,
        ...(prior ? { derivedFromDocumentId: prior.documentId } : {}),
        projectId: registerInput.projectId,
        documentCode: registerInput.documentCode,
        sourceSnapshotId: registerInput.sourceSnapshotId,
        sourceType: registerInput.sourceType,
        sourceName: registerInput.sourceName,
        revision,
        ...refs,
        validation: clone(registerInput.validation),
        status: 'NEEDS_REVIEW',
        createdBy: registerInput.actorUserId,
        createdAt,
        updatedAt: createdAt,
      };
      if (prior) {
        const priorIndex = state.documents.findIndex((item) => item.documentId === prior.documentId);
        state.documents[priorIndex] = { ...prior, status: 'SUPERSEDED', updatedAt: createdAt };
      }
      state.documents.push(document);
      await commit(snapshot, state);
      return clone(document);
    },
    async revise(reviseInput) {
      const { snapshot, state } = await readSnapshot();
      const currentIndex = state.documents.findIndex((document) => document.documentId === reviseInput.documentId);
      const current = state.documents[currentIndex];
      if (!current) throw new Error('来源文档不存在');
      if (!current.blocksRef) throw new Error('旧版来源文档没有结构化块，只能只读');
      if (current.revision !== reviseInput.expectedRevision) throw new Error('来源文档revision已变化');
      if (current.status === 'SUPERSEDED') throw new Error('只能修改当前来源文档revision');
      assertSections(reviseInput.sections);
      assertStructuredSourceDocumentProjection({
        blocks: reviseInput.blocks,
        assertions: reviseInput.assertions,
        sections: reviseInput.sections,
      });
      const [currentSections, currentAssertions, currentBlocks] = await Promise.all([
        readRequired(input.contentStore, current.sectionsRef, '章节'),
        readRequired(input.contentStore, current.assertionsRef, '断言'),
        readRequired(input.contentStore, current.blocksRef, '结构化块'),
      ]);
      if (currentSections === canonicalModelingJson(reviseInput.sections)
        && currentAssertions === canonicalModelingJson(reviseInput.assertions)
        && currentBlocks === canonicalModelingJson(reviseInput.blocks)) {
        throw new Error('修改没有产生任何变化');
      }
      state.sequence += 1;
      const documentId = `source-document-${state.sequence}`;
      const createdAt = nextCreatedAt(state.documents);
      const refs = await storeDocumentContent({
        documentId,
        projectId: current.projectId,
        documentCode: current.documentCode,
        sourceSnapshotId: current.sourceSnapshotId,
        sourceName: current.sourceName,
        revision: current.revision + 1,
        sections: reviseInput.sections,
        assertions: reviseInput.assertions,
        blocks: reviseInput.blocks,
      });
      const next: SourceModelingDocument = {
        ...current,
        documentId,
        derivedFromDocumentId: current.documentId,
        revision: current.revision + 1,
        ...refs,
        status: 'NEEDS_REVIEW',
        createdBy: reviseInput.actorUserId,
        createdAt,
        updatedAt: createdAt,
      };
      state.documents[currentIndex] = { ...current, status: 'SUPERSEDED', updatedAt: createdAt };
      state.documents.push(next);
      await commit(snapshot, state);
      return clone(next);
    },
    async updateSection(updateInput) {
      const { state } = await readSnapshot();
      const current = state.documents.find((document) => document.documentId === updateInput.documentId);
      if (!current) throw new Error('来源文档不存在');
      if (!current.blocksRef) throw new Error('旧版来源文档没有结构化块，只能只读');
      throw new Error('结构化来源文档必须通过revise修改块、断言和章节');
    },
    async markReady(readyInput) {
      const { snapshot, state } = await readSnapshot();
      const index = state.documents.findIndex((document) => document.documentId === readyInput.documentId);
      if (index < 0) throw new Error('来源文档不存在');
      const current = state.documents[index];
      if (current.revision !== readyInput.expectedRevision) throw new Error('来源文档revision已变化');
      if (current.status === 'SUPERSEDED') throw new Error('历史revision不能重新标记为可对齐');
      if (current.validation.errors.length) throw new Error('来源文档仍有校验错误，不能参与对齐');
      const [sectionsJson, assertionsJson, markdown, blocksJson] = await Promise.all([
        readRequired(input.contentStore, current.sectionsRef, '章节'),
        readRequired(input.contentStore, current.assertionsRef, '断言'),
        readRequired(input.contentStore, current.markdownRef, 'Markdown'),
        current.blocksRef
          ? readRequired(input.contentStore, current.blocksRef, '结构化块')
          : Promise.resolve(undefined),
      ]);
      const sections = JSON.parse(sectionsJson) as SourceDocumentSections;
      const assertions = JSON.parse(assertionsJson) as StructuredModelingAssertion[];
      assertSections(sections);
      if (blocksJson !== undefined) {
        assertStructuredSourceDocumentProjection({
          blocks: JSON.parse(blocksJson) as SourceDocumentBlock[], assertions, sections,
        });
      }
      if (current.markdownSha256 !== current.markdownRef.slice('sha256:'.length)
        || markdown !== renderSourceDocumentMarkdown({ ...current, sections, assertions })) {
        throw new Error('Markdown与章节断言投影不一致');
      }
      const ready: SourceModelingDocument = {
        ...current,
        status: 'READY_FOR_ALIGNMENT',
        updatedAt: now(),
      };
      state.documents[index] = ready;
      await commit(snapshot, state);
      return clone(ready);
    },
  };
}
