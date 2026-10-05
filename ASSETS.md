# 素材与数据授权边界

根目录 Apache-2.0 和后端 AGPL v3 许可证只覆盖相应的软件代码，不自动授予任何第三方商标、游戏内容、美术、字体或数据权利。

## 不属于代码许可证的内容

- Phigros、Pigeon Games、TapTap 的名称和商标
- 曲名、谱师、章节、谱面物量和其他游戏资料
- 曲绘、头像、课题模式图形及其他游戏美术
- 练习播放使用的 Phigros 音符、击打素材和噪域贴图；`practice/noise_map.png`、`noise_spark.png`、`noise_displace.png`、`noise_touch_hover.png` 分别来自 4.0.1 的 `FD_Noise_00000`、`PointNoise`、`BlockNoise1`、`Round10_Blur4`。前三者保留原 Texture2D 的 Point 过滤；云纹／触碰噪声为 Mirror 平铺，火花为 Repeat。指尖悬停遮罩保留 Bilinear／Clamp；区域遮罩使用 Point，独立边缘／发光纹理使用 Bilinear。噪域 AGSL 按该 APK 的 GLES3 着色器运算移植。
- `practice/hit_fx.png` 使用 Phigros 4.0.1 `NewHitFx` 动画对应的 30 个 Sprite，按裁剪偏移补回 256×256 帧并排列为 6×5 图集；素材权利仍归原权利人所有。粒子配置来自安装包内的 perfect/good prefab，运动积分是模拟器实现。
- `phi-plugin-ill` 等外部资源仓库中的文件

这些内容的权利归各自权利人所有。公开仓库不应被理解为对这些内容进行 Apache-2.0 或 AGPL 再许可。

服务器运行所需的大体积曲绘和头像资源未提交到 Git 仓库。构建或部署者必须自行确认其使用和分发权限。

## 字体

客户端使用由 Source Han Sans 与 Saira 字形组成的字体文件。这两个字体家族以 SIL Open Font License 1.1 发布；对应许可证文本随 APK 放在 `app/src/main/assets/licenses/OFL-1.1.txt`。保留字体时必须遵守 OFL，并确认任何重新生成的混合字体没有违反保留字体名称条款。
