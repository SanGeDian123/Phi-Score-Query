# Pre-0.9.7.8-Fix 服务器升级包

本包发布 `versionCode 40`、`Pre-0.9.7.8-Fix` 签名 Android 客户端，修复 Pre-0.9.7.8 Release 打开即崩溃的问题，并包含 Pre-0.9.7.8 的后端建议区、Phi-Plugin 金色光晕及其余功能。

## 升级命令

将 `server-upgrade-Pre-0.9.7.8-Fix.zip` 放在服务器桌面，以管理员身份打开 PowerShell，可在任意目录执行：

```powershell
$d=[Environment]::GetFolderPath('Desktop'); $z=Join-Path $d 'server-upgrade-Pre-0.9.7.8-Fix.zip'; $p=Join-Path $d 'server-upgrade-Pre-0.9.7.8-Fix'; if(Test-Path $p){Remove-Item -LiteralPath $p -Recurse -Force}; Expand-Archive -LiteralPath $z -DestinationPath $p -Force; Set-ExecutionPolicy -Scope Process Bypass -Force; & (Join-Path $p 'scripts\Deploy-Pre-0.9.7.8-Fix.ps1')
```

脚本会先停止 Caddy，再发布 APK 与更新清单；内容相同时直接复用，遇到短暂文件锁时自动等待。APP 更新校验通过后才重新启动 Caddy。后端、Caddy、Phi-Plugin 资源及旧更新清单都会在替换前备份，失败时自动回滚。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
try { Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/api/v2/suggestions/random' -UseBasicParsing } catch { $_.Exception.Response.StatusCode.value__ }
```

预期更新清单为 `versionCode 40`、`Pre-0.9.7.8-Fix`；建议区未登录请求应返回 `401`。
