# Pre-0.9.7.12 / v106

客户端包名仍为 `xyz.plcliangpicup.phigrosscore`，公开版本为 `Pre-0.9.7.12`，`versionCode` 为 106。正式 APK 使用原签名，可覆盖此前版本。

## 更新公告

· 还原了部分谱面中的“噪域”；

· 修复了“谱面播放与练习”功能的部分已知问题；

· 将课题模式等级“黄”更改为“金”；

· 玩家排行榜展示数量提升至前1,500名。

· 自定义BP30功能现支持导入已有BP30。

## 实现及验证范围

- RPE/PEZ 谱面通过保留官方根字段 `blockAreaList` 读取噪域。未保留该字段的转谱不会自动补出噪域。实现包括秒时间轴、区域变换、奇偶组合、触摸屏蔽及音乐低通；手动、AUTOPLAY 和预览共用运行时。
- 噪域保留模糊曲绘背景，特效采用有像素上限的独立叠加层与缓存。Android 13 使用完整场景快照与 AGSL，Android 14 及以上使用 RenderEffect，Android 8–12 通过 GLES3 执行相同的片元公式。视觉遮罩最高 30 Hz，输入判定逐帧执行。
- 设置中的“噪域兼容模式”默认关闭，开启后使用按 APK 还原、按视频调整前的 v100 样式；保留插值云纹、区域内火花和触碰 SDF。兼容特效最高 512×288 等效像素，基础遮罩最高 128×72，复用纹理和静态几何。Android 13 及以下首次打开含噪域谱面时显示引导，确认后继续进入谱面。
- 官谱派生 Hold 可保留 `phigrosHoldSpeed`，按剩余秒数和自身速度计算长条；普通 RPE 缺少该字段时使用判定线积分。
- 打击动画使用 APK 中 NewHitFx 的 30 个 Sprite，按裁剪偏移补回原始帧。Perfect 四个方块、Good 三个，独立随机出射，尺寸先增后回、线性淡出、方块不自转。曲线来自 APK 内的打击 prefab；Unity 原生拖曳积分未完整恢复，模拟器以共享积分表近似，在加载阶段预计算。默认资源选择桥接未完整证明，不宣称所有官方玩法模式逐像素一致。
- 排行榜通过分页累计展示前 1,500 名。自定义 BP30 支持导入自己的 B30/P30，输入期间保持槽位，输入法收起或外接键盘输入框失焦后再按 RKS 排序。
- 补齐第九章第二部分七首新曲、27 张谱面的物量和谱师资料。更新包仅合并指定曲目，保留其他曲库数据。

v106 已通过 Release 构建及其必要检查、包名和签名验证；打击特效通过 Native Skia 聚焦验证，服务器升级包通过本地发布与失败回滚验证。这些验证不代表真机实玩或正式服务器部署。

## 构建

需要 JDK 17、Android SDK 36 和自行准备的签名密钥。根据本机路径设置 `JAVA_HOME`，在项目根目录运行：

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

签名配置位于自行创建的 `keystore.properties`，密钥与该文件不纳入版本控制。APK 默认输出至 `app/build/outputs/apk/release/`；维护者可通过 Gradle init script 将构建目录重定向到 D 盘。

## 发布更新

使用配套 `server-upgrade-Pre-0.9.7.12-v106-20261005.zip`，将其上传至服务器桌面。该 ZIP 含 APK、更新清单、七首曲目的 CSV 增量、21 张公开曲绘和部署脚本；不含运行时密钥、数据库或后台启动配置。更新为非强制更新。

用管理员 PowerShell 先校验：

```powershell
Set-ExecutionPolicy -Scope Process Bypass -Force
$releaseDesktop = [Environment]::GetFolderPath('Desktop')
$releaseDir = Join-Path $releaseDesktop ('PSQ-v106-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
$serverRoot = 'C:\Services\PhigrosScore'
Expand-Archive -LiteralPath (Join-Path $releaseDesktop 'server-upgrade-Pre-0.9.7.12-v106-20261005.zip') -DestinationPath $releaseDir -ErrorAction Stop
Set-Location -LiteralPath $releaseDir
& .\scripts\Deploy-Release-Pre-0.9.7.12-v106.ps1 -InstallRoot $serverRoot -ValidateOnly
```

看到 `VALIDATION OK` 后，在同一窗口发布：

```powershell
& .\scripts\Deploy-Release-Pre-0.9.7.12-v106.ps1 -InstallRoot $serverRoot
```

成功标志为 `PUBLISHED OK`，并输出备份目录。实际安装目录不同时修改 `$serverRoot`；本地后端端口不为 3939 时，通过 `-CatalogUri` 指定实际本地曲库地址。脚本会拒绝覆盖等于或高于 106 的发布版本。

本仓库中的部署脚本需要配套 ZIP 的完整目录布局；源码仓库不包含 APK、升级 ZIP、大体积曲绘及维护者本机构建助手。
