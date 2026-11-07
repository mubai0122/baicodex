//! 内网直连通道：PC 与手机之间**不落地整文件**的极简文件读写 HTTP。
//!
//! ## 为什么单开一个服务，而不是复用 `serve`
//!
//! `serve` 是开发用的 `/call` + SSE 口子，绑死 127.0.0.1、CORS 全开、没有鉴权。
//! 把它开到 0.0.0.0 等于把整个内核 op 暴露给同网段。relay 只做四件事：
//! 列目录、看大小、**按字节区间读**、**按偏移写**——暴露面小到可以逐行审。
//!
//! ## 鉴权
//!
//! ```text
//! loopback(127.0.0.1 / ::1)   免 token（本机自己用）
//! 其它地址                    必须同时满足：
//!                               1. 对端 IP 命中白名单（默认空 = 全拒）
//!                               2. 带本次启动生成的 token
//! ```
//!
//! **IP 白名单不是访问控制**：同一 WiFi 下任何设备都在 `192.168.1.0/24` 里，
//! 而 IP 是自报的。它的作用是「别让别的网段误连」，真正挡住陌生设备的是 token。
//! 所以默认 `require_token = true`；把它关掉只是方便你在完全可信的机器上调试。
//!
//! ## token 在手 = 能读这台机器的任何文件
//!
//! 这是「远程文件管理器」的定位，不要假装还有沙箱。真要加强，只能加路径前缀
//! 白名单，那是下一步的事（见 plan S6）。
use std::io::{Read, Seek, SeekFrom, Write};
use std::net::IpAddr;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, Mutex, OnceLock};

use serde_json::{json, Value};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

/// 默认端口。选一个不常见的，避免和 8080/3000 这类开发口撞。
pub const DEFAULT_PORT: u16 = 8317;
/// 单次区间读的上限：64MB。再大就该分块了，一次读回来只会把内存打满。
const MAX_READ: u64 = 64 * 1024 * 1024;
/// 请求头上限。
const MAX_HEAD: usize = 32 * 1024;

/// 启动参数。
#[derive(Debug, Clone)]
pub struct Cfg {
    pub port: u16,
    pub require_token: bool,
    /// 白名单条目：`192.168.1.7` 或 `192.168.1.0/24`。空 = 非本机一律拒。
    pub allow: Vec<String>,
}

impl Default for Cfg {
    fn default() -> Self {
        Self { port: DEFAULT_PORT, require_token: true, allow: Vec::new() }
    }
}

/// 运行中服务的自报信息，给界面和 PC 端用。
#[derive(Debug, Clone)]
pub struct Status {
    pub port: u16,
    pub token: String,
    pub require_token: bool,
    pub allow: Vec<String>,
    pub lan: Vec<String>,
    pub hits: u64,
}

struct State {
    token: String,
    require_token: bool,
    allow: Mutex<Vec<String>>,
    hits: AtomicU64,
}

struct Running {
    port: u16,
    token: String,
    state: Arc<State>,
    task: tokio::task::JoinHandle<()>,
}

static RUN: OnceLock<Mutex<Option<Running>>> = OnceLock::new();

fn slot() -> &'static Mutex<Option<Running>> {
    RUN.get_or_init(|| Mutex::new(None))
}

/// 起服务。已经在跑就先停掉再起（换端口/换白名单都走这里）。
pub async fn start(cfg: Cfg) -> Result<Status, String> {
    stop();
    let bind = format!("0.0.0.0:{}", cfg.port);
    let listener = TcpListener::bind(&bind)
        .await
        .map_err(|e| format!("绑定 {bind} 失败：{e}"))?;
    let token = new_token();
    let state = Arc::new(State {
        token: token.clone(),
        require_token: cfg.require_token,
        allow: Mutex::new(cfg.allow.clone()),
        hits: AtomicU64::new(0),
    });
    let st = state.clone();
    let task = tokio::spawn(async move {
        loop {
            match listener.accept().await {
                Ok((sock, _)) => {
                    let st = st.clone();
                    tokio::spawn(async move {
                        if let Err(e) = handle(sock, st).await {
                            eprintln!("relay 连接结束：{e}");
                        }
                    });
                }
                Err(e) => {
                    eprintln!("relay accept 失败：{e}");
                    break;
                }
            }
        }
    });
    let run = Running { port: cfg.port, token: token.clone(), state: state.clone(), task };
    *slot().lock().unwrap_or_else(|e| e.into_inner()) = Some(run);
    Ok(query())
}

