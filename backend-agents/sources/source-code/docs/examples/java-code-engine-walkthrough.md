# 真实代码怎样通过可切换引擎走到业务解释

> [插件总设计](../modules/java-code-engines/README.md)。这里分清三件事：旧材料实际缺什么、JDT调研实际取到什么、正式插件未来怎样传给模型。本次没有重新执行JDT或Luna，没有修改历史packet。

## 1. 注册：以前只有入口，现在能读到实现

固定jshERP commit为`8c30ce7861570458920175e200bb2a6442713580`。输入只有`POST /user/registerUser`的Controller位置，程序没有收到预期Service路径或方法名单。源码中通配import由JDT处理，不用“当前包名+UserService”猜路径。

```text
UserController.registerUser
 ├─ validateCaptcha(code, uuid) → UserService完整正文
 ├─ checkLoginName(ue)          → UserService完整正文
 └─ registerUser(ue, role, req) → UserService完整正文
      ├─ 禁用名称检查、默认属性/状态
      ├─ userMapper.insertSelective(ue)
      ├─ updateUserTenant(user)
      ├─ insertUserBusiness(ubObj, null)
      └─ tenantMapper.insertSelective(tenant)
```

中文是对实际源码的阅读说明，不是JDT生成的业务结论，不表示一次事务成功。可导航仓库实现继续展开；外部/缺依赖/未展开点有具体记录，不能由这张精简图宣称全部动态行为已解决。

### 1.1 一个真实定位请求

调研的outgoingCalls找到了验证码与登录名检查，但没有列出注册Service调用。对实际调用位置的definition请求仍成功：

```json
{
  "method": "textDocument/definition",
  "params": {
    "textDocument": {"uri": "<固定投影>/src/com/jsh/erp/controller/UserController.java"},
    "position": {"line": 364, "character": 20}
  }
}
```

返回核心字段：

```json
{
  "uri": "<固定投影>/src/com/jsh/erp/service/UserService.java",
  "range": {
    "start": {"line": 607, "character": 16},
    "end": {"line": 607, "character": 28}
  }
}
```

URI宿主根隐去，零基行列来自实际记录。该位置只指方法名；随后prepareCallHierarchy的整声明range为606行4列至660行5列，对应含注解的607–661行。正式设计用整range或JDT Core声明范围读全文，不再需要JavaParser。

[保存的交换记录](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/registration/raw-exchanges.jsonl)包含客户端请求和解析后结果。`left/right`是LSP4J的Either包装，不是LS必须返回的在线JSON；文件不是逐字节网络报文。

### 1.2 自动取到的完整源码

以下四段直接取自[注册packet.json](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/registration/packet.json)，未省略body。M编号属于调研产物，不是未来公共ID。

<details>
<summary>M01 registerUser：jshERP-boot/src/main/java/com/jsh/erp/controller/UserController.java:357–367</summary>

```java
@PostMapping(value = "/registerUser")
    @ApiOperation(value = "注册用户")
    public Object registerUser(@RequestBody UserEx ue,
                               HttpServletRequest request)throws Exception{
        JSONObject result = ExceptionConstants.standardSuccess();
        ue.setUsername(ue.getLoginName());
        userService.validateCaptcha(ue.getCode(), ue.getUuid());
        userService.checkLoginName(ue); //检查登录名
        userService.registerUser(ue,manageRoleId,request);
        return result;
    }
```

</details>

<details>
<summary>M41 validateCaptcha：jshERP-boot/src/main/java/com/jsh/erp/service/UserService.java:297–319</summary>

