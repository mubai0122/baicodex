//! 调试服务端：把 `ffi` 的 JSON 契约暴露成 HTTP + SSE。
//!
//! 存在的唯一理由：Web 前端在浏览器里开发/截图时没有 WebView2，但需要连真内核。
//! 对应前端 `packages/bridge/src/transport.ts` 里的 `HttpTransport`：
//!
//! ```text
//! POST /call    {"op":"snapshot","args":{}}  ->  {"ok":true,"data":...}
//! GET  /events  SSE，每条 data: {"type":"delta",...}
//! ```
//!
//! 只绑 127.0.0.1：这是开发口子，不该出现在网络上。生产走 WebView2 的
//! `postMessage`，不经过这里。
//!
//! 不引入 axum/hyper：只有两个路由，手写 HTTP/1.1 足够，还能省掉一堆依赖的编译时间。

use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::{TcpListener, TcpStream};

/// 起服务，一直跑到进程被结束。
pub async fn run(base: &str, port: u16) -> Result<(), String> {
    crate::ffi::boot(base).map_err(|e| e.user_text())?;
    let addr = format!("127.0.0.1:{port}");
    let listener = TcpListener::bind(&addr)
        .await
        .map_err(|e| format!("绑定 {addr} 失败：{e}"))?;
    println!("baicodex serve: http://{addr}  前端用 ?http=http://{addr} 连过来");
    loop {
        let (sock, _) = match listener.accept().await {
            Ok(v) => v,
            Err(e) => {
                eprintln!("accept 失败：{e}");
                continue;
            }
        };
        tokio::spawn(async move {
            if let Err(e) = handle(sock).await {
                eprintln!("连接结束：{e}");
            }
        });
    }
} //服务主循环

async fn handle(mut sock: TcpStream) -> Result<(), String> {
    let (head, body) = read_request(&mut sock).await?;
    let mut it = head.split_whitespace();
    let method = it.next().unwrap_or("").to_string();
    let path = it.next().unwrap_or("/").to_string();
    let route = path.split('?').next().unwrap_or("/");

    // 浏览器跨域：dev 阶段前端在 5173，服务在 8123
    if method == "OPTIONS" {
        return reply(&mut sock, 204, "", CORS).await;
    }
    match (method.as_str(), route) {
        ("POST", "/call") => {
            let req: serde_json::Value =
                serde_json::from_slice(&body).unwrap_or(serde_json::Value::Null);
            // call_value 内部 block_on，丢到阻塞线程池，别卡住 reactor
            let out = tokio::task::spawn_blocking(move || crate::ffi::call_value(&req))
                .await
                .map_err(|e| format!("调用失败：{e}"))?;
            reply(
                &mut sock,
                200,
                &out.to_string(),
                &format!("{CORS}content-type: application/json; charset=utf-8\r\n"),
            )
            .await
        }
        ("GET", "/events") => sse(sock).await,
        ("GET", "/") => {
            reply(
                &mut sock,
                200,
                r#"{"ok":true,"service":"baicodex-serve"}"#,
                &format!("{CORS}content-type: application/json\r\n"),
            )
            .await
        }
        _ => {
            reply(
                &mut sock,
                404,
                r#"{"ok":false,"error":{"message":"没有这个路由"}}"#,
                &format!("{CORS}content-type: application/json\r\n"),
            )
            .await
        }
    }
} //单请求分发

const CORS: &str = "access-control-allow-origin: *\r\n\
access-control-allow-headers: content-type\r\n\
access-control-allow-methods: GET, POST, OPTIONS\r\n";

async fn reply(sock: &mut TcpStream, code: u16, body: &str, extra: &str) -> Result<(), String> {
    let msg = format!(
        "HTTP/1.1 {code} {}\r\n{extra}content-length: {}\r\nconnection: close\r\n\r\n{body}",
        reason(code),
        body.len()
    );
    sock.write_all(msg.as_bytes())
        .await
        .map_err(|e| e.to_string())?;
    sock.flush().await.map_err(|e| e.to_string())
} //回一个完整响应

fn reason(code: u16) -> &'static str {
    match code {
        200 => "OK",
        204 => "No Content",
        400 => "Bad Request",
        404 => "Not Found",
        _ => "OK",
    }
} //状态码文案

/// 事件流：订阅内核广播，转成 SSE，每 15 秒补一个注释行当心跳。
async fn sse(mut sock: TcpStream) -> Result<(), String> {
    let head = format!(
        "HTTP/1.1 200 OK\r\ncontent-type: text/event-stream\r\ncache-control: no-cache\r\n\
connection: keep-alive\r\n{CORS}\r\n"
    );
    sock.write_all(head.as_bytes())
        .await
        .map_err(|e| e.to_string())?;
    sock.flush().await.map_err(|e| e.to_string())?;

    let Some(mut rx) = crate::ffi::subscribe_events() else {
        return Ok(());
    };
    loop {
        tokio::select! {
            got = rx.recv() => match got {
                Ok(ev) => {
                    let data = serde_json::to_string(&ev).unwrap_or_else(|_| "{}".to_string());
                    let chunk = format!("data: {data}\n\n");
                    if sock.write_all(chunk.as_bytes()).await.is_err() {
                        return Ok(());
                    }
                    if sock.flush().await.is_err() {
                        return Ok(());
                    }
                }
                // 前端卡了一下导致积压：丢事件比断开好，界面下一帧会用 snapshot 补齐
                Err(tokio::sync::broadcast::error::RecvError::Lagged(n)) => {
                    eprintln!("事件积压，丢了 {n} 条");
                }
                Err(_) => return Ok(()),
            },
            _ = tokio::time::sleep(std::time::Duration::from_secs(15)) => {
                if sock.write_all(b": ping\n\n").await.is_err() {
                    return Ok(());
                }
            }
        }
    }
} //SSE 事件流

async fn read_request(sock: &mut TcpStream) -> Result<(String, Vec<u8>), String> {
    let mut buf = Vec::new();
    let mut tmp = [0u8; 4096];
    let head_end = loop {
        let n = sock.read(&mut tmp).await.map_err(|e| e.to_string())?;
        if n == 0 {
            return Err("连接提前关闭".to_string());
        }
        buf.extend_from_slice(&tmp[..n]);
        if let Some(p) = buf.windows(4).position(|w| w == b"\r\n\r\n") {
            break p;
        }
        if buf.len() > 64 * 1024 {
            return Err("请求头过大".to_string());
        }
    };
    let head = String::from_utf8_lossy(&buf[..head_end]).to_string();
    let want = content_length(&head);
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
} //读一个完整请求

fn content_length(head: &str) -> usize {
    for line in head.lines() {
        let lower = line.to_ascii_lowercase();
        if let Some(rest) = lower.strip_prefix("content-length:") {
            return rest.trim().parse().unwrap_or(0);
        }
    }
    0
} //取 body 长度