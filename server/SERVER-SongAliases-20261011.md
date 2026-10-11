# 曲目别名管理服务器升级包

交付文件：`server-only-upgrade-SongAliases-20261011.zip`。

本包包含当前后端 Release 程序、对应 AGPL 源码、部署与回滚脚本，以及完整管理台源码和本地启动入口。不包含 APK、APP 更新清单、私有配置、用户数据库、运行时别名数据或依赖缓存。

## 本次效果

- 管理台“曲目别名”页面可选择指定曲目，查看、添加和删除已有别名，保存到后端。
- 管理员保存成功后，后端立即更新搜索使用的别名索引，随后请求即可使用新别名；重启后保留修改。
- 每首曲目的管理结果以完整别名列表保存至运行时 `info/song-alias-overrides.json`，在原有 `nicklist.yaml` 基线上覆盖对应曲目。删除全部别名会保存空列表，不会被原始别名重新补回。
- 管理接口 `GET/PUT /api/v2/admin/songs/aliases` 使用现有 `X-Admin-Token`。普通用户不能读取或修改管理员别名列表。
- 后端程序从当前工作区构建，保留已有后端功能。管理台仍独立在管理员电脑运行。

**部署不替换整个 `info` 目录，不回退服务器已有曲库，也不覆盖已经保存的别名。** 源码归档内的公开 `info` 文件仅供源码复现，不会解压到服务目录。无须手工建表或数据库迁移。

## 服务器桌面部署命令

将 ZIP 上传到 **Windows 服务器桌面**，以管理员身份打开 PowerShell，执行下面整段命令。默认安装目录为 `C:\Services\PhigrosScore`，现有计划任务为 `PhigrosScore-Backend` 和 `PhigrosScore-Caddy`。

```powershell
& {
    $ErrorActionPreference = 'Stop'
    Set-ExecutionPolicy -Scope Process Bypass -Force
    $aliasDesktop = [Environment]::GetFolderPath('Desktop')
    $aliasZip = Join-Path $aliasDesktop 'server-only-upgrade-SongAliases-20261011.zip'
    $aliasDir = Join-Path $aliasDesktop ('PSQ-SongAliases-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8))
    Expand-Archive -LiteralPath $aliasZip -DestinationPath $aliasDir
    $aliasDeploy = Join-Path $aliasDir 'scripts\Deploy-SongAliases.ps1'
    & $aliasDeploy -InstallRoot 'C:\Services\PhigrosScore' -ValidateOnly
    & $aliasDeploy -InstallRoot 'C:\Services\PhigrosScore'
    Write-Output "解压目录：$aliasDir"
}
```

安装目录不同，修改两处 `-InstallRoot`。仅校验时去掉正式部署一行。`-ValidateOnly` 只读取包和已安装的 Caddy 配置，解析候选配置，不启动服务、不写服务器文件。校验成功显示 `VALIDATION OK`；部署成功显示 `DEPLOYED OK` 和备份路径。

脚本从已安装的 `/source` 规则读取源码下载文件名，备份并替换后端程序和该源码归档。没有专用别名规则时，只在现有管理台规则前增补 `@songAliasesApi` 和对应代理处理器，请求体上限为 128KB；已有符合要求的专用规则则保留原文。其他接口的请求体上限和 Caddy 规则不变。无法识别现有规则时中止，不会用包内旧配置覆盖服务器。

部署重启现有两个计划任务，检查本机和公网健康，并确认新接口在未携带管理员令牌时返回 `401`。失败时恢复程序、源码归档及 Caddy 配置，并重启原服务。整个 `info` 目录、数据库、私有配置、曲绘、谱面和 `app-update` 目录均保留。部署会短暂重启服务，建议在空闲时段执行。

## 在自己电脑运行管理台

后端升级成功后，将包解压到管理员自己的电脑，关闭原管理台，双击 `admin-console\启动APP后台网页.cmd`，或在解压目录打开 PowerShell 执行：

```powershell
& .\admin-console\psq-admin-web\Start-AdminWeb.ps1
```

首次自动安装依赖，需要 Node.js 22.13.0 或更新版本。继续填写现有服务器地址和管理员令牌，在“曲目别名”中选择曲目并管理别名。保存成功即同步服务器，无须重新部署后端。

已有本地管理台目录时，关闭旧页面服务，用包内 `admin-console\psq-admin-web` 源码覆盖原同名文件，保留自己的环境配置，再重新启动。避免旧服务占用 3000 端口而继续显示旧页面。

本包不会自动发布独立管理台站点。源码中的 `.openai/hosting.json` 为现有非敏感项目配置，不含管理员令牌；私有环境文件和依赖缓存不随包交付。

## 本地验证与生产状态

交付前检查后端和管理台构建、别名管理接口回归，以及服务器包结构和 Windows PowerShell 5.1 脚本解析。`-ValidateOnly` 使用真实 Caddy 检查语法；隔离部署夹具检查目标保留和失败恢复，不代表正式服务器已升级。

本次交付不在正式服务器执行部署、不发布 APP 或管理台站点，也不计算文件哈希。GitHub 提交范围及提交编号以交付说明为准；服务器二进制、升级 ZIP、私有配置和运行时别名数据不作为开源文件提交。