/// 停服务。没在跑就是空操作。
pub fn stop() {
    let mut g = slot().lock().unwrap_or_else(|e| e.into_inner());
    if let Some(r) = g.take() {
        r.task.abort();
    }
}

/// 当前状态。没在跑时 `running=false`。
pub fn query() -> Status {
    let g = slot().lock().unwrap_or_else(|e| e.into_inner());
    match g.as_ref() {
        None => Status {
            port: 0,
            token: String::new(),
            require_token: true,
            allow: Vec::new(),
            lan: lan_ips(),
            hits: 0,
        },
        Some(r) => Status {
            port: r.port,
            token: r.token.clone(),
            require_token: r.state.require_token,
            allow: r.state.allow.lock().unwrap_or_else(|e| e.into_inner()).clone(),
            lan: lan_ips(),
            hits: r.state.hits.load(Ordering::Relaxed),
        },
    }
}

/// 改白名单，不用重启服务。
pub fn set_allow(allow: Vec<String>) -> Status {
    {
        let g = slot().lock().unwrap_or_else(|e| e.into_inner());
        if let Some(r) = g.as_ref() {
            *r.state.allow.lock().unwrap_or_else(|e| e.into_inner()) = allow;
        }
    }
    query()
}

/// 状态转 JSON，给 op 直接回。
pub fn as_json(s: &Status) -> Value {
    let running = s.port != 0;
    json!({
        "running": running,
        "port": s.port,
        "token": if running && s.require_token { s.token.clone() } else { String::new() },
        "require_token": s.require_token,
        "allow": s.allow,
        "lan": s.lan,
        "hits": s.hits,
        // 界面直接可以点着复制的一行地址
        "endpoint": s.lan.first().map(|ip| format!("http://{ip}:{}", s.port)).unwrap_or_default(),
    })
}

/// 本机可能是「内网可达」的地址。用 UDP connect 探出口，不真的发包。
pub fn lan_ips() -> Vec<String> {
    let mut out: Vec<String> = Vec::new();
    if let Ok(sock) = std::net::UdpSocket::bind("0.0.0.0:0") {
        if sock.connect("8.8.8.8:80").is_ok() {
            if let Ok(a) = sock.local_addr() {
                let ip = a.ip();
                if !ip.is_loopback() && !ip.is_unspecified() {
                    out.push(ip.to_string());
                }
            }
        }
    }
    out
}

fn new_token() -> String {
    // 时间 + 进程号 + 地址熵，够用；不引 rand 只为一个口令
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    let pid = std::process::id() as u128;
    // 栈上变量的地址也参与熵：进程重启时栈布局会变，避免同一纳秒粒度内重复
    let ent = String::new();
    let mut x = nanos ^ (pid << 64) ^ (&ent as *const String as usize as u128);
    let mut s = String::with_capacity(32);
    const ALPHA: &[u8] = b"23456789abcdefghjkmnpqrstuvwxyz";
    for _ in 0..32 {
        x ^= x << 13;
        x ^= x >> 7;
        x ^= x << 17;
        s.push(ALPHA[(x % ALPHA.len() as u128) as usize] as char);
    }
    s
}

/// 定长比，避免按字节提前返回泄漏信息。
fn eq_quiet(a: &str, b: &str) -> bool {
    let (a, b) = (a.as_bytes(), b.as_bytes());
    if a.len() != b.len() {
        return false;
    }
    let mut d = 0u8;
    for i in 0..a.len() {
        d |= a[i] ^ b[i];
    }
    d == 0
}

fn in_allow(ip: IpAddr, allow: &[String]) -> bool {
    allow.iter().any(|e| {
        let e = e.trim();
        if e.is_empty() {
            return false;
        }
        if let Ok(single) = e.parse::<IpAddr>() {
            return single == ip;
        }
        cidr_hit(ip, e)
    })
}

fn cidr_hit(ip: IpAddr, pat: &str) -> bool {
    let Some((net, bits)) = pat.split_once('/') else {
        return false;
    };
    let (Ok(net), Ok(bits)) = (net.trim().parse::<IpAddr>(), bits.trim().parse::<u8>()) else {
        return false;
    };
    match (ip, net) {
        (IpAddr::V4(a), IpAddr::V4(n)) => {
            if bits > 32 {
                return false;
            }
            let m = if bits == 0 { 0 } else { u32::MAX << (32 - bits) };
            (u32::from(a) & m) == (u32::from(n) & m)
        }
        (IpAddr::V6(a), IpAddr::V6(n)) => {
            if bits > 128 {
                return false;
            }
            let m = if bits == 0 { 0 } else { u128::MAX << (128 - bits) };
            (u128::from(a) & m) == (u128::from(n) & m)
        }
        _ => false,
    }
}

