# 贡献指南

感谢参与 Phi Score Query 公开测试。

## 提交问题

- 先确认问题可在最新公开测试版复现。
- 提供系统版本、应用版本、操作步骤和已脱敏日志。
- 不要上传 SessionToken、Authorization 请求头、二维码内容、用户存档或服务器密钥。
- 涉及安全风险时按照 `SECURITY.md` 私下报告。

## 代码贡献

1. 从公开仓库创建分支。
2. Android 修改至少运行 `:app:testDebugUnitTest` 和 `:app:assembleDebug`。
3. 后端修改必须基于 `backend-source/`，运行相关 Rust 测试并更新 `MODIFICATIONS.md`。
4. 不提交 APK、构建缓存、数据库、日志、密钥、签名文件或无授权素材。
5. 提交即表示你有权按对应目录的许可证贡献代码。

Android 客户端贡献采用 Apache-2.0；`server/` 和 `backend-source/` 贡献采用 GNU AGPL v3。
