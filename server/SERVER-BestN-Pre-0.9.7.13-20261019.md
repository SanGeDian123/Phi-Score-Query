# Best N 服务器升级包 · Pre-0.9.7.13

此包为 **不发布 APP 更新** 的服务器升级包。APK 单独交付，包内不含 APK、`latest.json` 或 APP 更新发布脚本，不修改现有更新公告与更新清单。本次只提供升级包，尚未部署到生产服务器。

## 更新内容

- 新增 `/api/v2/image/bestn`，支持经典、简约和 Phi-Plugin 三种 Best N 图片。
- N 限制为 1–200；超限请求在获取存档、查询图片缓存和排队渲染前拒绝。
- Best N 仅展示按单谱 RKS 排序的前 N 张成绩，不额外添加 P3、B30 溢出区或直方图。
- 更新简约模板的 Best N 标题，保留已有 Phi-Plugin 字体、光照与 B30 直方图优化。
- 只向服务器现有 Caddy 配置增加 Best N 路由；保留其他路由。

## 使用方法

把 ZIP 上传服务器，解压至独立目录，例如 `C:\PSQ-BestN-Upgrade`。目录内应直接出现 `backend`、`source`、`scripts`。请勿直接解压覆盖正在运行的服务目录。

在服务器管理员 PowerShell 中先校验：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\PSQ-BestN-Upgrade\scripts\Deploy-BestN-Pre-0.9.7.13.ps1" -ValidateOnly
```

显示 `VALIDATION OK` 后执行升级：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "C:\PSQ-BestN-Upgrade\scripts\Deploy-BestN-Pre-0.9.7.13.ps1"
```

默认安装目录为 `C:\Services\PhigrosScore`，计划任务为 `PhigrosScore-Backend`、`PhigrosScore-Caddy`。自定义目录使用 `-InstallRoot`；任务名分别使用 `-BackendTaskName`、`-CaddyTaskName`。

脚本会校验包内容、对应源码和补丁后的 Caddy 语法，备份至安装目录 `backup\bestn-pre09713-*`，再短暂重启后端与 Caddy。健康检查或 Best N 路由检查失败时自动恢复原文件。服务器升级不改动 APP 数据。

## 保留的服务器内容

- `app-update`、APK、更新清单与公告目录。
- 私有配置、密钥、数据库、用户数据、运行时曲库和别名。
- 曲绘、谱面资源、PWA 和独立管理后台。
- 已存在的模板布局 `.json`；仅在缺少布局文件时补齐随包默认值。

图片模板、字体与后端二进制按随包源码更新；`/source` 对应的源码 ZIP 沿用服务器原下载文件名。此包不包含运行时曲库替换操作。

本地构建、测试及 `-ValidateOnly` 结果仅代表交付包验证，不代表生产部署或真机验证。
