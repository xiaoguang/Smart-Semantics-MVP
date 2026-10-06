const assert = require('node:assert/strict')
const { spawnSync } = require('node:child_process')
const { createHash } = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const fixtureRoot = path.resolve(__dirname, '../resources/ontology-page-context-three-arg')
const helperPath = path.resolve(__dirname, '../../main.cjs')
const fixtureNames = [
  'CanvasPage.vue',
  'ChoicePanel.vue',
  'CanvasEditorMixin.js',
  'actionClient.js',
  'transport.js',
]
const fixturePaths = {
  'CanvasPage.vue': 'frontend/pages/CanvasPage.vue',
  'ChoicePanel.vue': 'frontend/components/ChoicePanel.vue',
  'CanvasEditorMixin.js': 'frontend/mixins/CanvasEditorMixin.js',
  'actionClient.js': 'frontend/api/actionClient.js',
  'transport.js': 'frontend/utils/transport.js',
}

function sha256(sourceText) {
  return createHash('sha256').update(sourceText, 'utf8').digest('hex')
}

function fixtureRequest() {
  const files = fixtureNames.map((name) => {
    const sourceText = fs.readFileSync(path.join(fixtureRoot, name), 'utf8')
    return { path: fixturePaths[name], sourceHash: sha256(sourceText), sourceText }
  })
  return {
    schemaVersion: 'frontend-syntax-request-v1',
    requestId: 'ontology-page-context-three-arg-contract',
    selectedRoots: ['frontend'],
    aliases: {},
    files,
  }
}

function requestWithModifiedMixin(sourceText) {
  const request = fixtureRequest()
  const mixin = request.files.find((file) => file.path === fixturePaths['CanvasEditorMixin.js'])
  mixin.sourceText = sourceText
  mixin.sourceHash = sha256(sourceText)
  return request
}

function requestWithSplitStandaloneMixins() {
  const request = fixtureRequest()
  const page = request.files.find((file) => file.path === fixturePaths['CanvasPage.vue'])
  const editor = request.files.find((file) => file.path === fixturePaths['CanvasEditorMixin.js'])
  const entryMethods = [
    '    handleOkOnly() {',
    "      this.status = 'draft'",
    '      this.handleOk()',
    '    },',
    '    handleOkAndCheck() {',
    "      this.status = 'checked'",
    '      this.handleOk()',
    '    },',
  ].join('\n')
  const standaloneAction = [
    'httpAction(url, formData, method)',
    '        .then((response) => {',
    '          this.savedResponse = response',
    '        })',
    '        .then((saved) => {',
    '          this.completedRecord = saved',
    '        })',
  ].join('\n')
  assert.ok(editor.sourceText.includes(entryMethods), 'fixture has the entry click methods')
  assert.ok(
    editor.sourceText.includes('return httpAction(url, formData, method)'),
    'fixture has the existing returned action call',
  )
  editor.sourceText = editor.sourceText
    .replace(entryMethods, '')
    .replace('return httpAction(url, formData, method)', standaloneAction)
  editor.sourceHash = sha256(editor.sourceText)

  const editorImport = "import { CanvasEditorMixin } from '../mixins/CanvasEditorMixin.js'"
  assert.ok(page.sourceText.includes(editorImport), 'page imports its editor mixin')
  page.sourceText = page.sourceText
    .replace(
      editorImport,
      `${editorImport}\nimport { CanvasEntryMixin } from '../mixins/CanvasEntryMixin.js'`,
    )
    .replace('mixins: [CanvasEditorMixin],', 'mixins: [CanvasEntryMixin, CanvasEditorMixin],')
  page.sourceHash = sha256(page.sourceText)

  const entryMixinPath = 'frontend/mixins/CanvasEntryMixin.js'
  const entryMixinSource = [
    'export const CanvasEntryMixin = {',
    '  methods: {',
    "    handleOkOnly() { this.status = 'draft'; this.handleOk() },",
    "    handleOkAndCheck() { this.status = 'checked'; this.handleOk() },",
    '  },',
    '}',
  ].join('\n')
  request.files.push({
    path: entryMixinPath,
    sourceHash: sha256(entryMixinSource),
    sourceText: entryMixinSource,
  })
  return { request, entryMixinPath, editorMixinPath: fixturePaths['CanvasEditorMixin.js'] }
}

