# Mermaid 11.12.0 离线概览资源核对

状态：2026-10-05。核对官方发布标签下的源码与包声明，并复核 framework 已收入的资源；未在客户环境安装依赖，也未验证浏览器加载。

## 结论

- 固定 `mermaid@11.12.0` 的浏览器单文件候选为发布包 `dist/mermaid.min.js`。该标签的[构建入口](https://github.com/mermaid-js/mermaid/blob/0116b27/.esbuild/build.ts)明确创建名为 `mermaid.min.js` 的 IIFE 构建；[构建配置](https://github.com/mermaid-js/mermaid/blob/0116b27/.esbuild/util.ts)为 IIFE 设置 `splitting: false`、`bundle: true`，并将默认导出挂至 `globalThis.mermaid`。因此此入口本身不需要同目录的动态 `chunks/`；这只是对该构建的结论，不覆盖字体、链接或外部插件。
- 不要将 `dist/mermaid.esm.min.mjs` 当作已证实的单文件离线资源。同一[构建配置](https://github.com/mermaid-js/mermaid/blob/0116b27/.esbuild/util.ts)为 ESM 设置 `splitting: true` 和 `chunks/...` 路径；若改用 ESM，必须携带并核验实际导入闭包。
- [Mermaid 官方 flowchart 文档](https://mermaid.js.org/syntax/flowchart.html#special-characters-that-break-syntax)说明引号标签承载特殊字符，[实体转义节](https://mermaid.js.org/syntax/flowchart.html#entity-codes-to-escape-characters)说明十进制 `#NN;` 和 `#35;`。概览渲染器对模型标签的非字母数字逐码点实体编码，保留原字符与完整长度，不通过删标点/截断改变业务文字；HTML另做转义。此语法与固定11.12.0资源的最终浏览器呈现仍属于离线验收项，不由源码静态核对代替。
- [11.12.0 包声明](https://github.com/mermaid-js/mermaid/blob/mermaid%4011.12.0/packages/mermaid/package.json)的 `license` 为 `MIT`，发布文件包含 `dist/`。随资源在本地分发时，应保留相应许可与版权声明；第三方依赖的许可清单仍需在实际取包后核对。

## 框架接入边界

framework 已在 `src/main/resources/org/sourceanalysis/app/analysis/ontology/vendor/mermaid-11.12.0/` 保存 `mermaid.min.js`、`LICENSE` 与 `NOTICE.md`；本工作树中 JS 文件的 SHA-256 复核值为 `07e37dfa97b337ccc85365d57eddf99b9706f09db3b59b260d0333b23b343c4b`。打包者报告已对官方 npm tarball 做 SHA-512 校验及无动态导入检查；本笔记独立复核的是工作树文件哈希和官方构建规则，并未重做 registry 下载。概览 HTML 应以内嵌本地资源或 `data:` 脚本载入，绝不使用 CDN 或增加客户项目依赖；初始化使用 strict 安全级别、禁用 HTML 标签。发布前仍须在断网浏览器检查目标图型与实际网络请求。

版本锚点：[官方 11.12.0 release](https://github.com/mermaid-js/mermaid/releases/tag/mermaid%4011.12.0) 对应提交 `0116b27`。
