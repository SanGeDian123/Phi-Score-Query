# Pre-0.9.7.7 服务器升级包

本包更新 Next-Phi-Backend、Phi-Plugin B30/P30 渲染素材、Caddy 公告静态路由，并发布 Pre-0.9.7.7 签名 Android 客户端与更新清单。

## 升级命令

将 `server-upgrade-Pre-0.9.7.7.zip` 放在服务器桌面，以管理员身份打开 PowerShell，可在任意目录执行：

```powershell
$d=[Environment]::GetFolderPath('Desktop'); $z=Join-Path $d 'server-upgrade-Pre-0.9.7.7.zip'; $p=Join-Path $d 'server-upgrade-Pre-0.9.7.7'; Expand-Archive -LiteralPath $z -DestinationPath $p -Force; Set-ExecutionPolicy -Scope Process Bypass -Force; & (Join-Path $p 'scripts\Deploy-Pre-0.9.7.7.ps1')
```

脚本会先校验包内 SHA-256，备份当前后端、Caddy、Phi-Plugin 资源、发布脚本和 APP 更新清单，再替换文件并重启服务。健康检查或发布校验失败时会自动回滚。

部署脚本不会自动发布公告。公告功能部署完成后，由管理员另行执行公告发布命令。

## 发布后验证

```powershell
$latest = Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-update/latest.json'
$latest | Format-List versionCode, versionName, apkUrl, sha256, sizeBytes, changelog
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/health'
Invoke-WebRequest 'https://api.plc-liangpi-cup.xyz/app-announcement/latest.json' -UseBasicParsing
```

在尚未发布任何公告时，公告地址返回 `404` 属于正常现象。预期 APP 更新清单为 `versionCode 35`、`Pre-0.9.7.7`。

## 发布公告

```powershell
& 'C:\Services\PhigrosScore\current\scripts\Publish-AppAnnouncement.ps1' `
  -Title '公告标题' `
  -Body "公告正文`n可包含多行内容"
```

## 本版本公开更新说明

- 修复左侧导航栏在部分设备上无法上下滑动的问题。
- B30、P30 新增 Phi-Plugin 图片样式，并改为下拉式样式选择。
- 定数表新增 EZ、HD、IN、AT 多选难度筛选。
