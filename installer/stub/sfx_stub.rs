// Project:    Baicodex
// Author:     by 沐白/Baix
// License:    AGPL-3.0 (GNU Affero General Public License v3.0)
// Date:       2026-11-27 03:05:00
// Repository: https://github.com/mubai0122/Baicodex
// Website:    https://agent.ai.baiccb.cn/
//
// 自解压头（SFX stub，纯标准库，离线可编译）
//
// 安装包布局：
//   [stub.exe][installer.zip][app.zip][footer 48B]
// footer = magic(16) + installerOffset(8) + installerLength(8) + appOffset(8) + appLength(8)
//
// 双击后：把 installer 段解到 %TEMP%，启动里面的安装向导，等它退出再清场。
// 安装向导自己从本 exe 尾部解压 app 段到目标目录，所以中间不留大文件。
//
// 被复制到安装目录的 uninstall.exe 也是它：带 --uninstall 时走卸载分支，
// 卸载时再把自己复制到 TEMP 由副本动手，免得进程占着目录删不干净。

#![windows_subsystem = "windows"]

use std::env;
use std::ffi::c_void;
use std::fs::{self, File};
use std::io::{Read, Seek, SeekFrom, Write};
use std::os::windows::process::CommandExt;
use std::path::{Path, PathBuf};
use std::process::Command;

const MAGIC: &[u8; 16] = b"BAICODEXSFX01\0\0\0";
const FOOTER_LEN: i64 = 48;
const CREATE_NO_WINDOW: u32 = 0x0800_0000;
const MB_ICONERROR: u32 = 0x0000_0010;
const MB_ICONINFORMATION: u32 = 0x0000_0040;
const MB_YESNOCANCEL: u32 = 0x0000_0003;
const MB_ICONQUESTION: u32 = 0x0000_0020;
const IDYES: i32 = 6;
const IDNO: i32 = 7;

#[link(name = "user32")]
extern "system" {
    fn MessageBoxW(hwnd: *mut c_void, text: *const u16, caption: *const u16, utype: u32) -> i32;
}

fn wide(s: &str) -> Vec<u16> {
    s.encode_utf16().chain(std::iter::once(0)).collect()
}

fn msgbox(text: &str, caption: &str, flags: u32) -> i32 {
    let t = wide(text);
    let c = wide(caption);
    unsafe { MessageBoxW(std::ptr::null_mut(), t.as_ptr(), c.as_ptr(), flags) }
}

struct Footer {
    installer_off: u64,
    installer_len: u64,
}

fn u64_at(buf: &[u8], at: usize) -> u64 {
    let mut tmp = [0u8; 8];
    tmp.copy_from_slice(&buf[at..at + 8]);
    u64::from_le_bytes(tmp)
}

fn read_footer(exe: &Path) -> Result<Footer, String> {
    let mut file = File::open(exe).map_err(|e| format!("打不开安装包：{e}"))?;
    let size = file.metadata().map_err(|e| e.to_string())?.len() as i64;
    if size < FOOTER_LEN {
        return Err("安装包不完整（太小）".into());
    }
    file.seek(SeekFrom::End(-FOOTER_LEN))
        .map_err(|e| e.to_string())?;
    let mut buf = [0u8; 48];
    file.read_exact(&mut buf).map_err(|e| e.to_string())?;
    if &buf[0..16] != MAGIC {
        return Err("这不是完整的 Baicodex 安装包（尾部标识缺失，可能下载被截断）".into());
    }
    Ok(Footer {
        installer_off: u64_at(&buf, 16),
        installer_len: u64_at(&buf, 24),
    })
}

fn copy_range(src: &Path, off: u64, len: u64, dst: &Path) -> Result<(), String> {
    let mut input = File::open(src).map_err(|e| e.to_string())?;
    input.seek(SeekFrom::Start(off)).map_err(|e| e.to_string())?;
    let mut out = File::create(dst).map_err(|e| format!("写临时文件失败：{e}"))?;
    let mut left = len;
    let mut buf = vec![0u8; 1 << 20];
    while left > 0 {
        let want = std::cmp::min(left, buf.len() as u64) as usize;
        let n = input.read(&mut buf[..want]).map_err(|e| e.to_string())?;
        if n == 0 {
            return Err("安装包被截断（读取提前结束）".into());
        }
        out.write_all(&buf[..n]).map_err(|e| e.to_string())?;
        left -= n as u64;
    }
    out.flush().ok();
    Ok(())
}

