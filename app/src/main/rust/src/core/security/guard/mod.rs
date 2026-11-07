//! 数据保障：三层密钥的门面。
//!
//! 与 Kotlin `Guard` / C++ `guard.cpp` 的对应关系：
//!
//! | 层 | 谁负责 | 说明 |
//! |---|---|---|
//! | 设备密钥 R | Android Keystore（Kotlin） | TEE/StrongBox 包裹，明文不出硬件 |
//! | 半密钥 K2 | 本模块 [kdf] | HKDF(seed2, 设备因子) |
//! | 数据密钥 K | 本模块 [keyring] | R ⊕ K2，解锁后常驻 |
//! | 内容加密 | `security::secret` | AES-256-GCM，v2 文件 |
//!
//! 桌面端没有 Keystore：`R` 由一个 0600 权限的密钥文件提供（见 `security::vault`），
//! 安全性弱于 TEE，但至少做到"配置文件里没有明文密钥"。这个差异写在这里，是为了
//! 让后来的人知道**为什么桌面端的 guard 看起来少了一步**。

pub mod kdf;
pub mod keyring;

use serde::Serialize;

pub use keyring::{ring, KeyRing};

/// 解锁状态。给界面显示"加密已开启/已解锁"。
#[derive(Debug, Clone, Serialize)]
pub struct Status {
    /// 是否已派生 K2（等 R）。
    pub armed: bool,
    /// 是否已解锁（能读写加密文件）。
    pub ready: bool,
    /// 还剩几次解锁尝试。
    pub attempts_left: u32,
}

/// 查看状态。
pub fn status() -> Status {
    let r = ring();
    Status { armed: r.armed(), ready: r.ready(), attempts_left: r.attempts_left() }
}

/// 派生 K2。`device_factor` 由前端提供（Android 用 Build 字段拼接，桌面用 hostname+机器 id）。
pub fn start(device_factor: &str) -> bool {
    ring().start(device_factor)
}

/// 用 R 解锁。
pub fn unlock(r_hex: &str) -> bool {
    ring().unlock(r_hex)
}

/// 清空密钥。
pub fn lock() {
    ring().lock()
}

/// 是否已解锁。
pub fn ready() -> bool {
    ring().ready()
}

/// 取数据密钥副本（调用方负责清零）。
pub fn key() -> Option<[u8; 32]> {
    ring().key()
}

/// 测试用的串行锁。
///
/// 密钥环是进程级单例，而 `cargo test` 默认多线程并行：一个测试刚 `start()`
/// 出 K2，另一个测试的 `lock()` 就把它清了，于是 `unlock` 莫名其妙失败。
/// 所有动全局密钥环的测试都先拿这把锁，问题消失。
#[cfg(test)]
pub fn test_lock() -> std::sync::MutexGuard<'static, ()> {
    static M: std::sync::Mutex<()> = std::sync::Mutex::new(());
    M.lock().unwrap_or_else(|e| e.into_inner())
}

/// 生成随机半密钥 R（64 位 hex = 32 字节）。
pub fn random_hex() -> String {
    crate::core::base::bytes::random_hex(64)
}