async fn handle(mut sock: TcpStream, state: Arc<State>) -> Result<(), String> {
    let peer = sock.peer_addr().map(|a| a.ip()).ok();
    let (head, body) = read_request(&mut sock).await?;
    let mut it = head.lines().next().unwrap_or("").split_whitespace();
    let method = it.next().unwrap_or("").to_string();
    let target = it.next().unwrap_or("/").to_string();
    let (route, query) = match target.split_once('?') {
        Some((r, q)) => (r.to_string(), q.to_string()),
        None => (target.clone(), String::new()),
    };

    // —— 鉴权 ——
    let local = peer.map(|p| p.is_loopback()).unwrap_or(false);
    if !local {
        let ip = match peer {
            Some(ip) => ip,
            None => return reply_json(&mut sock, 403, json!({"ok": false, "error": "拿不到对端地址"})).await,
        };
        let allow = state.allow.lock().unwrap_or_else(|e| e.into_inner()).clone();
        if !in_allow(ip, &allow) {
            return reply_json(
                &mut sock,
                403,
                json!({"ok": false, "error": format!("{ip} 不在白名单里")}),
            )
            .await;
        }
        if state.require_token {
            let want = header(&head, "x-bai-token").or_else(|| qs(&query, "t")).unwrap_or_default();
            if !eq_quiet(&want, &state.token) {
                return reply_json(&mut sock, 401, json!({"ok": false, "error": "token 不对"})).await;
            }
        }
    }
    state.hits.fetch_add(1, Ordering::Relaxed);

    match (method.as_str(), route.as_str()) {
        ("GET", "/ping") => {
            reply_json(
                &mut sock,
                200,
                json!({
                    "ok": true,
                    "service": "baicodex-relay",
                    "os": crate::core::base::sys::os(),
                    "line": crate::core::base::sys::device_line(),
                    "peer": peer.map(|p| p.to_string()),
                }),
            )
            .await
        }
        ("GET", "/fs/list") => {
            let path = qs(&query, "path").unwrap_or_else(|| "/".to_string());
            match list_dir(&path).await {
                Ok(v) => reply_json(&mut sock, 200, json!({ "ok": true, "path": path, "entries": v })).await,
                Err(e) => reply_json(&mut sock, 400, json!({ "ok": false, "error": e })).await,
            }
        }
        ("GET", "/fs/stat") => {
            let path = qs(&query, "path").unwrap_or_default();
            match stat_path(&path).await {
                Ok(v) => reply_json(&mut sock, 200, json!({ "ok": true, "path": path, "stat": v })).await,
                Err(e) => reply_json(&mut sock, 400, json!({ "ok": false, "error": e })).await,
            }
        }
        ("GET", "/fs/read") => {
            let path = qs(&query, "path").unwrap_or_default();
            let off = qs(&query, "off").and_then(|v| v.parse::<u64>().ok()).unwrap_or(0);
            let len = qs(&query, "len").and_then(|v| v.parse::<u64>().ok()).unwrap_or(64 * 1024).min(MAX_READ);
            match read_range(path, off, len).await {
                // 区间读统一回 octet-stream，客户端按 offset 自己拼
                Ok((buf, total)) => reply_bytes(&mut sock, 200, &buf, total).await,
                Err(e) => reply_json(&mut sock, 400, json!({ "ok": false, "error": e })).await,
            }
        }
        ("PUT", "/fs/write") => {
            let path = qs(&query, "path").unwrap_or_default();
            let off = qs(&query, "off").and_then(|v| v.parse::<u64>().ok()).unwrap_or(0);
            let n = body.len();
            match write_range(path, off, body).await {
                Ok(total) => reply_json(&mut sock, 200, json!({ "ok": true, "wrote": n, "off": off, "size": total })).await,
                Err(e) => reply_json(&mut sock, 400, json!({ "ok": false, "error": e })).await,
            }
        }
        ("POST", "/fs/truncate") => {
            let path = qs(&query, "path").unwrap_or_default();
            let size = qs(&query, "size").and_then(|v| v.parse::<u64>().ok()).unwrap_or(0);
            match truncate(path, size).await {
                Ok(v) => reply_json(&mut sock, 200, json!({ "ok": true, "size": v })).await,
                Err(e) => reply_json(&mut sock, 400, json!({ "ok": false, "error": e })).await,
            }
        }
        _ => reply_json(&mut sock, 404, json!({"ok": false, "error": "没有这个路由"})).await,
    }
}

