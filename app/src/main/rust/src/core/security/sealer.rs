/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 21:47:29
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

//! AES-256-GCM 信封加密。
//!
//! # 文件格式（`BXCX2`）
//!
//! ```text
//! 0..6      magic  "BXCX2\n"
//! 6         alg    1 = AES-256-GCM（其它值一律拒绝）
//! 7         flags  保留，必须为 0
//! 8..16     key_id 主密钥短标识（SHA-256 前 8 字节）
//! 16..20    schema u32 LE，载荷格式代数
//! 20..22    aad_len u16 LE
//! 22..      aad     "bx2:<scope>/<name>"，同时作为 AEAD 附加数据
//!           nonce   12 字节
//!           ct_len  u32 LE
//!           ct      密文（尾部含 16 字节 GCM tag）
//! 末 32 字节 sha256(前面全部字节)
//! ```
//!
//! `aad` 进头部、也进 AEAD：文件被改名/挪桶/改 schema 都会解密失败，
//! 而不是「解出来一串乱码」再被上层当成合法 JSON 写回去。
//!
//! 末位 sha256 解决的是另一类问题：截断写（掉电、磁盘满）时 GCM 可能仍能验证前段，
//! 有了整体摘要，截断文件会在**解密前**就被判为损坏，直接退回 `.prev`。

use aes_gcm::aead::{Aead, KeyInit, Payload};
use aes_gcm::{Aes256Gcm, Nonce};
use hkdf::Hkdf;
use sha2::{Digest, Sha256};
use zeroize::{Zeroize, ZeroizeOnDrop};

use crate::core::base::err::{BxError, BxResult};
use crate::core::base::ids;
use crate::core::base::key::Key;

const MAGIC: &[u8; 6] = b"BXCX2\n";
const ALG_AES256_GCM: u8 = 1;
const NONCE_LEN: usize = 12;
const TAG_LEN: usize = 16;
const DIGEST_LEN: usize = 32;
const HDR_FIXED: usize = 22;

/// 主密钥：32 字节，Drop 时清零。
#[derive(Clone, Zeroize, ZeroizeOnDrop)]
pub struct MasterKey {
    key: [u8; 32],
}

impl MasterKey {
    /// 从宿主提供（Keystore / DPAPI 解出）的字节构造。
    pub fn from_bytes(bytes: &[u8]) -> BxResult<Self> {
        if bytes.len() != 32 {
            return Err(BxError::Invalid(format!(
                "主密钥必须 32 字节，实际 {}",
                bytes.len()
            )));
        }
        let mut key = [0u8; 32];
        key.copy_from_slice(bytes);
        Ok(Self { key })
    }

    /// 生成一把新主密钥。
    pub fn generate() -> BxResult<Self> {
        Ok(Self {
            key: ids::random::<32>()?,
        })
    }

    /// 短标识：SHA-256 前 8 字节，写进文件头用于「这把钥匙对不对」的快速判断。
    pub fn id(&self) -> [u8; 8] {
        let d = Sha256::digest(self.key);
        let mut id = [0u8; 8];
        id.copy_from_slice(&d[..8]);
        id
    }

    /// 十六进制指纹，给设置页展示。
    pub fn fingerprint(&self) -> String {
        ids::hex(&self.id())
    }

    /// 导出原始字节：只在宿主需要把密钥交给 Keystore/DPAPI 包裹时使用。
    pub fn expose(&self) -> &[u8; 32] {
        &self.key
    }
}

/// 信封加密器：持有主密钥，按桶派生子密钥。
pub struct Sealer {
    master: MasterKey,
}

impl Sealer {
    /// 用主密钥构造。
    pub fn new(master: MasterKey) -> Self {
        Self { master }
    }

    /// 主密钥指纹。
    pub fn fingerprint(&self) -> String {
        self.master.fingerprint()
    }

    /// 按桶派生 32 字节子密钥。
    fn subkey(&self, scope: &str) -> BxResult<[u8; 32]> {
        let hk = Hkdf::<Sha256>::new(Some(&self.master.id()), &self.master.key);
        let mut okm = [0u8; 32];
        let info = format!("bx2/aead/v1/{scope}");
        hk.expand(info.as_bytes(), &mut okm)
            .map_err(|_| BxError::Invalid("HKDF 派生失败".into()))?;
        Ok(okm)
    }

