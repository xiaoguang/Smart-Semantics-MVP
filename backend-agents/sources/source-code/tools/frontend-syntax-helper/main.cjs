#!/usr/bin/env node

const { createHash } = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const parser = require('vue-eslint-parser')

const REQUEST_SCHEMA = 'frontend-syntax-request-v1'
const RESPONSE_SCHEMA = 'frontend-syntax-v1'
const SUPPORTED_EXTENSIONS = new Set(['.vue', '.js'])

function sourceHash(sourceText) {
  return createHash('sha256').update(sourceText, 'utf8').digest('hex')
}

function sourceRange(node) {
  if (!node || !Array.isArray(node.range) || node.range.length !== 2) {
    throw new Error('parser node omitted a source range')
  }
  return {
    startOffsetUtf16: node.range[0],
    lengthUtf16: node.range[1] - node.range[0],
  }
}

function sourceTextAt(file, node) {
  return file.sourceText.slice(node.range[0], node.range[1])
}

function fileFallbackUnit(file) {
  return { range: [0, file.sourceText.length] }
}

function parseFile(file) {
  return parser.parseForESLint(file.sourceText, {
    sourceType: 'module',
    ecmaVersion: 'latest',
    filePath: file.path,
    ecmaFeatures: {
      jsx: true,
    },
    vueFeatures: {
      filter: true,
      interpolationAsNonHTML: false,
    },
  }).ast
}

function walkAll(root, predicate, { skipNestedFunctions = false } = {}) {
  const matches = []
  const pending = [{ node: root, root: true }]
  const seen = new WeakSet()
  while (pending.length > 0) {
    const { node, root: isRoot } = pending.pop()
    if (!node || typeof node !== 'object' || seen.has(node)) continue
    seen.add(node)
    if (predicate(node)) matches.push(node)
    if (
      skipNestedFunctions &&
      !isRoot &&
      (node.type === 'FunctionDeclaration' ||
        node.type === 'FunctionExpression' ||
        node.type === 'ArrowFunctionExpression')
    ) {
      continue
    }
    for (const [key, value] of Object.entries(node)) {
      if (key === 'parent' || key === 'tokens' || key === 'comments') continue
      if (Array.isArray(value)) {
        for (const entry of value) pending.push({ node: entry, root: false })
      } else if (value && typeof value === 'object') {
        pending.push({ node: value, root: false })
      }
    }
  }
  return matches
}

function callsInFunction(functionNode) {
  return walkAll(
    functionNode.body,
    (node) => node.type === 'CallExpression',
    { skipNestedFunctions: true },
  )
}

function propertyName(property) {
  if (!property || property.type !== 'Property') return null
  if (property.key.type === 'Identifier') return property.key.name
  if (property.key.type === 'Literal' && typeof property.key.value === 'string') return property.key.value
  return null
}

function propertyNamed(object, name) {
  if (!object || object.type !== 'ObjectExpression') return null
  return object.properties.find((property) => propertyName(property) === name) || null
}

function literalString(node) {
  return node && node.type === 'Literal' && typeof node.value === 'string' ? node.value : null
}

function memberPath(node) {
  if (!node) return null
  if (node.type === 'ThisExpression') return 'this'
  if (node.type === 'Identifier') return node.name
  if (node.type !== 'MemberExpression') return null
  const object = memberPath(node.object)
  if (!object) return null
  if (!node.computed && node.property.type === 'Identifier') return `${object}.${node.property.name}`
  if (node.computed && node.property.type === 'Literal' && typeof node.property.value === 'string') {
    return `${object}.${node.property.value}`
  }
  return null
}

function defaultExportObject(file) {
  const declaration = file.ast.body.find((node) => node.type === 'ExportDefaultDeclaration')
  return declaration?.declaration?.type === 'ObjectExpression' ? declaration.declaration : null
}

function methodsOf(options) {
  const methods = propertyNamed(options, 'methods')
  if (!methods || methods.value.type !== 'ObjectExpression') return new Map()
  return new Map(
    methods.value.properties
      .map((property) => [propertyName(property), property.value])
      .filter(([name, value]) => name && value && value.body),
  )
}

