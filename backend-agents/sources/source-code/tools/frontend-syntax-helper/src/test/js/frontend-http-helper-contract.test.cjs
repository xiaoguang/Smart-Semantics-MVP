const assert = require('node:assert/strict')
const { spawnSync } = require('node:child_process')
const { createHash } = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')

const fixtureRoot = path.resolve(__dirname, '../resources/vue2-static-chain')
const helperPath = path.resolve(__dirname, '../../main.cjs')
const fixtureNames = [
  'PurchaseOrderModal.vue',
  'LinkBillList.vue',
  'BillModalMixin.js',
  'JeecgListMixin.js',
  'manage.js',
  'request.js',
  'OrderHistoryPage.vue',
  'StockHistoryPage.vue',
]
const fixturePaths = {
  'PurchaseOrderModal.vue': 'frontend/pages/PurchaseOrderModal.vue',
  'LinkBillList.vue': 'frontend/dialog/LinkBillList.vue',
  'BillModalMixin.js': 'frontend/mixins/BillModalMixin.js',
  'JeecgListMixin.js': 'frontend/mixins/JeecgListMixin.js',
  'manage.js': 'frontend/api/manage.js',
  'request.js': 'frontend/utils/request.js',
  'OrderHistoryPage.vue': 'frontend/pages/OrderHistoryPage.vue',
  'StockHistoryPage.vue': 'frontend/pages/StockHistoryPage.vue',
}
const renamedPaths = {
  'frontend/pages/PurchaseOrderModal.vue': 'frontend/pages/SupplyArchivePage.vue',
  'frontend/dialog/LinkBillList.vue': 'frontend/dialog/InboundSelector.vue',
  'frontend/mixins/BillModalMixin.js': 'frontend/mixins/OverlayBehavior.js',
  'frontend/mixins/JeecgListMixin.js': 'frontend/mixins/ListDriver.js',
  'frontend/api/manage.js': 'frontend/api/actionClient.js',
  'frontend/utils/request.js': 'frontend/utils/transport.js',
  'frontend/pages/OrderHistoryPage.vue': 'frontend/pages/CustomerArchivePage.vue',
  'frontend/pages/StockHistoryPage.vue': 'frontend/pages/WarehouseArchivePage.vue',
}

function sha256(sourceText) {
  return createHash('sha256').update(sourceText, 'utf8').digest('hex')
}

function fixtureRequest() {
  const files = fixtureNames.map((name) => {
    const sourceText = fs.readFileSync(path.join(fixtureRoot, name), 'utf8')
    return {
      path: fixturePaths[name],
      sourceHash: sha256(sourceText),
      sourceText,
    }
  })
  return {
    request: {
      schemaVersion: 'frontend-syntax-request-v1',
      requestId: 'vue-static-chain-contract',
      selectedRoots: ['frontend'],
      aliases: { '@': 'frontend' },
      files,
    },
    filesByPath: new Map(files.map((file) => [file.path, file])),
  }
}

