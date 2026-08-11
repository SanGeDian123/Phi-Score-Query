# GNU AGPL v3 对应源码提供说明

Phi Score Query 的公网服务使用了修改后的 Next-Phi-Backend。任何通过网络与该后端交互的用户，都可以免费获取实际部署版本的对应源码。

## 对应源码位置

- 当前对应源码：Pre-0.9.7.8-Fix 服务器升级包内的 `source/backend-source-Pre-0.9.7.8.zip`
- 上游项目：<https://github.com/Sczr0/Next-Phi-Backend>
- 修改说明：源码归档内的 `MODIFICATIONS.md`

`backend-source/` 包含生成、修改和运行后端所需的源代码、锁定依赖、模板与测试。部署所需的配置模板、构建和安装脚本位于 `server/`。

生产密钥、SessionToken、数据库、日志、TLS 私钥和 Android 签名材料不属于对应源码，也不会公开。

## 网络入口

生产 Caddy 配置在 API 响应中加入 `Link` 和 `X-Source-Code` 响应头，并通过 `/source` 直接提供当前部署版本的源码归档。

本说明不限制用户依据 GNU AGPL v3 获得的任何权利。