function invokeHelper(request) {
  const processResult = spawnSync(process.execPath, [helperPath], {
    input: `${JSON.stringify(request)}\n`,
    encoding: 'utf8',
    timeout: 15_000,
  })
  assert.ifError(processResult.error)
  assert.equal(processResult.status, 0, `helper exits successfully: ${processResult.stderr}`)
  assert.equal(processResult.signal, null, 'helper is not terminated by a signal')
  return processResult.stdout
    .split(/\r?\n/)
    .filter(Boolean)
    .map((line) => JSON.parse(line))
}

function recordsOf(records, recordType) {
  return records.filter((record) => record.recordType === recordType).map((record) => record.payload)
}

function sourceFor(filesByPath, sourcePath, range) {
  const file = filesByPath.get(sourcePath)
  assert.ok(file, `source unit points at an admitted file: ${sourcePath}`)
  assert.ok(Number.isInteger(range.startOffsetUtf16))
  assert.ok(Number.isInteger(range.lengthUtf16))
  assert.ok(range.lengthUtf16 > 0)
  const end = range.startOffsetUtf16 + range.lengthUtf16
  assert.ok(end <= file.sourceText.length, `${sourcePath} unit range stays in its source`)
  return file.sourceText.slice(range.startOffsetUtf16, end)
}

test('records the finite mixin Promise save shape with all wrapper arguments and source conditions', () => {
  const request = fixtureRequest()
  const records = invokeHelper(request)
  const filesByPath = new Map(request.files.map((file) => [file.path, file]))
  const contexts = recordsOf(records, 'PAGE_CONTEXT')
  assert.equal(contexts.length, 1, 'one page context is published for the renamed non-ERP fixture')

  const context = contexts[0]
  assert.equal(context.pagePath, fixturePaths['CanvasPage.vue'])
  assert.deepEqual(context.observations.map((observation) => observation.kind).sort(), [
    'COMPONENT_EMIT',
    'EVENT_CALLBACK_BINDING',
    'HTTP_WRAPPER_CALL',
    'PROMISE_CALLBACK',
    'TEMPLATE_EVENT_BINDING',
  ])

  const callback = context.observations.find((observation) => observation.kind === 'PROMISE_CALLBACK')
  assert.deepEqual(callback.formalParameters, ['allValues'])
  assert.ok(callback.fromUnitRef)
  assert.ok(callback.toUnitRef)

  const wrapper = context.observations.find((observation) => observation.kind === 'HTTP_WRAPPER_CALL')
  assert.deepEqual(wrapper.actualArguments, ['url', 'formData', 'method'])
  assert.ok(wrapper.fromUnitRef)
  assert.ok(wrapper.toUnitRef)

  const saves = recordsOf(records, 'HTTP_REQUEST')
    .filter((record) => record.pagePath === context.pagePath)
    .sort((left, right) => left.httpMethod.localeCompare(right.httpMethod))
  assert.deepEqual(saves.map((record) => record.httpMethod), ['POST', 'PUT'])
  assert.ok(saves.every((record) => context.requestIds.includes(record.requestId)))
  assert.deepEqual(
    saves[0].argumentBindings.map((binding) => [binding.parameterName, binding.expression, binding.disposition]),
    [
      ['url', 'url', 'PASSED'],
      ['parameter', 'formData', 'PASSED'],
      ['method', 'method', 'PASSED'],
    ],
  )

  assert.deepEqual(
    context.requestConditions
      .map((condition) => [condition.expression, condition.branch])
      .sort((left, right) => left[1].localeCompare(right[1])),
    [
      ['this.model.id', 'FALSE'],
      ['this.model.id', 'TRUE'],
    ],
  )
  assert.ok(context.requestConditions.every((condition) => context.requestIds.includes(condition.requestId)))
  for (const condition of context.requestConditions) {
    const sourceUnit = context.sourceUnits.find((unit) => unit.unitRef === condition.unitRef)
    assert.ok(sourceUnit, 'each condition refers to one preserved source unit')
    assert.equal(sourceUnit.sourcePath, fixturePaths['CanvasEditorMixin.js'])
    assert.equal(sourceUnit.sourceHash, filesByPath.get(fixturePaths['CanvasEditorMixin.js']).sourceHash)
    assert.equal(
      sourceFor(filesByPath, sourceUnit.sourcePath, condition.range),
      'this.model.id',
      'the condition expression is restored from its original mixin source and range',
    )
    assert.ok(
      sourceFor(filesByPath, sourceUnit.sourcePath, sourceUnit.sourceUnitRange).includes(
        'if (this.model.id)',
      ),
      'the condition unit resolves to the complete mixin function containing its branch',
    )
  }

  const unitTexts = context.sourceUnits.map((unit) =>
    sourceFor(filesByPath, unit.sourcePath, unit.sourceUnitRange),
  )
  for (const fragment of [
    'handleOkOnly()',
    'return this.getAllTable().then((allValues) =>',
    'request(formData)',
    'httpAction(url, formData, method)',
  ]) {
    assert.ok(unitTexts.some((text) => text.includes(fragment)), `complete unit retains ${fragment}`)
  }
})

