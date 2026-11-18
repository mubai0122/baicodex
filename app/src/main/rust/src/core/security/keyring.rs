//! 主密钥来源。
//!
//! 分三档，按平台能力选：
//! - Android：Keystore 里放主密钥，宿主解出 32 字节后经 FFI 交给内核（内核不碰 JNI）；
//! - Windows：DPAPI 包裹后落盘，宿主解出后同样交给内核；
//! - Linux / CLI：`FileKeyProvider`，文件权限 0600。
//!
//! 三档的**结果**都一样：内核只拿到 32 字节主密钥，之后的派生、加密、校验完全一致。
//! 这就是「加解密一致性」的落点——平台差异被限制在「怎么保管主密钥」这一件事上。

use std::path::{Path, PathBuf};

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::fsx;
use crate::core::security::sealer::MasterKey;

const KEY_MAGIC: &[u8; 6] = b"BXKY2\n";

/// 主密钥提供者。
pub trait KeyProvider: Send + Sync {
    /// 取出主密钥；不存在则创建。
    fn load_or_create(&self) -> BxResult<MasterKey>;
    /// 人类可读的来源标识，用于诊断页与日志（不含密钥本身）。
    fn label(&self) -> &'static str;
}

/// 宿主已经解好的定值密钥（Android Keystore / Windows DPAPI 解包后走这条）。
pub struct StaticKeyProvider {
    key: MasterKey,
}

impl StaticKeyProvider {
    /// 用已有主密钥构造。
    pub fn new(key: MasterKey) -> Self {
        Self { key }
    }
}

impl KeyProvider for StaticKeyProvider {
    fn load_or_create(&self) -> BxResult<MasterKey> {
        Ok(self.key.clone())
    }

    fn label(&self) -> &'static str {
        "host"
    }
}

/// 文件密钥提供者：`<path>` 里存魔数 + 32 字节主密钥，权限 0600。
pub struct FileKeyProvider {
    path: PathBuf,
}

impl FileKeyProvider {
    /// 指定密钥文件路径。
    pub fn new(path: impl Into<PathBuf>) -> Self {
        Self { path: path.into() }
    }

    /// 密钥文件路径。
    pub fn path(&self) -> &Path {
        &self.path
    }
}

impl KeyProvider for FileKeyProvider {
    fn load_or_create(&self) -> BxResult<MasterKey> {
        match std::fs::read(&self.path) {
            Ok(bytes) => {
                if bytes.len() != KEY_MAGIC.len() + 32 || &bytes[..KEY_MAGIC.len()] != KEY_MAGIC {
                    return Err(BxError::Corrupt("主密钥文件格式不符"));
                }
                MasterKey::from_bytes(&bytes[KEY_MAGIC.len()..])
            }
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => {
                let key = MasterKey::generate()?;
                let mut out = Vec::with_capacity(KEY_MAGIC.len() + 32);
                out.extend_from_slice(KEY_MAGIC);
                out.extend_from_slice(key.expose());
                // 先按 0600 建文件再写内容：不能先建成 0644 再 chmod，中间那一下别人可读
                fsx::write_atomic_secret(&self.path, &out)?;
                Ok(key)
            }
            Err(e) => Err(e.into()),
        }
    }

    fn label(&self) -> &'static str {
        "file"
    }
}
