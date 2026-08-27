# 管伊佳真实证据与正式 V1 实施报告

## 结果

在不改变 Demo 导航、页面布局和协作流程的前提下，`guanyijia_erp`已经从“旧候选 JSON 驱动”切换为单一证据链：

```text
最新部署 MySQL
＋ 固定 GitHub master Commit
＋ 官方核心文档
→ Evidence／Claim／冲突决定
→ 冻结九段式 Markdown＋签名语义载荷
→ modeling-document-projector
→ 管伊佳正式 Catalog V1
→ 五个业务页面
```

系统基线发布回执明确标记“证据基线预置”，不伪造用户审批。未来变更仍从 V1 创建个人草稿并执行四眼审核和不可变 V2 发布。

## 固定证据身份

- MySQL：`20260813T032528Z-abb0502c7d79`，指纹`abb0502c7d79cab00928aa46752316827988bf27f7b6f29e95c97b11033eb61b`。
- GitHub：`20260813032126Z-5821d0ece9b1`，固定 Commit `b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1`，指纹`5821d0ece9b18e15d7ca28fc97f2058aebd9793409189b4ab8aba545f3644c1c`。
- 官方文档：`gyjerp-official-docs-20260813T031656Z`，4个官方页面、20条辅助建模主张。
- 标准文档：`artifact-guanyijia-v1-40c8572864bd`，Markdown SHA `5852f56cb63073b4978f0cb168afb34a8832a1c484454db9542dde1d3379c210`。

不存在真实 Manifest 的 SharePoint、Semantica 和 PDF/WPS Fixture 未进入正式 V1。官方文档只属于冻结 Artifact，不伪装为共享连接。

## 编译结果

- 95张部署表全部唯一归类：18张语义建模对象、14张技术支撑资产、63张待归类资产。
- 正式语义载荷：14实体、9事件、296字段、30关系、4维度、5指标、2层级、8只读规则候选、10同义词、9时间语义。
- 可执行业务规则与业务日历保持为空。
- “应收欠款”因源码字段与部署结构冲突被排除；“当前库存生效时点”因缺少可信字段保留为缺口。
- 层级只发布定义，不编造成员；时间语义只表达有证据的`oper_time`、`bill_time`和`create_time`角色。

## 生产实现

- `tools/modeling_evidence/`增强数据库、仓库和官方文档采集，并新增管伊佳建模文档生成器。
- `modeling-document-bridge`的冻结完整性信封同时绑定 Markdown、断言和完整语义载荷 SHA。
- 新增`modeling-document-projector`作为九段文档到五个业务页面的唯一深模块；运行时不再按中文名称回查旧`guanyijiaCandidateModel`。
- Catalog Sidecar 冻结来源身份、Claim、Finding、Locator、排除项、待归类资产和所有可见对象的严格 Evidence Binding。
- 协作状态升级为 v4 复制式兼容：旧键不覆盖；无管伊佳 Catalog 时幂等补入规范 V1；已有用户 Catalog 时不改写。
- 来源中心和物理映射显示真实 MySQL／GitHub快照身份，不再展示泛化别名。

## 页面验收

- 数据标准化显示3个冻结来源、373条 Evidence和完整九段文档。
- 本体建模显示正式实体、事件、字段、关系、层级和管伊佳物理映射。
- 指标配置只显示5个证据完整指标，不包含“应收欠款”。
- 业务规则正式列表为空，只读候选为8条。
- 同义词为10条稳定目标映射。
- 时间语义为9条证据字段映射，日历显示“未绑定日历”，不伪造营业日或节假日。
- 390像素手机视口沿用现有结构，无页面级横向溢出。

## 事实边界

MySQL画像采集有一项查询超时，两个无稳定主键的 View 不采样；这些警告保留在 Manifest，结构证据仍完整。官方用户手册只冻结入口和可核验索引，没有抓取外部文档正文。上述限制均未被静默补成正式语义。

## 最终验证

- 证据工具 pytest：14/14。
- MySQL 最新快照：`VALID`。
- GitHub 快照：固定 master Commit、436个白名单文件和32张核心表校验通过；交付前再次确认远端 master 仍为该 Commit。
- 九段 Markdown 与签名语义载荷检查通过。
- `test:semantic-evidence`：10/10。
- `test:modeling-document`：15/15。
- `test:modeling-document-projector`：4/4。
- `test:modeling-pipeline`：4/4。
- `test:guanyijia-story`：1/1。
- `test:catalog-browser`：7/7。
- `test:collaboration`：22/22。
- `test:ai-modeling`：73/73。
- `fixture:check`、本地 TypeScript project build、`git diff --check`和敏感信息扫描均通过。
- `test:e2e:core`：7/7；覆盖管伊佳九段文档、五页正式数据和390像素手机视口。

当前环境没有`uv`，Python定向测试与检查使用工作区 Python 3.12 运行同一模块；临时 pytest 依赖仅安装在`/private/tmp`，没有写入仓库或产品依赖。