```java
public void validateCaptcha(String code, String uuid) throws Exception {
        PlatformConfig platformConfig = platformConfigService.getInfoByKey("checkcode_flag");
        if(platformConfig!=null && "1".equals(platformConfig.getPlatformValue())) {
            if(StringUtil.isNotEmpty(code) && StringUtil.isNotEmpty(uuid)) {
                code = code.trim();
                uuid = uuid.trim();
                String verifyKey = BusinessConstants.CAPTCHA_CODE_KEY + uuid;
                String captcha = redisService.getCacheObject(verifyKey);
                redisService.deleteObject(verifyKey);
                if (captcha == null) {
                    logger.error("异常码[{}],异常提示[{}]", ExceptionConstants.USER_JCAPTCHA_EXPIRE_CODE, ExceptionConstants.USER_JCAPTCHA_EXPIRE_MSG);
                    throw new BusinessRunTimeException(ExceptionConstants.USER_JCAPTCHA_EXPIRE_CODE, ExceptionConstants.USER_JCAPTCHA_EXPIRE_MSG);
                }
                if (!code.equalsIgnoreCase(captcha)) {
                    logger.error("异常码[{}],异常提示[{}]", ExceptionConstants.USER_JCAPTCHA_ERROR_CODE, ExceptionConstants.USER_JCAPTCHA_ERROR_MSG);
                    throw new BusinessRunTimeException(ExceptionConstants.USER_JCAPTCHA_ERROR_CODE, ExceptionConstants.USER_JCAPTCHA_ERROR_MSG);
                }
            } else {
                logger.error("异常码[{}],异常提示[{}]", ExceptionConstants.USER_JCAPTCHA_EMPTY_CODE, ExceptionConstants.USER_JCAPTCHA_EMPTY_MSG);
                throw new BusinessRunTimeException(ExceptionConstants.USER_JCAPTCHA_EMPTY_CODE, ExceptionConstants.USER_JCAPTCHA_EMPTY_MSG);
            }
        }
    }
```

</details>

<details>
<summary>M71 checkLoginName：jshERP-boot/src/main/java/com/jsh/erp/service/UserService.java:776–805</summary>

```java
public void checkLoginName(UserEx userEx)throws Exception{
        List<User> list=null;
        if(userEx==null){
            return;
        }
        Long userId=userEx.getId();
        //检查登录名
        if(!StringUtils.isEmpty(userEx.getLoginName())){
            String loginName=userEx.getLoginName();
            list=this.getUserListByloginName(loginName);
            if(list!=null&&list.size()>0){
                if(list.size()>1){
                    //超过一条数据存在，该登录名已存在
                    logger.error("异常码[{}],异常提示[{}],参数,loginName:[{}]",
                            ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_CODE,ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_MSG,loginName);
                    throw new BusinessRunTimeException(ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_CODE,
                            ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_MSG);
                }
                //一条数据，新增时抛出异常，修改时和当前的id不同时抛出异常
                if(list.size()==1){
                    if(userId==null||(userId!=null&&!userId.equals(list.get(0).getId()))){
                        logger.error("异常码[{}],异常提示[{}],参数,loginName:[{}]",
                                ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_CODE,ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_MSG,loginName);
                        throw new BusinessRunTimeException(ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_CODE,
                                ExceptionConstants.USER_LOGIN_NAME_ALREADY_EXISTS_MSG);
                    }
                }
            }
        }
    }
```

</details>

<details>
<summary>M81 registerUser：jshERP-boot/src/main/java/com/jsh/erp/service/UserService.java:607–661</summary>

