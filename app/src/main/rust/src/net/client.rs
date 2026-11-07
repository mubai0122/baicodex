//! HTTP 客户端复用。
//!
//! `reqwest::Client` 内部带连接池，**每次新建等于每次重连**：一次工具调用建一个
//! Client，握手开销能占掉大半时间。这里按超时档位各留一个静态实例。
//!
//! 三档：
//!
//! - [shared] 60 秒：普通 API 调用；
//! - [long] 300 秒：下载、大响应；
//! - [stream] 只有连接超时，没有总超时：SSE 流可能持续几分钟，总超时会把正常回复切断。

use std::sync::OnceLock;
use std::time::Duration;

use reqwest::Client;

/// 桌面端的 UA。有些站点（12306、部分搜索页）对空 UA 直接 403。
pub const UA: &str =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36";

fn build(timeout: Option<Duration>) -> Client {
    let mut b = Client::builder()
        .connect_timeout(Duration::from_secs(20))
        .user_agent(UA)
        .redirect(reqwest::redirect::Policy::limited(10))
        .pool_idle_timeout(Duration::from_secs(90));
    if let Some(t) = timeout {
        b = b.timeout(t);
    }
    b.build().unwrap_or_else(|_| Client::new())
}

/// 普通请求（60 秒）。
pub fn shared() -> &'static Client {
    static C: OnceLock<Client> = OnceLock::new();
    C.get_or_init(|| build(Some(Duration::from_secs(60))))
}

/// 长请求（300 秒）。
pub fn long() -> &'static Client {
    static C: OnceLock<Client> = OnceLock::new();
    C.get_or_init(|| build(Some(Duration::from_secs(300))))
}

/// 流式请求：不设总超时。
pub fn stream() -> &'static Client {
    static C: OnceLock<Client> = OnceLock::new();
    C.get_or_init(|| build(None))
}

/// 一次性临时客户端（需要特殊 UA / 代理时用）。
pub fn custom(timeout: Duration, ua: &str) -> Client {
    Client::builder()
        .connect_timeout(Duration::from_secs(20))
        .timeout(timeout)
        .user_agent(ua)
        .redirect(reqwest::redirect::Policy::limited(10))
        .build()
        .unwrap_or_else(|_| Client::new())
}
