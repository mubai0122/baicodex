//! 滑杆几何：把「值 ↔ 像素」的换算全部收在 Rust 里，Kotlin 只负责画。
//!
//! 之所以要单独成模块：滑块的三个量（填充端点、滑块中心、手指落点）必须共用
//! 同一套公式，任何一处用近似写法都会出现「分界点和滑块对不上」的观感问题。
//! 口径：轨道两端各留半个滑块，滑块的可用行程 = 总宽 − 滑块宽。

/// 最小值兜底：范围非法时按 0..1，避免除法出 NaN 传染到绘制层
fn safe_span(min: f32, max: f32) -> f32 {
    let span = max - min;
    if span.is_finite() && span > 0.0 {
        span
    } else {
        1.0
    }
} //安全跨度

/// 值 → 比例（0..1）
pub fn frac(value: f32, min: f32, max: f32) -> f32 {
    let f = (value - min) / safe_span(min, max);
    if f.is_finite() {
        f.clamp(0.0, 1.0)
    } else {
        0.0
    }
} //值→比例

/// 比例 → 滑块**中心**的 x（像素）
///
/// 两端各留半个滑块，所以行程是 `总宽 − 滑块宽`；
/// 填充段直接画到这个 x 就是「分界点对齐滑块中线」。
pub fn center_x(frac: f32, track_w: f32, thumb_w: f32) -> f32 {
    let usable = (track_w - thumb_w).max(0.0);
    let half = thumb_w / 2.0;
    half + frac.clamp(0.0, 1.0) * usable
} //比例→中心 x

/// 值按步长吸附：step ≤ 0 视为连续
pub fn snap(value: f32, min: f32, step: f32) -> f32 {
    if step <= 0.0 || !step.is_finite() {
        return value;
    }
    min + ((value - min) / step).round() * step
} //步长吸附

/// 手指 x → 值：先反解比例，再吸附，最后夹在范围内
pub fn value_at(x: f32, track_w: f32, thumb_w: f32, min: f32, max: f32, step: f32) -> f32 {
    if !track_w.is_finite() || track_w <= 0.0 {
        return min;
    }
    let usable = (track_w - thumb_w).max(1.0);
    let half = thumb_w / 2.0;
    let f = ((x - half) / usable).clamp(0.0, 1.0);
    let v = snap(min + f * safe_span(min, max), min, step);
    if v.is_finite() {
        v.clamp(min, max)
    } else {
        min
    }
} //手指 x→值

#[cfg(test)]
mod tests {
    use super::*;

    /// 两端极值必须精确落在「半个滑块」和「总宽减半个滑块」上
    #[test]
    fn center_hits_both_ends() {
        assert_eq!(center_x(0.0, 1000.0, 100.0), 50.0);
        assert_eq!(center_x(1.0, 1000.0, 100.0), 950.0);
        assert_eq!(center_x(0.5, 1000.0, 100.0), 500.0);
    } //两端对齐

    /// 反解要能回到原值（含吸附）
    #[test]
    fn value_at_inverts_center_x() {
        let (w, tw, min, max, step) = (1000.0f32, 100.0f32, 0.0f32, 100.0f32, 1.0f32);
        for f in [0.0f32, 0.25, 0.5, 0.75, 1.0] {
            let x = center_x(f, w, tw);
            let v = value_at(x, w, tw, min, max, step);
            assert!((v - (min + f * (max - min))).abs() <= step, "f={f} v={v}");
        }
    } //反解往返

    /// 越界点击夹在两端
    #[test]
    fn value_at_clamps() {
        assert_eq!(value_at(-500.0, 1000.0, 100.0, 0.0, 10.0, 1.0), 0.0);
        assert_eq!(value_at(9999.0, 1000.0, 100.0, 0.0, 10.0, 1.0), 10.0);
    } //越界夹取

    /// 非法范围不能产生 NaN：min == max 时行程退化为 0，按「无填充」处理
    #[test]
    fn bad_range_is_safe() {
        assert_eq!(frac(5.0, 10.0, 10.0), 0.0);
        assert!(frac(f32::NAN, 0.0, 1.0).is_finite());
        assert_eq!(value_at(10.0, 0.0, 10.0, 0.0, 1.0, 0.0), 0.0);
    } //非法范围
} //测试