test('records three-argument saves in a standalone Promise chain with both callback bodies', () => {
  const original = fs.readFileSync(path.join(fixtureRoot, 'CanvasEditorMixin.js'), 'utf8')
  const returnedCall = 'return httpAction(url, formData, method)'
  const standaloneChain = [
    'httpAction(url, formData, method)',
    '  .then((response) => {',
    '    this.savedResponse = response',
    '  })',
    '  .then((saved) => {',
    '    this.completedRecord = saved',
    '  })',
  ].join('\n')
  assert.ok(original.includes(returnedCall), 'neutral mixin fixture has the returned wrapper call')
  const request = requestWithModifiedMixin(original.replace(returnedCall, standaloneChain))
  const records = invokeHelper(request)
  const filesByPath = new Map(request.files.map((file) => [file.path, file]))
  const contexts = recordsOf(records, 'PAGE_CONTEXT')
  assert.equal(contexts.length, 1, 'one page context remains published')

  const context = contexts[0]
  const saves = recordsOf(records, 'HTTP_REQUEST')
    .filter((record) => record.pagePath === fixturePaths['CanvasPage.vue'])
    .sort((left, right) => left.httpMethod.localeCompare(right.httpMethod))
  assert.deepEqual(
    saves.map((record) => [record.httpMethod, record.resolvedPath]),
    [
      ['POST', '/canvas/record/add'],
      ['PUT', '/canvas/record/edit'],
    ],
    'the standalone Promise expression retains both exact save branches',
  )
  assert.ok(saves.every((record) => context.requestIds.includes(record.requestId)))
  assert.deepEqual(
    context.requestConditions
      .map((condition) => [condition.expression, condition.branch])
      .sort((left, right) => left[1].localeCompare(right[1])),
    [
      ['this.model.id', 'FALSE'],
      ['this.model.id', 'TRUE'],
    ],
    'the original conditional evidence still maps to both requests',
  )

  const requestUnits = context.sourceUnits.filter(
    (unit) => unit.sourcePath === fixturePaths['CanvasEditorMixin.js'],
  )
  const completeRequestMethod = requestUnits
    .map((unit) => sourceFor(filesByPath, unit.sourcePath, unit.sourceUnitRange))
    .find((text) => text.includes('request(formData)'))
  assert.ok(completeRequestMethod, 'the source method unit for request(formData) is retained')
  for (const fragment of [
    'httpAction(url, formData, method)',
    'this.savedResponse = response',
    'this.completedRecord = saved',
  ]) {
    assert.ok(
      completeRequestMethod.includes(fragment),
      `the complete source method preserves ${fragment}`,
    )
  }
})

