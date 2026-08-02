# Next-Phi-Backend 修改说明

本目录是 Phi Score Query 服务所使用的 Next-Phi-Backend 对应源码。

## 来源

- 上游仓库：<https://github.com/Sczr0/Next-Phi-Backend>
- 基准提交：`3b167614e916b62b7f84606cb044c0590611a9d7`
- 上游许可证：GNU Affero General Public License v3.0
- 修改者：SanGeDian123 / Phi Score Query contributors
- 修改时间：2026 年 7 月至 8 月

## 主要修改

- 增加客户端使用的完整曲库目录接口及稳定版本标识。
- 扩展排行榜轻量返回字段和相关数据库查询。
- 扩展存档响应，提供客户端成绩比较需要的数据。
- 增加经典与简约 B30 图片模板、版本水印及渲染参数。
- 补充相关 API、渲染和排行榜回归测试。
- 更新部署所用歌曲资料与别名数据。
- 将两处一分钟回退值从 `Duration::from_mins(1)` 等价改写为 `Duration::from_secs(60)`，兼容部署构建工具链。

完整逐行修改可以将本目录与上述基准提交进行比较。发布标签 `pre-0.9.7.1` 对应 Pre-0.9.7.1 公测服务端构建。

## 构建

```powershell
$env:CARGO_TARGET_DIR='D:\PhigrosScoreBuild\backend-target-pre0971'
cargo test --locked
cargo build --locked --profile release-dist --target x86_64-pc-windows-msvc
```

运行时配置以 `config.example.toml` 和仓库 `server/backend/config.toml` 为模板。不得把生产密钥或用户数据写入公开配置。

## 许可证义务

本目录整体继续使用 GNU AGPL v3。分发二进制或通过网络提供修改版服务时，必须保留许可证、修改声明，并向用户提供该版本完整对应源码。
