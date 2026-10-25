use std::sync::{Arc, OnceLock};
use crate::startup::chart_loader::{ChartConstantsMap, parse_chart_constants};
use crate::features::image::renderer::RenderRecord;

pub(super) const P1_RULES_VERSION: &str = "p1-v2";

// Same order as Android selectTimeRewindPhi: constant descending, song ID, EZ/HD/IN/AT.
pub(super) fn select_phi(records: &[RenderRecord]) -> Option<&RenderRecord> {
    let difficulty_rank = |difficulty: &str| ["EZ", "HD", "IN", "AT"].iter()
        .position(|label| label.eq_ignore_ascii_case(difficulty)).unwrap_or(4);
    records.iter().filter(|r| r.score == Some(1_000_000.0)).min_by(|a, b| {
        b.difficulty_value.total_cmp(&a.difficulty_value)
            .then_with(|| a.song_id.encode_utf16().cmp(b.song_id.encode_utf16()))
            .then_with(|| difficulty_rank(&a.difficulty).cmp(&difficulty_rank(&b.difficulty)))
    })
}

pub(super) fn constants() -> &'static Arc<ChartConstantsMap> {
    static CONSTANTS: OnceLock<Arc<ChartConstantsMap>> = OnceLock::new();
    CONSTANTS.get_or_init(|| Arc::new(parse_chart_constants(
        include_bytes!(concat!(env!("CARGO_MANIFEST_DIR"), "/resources/difficulty_20250214.csv")).as_slice()
    ).expect("bundled 20250214 constants must be valid")))
}

// Records are already sorted by chart RKS. Unfilled slots contribute zero.
pub(super) fn ranking_rks(records: &[RenderRecord]) -> f64 {
    let best = records.iter().take(19).map(|r| r.rks).sum::<f64>();
    let phi = select_phi(records).map_or(0.0, |r| r.rks);
    (best + phi) / 20.0
}

pub(in crate::features::image) fn push_hints(records: &[RenderRecord], count: usize) -> std::collections::HashMap<String, crate::rks_contract::engine::PushAccHint> {
    use crate::rks_contract::engine::{PushAccHint, calculate_chart_rks};
    let current = ranking_rks(records);
    let target = (current * 100.0).floor() / 100.0 + if (current * 1000.0) % 10.0 >= 5.0 { 0.015 } else { 0.005 };
    records.iter().take(count).enumerate().map(|(index, r)| {
        let at = |acc: f64| {
            let candidate = calculate_chart_rks(acc, r.difficulty_value);
            let mut best = records.iter().enumerate().filter(|(i, _)| *i != index).map(|(_, r)| r.rks).collect::<Vec<_>>();
            best.push(candidate); best.sort_by(|a, b| b.total_cmp(a));
            let phi = records.iter().enumerate().filter(|(i, r)| *i != index && r.score == Some(1_000_000.0)).map(|(_, r)| r.rks).fold(if acc >= 100.0 { candidate } else { 0.0 }, f64::max);
            (best.iter().take(19).sum::<f64>() + phi) / 20.0
        };
        let hint = if r.score == Some(1_000_000.0) { PushAccHint::AlreadyPhi }
        else if at(100.0) < target { PushAccHint::Unreachable }
        else if at(99.999) < target { PushAccHint::PhiOnly }
        else {
            let mut lo = (r.acc.max(70.0) * 1000.0).ceil() as u32;
            let mut hi = 99_999;
            while lo < hi { let mid = (lo + hi) / 2; if at(mid as f64 / 1000.0) >= target { hi = mid; } else { lo = mid + 1; } }
            PushAccHint::TargetAcc { acc: lo as f64 / 1000.0 }
        };
        (format!("{}-{}", r.song_id, r.difficulty), hint)
    }).collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn supplied_constants_are_complete_and_keep_original_values() {
        let c = constants();
        assert_eq!(c.len(), 327);
        assert_eq!(c.values().map(|c| [c.ez, c.hd, c.in_level, c.at].iter().flatten().count()).sum::<usize>(), 1037);
        assert_eq!(c["Glaciaxion.SunsetRay"].in_level, Some(12.5));
        assert_eq!(c["Credits.Frums"].at, Some(15.5));
        assert_eq!(c["DesultorySignals.technoplanet"].in_level, Some(16.4));
        assert_eq!(c["ExoplanetaryMirage.かめりあ"].in_level, Some(16.3));
        assert!(c.values().flat_map(|c| [c.ez, c.hd, c.in_level, c.at]).flatten().all(|v| v.is_finite() && v > 0.0));
    }
    #[test]
    fn legacy_rks_has_one_phi_and_nineteen_best_slots() {
        let records = (0..30).map(|i| RenderRecord {
            song_id: i.to_string(), song_name: i.to_string(), difficulty: "IN".into(),
            score: Some(if i == 2 { 1_000_000.0 } else { 990_000.0 }), acc: 99.0,
            rks: 16.0 - i as f64 * 0.1, difficulty_value: 16.0, is_fc: true,
        }).collect::<Vec<_>>();
        let expected = ((0..19).map(|i| 16.0 - i as f64 * 0.1).sum::<f64>() + 15.8) / 20.0;
        assert!((ranking_rks(&records) - expected).abs() < 1e-9);
        assert!((ranking_rks(&records[..1]) - 0.8).abs() < 1e-9);
    }

    #[test]
    fn time_rewind_phi_uses_the_highest_constant_and_ignores_non_ap_scores() {
        let mut records = [
            ("A.song", "AT", 16.0, 1_000_000.0), ("Z.song", "IN", 16.1, 1_000_000.0),
            ("0.not-ap", "AT", 17.0, 999_999.0),
        ].into_iter().map(|(id, diff, constant, score)| RenderRecord {
            song_id: id.into(), song_name: id.into(), difficulty: diff.into(), score: Some(score),
            acc: 100.0, rks: constant, difficulty_value: constant, is_fc: true,
        }).collect::<Vec<_>>();
        assert_eq!(select_phi(&records).unwrap().song_id, "Z.song");
        records.reverse();
        assert_eq!(select_phi(&records).unwrap().song_id, "Z.song");
        records.retain(|r| r.score != Some(1_000_000.0));
        assert!(select_phi(&records).is_none());
    }
}