function staticDataListUrl(options) {
  const data = propertyNamed(options, 'data')
  if (!data || !data.value || !data.value.body) return null
  const returns = walkAll(data.value.body, (node) => node.type === 'ReturnStatement', {
    skipNestedFunctions: true,
  })
  if (returns.length !== 1 || returns[0].argument?.type !== 'ObjectExpression') return null
  const url = propertyNamed(returns[0].argument, 'url')
  const list = url && propertyNamed(url.value, 'list')
  return list ? literalString(list.value) : null
}

function normalizedComponentTag(name) {
  return name
    .replace(/([a-z0-9])([A-Z])/g, '$1-$2')
    .replace(/_/g, '-')
    .toLowerCase()
}

function selected(filePath, roots) {
  return roots.some((root) => filePath === root || filePath.startsWith(`${root}/`))
}

function supported(filePath) {
  return SUPPORTED_EXTENSIONS.has(path.posix.extname(filePath))
}

function resolveModulePath(importer, specifier, aliases, filesByPath) {
  let candidate
  if (specifier.startsWith('.')) {
    candidate = path.posix.normalize(path.posix.join(path.posix.dirname(importer.path), specifier))
  } else {
    const alias = Object.keys(aliases)
      .filter((name) =>
        name.endsWith('/')
          ? specifier.startsWith(name)
          : specifier === name || specifier.startsWith(`${name}/`),
      )
      .sort((left, right) => right.length - left.length)[0]
    if (!alias || typeof aliases[alias] !== 'string') return null
    candidate = path.posix.normalize(`${aliases[alias]}${specifier.slice(alias.length)}`)
  }
  const candidates = [candidate]
  if (!path.posix.extname(candidate)) candidates.push(`${candidate}.vue`, `${candidate}.js`)
  const matches = candidates.filter((filePath) => filesByPath.has(filePath))
  return matches.length === 1 ? matches[0] : null
}

function importsFor(file, aliases, filesByPath) {
  const imports = new Map()
  for (const node of file.ast.body) {
    if (node.type !== 'ImportDeclaration' || typeof node.source.value !== 'string') continue
    const targetPath = resolveModulePath(file, node.source.value, aliases, filesByPath)
    for (const specifier of node.specifiers) {
      const importedName =
        specifier.type === 'ImportDefaultSpecifier'
          ? 'default'
          : specifier.type === 'ImportSpecifier'
            ? specifier.imported.name ?? specifier.imported.value
            : null
      if (importedName) imports.set(specifier.local.name, { importedName, targetPath })
    }
  }
  return imports
}

function localDeclaration(file, name) {
  for (const statement of file.ast.body) {
    if (statement.type === 'FunctionDeclaration' && statement.id?.name === name) return statement
    if (statement.type === 'VariableDeclaration') {
      const declarator = statement.declarations.find(
        (entry) => entry.id.type === 'Identifier' && entry.id.name === name,
      )
      if (declarator) return declarator.init
    }
  }
  return null
}

function localDeclarationStatement(file, name) {
  for (const statement of file.ast.body) {
    if (statement.type !== 'VariableDeclaration') continue
    if (statement.declarations.some((entry) => entry.id.type === 'Identifier' && entry.id.name === name)) {
      return statement
    }
  }
  return null
}

function declaredExport(file, exportedName) {
  if (!file.ast) return null
  for (const statement of file.ast.body) {
    if (statement.type === 'ExportDefaultDeclaration' && exportedName === 'default') {
      return statement.declaration
    }
    if (statement.type !== 'ExportNamedDeclaration') continue
    if (statement.declaration?.type === 'FunctionDeclaration' && statement.declaration.id?.name === exportedName) {
      return statement.declaration
    }
    if (statement.declaration?.type === 'VariableDeclaration') {
      const declarator = statement.declaration.declarations.find(
        (entry) => entry.id.type === 'Identifier' && entry.id.name === exportedName,
      )
      if (declarator) return declarator.init
    }
    const specifier = statement.specifiers?.find(
      (entry) => (entry.exported.name ?? entry.exported.value) === exportedName,
    )
    if (specifier) return localDeclaration(file, specifier.local.name)
  }
  return null
}

