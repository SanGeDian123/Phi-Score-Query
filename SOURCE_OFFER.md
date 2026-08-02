# GNU AGPL v3 对应源码提供说明

Phi Score Query 的公网服务使用了修改后的 Next-Phi-Backend。任何通过网络与该后端交互的用户，都可以免费获取实际部署版本的对应源码。

## 对应源码位置

- 固定发布标签：<https://github.com/SanGeDian123/Phi-Score-Query/tree/pre-0.9.7.1/backend-source>
- 上游项目：<https://github.com/Sczr0/Next-Phi-Backend>
- 修改说明：<https://github.com/SanGeDian123/Phi-Score-Query/blob/pre-0.9.7.1/backend-source/MODIFICATIONS.md>

`backend-source/` 包含生成、修改和运行后端所需的源代码、锁定依赖、模板与测试。部署所需的配置模板、构建和安装脚本位于 `server/`。

生产密钥、SessionToken、数据库、日志、TLS 私钥和 Android 签名材料不属于对应源码，也不会公开。

## 网络入口

生产 Caddy 配置在 API 响应中加入 `Link` 和 `X-Source-Code` 响应头，并提供 `/source` 路由跳转至上述固定标签。

本说明不限制用户依据 GNU AGPL v3 获得的任何权利。
