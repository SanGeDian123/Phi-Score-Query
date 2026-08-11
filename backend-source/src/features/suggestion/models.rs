use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct SuggestionAuthor {
    pub nickname: String,
    pub avatar: Option<String>,
    pub challenge_mode_rank: Option<i64>,
    pub rks: f64,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct SuggestionComment {
    pub id: String,
    pub text: String,
    pub image_url: Option<String>,
    pub author: SuggestionAuthor,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
#[serde(rename_all = "camelCase")]
pub struct SuggestionPost {
    pub id: String,
    pub description: String,
    pub image_url: String,
    pub author: SuggestionAuthor,
    pub created_at: String,
    pub comments: Vec<SuggestionComment>,
}

#[derive(Debug, Clone)]
pub struct SuggestionPostRecord {
    pub id: String,
    pub description: String,
    pub image_name: String,
    pub nickname: String,
    pub avatar: Option<String>,
    pub challenge_mode_rank: Option<i64>,
    pub rks: f64,
    pub created_at: String,
}

#[derive(Debug, Clone)]
pub struct SuggestionCommentRecord {
    pub id: String,
    pub text: String,
    pub image_name: Option<String>,
    pub nickname: String,
    pub avatar: Option<String>,
    pub challenge_mode_rank: Option<i64>,
    pub rks: f64,
    pub created_at: String,
}