function componentChildren(file, filesByPath) {
  const options = defaultExportObject(file)
  const components = propertyNamed(options, 'components')
  if (!components || components.value.type !== 'ObjectExpression' || !file.ast.templateBody) return []
  const registered = new Map()
  for (const property of components.value.properties) {
    const componentName = propertyName(property)
    const localName = property.value?.type === 'Identifier' ? property.value.name : null
    const imported = localName && file.imports.get(localName)
    if (!componentName || !imported?.targetPath) continue
    const child = filesByPath.get(imported.targetPath)
    if (!child || declaredExport(child, imported.importedName)?.type !== 'ObjectExpression') continue
    registered.set(normalizedComponentTag(componentName), child)
  }
  const uses = []
  for (const element of walkAll(file.ast.templateBody, (node) => node.type === 'VElement')) {
    const child = registered.get(normalizedComponentTag(element.rawName ?? element.name ?? ''))
    if (!child) continue
    const refAttribute = element.startTag?.attributes?.find(
      (attribute) =>
        attribute.type === 'VAttribute' &&
        (attribute.key.name === 'ref' || attribute.key.name?.name === 'ref'),
    )
    const ref = refAttribute?.value?.value
    if (typeof ref === 'string' && ref.length > 0) uses.push({ ref, child })
  }
  return uses
}

function mixinMethodTarget(componentFile, methodName, filesByPath) {
  const options = defaultExportObject(componentFile)
  const mixins = propertyNamed(options, 'mixins')
  if (!mixins || mixins.value.type !== 'ArrayExpression') return null
  const candidates = []
  for (const entry of mixins.value.elements) {
    if (entry?.type !== 'Identifier') continue
    const imported = componentFile.imports.get(entry.name)
    if (!imported?.targetPath) continue
    const mixinFile = filesByPath.get(imported.targetPath)
    const mixinOptions = mixinFile && declaredExport(mixinFile, imported.importedName)
    const method = mixinOptions?.type === 'ObjectExpression' ? methodsOf(mixinOptions).get(methodName) : null
    if (method) candidates.push({ mixinFile, method, methodName })
  }
  return candidates.length === 1 ? candidates[0] : null
}

function axiosBaseUrl(requestFile, exportedAxiosName) {
  let localServiceName = null
  for (const statement of requestFile.ast.body) {
    if (statement.type !== 'ExportNamedDeclaration') continue
    const specifier = statement.specifiers?.find(
      (entry) => (entry.exported.name ?? entry.exported.value) === exportedAxiosName,
    )
    if (specifier) localServiceName = specifier.local.name
  }
  if (!localServiceName) return null
  const service = localDeclaration(requestFile, localServiceName)
  const serviceDeclaration = localDeclarationStatement(requestFile, localServiceName)
  if (service?.type !== 'CallExpression' || memberPath(service.callee) !== 'axios.create') return null
  const configuration = service.arguments[0]
  const baseURL = configuration && propertyNamed(configuration, 'baseURL')
  if (!baseURL) return null
  if (baseURL.value.type !== 'Identifier') {
    return {
      baseUrlExpression: sourceTextAt(requestFile, baseURL.value),
      baseUrlStaticFallback: null,
      call: service,
      sourceUnit: serviceDeclaration ?? fileFallbackUnit(requestFile),
      sourceUnitKind: serviceDeclaration ? 'STATIC_DECLARATION' : 'FILE_FALLBACK',
    }
  }
  const declaration = localDeclaration(requestFile, baseURL.value.name)
  if (declaration?.type === 'LogicalExpression' && declaration.operator === '||') {
    const fallback = literalString(declaration.right)
    if (fallback !== null) {
      return {
        baseUrlExpression: sourceTextAt(requestFile, declaration.left),
        baseUrlStaticFallback: fallback,
        call: service,
        sourceUnit: serviceDeclaration ?? fileFallbackUnit(requestFile),
        sourceUnitKind: serviceDeclaration ? 'STATIC_DECLARATION' : 'FILE_FALLBACK',
      }
    }
  }
  return {
    baseUrlExpression: sourceTextAt(requestFile, declaration ?? baseURL.value),
    baseUrlStaticFallback: null,
    call: service,
    sourceUnit: serviceDeclaration ?? fileFallbackUnit(requestFile),
    sourceUnitKind: serviceDeclaration ? 'STATIC_DECLARATION' : 'FILE_FALLBACK',
  }
}

