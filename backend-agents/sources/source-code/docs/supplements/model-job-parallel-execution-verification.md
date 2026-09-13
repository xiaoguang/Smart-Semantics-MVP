# 模型任务并行执行验证记录

本文记录 `sourceAnalysis.modelJobs` 并行实现的本地、零真实模型调用验收。它证明调度、隔离、保存和阶段屏障正确，不把 scripted Provider 的耗时冒充 Luna 或 API 的实际加速比例。

## Activity 对照

同一套材料在并发执行前已经由串行 `ActivityExplainer` 合同覆盖；并行实现没有改变材料、Prompt、DRAFT、完整 REVIEW、引用校验或最终聚合类型。新的直接验收使用 12 份独立材料、两个 Provider 和固定路由：

| 项目 | 实测结果 |
| --- | --- |
| 全局同时在途 job 峰值 | 6 |
| Pro Provider 峰值 | 4 |
| API Provider 峰值 | 2 |
| 完成材料 | 12 / 12 |
| Provider 调用 | 24，即每份材料一次 DRAFT、一次 REVIEW |
| 私有完成记录 | 12 |
| 聚合顺序 | 按原材料稳定顺序，不按线程完成顺序 |

另一个 13 材料直接测试证明默认并发 4 时不会只处理前 4/12 份材料；全部材料均完成，任一材料最多两次请求。受控 fatal 测试证明观察到失败后停止新派发，已开始且自身 DRAFT 合法的 job 仍完成唯一 REVIEW，不重试失败请求。

这些测试使用 latch 控制重叠，不用长时间 sleep 猜测并发。测试总耗时包含 fixture、编译和断言，不能用于估算真实 Provider 网络时间。

## Process 与最终屏障

过程测试使用两个互不依赖、各含两个活动的过程组：

| 项目 | 实测结果 |
| --- | --- |
| 过程组同时在途峰值 | 2 |
| 过程组调用 | 4，即每组一次 DRAFT、一次 REVIEW |
| 私有完成记录 | 2 |
| 仓库总结调用 | 2，即唯一总结的一次 DRAFT、一次 REVIEW |
| 屏障 | 两个过程 REVIEW 全部完成后才开始仓库总结 |
| 最终结果 | 一份稳定 `RepositoryBusinessKnowledge`，随后仍只生成一份九章报告 |

单活动组继续保持零过程模型调用。活动、过程、仓库总结和整篇报告之间的阶段顺序没有因线程池而改变。

## Provider 与配置

- 一份严格的 `repository-run-config-v2` YAML/JSON 同时配置全局上限、各 Provider 上限、固定阶段路由和非秘密认证环境引用。
- Codex Subscription 固定独立 `CODEX_HOME`，强制 ChatGPT 登录方式，并清除继承的 API 认证环境。
- Codex Provider 构造时只执行一次登录预检；退出码和受限状态文本必须共同表明 ChatGPT 登录，API-key 登录即使退出码为零也被拒绝。
- 不同 Provider 若解析为同一个 Codex home 或相同 API key，即使声明不同 `quotaScope` 也在请求前被拒绝；比较只在内存中完成，不记录凭据。
- OpenAI Responses adapter 使用官方 Java SDK，关闭自动重试；模拟 HTTP 500 的直接测试观察到恰好一次请求，HTTP 成功但响应状态不是 `completed` 也被拒绝。
- 一个 job 的 DRAFT 与 REVIEW 固定到同一 Provider 和同一 credential client；Provider 失败不会换服务或重试。
- Activity 与 Process-group 私有记录都保存覆盖真实输入、Prompt、Schema、预算、Module 版本和预期运行身份的完整 SHA-256 指纹；待执行队列只保留轻量工作描述，不为每个待执行项常驻一份序列化 packet。

## 结论与未声称事项

本地 scripted 验收说明线程池确实重叠执行完整 job，且全局 6 / Pro 4 / API 2 两级限制没有被突破。并行度 1、4、6 改变派发与等待，不改变需要处理的材料总数、业务内容排序或最终聚合规则。

本轮没有调用真实 Pro 或 API，因此不声称真实运行快 4 倍或 6 倍。真实耗时仍取决于各 Provider 的响应时间、额度和限流；后续实测可以直接复用已保存材料，不需要重新运行 JDT。
