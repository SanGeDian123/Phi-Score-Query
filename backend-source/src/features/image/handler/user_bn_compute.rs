use std::{
    collections::{HashMap, HashSet},
    sync::Arc,
};

use crate::{
    error::AppError,
    features::image::{
        renderer::RenderRecord,
        types::{UserRanking, UserScoreItem},
    },
    song_contract::SongCatalog,
};

use super::score::{
    build_engine_records_from_render_records, calculate_ap_top_3_avg, calculate_best_27_avg,
    calculate_push_acc_map, chart_constant_for_difficulty, collect_ap_top_3_scores,
    is_user_score_full_combo, parse_user_score_difficulty, sort_render_records_by_rks_desc,
    user_score_difficulty_error,
};

pub(super) struct UserBnComputeOutput {
    pub(super) records: Vec<RenderRecord>,
    pub(super) push_acc_map: HashMap<String, crate::rks_contract::engine::PushAccHint>,
    pub(super) exact_rks: f64,
    pub(super) ap_top_3_avg: Option<f64>,
    pub(super) best_27_avg: Option<f64>,
    pub(super) ap_top_3_scores: Vec<RenderRecord>,
}

#[allow(clippy::needless_pass_by_value)]
pub(super) fn build_user_bn_compute_output(
    scores: Vec<UserScoreItem>,
    song_catalog: Arc<SongCatalog>,
    custom: bool,
    ranking: UserRanking,
    phi_plugin: bool,
) -> Result<UserBnComputeOutput, AppError> {
    if custom {
        validate_custom_request(&scores, ranking, phi_plugin)?;
    }
    // 解析成绩并计算 RKS
    let mut records: Vec<RenderRecord> = Vec::with_capacity(scores.len());
    let mut song_lookup_cache = HashMap::<String, Arc<_>>::with_capacity(scores.len());
    let mut seen_charts = HashSet::<(String, String)>::with_capacity(scores.len());
    let mut previous_rks: Option<f64> = None;
    for (idx, item) in scores.iter().enumerate() {
        // Both custom modes allow a top-three chart once in each independent group.
        if custom && idx == 3 {
            seen_charts.clear();
        }
        // 同一次自报请求内可能多次引用同一首歌，避免重复执行模糊/别名搜索。
        let song_lookup_key = item.song.trim();
        let info = if let Some(info) = song_lookup_cache.get(song_lookup_key) {
            Arc::clone(info)
        } else {
            let info = song_catalog
                .search_unique(song_lookup_key)
                .map_err(AppError::Search)?;
            song_lookup_cache.insert(song_lookup_key.to_string(), Arc::clone(&info));
            info
        };
        let Some((difficulty, difficulty_label)) = parse_user_score_difficulty(&item.difficulty)
        else {
            return Err(user_score_difficulty_error(
                idx,
                &info.name,
                &item.difficulty,
            ));
        };
        if custom && !seen_charts.insert((info.id.clone(), difficulty_label.to_string())) {
            return Err(AppError::Validation(format!(
                "scores 第 {} 项与之前选择了相同谱面: {} {}",
                idx + 1,
                info.name,
                difficulty_label,
            )));
        }
        // 定数
        let Some(dv) = chart_constant_for_difficulty(Some(&info.chart_constants), difficulty)
        else {
            return Err(user_score_difficulty_error(
                idx,
                &info.name,
                &item.difficulty,
            ));
        };
        // ACC 统一百分比
        let acc = item.acc;
        // RKS
        let rks = crate::rks_contract::engine::calculate_chart_rks(acc, dv);
        if custom {
            if let Some(previous) = previous_rks
                && idx != 3
                && previous + 1e-9 < rks
            {
                return Err(AppError::Validation(format!(
                    "scores 第 {} 项单曲 RKS 不能高于前一项",
                    idx + 1
                )));
            }
            previous_rks = Some(rks);
        }
        records.push(RenderRecord {
            song_id: info.id.clone(),
            song_name: info.name.clone(),
            difficulty: difficulty_label.to_string(),
            score: item.score.map(f64::from),
            acc,
            rks,
            difficulty_value: dv,
            is_fc: is_user_score_full_combo(item.score, acc),
        });
    }

    // Custom requests contain three dedicated AP slots and 33 independent main slots.
    if custom {
        return Ok(build_custom_output(records));
    }

    // 非自定义请求保持原有用户自报 BestN 行为。
    sort_render_records_by_rks_desc(&mut records);
    let engine_all = build_engine_records_from_render_records(&records);
    let push_acc_map = calculate_push_acc_map(&records, &engine_all, records.len());

    let (exact_rks, _rounded) =
        crate::rks_contract::engine::calculate_player_rks_details(&engine_all);
    let ap_top_3_avg = calculate_ap_top_3_avg(&records);
    let best_27_avg = calculate_best_27_avg(&records);
    let ap_top_3_scores = collect_ap_top_3_scores(&records);

    Ok(UserBnComputeOutput {
        records,
        push_acc_map,
        exact_rks,
        ap_top_3_avg,
        best_27_avg,
        ap_top_3_scores,
    })
}