fn system_tar() -> PathBuf {
    let root = env::var("SystemRoot").unwrap_or_else(|_| "C:\\Windows".to_string());
    let candidate = PathBuf::from(&root).join("System32").join("tar.exe");
    if candidate.exists() {
        candidate
    } else {
        PathBuf::from("tar.exe")
    }
}

fn run_setup(exe: &Path) -> Result<i32, String> {
    let footer = read_footer(exe)?;
    let temp = env::temp_dir().join(format!("baicodex-setup-{}", std::process::id()));
    let _ = fs::remove_dir_all(&temp);
    fs::create_dir_all(&temp).map_err(|e| format!("建临时目录失败：{e}"))?;

    let archive = temp.join("installer.zip");
    copy_range(exe, footer.installer_off, footer.installer_len, &archive)?;

    let unpacked = temp.join("setup");
    fs::create_dir_all(&unpacked).map_err(|e| e.to_string())?;
    let tar = system_tar();
    // bsdtar 能直接解 zip；系统没带 tar 的机器会在这里报错
    let status = Command::new(&tar)
        .arg("-xf")
        .arg(&archive)
        .arg("-C")
        .arg(&unpacked)
        .creation_flags(CREATE_NO_WINDOW)
        .status()
        .map_err(|e| format!("调用 {} 失败：{e}", tar.display()))?;
    if !status.success() {
        return Err("解压安装向导失败（系统 tar 返回错误）".into());
    }

    let setup = unpacked.join("BaicodexSetup.exe");
    if !setup.exists() {
        return Err("安装向导缺失：BaicodexSetup.exe 不在解压目录里".into());
    }

    // 原始参数继续往下传，--silent、--dir=… 这些由安装向导自己解析
    let passthrough: Vec<String> = env::args().skip(1).collect();
    let status = Command::new(&setup)
        .arg("--sfx")
        .arg(exe)
        .args(&passthrough)
        .current_dir(&unpacked)
        .status()
        .map_err(|e| format!("启动安装向导失败：{e}"))?;
    let code = status.code().unwrap_or(0);

    // 向导退出后再清，避免它还在读自己的 runtime
    std::thread::sleep(std::time::Duration::from_millis(500));
    let _ = fs::remove_dir_all(&temp);
    Ok(code)
}

fn ps_quote(s: &str) -> String {
    format!("'{}'", s.replace('\'', "''"))
}

/// 删注册表键：先 reg.exe（最轻），被判拦截再退回 PowerShell
fn reg_delete(key: &str) {
    let ok = Command::new("reg")
        .args(["delete", key, "/f"])
        .creation_flags(CREATE_NO_WINDOW)
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    if ok {
        return;
    }
    let drive = key.replace("HKLM\\", "HKLM:\\").replace("HKCU\\", "HKCU:\\");
    let script = format!(
        "$ProgressPreference='SilentlyContinue';Remove-Item {} -Recurse -Force -ErrorAction SilentlyContinue;exit 0",
        ps_quote(&drive)
    );
    let _ = Command::new("powershell")
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle",
            "Hidden",
            "-Command",
            &script,
        ])
        .creation_flags(CREATE_NO_WINDOW)
        .status();
}

/// 删注册表里的单个值（开机启动项）
fn reg_delete_value(key: &str, name: &str) {
    let ok = Command::new("reg")
        .args(["delete", key, "/v", name, "/f"])
        .creation_flags(CREATE_NO_WINDOW)
        .status()
        .map(|s| s.success())
        .unwrap_or(false);
    if ok {
        return;
    }
    let drive = key.replace("HKLM\\", "HKLM:\\").replace("HKCU\\", "HKCU:\\");
    let script = format!(
        "Remove-ItemProperty -Path {} -Name {} -ErrorAction SilentlyContinue;exit 0",
        ps_quote(&drive),
        ps_quote(name)
    );
    let _ = Command::new("powershell")
        .args([
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle",
            "Hidden",
            "-Command",
            &script,
        ])
        .creation_flags(CREATE_NO_WINDOW)
        .status();
}