function renamedFixtureRequest(originalRequest) {
  const editsByPath = {
    'frontend/pages/PurchaseOrderModal.vue': [
      ["import LinkBillList from '../dialog/LinkBillList'", "import InboundSelector from '../dialog/InboundSelector'"],
      ["import BillModalMixin from '../mixins/BillModalMixin.js'", "import OverlayBehavior from '../mixins/OverlayBehavior.js'"],
      ['<link-bill-list', '<inbound-selector'],
      ['</link-bill-list>', '</inbound-selector>'],
      ['ref="linkBillList"', 'ref="inboundSelector"'],
      ['@search="onSearchLinkApply"', '@search="applyInboundFilter"'],
      ['@search-number="onSearchLinkNumber"', '@search-number="applyNumberFilter"'],
      ['components: { LinkBillList }', 'components: { InboundSelector }'],
      ['onSearchLinkApply', 'applyInboundFilter'],
      ['onSearchLinkNumber', 'applyNumberFilter'],
      ['this.$refs.linkBillList.purchaseShow(', 'this.$refs.inboundSelector.selectInbound('],
    ],
    'frontend/dialog/LinkBillList.vue': [
      ["import { JeecgListMixin } from '../mixins/JeecgListMixin.js'", "import { ListDriver } from '../mixins/ListDriver.js'"],
      ['mixins: [JeecgListMixin]', 'mixins: [ListDriver]'],
      ['purchaseShow', 'selectInbound'],
      [
        'selectInbound(type, source, category, status, purchaseStatus)',
        'selectInbound(kind, origin, family, phase, filterGroup)',
      ],
      ['this.queryParam.status = status', 'this.queryParam.status = phase'],
      ['this.queryParam.purchaseStatus = purchaseStatus', 'this.queryParam.purchaseStatus = filterGroup'],
    ],
    'frontend/mixins/BillModalMixin.js': [
      ['this.$refs.linkBillList', 'this.$refs.inboundSelector'],
    ],
    'frontend/pages/OrderHistoryPage.vue': [
      ["import { JeecgListMixin } from '../mixins/JeecgListMixin.js'", "import { ListDriver } from '../mixins/ListDriver.js'"],
      ['mixins: [JeecgListMixin]', 'mixins: [ListDriver]'],
      ['name: \'OrderHistoryPage\'', "name: 'CustomerArchivePage'"],
      ['refreshHistory', 'reloadArchive'],
    ],
    'frontend/pages/StockHistoryPage.vue': [
      ["import { JeecgListMixin } from '../mixins/JeecgListMixin.js'", "import { ListDriver } from '../mixins/ListDriver.js'"],
      ['mixins: [JeecgListMixin]', 'mixins: [ListDriver]'],
      ['name: \'StockHistoryPage\'', "name: 'WarehouseArchivePage'"],
      ['refreshHistory', 'reloadArchive'],
    ],
    'frontend/mixins/JeecgListMixin.js': [
      ['export const JeecgListMixin =', 'export const ListDriver ='],
      ["'@/api/manage'", "'@/api/actionClient'"],
    ],
    'frontend/api/manage.js': [["'@/utils/request'", "'@/utils/transport'"]],
  }
  const files = originalRequest.files.map((file) => {
    let sourceText = file.sourceText
    for (const [before, after] of editsByPath[file.path] ?? []) {
      assert.ok(sourceText.includes(before), `renaming fixture marker is present: ${before}`)
      sourceText = sourceText.replaceAll(before, after)
    }
    return {
      path: renamedPaths[file.path] ?? file.path,
      sourceHash: sha256(sourceText),
      sourceText,
    }
  })
  return {
    schemaVersion: 'frontend-syntax-request-v1',
    requestId: 'renamed-vue-static-chain-contract',
    selectedRoots: ['frontend'],
    aliases: { '@': 'frontend' },
    files,
  }
}

function invokeHelper(request) {
  const processResult = spawnSync(
    process.execPath,
    [helperPath],
    {
      input: `${JSON.stringify(request)}\n`,
      encoding: 'utf8',
      timeout: 15_000,
    },
  )
  assert.ifError(processResult.error)
  return processResult
}

function parseRecords(stdout) {
  const lines = stdout.split(/\r?\n/).filter((line) => line.length > 0)
  assert.ok(lines.length > 0, 'successful helper response must not be empty')
  return lines.map((line, index) => {
    let record
    assert.doesNotThrow(() => {
      record = JSON.parse(line)
    }, `stdout line ${index + 1} must be a JSON record`)
    assert.equal(record.schemaVersion, 'frontend-syntax-v1')
    assert.ok(['FILE', 'HTTP_REQUEST', 'DIAGNOSTIC'].includes(record.recordType))
    assert.ok(record.payload && typeof record.payload === 'object')
    return record
  })
}

