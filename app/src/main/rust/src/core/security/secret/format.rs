//! 加密文件格式（v1 / v2）。
//!
//! ```text
//! ┌────────────┬─────┬──────────┬────────────┬──────────┬──────────┐
//! │ MAGIC 18B  │ ver │ len 4B   │ nonce 12B  │ ct N B   │ tag 16B  │
//! └────────────┴─────┴──────────┴────────────┴──────────┴──────────┘
//!                └──────────── 作为 AAD ───────────┘
//! ```
//!
//! - `MAGIC` 是 `baicodexencryption`，18 字节。认不出这个头就不是我们的文件。
//! - `ver=1`：密钥来自编译期内置片段（旧方案）。
//! - `ver=2`：密钥来自 [super::super::guard] 的三层密钥（当前方案）。
//! - `len` 是明文长度，大端。**解密前必须校验**：`blob.len() == HEAD + 12 + len + 16`。
//!   不校验长度，攻击者可以把两个文件拼起来，让 GCM 去解一段错位的数据。
//! - 整个头（含 MAGIC、ver、len）都进 AAD：改任何一个字节，tag 校验都会失败。
//!
//! 与 C++ `runtime/secret.cpp` 完全兼容，已有文件不用重写就能读。

use aes_gcm::aead::{Aead, Payload};
use aes_gcm::{Aes256Gcm, Key, KeyInit, Nonce};

use crate::core::base::error::{BError, BResult, ErrorCode};

/// 文件魔数。
pub const MAGIC: &[u8] = b"baicodexencryption";
/// nonce 长度。
pub const NONCE_LEN: usize = 12;
/// GCM tag 长度。
pub const TAG_LEN: usize = 16;
/// 头部长度：魔数 + 版本 1 + 明文长度 4。
pub const HEAD_LEN: usize = MAGIC.len() + 1 + 4;

/// 解析出来的头。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Head {
    pub version: u8,
    pub len: usize,
}

/// 组头。
pub fn build_head(len: usize, version: u8) -> Vec<u8> {
    let mut h = Vec::with_capacity(HEAD_LEN);
    h.extend_from_slice(MAGIC);
    h.push(version);
    h.extend_from_slice(&(len as u32).to_be_bytes());
    h
}

/// 解头。魔数不对、版本不认识、长度不合都返回 None。
pub fn parse_head(blob: &[u8]) -> Option<Head> {
    if blob.len() < HEAD_LEN {
        return None;
    }
    if &blob[..MAGIC.len()] != MAGIC {
        return None;
    }
    let version = blob[MAGIC.len()];
    if version != 1 && version != 2 {
        return None;
    }
    let len = u32::from_be_bytes([
        blob[MAGIC.len() + 1],
        blob[MAGIC.len() + 2],
        blob[MAGIC.len() + 3],
        blob[MAGIC.len() + 4],
    ]) as usize;
    Some(Head { version, len })
}

/// 判断一段字节是不是加密文件（只看魔数，不做完整校验）。
pub fn is_encrypted(blob: &[u8]) -> bool {
    blob.len() >= HEAD_LEN && &blob[..MAGIC.len()] == MAGIC
}

/// 加密。返回完整文件内容。
pub fn seal(key: &[u8; 32], plain: &[u8], version: u8) -> BResult<Vec<u8>> {
    let head = build_head(plain.len(), version);
    let mut nonce = [0u8; NONCE_LEN];
    // 用系统随机源。rand 的 thread_rng 是 CSPRNG（ChaCha12），够用。
    use rand::RngCore;
    rand::thread_rng().fill_bytes(&mut nonce);

    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key));
    let ct = cipher
        .encrypt(
            Nonce::from_slice(&nonce),
            Payload { msg: plain, aad: &head },
        )
        .map_err(|_| BError::new(ErrorCode::Crypto, "加密失败"))?;

    let mut out = Vec::with_capacity(HEAD_LEN + NONCE_LEN + ct.len());
    out.extend_from_slice(&head);
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct); // ct 末尾已经带着 16 字节 tag
    Ok(out)
}

/// 解密。任何一步对不上都返回 `Crypto`，不区分"坏了"和"密钥不对"——
/// 区分了就是在给攻击者做 oracle。
pub fn open(key: &[u8; 32], blob: &[u8]) -> BResult<Vec<u8>> {
    let head = parse_head(blob)
        .ok_or_else(|| BError::new(ErrorCode::Crypto, "不是有效的加密文件"))?;
    let need = HEAD_LEN + NONCE_LEN + TAG_LEN + head.len;
    if blob.len() != need {
        return Err(BError::new(ErrorCode::Crypto, "文件长度不对，可能被截断或拼接"));
    }
    let nonce = &blob[HEAD_LEN..HEAD_LEN + NONCE_LEN];
    let ct = &blob[HEAD_LEN + NONCE_LEN..];
    let cipher = Aes256Gcm::new(Key::<Aes256Gcm>::from_slice(key));
    let plain = cipher
        .decrypt(Nonce::from_slice(nonce), Payload { msg: ct, aad: &blob[..HEAD_LEN] })
        .map_err(|_| BError::new(ErrorCode::Crypto, "解密失败：密钥不对或文件已损坏"))?;
    if plain.len() != head.len {
        return Err(BError::new(ErrorCode::Crypto, "明文长度与文件头不符"));
    }
    Ok(plain)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn key() -> [u8; 32] {
        [7u8; 32]
    }

    #[test]
    fn seal_open_roundtrip() {
        for ver in [1u8, 2u8] {
            let blob = seal(&key(), b"hello \xe4\xb8\xad\xe6\x96\x87", ver).unwrap();
            assert!(is_encrypted(&blob));
            assert_eq!(parse_head(&blob).unwrap().version, ver);
            assert_eq!(open(&key(), &blob).unwrap(), b"hello \xe4\xb8\xad\xe6\x96\x87");
        }
    }

    #[test]
    fn tamper_detected() {
        let mut blob = seal(&key(), b"secret", 2).unwrap();
        let n = blob.len();
        blob[n - 1] ^= 1;
        assert!(open(&key(), &blob).is_err());
    }

    #[test]
    fn truncation_detected() {
        let blob = seal(&key(), b"secret", 2).unwrap();
        assert!(open(&key(), &blob[..blob.len() - 1]).is_err());
        // 拼接一个合法头 + 别人的尾部，长度对不上要被拒
        let mut joined = blob.clone();
        joined.extend_from_slice(b"extra");
        assert!(open(&key(), &joined).is_err());
    }

    #[test]
    fn wrong_key_fails() {
        let blob = seal(&key(), b"secret", 2).unwrap();
        assert!(open(&[8u8; 32], &blob).is_err());
    }

    #[test]
    fn header_is_aad() {
        let mut blob = seal(&key(), b"secret", 2).unwrap();
        // 改版本号会同时破坏 AAD 与密钥选择
        blob[MAGIC.len()] = 1;
        assert!(open(&key(), &blob).is_err());
    }
}
