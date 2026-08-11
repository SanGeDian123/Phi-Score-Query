# Pre-0.9.7.8 服务器升级包

本包更新后端建议区接口、持久化图片目录、SQLite 表结构、Caddy 路由、Phi-Plugin B30/P30 金色光晕，并发布 Pre-0.9.7.8 签名 Android 测试客户端。客户端更多页主体色跟随主题，RKS 草稿会持久化，提升估算会读取账号真实 B27 与 AP3 并按替换线重算；建议区仅允许选择 APP 内生成的 B30/P30 图片，并新增作者删除、评论通知、通知直达、图片放大及正确的本地时间显示。本次后端对应源码也随包提供，部署后可从 `/source` 下载。

## 升级命令

将 `server-upgrade-Pre-0.9.7.8.zip` 放在服务器桌面，以管理员身份打开 PowerShell，可在任意目录执行：

```powershell
$d=[Environment]::GetFolderPath('Desktop'); $z=Join-Path $d 'server-upgrade-Pre-0.9.7.8.zip'; $p=Join-Path $d 'server-upgrade-Pre-0.9.7.8'; if(Test-Path $p){Remove-Item -LiteralPath $p -Recurse -Force}; Expand-Archive -LiteralPath $z -DestinationPath $p -Force; Set-ExecutionPolicy -Scope Process Bypass -Force; & (Join-Path $p 'scripts\Deploy-Pre-0.9.7.8.ps1')
```

脚本会校验包内 SHA-256，备份后端、Caddy、运行脚本、Phi-Plugin 资源及 APP 更新清单，再替换并重启服务。后端启动时会幂等创建建议区数据表；成绩图持久保存在 `C:\Services\PhigrosScore\suggestion-media`，版本升级不会覆盖。

健康检查、建议区鉴权路由、Caddy 或 APK 发布校验失败时会自动回滚程序文件。部署脚本不会提交 GitHub，也不会自动发布公告。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
try { Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/suggestions/random' -UseBasicParsing } catch { $_.Exception.Response.StatusCode.value__ }
```

预期更新清单为 `versionCode 39`、`Pre-0.9.7.8`；未携带登录令牌访问建议区应返回 `401`，而不是 Caddy `404`。