function getActionPipeline(mixinFile, mixinMethod, filesByPath) {
  const candidates = []
  for (const call of callsInFunction(mixinMethod)) {
    if (call.callee.type !== 'Identifier' || call.callee.name !== 'getAction' || call.arguments.length < 1) continue
    const imported = mixinFile.imports.get(call.callee.name)
    if (!imported?.targetPath || imported.importedName !== 'getAction') continue
    const manageFile = filesByPath.get(imported.targetPath)
    const getAction = manageFile && declaredExport(manageFile, imported.importedName)
    if (!manageFile || !getAction?.params || getAction.params[0]?.type !== 'Identifier') continue
    const urlParameter = getAction.params[0].name
    for (const axiosCall of callsInFunction(getAction)) {
      if (axiosCall.callee.type !== 'Identifier' || axiosCall.arguments[0]?.type !== 'ObjectExpression') continue
      const axiosImport = manageFile.imports.get(axiosCall.callee.name)
      if (!axiosImport?.targetPath || axiosImport.importedName !== 'axios') continue
      const configuration = axiosCall.arguments[0]
      const url = propertyNamed(configuration, 'url')
      const method = propertyNamed(configuration, 'method')
      const httpMethod = literalString(method?.value)
      if (url?.value?.type !== 'Identifier' || url.value.name !== urlParameter || !httpMethod) continue
      const requestFile = filesByPath.get(axiosImport.targetPath)
      if (!requestFile || !declaredExport(requestFile, axiosImport.importedName)) continue
      const base = axiosBaseUrl(requestFile, axiosImport.importedName)
      if (!base) continue
      candidates.push({
        mixinFile,
        mixinMethod,
        getActionCall: call,
        manageFile,
        getAction,
        axiosCall,
        requestFile,
        base,
        httpMethod: httpMethod.toUpperCase(),
        rawUrlExpression: sourceTextAt(mixinFile, call.arguments[0]),
      })
    }
  }
  return candidates.length === 1 ? candidates[0] : null
}

function unitId(file, methodName) {
  return `${file.path}#${methodName}`
}

function segment(file, node, sourceUnit, sourceUnitKind, fromUnit, toUnit) {
  return {
    sourcePath: file.path,
    sourceHash: file.sourceHash,
    sourceRange: sourceRange(node),
    sourceUnitRange: sourceRange(sourceUnit),
    sourceUnitKind,
    fromUnit,
    toUnit,
  }
}

function argumentBindings(call, parameters, sourceFile) {
  return parameters.map((parameter, parameterIndex) => {
    const argument = call.arguments[parameterIndex]
    return argument
      ? {
          parameterIndex,
          parameterName: parameter.name,
          expression: sourceTextAt(sourceFile, argument),
          disposition: 'PASSED',
        }
      : { parameterIndex, parameterName: parameter.name, expression: null, disposition: 'NOT_PASSED' }
  })
}