function runHelper(request) {
  const processResult = invokeHelper(request)
  assert.equal(
    processResult.status,
    0,
    `helper must exit successfully; stderr:\n${processResult.stderr}`,
  )
  assert.equal(processResult.signal, null, 'helper must not be terminated by a signal')
  return parseRecords(processResult.stdout)
}

function recordsOf(records, recordType) {
  return records.filter((record) => record.recordType === recordType)
}

function textAtRange(sourceText, sourceRange) {
  assert.ok(Number.isInteger(sourceRange.startOffsetUtf16))
  assert.ok(Number.isInteger(sourceRange.lengthUtf16))
  assert.ok(sourceRange.startOffsetUtf16 >= 0)
  assert.ok(sourceRange.lengthUtf16 > 0)
  const end = sourceRange.startOffsetUtf16 + sourceRange.lengthUtf16
  assert.ok(end <= sourceText.length, 'UTF-16 range stays inside its input source')
  return sourceText.slice(sourceRange.startOffsetUtf16, end)
}

function assertSourceBoundChain(requestFilesByPath, requestRecord, expectedSourceText) {
  const payload = requestRecord.payload
  const pageFile = requestFilesByPath.get(payload.pagePath)
  assert.ok(pageFile, `page path ${payload.pagePath} was not in the admitted input`)
  assert.equal(payload.pageSourceHash, pageFile.sourceHash)
  assert.equal(textAtRange(pageFile.sourceText, payload.sourceRange), expectedSourceText)

  assert.ok(Array.isArray(payload.wrapperPath))
  assert.ok(payload.wrapperPath.length > 0)
  for (const segment of payload.wrapperPath) {
    const file = requestFilesByPath.get(segment.sourcePath)
    assert.ok(file, `wrapper source ${segment.sourcePath} was not in the admitted input`)
    assert.equal(segment.sourceHash, file.sourceHash)
    assert.ok(Number.isInteger(segment.sourceRange.startOffsetUtf16))
    assert.ok(Number.isInteger(segment.sourceRange.lengthUtf16))
    assert.ok(segment.sourceRange.startOffsetUtf16 >= 0)
    assert.ok(segment.sourceRange.lengthUtf16 > 0)
    assert.ok(
      segment.sourceRange.startOffsetUtf16 + segment.sourceRange.lengthUtf16 <=
        file.sourceText.length,
      `wrapper range stays inside ${segment.sourcePath}`,
    )
  }
}

function assertOrderedWrapperSources(observation, expectedPaths) {
  const actualPaths = observation.wrapperPath.map((segment) => segment.sourcePath)
  let priorIndex = -1
  for (const expectedPath of expectedPaths) {
    const index = actualPaths.indexOf(expectedPath)
    assert.ok(index > priorIndex, `${expectedPath} occurs in wrapper-path order`)
    priorIndex = index
  }
}

