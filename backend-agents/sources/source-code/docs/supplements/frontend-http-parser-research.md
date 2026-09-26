# Vue 页面到 Spring HTTP 入口的静态解析器调研

状态：**研究结论与设计建议；未做原型验证，未实现，未运行客户代码。** 本文服务于 Step02 的 Vue 页面→HTTP 请求→Spring Controller 正式关联，以及 Step05 在既有 Java 方法、Mapper/XML、SQL 材料旁组装页面来源。它不修改现行 Step02/Step05 合同。本次目标来源是 Source Agent 已保存的 `snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c`（`.workspace/jsherp-full-parallel-20260913/capture/snapshots/`，capture receipt 记录 commit `8c30ce7861570458920175e200bb2a6442713580`）。先前只读的 `modeling-evidence/.../guanyijia-demo-content-v7-20260827/sources/github/source/` 是**另一个**资料快照；下面只对已逐文件核对字节相同的文件复用其行号，不能把两个快照整体等同，也不能把人工读取写成程序输出。

## 结论和最小工具组合

建议先用**一个固定版本、独立安装的 Node 静态解析 helper**验证有限夹具。Java Agent 仍掌管冻结文件准入、输出合同、持久化和与现有 JDT Controller/Java/Mapper 材料的关联；Node helper 只把准入的 `.vue`、`.js`，以后必要时 `.ts`，转成带位置和诊断的语法/局部关系记录。helper 应只读源文本和配置文本，只调用解析 API，不 `require`、`import`、编译或执行客户模块、模板表达式、构建脚本，也不安装或构建客户项目。独立 Node 工具进程与现有独立 JDT 工具 JVM 是类似的进程边界，**不是**声称二者有现成的跨语言链接 API；进程调用与资源限制是拟议架构，未验证。

