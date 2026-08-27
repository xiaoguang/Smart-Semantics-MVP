# 来源文档、对齐审阅与可读性整改实施报告

## 实施结果

- 每个来源生成独立九段文档；章节正文、结构化断言和 Markdown 使用内容寻址存储，元数据只保存引用。
- 章节修改创建新 revision；已确认参与对齐的修订不可覆盖，并提供章节级 Before／After。
- 来源对齐区分一致、冲突和派生血缘缺口。人类决定必须选择真实来源主张并填写理由，决定会改变最终语义载荷和 Markdown。
- 最终产物支持“来源文档集合”和“合并文档”，两种模式使用同一封装、溯源清单、SHA 门禁、作者确认和异人审核；审核通过后自动、可恢复冻结。
- 大规模审阅只加载摘要与当前 20 条问题；证据正文点击后读取。搜索查询完整索引，技术值统一折叠、展开和复制。
- 数据标准化、来源中心、AI 模型浏览器和本体主从布局按实际容器宽度切换；窄屏使用全屏检查器或列表→详情，输入框不随检查器消失。
- 指标、规则、目标、同义词和时间语义使用中文业务标签、分页卡片或可达横向滚动；内部编码与技术契约默认折叠。
- 草稿变化改为全局风险优先分页，删除、冲突和待核验项先于普通修改。

## 数据故事

零售经营使用 MySQL、GitHub、Semantica、SharePoint、MongoDB、Elasticsearch、MinIO 和 Kafka 八份来源文档。对齐固定产生净销售额退款时点、机器人流量过滤、营业日归属三项主要冲突；决定写回后，两种产物均可冻结并交给 M4，随后走 V2 草稿、审核和发布。

管伊佳使用 MySQL、GitHub 和官方文档三份真实来源文档。黄金 V1 保持 14 实体、9 事件、296 字段、30 关系、4 维度、5 指标、2 层级、8 规则候选、10 同义词、9 时间语义、63 待归类资产和 2 排除项。仅 MySQL＋GitHub 的未来文档保留证据缺口，不允许伪冻结或重发 V1。

## 风险边界

- 所有来源与 Agent 仍为确定性前端 Mock，不访问真实外部服务。
- IndexedDB 不可用或配额不足时，内容写入必须失败并保留轻量元数据，不降级为 localStorage 大正文。
- 管伊佳黄金 V1 不由新流程覆盖；未来证据只形成新的来源文档、对齐说明或个人草稿。
- 本轮未修改备份、Java Demo或远端服务，也未部署。

## 最终验证

- 来源与证据：`test:source-management` 17/17、`test:semantic-evidence` 10/10、`test:source-documents` 7/7。
- 对齐与产物：`test:document-alignment` 5/5、`test:standardization-deliverable` 5/5、`test:evidence-review` 2/2、`test:data-standardization` 11/11。
- 建模与协作：`test:modeling-document` 15/15、`test:ai-modeling` 73/73、`test:collaboration` 22/22、`test:catalog-browser` 8/8。
- 数据与类型：`fixture:check`、`npx tsc -b --pretty false`、`git diff --check`均通过。
- 浏览器：核心故事 7/7；320、360、390、768、1024、1440及200%缩放响应式回归12/12。
- 浏览器控制台仍有Ant Design旧属性弃用提示（`Alert.message`、`Drawer.width`、`Space.direction`及静态`message`上下文提示），不影响当前交互与验收，后续可作为依赖升级清理项。
