# 管伊佳 V7 内容发布与远端部署交接

## 当前事实

- 功能分支：`codex/data-standardization-review-ia`。
- 已推送的 V7 发布提交：`c9ded571e198d065a37587c66c5c770ab63f37cc`
  （`feat: publish approved human-readable V7 documents`）。
- 已批准的五源 Selection：
  `v7-five-source-review-20260829.json`。
- 已冻结并激活的新运行内容：`guanyijia-demo-content-v7-20260827`。
- V7 内容 SHA-256：
  `sha256:b570297c59fd19538970985eacf60dd1bc9f46c7dbda550ecf46e2b43f706ca4`。
- V7 是从精确 V6 冻结输入确定性投影出的业务阅读版；它没有重捕获来源、改写 V6、改变正式
  Claim／Conflict 或修改正式 V1。既有 V5/V6 运行继续读取其原绑定版本。

五源 Selection 的确定性验收状态如下：

| 来源 | Candidate 验收 |
| --- | --- |
| 管伊佳部署数据库 | `READY_WITH_WARNINGS` |
| GitHub 代码仓库 | `READY_WITH_WARNINGS` |
| 管伊佳官方核心文档 | `READY` |
| ERP 管理制度（演示） | `READY` |
| 企业术语图（派生） | `READY` |

两个 `READY_WITH_WARNINGS` 来源没有致命错误，且已获用户批准进入 Selection；告警仍保留在其
不可变 Candidate receipt 与 review report 中。没有创建第三份内容候选，也没有在冻结、package
或浏览器运行时调用模型。

## 已实际执行的本地验证

以下命令均串行执行且成功：

- `node --test scripts/evidence/guanyijia-demo-content-v7-generate.test.mjs`：28/28。
- `node --test scripts/evidence/guanyijia-demo-content-v7-transform.test.mjs`：7/7。
- `node --test scripts/evidence/guanyijia-demo-content-package.test.mjs`：15/15。
- `node --test scripts/evidence/guanyijia-demo-content-v7-governance.test.mjs`：10/10。
- `demo-content-review.test.ts`：15/15；`reviewed-source-documents.test.ts`：2/2；
  `standardization-deliverable.test.ts`：27/27。
- `data-standardization.test.ts`：25/25；`guanyijia-workbench-runtime.test.ts`：54/54；
  `npm run test:data-standardization`：86/86；`npm run test:standardization-run`：60/60。
- 通过了批准 Selection 的 candidate check、V7 content check 与 V7 package check。
- `npm run build`：成功；构建工具仅报告了既有的大 chunk 建议，没有构建错误。
- 单 Worker Playwright：40/40（16.0 分钟），覆盖 V7 阅读和 Markdown 源文、五源连续审阅、
  即时差异、右栏来源进展与证据区、320/390/1024/1440 布局及真实 200% 缩放。

## 远端部署

部署只上传了从 `c9ded57` 构建且已验证的 `dist/`，没有修改远端源码、主 Nginx 配置、端口或
历史 release。部署前 `current` 指向：

```text
/opt/linguan-prototype-v2/releases/20260829T055236Z-c35d575
```

已原子切换到：

```text
/opt/linguan-prototype-v2/releases/20260829T081950Z-c9ded57
```

服务 `linguan-prototype-v2` reload 后为 `active`。远端与本地逐项核对了 `index.html`、哈希
CSS、哈希 JS、`icons.svg` 与 `favicon.svg` 的 SHA-256，全部相等。实际远端冒烟结果：

- `http://127.0.0.1:4173/`：200。
- 哈希 JavaScript 资源：200。
- `/data-standardization/review`：返回与当前 `index.html` 完全相同的 SPA fallback。
- `http://172.21.1.150:4173/`：外部可达，200。

部署使用一次性、交互式认证；没有把凭据写入仓库、脚本或本文档。部署结束后已关闭临时 SSH
控制连接并删除本地与远端临时传输包。

## 保留的并发工作

本次提交与部署没有读取、暂存或修改 `source-to-standard-markdown/`。已有的
`docs/handoffs/2026-08-27-data-standardization-review-ia.md` 以及权威设计文档中的其他 Agent
未暂存段落继续留在工作树，未混入 V7 发布提交。
