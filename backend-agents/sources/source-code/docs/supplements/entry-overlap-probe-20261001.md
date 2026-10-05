# 入口技术重合度与状态依赖：零模型定向测量

状态：2026-10-01。本页是用户提出的薄实验记录及下一阶段规则草案，不是已安装的本体选材功能。当前生产实验仍按顺序分页调查；本次未修改该路径，没有重新取证，没有模型请求，也没有生成新的本体结果。

## 1. 输入与实际范围

固定R4为 `analysis-run:9138a4bb7d49e05bdacdb3983adb327d54b9f51f2de98e313bbaaaf9f160c4fe`，目录摘要为 `22857198f71fd77918447e4c5f16a8109f88f7efaeb0795e3d9a9c1196620fb1`。直接只读全部339份入口文件，从已保存字段取集合，不解析新源码、不执行客户程序、不读客户当前工作区。测量不是重新调用完整canonical reader的验收；程序核对目录与历史调查来源身份、各入口ID及sourceBasis，并记录所读文件摘要。

本次选20个入口、计算190个无重复入口对：

- 历史P2i实际选中的两个问题：仓储单据明细与库存写入/状态条件；财务单据头与明细的关联。前者7个候选，后者4个候选，共用一个入口，合计10个。
- 采购调查增加6个真实入口：单据列表、新增、修改、状态修改、财务单据列表和新增。它们是测试范围，不是程序已经确认的采购业务链。
- 4个不同功能的对照：退出登录、日志列表、系统配置、导入客户。它们没有被人工标为“绝对无业务关系”；尤其供应商/客户资料可能与交易业务有关，保留具体共同点。

用户举例的“采购清单”在当前页面材料中对应可调查的采购订单内容；不能将“库存结算”直接认定为已经找到的独立页面或入口。结算应调查财务与库存业务单据联系，而不是预先给出阶段答案。

机器结果：[190对记录](../../.workspace/ontology-recognition-poc-20261001/entry-overlap-report-20261001.json)；薄诊断脚本：[只读测量脚本](../../.workspace/ontology-recognition-poc-20261001/entry-overlap-probe-20261001.cjs)。两文件在忽略的实验目录，不是正式CLI或新取证产物。

运行命令（模块目录）：

```bash
node .workspace/ontology-recognition-poc-20261001/entry-overlap-probe-20261001.cjs --full
node .workspace/ontology-recognition-poc-20261001/entry-overlap-probe-20261001.cjs --pair /depotHead/list /supplier/importCustomer
```

## 2. 已测的相似度规则

每个entryId是一个证据分片身份，不是业务名称。每个维度分别组成去重集合；同名字符串不替代准确身份。

| 维度 | 本次取值规则 | 必须保留的限制 |
| --- | --- | --- |
| Java方法 | java.methods的完整methodKey | 只表示材料包含；不宣称实际运行或所有调用确定 |
| Service方法 | 上述方法中source.path包含/service/ | 这是本仓库的角色约定，不是框架通用Service识别器 |
| Mapper方法/接口 | bindings的methodKey、javaInterfaceFqn，分别计算 | 静态绑定不是运行时执行证明 |
| XML语句 | statementRef与databaseId组成准确变体键 | 不混同不同databaseId |
| SQL文本 | 已保存analysisCopy的准确摘要 | 完全相同的静态副本，不是完整动态SQL等价 |
| SQL表/列 | AST实际TABLE/COLUMN节点 | AST缺失、PARTIAL及表达式未投影必须披露；不从字符串重写SQL解析器 |
| Controller | 入口methodKey与handlerFqn中的类分别计算 | 同Controller类只是粗线索，不等于同业务 |
| Vue/前端 | sourceUnitId；Vue单元与所有JS单元分开 | 共享mixin不能当业务关系 |
| 页面关联 | confirmed requestUses与candidateRequestUses分别取pagePath | 候选地址未确认，不能升级为确定前后端链 |

各维度报告集合A数量、B数量、共同项数量、并集数量、实际共同身份及覆盖信息：