test('derives source-bound finite Vue request chains with per-page instances and ordered arguments', () => {
  const { request, filesByPath } = fixtureRequest()
  const records = runHelper(request)
  const fileRecords = recordsOf(records, 'FILE').map((record) => record.payload)

  assert.equal(fileRecords.length, request.files.length, 'one disposition per selected input file')
  assert.deepEqual(
    fileRecords.map((file) => file.path).sort(),
    request.files.map((file) => file.path).sort(),
  )
  for (const file of fileRecords) {
    const inputFile = filesByPath.get(file.path)
    assert.equal(file.sourceHash, inputFile.sourceHash)
    assert.equal(file.status, 'PARSED')
  }

  const requestRecords = recordsOf(records, 'HTTP_REQUEST').map((record) => record.payload)
  const purchaseRequests = requestRecords.filter(
    (observation) => observation.pagePath === 'frontend/pages/PurchaseOrderModal.vue',
  )
  assert.equal(purchaseRequests.length, 2, 'both physical purchaseShow uses remain distinct')
  for (const observation of purchaseRequests) {
    assert.equal(observation.httpMethod, 'GET')
    assert.equal(observation.resolvedPath, '/depotHead/list')
    assert.match(observation.baseUrlExpression, /window\._CONFIG\['domianURL'\]/)
    assert.equal(observation.baseUrlStaticFallback, '/jshERP-boot')
    assert.equal(Object.hasOwn(observation, 'baseUrlResolved'), false)
    assert.equal(observation.rawUrlExpression, 'this.url.list')
    const callText = textAtRange(
      filesByPath.get(observation.pagePath).sourceText,
      observation.sourceRange,
    )
    assert.ok(callText.startsWith('this.$refs.linkBillList.purchaseShow('))
    assertSourceBoundChain(filesByPath, { payload: observation }, callText)
  }

  const purchaseCalls = purchaseRequests
    .map((observation) =>
      textAtRange(filesByPath.get(observation.pagePath).sourceText, observation.sourceRange),
    )
    .sort()
  assert.deepEqual(purchaseCalls, [
    "this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '0,3', 'number')",
    "this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '1,3')",
  ])
  const byCall = new Map(
    purchaseRequests.map((observation) => [
      textAtRange(filesByPath.get(observation.pagePath).sourceText, observation.sourceRange),
      observation,
    ]),
  )
  const missingFifth = byCall.get(
    "this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '1,3')",
  )
  const suppliedFifth = byCall.get(
    "this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '0,3', 'number')",
  )
  assert.ok(missingFifth && suppliedFifth)
  assert.equal(missingFifth.instanceKey, 'frontend/pages/PurchaseOrderModal.vue#linkBillList')
  assert.equal(missingFifth.instanceKey, suppliedFifth.instanceKey)
  assert.equal(missingFifth.argumentBindings.length, 5)
  assert.equal(suppliedFifth.argumentBindings.length, 5)
  assert.equal(missingFifth.argumentBindings[3].parameterName, 'status')
  assert.equal(missingFifth.argumentBindings[3].expression, "'1,3'")
  assert.equal(missingFifth.argumentBindings[4].disposition, 'NOT_PASSED')
  assert.deepEqual(
    missingFifth.argumentBindings.map((binding) => binding.disposition),
    ['PASSED', 'PASSED', 'PASSED', 'PASSED', 'NOT_PASSED'],
  )
  assert.deepEqual(
    suppliedFifth.argumentBindings.map((binding) => binding.disposition),
    ['PASSED', 'PASSED', 'PASSED', 'PASSED', 'PASSED'],
  )
  assert.equal(missingFifth.argumentBindings[4].parameterIndex, 4)
  assert.deepEqual(
    missingFifth.argumentBindings.map((binding) => binding.parameterName),
    ['type', 'source', 'category', 'status', 'purchaseStatus'],
  )
  assert.equal(suppliedFifth.argumentBindings[4].parameterName, 'purchaseStatus')
  assert.equal(suppliedFifth.argumentBindings[4].expression, "'number'")
  assert.ok(
    purchaseRequests.every((observation) =>
      observation.wrapperPath.some((segment) => segment.sourcePath === 'frontend/dialog/LinkBillList.vue'),
    ),
    'the parent use reaches the declared LinkBillList component, not a same-named method',
  )
  for (const observation of purchaseRequests) {
    assertOrderedWrapperSources(observation, [
      'frontend/pages/PurchaseOrderModal.vue',
      'frontend/dialog/LinkBillList.vue',
      'frontend/mixins/JeecgListMixin.js',
      'frontend/api/manage.js',
      'frontend/utils/request.js',
    ])
  }

  const pageRequests = requestRecords.filter(
    (observation) =>
      observation.pagePath === 'frontend/pages/OrderHistoryPage.vue' ||
      observation.pagePath === 'frontend/pages/StockHistoryPage.vue',
  )
  assert.equal(pageRequests.length, 2, 'the shared mixin call is retained for both page instances')
  const byPagePath = new Map(pageRequests.map((observation) => [observation.pagePath, observation]))
  const orderRequest = byPagePath.get('frontend/pages/OrderHistoryPage.vue')
  const stockRequest = byPagePath.get('frontend/pages/StockHistoryPage.vue')
  assert.ok(orderRequest && stockRequest)
  assert.notEqual(orderRequest.instanceKey, stockRequest.instanceKey)
  assert.equal(orderRequest.httpMethod, 'GET')
  assert.equal(stockRequest.httpMethod, 'GET')
  assert.equal(orderRequest.resolvedPath, '/orders/history')
  assert.equal(stockRequest.resolvedPath, '/stock/history')
  assert.match(orderRequest.baseUrlExpression, /window\._CONFIG\['domianURL'\]/)
  assert.match(stockRequest.baseUrlExpression, /window\._CONFIG\['domianURL'\]/)
  assert.equal(orderRequest.baseUrlStaticFallback, '/jshERP-boot')
  assert.equal(stockRequest.baseUrlStaticFallback, '/jshERP-boot')
  assert.equal(Object.hasOwn(orderRequest, 'baseUrlResolved'), false)
  assert.equal(Object.hasOwn(stockRequest, 'baseUrlResolved'), false)
  const admittedChild = filesByPath.get('frontend/dialog/LinkBillList.vue').sourceText
  assert.match(admittedChild, /this\.queryParam\.status\s*=\s*status/)
  assert.match(admittedChild, /this\.queryParam\.purchaseStatus\s*=\s*purchaseStatus/)
  assert.match(admittedChild, /this\.loadData\(1\)/)
  for (const observation of pageRequests) {
    assertSourceBoundChain(
      filesByPath,
      { payload: observation },
      'this.loadData(1)',
    )
    assertOrderedWrapperSources(observation, [
      observation.pagePath,
      'frontend/mixins/JeecgListMixin.js',
      'frontend/api/manage.js',
      'frontend/utils/request.js',
    ])
  }
  assert.deepEqual(recordsOf(records, 'DIAGNOSTIC'), [])
})

