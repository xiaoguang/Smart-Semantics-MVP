# 管伊佳私有证据 capture 本地预检

`npm run evidence:guanyijia:capture` 是一个维护专用、**只做本地预检**的
入口。它不是浏览器 Demo、日常测试或 UI 的回源路径。Tenant 153 的原始行和
完整源制品只能留在原型工作树外、启用 Git LFS 的私有证据仓库中。

## 授权命令

```text
npm run evidence:guanyijia:capture -- \
  --authorize-private-capture \
  --private-repository <absolute-private-repository-root> \
  --private-remote <exact-codeup-ssh-origin> \
  --git-commit b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1 \
  --manifest <absolute-private-repository-root>/manifests/<id>.json
```

授权 flag 必须完全等于 `--authorize-private-capture`。私有 root 与
manifest 必须是绝对路径；manifest 必须是私有 root 中既有、安全的
`manifests/` 目录里的新 `.json` 文件。私有 root 绝不能位于
`linguan-prototype-v2` 或其工作区内。

remote 必须是严格的 canonical Codeup SSH origin：
`git@codeup.aliyun.com:<namespace>/<repository>.git`。HTTPS、其他 host、
URL query/fragment、额外 userinfo、反斜杠及 `.`/`..` path segment 都会被
拒绝。

## Fail-closed 本地验证

生产 adapter 只使用 Node 本地文件系统，以及固定参数数组的本地 `git` 和
`git-lfs` 子进程（`shell: false`）。它不会访问网络、Codeup API、MySQL、
官方文档、GitHub、浏览器、LLM 或 OCR；不会 clone、fetch、pull、push 或读取
业务源。任何失败均不会打开 source reader，也不会写入最终 manifest。

预检顺序为：

1. 解析参数并要求显式授权；
2. 用 `lstat`/`realpath` 检查实际私有 root、禁止 symlink/reparse escape，
   并确认其位于 prototype/workspace 外；
3. 验证 manifest 的 containment、安全 `manifests/` parent 以及不存在的
   final target；
4. canonicalize Codeup origin，检查全局 `git-lfs`；
5. 先要求 `extensions.worktreeconfig` 处于 Git 的精确 absent 状态，再将本地
   repository 的 canonical root、唯一 `origin` 和 HEAD 精确绑定到本次请求及
   固定提交；
6. 校验私有 repository 中既有的 Codeup privacy 和 repo-local LFS
   attestations（严格 schema、identity 与自摘要）、四个实际读取且逐值批准的
   repo-local LFS filter 值的 canonical bytes/SHA-256、`.gitattributes` 的
   canonical bytes/SHA-256、真实 `check-attr -z` 属性三元组、所有 local LFS
   endpoint/rewrite/transfer override 均不存在，以及 LFS sentinel OID；
7. 重验路径，预检 immutable writer，并写出 canonical metadata-only request。

本地 Codeup privacy 不是通过 host-name 推断的。生产 adapter 只读取私有
repository 中已存在、由另一个获准流程取得的以下三个 preflight proof artifact；
缺失、schema 或 self-digest 无效、identity/origin 不匹配、过期、非 private 或
没有 LFS write permission 一律失败：

```text
<canonical-private-root>/preflight/codeup-private-attestation.json
<canonical-private-root>/preflight/lfs-local-attestation.json
<canonical-private-root>/preflight/lfs-sentinel.bin
```

`filterConfigSha256` 不是某个可替换的镜像文件的摘要。它只从本次实际以固定
`git config --local --get` 参数读取、并逐值匹配以下 policy 的四项生成稳定 UTF-8
`key=value\\n` bytes（key 按固定顺序）：

```text
filter.lfs.clean=git-lfs clean -- %f
filter.lfs.process=git-lfs filter-process
filter.lfs.required=true
filter.lfs.smudge=git-lfs smudge -- %f
```

