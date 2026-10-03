# 服务器控制谱面练习入口

配套客户端：Pre-0.9.7.11 / versionCode 87。先部署配套后端，再安装 APK。
本次服务器包不发布 APP 更新，不修改 `latest.json`、Caddy、数据库或凭据。

后端读取 `C:\Services\PhigrosScore\app-update\practice-charts` 中的 `.pez` 文件，
通过 `GET /api/v2/songs/catalog?practice=true` 返回当前可用谱面。
复用现有曲库路由，因此不需要更改 Caddy 白名单或重启网关。
默认从后端安装路径推导资源目录；自定义部署可设置 `APP_PRACTICE_CHART_DIR`，
或由 `APP_UPDATE_DIR` 指向现有 app-update 目录。

## 新增与下架

1. 将完整的 **RPE 格式 PEZ** 上传至上述目录，建议先使用 `.upload` 后缀，完成后改为 `.pez`。
2. 压缩包必须包含 `chart.json`，其中有 `BPMList`、`judgeLineList`、`META`，且 `META.song`、
   `META.background` 指向包内存在的音频和曲绘。
3. `META.name` 应与服务器曲库名称匹配，`META.level` 以 `EZ`、`HD`、`IN` 或 `AT` 开头。
   重名曲目可在 `META.songId` 指定服务器曲目 ID。定数读取服务器曲库对应难度。
4. 下架时移出该目录，或将扩展名改为 `.pez.disabled`。无需重启后端，也无需发布 APK。

客户端进入单曲页、回到前台时刷新；停留时每 15 秒刷新。服务器成功返回空清单会隐藏全部入口。
网络失败或后端尚未升级时同样不显示入口，不使用旧的硬编码清单兜底。
同曲难度按 EZ、HD、IN、AT 排序，IN 始终在 AT 之前。
本地已下载谱面及下架谱面仍可通过“谱面资源管理”清理。

普通官谱 JSON 格式 PEZ 需要先转为 RPE；缺少音频、曲绘或无法对应曲库的文件不会显示。
此次包中的 Rrhar'il 两谱已转换。部署脚本保留其余现有谱面。

## 调速实现

参考 Phira `prpr/src/scene/game.rs` 的 `MusicParams.playback_rate` 和
`prpr/src/time.rs` 的连续时间校正思路，独立实现采样率调速与平滑音频时钟。
播放范围仍为 0.5×–2.0×，音高随速度改变。移除 Android 实时保音高处理，
音频输出保持缓冲余量；输出停滞时停止推进，跳转时重置时钟。
手动判定和 AUTOPLAY 使用相同的音频时间基准。没有复制 Phira 的代码。

本地测试不能替代真机验证，需在实际设备上体验慢速、快速、切速和循环播放。
