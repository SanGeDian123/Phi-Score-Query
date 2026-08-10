# APP 公告服务器部署与发布

## 单独部署公告模块

如果不使用完整的 Pre-0.9.7.7 服务器升级包，可将
`app-announcement-server-deploy-Pre-0.9.7.7.zip` 放到服务器桌面，以管理员身份打开 PowerShell并执行：

```powershell
$d=[Environment]::GetFolderPath('Desktop'); $z=Join-Path $d 'app-announcement-server-deploy-Pre-0.9.7.7.zip'; $p=Join-Path $d 'app-announcement-server-deploy-Pre-0.9.7.7'; Expand-Archive -LiteralPath $z -DestinationPath $p -Force; Set-ExecutionPolicy -Scope Process Bypass -Force; & (Join-Path $p 'scripts\Deploy-AppAnnouncement.ps1')
```

脚本会备份并更新 Caddy 配置、Caddy 启动脚本和公告发布脚本，创建公告目录并重启 Caddy；失败时自动回滚。部署过程不会自动发布公告。

## 发布公告

服务器完成 Pre-0.9.7.7 配套升级后，管理员可直接发布公告，无需重新构建或更新 APK。

```powershell
& 'C:\Services\PhigrosScore\current\scripts\Publish-AppAnnouncement.ps1' `
  -Title '公告标题' `
  -Body "公告正文`n可包含多行内容"
```

脚本每次会生成新的公告 ID，并原子替换：

`C:\Services\PhigrosScore\app-announcement\latest.json`

客户端按公告 ID 记录已读状态。同一个公告仅在发布后用户首次打开 APP 时弹出；再次执行脚本会产生新 ID，因此所有用户下次打开 APP 时会收到新公告。

发布后可验证：

```powershell
Invoke-RestMethod 'https://api.plc-liangpi-cup.xyz/app-announcement/latest.json'
```