首个 Vue 2.7/普通 JavaScript 验证组合推荐**直接依赖一个 `vue-eslint-parser`**：`parseForESLint(source, { filePath, sourceType: 'module', ecmaVersion, vueFeatures })` 对 `.vue` 返回 `result.ast` 中的脚本 Program 与 `result.ast.templateBody`，对 `.js` 也走其内部默认的 `espree` 脚本解析路径；调用方无需另外直接调用 `espree`。该准确调用形态由 Vue 组织的实现源码确认，正式锁版后须用夹具再确认当版接口。[parseForESLint 与 `.vue`/`.js` 分流源码](https://github.com/vuejs/vue-eslint-parser/blob/master/src/index.ts)、[vue-eslint-parser README](https://github.com/vuejs/vue-eslint-parser#readme)。其模板 AST 有 `VExpressionContainer`、`VOnExpression`、`VSlotScopeExpression` 等节点，供识别事件、Vue 2 slot scope 等语法；这仍只是**语法输入能力**，HTTP 封装追踪和后端匹配需本 Agent 编写。[模板 AST 规格](https://github.com/vuejs/vue-eslint-parser/blob/master/docs/ast.md)。

首版必须针对 Vue 2 显式测试 `vueFeatures.filter`、`vueFeatures.interpolationAsNonHTML`、`slot-scope`/`v-slot`、Options API `mixins`，并固定选择值。文档明确 filter 和插值模式会改变 Vue 2/3 合法模板的解析结果；slot scope 在 AST 规格中有专门节点。不要用一次“能 parse”代替这些语义夹具。[Vue 模板选项](https://github.com/vuejs/vue-eslint-parser#parseroptionsvuefeatures)、[Vue 模板 AST](https://github.com/vuejs/vue-eslint-parser/blob/master/docs/ast.md)。

未来有真实 TS 文件时，候选是给 `vue-eslint-parser` 的 `ts` 脚本项接 `@typescript-eslint/parser`，独立 TS 文件也用同一解析器；先只取语法 AST，不默认启动类型项目服务。其官方文档把语法解析与需要 `project`/`projectService` 的类型信息区分开，且受 Node/TypeScript 兼容窗口约束；最终版本要对 helper 的部署环境锁定后再选。[vue-eslint-parser 脚本解析器配置](https://github.com/vuejs/vue-eslint-parser#parseroptionsparser)、[@typescript-eslint/parser](https://typescript-eslint.io/packages/parser/)、[依赖版本窗口](https://typescript-eslint.io/users/dependency-versions/)。

`@babel/parser` 可作为**备用 JS/TS 语法解析方案**：`parse`/`parseExpression` 接收源码，提供 TypeScript/JSX 等插件和位置；默认输出 Babel AST（与 ESTree 有差异，`estree` 插件可转换部分差异）。它不是 `vue-eslint-parser` 的脚本解析器接口的直接等价替换；如遇 `espree` 无法解析的冻结 JS，先验证 `@babel/eslint-parser` 作为其脚本 parser 的单一替换路径，不同时引入平行 Babel AST 关系引擎。[Babel parser API、AST 差异与插件](https://babeljs.io/docs/babel-parser)、[Vue 脚本解析器接口](https://github.com/vuejs/vue-eslint-parser#parseroptionsparser)、[Babel ESLint parser](https://babeljs.io/docs/babel-eslint-parser)。TypeScript 原生 Compiler API 的 `createSourceFile` 和 `Program`/`CompilerHost` 也是成熟候选，但 `Program` 是项目级能力，当前静态 URL/调用识别未显示非它不可；如将来要求类型级解析，应单独验证其文件读取和模块解析边界。[TypeScript 官方 Compiler API Wiki](https://github.com/microsoft/TypeScript/wiki/Using-the-Compiler-API)。

## Vue 版本不能混同

| 输入版本 | 官方可确认能力 | 本任务的取舍 |
| --- | --- | --- |
| Vue 3 | `vue/compiler-sfc`（底层 `@vue/compiler-sfc`）提供 `parse(source, options)`，返回 descriptor/errors；descriptor 有 template、script、scriptSetup、块位置，模板可带 AST。Vue 官方建议通过 `vue/compiler-sfc` 使用，以保持 compiler 和 runtime 版本同步。[Vue tooling](https://vuejs.org/guide/scaling-up/tooling.html#vue-compiler-sfc)、[Vue compiler-sfc parse 源码](https://github.com/vuejs/core/blob/main/packages/compiler-sfc/src/parse.ts)、[SFC 规范](https://vuejs.org/api/sfc-spec.html)。 | 可作 Vue 3 版本忠实的块/模板解析候选；不要在未验证下套用于 Vue 2 模板。`<script setup>` 有独立作用域与编译期宏语义，单看原始 script AST 不能当成已编译运行逻辑。[Vue `<script setup>`](https://vuejs.org/api/sfc-script-setup.html)。 |
| Vue 2.7 | Vue 官方变更记录列出 2.7 的 `<script setup>` backport，并说明可移除 `vue-template-compiler`；这是 Vue 2.7 自身工具路线，不能据此推断 Vue 3 compiler 的解析语义等同。[Vue 2.7 changelog](https://github.com/vuejs/vue/blob/main/CHANGELOG.md#270-2022-07-01)。 | 固定 snapshot 的 `jshERP-web/package.json:27,54` 声明 `vue: ^2.7.16`、`vue-template-compiler: ^2.6.10`。范围声明并不证明实际安装版本，也不证明两个包可配合；先用 helper 自己锁定的解析依赖及真实夹具验证。 |
| Vue 2.6 及更早 Vue 2 | 官方 `vue-template-compiler.parseComponent(file, options)` 把 SFC 拆成 descriptor；`pad` 可保留位置。其入口源码对与 `vue` 的版本不一致会报错。`parseComponent` 本身未承诺输出模板事件调用图。[Vue 2 compiler README](https://github.com/vuejs/vue/blob/dev/packages/vue-template-compiler/README.md#compilerparsecomponentfile-options)、[Vue 2 compiler 版本检查源码](https://github.com/vuejs/vue/blob/dev/packages/vue-template-compiler/index.js)。 | 若以后需要版本忠实的 Vue 2 块拆分可考虑它，且需同版依赖；它不能单独完成本文要的 `@click`→方法关系。不要用 `compile`/`compileToFunctions` 获取关系。 |

首选的 `vue-eslint-parser` 有 Vue 2 filter 与模板插值模式选项；文档明确一些 Vue 2 合法模板在 Vue 3 模式下不能解析。因此 helper 必须记录输入仓库的已知 Vue 主版本并显式配置，未知版本不能默默按 Vue 3 处理。插件当前发布元数据列出 Node 引擎和 ESLint peer 约束，但这不是本机或未来工具环境已经满足的证据；实现前选定并锁定一个经过夹具验证的版本组合。[Vue 模板选项](https://github.com/vuejs/vue-eslint-parser#parseroptionsvuefeatures)、[vue-eslint-parser package 元数据](https://github.com/vuejs/vue-eslint-parser/blob/master/package.json)。

## 固定 snapshot 中确实存在的链（人工只读核对）

以下八个目标文件在固定 snapshot 的 `snapshot-manifest.jsonl` 中均为 `ANALYZABLE_TEXT`：`PurchaseOrderModal.vue`、`LinkBillList.vue`、`JEditableTableMixin.js`、`JeecgListMixin.js`、`BillModalMixin.js`、`api/manage.js`、`utils/request.js`、`DepotHeadController.java`。逐项比较 manifest 的 SHA-256 与另一个 V7 资料快照对应文件的实际 SHA-256，**八项均相同**，因此下表这些文件的源行号对固定 snapshot 也成立；比较未覆盖其他文件，更未证明两个快照同一身份。manifest 路径为 `.workspace/jsherp-full-parallel-20260913/capture/snapshots/snapshot:8712a1c127b4bbb5ab7c2cb317ef1cdab0f582a0473514b88c172e2b27888f9c/snapshot-manifest.jsonl`。下表路径从该 snapshot 的仓库根开始，不能把“人工读到”表述为“工具已发现”。

| 源文件/行 | 可观察的静态事实 | 它要求链接器做什么 |
| --- | --- | --- |
| `jshERP-web/src/views/bill/modules/PurchaseOrderModal.vue:20-21,194-210,307-312` | 保存/保存并审核按钮的 `@click`；`export default` 注册 `JEditableTableMixin`、`BillModalMixin`；`data().url.add/edit` 分别是 `/depotHead/addDepotHeadAndDetail`、`/depotHead/updateDepotHeadAndDetail`。 | 将模板事件名、组件本地/导入的 mixin 方法、`this.url` 的静态属性值跨文件关联，同时保存每一段位置。 |
| `jshERP-web/src/views/bill/mixins/BillModalMixin.js:1181-1190` 与 `src/mixins/JEditableTableMixin.js:95-101,131-140` | `handleOkOnly/handleOkAndCheck` 设置单据状态并调用 `handleOk`；继承的处理最终调用 `request(formData)`；`request` 根据 `this.model.id` 在 add/post 与 edit/put 间分支。 | 记录同一 UI 操作的两条条件候选；条件是客户端 `model.id`，不是无条件“总会发 POST”。Vue 2 官方说明 mixin 的方法合并，组件自身同名方法优先，因此静态解析还需处理覆盖与冲突。[Vue 2 mixin 合并规则](https://v2.vuejs.org/v2/guide/mixins.html)。 |
| `jshERP-web/src/api/manage.js:17-28`，`src/utils/request.js:15-23,100-112` | `httpAction` 把形参写入 Axios config 的 `url`、`method`、`data`；请求工具建立带 `baseURL` 的 Axios 实例，取 `window._CONFIG['domianURL']` 或字面量 `/jshERP-boot`。 | 沿实际 import/export、调用实参→封装形参→Axios config 传播，保留 URL path、HTTP verb 和分支来源。Axios 官方将 `url`/`method`/`baseURL` 定义为请求配置。[Axios request config](https://github.com/axios/axios/blob/v1.x/docs/pages/advanced/request-config.md)。 |
| `jshERP-boot/src/main/java/com/jsh/erp/controller/DepotHeadController.java:43,613-636` | 类级 `/depotHead`；方法级 POST `/addDepotHeadAndDetail` 与 PUT `/updateDepotHeadAndDetail`，方法体进入 service。 | 复用 Step02 已保存的 Spring route/methodCondition，而不是 Node 自行解释 Java；把前端候选 method+path 与后端 route 候选精确匹配，歧义或未知显式留下。 |
| `jshERP-web/src/views/bill/modules/PurchaseOrderModal.vue:181,417-421`、`src/views/bill/dialog/LinkBillList.vue:115-126,209-210,315-320`、`src/mixins/JeecgListMixin.js:90-101` | 父页面引用 `link-bill-list` 并订阅 `@ok`，子组件 `$emit('ok', ...)`；子组件混入的 `loadData` 经 `getAction(this.url.list, ...)` 读取 `/depotHead/list`，后端 Controller `:77` 是 GET `/list`。 | 保留组件事件/props/refs 的中间证据，区分“父页面可达子组件调用”与“用户在该次操作一定触发了请求”。Vue 官方文档说明 `v-on` 方法事件和组件 `$emit`；解析语法不验证运行时分支。[Vue 2 事件处理](https://v2.vuejs.org/v2/guide/events.html)、[Vue 2 组件事件](https://v2.vuejs.org/v2/guide/components-custom-events.html)。 |

固定 snapshot 自身另有配置证据：`jshERP-web/public/index.html:258-259` 将 `window._CONFIG['domianURL']` 赋为 `/jshERP-boot`；`jshERP-boot/src/main/resources/application.yml:2-9` 把 Spring `context-path` 设为同值；`jshERP-web/vue.config.js:50-57` 的开发代理按该前缀转发到本地服务。这些只说明**该冻结配置中的一个明确分支**，不证明运行部署时没有覆盖或改写。配置文件来自固定 snapshot 的 manifest/blobs；未拿 V7 配置替代。

## 从 AST 到可发布关系所需的自有规则

解析器只回答“源码长什么样”。它不提供仓库的 import alias 解析、Vue options/mixin 合并、`this.url` 取值传播、函数调用/参数传递、Axios wrapper 语义、运行时配置、Spring 路由条件、Java 方法导航或 Mapper/SQL 绑定。官方解析器文档分别只定义 SFC/模板或 JS/TS AST API；由此得出“不是现成端到端链接器”是**本设计推论**，不是某产品声称不能扩展。[vue-eslint-parser README](https://github.com/vuejs/vue-eslint-parser#readme)、[Vue SFC parse 源码](https://github.com/vuejs/core/blob/main/packages/compiler-sfc/src/parse.ts)、[Babel parser 文档](https://babeljs.io/docs/babel-parser)。

建议按可解释的有限规则产出关系和 Gap：

1. **文件与组件。** Step01 已将固定 snapshot 中 Vue/JS 文件列为可分析文本，文本准入不是本次尚待发明的能力；前端语法解释和正式关系发布才是拟议增量。对已准入文本记录文件 SHA/相对路径/行列，解析 `.vue` 模板与脚本、独立 JS（未来实际出现 TS 时再接）的 import/export 与组件 `components`/`mixins`。仅解析静态相对路径及经冻结构建配置明确确认的 alias；动态 import、运行时注册、全局 mixin 均标未知。Vue 官方允许构建工具定义 import alias，不能仅看 `@/` 字面就宣称已解析。[Vue `<script setup>` import 说明](https://vuejs.org/api/sfc-script-setup.html#import-statements)、[Vue 2 全局 mixin 影响](https://v2.vuejs.org/v2/guide/mixins.html#global-mixin)。
2. **页面操作与局部调用。** 从 `v-on/@`、组件 `@ok`、静态 method、`this.method()`、静态 `$refs.child.method()` 和 `$emit('literal')` 提取有位置的候选边；在确定的 mixin 合并顺序下处理覆盖；表达式只解析 AST，绝不求值或执行。`v-if`、权限、用户输入、异步结果保留为条件/未知，不把“静态可达”升级为“执行过”。[Vue 2 `v-on` API](https://v2.vuejs.org/v2/api/#v-on)、[Vue 2 mixin 合并规则](https://v2.vuejs.org/v2/guide/mixins.html)。
3. **HTTP 调用。** 从实际 import/export 绑定到被调用函数，再把调用实参沿可核验的 wrapper 形参和局部静态赋值传播到 Axios config；`getAction`/`httpAction` 只是本样例的函数名，不是硬编码白名单或设计上限。对 string literal、静态成员、有限字面量条件分支形成 method/path 候选集合。无法静态界定的模板插值、可变对象、拦截器改写等留未定，不能执行表达式取得“答案”。Axios 允许 request interceptor 修改 config，故只看调用点 config 也不是实际请求的运行时证明。[Axios request config](https://github.com/axios/axios/blob/v1.x/docs/pages/advanced/request-config.md)、[Axios interceptors](https://github.com/axios/axios#interceptors)。
4. **后端匹配与材料。** Java 侧复用 Step02 的已保存 Controller path 与 `methodCondition`（包括 unrestricted、条件集合）。先只读解析和保存本 snapshot 的 `public/index.html`、`request.js`、`vue.config.js`、`application.yml` 中的静态配置、适用环境与分支依据；可证明的 `/jshERP-boot` 前缀按该证据解释，不凭 URL 字面猜测或剥离。遇到配置冲突、覆盖或缺失，使前后端路径关系不能静态确定时，再要求用户给出明确映射。然后才匹配 method/path；不通过名称猜测。Step05 只消费前端候选边与既有已保存 JDT/Mapper/SQL 材料，给页面→请求→入口→Java/SQL 的每一环 source ref、条件和失败原因，不重新运行 JDT。一个页面操作可能对应多个请求或多个条件结果；未能闭合的链仍保留页面材料与 Gap。

## 固定 Node helper 的可验证接口建议

这是待设计/验证的实现建议，不是已存在工具。Java 可用 JDK 17 的 `ProcessBuilder` 以程序和参数列表启动固定 helper（不在客户仓库运行 npm 脚本），输入限定为冻结快照的只读文件清单、每项 hash、语言/版本与静态 alias 配置；输出版本化 JSONL：文件与解析器版本、语法诊断、组件/事件/方法/import/export/调用/请求候选节点和带源位置的边。超时、标准输出/错误限额及退出清理仍需原型测试；此处不假称已实现。[JDK 17 ProcessBuilder](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/ProcessBuilder.html)、[JDK 17 Process](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/lang/Process.html)。

候选边建议携带 `SUPPORTED_STATIC`、`CONDITIONAL_STATIC`、`UNRESOLVED` 等**设计标签**及逐段源位置；标签名只是提议，不是现行 public schema。静态配置给出的 baseURL/context-path 分支可以有来源地表达；其他部署覆盖、router 条件、运行时权限和 Axios interceptor 造成的行为差异须保留未定。当前不建议暴露“完整页面→后端覆盖率”，因为解析上限与端到端夹具尚未证明。

## 必须先验证的有限夹具与 unknown

这些均为**后续原型验证事项**，此次没有安装解析依赖、执行客户代码或运行工具链。

| 有限夹具 | 必须核验的输出/失败 |
| --- | --- |
| 固定 snapshot 的 `PurchaseOrderModal.vue` + 两个 mixin + `api/manage.js` + `utils/request.js` + 三份前后端配置 + `DepotHeadController.java` | 保存与保存并审核各保留页面事件位置；`model.id` 两分支得到 POST/PUT 两候选；保存 `window._CONFIG` 静态赋值、Axios fallback、开发代理和 Spring context-path 各自的分支依据；对应 Controller，部署覆盖则留未定。 |
| `LinkBillList.vue` + `JeecgListMixin.js` + 父页面 | 子组件注册、`@ok`/`$emit`、`loadData`→GET `/depotHead/list` 均有分段来源；不宣称事件必然发生。 |
| 人工最小 Vue 2.6、Vue 2.7、Vue 3 SFC 各一份，包含模板事件、Vue 2 filter、`slot-scope`/`v-slot` 与 `<script setup lang="ts">` | 分别验证 parser 选项、AST 位置、脚本语法和失败诊断；Vue 3 仅承诺验证到语法层，不承诺任意 Composition API 数据流；不跨版本冒充。 |
| 人工最小 JS/TS 请求封装各一份，分别包含静态 alias、re-export、字面量条件与动态 URL | 静态分支集合完整；动态值明确 `UNRESOLVED`；无 `eval`、客户 import 或构建。 |
| 同名 mixin/component method、Axios interceptor 改写 URL/method、多个同 path Controller、Spring unrestricted method | 覆盖/冲突、请求真实值不确定、路由歧义及 methodCondition 均不被偷换为单一确定连接。 |

未验证的**工具/规模事项**：helper 的实际 Node/ESLint/解析器版本和可用安装源、协议版本、资源上限；目标输入是否还有其他 Vue 主版本、TS 或非 JS 模板语言；构建 alias 与配置覆盖的全仓分布；编译期宏/全局 mixin/自定义指令的可支持上限；全仓链接覆盖率与误报率。Step01 对固定 snapshot Vue/JS 文本的准入已有 manifest 证据，不列为 unknown。**需要用户选择的仅是证据不能闭合时的前后端部署路径映射或未来扩大解析语义的取舍**；不能把尚未实验的工具版本/解析上限冒充待用户给答案的问题。上述 unknown 需要独立验证和设计审查，不能由官方 API 文档或本次人工样例推定。
