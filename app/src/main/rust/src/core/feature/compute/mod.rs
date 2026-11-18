//! 计算块：滑杆几何与请求重试策略，都是纯函数，零 I/O、零平台依赖。
//!
//! - `slider` 值 ↔ 像素换算。填充端点、滑块中心、手指落点共用同一套公式，
//!            任何一处用近似写法都会出现「分界线和滑块对不上」。
//! - `retry`  判断「这次失败该不该再发一次」「隔多久再发」。只管同一次 HTTP 请求，
//!            工具调用失败与用户取消一律不进来。

pub mod retry;
pub mod slider;

pub use retry::{allow, classify, delay_ms, MAX_RETRY};
pub use slider::{center_x, frac, value_at};