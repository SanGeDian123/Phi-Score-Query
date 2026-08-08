use serde::{Deserialize, Serialize};

use crate::auth_contract::UnifiedSaveRequest;

/// 渲染主题
#[derive(Debug, Clone, Copy, Serialize, Deserialize, utoipa::ToSchema, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
#[derive(Default)]
pub enum Theme {
    #[serde(alias = "white", alias = "WHITE")]
    White,
    #[serde(alias = "black", alias = "BLACK")]
    #[default]
    Black,
}

/// 成绩图内部采用的成绩筛选口径。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BnMode {
    /// P3 + Best 27。
    B30,
    /// 单曲 RKS 最高的 30 张 All Perfect。
    P30,
}

/// BN 渲染请求体
#[derive(Debug, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct RenderBnRequest {
    /// 认证方式（二选一）：sessionToken 或 externalCredentials
    #[serde(flatten)]
    pub auth: UnifiedSaveRequest,
    /// 取前 N 条 RKS 最高的成绩（默认 30）
    #[schema(example = 30)]
    #[serde(default = "default_n")]
    pub n: u32,
    /// 渲染主题：white/black（默认 black）
    #[serde(default)]
    pub theme: Theme,
    /// 是否将封面等资源内嵌到 PNG（默认为 false）
    #[serde(default)]
    pub embed_images: bool,
    /// 可选：用于显示的玩家昵称（若未提供且无法从服务端获取，将使用默认占位）
    #[serde(skip_serializing_if = "Option::is_none")]
    pub nickname: Option<String>,
    /// 可选：发起生图请求的 APP 版本，用于生成动态版本水印
    #[serde(skip_serializing_if = "Option::is_none")]
    pub app_version: Option<String>,
}

/// P30 渲染请求体。P30 固定取单曲 RKS 最高的 30 张 All Perfect。
#[derive(Debug, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct RenderP30Request {
    /// 认证方式（二选一）：sessionToken 或 externalCredentials
    #[serde(flatten)]
    pub auth: UnifiedSaveRequest,
    /// 渲染主题：white/black（默认 black）
    #[serde(default)]
    pub theme: Theme,
    /// 是否将封面等资源内嵌到 PNG（默认为 false）
    #[serde(default)]
    pub embed_images: bool,
    /// 可选：用于显示的玩家昵称
    #[serde(skip_serializing_if = "Option::is_none")]
    pub nickname: Option<String>,
    /// 可选：发起生图请求的 APP 版本，用于生成动态版本水印
    #[serde(skip_serializing_if = "Option::is_none")]
    pub app_version: Option<String>,
}

impl From<RenderP30Request> for RenderBnRequest {
    fn from(value: RenderP30Request) -> Self {
        Self {
            auth: value.auth,
            n: 30,
            theme: value.theme,
            embed_images: value.embed_images,
            nickname: value.nickname,
            app_version: value.app_version,
        }
    }
}

/// 单曲渲染请求体
#[derive(Debug, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct RenderSongRequest {
    /// 认证方式（二选一）：sessionToken 或 externalCredentials
    #[serde(flatten)]
    pub auth: UnifiedSaveRequest,
    /// 歌曲 ID 或名称
    #[schema(example = "Arcahv")]
    pub song: String,
    /// 是否将封面等资源内嵌到 PNG（默认为 false）
    #[serde(default)]
    pub embed_images: bool,
    /// 可选：用于显示的玩家昵称
    #[serde(skip_serializing_if = "Option::is_none")]
    pub nickname: Option<String>,
}

fn default_n() -> u32 {
    30
}

/// 用户自定义 BN 渲染请求（未验证成绩）
#[derive(Debug, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct RenderUserBnRequest {
    /// 主题（默认 black）
    #[serde(default)]
    pub theme: Theme,
    /// 可选昵称（未提供时可从 users/me 获取）
    #[serde(skip_serializing_if = "Option::is_none")]
    pub nickname: Option<String>,
    /// 解除水印的口令（匹配配置或动态口令时，显式/隐式水印均关闭）
    #[serde(skip_serializing_if = "Option::is_none")]
    pub unlock_password: Option<String>,
    /// 成绩列表
    pub scores: Vec<UserScoreItem>,
}

#[derive(Debug, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct UserScoreItem {
    /// 歌曲 ID 或名称
    pub song: String,
    /// 难度（EZ/HD/IN/AT）
    pub difficulty: String,
    /// ACC 百分比（示例：98.50）
    pub acc: f64,
    /// 分数（可选）
    #[serde(skip_serializing_if = "Option::is_none")]
    pub score: Option<u32>,
}

/// OpenAPI 文档用：二进制图片响应体。
///
/// - 实际返回为原始字节（非 base64），具体类型由响应头 `Content-Type` 决定。
/// - 主要用于提示 OpenAPI / SDK：`image/png` / `image/jpeg` / `image/webp`。
#[allow(dead_code)]
#[derive(Debug, Clone, utoipa::ToSchema)]
#[schema(value_type = String, format = Binary)]
pub struct BinaryImage(pub Vec<u8>);
