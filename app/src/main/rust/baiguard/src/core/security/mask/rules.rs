//! 规则表：键名 / 已知前缀 / 认证头 / 长度阈值 / 掩码串
//!
//! 键名与前缀一律按长度降序排列：短项先匹配会把 access_token 拆成 access_ + token，
//! 也会让 sk_live_ 被 sk- 抢先吃掉一半。

use crate::core::base::{bytes, kv, marks};

/// 生效中的一套脱敏规则
pub struct Rules {
    /// 键名：命中之上的值整段打码
    pub keys: Vec<Vec<u8>>,
    /// 已知凭据前缀：sk- / ghp_ / AKIA 这类，连同后面的密文一起打码
    pub prefixes: Vec<Vec<u8>>,
    /// 认证头标签：Bearer / Basic 这种，后面跟的就是凭据
    pub auth: Vec<Vec<u8>>,
    /// 前缀后面密文的最短长度，太短当普通文本
    pub min_len: usize,
    /// 没有键名也没有前缀时，靠高熵判定打码的最短长度
    pub alnum_len: usize,
    /// 键值对里值的最短长度，太短不打码（避免 password:1 把结构打烂）
    pub min_val: usize,
    /// 掩码串
    pub mark: Vec<u8>,
}

impl Rules {
    /// 内置兜底：服务端规则拉不到时用这套，覆盖最常见的凭据形态
    pub fn builtin() -> Rules {
        let keys = vec![
            "refresh_token",
            "access_token",
            "id_token",
            "client_secret",
            "private_key",
            "authorization",
            "x-api-key",
            "api_key",
            "api-key",
            "apikey",
            "access_key",
            "secret_key",
            "secretkey",
            "password_hash",
            "password",
            "passwd",
            "passphrase",
            "token",
            "secret",
            "cookie",
            "set-cookie",
            "sessionid",
            "session_id",
            "sessid",
            "credential",
            "credentials",
            "bearer",
            "pwd",
            "pass",
        ];
        let prefixes = vec![
            "sk_live_", "sk_test_", "sk-", "rk_live_", "github_pat_", "ghp_", "gho_", "ghu_", "ghs_",
            "xoxb-", "xoxp-", "xoxa-", "xoxr-", "AKIA", "ASIA", "AIza", "ya29.",
        ];
        let auth = vec!["Bearer ", "Basic ", "Token "];
        Rules {
            keys: rank(keys),
            prefixes: rank(prefixes),
            auth: rank(auth),
            min_len: 16,
            alnum_len: 24,
            min_val: 4,
            mark: b"******".to_vec(),
        }
    } //内置兜底

    /// 从 at 起命中哪个键名（长键优先）
    pub fn hit_key(&self, src: &[u8], at: usize) -> Option<usize> {
        self.keys
            .iter()
            .find(|k| bytes::starts_ci(src, at, k))
            .map(|k| k.len())
    } //命中键名

    /// 从 at 起命中哪个已知前缀
    pub fn hit_prefix(&self, src: &[u8], at: usize) -> Option<usize> {
        self.prefixes
            .iter()
            .find(|p| bytes::starts_ci(src, at, p))
            .map(|p| p.len())
    } //命中前缀

    /// 从 at 起命中哪个认证头标签
    pub fn hit_auth(&self, src: &[u8], at: usize) -> Option<usize> {
        self.auth
            .iter()
            .find(|t| bytes::starts_ci(src, at, t))
            .map(|t| t.len())
    } //命中认证头
}

/// 长项在前，同长按字典序，保证匹配结果稳定
fn rank(items: Vec<&str>) -> Vec<Vec<u8>> {
    let mut out: Vec<Vec<u8>> = items.into_iter().map(|s| s.as_bytes().to_vec()).collect();
    sort(&mut out);
    out
} //排成确定顺序

fn sort(items: &mut Vec<Vec<u8>>) {
    items.sort_by(|a, b| b.len().cmp(&a.len()).then_with(|| a.cmp(b)));
} //按长度降序

/// 解析服务端下发的规则文本，缺省字段继承内置兜底
pub fn parse(text: &[u8]) -> Rules {
    let mut r = Rules::builtin();
    for (key, val) in kv::pairs(text) {
        if val.is_empty() {
            continue;
        }
        match key.as_str() {
            "keys" => {
                let v = kv::csv(&val);
                if !v.is_empty() {
                    r.keys = owned(v);
                }
            }
            "prefixes" => {
                let v = kv::csv(&val);
                if !v.is_empty() {
                    r.prefixes = owned(v);
                }
            }
            "auth" => {
                let v = kv::csv(&val);
                if !v.is_empty() {
                    r.auth = owned(v);
                }
            }
            "minlen" => r.min_len = kv::usize_in(&val, r.min_len, 4, 512),
            "alnum" => r.alnum_len = kv::usize_in(&val, r.alnum_len, 8, 4096),
            "minval" => r.min_val = kv::usize_in(&val, r.min_val, 1, 64),
            "mask" => r.mark = marks::normalize(val.as_bytes()),
            _ => {}
        }
    }
    r
} //解析规则

fn owned(items: Vec<String>) -> Vec<Vec<u8>> {
    let mut out: Vec<Vec<u8>> = items.into_iter().map(|s| s.as_bytes().to_vec()).collect();
    sort(&mut out);
    out
} //转成字节表

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn long_key_first() {
        let r = Rules::builtin();
        //access_token 必须整体命中，不能被 token 截成一半
        assert_eq!(r.hit_key(b"access_token=x", 0), Some(12));
        assert_eq!(r.hit_key(b"\"access_token\": 1", 1), Some(12));
        assert_eq!(r.hit_key(b"token=1", 0), Some(5));
    }

    #[test]
    fn prefix_long_first() {
        let r = Rules::builtin();
        assert_eq!(r.hit_prefix(b"sk_live_abc", 0), Some(8));
        assert_eq!(r.hit_prefix(b"sk-abc", 0), Some(3));
        assert_eq!(r.hit_prefix(b"ghp_abc", 0), Some(4));
        assert_eq!(r.hit_prefix(b"zzz", 0), None);
    }

    #[test]
    fn auth_tags() {
        let r = Rules::builtin();
        assert_eq!(r.hit_auth(b"Bearer xyz", 0), Some(7));
        assert_eq!(r.hit_auth(b"basic xyz", 0), Some(6));
        assert_eq!(r.hit_auth(b"Digest xyz", 0), None);
    }

    #[test]
    fn parse_overrides_but_keeps_defaults() {
        let r = parse(b"keys=foo,bar\nminlen=9999\nmask=##\nalnum=abc\n");
        assert_eq!(r.keys.len(), 2);
        assert_eq!(r.min_len, 512); //超上限被夹到 512
        assert_eq!(r.mark, b"##".to_vec());
        assert_eq!(r.alnum_len, 24); //坏值退回默认
        assert!(!r.prefixes.is_empty()); //没下发的字段继续用内置
    }

    #[test]
    fn parse_bad_mask_falls_back() {
        let r = parse(b"mask=\n");
        //空值该行被跳过，掩码串仍是内置的六个星号
        assert_eq!(r.mark, b"******".to_vec());
        let r2 = parse(b"mask=*\n\n\t");
        assert_eq!(r2.mark, b"*".to_vec());
    }
}