function requestObservation({ page, instanceKey, pageCall, pageMethodName, pageMethod, urlOwner, loadCall, loadOwner, loadMethodName, loadMethod, pipeline, bindings }) {
  const pageSegment = segment(
    page,
    pageCall,
    pageMethod,
    'FUNCTION',
    unitId(page, pageMethodName),
    unitId(loadOwner, loadMethodName),
  )
  const loadSegment =
    page === loadOwner
      ? []
      : [
          segment(
            loadOwner,
            loadCall,
            loadMethod,
            'FUNCTION',
            unitId(loadOwner, loadMethodName),
            unitId(pipeline.mixinFile, pipeline.mixinMethodName),
          ),
        ]
  return {
    requestId: `${page.path}:${pageCall.range[0]}`,
    pagePath: page.path,
    pageSourceHash: page.sourceHash,
    instanceKey,
    sourceRange: sourceRange(pageCall),
    httpMethod: pipeline.httpMethod,
    rawUrlExpression: pipeline.rawUrlExpression,
    resolvedPath: staticDataListUrl(defaultExportObject(urlOwner)),
    baseUrlExpression: pipeline.base.baseUrlExpression,
    baseUrlStaticFallback: pipeline.base.baseUrlStaticFallback,
    wrapperPath: [
      pageSegment,
      ...loadSegment,
      segment(
        pipeline.mixinFile,
        pipeline.getActionCall,
        pipeline.mixinMethod,
        'FUNCTION',
        unitId(pipeline.mixinFile, pipeline.mixinMethodName),
        unitId(pipeline.manageFile, 'getAction'),
      ),
      segment(
        pipeline.manageFile,
        pipeline.axiosCall,
        pipeline.getAction,
        'FUNCTION',
        unitId(pipeline.manageFile, 'getAction'),
        unitId(pipeline.requestFile, 'axios'),
      ),
      segment(
        pipeline.requestFile,
        pipeline.base.call,
        pipeline.base.sourceUnit,
        pipeline.base.sourceUnitKind,
        unitId(pipeline.requestFile, 'axios'),
        'axios',
      ),
    ],
    argumentBindings: bindings,
  }
}

function childRequestChains(page, children, filesByPath) {
  const pageMethods = methodsOf(defaultExportObject(page))
  const observations = []
  for (const childUse of children) {
    const childOptions = defaultExportObject(childUse.child)
    const childMethods = methodsOf(childOptions)
    for (const [pageMethodName, pageMethod] of pageMethods) {
      for (const pageCall of callsInFunction(pageMethod)) {
        const callPath = memberPath(pageCall.callee)
        const prefix = `this.$refs.${childUse.ref}.`
        if (!callPath?.startsWith(prefix)) continue
        const childMethodName = callPath.slice(prefix.length)
        const childMethod = childMethods.get(childMethodName)
        if (!childMethod) continue
        for (const loadCall of callsInFunction(childMethod)) {
          const loadPath = memberPath(loadCall.callee)
          if (!loadPath?.startsWith('this.')) continue
          const loadMethodName = loadPath.slice('this.'.length)
          const mixinTarget = mixinMethodTarget(childUse.child, loadMethodName, filesByPath)
          if (!mixinTarget || !staticDataListUrl(childOptions)) continue
          const pipeline = getActionPipeline(mixinTarget.mixinFile, mixinTarget.method, filesByPath)
          if (!pipeline) continue
          pipeline.mixinMethodName = mixinTarget.methodName
          observations.push(
            requestObservation({
              page,
              instanceKey: `${page.path}#${childUse.ref}`,
              pageCall,
              pageMethodName,
              pageMethod,
              urlOwner: childUse.child,
              loadCall,
              loadOwner: childUse.child,
              loadMethodName: childMethodName,
              loadMethod: childMethod,
              pipeline,
              bindings: argumentBindings(pageCall, childMethod.params, page),
            }),
          )
        }
      }
    }
  }
  return observations
}

function directPageRequestChains(page, filesByPath) {
  const options = defaultExportObject(page)
  const methods = methodsOf(options)
  const observations = []
  if (!staticDataListUrl(options)) return observations
  for (const [pageMethodName, pageMethod] of methods) {
    for (const pageCall of callsInFunction(pageMethod)) {
      const methodPath = memberPath(pageCall.callee)
      if (!methodPath?.startsWith('this.')) continue
      const mixinMethodName = methodPath.slice('this.'.length)
      const mixinTarget = mixinMethodTarget(page, mixinMethodName, filesByPath)
      if (!mixinTarget) continue
      const pipeline = getActionPipeline(mixinTarget.mixinFile, mixinTarget.method, filesByPath)
      if (!pipeline) continue
      pipeline.mixinMethodName = mixinTarget.methodName
      observations.push(
        requestObservation({
          page,
          instanceKey: `${page.path}#default`,
          pageCall,
          pageMethodName,
          pageMethod,
          urlOwner: page,
          loadCall: pageCall,
          loadOwner: page,
          loadMethodName: mixinMethodName,
          loadMethod: pageMethod,
          pipeline,
          bindings: [],
        }),
      )
    }
  }
  return observations
}

