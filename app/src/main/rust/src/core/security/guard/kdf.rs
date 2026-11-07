//! 密钥派生。
//!
//! 与 C++ 版 `runtime/guard.cpp` 的算法**逐字节一致**，这样旧数据不用迁移：
//!
//! ```text
//! seed      = SHA256(内置片段0..3 [+ 构建期注入的种子])
//! seed2     = HKDF-SHA256(ikm=seed,  salt="bai.guard.seed2", info="bai.guard.v1")
//! K2        = HKDF-SHA256(ikm=seed2, salt=deviceFactor,      info="bai.guard.v1")
//! K         = R XOR K2
//! ```
//!
//! 两层 HKDF 的意义：`seed2` 与"落盘加密用的旧种子"分开派生，即使攻击者拿到
//! 旧版密钥（v1 文件），也推不出 `K2`；`deviceFactor` 参与第二层，换机就解不开。
//!
//! 所有中间结果用完立即 `zeroize`。Rust 的 `[u8; 32]` 是栈上值，编译器可能把
//! 副本留在栈里；`Zeroize` 至少保证我们主动清零的那份不留。

use hkdf::Hkdf;
use sha2::{Digest, Sha256};
use zeroize::Zeroize;

/// HKDF 的 info 串。改它等于换算法，旧数据全废，**不要改**。
pub const INFO: &[u8] = b"bai.guard.v1";
/// 第二种子派生的 salt。
pub const SALT_SEED2: &[u8] = b"bai.guard.seed2";
/// 设备因子为空时的兜底 salt。
pub const SALT_DEFAULT: &[u8] = b"bai.guard.salt";

/// 内置密钥片段。分四段写，避免 `strings` 一把捞出完整串。
///
/// 这不是"硬编码密钥"——真正的安全性来自设备密钥 `R`（Keystore 包裹）与
/// 设备因子；这四段只是让 v1 旧文件能被解开，以及给 `seed2` 一个起点。
/// 构建时可用 `BAICODE_SEED` 环境变量覆盖（见 `build.rs`）。
///
/// **四段的值必须与 C++ `piece0..3` 完全一致**，否则旧加密文件解不开：
/// `piece0` 在 `tools/dsl.cpp`、`piece1` 在 `net/http.cpp`、
/// `piece2` 在 `image/blur.cpp`、`piece3` 在 `runtime/secret.cpp`。
const PIECES: [&str; 4] = [
    "9f3c71ab5e2d4c08",
    "d14b8e6027af35c9",
    "7a05c93fe1b62d84",
    "3e6f08d5b94a17c2",
];

/// 派生 32 字节主种子。
pub fn seed() -> [u8; 32] {
    let mut h = Sha256::new();
    for p in PIECES {
        h.update(p.as_bytes());
    }
    if let Some(extra) = option_env!("BAICODE_SEED") {
        h.update(extra.as_bytes());
    }
    let out = h.finalize();
    let mut arr = [0u8; 32];
    arr.copy_from_slice(&out);
    arr
}

/// HKDF-SHA256 → 32 字节。`ikm` 为空或派生失败返回 None。
pub fn hkdf32(ikm: &[u8], salt: &[u8], info: &[u8]) -> Option<[u8; 32]> {
    if ikm.is_empty() {
        return None;
    }
    let hk = Hkdf::<Sha256>::new(Some(salt), ikm);
    let mut okm = [0u8; 32];
    match hk.expand(info, &mut okm) {
        Ok(()) => Some(okm),
        Err(_) => {
            okm.zeroize();
            None
        }
    }
}

/// 第二种子：`HKDF(seed, "bai.guard.seed2")`。
pub fn second_seed(seed: &[u8; 32]) -> Option<[u8; 32]> {
    hkdf32(seed, SALT_SEED2, INFO)
}

/// 组合半密钥：`HKDF(seed2, deviceFactor)`。设备因子为空时用固定 salt。
pub fn combine(seed2: &[u8; 32], device_factor: &str) -> Option<[u8; 32]> {
    let salt = if device_factor.trim().is_empty() {
        SALT_DEFAULT.to_vec()
    } else {
        device_factor.as_bytes().to_vec()
    };
    hkdf32(seed2, &salt, INFO)
}

/// 数据密钥 = R ⊕ K2。
pub fn xor32(a: &[u8; 32], b: &[u8; 32]) -> [u8; 32] {
    let mut out = [0u8; 32];
    for i in 0..32 {
        out[i] = a[i] ^ b[i];
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn derivation_is_deterministic() {
        let s = seed();
        let s2 = second_seed(&s).unwrap();
        let k2a = combine(&s2, "device-a").unwrap();
        let k2b = combine(&s2, "device-a").unwrap();
        assert_eq!(k2a, k2b);
        let k2c = combine(&s2, "device-b").unwrap();
        assert_ne!(k2a, k2c);
    }

    #[test]
    fn xor_inverts() {
        let a = [7u8; 32];
        let b = [9u8; 32];
        let k = xor32(&a, &b);
        assert_eq!(xor32(&k, &b), a);
    }

    #[test]
    fn different_seed_gives_different_key() {
        let s = seed();
        let s2 = second_seed(&s).unwrap();
        let mut fake = s2;
        fake[0] ^= 1;
        assert_ne!(combine(&s2, "x").unwrap(), combine(&fake, "x").unwrap());
    }
}