test('resolves the same finite chains after page, component, method, and import names change', () => {
  const { request: originalRequest } = fixtureRequest()
  const request = renamedFixtureRequest(originalRequest)
  const filesByPath = new Map(request.files.map((file) => [file.path, file]))
  const records = runHelper(request)
  const fileRecords = recordsOf(records, 'FILE').map((record) => record.payload)
  assert.deepEqual(
    fileRecords.map((file) => file.path).sort(),
    request.files.map((file) => file.path).sort(),
  )
  for (const file of fileRecords) assert.equal(file.sourceHash, filesByPath.get(file.path).sourceHash)

  const requestRecords = recordsOf(records, 'HTTP_REQUEST').map((record) => record.payload)
  const supplyArchiveRequests = requestRecords.filter(
    (observation) => observation.pagePath === renamedPaths['frontend/pages/PurchaseOrderModal.vue'],
  )
  assert.equal(supplyArchiveRequests.length, 2)
  const renamedPage = filesByPath.get(renamedPaths['frontend/pages/PurchaseOrderModal.vue'])
  const renamedCallTexts = supplyArchiveRequests
    .map((observation) => {
      assertSourceBoundChain(
        filesByPath,
        { payload: observation },
        textAtRange(renamedPage.sourceText, observation.sourceRange),
      )
      return textAtRange(renamedPage.sourceText, observation.sourceRange)
    })
    .sort()
  assert.deepEqual(renamedCallTexts, [
    "this.$refs.inboundSelector.selectInbound('其它', '请购单', '客户', '0,3', 'number')",
    "this.$refs.inboundSelector.selectInbound('其它', '请购单', '客户', '1,3')",
  ])
  const missingFifth = supplyArchiveRequests.find((observation) =>
    textAtRange(renamedPage.sourceText, observation.sourceRange).endsWith("'1,3')"),
  )
  const suppliedFifth = supplyArchiveRequests.find((observation) =>
    textAtRange(renamedPage.sourceText, observation.sourceRange).endsWith("'number')"),
  )
  assert.ok(missingFifth && suppliedFifth)
  assert.equal(
    missingFifth.instanceKey,
    'frontend/pages/SupplyArchivePage.vue#inboundSelector',
  )
  assert.deepEqual(
    missingFifth.argumentBindings.map((binding) => binding.disposition),
    ['PASSED', 'PASSED', 'PASSED', 'PASSED', 'NOT_PASSED'],
  )
  assert.equal(missingFifth.argumentBindings[4].parameterName, 'filterGroup')
  assert.equal(suppliedFifth.argumentBindings[4].parameterName, 'filterGroup')
  for (const observation of supplyArchiveRequests) {
    assertOrderedWrapperSources(observation, [
      renamedPaths['frontend/pages/PurchaseOrderModal.vue'],
      renamedPaths['frontend/dialog/LinkBillList.vue'],
      renamedPaths['frontend/mixins/JeecgListMixin.js'],
      renamedPaths['frontend/api/manage.js'],
      renamedPaths['frontend/utils/request.js'],
    ])
  }

  const archivePages = [
    renamedPaths['frontend/pages/OrderHistoryPage.vue'],
    renamedPaths['frontend/pages/StockHistoryPage.vue'],
  ]
  const pageRequests = requestRecords.filter((observation) => archivePages.includes(observation.pagePath))
  assert.equal(pageRequests.length, 2)
  assert.deepEqual(
    pageRequests.map((observation) => observation.pagePath).sort(),
    archivePages.slice().sort(),
  )
  assert.deepEqual(
    pageRequests.map((observation) => observation.resolvedPath).sort(),
    ['/orders/history', '/stock/history'],
  )
  for (const observation of pageRequests) {
    assert.equal(observation.instanceKey, `${observation.pagePath}#default`)
    assertOrderedWrapperSources(observation, [
      observation.pagePath,
      renamedPaths['frontend/mixins/JeecgListMixin.js'],
      renamedPaths['frontend/api/manage.js'],
      renamedPaths['frontend/utils/request.js'],
    ])
  }
})

