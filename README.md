# Phi Score Query

Phi Score Query 是一个面向 Android 8.0 及以上系统的非官方 Phigros 成绩查询客户端。当前版本为 **Pre-0.9.7.8-Fix 公开测试版**。

项目提供 Android 客户端，以及线上服务实际使用的 Next-Phi-Backend 修改版对应源码。

> 本项目与 Pigeon Games、TapTap 无隶属或授权关系。请勿在 Issue、日志、截图或其他公开位置提交 SessionToken、Access Token 或其他账号凭据。

## 功能

- 大陆版 TapTap 扫码和 SessionToken 登录
- B30、P30、Best N等成绩查询
- 单曲查询、定数表和排行榜
- 多种样式（经典、简约、Phi-Plugin）的 B30/P30 图片生成、保存与分享
- RKS 计算器，支持真实 B30 替换线提升估算和自定义 B30/P30
- 求建议/给建议成绩图交流区，支持作者删除、评论通知与图片放大
- 服务器曲库同步与离线缓存
- 应该能用的在线更新系统(我自己能用😋)

## 项目结构

- `app/`：Kotlin、Jetpack Compose Android 客户端
- `server/`：Caddy 配置、构建和服务器部署脚本
- `backend-source/`：线上后端对应源码，基于 [Sczr0/Next-Phi-Backend](https://github.com/Sczr0/Next-Phi-Backend) 修改

## 构建客户端

需要 JDK 17 和 Android SDK：

```powershell
$env:JAVA_HOME='D:\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

Release 构建需要自行创建 `keystore.properties` 和签名密钥；这些文件不会提交到仓库。

## 后端对应源码

`backend-source/` 基于上游提交 `3b167614e916b62b7f84606cb044c0590611a9d7`，包含当前部署所需的功能修改、测试、模板和曲库接口。具体修改见 [backend-source/MODIFICATIONS.md](backend-source/MODIFICATIONS.md)。

公网 API 通过响应头和 `/source` 路由向用户提供该目录的固定版本链接。完整 AGPL 源码提供说明见 [SOURCE_OFFER.md](SOURCE_OFFER.md)。

## 许可证

本仓库不是单一许可证项目：

- Android 客户端及未另行声明的项目原创内容：Apache License 2.0
- `server/`：GNU Affero General Public License v3.0
- `backend-source/`：GNU Affero General Public License v3.0，并保留上游许可和版权声明
- 字体、图标、曲绘、头像和游戏资料：不自动纳入上述代码许可证，详见 [ASSETS.md](ASSETS.md)
- 第三方软件声明见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)

使用或分发前请同时阅读根目录、`server/` 和 `backend-source/` 中的许可证文件。

## 隐私与安全

- [隐私说明](PRIVACY.md)
- [安全策略](SECURITY.md)
- [贡献指南](CONTRIBUTING.md)

发现安全问题时请不要创建公开 Issue，按照安全策略中的方式联系相关维护者。