async fn list_dir(path: &str) -> Result<Vec<Value>, String> {
    let p = PathBuf::from(path);
    let md = tokio::task::spawn_blocking(move || std::fs::metadata(&p).map(|m| m.is_dir()))
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| format!("{path} 打不开：{e}"))?;
    if !md {
        return Err(format!("{path} 不是目录"));
    }
    // spawn_blocking 要求 'static，path 必须先转成自有字符串再 move 进去
    let dir = path.to_string();
    let items = tokio::task::spawn_blocking(move || {
        let mut out: Vec<Value> = Vec::new();
        let rd = match std::fs::read_dir(&dir) {
            Ok(r) => r,
            Err(e) => return Err(format!("读目录失败：{e}")),
        };
        for ent in rd.flatten() {
            let meta = ent.metadata().ok();
            out.push(json!({
                "name": ent.file_name().to_string_lossy(),
                "dir": meta.as_ref().map(|m| m.is_dir()).unwrap_or(false),
                "size": meta.as_ref().map(|m| m.len()).unwrap_or(0),
                "mtime": meta.as_ref().and_then(|m| m.modified().ok())
                    .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                    .map(|d| d.as_millis() as u64).unwrap_or(0),
            }));
        }
        out.sort_by(|a, b| {
            let ad = a["dir"].as_bool().unwrap_or(false);
            let bd = b["dir"].as_bool().unwrap_or(false);
            bd.cmp(&ad)
                .then_with(|| a["name"].as_str().unwrap_or("").cmp(b["name"].as_str().unwrap_or("")))
        });
        Ok(out)
    })
    .await
    .map_err(|e| e.to_string())?;
    items
}

async fn stat_path(path: &str) -> Result<Value, String> {
    let p = PathBuf::from(path);
    let md = tokio::task::spawn_blocking(move || std::fs::metadata(&p))
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| format!("{path} 取不到信息：{e}"))?;
    Ok(json!({
        "dir": md.is_dir(),
        "size": md.len(),
        "mtime": md.modified().ok()
            .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
            .map(|d| d.as_millis() as u64).unwrap_or(0),
    }))
}

/// 按区间读。返回（数据, 文件总大小）。
async fn read_range(path: String, off: u64, len: u64) -> Result<(Vec<u8>, u64), String> {
    tokio::task::spawn_blocking(move || {
        let mut f = std::fs::File::open(&path).map_err(|e| format!("{path} 打不开：{e}"))?;
        let total = f.metadata().map_err(|e| e.to_string())?.len();
        if off >= total {
            return Ok((Vec::new(), total));
        }
        f.seek(SeekFrom::Start(off)).map_err(|e| e.to_string())?;
        let want = len.min(total - off) as usize;
        let mut buf = vec![0u8; want];
        f.read_exact(&mut buf).map_err(|e| format!("读 {path} 失败：{e}"))?;
        Ok((buf, total))
    })
    .await
    .map_err(|e| e.to_string())?
}

/// 按偏移写。返回写完后的文件大小。
async fn write_range(path: String, off: u64, data: Vec<u8>) -> Result<u64, String> {
    tokio::task::spawn_blocking(move || {
        let p = Path::new(&path);
        if let Some(dir) = p.parent() {
            if !dir.as_os_str().is_empty() {
                std::fs::create_dir_all(dir).map_err(|e| format!("建目录失败：{e}"))?;
            }
        }
        let mut f = std::fs::OpenOptions::new()
            .create(true)
            .read(true)
            .write(true)
            .open(p)
            .map_err(|e| format!("{path} 打不开：{e}"))?;
        f.seek(SeekFrom::Start(off)).map_err(|e| e.to_string())?;
        f.write_all(&data).map_err(|e| format!("写 {path} 失败：{e}"))?;
        f.flush().map_err(|e| e.to_string())?;
        Ok(f.metadata().map(|m| m.len()).unwrap_or(0))
    })
    .await
    .map_err(|e| e.to_string())?
}

async fn truncate(path: String, size: u64) -> Result<u64, String> {
    tokio::task::spawn_blocking(move || {
        let f = std::fs::OpenOptions::new()
            .create(true)
            .write(true)
            .open(&path)
            .map_err(|e| format!("{path} 打不开：{e}"))?;
        f.set_len(size).map_err(|e| format!("{path} 改大小失败：{e}"))?;
        Ok(size)
    })
    .await
    .map_err(|e| e.to_string())?
}

