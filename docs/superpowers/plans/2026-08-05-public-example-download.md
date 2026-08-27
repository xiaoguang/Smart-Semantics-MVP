# Public Example Download Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the next example Markdown and the confirmed workspace reset available from the ordinary application URL while keeping unknown-data download behind `?demoTools=1`.

**Architecture:** Keep example-version selection in `demo-document.ts`. Add a pure menu projection so `AssistantPanel` receives public download and confirmed reset items for every upload-capable workspace, then inserts the unknown-data action only when the query flag is present.

**Tech Stack:** React 19, TypeScript 6, Ant Design 6, Node test runner, Vite 8.

## Global Constraints

- Do not change Fixture data, Runtime commands, localStorage schemas, or port `4173`.
- Run only `npm run test:ai-modeling`, `npm run build`, and `git diff --check`.
- Preserve the existing release and deploy through a new release directory plus atomic `current` symlink switch.

---

### Task 1: Public example download menu

**Files:**
- Modify: `src/features/ai-modeling/demo-document.ts`
- Modify: `src/features/ai-modeling/assistant-panel.tsx`
- Test: `src/features/ai-modeling/ai-modeling.test.ts`
- Modify: `README.md`

**Interfaces:**
- Consumes: `selectDemoDocument(fixture, activeSystemCode, nextExpectedVersion)`.
- Produces: `projectAssistantToolMenu({ canUpload, demoTools, nextExampleAvailable })` returning Ant Design menu items.

- [ ] **Step 1: Add the failing menu projection test**

```ts
assert.deepEqual(projectAssistantToolMenu({ canUpload: true, demoTools: false, nextExampleAvailable: true }).map((item) => item.key), ['download', 'reset']);
assert.deepEqual(projectAssistantToolMenu({ canUpload: true, demoTools: true, nextExampleAvailable: true }).map((item) => item.key), ['download', 'unknown', 'reset']);
```

- [ ] **Step 2: Run the targeted test and confirm it fails because the projection does not exist**

Run: `npm run test:ai-modeling`

- [ ] **Step 3: Implement the pure projection and use it in AssistantPanel**

```ts
export function projectAssistantToolMenu(input: {
  canUpload: boolean;
  demoTools: boolean;
  nextExampleAvailable: boolean;
}) {
  if (!input.canUpload) return [];
  const items = [{ key: 'download', label: '下载下一份示例资料', disabled: !input.nextExampleAvailable }];
  if (input.demoTools) items.push({ key: 'unknown', label: '下载未知资料' });
  items.push({ key: 'reset', label: '恢复当前空间', danger: true });
  return items;
}
```

- [ ] **Step 4: Update README and run targeted verification**

Run sequentially:

```bash
npm run test:ai-modeling
npm run build
git diff --check
```

- [ ] **Step 5: Deploy to the existing server port**

Build a new archive, verify SHA-256 on both sides, extract to a new `/opt/linguan-prototype-v2/releases/<release-id>`, atomically switch `/opt/linguan-prototype-v2/current`, reload `linguan-prototype-v2`, and verify both the ordinary URL and the hashed JavaScript asset return HTTP 200.
