//! 设备块：与外部设备/协议打交道的功能。
//!
//! - `miot` 米家局域网协议：探测包、报文头解析、JSON 取整

pub mod miot;

pub use miot::probe;