# Pre-0.9.7.1 服务器升级

该升级包同时更新：

- Pre-0.9.7.1 Release APK 与 `latest.json`
- 从公开 `backend-source/` 构建的 `phi-backend.exe`
- 运行时曲库和简约 B30 模板
- 带 AGPL 源码响应头和 `/source` 跳转的 Caddyfile
- 后端 AGPL 许可证、源码提供说明和完整对应源码压缩包

## 升级步骤

1. 将 `server-upgrade-Pre-0.9.7.1.zip` 和同名 `.sha256` 上传到 Windows 服务器。
2. 核对 SHA-256 后解压到一个新的临时目录。
3. 以管理员身份打开 PowerShell，在解压目录运行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\scripts\Deploy-Pre-0.9.7.1.ps1
```

脚本会先校验包内每个文件和 Caddy 配置，备份现有后端、Caddy、模板、许可证及应用更新清单，再停止服务完成替换。部署后会验证：

- 本机后端健康状态和 312 首曲库
- 公网 `/health`
- `Link`、`X-Source-Code` AGPL 源码响应头
- 公网 `/source` 固定版本跳转
- Pre-0.9.7.1 更新清单和 APK 哈希

任一步骤失败会自动恢复原文件并重新启动服务。备份保存在 `C:\Services\PhigrosScore\backup\pre-0.9.7.1-*`。

## 对应源码

固定版本源码：

<https://github.com/SanGeDian123/Phi-Score-Query/tree/pre-0.9.7.1/backend-source>

升级包内也附带 `source/backend-source-pre-0.9.7.1.zip`，其内容与用于构建升级包后端二进制的公开目录一致。