因此不存在或不会读取 `preflight/lfs-filter-config.txt`。即使攻击者重算候选
attestation 的 self-digest，也不能以变更后的 filter 值取得通过。

对受控本地目录，writer 在 staging 前、publish 前以及 final readback 后均重验
root/parent/final 路径；它使用随机、`0600`、exclusive/no-follow 临时文件，
fsync 后以同目录 hard-link 发布，并读取最终文件复算 SHA-256。已有 final
target、`BLOCKED`、`PARTIAL`、缺少或不匹配摘要、以及任一阶段的路径重验失败
均不会报告成功；不安全 final 不会被盲目清理。Node 路径 API 本身不能替代对
恶意并发本地攻击者的目录描述符级隔离；若这种威胁模型进入范围，必须引入平台
专用的 directory-handle adapter，不能降级为普通 `writeFile` 或覆盖式 rename。

CLI 和公共异常仅输出 closed allowlist 中固定的 `CapturePreflightError`
code/message。伪造、修改或未知 code 一律映射为
`INTERNAL_PREFLIGHT_FAILURE`；不会回显 child stderr、路径、remote、
attestation 内容、环境变量、凭据、token 或换行注入文本。

仓库本地 LFS endpoint policy 不接受任何 override：`lfs.url`、
`lfs.pushurl`、`remote.origin.lfsurl`、`remote.origin.lfspushurl` 与
`lfs.standalonetransferagent` 都必须处于 Git 的精确 absent 状态；
Git canonicalizes config variable names to lowercase before `--get-regexp`
matching，因此固定查询的 pattern 是 `url.*.insteadof`、
`url.*.pushinsteadof` 与 `lfs.customtransfer.*`（subsection 保留原样）。它们都
必须得到精确 absent 状态，且私有 root 不得含 `.lfsconfig`。非 absent 的命令
错误也会 fail closed，不会被误认为“没有配置”。

同样，`extensions.worktreeconfig` 必须以固定
`git config --local --get extensions.worktreeconfig` 查询得到精确 absent；
`true`、`false` 或任意其他值，以及非 absent 的命令错误都会拒绝本次预检。
不支持 Git worktree config 的原因是将来 Git/Git-LFS 的有效配置 scope 必须与本次
已验证且已摘要的 local scope 完全相同；不能让未纳入证明的 `config.worktree`
改变 origin、LFS endpoint、rewrite 或 transfer-agent。

## 唯一允许的成功输出

只有上述本地验证全部通过后，writer 才能在请求路径原子写入一份确定性的
metadata-only `CAPTURE_REQUEST`。JSON object key 递归排序、使用 UTF-8 并带
一个结尾换行。`requestDigest` 覆盖不含自身字段的 canonical payload；
`manifestDigest` 覆盖完整 canonical bytes，并且 writer 必须在 readback 后回报
完全相同的 digest：

```json
{
  "schemaVersion": 1,
  "kind": "CAPTURE_REQUEST",
  "metadataOnly": true,
  "privateRepositoryRoot": "<private-root>",
  "privateRemote": "git@codeup.aliyun.com:<group>/<repo>.git",
  "gitCommit": "b3ab269b05070d4094a4c8ab4e5ffd42db1d7eb1",
  "manifestPath": "<private-root>/manifests/<id>.json",
  "codeupAttestationDigest": "sha256:<64-lowercase-hex>",
  "lfsAttestationDigest": "sha256:<64-lowercase-hex>",
  "requestDigest": "sha256:<64-lowercase-hex>"
}
```

两份 attestation digest 将此请求绑定至本次本地批准证明，不能省略或由普通
`READY` 状态替代。该请求不含数据库凭据、密钥、原始行、摘录、完整源文件、
租户内容或浏览器 Bundle。它只是未来经单独授权的 capture adapter 的输入；
本任务没有实现真实 capture、网络访问、Git clone/fetch、Git LFS 安装或任何
原始制品写入。