```text
原始重合度 J = 共同项数量 / 并集数量
包含度 O = 共同项数量 / 较小集合数量
```

并集为空时J=null；较小集合为空时O=null。一个维度只在一侧有观察值时，技术集合J可为0，但其业务含义仍是“未观察共同项”，不是“没有业务关系”。不能把没有Vue记录、SQL解析失败当作业务无关的负证据。

为减少公共用户、会话等材料造成的假高重合，本次同时测量：

```text
某项权重 w(x) = 1 / 全部339个入口中包含该项的入口数
稀有性加权重合度 W = 共同项的权重之和 / 并集项的权重之和
```

W是技术排序辅助量，不是业务关系概率。J、W和共同项必须同时显示；不只输出一个分数。某种特征很常见仍可能业务重要，不能因权重低而删除它。Mapper/XML/SQL维度相互相关，不重复加分制造综合“置信度”。尚未确定通用综合权重、业务阈值或自动排除规则。

## 3. 实测结果

以下均为各维度原始J，不是业务准确率。SQL表仅取已有AST节点。

| 实际入口对 | Service方法 | XML语句 | 已解析表 | Controller类 |
| --- | ---: | ---: | ---: | ---: |
| `/depotHead/list` ↔ `/depotHead/addDepotHeadAndDetail` | 10.4% | 15.8% | 22.2% | 100.0% |
| `/depotHead/list` ↔ `/depotHead/batchSetStatus` | 2.7% | 6.7% | 11.1% | 100.0% |
| `/depotHead/addDepotHeadAndDetail` ↔ `/depotHead/updateDepotHeadAndDetail` | 24.0% | 7.1% | 66.7% | 100.0% |
| `/depotHead/list` ↔ `/accountHead/list` | 12.5% | 7.7% | 60.0% | 0.0% |
| `/depotHead/list` ↔ `/supplier/importCustomer` | 11.8% | 6.7% | 11.1% | 0.0% |
| `/depotHead/list` ↔ `/log/list` | 0.0% | 0.0% | 9.1% | 0.0% |

一个具体反例：单据列表与导入客户的Service重合为4/34=11.8%，共同方法实际是：

- UserService#getUserId：118个入口包含；
- UserService#getCurrentUser：95个入口包含；
- RedisService#getObjectFromSessionByKey：120个入口包含；
- UserService#getUser：102个入口包含。

其W为0.36%。共同Mapper是UserMapper#selectByPrimaryKey，102个入口包含；共同AST表是jsh_user，110个入口包含。这提供了“重合来自公共用户/会话材料”的可检查依据，不需要用中文接口名猜无关，也不需要写日志/权限方法黑名单。

另一个重要结果：列表与状态修改的Service重合仅1/37=2.7%，但仍存在状态读写线索。故不能仅凭J或W低就阻止调查；典型前后动作可能采用不同方法和不同SQL。

## 4. 状态读写：实际原文有，但当前未自动结构化为依赖

人工对本次R4两份文件作了具名核对；这不是诊断脚本已经识别出的业务关系。

写方：`/depotHead/batchSetStatus`，entryId为 `entry:572a7d0add93cece17cac58b6c67aa4578c4ee78a04aa0dec93b7fafe583804f`。

其DepotHeadService#batchSetStatus保存原文，第800–803行实际包含：

```java
depotHead.setStatus(status);
example.createCriteria().andIdIn(dhIds);
result = depotHeadMapper.updateByExampleSelective(depotHead, example);
```

对应DepotHeadMapper.updateByExampleSelective的XML含：

```text
update jsh_depot_head
status = #{record.status,jdbcType=VARCHAR},
```

读方：`/depotHead/list`，entryId为 `entry:50b7073a3ca2bce8612df51fdaa2d22cb22e33c7d5b1bf28c23c532e661c65a7`。

DepotHeadService#select原文第118、128–129行保留status拆分及传递statusArray；DepotHeadMapperEx.selectByConditionDepotHead XML保留：

