const assert = require('node:assert/strict')
const { spawnSync } = require('node:child_process')
const { createHash } = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const fixtureRoot = path.resolve(__dirname, '../resources/ontology-page-context')
const helperPath = path.resolve(__dirname, '../../main.cjs')
const fixtureNames = [
  'AlphaPage.vue',
  'BetaPage.vue',
  'RecordPicker.vue',
  'QueryMixin.js',
  'client.js',
  'request.js',
]
const pagePaths = {
  alpha: 'frontend/pages/AlphaPage.vue',
  beta: 'frontend/pages/BetaPage.vue',
  picker: 'frontend/components/RecordPicker.vue',
  queryMixin: 'frontend/mixins/QueryMixin.js',
  client: 'frontend/api/client.js',
  request: 'frontend/utils/request.js',
}
const fixturePaths = {
  'AlphaPage.vue': pagePaths.alpha,
  'BetaPage.vue': pagePaths.beta,
  'RecordPicker.vue': pagePaths.picker,
  'QueryMixin.js': pagePaths.queryMixin,
  'client.js': pagePaths.client,
  'request.js': pagePaths.request,
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
    requestId: 'ontology-page-context-contract',
    selectedRoots: ['frontend'],
    aliases: {},
    files,
  }
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
  assert.ok(file, `context source unit points at an admitted file: ${sourcePath}`)
  assert.ok(Number.isInteger(range.startOffsetUtf16))
  assert.ok(Number.isInteger(range.lengthUtf16))
  assert.ok(range.lengthUtf16 > 0)
  const end = range.startOffsetUtf16 + range.lengthUtf16
  assert.ok(end <= file.sourceText.length, `${sourcePath} unit range stays in its source`)
  return file.sourceText.slice(range.startOffsetUtf16, end)
}

function contextFor(contexts, pagePath, ref) {
  const instanceKey = `${pagePath}#${ref}`
  const matches = contexts.filter((context) => context.instanceKey === instanceKey)
  assert.equal(matches.length, 1, `one PAGE_CONTEXT record exists for ${instanceKey}`)
  return matches[0]
}

function observationOf(context, kind) {
  const matches = context.observations.filter((observation) => observation.kind === kind)
  assert.equal(matches.length, 1, `${kind} is observed once in ${context.pagePath}`)
  return matches[0]
}

function assertCompleteUnits(context, filesByPath, expectedFragments) {
  assert.ok(Array.isArray(context.sourceUnits) && context.sourceUnits.length > 0)
  const sourceUnitTexts = context.sourceUnits.map((unit) => {
    assert.equal(typeof unit.unitRef, 'string')
    assert.ok(unit.unitRef.length > 0)
    assert.equal(typeof unit.sourcePath, 'string')
    assert.equal(typeof unit.sourceHash, 'string')
    assert.ok(unit.sourceUnitRange, 'each context unit carries a complete sourceUnitRange')
    return sourceFor(filesByPath, unit.sourcePath, unit.sourceUnitRange)
  })
  for (const fragment of expectedFragments) {
    assert.ok(
      sourceUnitTexts.some((text) => text.includes(fragment)),
      `${context.pagePath} retains a complete unit containing ${fragment}`,
    )
  }
}