fn build_custom_output(mut records: Vec<RenderRecord>) -> UserBnComputeOutput {
    let mut main_records = records.split_off(3);
    sort_render_records_by_rks_desc(&mut records);
    sort_render_records_by_rks_desc(&mut main_records);
    let ap_sum = records.iter().map(|record| record.rks).sum::<f64>();
    let best_sum = main_records
        .iter()
        .take(27)
        .map(|record| record.rks)
        .sum::<f64>();
    let engine_all = build_engine_records_from_render_records(
        &records
            .iter()
            .chain(main_records.iter())
            .cloned()
            .collect::<Vec<_>>(),
    );
    UserBnComputeOutput {
        push_acc_map: calculate_push_acc_map(&main_records, &engine_all, main_records.len()),
        exact_rks: (ap_sum + best_sum) / 30.0,
        ap_top_3_avg: Some(ap_sum / 3.0),
        best_27_avg: Some(best_sum / 27.0),
        ap_top_3_scores: records,
        records: main_records,
    }
}

fn validate_custom_request(
    scores: &[UserScoreItem],
    ranking: UserRanking,
    phi_plugin: bool,
) -> Result<(), AppError> {
    if !phi_plugin {
        return Err(AppError::Validation(
            "自定义 BP30 仅支持 Phi-Plugin 样式".into(),
        ));
    }
    let expected = 36;
    if scores.len() != expected {
        return Err(AppError::Validation(format!(
            "自定义 {} 必须正好提交 {} 张谱面，当前为 {} 张",
            match ranking {
                UserRanking::B30 => "B30",
                UserRanking::P30 => "P30",
            },
            expected,
            scores.len()
        )));
    }
    for (idx, item) in scores.iter().enumerate() {
        if !item.acc.is_finite() || !(0.0..=100.0).contains(&item.acc) {
            return Err(AppError::Validation(format!(
                "scores 第 {} 项 ACC 必须在 0 到 100 之间",
                idx + 1
            )));
        }
        if item.score.is_some_and(|score| score > 1_000_000) {
            return Err(AppError::Validation(format!(
                "scores 第 {} 项分数不能超过 1000000",
                idx + 1
            )));
        }
        if (ranking == UserRanking::P30 || idx < 3)
            && (item.score != Some(1_000_000) || (item.acc - 100.0).abs() > f64::EPSILON)
        {
            return Err(AppError::Validation(
                "AP 槽位的分数必须为 1000000，ACC 必须为 100.00%".into(),
            ));
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scores(count: usize, score: Option<u32>, acc: f64) -> Vec<UserScoreItem> {
        (0..count)
            .map(|index| UserScoreItem {
                song: format!("song-{index}"),
                difficulty: "IN".to_string(),
                acc,
                score,
            })
            .collect()
    }

    fn valid_b30() -> Vec<UserScoreItem> {
        let mut values = scores(36, None, 99.0);
        for score in &mut values[..3] {
            score.score = Some(1_000_000);
            score.acc = 100.0;
        }
        values
    }

    #[test]
    fn both_rankings_require_36_and_phi_plugin() {
        for ranking in [UserRanking::B30, UserRanking::P30] {
            let perfect = scores(36, Some(1_000_000), 100.0);
            validate_custom_request(&perfect, ranking, true).unwrap();
            assert!(validate_custom_request(&perfect, ranking, false).is_err());
            for count in [30, 33, 35, 37] {
                assert!(
                    validate_custom_request(&scores(count, Some(1_000_000), 100.0), ranking, true)
                        .is_err()
                );
            }
        }
    }

    #[test]
    fn b30_requires_first_three_perfect_but_main_score_is_optional() {
        let values = valid_b30();
        validate_custom_request(&values, UserRanking::B30, true).unwrap();
        for index in 0..3 {
            let mut invalid = valid_b30();
            invalid[index].score = None;
            assert!(validate_custom_request(&invalid, UserRanking::B30, true).is_err());
            invalid[index].score = Some(1_000_000);
            invalid[index].acc = 99.99;
            assert!(validate_custom_request(&invalid, UserRanking::B30, true).is_err());
        }
        for acc in [f64::NAN, -0.1, 100.1] {
            let mut invalid = valid_b30();
            invalid[35].acc = acc;
            assert!(validate_custom_request(&invalid, UserRanking::B30, true).is_err());
        }
    }

    #[test]
    fn p30_requires_all_36_perfect() {
        for index in [0, 3, 29, 35] {
            let mut invalid = scores(36, Some(1_000_000), 100.0);
            invalid[index].score = Some(999_999);
            assert!(validate_custom_request(&invalid, UserRanking::P30, true).is_err());
        }
    }

    fn record(index: usize, rks: f64) -> RenderRecord {
        RenderRecord {
            song_id: format!("song-{index}"),
            song_name: format!("Song {index}"),
            difficulty: "IN".into(),
            score: Some(1_000_000.0),
            acc: 100.0,
            rks,
            difficulty_value: rks,
            is_fc: true,
        }
    }

    #[test]
    fn b30_accepts_ap_in_best_but_rejects_duplicates_inside_each_group() {
        let mut catalog = SongCatalog::default();
        for i in 0..36 {
            let id = format!("song-{i}");
            catalog.by_id.insert(id.clone(), Arc::new(crate::features::song::models::SongInfo {
                id: id.clone(), name: id, composer: String::new(), illustrator: String::new(),
                chart_constants: crate::startup::chart_loader::ChartConstants {
                    ez: None, hd: None, in_level: Some(16.0), at: None,
                },
            }));
        }
        let catalog = Arc::new(catalog);
        let mut values = scores(36, Some(1_000_000), 100.0);
        for i in 0..3 { values[3 + i].song = values[i].song.clone(); }
        let copy = |items: &[UserScoreItem]| items.iter().map(|item| UserScoreItem {
            song: item.song.clone(), difficulty: item.difficulty.clone(), score: item.score, acc: item.acc,
        }).collect();
        let result = build_user_bn_compute_output(copy(&values), catalog.clone(), true, UserRanking::B30, true).unwrap();
        assert!((result.exact_rks - 16.0).abs() < 1e-9);
        assert_eq!(result.records.len(), 33);
        let p30 = build_user_bn_compute_output(copy(&values), catalog.clone(), true, UserRanking::P30, true).unwrap();
        assert!((p30.exact_rks - 16.0).abs() < 1e-9);
        assert_eq!(p30.records.len(), 33);
        values[6].song = values[3].song.clone();
        assert!(build_user_bn_compute_output(copy(&values), catalog.clone(), true, UserRanking::P30, true).is_err());
        assert!(build_user_bn_compute_output(values, catalog, true, UserRanking::B30, true).is_err());
    }

    #[test]
    fn custom_sections_keep_36_unique_cards_and_exclude_six_overflow() {
        let values = (0..36)
            .map(|index| record(index, 20.0 - index as f64 / 10.0))
            .collect::<Vec<_>>();
        let expected = values[..30].iter().map(|record| record.rks).sum::<f64>() / 30.0;
        let result = build_custom_output(values.clone());
        assert_eq!(result.ap_top_3_scores.len(), 3);
        assert_eq!(result.records.len(), 33);
        assert_eq!(result.ap_top_3_scores[0].song_id, "song-0");
        assert_eq!(result.records[0].song_id, "song-3");
        assert_eq!(result.records[27].song_id, "song-30");
        assert!((result.exact_rks - expected).abs() < 1e-12);
        let mut changed = values;
        for record in &mut changed[30..] {
            record.rks = 0.0;
        }
        assert!((build_custom_output(changed).exact_rks - expected).abs() < 1e-12);
    }
}