test('keeps source units with their owning mixin when the clicked ancestor delegates across mixins', () => {
  const { request, entryMixinPath, editorMixinPath } = requestWithSplitStandaloneMixins()
  const records = invokeHelper(request)
  const filesByPath = new Map(request.files.map((file) => [file.path, file]))
  const contexts = recordsOf(records, 'PAGE_CONTEXT')
  assert.equal(contexts.length, 1, 'one page context is published')
  const context = contexts[0]

  const saves = recordsOf(records, 'HTTP_REQUEST')
    .filter((record) => record.pagePath === fixturePaths['CanvasPage.vue'])
    .sort((left, right) => left.httpMethod.localeCompare(right.httpMethod))
  assert.deepEqual(
    saves.map((record) => [record.httpMethod, record.resolvedPath]),
    [
      ['POST', '/canvas/record/add'],
      ['PUT', '/canvas/record/edit'],
    ],
    'the split-mixin standalone wrapper preserves both exact request branches',
  )
  assert.ok(saves.every((record) => context.requestIds.includes(record.requestId)))

  const sourceTextForUnit = (unitRef) => {
    const unit = context.sourceUnits.find((candidate) => candidate.unitRef === unitRef)
    assert.ok(unit, `source unit ${unitRef} is present`)
    const sourceFile = filesByPath.get(unit.sourcePath)
    assert.ok(sourceFile, `source path ${unit.sourcePath} is an admitted file`)
    assert.equal(unit.sourceHash, sourceFile.sourceHash, 'source-unit hash matches its owning file')
    const text = sourceFor(filesByPath, unit.sourcePath, unit.sourceUnitRange)
    assert.ok(text.length > 0)
    assert.equal(
      unit.unitRef,
      `${unit.sourcePath}:${unit.sourceUnitKind}:${unit.sourceUnitRange.startOffsetUtf16}:${unit.sourceUnitRange.lengthUtf16}`,
      'unit identity agrees with the exact source path and full range',
    )
    return { unit, text }
  }
  for (const unit of context.sourceUnits) sourceTextForUnit(unit.unitRef)

  const entryUnit = context.sourceUnits
    .filter((unit) => unit.sourcePath === entryMixinPath)
    .map((unit) => ({ unit, text: sourceTextForUnit(unit.unitRef).text }))
    .find(({ text }) => text.includes('handleOkOnly()'))
  assert.ok(entryUnit, 'the clicked initial method is restored from its owning entry mixin')
  assert.ok(entryUnit.text.includes("this.status = 'draft'"))

  const promise = context.observations.find(
    (observation) =>
      observation.kind === 'PROMISE_CALLBACK' &&
      observation.detail === 'literal mixin Promise.then callback',
  )
  assert.ok(promise, 'the delegated mixin Promise callback is present')
  const from = sourceTextForUnit(promise.fromUnitRef)
  const to = sourceTextForUnit(promise.toUnitRef)
  assert.equal(from.unit.sourcePath, editorMixinPath, 'callback source belongs to handleOk in the editor mixin')
  assert.ok(from.text.includes('handleOk()'))
  assert.equal(to.unit.sourcePath, editorMixinPath, 'callback body belongs to the same editor mixin')
  assert.ok(to.text.includes('return this.request(formData)'))

  const requestMethod = context.sourceUnits
    .filter((unit) => unit.sourcePath === editorMixinPath)
    .map((unit) => sourceTextForUnit(unit.unitRef).text)
    .find((text) => text.includes('request(formData)'))
  assert.ok(requestMethod, 'the delegated request method is restored from the editor mixin')
  for (const fragment of [
    'httpAction(url, formData, method)',
    'this.savedResponse = response',
    'this.completedRecord = saved',
  ]) {
    assert.ok(requestMethod.includes(fragment), `request method preserves ${fragment}`)
  }

  assert.deepEqual(
    context.requestConditions
      .map((condition) => [condition.expression, condition.branch])
      .sort((left, right) => left[1].localeCompare(right[1])),
    [
      ['this.model.id', 'FALSE'],
      ['this.model.id', 'TRUE'],
    ],
    'conditional evidence remains attached to the emitted POST/PUT requests',
  )
  for (const condition of context.requestConditions) {
    const { unit, text } = sourceTextForUnit(condition.unitRef)
    assert.equal(unit.sourcePath, editorMixinPath)
    assert.equal(
      sourceFor(filesByPath, unit.sourcePath, condition.range),
      'this.model.id',
      'condition range resolves within its owning request method',
    )
    assert.ok(text.includes('if (this.model.id)'))
  }
})

test('does not confirm a conditional mixin save after an additional method rewrite', () => {
  const original = fs.readFileSync(path.join(fixtureRoot, 'CanvasEditorMixin.js'), 'utf8')
  const request = requestWithModifiedMixin(
    original.replace(
      "        method = 'PUT'\n      }\n      return httpAction",
      "        method = 'PUT'\n      }\n      method = this.overrideMethod\n      return httpAction",
    ),
  )
  const records = invokeHelper(request)
  const contexts = recordsOf(records, 'PAGE_CONTEXT')
  assert.equal(contexts.length, 1, 'the independent component-event context remains available')
  assert.equal(
    recordsOf(records, 'HTTP_REQUEST').filter((record) => record.pagePath === fixturePaths['CanvasPage.vue']).length,
    0,
    'an extended method-rewrite shape is not reported as either confirmed HTTP branch',
  )
  assert.deepEqual(
    contexts[0].observations.map((observation) => observation.kind).sort(),
    ['COMPONENT_EMIT', 'EVENT_CALLBACK_BINDING', 'TEMPLATE_EVENT_BINDING'],
    'no Promise or wrapper observation is emitted without the exact finite save shape',
  )
})
