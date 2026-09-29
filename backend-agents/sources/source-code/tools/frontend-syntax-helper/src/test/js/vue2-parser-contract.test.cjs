const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const test = require('node:test')
const parser = require('vue-eslint-parser')

const fixtureRoot = path.resolve(__dirname, '../resources/vue2-static-chain')
const parserVersion = require('vue-eslint-parser/package.json').version
const vueOptions = {
  sourceType: 'module',
  ecmaVersion: 'latest',
  vueFeatures: { filter: true, interpolationAsNonHTML: false },
}

function parseFixture(relativePath) {
  const filename = path.join(fixtureRoot, relativePath)
  const source = fs.readFileSync(filename, 'utf8')
  const result = parser.parseForESLint(source, {
    ...vueOptions,
    filePath: filename,
  })
  return { source, result }
}

function walk(root) {
  const nodes = []
  const pending = [root]
  const seen = new WeakSet()
  while (pending.length > 0) {
    const node = pending.pop()
    if (!node || typeof node !== 'object' || seen.has(node)) continue
    seen.add(node)
    if (typeof node.type === 'string') nodes.push(node)
    for (const [key, value] of Object.entries(node)) {
      if (key === 'parent' || key === 'tokens' || key === 'comments') continue
      if (Array.isArray(value)) pending.push(...value)
      else if (value && typeof value === 'object') pending.push(value)
    }
  }
  return nodes
}

function propertyNamed(object, name) {
  return object.properties.find(
    (property) =>
      property.type === 'Property' &&
      ((property.key.type === 'Identifier' && property.key.name === name) ||
        (property.key.type === 'Literal' && property.key.value === name)),
  )
}

function defaultExportObject(ast) {
  const declaration = ast.body.find((node) => node.type === 'ExportDefaultDeclaration')
  assert.ok(declaration, 'fixture has a default-exported Vue Options API object')
  assert.equal(declaration.declaration.type, 'ObjectExpression')
  return declaration.declaration
}

function methodBody(component, name) {
  const methods = propertyNamed(component, 'methods')
  assert.ok(methods, `${name} is under the Options API methods object`)
  const method = propertyNamed(methods.value, name)
  assert.ok(method, `method ${name} is present in the parser AST`)
  return method.value.body
}

function sourceForNode(source, node) {
  assert.ok(node.range, 'parser provides exact source ranges')
  return source.slice(node.range[0], node.range[1])
}

test('extracts Vue 2 template/script syntax with pinned parser positions', () => {
  assert.equal(parserVersion, '10.4.1')

  const { source, result } = parseFixture('PurchaseOrderModal.vue')
  assert.ok(result.ast.templateBody, 'the SFC template remains available in the AST')
  assert.ok(result.ast.body.some((node) => node.type === 'ExportDefaultDeclaration'))

  const templateNodes = walk(result.ast.templateBody)
  const searchHandler = templateNodes.find(
    (node) =>
      node.type === 'VAttribute' &&
      node.key.name.name === 'on' &&
      node.key.argument.name === 'search',
  )
  assert.ok(searchHandler, 'Vue 2 @search syntax is represented as a VAttribute')
  assert.equal(searchHandler.value.expression.name, 'onSearchLinkApply')
  assert.equal(sourceForNode(source, searchHandler), '@search="onSearchLinkApply"')
  assert.ok(searchHandler.loc.start.line > 0)

  const page = defaultExportObject(result.ast)
  const searchBody = methodBody(page, 'onSearchLinkApply')
  const numberBody = methodBody(page, 'onSearchLinkNumber')
  const searchCalls = walk(searchBody).filter((node) => node.type === 'CallExpression')
  const numberCalls = walk(numberBody).filter((node) => node.type === 'CallExpression')
  assert.deepEqual(
    searchCalls.map((node) => sourceForNode(source, node)),
    ["this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '1,3')"],
  )
  assert.deepEqual(
    numberCalls.map((node) => sourceForNode(source, node)),
    ["this.$refs.linkBillList.purchaseShow('其它', '请购单', '客户', '0,3', 'number')"],
  )
})

