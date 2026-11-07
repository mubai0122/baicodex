//! 文件工具：读 / 写 / 局部改 / 建目录 / 删 / 列 / 信息 / 移动 / 压缩 / 内容搜索。
//!
//! 这里是"零宽通道"真正生效的地方：普通身份下访问 Android/data 时，
//! [Router] 会给出一条实测能过的路径变换，读写都走它。
//!
//! **每一次读写都要过沙箱**。路由负责"能不能到达"，沙箱负责"该不该去"，
//! 两者缺一不可：零宽能让你读到 /data/data，但沙箱不应该允许模型去读别的应用的私有数据。
//!
//! 路由与沙箱的顺序：先用**逻辑路径**做沙箱判断（用户看到的路径），再变形去访问。
//! 反过来先变形会导致"沙箱看到带零宽字符的路径，匹配不上规则"。

use std::io::{Read, Write};
use std::path::{Path, PathBuf};

use regex::Regex;
use sha2::Digest;
use walkdir::WalkDir;

use crate::core::security::route::Router;
use crate::core::feature::model::session::Route;
use crate::core::security::sandbox::Sandbox;

#[derive(Debug, Clone, serde::Serialize)]
pub struct Out {
    pub ok: bool,
    pub text: String,
    pub route: Route,
}

impl Out {
    fn ok(text: impl Into<String>, route: Route) -> Self {
        Self { ok: true, text: text.into(), route }
    }
    fn err(text: impl Into<String>, route: Route) -> Self {
        Self { ok: false, text: text.into(), route }
    }
}

/// 读文件的上限：超过就只给头尾，别把窗口一次打爆。
/// 默认读取上限（字节）。实际值来自配置 `tool.max_read_bytes`。
const READ_MAX: usize = 400_000;
/// grep 单文件上限。
const GREP_MAX_BYTES: u64 = 2 * 1024 * 1024;

#[derive(Debug)]
pub struct FileOps<'a> {
    pub router: &'a Router,
    pub sandbox: Sandbox,
    /// 单次读取上限（字节）。来自配置，默认 2MB。
    pub max_read: usize,
}

impl<'a> FileOps<'a> {
    pub fn new(router: &'a Router, sandbox: Sandbox, max_read: usize) -> Self {
        Self { router, sandbox, max_read: max_read.max(64 * 1024) }
    }

    /// 规整路径 + 读权限检查 + 零宽变形。
    fn resolve(&self, raw: &str) -> Result<(PathBuf, String), Out> {
        let norm = self.router.normalize(raw);
        let v = self.sandbox.check_real(&norm, false);
        if !v.allowed {
            return Err(Out::err(format!("拒绝访问：{}", v.reason), self.router.route(&norm)));
        }
        let mapped = self.router.map(&norm);
        Ok((PathBuf::from(mapped), norm))
    }

    /// 写权限检查。写操作在 resolve 之后必须再过一次。
    fn need_write(&self, norm: &str) -> Option<Out> {
        let v = self.sandbox.check_real(norm, true);
        if !v.allowed {
            return Some(Out::err(format!("拒绝写入：{}", v.reason), self.router.route(norm)));
        }
        None
    }

    /// 批量路径（zip 的输入）逐个检查读权限。
    fn resolve_list(&self, raw: &str) -> Result<Vec<(PathBuf, String)>, Out> {
        let mut out = Vec::new();
        for part in raw.split([',', '\n']).map(|x| x.trim()).filter(|x| !x.is_empty()) {
            out.push(self.resolve(part)?);
        }
        if out.is_empty() {
            return Err(Out::err("没有给出任何路径", self.router.route(raw)));
        }
        Ok(out)
    }