fn del_file(path: &Path) {
    if path.exists() {
        let _ = fs::remove_file(path);
    }
}

/// 卸载事件上报：把设备码交给 put_uninstall.php 记一笔。
/// Rust 标准库没有 HTTPS，借道 PowerShell 的 Invoke-RestMethod；失败完全静默。
fn report_uninstall() {
    // device.json = {"code": "....", "sources": [...], ...}
    let device_json = env::var("APPDATA").ok().map(|appdata| {
        PathBuf::from(&appdata).join("Baicodex").join("device.json")
    });
    let code = device_json
        .and_then(|path| fs::read_to_string(path).ok())
        .and_then(|text| {
            let marker = "\"code\": \"";
            let alt = "\"code\":\"";
            let start = text.find(marker).map(|i| i + marker.len())
                .or_else(|| text.find(alt).map(|i| i + alt.len()))?;
            let rest = &text[start..];
            let end = rest.find('"')?;
            let value = &rest[..end];
            (!value.is_empty() && value.len() <= 128).then(|| value.to_string())
        });
    let Some(code) = code else { return };

    // 一次性随机串：时间纳秒 + 进程号，够防碰撞了
    let nanos = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_nanos())
        .unwrap_or(0);
    let nonce = format!("{:x}{:x}", nanos, std::process::id());
    let body = format!(
        "{{\"deviceCode\":\"{}\",\"count\":1,\"timestamp\":{},\"nonce\":\"{}\"}}",
        code,
        nanos / 1_000_000_000,
        nonce
    );

    let script = format!(
        "$ProgressPreference='SilentlyContinue';try{{Invoke-RestMethod -Uri 'https://agent.ai.baiccb.cn/api/use/put_uninstall.php' -Method Post -ContentType 'application/json; charset=utf-8' -Body '{}' -TimeoutSec 8}}catch{{}};exit 0",
        body.replace('\'', "''")
    );
    let _ = Command::new("powershell")
        .args(["-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", &script])
        .creation_flags(CREATE_NO_WINDOW)
        .status();
}

/// jpackage 产出的 exe 带只读属性，直接 remove_dir_all 会 PermissionDenied；
/// 先递归清掉只读位再删。句柄刚释放的文件删不掉就退避重试几次。
fn force_remove_dir(path: &Path) -> bool {
    fn clear_readonly(p: &Path) {
        if let Ok(meta) = fs::metadata(p) {
            let mut perms = meta.permissions();
            if perms.readonly() {
                perms.set_readonly(false);
                let _ = fs::set_permissions(p, perms);
            }
            if meta.is_dir() {
                if let Ok(entries) = fs::read_dir(p) {
                    for entry in entries.flatten() {
                        clear_readonly(&entry.path());
                    }
                }
            }
        }
    }
    for _ in 0..3 {
        clear_readonly(path);
        if fs::remove_dir_all(path).is_ok() {
            return true;
        }
        std::thread::sleep(std::time::Duration::from_millis(900));
    }
    false
}