test('keeps selection contexts separate for two pages sharing one component and preserves the old query request', () => {
  const request = fixtureRequest()
  const records = invokeHelper(request)
  const filesByPath = new Map(request.files.map((file) => [file.path, file]))
  const contexts = recordsOf(records, 'PAGE_CONTEXT')
  assert.equal(contexts.length, 3, 'each child ref instance publishes one typed PAGE_CONTEXT')

  const alpha = contextFor(contexts, pagePaths.alpha, 'picker')
  const alphaSecondary = contextFor(contexts, pagePaths.alpha, 'secondaryPicker')
  const beta = contextFor(contexts, pagePaths.beta, 'picker')
  assert.notEqual(alpha.contextId, alphaSecondary.contextId, 'same-page child refs use distinct contexts')
  assert.notEqual(alpha.contextId, beta.contextId, 'shared component uses distinct page contexts')
  assert.equal(alpha.sourceSha256, filesByPath.get(pagePaths.alpha).sourceHash)
  assert.equal(alphaSecondary.sourceSha256, filesByPath.get(pagePaths.alpha).sourceHash)
  assert.equal(beta.sourceSha256, filesByPath.get(pagePaths.beta).sourceHash)
  assert.deepEqual(
    contexts.map((context) => context.instanceKey).sort(),
    [
      `${pagePaths.alpha}#picker`,
      `${pagePaths.alpha}#secondaryPicker`,
      `${pagePaths.beta}#picker`,
    ].sort(),
    'each literal child reference has its own page-context identity',
  )

  for (const context of [alpha, alphaSecondary, beta]) {
    const templateBinding = observationOf(context, 'TEMPLATE_EVENT_BINDING')
    assert.equal(templateBinding.eventName, 'chosen')
    assert.ok(templateBinding.fromUnitRef)
    assert.ok(templateBinding.toUnitRef)

    const emit = observationOf(context, 'COMPONENT_EMIT')
    assert.equal(emit.eventName, 'chosen')
    assert.deepEqual(emit.actualArguments, ['selectedRows', 'selectedId', "'fixture-source'"])
    assert.ok(emit.fromUnitRef)
    assert.ok(emit.callRange)

    const callback = observationOf(context, 'EVENT_CALLBACK_BINDING')
    assert.equal(callback.eventName, 'chosen')
    assert.deepEqual(callback.actualArguments, ['selectedRows', 'selectedId', "'fixture-source'"])
    assert.equal(callback.formalParameters.length, 2, 'the parent callback retains its two formal parameters')
    assert.deepEqual(
      callback.formalParameters,
      context.instanceKey.endsWith('#secondaryPicker')
        ? ['rows', 'secondaryId']
        : context.pagePath === pagePaths.alpha ? ['rows', 'selectedId'] : ['entries', 'betaId'],
    )
    assert.equal(callback.argumentBindings.length, 2)
    assert.equal(callback.argumentBindings[0].disposition, 'PASSED')
    assert.equal(callback.argumentBindings[1].expression, 'selectedId')

    const callbackFragment = context.instanceKey.endsWith('#secondaryPicker')
      ? 'onSecondaryChosen(rows, secondaryId)'
      : context.pagePath === pagePaths.alpha
        ? 'onChosen(rows, selectedId)'
        : 'onChosen(entries, betaId)'
    assertCompleteUnits(context, filesByPath, [
      'this.$emit(\'chosen\', selectedRows, selectedId, \'fixture-source\')',
      callbackFragment,
    ])

    const pageRequests = recordsOf(records, 'HTTP_REQUEST').filter(
      (requestRecord) => requestRecord.pagePath === context.pagePath,
    )
    const queryRequests = pageRequests.filter((requestRecord) => requestRecord.httpMethod === 'GET')
    const ownQueryRequests = queryRequests.filter(
      (requestRecord) => requestRecord.instanceKey === context.instanceKey,
    )
    assert.equal(ownQueryRequests.length, 1, 'each child ref owns one actual GET request')
    assert.equal(ownQueryRequests[0].resolvedPath, '/records/list')
    const pageDefaultSaveIds = pageRequests
      .filter((requestRecord) => requestRecord.instanceKey === `${context.pagePath}#default`)
      .map((requestRecord) => requestRecord.requestId)
      .sort()
    assert.deepEqual(
      context.requestIds,
      [...pageDefaultSaveIds, ownQueryRequests[0].requestId].sort(),
      'context membership is exact to this child ref, with only page-default saves co-shared',
    )
    assert.deepEqual(
      ownQueryRequests[0].argumentBindings.map((binding) => binding.disposition),
      ['PASSED', 'NOT_PASSED'],
      'the unpassed child-call argument remains explicit',
    )
    assert.ok(context.requestIds.includes(ownQueryRequests[0].requestId))
  }

  const alphaDefaultWrappers = alpha.observations.filter(
    (observation) => observation.kind === 'HTTP_WRAPPER_CALL'
      && observation.detail === 'literal imported save wrapper call',
  )
  assert.equal(alphaDefaultWrappers.length, 2, 'both co-page save branches retain wrapper observations')
  const extraActual = alphaDefaultWrappers.find((observation) =>
    observation.actualArguments.includes("'audit-extra'"),
  )
  assert.ok(extraActual, 'the extra direct-wrapper actual expression remains visible')
  assert.deepEqual(extraActual.formalParameters, ['url', 'method', 'payload'])
  assert.deepEqual(
    extraActual.argumentBindings.map((binding) => [binding.parameterIndex, binding.parameterName, binding.expression, binding.disposition]),
    [
      [0, 'url', 'this.url.update', 'PASSED'],
      [1, 'method', "'PUT'", 'PASSED'],
      [2, 'payload', 'payload', 'PASSED'],
    ],
  )
  const missingFormal = alphaDefaultWrappers.find(
    (observation) => observation.actualArguments.length === 2,
  )
  assert.ok(missingFormal, 'the shorter direct-wrapper call remains present')
  assert.deepEqual(missingFormal.actualArguments, ['this.url.create', "'POST'"])
  assert.deepEqual(missingFormal.formalParameters, ['url', 'method', 'payload'])
  assert.deepEqual(
    missingFormal.argumentBindings.map((binding) => [binding.parameterIndex, binding.parameterName, binding.expression, binding.disposition]),
    [
      [0, 'url', 'this.url.create', 'PASSED'],
      [1, 'method', "'POST'", 'PASSED'],
      [2, 'payload', null, 'NOT_PASSED'],
    ],
  )
})