async fn reply_json(sock: &mut TcpStream, code: u16, v: Value) -> Result<(), String> {
    let body = v.to_string();
    let head = format!(
        "HTTP/1.1 {code} {}\r\ncontent-type: application/json; charset=utf-8\r\ncontent-length: {}\r\nconnection: close\r\n\r\n",
        reason(code),
        body.len()
    );
    sock.write_all(head.as_bytes()).await.map_err(|e| e.to_string())?;
    sock.write_all(body.as_bytes()).await.map_err(|e| e.to_string())?;
    sock.flush().await.map_err(|e| e.to_string())
}

/// 二进制响应。带 `x-file-size`，客户端据此算还剩多少块。
async fn reply_bytes(sock: &mut TcpStream, code: u16, body: &[u8], total: u64) -> Result<(), String> {
    let head = format!(
        "HTTP/1.1 {code} {}\r\ncontent-type: application/octet-stream\r\ncontent-length: {}\r\nx-file-size: {total}\r\nconnection: close\r\n\r\n",
        reason(code),
        body.len()
    );
    sock.write_all(head.as_bytes()).await.map_err(|e| e.to_string())?;
    sock.write_all(body).await.map_err(|e| e.to_string())?;
    sock.flush().await.map_err(|e| e.to_string())
}

fn reason(code: u16) -> &'static str {
    match code {
        200 => "OK",
        400 => "Bad Request",
        401 => "Unauthorized",
        403 => "Forbidden",
        404 => "Not Found",
        413 => "Payload Too Large",
        _ => "OK",
    }
}

fn header(head: &str, name: &str) -> Option<String> {
    let want = name.to_ascii_lowercase();
    for line in head.lines().skip(1) {
        if let Some((k, v)) = line.split_once(':') {
            if k.trim().to_ascii_lowercase() == want {
                return Some(v.trim().to_string());
            }
        }
    }
    None
}

/// 取查询参数并做一次 URL 解码（路径里有空格/中文必须解）。
fn qs(query: &str, key: &str) -> Option<String> {
    for kv in query.split('&') {
        // 没有 '=' 的段直接跳过，不能用 ? 提前返回：`a&t=xxx` 会让 t 永远查不到
        let Some((k, v)) = kv.split_once('=') else { continue };
        if k == key {
            return Some(url_decode(v));
        }
    }
    None
}

fn url_decode(s: &str) -> String {
    let b = s.as_bytes();
    let mut out: Vec<u8> = Vec::with_capacity(b.len());
    let mut i = 0;
    while i < b.len() {
        match b[i] {
            b'%' if i + 2 < b.len() => {
                let hi = (b[i + 1] as char).to_digit(16);
                let lo = (b[i + 2] as char).to_digit(16);
                match (hi, lo) {
                    (Some(h), Some(l)) => {
                        out.push((h * 16 + l) as u8);
                        i += 3;
                    }
                    _ => {
                        out.push(b[i]);
                        i += 1;
                    }
                }
            }
            b'+' => {
                out.push(b' ');
                i += 1;
            }
            c => {
                out.push(c);
                i += 1;
            }
        }
    }
    String::from_utf8_lossy(&out).to_string()
}

async fn read_request(sock: &mut TcpStream) -> Result<(String, Vec<u8>), String> {
    let mut buf = Vec::new();
    let mut tmp = [0u8; 8192];
    let head_end = loop {
        let n = sock.read(&mut tmp).await.map_err(|e| e.to_string())?;
        if n == 0 {
            return Err("连接提前关闭".to_string());
        }
        buf.extend_from_slice(&tmp[..n]);
        if let Some(p) = buf.windows(4).position(|w| w == b"\r\n\r\n") {
            break p;
        }
        if buf.len() > MAX_HEAD {
            return Err("请求头过大".to_string());
        }
    };
    let head = String::from_utf8_lossy(&buf[..head_end]).to_string();
    let want = header(&head, "content-length")
        .and_then(|v| v.parse::<usize>().ok())
        .unwrap_or(0);
    let mut body = buf[head_end + 4..].to_vec();
    while body.len() < want {
        let n = sock.read(&mut tmp).await.map_err(|e| e.to_string())?;
        if n == 0 {
            break;
        }
        body.extend_from_slice(&tmp[..n]);
    }
    body.truncate(want);
    Ok((head, body))
} //读一个完整请求（头部 + 按 content-length 补齐的 body）