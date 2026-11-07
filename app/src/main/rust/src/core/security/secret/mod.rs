//! 加密落盘。
//!
//! 对业务层只暴露四个动作：`save` / `load` / `seal` / `open`。
//! 业务层不需要知道当前用的是 v1 还是 v2 密钥，也不需要知道 guard 有没有解锁——
//! 这一层会自己选：
//!
//! - guard 已解锁 → 用数据密钥 K，写 **v2**；
//! - 未解锁 → 用内置片段派生的旧密钥，写 **v1**（兼容旧数据，安全性较低）；
//! - 读的时候按文件头的版本号选，与当前状态无关。
//!
//! 这个"自动降级"是有意的：用户没开加密时功能不能不可用；开了之后新写的是 v2，
//! 旧 v1 也能读。迁移由 [migrate] 在后台做，不阻塞主流程。

pub mod format;
pub mod migrate;

use std::path::Path;

use zeroize::Zeroize;

use crate::core::base::atomic;
use crate::core::base::error::{BError, BResult, ErrorCode};

pub use format::{is_encrypted, Head, HEAD_LEN, MAGIC};

/// 旧方案（v1）密钥：内置片段拼接后 SHA256。
///
/// 与 C++ `secret::derive()` 一致。**不要改片段值**，改了等于让所有旧加密文件报废。
pub fn legacy_key() -> [u8; 32] {
    super::guard::kdf::seed()
}

/// 当前可用的写密钥 + 版本号。
fn write_key() -> ([u8; 32], u8) {
    match super::guard::key() {
        Some(k) => (k, 2),
        None => (legacy_key(), 1),
    }
}

/// 按文件头版本选读密钥。
fn read_key(version: u8) -> BResult<[u8; 32]> {
    match version {
        2 => super::guard::key()
            .ok_or_else(|| BError::new(ErrorCode::Crypto, "文件是加密 v2，但当前未解锁")),
        1 => Ok(legacy_key()),
        _ => Err(BError::new(ErrorCode::Crypto, "不认识的文件版本")),
    }
}

/// 加密一段字节，返回完整文件内容。
pub fn seal(plain: &[u8]) -> BResult<Vec<u8>> {
    let (mut key, ver) = write_key();
    let out = format::seal(&key, plain, ver);
    key.zeroize();
    out
}

/// 解密一段完整文件内容。
pub fn open(blob: &[u8]) -> BResult<Vec<u8>> {
    let head = format::parse_head(blob)
        .ok_or_else(|| BError::new(ErrorCode::Crypto, "不是有效的加密文件"))?;
    let mut key = read_key(head.version)?;
    let out = format::open(&key, blob);
    key.zeroize();
    out
}

/// 加密后原子写盘，权限 0600。
pub fn save(path: impl AsRef<Path>, plain: &[u8]) -> BResult<()> {
    let blob = seal(plain)?;
    #[cfg(unix)]
    {
        atomic::write_secure(path, &blob, 0o600)
    }
    #[cfg(not(unix))]
    {
        atomic::write(path, &blob)
    }
}

/// 读盘并解密。文件不存在返回 `NotFound`。
pub fn load(path: impl AsRef<Path>) -> BResult<Vec<u8>> {
    let path = path.as_ref();
    if !path.exists() {
        return Err(BError::not_found(format!("找不到 {}", path.display())));
    }
    let blob = atomic::read_limited(path, 64 * 1024 * 1024)?;
    open(&blob)
}

/// 读盘并解密成文本。
pub fn load_text(path: impl AsRef<Path>) -> BResult<String> {
    let bytes = load(path)?;
    String::from_utf8(bytes).map_err(|e| BError::new(ErrorCode::Crypto, "解密后不是 UTF-8").with_detail(e.to_string()))
}

/// 密钥指纹：旧密钥前 8 字节的 hex。用来判断"两台机器是不是同一套内置片段"，
/// 不能反推出密钥。
pub fn fingerprint() -> String {
    let mut k = legacy_key();
    let s = crate::core::base::bytes::hex_encode(&k[..8]);
    k.zeroize();
    s
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn without_guard_uses_v1() {
        let _g = crate::core::security::guard::test_lock();
        crate::core::security::guard::lock();
        let blob = seal(b"hello").unwrap();
        assert_eq!(format::parse_head(&blob).unwrap().version, 1);
        assert_eq!(open(&blob).unwrap(), b"hello");
    }

    #[test]
    fn with_guard_uses_v2() {
        let _g = crate::core::security::guard::test_lock();
        let g = crate::core::security::guard::ring();
        assert!(g.start("test-device"));
        let r = super::super::guard::random_hex();
        assert!(g.unlock(&r));
        let blob = seal(b"hello").unwrap();
        assert_eq!(format::parse_head(&blob).unwrap().version, 2);
        assert_eq!(open(&blob).unwrap(), b"hello");
        g.lock();
    }

    #[test]
    fn v1_file_still_readable_after_unlock() {
        let _g = crate::core::security::guard::test_lock();
        crate::core::security::guard::lock();
        let old = seal(b"legacy").unwrap();
        let g = crate::core::security::guard::ring();
        g.start("d");
        g.unlock(&super::super::guard::random_hex());
        assert_eq!(open(&old).unwrap(), b"legacy");
        g.lock();
    }

    #[test]
    fn file_roundtrip() {
        let dir = std::env::temp_dir().join(format!("bai-sec-{}", crate::core::base::ids::IdGen::hex(6)));
        let p = dir.join("x.enc");
        save(&p, b"payload").unwrap();
        assert_eq!(load(&p).unwrap(), b"payload");
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn plain_file_is_rejected() {
        assert!(open(b"{\"not\":\"encrypted\"}").is_err());
    }
}