test('records page-local Promise save callbacks and both conditional save branches without merging URLs', () => {
  const request = fixtureRequest()
  const records = invokeHelper(request)
  const contexts = recordsOf(records, 'PAGE_CONTEXT')
  assert.equal(contexts.length, 3, 'the two Alpha refs remain separate alongside Beta’s ref')

  for (const context of contexts) {
    const promise = observationOf(context, 'PROMISE_CALLBACK')
    assert.ok(promise.fromUnitRef)
    assert.ok(promise.toUnitRef)
    assert.deepEqual(promise.formalParameters, ['value'])

    const conditions = context.requestConditions.filter(
      (condition) => condition.expression === 'this.form.id',
    )
    assert.deepEqual(
      conditions.map((condition) => condition.branch).sort(),
      ['FALSE', 'TRUE'],
      `${context.pagePath} retains both source-controlled save branches`,
    )
    for (const condition of conditions) {
      assert.ok(context.requestIds.includes(condition.requestId))
      assert.ok(condition.unitRef)
      assert.ok(condition.range)
      assert.equal(typeof condition.range.startOffsetUtf16, 'number')
    }

    const pageSpecificFragment = context.pagePath === pagePaths.alpha
      ? '/records/alpha/create'
      : '/records/beta/create'
    assertCompleteUnits(context, new Map(request.files.map((file) => [file.path, file])), [
      'Promise.resolve(payload).then((value) => this.persistRecord(value))',
      pageSpecificFragment,
    ])

    const pageRequests = recordsOf(records, 'HTTP_REQUEST').filter(
      (requestRecord) => requestRecord.pagePath === context.pagePath,
    )
    assert.ok(
      pageRequests.some((requestRecord) => requestRecord.httpMethod === 'POST'),
      `${context.pagePath} retains its POST save request`,
    )
    assert.ok(
      pageRequests.some((requestRecord) => requestRecord.httpMethod === 'PUT'),
      `${context.pagePath} retains its PUT save request`,
    )
    assert.ok(
      pageRequests.every((requestRecord) => requestRecord.resolvedPath?.includes(
        context.pagePath === pagePaths.alpha ? '/alpha/' : '/beta/',
      ) || requestRecord.httpMethod === 'GET'),
      `${context.pagePath} does not borrow another page's save URL`,
    )
  }
})

test('chained Promise callbacks keep distinct observation identities and complete callback bodies', () => {
  const request = fixtureRequest()
  const alpha = request.files.find((file) => file.path === pagePaths.alpha)
  const original = 'return Promise.resolve(payload).then((value) => this.persistRecord(value))'
  const chained = [
    'return Promise.resolve(payload)',
    '  .then((value) => this.persistRecord(value))',
    '  .then((saved) => this.auditRecord(saved))',
  ].join('\n')
  assert.ok(alpha.sourceText.includes(original), 'neutral page fixture has its original Promise call')
  alpha.sourceText = alpha.sourceText.replace(original, chained)
  alpha.sourceHash = sha256(alpha.sourceText)

  const records = invokeHelper(request)
  const contexts = recordsOf(records, 'PAGE_CONTEXT').filter(
    (context) => context.pagePath === pagePaths.alpha,
  )
  assert.equal(contexts.length, 2, 'both Alpha child-reference contexts remain present')

  const filesByPath = new Map(request.files.map((file) => [file.path, file]))
  for (const context of contexts) {
    const callbacks = context.observations.filter(
      (observation) => observation.kind === 'PROMISE_CALLBACK',
    )
    assert.equal(callbacks.length, 2, 'both chained callbacks are represented')
    assert.equal(
      new Set(callbacks.map((observation) => observation.observationId)).size,
      2,
      'different callback nodes have distinct observation identities',
    )
    assert.equal(
      new Set(callbacks.map((observation) => observation.callRange.startOffsetUtf16)).size,
      1,
      'the chained call expressions share their root start offset',
    )
    assert.equal(
      new Set(callbacks.map((observation) => observation.callRange.lengthUtf16)).size,
      2,
      'the chained call expressions retain their distinct AST end offsets',
    )

    const callbackTexts = callbacks.map((observation) => {
      const unit = context.sourceUnits.find((candidate) => candidate.unitRef === observation.toUnitRef)
      assert.ok(unit, 'each Promise callback points to a retained source unit')
      return sourceFor(filesByPath, unit.sourcePath, unit.sourceUnitRange)
    })
    assert.ok(callbackTexts.some((text) => text.includes('this.persistRecord(value)')))
    assert.ok(callbackTexts.some((text) => text.includes('this.auditRecord(saved)')))
  }
})