test('resolves the fixed purchase request chain with the configured trailing-slash @/ alias', () => {
  const { request: baseRequest } = fixtureRequest()
  const request = {
    ...baseRequest,
    aliases: { '@/': 'frontend/' },
  }
  const records = runHelper(request)
  const purchaseRequests = recordsOf(records, 'HTTP_REQUEST')
    .map((record) => record.payload)
    .filter((observation) => observation.pagePath === 'frontend/pages/PurchaseOrderModal.vue')

  assert.equal(purchaseRequests.length, 2, 'both purchase-page request uses resolve through the alias chain')
  assert.ok(purchaseRequests.every((observation) => observation.resolvedPath === '/depotHead/list'))
})

test('keeps good request chains when one independent Vue file has a syntax error', () => {
  const { request: baseRequest } = fixtureRequest()
  const badPage = {
    path: 'frontend/InvalidIndependentPanel.vue',
    sourceText: '<template><section /></template>\n<script>export default { methods: { run( } }</script>\n',
  }
  const request = {
    ...baseRequest,
    files: [
      ...baseRequest.files,
      { ...badPage, sourceHash: sha256(badPage.sourceText) },
    ],
  }
  const processResult = invokeHelper(request)
  assert.equal(
    processResult.status,
    0,
    `an independent file syntax failure is represented as data; stderr:\n${processResult.stderr}`,
  )
  const records = parseRecords(processResult.stdout)
  const dispositions = recordsOf(records, 'FILE').map((record) => record.payload)
  assert.equal(
    dispositions.find((file) => file.path === badPage.path)?.status,
    'FAILED',
  )
  assert.ok(
    recordsOf(records, 'DIAGNOSTIC').some((record) => record.payload.path === badPage.path),
    'the failed file has its own source-bound diagnostic',
  )
  const observations = recordsOf(records, 'HTTP_REQUEST').map((record) => record.payload)
  assert.equal(observations.length, 4, 'a failed independent file does not erase unrelated request chains')
  assert.deepEqual(
    observations.map((observation) => observation.pagePath).sort(),
    [
      'frontend/pages/OrderHistoryPage.vue',
      'frontend/pages/PurchaseOrderModal.vue',
      'frontend/pages/PurchaseOrderModal.vue',
      'frontend/pages/StockHistoryPage.vue',
    ],
  )
})