    pub fn read(&self, raw: &str) -> Out {
        let (p, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        let route = self.router.route(&norm);
        if p.is_dir() {
            return self.list(raw, false, 200);
        }
        match std::fs::read(&p) {
            Ok(bytes) => {
                if bytes.iter().take(4096).any(|b| *b == 0) {
                    return Out::err(
                        format!("这是二进制文件（{} 字节），用 exec_shell 配合 file/xxd 看", bytes.len()),
                        route,
                    );
                }
                let mut text = String::from_utf8_lossy(&bytes).to_string();
                // 字符上限按"最坏情况 3 字节一个字"折算，保证转成字符后不会比字节上限大太多。
                let cap = (self.max_read / 3).max(READ_MAX / 4);
                if text.chars().count() > cap {
                    let head: String = text.chars().take(cap).collect();
                    let tail: String = text.chars().rev().take(2000).collect::<String>().chars().rev().collect();
                    let total = text.chars().count();
                    text = format!("{head}\n\n…（已截断，原文 {total} 字符，只给了前 {cap}）…\n\n{tail}");
                }
                let lines = text.lines().count();
                Out::ok(format!("{text}\n\n（{lines} 行，{} 字节）", bytes.len()), route)
            }
            Err(e) => Out::err(format!("读取失败：{e}"), route),
        }
    }

    pub fn write(&self, raw: &str, content: &str) -> Out {
        let (p, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        if let Some(e) = self.need_write(&norm) { return e; }
        let route = self.router.route(&norm);
        if let Some(dir) = p.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        // 先写临时文件再改名：写一半被杀不会留下半个文件。
        let tmp = p.with_extension("baicode.tmp");
        let res = (|| -> std::io::Result<()> {
            let mut f = std::fs::File::create(&tmp)?;
            f.write_all(content.as_bytes())?;
            f.sync_all()?;
            std::fs::rename(&tmp, &p)
        })();
        match res {
            Ok(_) => Out::ok(
                format!("已写入 {}（{} 字节）", norm, content.len()),
                route,
            ),
            Err(e) => {
                let _ = std::fs::remove_file(&tmp);
                Out::err(format!("写入失败：{e}"), route)
            }
        }
    }

    /// 局部修改：`old` 按行匹配、缩进与行尾空白忽略，其余字符必须一致；
    /// 命中多处报错（要求带更多上下文）；`new` 的缩进按原位置的缩进自动对齐。
    pub fn edit(&self, raw: &str, old: &str, new: &str) -> Out {
        let (p, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        if let Some(e) = self.need_write(&norm) { return e; }
        let route = self.router.route(&norm);
        let raw_text = match std::fs::read_to_string(&p) {
            Ok(t) => t,
            Err(e) => return Out::err(format!("读取失败：{e}"), route),
        };
        if old.trim().is_empty() {
            return Out::err("old 为空：整篇重写请用 write_file", route);
        }

        let crlf = raw_text.contains("\r\n");
        let lines: Vec<&str> = raw_text.split('\n').map(|l| l.strip_suffix('\r').unwrap_or(l)).collect();

        // old 两端可能带空行：去掉首尾空行，中间的空行保留（它也是上下文的一部分）。
        let mut old_lines: Vec<String> = old.split('\n').map(|l| l.trim().to_string()).collect();
        while old_lines.first().map(|s| s.is_empty()).unwrap_or(false) {
            old_lines.remove(0);
        }
        while old_lines.last().map(|s| s.is_empty()).unwrap_or(false) {
            old_lines.pop();
        }
        if old_lines.is_empty() {
            return Out::err("old 里没有有效内容", route);
        }

        let norm_line = |s: &str| s.trim().to_string();
        let mut hits: Vec<usize> = Vec::new();
        if old_lines.len() <= lines.len() {
            for i in 0..=lines.len() - old_lines.len() {
                if (0..old_lines.len()).all(|k| norm_line(lines[i + k]) == old_lines[k]) {
                    hits.push(i);
                }
            }
        }
        match hits.len() {
            0 => return Out::err("没找到 old 这段原文：请先 read_file 确认当前内容", route),
            1 => {}
            n => {
                return Out::err(
                    format!("old 命中 {n} 处，改成唯一的一段（多带几行上下文）再试"),
                    route,
                )
            }
        }

        let at = hits[0];
        let old_indent = indent_of(lines[at]);
        let new_lines: Vec<&str> = new.split('\n').map(|l| l.trim_end_matches('\r')).collect();
        let new_indent = new_lines.first().map(|l| indent_of(l)).unwrap_or(0);
        let fixed: Vec<String> = new_lines
            .iter()
            .map(|l| {
                if l.trim().is_empty() {
                    String::new()
                } else {
                    let own = indent_of(l);
                    let body = l.trim_start();
                    format!("{}{}", " ".repeat((old_indent + own).saturating_sub(new_indent)), body)
                }
            })
            .collect();

        let mut out: Vec<String> = Vec::new();
        out.extend(lines[..at].iter().map(|s| s.to_string()));
        out.extend(fixed);
        out.extend(lines[at + old_lines.len()..].iter().map(|s| s.to_string()));
        let mut text = out.join(if crlf { "\r\n" } else { "\n" });
        if raw_text.ends_with('\n') && !text.ends_with('\n') {
            text.push('\n');
        }

        match std::fs::write(&p, text) {
            Ok(_) => Out::ok(
                format!("已修改 {norm}：第 {} 行起替换了 {} 行", at + 1, old_lines.len()),
                route,
            ),
            Err(e) => Out::err(format!("写回失败：{e}"), route),
        }
    }

    pub fn mkdir(&self, raw: &str) -> Out {
        let (p, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        if let Some(e) = self.need_write(&norm) { return e; }
        let route = self.router.route(&norm);
        match std::fs::create_dir_all(&p) {
            Ok(_) => Out::ok(format!("目录已就绪：{norm}"), route),
            Err(e) => Out::err(format!("建目录失败：{e}"), route),
        }
    }

    pub fn delete(&self, raw: &str) -> Out {
        let (p, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        if let Some(e) = self.need_write(&norm) { return e; }
        let route = self.router.route(&norm);
        if norm == "/" || norm == crate::core::base::dirs::base().to_string_lossy() {
            return Out::err("拒绝删除根目录", route);
        }
        let res = if p.is_dir() {
            std::fs::remove_dir_all(&p)
        } else {
            std::fs::remove_file(&p)
        };
        match res {
            Ok(_) => Out::ok(format!("已删除 {norm}"), route),
            Err(e) => Out::err(format!("删除失败：{e}"), route),
        }
    }

    pub fn list(&self, raw: &str, recurse: bool, limit: usize) -> Out {
        let (p, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        let route = self.router.route(&norm);
        if !p.exists() {
            return Out::err(format!("路径不存在：{norm}"), route);
        }
        let mut rows: Vec<String> = Vec::new();
        let limit = limit.clamp(1, 2000);

        if recurse {
            for e in WalkDir::new(&p).max_depth(6).into_iter().flatten() {
                if rows.len() >= limit {
                    rows.push(format!("…（只列前 {limit} 项）"));
                    break;
                }
                if e.path() == p {
                    continue;
                }
                let rel = e.path().strip_prefix(&p).unwrap_or(e.path());
                rows.push(fmt_entry(e.path(), &rel.to_string_lossy(), e.file_type().is_dir()));
            }
        } else {
            let mut items: Vec<_> = match std::fs::read_dir(&p) {
                Ok(rd) => rd.flatten().collect(),
                Err(e) => return Out::err(format!("列目录失败：{e}"), route),
            };
            items.sort_by_key(|e| e.file_name());
            for e in items.into_iter().take(limit) {
                let name = e.file_name().to_string_lossy().to_string();
                rows.push(fmt_entry(&e.path(), &name, e.path().is_dir()));
            }
        }
        if rows.is_empty() {
            return Out::ok(format!("{norm} 是空目录"), route);
        }
        Out::ok(format!("{norm}：\n{}", rows.join("\n")), route)
    }

    pub fn info(&self, raw: &str) -> Out {
        let (p, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        let route = self.router.route(&norm);
        let md = match std::fs::metadata(&p) {
            Ok(m) => m,
            Err(e) => return Out::err(format!("取信息失败：{e}"), route),
        };
        let mut s = format!("路径：{norm}\n类型：{}", if md.is_dir() { "目录" } else { "文件" });
        s.push_str(&format!("\n体积：{}（{} 字节）", human(md.len()), md.len()));
        if let Ok(t) = md.modified() {
            let dt: chrono::DateTime<chrono::Local> = t.into();
            s.push_str(&format!("\n修改时间：{}", dt.format("%Y-%m-%d %H:%M:%S")));
        }
        if md.is_file() && md.len() <= 20 * 1024 * 1024 {
            if let Ok(bytes) = std::fs::read(&p) {
                let md5 = format!("{:x}", md5::Md5::digest(&bytes));
                let sha = format!("{:x}", sha2::Sha256::digest(&bytes));
                s.push_str(&format!("\nMD5：{md5}\nSHA256：{sha}"));
                let kind = crate::core::feature::image::sniff(&bytes);
                if kind != crate::core::feature::image::Kind::Unknown {
                    s.push_str(&format!("\n类型：{}", kind.mime()));
                }
            }
        }
        Out::ok(s, route)
    }

    pub fn mv(&self, from: &str, to: &str, copy: bool) -> Out {
        let (src, snorm) = match self.resolve(from) { Ok(v) => v, Err(e) => return e };
        let (dst, dnorm) = match self.resolve(to) { Ok(v) => v, Err(e) => return e };
        if let Some(e) = self.need_write(&dnorm) { return e; }
        if !copy {
            if let Some(e) = self.need_write(&snorm) { return e; }
        }
        let route = self.router.route(&snorm);
        if let Some(d) = dst.parent() {
            let _ = std::fs::create_dir_all(d);
        }
        let res = if copy {
            if src.is_dir() {
                copy_dir(&src, &dst)
            } else {
                std::fs::copy(&src, &dst).map(|_| ())
            }
        } else {
            std::fs::rename(&src, &dst).or_else(|_| {
                // 跨分区改名会失败，退化成复制 + 删。
                if src.is_dir() {
                    copy_dir(&src, &dst).and_then(|_| std::fs::remove_dir_all(&src))
                } else {
                    std::fs::copy(&src, &dst).and_then(|_| std::fs::remove_file(&src)).map(|_| ())
                }
            })
        };
        match res {
            Ok(_) => Out::ok(format!("{}：{snorm} → {dnorm}", if copy { "已复制" } else { "已移动" }), route),
            Err(e) => Out::err(format!("失败：{e}"), route),
        }
    }

    pub fn zip(&self, paths: &str, out: &str) -> Out {
        let (outp, onorm) = match self.resolve(out) { Ok(v) => v, Err(e) => return e };
        if let Some(e) = self.need_write(&onorm) { return e; }
        let route = self.router.route(&onorm);
        let file = match std::fs::File::create(&outp) {
            Ok(f) => f,
            Err(e) => return Out::err(format!("建压缩包失败：{e}"), route),
        };
        let mut zw = zip::ZipWriter::new(file);
        let opts = zip::write::SimpleFileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated)
            .unix_permissions(0o644);
        let mut n = 0usize;
        // 批量路径统一走 resolve_list：逗号与换行都认，权限也只查一遍。
        let list = match self.resolve_list(paths) {
            Ok(v) => v,
            Err(e) => return e,
        };
        for (p, _) in list {
            if p.is_dir() {
                for e in WalkDir::new(&p).into_iter().flatten().filter(|e| e.file_type().is_file()) {
                    let rel = e.path().strip_prefix(&p).unwrap_or(e.path());
                    let name = format!("{}/{}", p.file_name().unwrap_or_default().to_string_lossy(), rel.display());
                    if zw.start_file(name, opts).is_ok() {
                        if let Ok(mut f) = std::fs::File::open(e.path()) {
                            let mut buf = Vec::new();
                            let _ = f.read_to_end(&mut buf);
                            let _ = zw.write_all(&buf);
                            n += 1;
                        }
                    }
                }
            } else if p.is_file()
                && zw.start_file(p.file_name().unwrap_or_default().to_string_lossy(), opts).is_ok() {
                    if let Ok(mut f) = std::fs::File::open(&p) {
                        let mut buf = Vec::new();
                        let _ = f.read_to_end(&mut buf);
                        let _ = zw.write_all(&buf);
                        n += 1;
                    }
                }
        }
        match zw.finish() {
            Ok(_) => Out::ok(format!("已打包 {n} 个文件到 {onorm}"), route),
            Err(e) => Out::err(format!("收尾失败：{e}"), route),
        }
    }

    pub fn unzip(&self, zip_path: &str, to: &str) -> Out {
        let (zp, znorm) = match self.resolve(zip_path) { Ok(v) => v, Err(e) => return e };
        let (dst, dnorm) = match self.resolve(to) { Ok(v) => v, Err(e) => return e };
        if let Some(e) = self.need_write(&dnorm) { return e; }
        let route = self.router.route(&znorm);
        let f = match std::fs::File::open(&zp) {
            Ok(f) => f,
            Err(e) => return Out::err(format!("打开压缩包失败：{e}"), route),
        };
        let mut ar = match zip::ZipArchive::new(f) {
            Ok(a) => a,
            Err(e) => return Out::err(format!("不是合法 zip：{e}"), route),
        };
        let _ = std::fs::create_dir_all(&dst);
        let mut n = 0usize;
        for i in 0..ar.len() {
            let mut entry = match ar.by_index(i) {
                Ok(e) => e,
                Err(_) => continue,
            };
            // 防目录穿越：交给 safe_join 统一判断（绝对路径、`..`、符号链接都挡）。
            // 手写 `contains("..")` 会误杀 `a..b.txt` 这种正常文件名。
            let name = entry.name().to_string();
            let Some(target) = crate::core::base::path::safe_join(&dst, &name) else {
                continue;
            };
            if entry.is_dir() {
                let _ = std::fs::create_dir_all(&target);
                continue;
            }
            if let Some(p) = target.parent() {
                let _ = std::fs::create_dir_all(p);
            }
            if let Ok(mut out) = std::fs::File::create(&target) {
                let mut buf = Vec::new();
                if entry.read_to_end(&mut buf).is_ok() && out.write_all(&buf).is_ok() {
                    n += 1;
                }
            }
        }
        Out::ok(format!("已解压 {n} 个文件到 {dnorm}"), route)
    }

    pub fn grep(&self, raw: &str, pattern: &str, ext: &str, max: usize, ignore_case: bool) -> Out {
        let (root, norm) = match self.resolve(raw) { Ok(v) => v, Err(e) => return e };
        let route = self.router.route(&norm);
        let re = match regex::RegexBuilder::new(pattern).case_insensitive(ignore_case).build() {
            Ok(r) => r,
            Err(e) => return Out::err(format!("正则不合法：{e}"), route),
        };
        let exts: Vec<String> = ext
            .split(',')
            .map(|s| s.trim().trim_start_matches('.').to_ascii_lowercase())
            .filter(|s| !s.is_empty())
            .collect();
        let max = max.clamp(1, 500);
        let mut hits: Vec<String> = Vec::new();

        let files: Vec<PathBuf> = if root.is_file() {
            vec![root.clone()]
        } else {
            WalkDir::new(&root)
                .max_depth(12)
                .into_iter()
                .flatten()
                .filter(|e| e.file_type().is_file())
                .map(|e| e.path().to_path_buf())
                .collect()
        };

        for f in files {
            if hits.len() >= max {
                break;
            }
            if !exts.is_empty() {
                let e = f.extension().and_then(|s| s.to_str()).unwrap_or("").to_ascii_lowercase();
                if !exts.contains(&e) {
                    continue;
                }
            }
            if std::fs::metadata(&f).map(|m| m.len() > GREP_MAX_BYTES).unwrap_or(true) {
                continue;
            }
            let Ok(bytes) = std::fs::read(&f) else { continue };
            if bytes.iter().take(8192).any(|b| *b == 0) {
                continue;
            }
            let text = String::from_utf8_lossy(&bytes);
            for (i, line) in text.lines().enumerate() {
                if re.is_match(line) {
                    let rel = f.strip_prefix(&root).unwrap_or(&f);
                    hits.push(format!("{}:{}: {}", rel.display(), i + 1, line.trim()));
                    if hits.len() >= max {
                        break;
                    }
                }
            }
        }

        if hits.is_empty() {
            Out::ok(format!("没有匹配「{pattern}」的内容"), route)
        } else {
            Out::ok(format!("命中 {} 条：\n{}", hits.len(), hits.join("\n")), route)
        }
    }
}

fn indent_of(s: &str) -> usize {
    s.chars().take_while(|c| *c == ' ' || *c == '\t').count()
}

fn human(n: u64) -> String {
    if n >= 1 << 20 {
        format!("{:.1}M", n as f64 / 1048576.0)
    } else if n >= 1 << 10 {
        format!("{}K", n / 1024)
    } else {
        format!("{n}B")
    }
}

fn fmt_entry(p: &Path, name: &str, dir: bool) -> String {
    let size = std::fs::metadata(p).map(|m| m.len()).unwrap_or(0);
    let mtime = std::fs::metadata(p)
        .and_then(|m| m.modified())
        .ok()
        .map(|t| {
            let dt: chrono::DateTime<chrono::Local> = t.into();
            dt.format("%m-%d %H:%M").to_string()
        })
        .unwrap_or_else(|| "-".into());
    if dir {
        format!("{name}/  {mtime}")
    } else {
        format!("{name}  {}  {mtime}", human(size))
    }
}

fn copy_dir(src: &Path, dst: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(dst)?;
    for e in std::fs::read_dir(src)? {
        let e = e?;
        let to = dst.join(e.file_name());
        if e.path().is_dir() {
            copy_dir(&e.path(), &to)?;
        } else {
            std::fs::copy(e.path(), &to)?;
        }
    }
    Ok(())
}

/// 计划模式下判断一条命令是否会落盘：返回命中的片段。
pub fn plan_write_hit(command: &str) -> Option<String> {
    const WRITE_WORDS: &[&str] = &[
        "rm ", "mv ", "cp ", "touch ", "mkdir ", "rmdir ", "tee ", "chmod ", "chown ", "ln ",
        "sed -i", "truncate ", "dd ", "git commit", "git checkout", "git reset", "git add",
    ];
    if let Some(w) = WRITE_WORDS.iter().find(|w| command.contains(**w)) {
        return Some((*w).to_string());
    }
    // 重定向只拦真正落盘的目标，/dev/null 与 2>&1 放过。
    if let Ok(re) = Regex::new(r"\d?>>?\s*([^\s;|&]+)") {
        for c in re.captures_iter(command) {
            let target = c.get(1).map(|m| m.as_str()).unwrap_or("");
            if !matches!(target, "/dev/null" | "&1" | "&2") {
                return Some(target.to_string());
            }
        }
    }
    None
}