test('parses the finite component, mixin, wrapper, and Axios syntax segments', () => {
  const parent = parseFixture('PurchaseOrderModal.vue')
  const child = parseFixture('LinkBillList.vue')
  const mixin = parseFixture('JeecgListMixin.js')
  const manage = parseFixture('manage.js')
  const request = parseFixture('request.js')

  const parentObject = defaultExportObject(parent.result.ast)
  assert.match(parent.source, /import LinkBillList from '\.\.\/dialog\/LinkBillList'/)
  assert.match(parent.source, /components:\s*\{\s*LinkBillList\s*\}/)
  assert.ok(propertyNamed(parentObject, 'mixins'))

  const childObject = defaultExportObject(child.result.ast)
  assert.ok(propertyNamed(childObject, 'mixins'))
  assert.match(child.source, /url:\s*\{\s*list:\s*'\/depotHead\/list'/)
  const purchaseShow = methodBody(childObject, 'purchaseShow')
  assert.deepEqual(
    walk(purchaseShow)
      .filter((node) => node.type === 'CallExpression')
      .map((node) => sourceForNode(child.source, node)),
    ['this.loadData(1)'],
  )

  const namedMixinExport = mixin.result.ast.body.find(
    (node) =>
      node.type === 'ExportNamedDeclaration' &&
      node.declaration?.type === 'VariableDeclaration' &&
      node.declaration.declarations.some(
        (declaration) => declaration.id.type === 'Identifier' && declaration.id.name === 'JeecgListMixin',
      ),
  )
  assert.ok(namedMixinExport, 'the finite shared mixin is a named export')
  const mixinObject = namedMixinExport.declaration.declarations.find(
    (declaration) => declaration.id.name === 'JeecgListMixin',
  ).init
  const loadData = methodBody(mixinObject, 'loadData')
  const loadCalls =
    walk(loadData)
      .filter((node) => node.type === 'CallExpression')
      .map((node) => sourceForNode(mixin.source, node))
  assert.ok(loadCalls.includes('this.getQueryParams()'))
  assert.ok(loadCalls.includes('getAction(this.url.list, params)'))
  assert.match(mixin.source, /getAction\(this\.url\.list, params\)\.then\(/)
  assert.ok(mixin.result.ast.body.some((node) => node.type === 'ImportDeclaration'))
  assert.match(manage.source, /axios\(\{\s*url:\s*url,\s*method:\s*'get',\s*params:\s*parameter\s*\}\)/)
  assert.ok(manage.result.ast.body.some((node) => node.type === 'ExportNamedDeclaration'))
  assert.match(request.source, /window\._CONFIG\['domianURL'\]\s*\|\|\s*'\/jshERP-boot'/)
  assert.match(request.source, /axios\.create\(\{\s*baseURL:\s*apiBaseUrl\s*\}\)/)
  assert.match(request.source, /export \{ service as axios \}/)
  assert.ok(request.result.ast.body.some((node) => node.type === 'ImportDeclaration'))
})

test('keeps recoverable template issues distinct from blocking script syntax errors', () => {
  const recovered = parser.parseForESLint(
    '<template><section><legacy-widget /></section></template>\n' +
      '<script>export default { methods: { run() { return api.fetch() } } }</script>',
    { ...vueOptions, filePath: 'Recovered.vue' },
  )
  const templateErrors = recovered.services.getDocumentFragment().errors
  assert.ok(
    recovered.ast.templateBody,
    'template AST is retained when the parser reports template issues',
  )
  assert.ok(walk(recovered.ast.templateBody).some((node) => node.type === 'VElement'))
  assert.deepEqual(
    templateErrors.map((error) => error.code),
    ['non-void-html-element-start-tag-with-trailing-solidus'],
  )

  assert.throws(() =>
    parser.parseForESLint(
      '<template><div></div></template><script>export default { methods: { run( } }</script>',
      { ...vueOptions, filePath: 'BrokenScript.vue' },
    ),
  )
})
