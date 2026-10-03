# RKS 猜猜乐紧急停用与后端恢复

此应急包不会替换 APK、后端程序或 APP 在线更新文件。脚本只执行以下操作：

1. 备份当前 `Caddyfile`；
2. 将 RKS 猜猜乐 API 改为由 Caddy 直接返回 HTTP 503「暂未开放」；
3. 验证 Caddy 配置后重启 Caddy；
4. 启动现有后端，并验证本机、公网健康检查以及小游戏拦截状态。

请在服务器管理员 PowerShell 中，从解压后的目录执行：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
.\Disable-RksGuess-And-Recover.ps1
```

成功时会输出 `RECOVERY_OK`、两个健康检查 `200`，以及 `RKS Guess: 503 暂未开放`。

在隔离环境完成真实认证存档开局验证前，不要重新开放 RKS 猜猜乐路由。
