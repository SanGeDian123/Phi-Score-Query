use axum::{
    extract::{Query, State},
    response::Response,
};

use crate::{
    error::AppError,
    features::image::{BnMode, RenderP30Request},
    state::AppState,
};

use super::{ImageQueryOpts, bn::render_ranking_image};

#[utoipa::path(
    post,
    path = "/image/p30",
    summary = "生成 P30 成绩图片",
    description = "从玩家存档中筛选 All Perfect 成绩，按单曲 RKS 降序取前 30 张生成 P30 图片。",
    request_body = RenderP30Request,
    params(
        ("format" = Option<String>, Query, description = "输出格式：png|jpeg|webp|svg，默认 png"),
        ("template" = Option<String>, Query, description = "SVG 模板 ID：对应 resources/templates/image/bn/{id}.svg.jinja（不传则使用内置手写 SVG）"),
        ("width" = Option<u32>, Query, description = "目标宽度像素：按宽度同比例缩放"),
        ("webp_quality" = Option<u8>, Query, description = "WebP 质量：1-100（仅在 format=webp 时有效，默认 80）"),
        ("webp_lossless" = Option<bool>, Query, description = "WebP 无损模式（仅在 format=webp 时有效，默认 false）")
    ),
    responses(
        (
            status = 200,
            description = "P30 图片（由 query format 决定）",
            content(
                (crate::features::image::types::BinaryImage = "image/png"),
                (crate::features::image::types::BinaryImage = "image/jpeg"),
                (crate::features::image::types::BinaryImage = "image/webp"),
                (String = "image/svg+xml")
            )
        ),
        (status = 400, description = "请求参数错误/认证缺失", body = crate::error::ProblemDetails),
        (status = 422, description = "参数校验失败/渲染错误", body = crate::error::ProblemDetails),
        (status = 500, description = "服务器内部错误", body = crate::error::ProblemDetails)
    ),
    tag = "Image"
)]
pub async fn render_p30(
    State(state): State<AppState>,
    Query(q): Query<ImageQueryOpts>,
    request: axum::extract::Request,
) -> Result<Response, AppError> {
    let (mut req, bearer_state) =
        crate::session_auth::parse_json_with_bearer_state::<RenderP30Request>(request).await?;
    crate::session_auth::merge_auth_from_bearer_if_missing(
        state.stats_storage.as_ref(),
        &bearer_state,
        &mut req.auth,
    )
    .await?;

    render_ranking_image(
        state,
        q,
        req.into(),
        bearer_state,
        BnMode::P30,
        "/image/p30",
    )
    .await
}