/// 卸载入口：真正的删除交给「复制到 TEMP 的副本」做，
/// 否则进程占着自己的 exe，安装目录永远删不干净。
fn uninstall(exe: &Path) {
    let args: Vec<String> = env::args().skip(1).collect();
    let silent = args.iter().any(|a| a == "--silent" || a == "/S");
    let is_worker = args.iter().any(|a| a == "--uninstall-run");

    let arg_dir = args
        .iter()
        .find_map(|a| a.strip_prefix("--install-dir="))
        .map(PathBuf::from);
    let dir = arg_dir.unwrap_or_else(|| {
        exe.parent()
            .map(|p| p.to_path_buf())
            .unwrap_or_else(|| PathBuf::from("."))
    });

    if !is_worker {
        let answer = if silent {
            IDYES
        } else {
            msgbox(
                &format!(
                    "要从这台电脑上卸载 Baicodex 吗？\n\n安装位置：{}\n\n\
                     「是」= 卸载程序，保留会话与设置\n\
                     「否」= 卸载程序并删除全部会话、设置与工作区\n\
                     「取消」= 什么都不做",
                    dir.display()
                ),
                "卸载 Baicodex",
                MB_YESNOCANCEL | MB_ICONQUESTION,
            )
        };
        if answer != IDYES && answer != IDNO {
            return;
        }

        // 复制一份到 TEMP 再跑：副本不在安装目录里，可以直接整树删除
        let worker = env::temp_dir().join("baicodex-uninstall-worker.exe");
        let _ = fs::remove_file(&worker);
        let copied = fs::copy(exe, &worker).is_ok();
        let target = if copied { worker.as_path() } else { exe };

        let mut command = Command::new(target);
        command
            .arg("--uninstall")
            .arg("--uninstall-run")
            .arg(format!("--install-dir={}", dir.display()));
        if answer == IDNO {
            command.arg("--purge-data");
        }
        if silent {
            command.arg("--silent");
        }
        let _ = command.creation_flags(CREATE_NO_WINDOW).spawn();
        return;
    }

    // ---- 副本：真正干活
    // 上报卸载事件要在删数据之前：device.json 一删就找不到设备码了
    report_uninstall();

    // 主程序可能还在跑（用户直接删不放它），先补一刀再删目录
    let _ = Command::new("taskkill")
        .args(["/F", "/IM", "Baicodex.exe"])
        .creation_flags(CREATE_NO_WINDOW)
        .status();
    std::thread::sleep(std::time::Duration::from_millis(600));

    reg_delete(r"HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Baicodex");
    reg_delete(r"HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\Baicodex");
    reg_delete(r"HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\Baicodex.exe");
    reg_delete(r"HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\App Paths\Baicodex.exe");
    reg_delete_value(r"HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\Run", "Baicodex");
    reg_delete_value(r"HKCU\SOFTWARE\Microsoft\Windows\CurrentVersion\Run", "Baicodex");

    let public_desktop = env::var("PUBLIC").unwrap_or_else(|_| "C:\\Users\\Public".into());
    del_file(&PathBuf::from(&public_desktop).join("Desktop").join("Baicodex.lnk"));
    if let Ok(profile) = env::var("USERPROFILE") {
        del_file(&PathBuf::from(&profile).join("Desktop").join("Baicodex.lnk"));
    }
    let program_data = env::var("ProgramData").unwrap_or_else(|_| "C:\\ProgramData".into());
    let _ = fs::remove_dir_all(
        PathBuf::from(&program_data).join("Microsoft\\Windows\\Start Menu\\Programs\\Baicodex"),
    );
    if let Ok(appdata) = env::var("APPDATA") {
        let _ = fs::remove_dir_all(
            PathBuf::from(&appdata).join("Microsoft\\Windows\\Start Menu\\Programs\\Baicodex"),
        );
    }

    if args.iter().any(|a| a == "--purge-data") {
        if let Ok(appdata) = env::var("APPDATA") {
            let _ = fs::remove_dir_all(PathBuf::from(&appdata).join("Baicodex"));
        }
        if let Ok(local) = env::var("LOCALAPPDATA") {
            let _ = fs::remove_dir_all(PathBuf::from(&local).join("Baicodex"));
        }
    }

    // 安装目录整树删除；仍失败就让 cmd 隔 5 秒补一刀
    if !force_remove_dir(&dir) {
        let script = format!("ping -n 6 127.0.0.1 >nul&rd /s /q \"{}\"", dir.display());
        let mut fallback = Command::new("cmd");
        fallback.raw_arg("/c");
        fallback.raw_arg(script);
        let _ = fallback.creation_flags(CREATE_NO_WINDOW).spawn();
    }

    if !silent {
        msgbox("Baicodex 已卸载。", "卸载完成", MB_ICONINFORMATION);
    }
}

fn main() {
    let exe = env::current_exe().unwrap_or_else(|_| PathBuf::from("Baicodex-Setup.exe"));
    let args: Vec<String> = env::args().skip(1).collect();

    if args.iter().any(|a| a == "--uninstall") {
        uninstall(&exe);
        return;
    }

    match run_setup(&exe) {
        Ok(code) => {
            if code != 0 {
                std::process::exit(code);
            }
        }
        Err(message) => {
            msgbox(&message, "Baicodex 安装", MB_ICONERROR);
            std::process::exit(1);
        }
    }
}
