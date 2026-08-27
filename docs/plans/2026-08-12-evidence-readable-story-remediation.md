# Linguan 大规模证据审阅、页面可读性与统一建模故事整改

## 全局约束

- 只修改 `linguan-prototype-v2`，不修改 backup、web-next、Java Demo 或远端服务。
- 保留正式 V1、历史 Catalog、草稿和旧存储键。
- 零售保留 7 实体、5 事件、84 字段、15 关系、11 维度、12 指标、2 层级、7 规则、191 同义词和 3 项时间语义。
- 新实现使用唯一 Evidence Registry，Markdown 必须由真实批次、主张、决定和证据绑定生成。
- 页面以“总览 → 问题 → 结论 → 证据”为审阅主线；证据与对象按需分页。

## Task 1：证据注册表与因果故事

- 合并冲突的 GR 证据编号宇宙，建立语义 Claim、根来源血缘和对象证据绑定。
- 零售按 MySQL → GitHub → Semantica → SharePoint → MongoDB → Elasticsearch → MinIO → Kafka 形成 B01-B08；核心证据累计 4/7/9/12/15/18/21/25。
- B02、B06、B08 分别产生净销售、机器人过滤、营业日归属 Blocker。
- Semantica 为 SharePoint 派生；缺上游时产生 gap，不增加独立来源数。
- 保留大模型并确保全部业务可见对象存在证据绑定或明确待确认状态。
- 管伊佳绑定真实 MySQL/GitHub snapshot identity，并按 MySQL → GitHub → SharePoint → Semantica 累积。
- 先补失败测试，再实现。

## Task 2：ReviewIssue 与标准文档审批

- 新增 BatchOverview、ReviewIssue、Decision、游标分页和按需证据读取。
- 页面默认审阅未决问题，证据只在展开根来源后读取。
- 标准文档 revision 由批次和决定物化；冻结检查 Blocker、Locator、Checksum、血缘和章节就绪度。
- 零售形成 r1-r8 自动 revision，三项人工决定形成 r9，并经作者外审核后冻结。
- 管伊佳使用相同状态机。
- M4 仅按 frozen artifact + SHA 导入，不按 projectId 硬编码完整候选。

## Task 3：数据标准化与来源中心 UX

- M3 右栏调整为来源、证据、互证、标准文档，互证内部为总览→问题→结论→证据。
- 问题桌面 25、手机 15；证据桌面 50、手机 20；搜索作用于全量索引。
- 来源中心手机使用列表→详情→步骤，消除嵌套滚动。
- ReadableTechnicalValue 支持完整查看、复制、键盘和手机触摸。
- 标准文档进入审核、批准、冻结后才能交给 M4。

## Task 4：全站响应式与字段可读性

- 手机导航固定为数据标准化、AI 建模、本体、指标、更多；其余三页从更多进入。
- Header 手机两层，不压缩项目/版本。
- 新增 ResponsiveDataView；指标、规则、同义词、时间语义手机改卡片，桌面使用明确列宽。
- 同义词、时间、关系、值、层级和映射增加分页或渐进加载。
- Drawer 手机单列，固定底部操作，禁止固定宽表单溢出。
- 修复登录抽屉、Before/After、技术字段截断和所有嵌套滚动。

## Task 5：端到端验证与文档

- 增加 evidence-review、standardization-story 和容量 fixture 测试。
- 覆盖零售 B01→B09→M4→V2 和管伊佳→标准文档→V1。
- 覆盖 320/360/390/768/1024/1440 与 200% 缩放。
- 更新 README，说明唯一证据真相、文档审批、分页和移动规则。
- 顺序运行计划规定的定向测试、tsc、E2E 和 diff check。