function finiteStaticChains(filesByPath) {
  const components = [...filesByPath.values()]
    .filter((file) => file.ast && defaultExportObject(file))
    .sort((left, right) => left.path.localeCompare(right.path))
  const childUses = new Map(components.map((component) => [component.path, componentChildren(component, filesByPath)]))
  const childPaths = new Set([...childUses.values()].flatMap((uses) => uses.map((use) => use.child.path)))
  const observations = []
  for (const component of components) {
    observations.push(...childRequestChains(component, childUses.get(component.path), filesByPath))
    if (!childPaths.has(component.path)) observations.push(...directPageRequestChains(component, filesByPath))
  }
  return observations.sort(
    (left, right) =>
      left.pagePath.localeCompare(right.pagePath) ||
      left.sourceRange.startOffsetUtf16 - right.sourceRange.startOffsetUtf16,
  )
}

function validateRequest(request) {
  if (!request || request.schemaVersion !== REQUEST_SCHEMA || !Array.isArray(request.files)) {
    throw new Error('frontend syntax request is invalid')
  }
  for (const file of request.files) {
    if (
      !file ||
      typeof file.path !== 'string' ||
      typeof file.sourceHash !== 'string' ||
      typeof file.sourceText !== 'string' ||
      sourceHash(file.sourceText) !== file.sourceHash
    ) {
      throw new Error('SOURCE_HASH_MISMATCH')
    }
  }
}

function scan(request) {
  validateRequest(request)
  const selectedRoots = Array.isArray(request.selectedRoots) ? request.selectedRoots : []
  const aliases = request.aliases && typeof request.aliases === 'object' ? request.aliases : {}
  const files = request.files.map((file) => ({ ...file }))
  const filesByPath = new Map()
  for (const file of files) {
    if (filesByPath.has(file.path)) throw new Error('frontend syntax input has duplicate paths')
    filesByPath.set(file.path, file)
  }
  const records = []
  const diagnostics = []
  for (const file of files) {
    let status = selected(file.path, selectedRoots) && supported(file.path) ? 'PARSED' : 'NOT_INSPECTED'
    if (status === 'PARSED') {
      try {
        file.ast = parseFile(file)
      } catch {
        status = 'FAILED'
        diagnostics.push({
          code: 'SYNTAX_PARSE_FAILED',
          sourcePath: file.path,
          sourceHash: file.sourceHash,
          requestId: null,
          path: file.path,
        })
      }
    }
    records.push({
      schemaVersion: RESPONSE_SCHEMA,
      recordType: 'FILE',
      key: file.path,
      payload: { path: file.path, sourceHash: file.sourceHash, status },
    })
  }
  for (const file of files) {
    if (file.ast) file.imports = importsFor(file, aliases, filesByPath)
  }
  for (const observation of finiteStaticChains(filesByPath)) {
    records.push({
      schemaVersion: RESPONSE_SCHEMA,
      recordType: 'HTTP_REQUEST',
      key: observation.requestId,
      payload: observation,
    })
  }
  for (const diagnostic of diagnostics) {
    records.push({
      schemaVersion: RESPONSE_SCHEMA,
      recordType: 'DIAGNOSTIC',
      key: `${diagnostic.sourcePath}:${diagnostic.code}`,
      payload: diagnostic,
    })
  }
  return records
}

function main() {
  const input = fs.readFileSync(0, 'utf8')
  const requests = input.split(/\r?\n/).filter((line) => line.length > 0)
  for (const line of requests) {
    const records = scan(JSON.parse(line))
    for (const record of records) process.stdout.write(`${JSON.stringify(record)}\n`)
  }
}

try {
  main()
} catch (failure) {
  process.stderr.write(`${failure instanceof Error ? failure.message : String(failure)}\n`)
  process.exitCode = 1
}