```java
@Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void registerUser(UserEx ue, Integer manageRoleId, HttpServletRequest request) throws Exception{
        /**
         * 多次创建事务，事物之间无法协同，应该在入口处创建一个事务以做协调
         */
        if(BusinessConstants.DEFAULT_MANAGER.equals(ue.getLoginName())) {
            throw new BusinessRunTimeException(ExceptionConstants.USER_NAME_LIMIT_USE_CODE,
                    ExceptionConstants.USER_NAME_LIMIT_USE_MSG);
        } else {
            ue.setPassword(ue.getPassword());
            ue.setIsystem(BusinessConstants.USER_NOT_SYSTEM);
            if (ue.getIsmanager() == null) {
                ue.setIsmanager(BusinessConstants.USER_NOT_MANAGER);
            }
            ue.setStatus(BusinessConstants.USER_STATUS_NORMAL);
            try{
                userMapper.insertSelective(ue);
                Long userId = getIdByLoginName(ue.getLoginName());
                ue.setId(userId);
            }catch(Exception e){
                JshException.writeFail(logger, e);
            }
            //更新租户id
            User user = new User();
            user.setId(ue.getId());
            user.setTenantId(ue.getId());
            userService.updateUserTenant(user);
            //新增用户与角色的关系
            JSONObject ubObj = new JSONObject();
            ubObj.put("type", "UserRole");
            ubObj.put("keyid", ue.getId());
            JSONArray ubArr = new JSONArray();
            ubArr.add(manageRoleId);
            ubObj.put("value", ubArr.toString());
            ubObj.put("tenantId", ue.getId());
            userBusinessService.insertUserBusiness(ubObj, null);
            //创建租户信息
            JSONObject tenantObj = new JSONObject();
            tenantObj.put("tenantId", ue.getId());
            tenantObj.put("loginName",ue.getLoginName());
            tenantObj.put("userNumLimit", ue.getUserNumLimit());
            tenantObj.put("expireTime", ue.getExpireTime());
            tenantObj.put("remark", ue.getRemark());
            Tenant tenant = JSONObject.parseObject(tenantObj.toJSONString(), Tenant.class);
            tenant.setCreateTime(new Date());
            if(tenant.getUserNumLimit()==null) {
                tenant.setUserNumLimit(userNumLimit); //默认用户限制数量
            }
            if(tenant.getExpireTime()==null) {
                tenant.setExpireTime(Tools.addDays(new Date(), tryDayLimit)); //租户允许试用的天数
            }
            tenantMapper.insertSelective(tenant);
            logger.info("===============创建租户信息完成===============");
        }
    }
```

</details>

### 1.3 每个子模块怎样使用这些内容

| 交接 | 本例输入 | 程序处理 | 下一步得到什么 |
| --- | --- | --- | --- |
| 项目会话 | 固定Controller/Service等文件及源码根 | 建JDT索引，不执行客户构建 | LS能定位通配import下的Service |
| Core语法读取 | 完整Controller原文 | 取调用位置、实参、形参及完整方法 | 查询有真实位置，不靠猜名字 |
| LS导航 | registerUser调用名称的位置 | hierarchy不足时definition定位，需候选时implementation | UserService真实方法位置 |
| Core正文读取 | 返回位置 | 找准确声明并取完整body，枚举内部调用 | 限制、默认属性、保存及关系处理 |
| Collector | 已取到的方法与调用 | 继续展开、去重、保留循环和边界 | 一个连贯入口代码集合 |
| Step05 | 这个集合与可选技术增强 | 保存上下文、原样投影，不再证明一次 | 持久化后无需扫描即可读 |
| Builder | 同一context | 选完整方法、分配S引用、展示参数与候选 | Service正文进入实际模型输入 |
| ActivityExplainer | 连贯代码及限制 | Luna解释并完整REVIEW | 目的、条件、动作、对象与结果 |

**验收必须同时打开模型请求。** M41/M71/M81只存在技术索引不算完成；若实际注册模型包仍只有M01，就是组包/接线缺陷，不是JDT没有找到。

### 1.4 可以支撑什么业务说明

下段是设计者根据已取得代码的推演，不是本次Luna输出：

> 注册处理会在验证码功能启用时校验验证码，并检查登录名冲突及禁止使用的名称。通过检查后，系统设置用户的初始属性和状态，发起用户保存，设置与用户关联的租户标识，并创建用户角色关系和租户信息。租户的用户数限制、到期时间在未提供时使用代码配置的默认值。代码还包含保存异常处理；这不表示任何一次实际请求必然成功。

