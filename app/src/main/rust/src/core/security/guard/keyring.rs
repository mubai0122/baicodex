//! 半密钥与数据密钥的生命周期。
//!
//! 三个状态，顺序严格：
//!
//! ```text
//! idle ──start(device)──▶ armed(K2) ──unlock(R)──▶ ready(K)
//!   ▲                         │                      │
//!   └──────── lock() ─────────┴──────────────────────┘
//! ```
//!
//! - `armed`：知道"这台机器该用哪个 K2"，但还没有设备密钥 R，解不开数据。
//! - `ready`：K 已合成，能读写加密文件。
//!
//! **`unlock` 成功后立刻销毁 K2**：内存里只留合成后的 K，减少"一半密钥长期驻留"
//! 的窗口。`lock()` 两者都清，进程退出前、切换用户时调用。

use std::sync::{Mutex, OnceLock};

use zeroize::Zeroize;

use super::kdf;

struct Inner {
    k2: Option<[u8; 32]>,
    kd: Option<[u8; 32]>,
    /// 解锁失败次数。超过阈值后 refuse，防止被暴力尝试（虽然 R 是 256 位，
    /// 但设备因子可能被猜到，多一层限制没坏处）。
    fails: u32,
}

impl Inner {
    fn wipe(&mut self) {
        if let Some(mut k) = self.k2.take() {
            k.zeroize();
        }
        if let Some(mut k) = self.kd.take() {
            k.zeroize();
        }
    }
}

impl std::fmt::Debug for Inner {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        // 密钥本身绝不进 Debug。只报"有没有、失败几次"，这两种信息足够定位问题。
        f.debug_struct("Inner")
            .field("k2", &self.k2.is_some())
            .field("kd", &self.kd.is_some())
            .field("fails", &self.fails)
            .finish()
    }
}

/// 密钥环。
#[derive(Debug)]
pub struct KeyRing {
    inner: Mutex<Inner>,
}

impl KeyRing {
    pub const fn new() -> Self {
        Self {
            inner: Mutex::new(Inner { k2: None, kd: None, fails: 0 }),
        }
    }

    fn with<R>(&self, f: impl FnOnce(&mut Inner) -> R) -> R {
        let mut g = match self.inner.lock() {
            Ok(g) => g,
            Err(p) => p.into_inner(),
        };
        f(&mut g)
    }

    /// 派生 K2。可以重复调用（比如换了设备因子后重新 start）。
    pub fn start(&self, device_factor: &str) -> bool {
        let mut s = kdf::seed();
        let s2 = kdf::second_seed(&s);
        s.zeroize();
        let Some(mut s2v) = s2 else { return false };
        let k2 = kdf::combine(&s2v, device_factor);
        s2v.zeroize();
        let Some(k2v) = k2 else { return false };
        self.with(|i| {
            i.wipe();
            i.k2 = Some(k2v);
            i.fails = 0;
        });
        true
    }

    /// 用设备密钥 R（32 字节 hex）合成数据密钥 K。
    pub fn unlock(&self, r_hex: &str) -> bool {
        let Some(r) = crate::core::base::bytes::hex_decode(r_hex) else { return false };
        if r.len() != 32 {
            return false;
        }
        let mut r_arr = [0u8; 32];
        r_arr.copy_from_slice(&r);
        let ok = self.with(|i| {
            let Some(k2) = i.k2 else { return false };
            if i.fails >= 8 {
                return false;
            }
            let k = kdf::xor32(&r_arr, &k2);
            i.wipe();
            i.kd = Some(k);
            true
        });
        r_arr.zeroize();
        if !ok {
            self.with(|i| i.fails = i.fails.saturating_add(1));
        }
        ok
    }

    /// 清空全部密钥材料。
    pub fn lock(&self) {
        self.with(|i| {
            i.wipe();
            i.fails = 0;
        });
    }

    /// 是否已解锁。
    pub fn ready(&self) -> bool {
        self.with(|i| i.kd.is_some())
    }

    /// 是否已派生 K2（等 R）。
    pub fn armed(&self) -> bool {
        self.with(|i| i.k2.is_some())
    }

    /// 取数据密钥副本。调用方用完必须 `zeroize`。
    pub fn key(&self) -> Option<[u8; 32]> {
        self.with(|i| i.kd)
    }

    /// 还剩几次尝试。
    pub fn attempts_left(&self) -> u32 {
        self.with(|i| 8u32.saturating_sub(i.fails))
    }
}

impl Default for KeyRing {
    fn default() -> Self {
        Self::new()
    }
}

/// 全局密钥环。一个进程只有一份——数据密钥没有"多实例"的语义。
pub fn ring() -> &'static KeyRing {
    static R: OnceLock<KeyRing> = OnceLock::new();
    R.get_or_init(KeyRing::new)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn lifecycle() {
        let k = KeyRing::new();
        assert!(!k.ready());
        assert!(k.start("device-x"));
        assert!(k.armed());
        assert!(!k.ready());
        let r = crate::core::base::bytes::random_hex(64);
        assert!(k.unlock(&r));
        assert!(k.ready());
        assert!(k.key().is_some());
        // 解锁后 K2 必须被销毁
        assert!(!k.armed());
        k.lock();
        assert!(!k.ready());
    }

    #[test]
    fn unlock_requires_start() {
        let k = KeyRing::new();
        let r = crate::core::base::bytes::random_hex(64);
        assert!(!k.unlock(&r));
    }

    #[test]
    fn bad_r_rejected() {
        let k = KeyRing::new();
        k.start("d");
        assert!(!k.unlock("abcd"));
        assert!(!k.unlock(""));
        assert!(!k.ready());
    }
}