```xml
from jsh_depot_head dh
<if test="statusArray != null and statusArray !=''">
    and dh.status in (
    <foreach collection="statusArray" item="status" separator=",">
        #{status}
    </foreach>
    )
</if>
```

上述片段用于指示定位，省略处没有被当成模型已读完整实现。模型验证时仍须读完整方法及XML单元。

这可以形成“同表同字段，一侧写入、另一侧用于筛选”的候选信号；尚不能据此认定某个订单的修改必然影响某次查询，或认定查询就是某个后续办理动作。还须核对写入ID集合、读取条件、业务变体、租户/删除/类型限制及动作条件。

重要限制：该XML的现有静态SQL副本及AST没有status条件；其状态为PARTIAL。仅比较当前AST不会得到完整状态依赖，必须使用已保存XML原文。写入XML的AST也未成功生成。当前没有“所有入口的表列读写及跨方法数据流”索引，本次未开发该系统。

## 5. 建议的相关度规则：有方向的信号优先，而不是重合度决定一切

这是待验证规则，不是当前生产行为。将相关度作为一组带来源的候选信号：

1. 同一表列被一方写入、另一方读取并用于筛选/分支/动作判断：优先调查；记录写→读方向及双方具体原文。
2. 在上述信号上，若进一步核对记录ID或明确连接字段，单列“记录连接已核对”；未核对则明确未知，不能靠两边都叫id补成功。
3. 同一Mapper变体、Service方法或明确SQL连接字段：提供具体共同材料及用法，按频次加权排序辅助取材。
4. 同表、同Controller、同Vue文件等粗重合：提供辅助导航；不能单独批准关系。
5. 无重合、缺解析或缺前端：保留未调查范围，不自动丢弃。

Java当前能薄适配计算第3–4项集合与频次。第1–2项在本次例子中已经人工核对到候选线索，但未有通用自动提取。首轮模型实验应将现有成熟工具取得的结构、完整XML及Java条件一并交给模型核对；缺字段时记录未知，不为了打满评分重建跨函数数据流分析器、SQL解析器或业务规则系统。是否增加JSqlParser已有字段的窄投影，应依据这个实际断点另定。

## 6. 采购范围不能只剩entryId

R4的/depotHead/list保留PurchaseApplyModal、PurchaseOrderModal、PurchaseInModal的不同实例：

- 请购页面历史查询：type='其它'，subType='请购单'。
- 订单页面关联请购：type='其它'，subType='请购单'，status="1,3"，purchaseStatus未传。
- 入库页面关联订单：type='其它'，subType='采购订单'，status="1,3"。
- 入库页面历史查询：type='入库'，subType='采购'。

它们共用同一个entryId，不是四个独立后端入口，也不能自动合并成同一业务动作。调查身份至少保留entryId、requestId、pagePath、instanceKey及原始argumentBindings；程序保存长身份，模型可使用可逆短引用。

本R4的51条前端请求均是地址条件未确认的候选；本样本中这三页请求也为UNRESOLVED_REQUEST。相同相对路径不代表部署地址已经确认。新增/修改/状态入口没有已保存的这些页面提交关联，不能由页面名称补造Vue→入口边。

## 7. 已验证与未验证

已验证：

- 20个真实入口、190对集合结果可确定性计算；
- 准确共同身份可反查；公共项频次可计算；
- 自重合与对称性检查通过，脚本语法检查通过；
- 全程模型请求0，没有改写固定证据或旧模型记录。

未验证：

- 尚未把相关导航接入SURVEY或替换当前顺序分页；
- 状态依赖尚未自动提取；
- 尚未进行新导航与旧导航的真实模型对照；
- 尚未证明能提高关系判断或本体准确率；
- 尚未证明“库存结算”存在用户举例的固定阶段。

下一道验收应分开看：能否找到具名相关材料；能否解释字段/记录交接；能否在原文REVIEW后输出可组装定义。必须有较低相关、同Controller不同用途、公共表/方法以及同entry不同页面参数的反例，不能只看示例对象名称出现。规则、权重及选择策略不得加入采购词或正确业务答案。