验证码条件来自M41；名称冲突来自M71；禁止名称、默认属性、保存及租户角色关系来自M81。不能只见@Transactional就保证异常全部回滚，实际捕获处理和运行环境仍需谨慎说明。

原来缺的是没有交给模型的Service代码，不是仓库没有这些业务行为。现在材料增加了具体条件和动作，模型才有依据解释。角色、实际配置、部署后的运行结果依然不能凭空确定。

## 2. 财务查询：一个小型完整跨层例子

[财务packet.json](../../.workspace/jdtls-source-navigation-feasibility/goal-driven/run-20260912T115800-0230/financial/packet.json)保存3条方法记录：2条body与1个Mapper声明。以下是全部三段：

<details>
<summary>M01 getFinancialBillNoByBillId：jshERP-boot/src/main/java/com/jsh/erp/controller/AccountHeadController.java:181–196</summary>

```java
@GetMapping(value = "/getFinancialBillNoByBillId")
    @ApiOperation(value = "根据编号查询单据信息")
    public BaseResponseInfo getFinancialBillNoByBillId(@RequestParam("billId") Long billId,
                                              HttpServletRequest request)throws Exception {
        BaseResponseInfo res = new BaseResponseInfo();
        try {
            List<AccountHead> list = accountHeadService.getFinancialBillNoByBillId(billId);
            res.code = 200;
            res.data = list;
        } catch(Exception e){
            logger.error(e.getMessage(), e);
            res.code = 500;
            res.data = "获取数据失败";
        }
        return res;
    }
```

</details>

<details>
<summary>M11 getFinancialBillNoByBillId：jshERP-boot/src/main/java/com/jsh/erp/service/AccountHeadService.java:442–444</summary>

```java
public List<AccountHead> getFinancialBillNoByBillId(Long billId) {
        return accountHeadMapperEx.getFinancialBillNoByBillId(billId);
    }
```

</details>

<details>
<summary>M21 getFinancialBillNoByBillId：jshERP-boot/src/main/java/com/jsh/erp/datasource/mappers/AccountHeadMapperEx.java:44–45</summary>

```java
List<AccountHead> getFinancialBillNoByBillId(
            @Param("billId") Long billId);
```

</details>

### 2.1 统一材料的目标阅读投影

下面是**新设计的人类阅读投影**，不是已生成的新格式文件。完整required字段见[共同合同](../modules/java-code-engines/contracts-and-configuration.md)。M1/M2/M3分别指上方三段原文，真实模型包必须放入原文，不能发送说明占位文本。

```json
{
  "entry": "GET /accountHead/getFinancialBillNoByBillId",
  "methods": ["M1:完整Controller", "M2:完整Service", "M3:完整Mapper声明"],
  "calls": [
    {
      "call": "C1", "from": "M1",
      "expression": "new BaseResponseInfo()", "actuals": [],
      "target": null, "status": "UNRESOLVED",
      "reason": "历史试验只取得非方法位置，正式构造器处理待验证"
    },
    {
      "call": "C2", "from": "M1", "target": "M2",
      "expression": "accountHeadService.getFinancialBillNoByBillId(billId)",
      "actuals": ["billId"], "formals": ["Long billId"],
      "resultContext": "赋给list，正常分支写入res.data"
    },
    {
      "call": "C3", "from": "M1",
      "expression": "logger.error(e.getMessage(), e)",
      "actuals": ["e.getMessage()", "e"], "target": null,
      "status": "UNRESOLVED", "context": "catch"
    },
    {
      "call": "C4", "from": "M1",
      "expression": "e.getMessage()", "actuals": [], "target": null,
      "status": "UNRESOLVED", "context": "C3的实参表达式"
    },
    {
      "call": "C5", "from": "M2", "target": "M3",
      "expression": "accountHeadMapperEx.getFinancialBillNoByBillId(billId)",
      "actuals": ["billId"], "formals": ["@Param(\"billId\") Long billId"],
      "status": "DECLARATION_ONLY",
      "resultContext": "return该调用表达式",
      "reason": "只取得Mapper声明，未取得Java实现"
    }
  ],
  "control": [
    "M1 try：调用M2，写res.code=200、res.data=list",
    "M1 catch：记录错误，写res.code=500、res.data=获取数据失败",
    "M1 在try/catch之后return res"
  ]
}
```