test('keeps independent request chains when a parsed parent imports a syntax-failed child', () => {
  const { request: baseRequest } = fixtureRequest()
  const failedChildPath = 'frontend/dialog/LinkBillList.vue'
  const failedChildSource =
    '<template><div /></template>\n<script>export default { methods: { broken( } }</script>\n'
  const request = {
    ...baseRequest,
    files: baseRequest.files.map((file) =>
      file.path === failedChildPath
        ? { ...file, sourceText: failedChildSource, sourceHash: sha256(failedChildSource) }
        : file,
    ),
  }

  const processResult = invokeHelper(request)
  assert.equal(
    processResult.status,
    0,
    `a missing AST for an imported failed child is local; stderr:\n${processResult.stderr}`,
  )
  const records = parseRecords(processResult.stdout)
  const dispositions = recordsOf(records, 'FILE').map((record) => record.payload)
  const failedChild = dispositions.find((file) => file.path === failedChildPath)
  assert.equal(failedChild?.status, 'FAILED')
  assert.equal(failedChild?.sourceHash, sha256(failedChildSource))
  assert.ok(
    recordsOf(records, 'DIAGNOSTIC').some(
      (record) =>
        record.payload.code === 'SYNTAX_PARSE_FAILED' &&
        record.payload.sourcePath === failedChildPath &&
        record.payload.sourceHash === sha256(failedChildSource),
    ),
    'the invalid child remains explicitly source-bound as a failed file',
  )

  const independentRequests = recordsOf(records, 'HTTP_REQUEST').map((record) => record.payload)
  assert.deepEqual(
    independentRequests.map((observation) => observation.pagePath).sort(),
    ['frontend/pages/OrderHistoryPage.vue', 'frontend/pages/StockHistoryPage.vue'],
    'unrelated parsed page instances still publish their known request chains',
  )
})

test('parses a valid Vue script containing JSX as an admitted file', () => {
  const sourceText = [
    '<template><div /></template>',
    '<script>',
    'export default {',
    '  render() { return <section class="jsx-panel" />; },',
    '};',
    '</script>',
    '',
  ].join('\n')
  const file = {
    path: 'frontend/JsxPanel.vue',
    sourceHash: sha256(sourceText),
    sourceText,
  }
  const request = {
    schemaVersion: 'frontend-syntax-request-v1',
    requestId: 'valid-vue-jsx-contract',
    selectedRoots: ['frontend'],
    aliases: {},
    files: [file],
  }

  const records = runHelper(request)
  const disposition = recordsOf(records, 'FILE').find((record) => record.payload.path === file.path)
  assert.equal(disposition?.payload.status, 'PARSED')
  assert.equal(disposition?.payload.sourceHash, file.sourceHash)
  assert.equal(
    recordsOf(records, 'DIAGNOSTIC').some((record) => record.payload.sourcePath === file.path),
    false,
    'valid JSX syntax is not reported as a parse failure',
  )
})

test('fails the protocol when an admitted source hash does not match its text', () => {
  const { request: baseRequest } = fixtureRequest()
  const request = {
    ...baseRequest,
    files: baseRequest.files.map((file, index) =>
      index === 0 ? { ...file, sourceHash: '0'.repeat(64) } : file,
    ),
  }
  const processResult = invokeHelper(request)
  assert.notEqual(processResult.status, 0, 'source identity corruption is a fatal helper error')
  assert.equal(
    processResult.stdout.match(/"recordType"\s*:\s*"HTTP_REQUEST"/g)?.length ?? 0,
    0,
    'no request chain is published from a source-identity-corrupt invocation',
  )
})
