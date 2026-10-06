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

function methodProperty(options, name) {
  const methods = propertyNamed(options, 'methods')
  if (!methods || methods.value.type !== 'ObjectExpression') return null
  return methods.value.properties.find(
    (property) => propertyName(property) === name && property.value && property.value.body,
  ) || null
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

function staticDataUrl(options, propertyName) {
  const data = propertyNamed(options, 'data')
  if (!data || !data.value || !data.value.body) return null
  const returns = walkAll(data.value.body, (node) => node.type === 'ReturnStatement', {
    skipNestedFunctions: true,
  })
  if (returns.length !== 1 || returns[0].argument?.type !== 'ObjectExpression') return null
  const url = propertyNamed(returns[0].argument, 'url')
  const value = url && propertyNamed(url.value, propertyName)
  return value ? literalString(value.value) : null
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
    if (typeof ref === 'string' && ref.length > 0) uses.push({ ref, child, element })
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
    const sourceUnit = mixinOptions?.type === 'ObjectExpression'
      ? methodProperty(mixinOptions, methodName)
      : null
    if (method && sourceUnit) {
      candidates.push({ mixinFile, mixinOptions, method, sourceUnit, methodName, mixinUnitName: entry.name })
    }
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
  if (service?.type !== 'CallExpression' || !supportedAxiosCreate(requestFile, service.callee)) return null
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

function supportedAxiosCreate(file, callee) {
  if (memberPath(callee) === 'axios.create') return true
  if (
    callee?.type !== 'MemberExpression' ||
    callee.computed ||
    callee.property?.type !== 'Identifier' ||
    callee.property.name !== 'create' ||
    callee.object?.type !== 'Identifier'
  ) return false
  return file.ast.body.some(
    (statement) =>
      statement.type === 'ImportDeclaration' &&
      statement.source.value === 'axios' &&
      statement.specifiers.some(
        (specifier) =>
          specifier.type === 'ImportDefaultSpecifier' && specifier.local.name === callee.object.name,
      ),
  )
}

function getActionPipeline(mixinFile, mixinOptions, mixinMethod, filesByPath) {
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
        supportingQueryParamCalls: supportingQueryParamCalls(mixinOptions, mixinMethod),
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

function supportingSourceUnit(file, sourceUnit, sourceUnitKind) {
  return {
    sourcePath: file.path,
    sourceHash: file.sourceHash,
    sourceUnitRange: sourceRange(sourceUnit),
    sourceUnitKind,
  }
}

function supportingQueryParamCalls(mixinOptions, mixinMethod) {
  const targetProperty = methodProperty(mixinOptions, 'getQueryParams')
  if (!targetProperty) return []
  return callsInFunction(mixinMethod)
    .filter((call) => memberPath(call.callee) === 'this.getQueryParams')
    .map((call) => ({ call, target: targetProperty }))
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

function requestObservation({ page, instanceKey, pageCall, pageMethodName, pageSourceUnit, urlOwner, loadCall, loadOwner, loadMethodName, loadSourceUnit, pipeline, bindings }) {
  const pageSegment = segment(
    page,
    pageCall,
    pageSourceUnit,
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
            loadSourceUnit,
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
      ...pipeline.supportingQueryParamCalls.map(({ call }) =>
        segment(
          pipeline.mixinFile,
          call,
          pipeline.mixinMethodSourceUnit,
          'FUNCTION',
          unitId(pipeline.mixinFile, pipeline.mixinMethodName),
          `${pipeline.mixinUnitName}#getQueryParams`,
        ),
      ),
      segment(
        pipeline.mixinFile,
        pipeline.getActionCall,
        pipeline.mixinMethodSourceUnit,
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
    supportingSourceUnits: pipeline.supportingQueryParamCalls.map(({ target }) =>
      supportingSourceUnit(pipeline.mixinFile, target, 'FUNCTION'),
    ),
    argumentBindings: bindings,
  }
}

function childRequestChains(page, children, filesByPath) {
  const pageOptions = defaultExportObject(page)
  const pageMethods = methodsOf(pageOptions)
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
        const childSourceUnit = methodProperty(childOptions, childMethodName)
        if (!childMethod || !childSourceUnit) continue
        for (const loadCall of callsInFunction(childMethod)) {
          const loadPath = memberPath(loadCall.callee)
          if (!loadPath?.startsWith('this.')) continue
          const loadMethodName = loadPath.slice('this.'.length)
          const mixinTarget = mixinMethodTarget(childUse.child, loadMethodName, filesByPath)
          if (!mixinTarget || !staticDataListUrl(childOptions)) continue
          const pipeline = getActionPipeline(
            mixinTarget.mixinFile,
            mixinTarget.mixinOptions,
            mixinTarget.method,
            filesByPath,
          )
          if (!pipeline) continue
          pipeline.mixinMethodName = mixinTarget.methodName
          pipeline.mixinUnitName = mixinTarget.mixinUnitName
          pipeline.mixinMethodSourceUnit = mixinTarget.sourceUnit
          observations.push(
            requestObservation({
              page,
              instanceKey: `${page.path}#${childUse.ref}`,
              pageCall,
              pageMethodName,
              pageSourceUnit: methodProperty(pageOptions, pageMethodName),
              urlOwner: childUse.child,
              loadCall,
              loadOwner: childUse.child,
              loadMethodName: childMethodName,
              loadSourceUnit: childSourceUnit,
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
      const pipeline = getActionPipeline(
        mixinTarget.mixinFile,
        mixinTarget.mixinOptions,
        mixinTarget.method,
        filesByPath,
      )
      if (!pipeline) continue
      pipeline.mixinMethodName = mixinTarget.methodName
      pipeline.mixinUnitName = mixinTarget.mixinUnitName
      pipeline.mixinMethodSourceUnit = mixinTarget.sourceUnit
      observations.push(
        requestObservation({
          page,
          instanceKey: `${page.path}#default`,
          pageCall,
          pageMethodName,
          pageSourceUnit: methodProperty(options, pageMethodName),
          urlOwner: page,
          loadCall: pageCall,
          loadOwner: page,
          loadMethodName: mixinMethodName,
          loadSourceUnit: methodProperty(options, pageMethodName),
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

function methodParameters(method, file) {
  return (method?.params ?? []).map((parameter) => sourceTextAt(file, parameter))
}

function unitRef(file, node, sourceUnitKind) {
  return `${file.path}:${sourceUnitKind}:${node.range[0]}:${node.range[1] - node.range[0]}`
}

function sourceUnit(file, node, sourceUnitKind) {
  return {
    unitRef: unitRef(file, node, sourceUnitKind),
    sourcePath: file.path,
    sourceHash: file.sourceHash,
    sourceUnitRange: sourceRange(node),
    sourceUnitKind,
  }
}

function addContextUnit(units, file, node, sourceUnitKind) {
  const unit = sourceUnit(file, node, sourceUnitKind)
  if (!units.has(unit.unitRef)) units.set(unit.unitRef, unit)
  return unit.unitRef
}

function directiveEvent(attribute) {
  if (attribute?.type !== 'VAttribute' || !attribute.directive) return null
  if (attribute.key?.name?.name !== 'on') return null
  const event = attribute.key.argument
  return event?.type === 'VIdentifier' && typeof event.name === 'string' ? event.name : null
}

function templateEventBindings(childUse) {
  return (childUse.element?.startTag?.attributes ?? [])
    .map((attribute) => ({ attribute, eventName: directiveEvent(attribute) }))
    .filter(({ eventName }) => eventName !== null)
    .map(({ attribute, eventName }) => {
      const expression = attribute.value?.expression
      return {
        eventName,
        attribute,
        callbackName: expression?.type === 'Identifier' ? expression.name : null,
      }
    })
    .filter(({ callbackName }) => callbackName !== null)
}

function componentEmits(component) {
  const options = defaultExportObject(component)
  const emits = []
  for (const [methodName, method] of methodsOf(options)) {
    const sourceUnit = methodProperty(options, methodName)
    if (!sourceUnit) continue
    for (const call of callsInFunction(method)) {
      if (memberPath(call.callee) !== 'this.$emit') continue
      const eventName = literalString(call.arguments[0])
      if (!eventName) continue
      emits.push({
        eventName,
        call,
        methodName,
        method,
        sourceUnit,
        actualArguments: call.arguments.slice(1).map((argument) => sourceTextAt(component, argument)),
      })
    }
  }
  return emits
}

function directReturnCall(statement) {
  if (!statement) return null
  if (statement.type === 'ReturnStatement' && statement.argument?.type === 'CallExpression') {
    return statement.argument
  }
  if (statement.type === 'BlockStatement' && statement.body.length === 1) {
    return directReturnCall(statement.body[0])
  }
  return null
}

function saveActionPipeline(page, call, pageMethodName, pageSourceUnit, filesByPath) {
  if (call.callee.type !== 'Identifier') return null
  const imported = page.imports.get(call.callee.name)
  if (!imported?.targetPath) return null
  const client = filesByPath.get(imported.targetPath)
  const saveAction = client && declaredExport(client, imported.importedName)
  if (!client || !saveAction?.params || saveAction.params.length < 3) return null
  const httpMethod = literalString(call.arguments[1])
  if (!httpMethod || !['POST', 'PUT'].includes(httpMethod.toUpperCase())) return null
  const axiosCall = callsInFunction(saveAction).find(
    (candidate) =>
      candidate.callee.type === 'Identifier' &&
      candidate.arguments[0]?.type === 'ObjectExpression' &&
      client.imports.get(candidate.callee.name)?.importedName === 'axios',
  )
  if (!axiosCall) return null
  const axiosImport = client.imports.get(axiosCall.callee.name)
  const requestFile = axiosImport?.targetPath && filesByPath.get(axiosImport.targetPath)
  if (!requestFile || !declaredExport(requestFile, axiosImport.importedName)) return null
  const base = axiosBaseUrl(requestFile, axiosImport.importedName)
  if (!base) return null
  const urlArgument = call.arguments[0]
  const urlPath =
    urlArgument?.type === 'MemberExpression' && memberPath(urlArgument)?.startsWith('this.url.')
      ? staticDataUrl(defaultExportObject(page), memberPath(urlArgument).slice('this.url.'.length))
      : null
  const clientUnit =
    client.ast.body.find(
      (statement) =>
        statement.type === 'ExportNamedDeclaration' && statement.declaration?.type === 'FunctionDeclaration' &&
        statement.declaration.id?.name === imported.importedName,
    )?.declaration ?? fileFallbackUnit(client)
  return {
    requestId: `${page.path}:${call.range[0]}`,
    pagePath: page.path,
    pageSourceHash: page.sourceHash,
    instanceKey: `${page.path}#default`,
    sourceRange: sourceRange(call),
    httpMethod: httpMethod.toUpperCase(),
    rawUrlExpression: sourceTextAt(page, urlArgument),
    resolvedPath: urlPath,
    baseUrlExpression: base.baseUrlExpression,
    baseUrlStaticFallback: base.baseUrlStaticFallback,
    wrapperPath: [
      segment(
        page,
        call,
        pageSourceUnit,
        'FUNCTION',
        unitId(page, pageMethodName),
        unitId(client, imported.importedName),
      ),
      segment(
        client,
        axiosCall,
        clientUnit,
        'FUNCTION',
        unitId(client, imported.importedName),
        unitId(requestFile, 'axios'),
      ),
      segment(
        requestFile,
        base.call,
        base.sourceUnit,
        base.sourceUnitKind,
        unitId(requestFile, 'axios'),
        'axios',
      ),
    ],
    supportingSourceUnits: [],
    argumentBindings: argumentBindings(call, saveAction.params, page),
    _actualArguments: call.arguments.map((argument) => sourceTextAt(page, argument)),
    _pageMethodName: pageMethodName,
    _pageSourceUnit: pageSourceUnit,
    _client: client,
    _clientUnit: clientUnit,
  }
}

function directSaveRequests(page, filesByPath) {
  const options = defaultExportObject(page)
  const requests = []
  for (const [methodName, method] of methodsOf(options)) {
    const sourceUnit = methodProperty(options, methodName)
    if (!sourceUnit) continue
    for (const call of callsInFunction(method)) {
      const request = saveActionPipeline(page, call, methodName, sourceUnit, filesByPath)
      if (request) requests.push(request)
    }
  }
  return requests
}

function templateHandlers(page, eventName) {
  if (!page.ast.templateBody) return []
  const handlers = []
  for (const element of walkAll(page.ast.templateBody, (node) => node.type === 'VElement')) {
    for (const attribute of element.startTag?.attributes ?? []) {
      if (directiveEvent(attribute) !== eventName) continue
      const expression = attribute.value?.expression
      if (expression?.type === 'Identifier') handlers.push({ attribute, name: expression.name })
    }
  }
  return handlers.sort((left, right) => left.attribute.range[0] - right.attribute.range[0])
}

function methodCallsThis(functionNode, name) {
  return callsInFunction(functionNode).filter((call) => memberPath(call.callee) === `this.${name}`)
}

function variableInitializer(method, variableName) {
  for (const statement of method.body?.body ?? []) {
    if (statement.type !== 'VariableDeclaration') continue
    const declarator = statement.declarations.find(
      (candidate) => candidate.id?.type === 'Identifier' && candidate.id.name === variableName,
    )
    if (declarator?.init) return declarator.init
  }
  return null
}

function branchAssignment(ifStatement, variableName) {
  const statements = ifStatement?.consequent?.type === 'BlockStatement'
    ? ifStatement.consequent.body
    : [ifStatement?.consequent]
  return statements
    .map((statement) => {
      if (
        statement?.type !== 'ExpressionStatement' ||
        statement.expression?.type !== 'AssignmentExpression' ||
        statement.expression.operator !== '=' ||
        statement.expression.left?.type !== 'Identifier' ||
        statement.expression.left.name !== variableName
      ) return null
      return statement.expression
    })
    .find(Boolean) ?? null
}

function methodReturnCall(method) {
  const returns = (method?.body?.body ?? []).filter((statement) => statement.type === 'ReturnStatement')
  return returns.length === 1 ? directReturnCall(returns[0]) : null
}

function topLevelPromiseActionCall(method) {
  const calls = (method?.body?.body ?? [])
    .filter((statement) => statement.type === 'ExpressionStatement')
    .map((statement) => {
      let expression = statement.expression
      let hasThen = false
      while (
        expression?.type === 'CallExpression' &&
        expression.callee?.type === 'MemberExpression' &&
        !expression.callee.computed &&
        ['then', 'finally'].includes(expression.callee.property?.name)
      ) {
        hasThen ||= expression.callee.property.name === 'then'
        expression = expression.callee.object
      }
      return hasThen && expression?.type === 'CallExpression' && expression.callee.type === 'Identifier'
        ? expression
        : null
    })
    .filter(Boolean)
  return calls.length === 1 ? calls[0] : null
}

function supportedMixinHttpActionCall(method) {
  return methodReturnCall(method) ?? topLevelPromiseActionCall(method)
}

function resolvedPageUrl(page, expression) {
  const member = memberPath(expression)
  if (!member?.startsWith('this.url.')) return null
  return staticDataUrl(defaultExportObject(page), member.slice('this.url.'.length))
}

function exportedFunctionUnit(file, exportedName) {
  const statement = file.ast.body.find(
    (candidate) =>
      candidate.type === 'ExportNamedDeclaration' &&
      candidate.declaration?.type === 'FunctionDeclaration' &&
      candidate.declaration.id?.name === exportedName,
  )
  return statement?.declaration ?? fileFallbackUnit(file)
}

function mixinSaveActionRequests(page, mixinTarget, call, trigger, filesByPath) {
  if (!call || call.callee.type !== 'Identifier') return []
  if (call !== supportedMixinHttpActionCall(mixinTarget.method)) return []
  const imported = mixinTarget.mixinFile.imports.get(call.callee.name)
  if (!imported?.targetPath) return []
  const client = filesByPath.get(imported.targetPath)
  const httpAction = client && declaredExport(client, imported.importedName)
  if (!client || !httpAction?.params || httpAction.params.length !== 3 || call.arguments.length !== 3) return []
  const [urlArgument, parameterArgument, methodArgument] = call.arguments
  if (
    urlArgument?.type !== 'Identifier' ||
    parameterArgument?.type !== 'Identifier' ||
    methodArgument?.type !== 'Identifier'
  ) return []
  const initialUrl = variableInitializer(mixinTarget.method, urlArgument.name)
  const initialMethod = variableInitializer(mixinTarget.method, methodArgument.name)
  const conditions = (mixinTarget.method.body?.body ?? []).filter(
    (statement) => statement.type === 'IfStatement',
  )
  if (!initialUrl || literalString(initialMethod) === null || conditions.length !== 1) return []
  const condition = conditions[0]
  if (condition.alternate) return []
  const branchUrlAssignment = branchAssignment(condition, urlArgument.name)
  const branchMethodAssignment = branchAssignment(condition, methodArgument.name)
  if (!branchUrlAssignment || !branchMethodAssignment) return []
  const relevantAssignments = walkAll(
    mixinTarget.method.body,
    (node) =>
      node.type === 'AssignmentExpression' &&
      node.operator === '=' &&
      node.left?.type === 'Identifier' &&
      (node.left.name === urlArgument.name || node.left.name === methodArgument.name),
  )
  if (
    relevantAssignments.length !== 2 ||
    relevantAssignments.some(
      (assignment) => assignment !== branchUrlAssignment && assignment !== branchMethodAssignment,
    )
  ) return []
  const branchUrl = branchUrlAssignment.right
  const branchMethod = literalString(branchMethodAssignment.right)
  const defaultPath = resolvedPageUrl(page, initialUrl)
  const branchPath = resolvedPageUrl(page, branchUrl)
  if (!defaultPath || !branchPath || !branchMethod) return []
  const axiosCall = callsInFunction(httpAction).find(
    (candidate) =>
      candidate.callee.type === 'Identifier' &&
      candidate.arguments[0]?.type === 'ObjectExpression' &&
      client.imports.get(candidate.callee.name)?.importedName === 'axios',
  )
  if (!axiosCall) return []
  const axiosImport = client.imports.get(axiosCall.callee.name)
  const requestFile = axiosImport?.targetPath && filesByPath.get(axiosImport.targetPath)
  if (!requestFile || !declaredExport(requestFile, axiosImport.importedName)) return []
  const base = axiosBaseUrl(requestFile, axiosImport.importedName)
  if (!base) return []
  const clientUnit = exportedFunctionUnit(client, imported.importedName)
  const wrapperPath = [
    segment(
      mixinTarget.mixinFile,
      call,
      mixinTarget.sourceUnit,
      'FUNCTION',
      unitId(mixinTarget.mixinFile, mixinTarget.methodName),
      unitId(client, imported.importedName),
    ),
    segment(
      client,
      axiosCall,
      clientUnit,
      'FUNCTION',
      unitId(client, imported.importedName),
      unitId(requestFile, 'axios'),
    ),
    segment(
      requestFile,
      base.call,
      base.sourceUnit,
      base.sourceUnitKind,
      unitId(requestFile, 'axios'),
      'axios',
    ),
  ]
  const request = (resolvedPath, httpMethod, branch) => ({
    requestId: `${page.path}:${call.range[0]}:${branch}`,
    pagePath: page.path,
    pageSourceHash: page.sourceHash,
    instanceKey: `${page.path}#default`,
    sourceRange: sourceRange(trigger.attribute),
    httpMethod,
    rawUrlExpression: sourceTextAt(mixinTarget.mixinFile, urlArgument),
    resolvedPath,
    baseUrlExpression: base.baseUrlExpression,
    baseUrlStaticFallback: base.baseUrlStaticFallback,
    wrapperPath,
    supportingSourceUnits: [],
    argumentBindings: argumentBindings(call, httpAction.params, mixinTarget.mixinFile),
    _pageMethodName: null,
    _pageSourceUnit: null,
    _client: client,
    _clientUnit: clientUnit,
    _mixinRequest: mixinTarget,
    _wrapperKey: `${mixinTarget.mixinFile.path}:${call.range[0]}`,
    _condition: {
      sourceFile: mixinTarget.mixinFile,
      sourceUnit: mixinTarget.sourceUnit,
      expression: sourceTextAt(mixinTarget.mixinFile, condition.test),
      range: sourceRange(condition.test),
      branch,
    },
  })
  return [
    request(defaultPath, literalString(initialMethod).toUpperCase(), 'FALSE'),
    request(branchPath, branchMethod.toUpperCase(), 'TRUE'),
  ]
}

function mixinPromiseSaveShapes(page, filesByPath) {
  const shapes = []
  const seen = new Set()
  for (const trigger of templateHandlers(page, 'click')) {
    const initial = mixinMethodTarget(page, trigger.name, filesByPath)
    if (!initial) continue
    const candidates = [initial]
    for (const call of callsInFunction(initial.method)) {
      const targetName = memberPath(call.callee)?.startsWith('this.')
        ? memberPath(call.callee).slice('this.'.length)
        : null
      const target = targetName && mixinMethodTarget(page, targetName, filesByPath)
      if (target) candidates.push(target)
    }
    for (const candidate of candidates) {
      for (const promise of callsInFunction(candidate.method)) {
        if (
          promise.callee.type !== 'MemberExpression' ||
          promise.callee.computed ||
          promise.callee.property?.name !== 'then' ||
          promise.arguments[0]?.type !== 'ArrowFunctionExpression'
        ) continue
        const callback = promise.arguments[0]
        const requestCall = callsInFunction(callback).find((call) => {
          const name = memberPath(call.callee)?.startsWith('this.')
            ? memberPath(call.callee).slice('this.'.length)
            : null
          return name !== null && mixinMethodTarget(page, name, filesByPath) !== null
        })
        if (!requestCall) continue
        const requestName = memberPath(requestCall.callee).slice('this.'.length)
        const requestTarget = mixinMethodTarget(page, requestName, filesByPath)
        const requests = mixinSaveActionRequests(page, requestTarget, firstHttpCall(requestTarget), trigger, filesByPath)
        if (requests.length !== 2) continue
        const key = `${candidate.mixinFile.path}:${promise.range[0]}:${promise.range[1]}:${requestTarget.method.range[0]}`
        if (seen.has(key)) continue
        seen.add(key)
        const shape = { initial, candidate, promise, callback, requestTarget, requests }
        for (const request of requests) {
          request._mixinInitial = initial
          request._mixinCandidate = candidate
          request._mixinPromise = promise
          request._mixinCallback = callback
        }
        shapes.push(shape)
      }
    }
  }
  return shapes
}

function firstHttpCall(mixinTarget) {
  return supportedMixinHttpActionCall(mixinTarget.method)
}

function saveConditions(page, saveRequests) {
  const explicitConditions = saveRequests
    .filter((request) => request._condition)
    .map((request) => ({
      requestId: request.requestId,
      sourceFile: request._condition.sourceFile,
      sourceUnit: request._condition.sourceUnit,
      expression: request._condition.expression,
      branch: request._condition.branch,
      range: request._condition.range,
    }))
  const requestsByOffset = new Map(saveRequests.map((request) => [request.sourceRange.startOffsetUtf16, request]))
  const conditions = [...explicitConditions]
  const options = defaultExportObject(page)
  for (const [methodName, method] of methodsOf(options)) {
    const sourceUnit = methodProperty(options, methodName)
    if (!sourceUnit || method.body?.type !== 'BlockStatement') continue
    const statements = method.body.body
    for (let index = 0; index < statements.length; index += 1) {
      const statement = statements[index]
      if (statement.type !== 'IfStatement') continue
      const trueCall = directReturnCall(statement.consequent)
      const falseCall = directReturnCall(statement.alternate) ?? directReturnCall(statements[index + 1])
      const expression = sourceTextAt(page, statement.test)
      const unit = sourceUnit
      for (const [call, branch] of [[trueCall, 'TRUE'], [falseCall, 'FALSE']]) {
        const request = call && requestsByOffset.get(call.range[0])
        if (!request) continue
        conditions.push({
          requestId: request.requestId,
          methodName,
          sourceFile: page,
          sourceUnit: unit,
          expression,
          branch,
          range: sourceRange(statement.test),
        })
      }
    }
  }
  return conditions
}

function promiseCallbacks(page, units) {
  const options = defaultExportObject(page)
  const observations = []
  for (const [methodName, method] of methodsOf(options)) {
    const parentUnit = methodProperty(options, methodName)
    if (!parentUnit) continue
    for (const call of callsInFunction(method)) {
      if (
        call.callee.type !== 'MemberExpression' ||
        call.callee.computed ||
        call.callee.property?.name !== 'then' ||
        !['ArrowFunctionExpression', 'FunctionExpression'].includes(call.arguments[0]?.type)
      ) continue
      const callback = call.arguments[0]
      const fromUnitRef = addContextUnit(units, page, parentUnit, 'FUNCTION')
      const toUnitRef = addContextUnit(units, page, callback, 'FUNCTION')
      observations.push({
        observationId: `${page.path}:promise:${call.range[0]}:${call.range[1]}`,
        kind: 'PROMISE_CALLBACK',
        fromUnitRef,
        toUnitRef,
        callRange: sourceRange(call),
        eventName: null,
        actualArguments: [],
        formalParameters: methodParameters(callback, page),
        argumentBindings: [],
        detail: 'literal Promise.then callback',
      })
    }
  }
  return observations
}

function directCalls(page, childUse, units) {
  const options = defaultExportObject(page)
  const childOptions = defaultExportObject(childUse.child)
  const childMethods = methodsOf(childOptions)
  const observations = []
  for (const [methodName, method] of methodsOf(options)) {
    const sourceUnit = methodProperty(options, methodName)
    if (!sourceUnit) continue
    for (const call of callsInFunction(method)) {
      const path = memberPath(call.callee)
      const prefix = `this.$refs.${childUse.ref}.`
      if (!path?.startsWith(prefix)) continue
      const targetName = path.slice(prefix.length)
      const target = childMethods.get(targetName)
      const targetUnit = methodProperty(childOptions, targetName)
      if (!target || !targetUnit) continue
      observations.push({
        observationId: `${page.path}:direct:${call.range[0]}`,
        kind: 'DIRECT_CALL',
        fromUnitRef: addContextUnit(units, page, sourceUnit, 'FUNCTION'),
        toUnitRef: addContextUnit(units, childUse.child, targetUnit, 'FUNCTION'),
        callRange: sourceRange(call),
        eventName: null,
        actualArguments: call.arguments.map((argument) => sourceTextAt(page, argument)),
        formalParameters: methodParameters(target, childUse.child),
        argumentBindings: argumentBindings(call, target.params, page),
        detail: 'literal component ref method call',
      })
    }
  }
  return observations
}

function pageContext(page, childUse, contextRequests, saveRequestList, filesByPath) {
  const pageOptions = defaultExportObject(page)
  const pageMethods = methodsOf(pageOptions)
  const units = new Map()
  const observations = []
  const emits = componentEmits(childUse.child)
  const bindings = templateEventBindings(childUse)
  for (const binding of bindings) {
    const callback = pageMethods.get(binding.callbackName)
    const callbackUnit = methodProperty(pageOptions, binding.callbackName)
    if (!callback || !callbackUnit) continue
    const emit = emits.find((candidate) => candidate.eventName === binding.eventName)
    if (!emit) continue
    const templateRef = addContextUnit(units, page, childUse.element, 'TEMPLATE')
    const callbackRef = addContextUnit(units, page, callbackUnit, 'FUNCTION')
    const emitRef = addContextUnit(units, childUse.child, emit.sourceUnit, 'FUNCTION')
    const bindingsForCallback = callback.params.map((parameter, parameterIndex) => {
      const argument = emit.call.arguments[parameterIndex + 1]
      return argument
        ? {
            parameterIndex,
            parameterName: sourceTextAt(page, parameter),
            expression: sourceTextAt(childUse.child, argument),
            disposition: 'PASSED',
          }
        : {
            parameterIndex,
            parameterName: sourceTextAt(page, parameter),
            expression: null,
            disposition: 'NOT_PASSED',
          }
    })
    observations.push(
      {
        observationId: `${page.path}:template:${binding.attribute.range[0]}`,
        kind: 'TEMPLATE_EVENT_BINDING',
        fromUnitRef: templateRef,
        toUnitRef: callbackRef,
        callRange: sourceRange(binding.attribute),
        eventName: binding.eventName,
        actualArguments: [],
        formalParameters: methodParameters(callback, page),
        argumentBindings: [],
        detail: 'literal template event binding',
      },
      {
        observationId: `${childUse.child.path}:emit:${emit.call.range[0]}`,
        kind: 'COMPONENT_EMIT',
        fromUnitRef: emitRef,
        toUnitRef: null,
        callRange: sourceRange(emit.call),
        eventName: binding.eventName,
        actualArguments: emit.actualArguments,
        formalParameters: [],
        argumentBindings: [],
        detail: 'literal component emit',
      },
      {
        observationId: `${page.path}:callback:${binding.attribute.range[0]}`,
        kind: 'EVENT_CALLBACK_BINDING',
        fromUnitRef: templateRef,
        toUnitRef: callbackRef,
        callRange: sourceRange(binding.attribute),
        eventName: binding.eventName,
        actualArguments: emit.actualArguments,
        formalParameters: methodParameters(callback, page),
        argumentBindings: bindingsForCallback,
        detail: 'same literal component instance and event',
      },
    )
  }
  if (observations.length === 0) return null
  observations.push(...directCalls(page, childUse, units), ...promiseCallbacks(page, units))
  if (saveRequestList.length > 0) {
    const data = propertyNamed(pageOptions, 'data')
    if (data?.value?.body) addContextUnit(units, page, data.value, 'FUNCTION')
  }
  const handledMixinWrappers = new Set()
  for (const save of saveRequestList.filter((request) => request._mixinRequest)) {
    if (handledMixinWrappers.has(save._wrapperKey)) continue
    handledMixinWrappers.add(save._wrapperKey)
    const mixinRequest = save._mixinRequest
    const initialMixin = save._mixinInitial.mixinFile
    const promiseMixin = save._mixinCandidate.mixinFile
    const requestUnit = addContextUnit(units, mixinRequest.mixinFile, mixinRequest.sourceUnit, 'FUNCTION')
    addContextUnit(units, initialMixin, save._mixinInitial.sourceUnit, 'FUNCTION')
    const promiseFromUnit = addContextUnit(units, promiseMixin, save._mixinCandidate.sourceUnit, 'FUNCTION')
    const promiseToUnit = addContextUnit(units, promiseMixin, save._mixinCallback, 'FUNCTION')
    const clientUnit = addContextUnit(units, save._client, save._clientUnit, 'FUNCTION')
    const pageOptions = defaultExportObject(page)
    for (const callbackCall of callsInFunction(save._mixinCallback)) {
      const targetName = memberPath(callbackCall.callee)?.startsWith('this.')
        ? memberPath(callbackCall.callee).slice('this.'.length)
        : null
      const targetUnit = targetName && methodProperty(pageOptions, targetName)
      if (targetUnit) addContextUnit(units, page, targetUnit, 'FUNCTION')
    }
    observations.push(
      {
        observationId: `${promiseMixin.path}:promise:${save._mixinPromise.range[0]}:${save._mixinPromise.range[1]}`,
        kind: 'PROMISE_CALLBACK',
        fromUnitRef: promiseFromUnit,
        toUnitRef: promiseToUnit,
        callRange: sourceRange(save._mixinPromise),
        eventName: null,
        actualArguments: [],
        formalParameters: methodParameters(save._mixinCallback, promiseMixin),
        argumentBindings: [],
        detail: 'literal mixin Promise.then callback',
      },
      {
        observationId: `${mixinRequest.mixinFile.path}:http-wrapper:${save._wrapperKey}`,
        kind: 'HTTP_WRAPPER_CALL',
        fromUnitRef: requestUnit,
        toUnitRef: clientUnit,
        callRange: save.wrapperPath[0].sourceRange,
        eventName: null,
        actualArguments: save.argumentBindings.map((binding) => binding.expression),
        formalParameters: save.argumentBindings.map((binding) => binding.parameterName),
        argumentBindings: save.argumentBindings,
        detail: 'literal imported three-argument HTTP wrapper call',
      },
    )
  }
  const handledWrappers = new Set()
  for (const save of saveRequestList) {
    const wrapperKey = save._wrapperKey ?? save.requestId
    if (save._mixinRequest || handledWrappers.has(wrapperKey)) continue
    handledWrappers.add(wrapperKey)
    observations.push({
      observationId: `${page.path}:http-wrapper:${save.sourceRange.startOffsetUtf16}`,
      kind: 'HTTP_WRAPPER_CALL',
      fromUnitRef: addContextUnit(units, page, save._pageSourceUnit, 'FUNCTION'),
      toUnitRef: addContextUnit(units, save._client, save._clientUnit, 'FUNCTION'),
      callRange: save.sourceRange,
      eventName: null,
      actualArguments: save._actualArguments,
      formalParameters: save.argumentBindings.map((binding) => binding.parameterName),
      argumentBindings: save.argumentBindings,
      detail: 'literal imported save wrapper call',
    })
  }
  const conditions = saveConditions(page, saveRequestList).map((condition) => ({
    requestId: condition.requestId,
    unitRef: addContextUnit(units, condition.sourceFile, condition.sourceUnit, 'FUNCTION'),
    range: condition.range,
    expression: condition.expression,
    branch: condition.branch,
  }))
  const requestIds = contextRequests.map((request) => request.requestId).sort()
  const stable = JSON.stringify({
    pagePath: page.path,
    sourceSha256: page.sourceHash,
    instanceKey: `${page.path}#${childUse.ref}`,
    requestIds,
    observations,
    requestConditions: conditions,
  })
  return {
    context: {
      contextId: `page-context:${sourceHash(stable)}`,
      pagePath: page.path,
      sourceSha256: page.sourceHash,
      instanceKey: `${page.path}#${childUse.ref}`,
      requestIds,
      sourceUnits: [...units.values()],
      observations,
      requestConditions: conditions,
      limitations: [],
    },
    requests: contextRequests,
  }
}

function finitePageContexts(filesByPath, existingRequests) {
  const components = [...filesByPath.values()]
    .filter((file) => file.ast && defaultExportObject(file))
    .sort((left, right) => left.path.localeCompare(right.path))
  const childUses = new Map(components.map((component) => [component.path, componentChildren(component, filesByPath)]))
  const saveRequests = components.flatMap((page) => {
    if (childUses.get(page.path).length === 0) return []
    return [...directSaveRequests(page, filesByPath), ...mixinPromiseSaveShapes(page, filesByPath).flatMap((shape) => shape.requests)]
  })
  const contexts = []
  for (const page of components) {
    for (const childUse of childUses.get(page.path)) {
      const childInstanceKey = `${page.path}#${childUse.ref}`
      const pageDefaultInstanceKey = `${page.path}#default`
      const pageRequests = existingRequests.filter(
        (request) =>
          request.instanceKey === childInstanceKey || request.instanceKey === pageDefaultInstanceKey,
      )
      const pageSaveRequests = saveRequests.filter((request) => request.pagePath === page.path)
      const result = pageContext(page, childUse, [...pageRequests, ...pageSaveRequests], pageSaveRequests, filesByPath)
      if (!result) continue
      contexts.push(result.context)
    }
  }
  const normalizedRequests = [
    ...existingRequests,
    ...saveRequests,
  ].sort(
    (left, right) =>
      left.pagePath.localeCompare(right.pagePath) ||
      left.sourceRange.startOffsetUtf16 - right.sourceRange.startOffsetUtf16 ||
      left.requestId.localeCompare(right.requestId),
  )
  return { requests: normalizedRequests, contexts }
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
  const finite = finitePageContexts(filesByPath, finiteStaticChains(filesByPath))
  for (const observation of finite.requests) {
    const publicObservation = Object.fromEntries(
      Object.entries(observation).filter(([name]) => !name.startsWith('_')),
    )
    records.push({
      schemaVersion: RESPONSE_SCHEMA,
      recordType: 'HTTP_REQUEST',
      key: publicObservation.requestId,
      payload: publicObservation,
    })
  }
  for (const context of finite.contexts) {
    records.push({
      schemaVersion: RESPONSE_SCHEMA,
      recordType: 'PAGE_CONTEXT',
      key: context.contextId,
      payload: context,
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