入口取得billId，传给Service，同一表达式继续传到Mapper；Service返回调用值，Controller形成正常或异常响应。调用点、参数与完整body放在一起，读者不再需要解码五张图才能看清主线。

参数配对不等于SQL数据流Proof，也不说明某次数据库实际执行。历史程序在hierarchy命中Mapper后没有继续询问implementation，因此不能声称“JDT已证明不存在实现”；新设计必须先查询再记录边界。XML若经既有安全能力定位可以附上，不是此次Java导航通过的先决条件。

## 3. 多实现：换项目不加行业规则

以下是合成例子，不是管伊佳源码：

```java
interface NotificationSender { void send(Message message); }
class MailSender implements NotificationSender { /* send的邮件实现 */ }
class SmsSender implements NotificationSender { /* send的短信实现 */ }
// Controller调用：
sender.send(message);
```

JDT可能给出接口声明与两个实现。材料保留全部候选body，未知实际配置就不选邮件或短信，不把两者编成顺序执行。模型可说明“系统通过通知接口发送通知，仓库提供不同实现，实际选择取决于配置”。

JavaParser第二阶段如果只能给接口声明，就保留声明与未知实现；不要求增加Notification行业规则或补齐JDT解析能力。

## 4. 最后如何进入一份九章

注册、登录、用户维护与财务查询分别形成活动。模型阅读多活动判断业务联系，保留独立活动和待确认关系，然后汇总一份仓库知识与一份九章，不是每入口一份报告。

| 章节 | 这些材料能够贡献什么 | 不能补造什么 |
| --- | --- | --- |
| 文档说明 | 固定版本、已读入口与未展开范围 | 把两入口说成整仓完成 |
| 业务目标 | 账户注册及关联初始化；查询财务单号 | 无依据的组织战略 |
| 业务对象 | 用户、租户、角色关系、财务单据关联信息 | 把Controller/Service当岗位 |
| 业务活动 | 注册条件→初始化→保存与关联；查询及异常处理 | 注册后一定发生财务查询 |
| 字段与维度 | 登录名、状态、租户标识、到期时间、单据标识 | 未看见的字段规则 |
| 对象关系 | 代码明确设置的用户/租户/角色关联 | 制度上绝对一对一 |
| 指标口径 | 已读范围没有经营指标时明确说明 | 注册成功率或会计口径 |
| 示例问题 | 什么情况拒绝注册？默认限制来自哪里？ | 问题形式里的虚构制度 |
| 待确认事项 | 配置、dispatch、运行结果、未读范围 | 把缺失藏在日志里 |

可行性不是来自更多校验：它来自原来缺失的实现现在真的到达模型。仍要检查真实Activity和报告质量，不能只数ref和标题。

## 5. 本设计采用的实测边界

- 注册76条方法记录=67条body+9条声明；228个语法调用中110个UNRESOLVED，另有18个候选目标落在非方法位置。后两个数分母不同，不能相加作为未解析调用总数。
- 财务3条方法记录=2条body+1条声明；5个语法调用，2个UNRESOLVED，另有构造目标非方法位置问题。
- 调研使用JDT导航+JavaParser语法；JDT-only插件尚未实施。
- 没有对正确配置Symbol Solver的JavaParser做同条件实验，不能把当前手写import处理不足说成JavaParser库不可能做到。
- 未重新调用Luna；本文业务段落是设计推演。
- 第一阶段只打通JDT，第二阶段适配JavaParser现有能力，不同时开发两种引擎。