    /// 加密一条记录。
    pub fn seal(&self, key: &Key, schema: u32, plain: &[u8]) -> BxResult<Vec<u8>> {
        let mut sub = self.subkey(key.scope())?;
        let cipher = Aes256Gcm::new_from_slice(&sub)
            .map_err(|_| BxError::Invalid("密钥长度不合法".into()))?;
        sub.zeroize();

        let aad = key.aad();
        let aad_len = u16::try_from(aad.len()).map_err(|_| BxError::Invalid("aad 过长".into()))?;

        let mut head = Vec::with_capacity(HDR_FIXED + aad.len() + NONCE_LEN + 4);
        head.extend_from_slice(MAGIC);
        head.push(ALG_AES256_GCM);
        head.push(0);
        head.extend_from_slice(&self.master.id());
        head.extend_from_slice(&schema.to_le_bytes());
        head.extend_from_slice(&aad_len.to_le_bytes());
        head.extend_from_slice(&aad);

        let nonce_bytes: [u8; NONCE_LEN] = ids::random()?;
        // AEAD 的附加数据 = 头部（含 magic/alg/key_id/schema/aad），改任何一位都解不开
        let ct = cipher
            .encrypt(
                Nonce::from_slice(&nonce_bytes),
                Payload {
                    msg: plain,
                    aad: &head,
                },
            )
            .map_err(|_| BxError::Invalid("加密失败".into()))?;

        let mut out = head;
        out.extend_from_slice(&nonce_bytes);
        out.extend_from_slice(&(ct.len() as u32).to_le_bytes());
        out.extend_from_slice(&ct);
        let digest = Sha256::digest(&out);
        out.extend_from_slice(&digest);
        Ok(out)
    }

    /// 解密一条记录。
    pub fn open(&self, key: &Key, blob: &[u8]) -> BxResult<Vec<u8>> {
        if blob.len() < HDR_FIXED + NONCE_LEN + 4 + TAG_LEN + DIGEST_LEN {
            return Err(BxError::Corrupt("文件过短"));
        }
        // 1) 先验整体摘要：截断/撕裂写在这里就止住，不会走到解密
        let body = &blob[..blob.len() - DIGEST_LEN];
        let want = &blob[blob.len() - DIGEST_LEN..];
        let got = Sha256::digest(body);
        if !ct_eq(&got, want) {
            return Err(BxError::Corrupt("整体校验和不符"));
        }
        // 2) 头部字段
        if &blob[..6] != MAGIC {
            return Err(BxError::Corrupt("魔数不符"));
        }
        if blob[6] != ALG_AES256_GCM {
            return Err(BxError::Version(blob[6] as u16));
        }
        if blob[7] != 0 {
            return Err(BxError::Corrupt("未知标志位"));
        }
        let mut key_id = [0u8; 8];
        key_id.copy_from_slice(&blob[8..16]);
        if key_id != self.master.id() {
            // 关键：这是「钥匙不对」，不是「文件坏了」。上层必须据此拒绝写入
            return Err(BxError::WrongKey);
        }
        let schema = u32::from_le_bytes(blob[16..20].try_into().unwrap());
        if schema > crate::SCHEMA {
            return Err(BxError::Version(schema as u16));
        }
        let aad_len = u16::from_le_bytes(blob[20..22].try_into().unwrap()) as usize;
        let head_end = HDR_FIXED + aad_len;
        if blob.len() < head_end + NONCE_LEN + 4 + TAG_LEN + DIGEST_LEN {
            return Err(BxError::Corrupt("头部长度越界"));
        }
        if &blob[HDR_FIXED..head_end] != key.aad().as_slice() {
            // 文件被挪到了别的桶/条目：宁可报损坏，也不能解出错位的数据
            return Err(BxError::Corrupt("aad 与键不匹配"));
        }
        let nonce = &blob[head_end..head_end + NONCE_LEN];
        let len_at = head_end + NONCE_LEN;
        let ct_len = u32::from_le_bytes(blob[len_at..len_at + 4].try_into().unwrap()) as usize;
        let ct_at = len_at + 4;
        if body.len() != ct_at + ct_len {
            return Err(BxError::Corrupt("密文长度与头部不符"));
        }

        let mut sub = self.subkey(key.scope())?;
        let cipher = Aes256Gcm::new_from_slice(&sub)
            .map_err(|_| BxError::Invalid("密钥长度不合法".into()))?;
        sub.zeroize();
        cipher
            .decrypt(
                Nonce::from_slice(nonce),
                Payload {
                    msg: &blob[ct_at..ct_at + ct_len],
                    aad: &blob[..head_end],
                },
            )
            .map_err(|_| BxError::Corrupt("解密失败（密文被篡改或密钥不符）"))
    }

    /// 把一份旧密文用新主密钥重封（密钥轮换）。
    pub fn reseal(&self, old: &Sealer, key: &Key, blob: &[u8]) -> BxResult<Vec<u8>> {
        let plain = old.open(key, blob)?;
        self.seal(key, crate::SCHEMA, &plain)
    }
}

/// 常数时间比较，避免校验和比较泄漏位置信息。
fn ct_eq(a: &[u8], b: &[u8]) -> bool {
    if a.len() != b.len() {
        return false;
    }
    let mut diff = 0u8;
    for (x, y) in a.iter().zip(b.iter()) {
        diff |= x ^ y;
    }
    diff == 0
}
