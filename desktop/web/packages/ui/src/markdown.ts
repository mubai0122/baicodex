/**
 * Markdown 渲染。
 *
 * 手写而不是引 `marked` + `dompurify`：那套组合 70KB 起步，而这里要支持的语法
 * 只有模型真正会用的那几种。更要紧的是**安全边界要自己捏在手里**——模型输出是
 * 不可信文本，任何"先转 HTML 再消毒"的链路都有一个版本漏洞的距离。
 *
 * 这里的做法是：**先转义，再拼标签**。所有用户可控的字符在进 DOM 之前就已经是
 * `&lt;` 了，不存在注入面。
 *
 * 支持：标题、段落、粗体/斜体/删除线、行内代码、代码块（带语言与复制）、
 * 有序/无序列表、引用、表格、分隔线、链接（只允许 http/https/mailto）。
 */

import { icon } from "./icon";

const ESC: Record<string, string> = {
  "&": "&amp;",
  "<": "&lt;",
  ">": "&gt;",
  '"': "&quot;",
  "'": "&#39;",
};

export function escapeHtml(s: string): string {
  return s.replace(/[&<>"']/g, (c) => ESC[c] ?? c);
}

/** 只放行这几种协议。`javascript:` 一律变成不可点的文本。 */
function safeHref(raw: string): string | null {
  const u = raw.trim();
  if (/^(https?:|mailto:)/i.test(u)) return u;
  return null;
}

// —— 语法着色 ——

interface Lang {
  kw: Set<string>;
  hashComment: boolean;
  tripleQuote?: boolean;
}

const COMMON = [
  "if", "else", "for", "while", "return", "break", "continue", "in", "of", "new", "class",
  "function", "const", "let", "var", "true", "false", "null", "undefined", "this", "try",
  "catch", "finally", "throw", "switch", "case", "default", "do", "typeof", "instanceof",
];

const LANGS: Record<string, Lang> = {
  rust: {
    kw: new Set([...COMMON, "fn", "mut", "pub", "use", "impl", "struct", "enum", "match", "loop", "self", "Self", "crate", "mod", "trait", "where", "async", "await", "move", "ref", "dyn", "static", "type", "unsafe", "extern", "as", "Some", "None", "Ok", "Err", "String", "Vec", "Option", "Result"]),
    hashComment: false,
  },
  ts: {
    kw: new Set([...COMMON, "interface", "type", "enum", "implements", "extends", "public", "private", "protected", "readonly", "as", "satisfies", "declare", "namespace", "abstract", "override", "string", "number", "boolean", "void", "any", "unknown", "never"]),
    hashComment: false,
  },
  js: { kw: new Set([...COMMON, "export", "import", "from", "async", "await", "yield"]), hashComment: false },
  python: {
    kw: new Set([...COMMON, "def", "elif", "lambda", "pass", "raise", "with", "as", "yield", "global", "nonlocal", "assert", "del", "is", "not", "and", "or", "None", "True", "False", "self", "import", "from"]),
    hashComment: true,
  },
  kotlin: {
    kw: new Set([...COMMON, "fun", "val", "var", "when", "object", "companion", "data", "sealed", "suspend", "inline", "reified", "lateinit", "override", "open", "internal", "private", "package", "import"]),
    hashComment: false,
  },
  bash: { kw: new Set(["if", "then", "else", "fi", "for", "do", "done", "while", "case", "esac", "function", "local", "export", "return", "echo", "cd", "set"]), hashComment: true },
  json: { kw: new Set(["true", "false", "null"]), hashComment: false },
  sh: { kw: new Set(["if", "then", "else", "fi", "for", "do", "done", "export"]), hashComment: true },
};

const TOKEN = /(\/\/[^\n]*|\/\*[\s\S]*?\*\/|#[^\n]*)|("(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|`(?:[^`\\]|\\.)*`)|(\b\d[\d_]*(?:\.\d+)?\b)|([A-Za-z_$][\w$]*)/g;

/** 给一段代码上色。返回的 HTML 已经转义过。 */
export function highlight(code: string, lang: string): string {
  const l = LANGS[lang.toLowerCase()] ?? LANGS.ts!;
  let out = "";
  let last = 0;
  TOKEN.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = TOKEN.exec(code))) {
    out += escapeHtml(code.slice(last, m.index));
    const [text, comment, str, num, ident] = m;
    if (comment) {
      if (l.hashComment || !text.startsWith("#")) out += `<span class="tok-com">${escapeHtml(text)}</span>`;
      else out += escapeHtml(text);
    } else if (str) {
      out += `<span class="tok-str">${escapeHtml(text)}</span>`;
    } else if (num) {
      out += `<span class="tok-num">${escapeHtml(text)}</span>`;
    } else if (ident) {
      if (l.kw.has(text)) out += `<span class="tok-kw">${escapeHtml(text)}</span>`;
      else if (/^[A-Z]/.test(text)) out += `<span class="tok-ty">${escapeHtml(text)}</span>`;
      else if (code[m.index + text.length] === "(") out += `<span class="tok-fn">${escapeHtml(text)}</span>`;
      else out += escapeHtml(text);
    } else {
      out += escapeHtml(text);
    }
    last = m.index + text.length;
  }
  out += escapeHtml(code.slice(last));
  return out;
}

// —— 行内 ——

function inline(src: string): string {
  const codes: string[] = [];
  // 先把行内代码抠出来，免得里面的 * _ 被当成标记
  let s = src.replace(/`([^`\n]+)`/g, (_, c: string) => {
    codes.push(c);
    return `\u0000${codes.length - 1}\u0000`;
  });

  s = escapeHtml(s);

  // 链接：[文字](url)
  s = s.replace(/\[([^\]\n]+)\]\(([^)\s]+)\)/g, (whole, text: string, url: string) => {
    const href = safeHref(url);
    if (!href) return text;
    return `<a href="${escapeHtml(href)}" target="_blank" rel="noreferrer noopener">${text}</a>`;
  });

  // 粗斜体组合要按顺序来，否则 ** 会被 * 抢先吃掉
  s = s.replace(/\*\*\*([^*\n]+)\*\*\*/g, "<strong><em>$1</em></strong>");
  s = s.replace(/\*\*([^*\n]+)\*\*/g, "<strong>$1</strong>");
  s = s.replace(/(^|\W)\*([^*\n]+)\*/g, "$1<em>$2</em>");
  s = s.replace(/~~([^~\n]+)~~/g, "<del>$1</del>");
  s = s.replace(/(^|\W)_([^_\n]+)_(?=\W|$)/g, "$1<em>$2</em>");

  // 还原行内代码
  s = s.replace(/\u0000(\d+)\u0000/g, (_, i: string) => `<code>${highlight(codes[Number(i)] ?? "", "")}</code>`);
  return s;
}

// —— 块级 ——

interface Block {
  type: "p" | "h" | "ul" | "ol" | "quote" | "code" | "hr" | "table";
  level?: number;
  lang?: string;
  lines: string[];
  rows?: string[][];
}

function parseBlocks(src: string): Block[] {
  const lines = src.replace(/\r\n?/g, "\n").split("\n");
  const out: Block[] = [];
  let i = 0;

  const flushPara = (buf: string[]) => {
    if (buf.length) out.push({ type: "p", lines: buf });
  };
  let para: string[] = [];

  while (i < lines.length) {
    const line = lines[i]!;

    // 代码块
    const fence = /^```(\w*)\s*$/.exec(line);
    if (fence) {
      flushPara(para);
      para = [];
      const body: string[] = [];
      i++;
      while (i < lines.length && !/^```/.test(lines[i]!)) {
        body.push(lines[i]!);
        i++;
      }
      i++; // 跳过结束的 ```
      out.push({ type: "code", lang: fence[1] || "", lines: body });
      continue;
    }

    // 标题
    const h = /^(#{1,6})\s+(.*)$/.exec(line);
    if (h) {
      flushPara(para);
      para = [];
      out.push({ type: "h", level: h[1]!.length, lines: [h[2]!] });
      i++;
      continue;
    }

    // 分隔线
    if (/^\s*([-*_])\s*\1\s*\1[\s\S]*$/.test(line) && line.trim().length >= 3) {
      flushPara(para);
      para = [];
      out.push({ type: "hr", lines: [] });
      i++;
      continue;
    }

    // 引用
    if (/^>\s?/.test(line)) {
      flushPara(para);
      para = [];
      const body: string[] = [];
      while (i < lines.length && /^>\s?/.test(lines[i]!)) {
        body.push(lines[i]!.replace(/^>\s?/, ""));
        i++;
      }
      out.push({ type: "quote", lines: body });
      continue;
    }

    // 表格：表头 + 分隔行
    if (/\|/.test(line) && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|[\s:|-]*$/.test(lines[i + 1]!)) {
      flushPara(para);
      para = [];
      const rows: string[][] = [];
      const split = (s: string) =>
        s.replace(/^\s*\|/, "").replace(/\|\s*$/, "").split("|").map((c) => c.trim());
      rows.push(split(line));
      i += 2;
      while (i < lines.length && /\|/.test(lines[i]!) && lines[i]!.trim()) {
        rows.push(split(lines[i]!));
        i++;
      }
      out.push({ type: "table", lines: [], rows });
      continue;
    }

    // 列表
    if (/^\s*([-*+]|\d+[.)])\s+/.test(line)) {
      flushPara(para);
      para = [];
      const ordered = /^\s*\d+[.)]\s+/.test(line);
      const items: string[] = [];
      let cur = "";
      let lastIndent = 0;
      while (i < lines.length) {
        const l = lines[i]!;
        const m = /^(\s*)([-*+]|\d+[.)])\s+(.*)$/.exec(l);
        if (m) {
          if (cur) items.push(cur.trim());
          cur = m[3]!;
          lastIndent = m[1]!.length;
          i++;
          continue;
        }
        // 续行：缩进比标记深，或者空行后面还跟着内容
        if (cur && l.trim() && /^\s{2,}/.test(l)) {
          cur += " " + l.trim();
          i++;
          continue;
        }
        if (cur && !l.trim()) {
          // 空行：可能是列表结束，也可能是松散列表的分隔
          const nxt = lines[i + 1];
          if (nxt && /^\s*([-*+]|\d+[.)])\s+/.test(nxt)) {
            items.push(cur.trim());
            cur = "";
            i++;
            continue;
          }
          break;
        }
        break;
      }
      if (cur) items.push(cur.trim());
      out.push({ type: ordered ? "ol" : "ul", lines: items });
      void lastIndent;
      continue;
    }

    // 空行
    if (!line.trim()) {
      flushPara(para);
      para = [];
      i++;
      continue;
    }

    para.push(line);
    i++;
  }
  flushPara(para);
  return out;
}

function blockHtml(b: Block): string {
  switch (b.type) {
    case "h":
      return `<h${b.level}>${inline(b.lines.join(" "))}</h${b.level}>`;
    case "p":
      return `<p>${inline(b.lines.join("\n")).replace(/\n/g, "<br>")}</p>`;
    case "hr":
      return "<hr>";
    case "quote":
      return `<blockquote>${b.lines.map((l) => inline(l)).join("<br>")}</blockquote>`;
    case "ul":
      return `<ul>${b.lines.map((l) => `<li>${inline(l)}</li>`).join("")}</ul>`;
    case "ol":
      return `<ol>${b.lines.map((l) => `<li>${inline(l)}</li>`).join("")}</ol>`;
    case "code": {
      const lang = b.lang || "text";
      const code = b.lines.join("\n");
      const body = b.lang ? highlight(code, b.lang) : escapeHtml(code);
      return (
        `<pre data-lang="${escapeHtml(lang)}">` +
        `<div class="ui-code-head"><span>${escapeHtml(lang)}</span>` +
        `<button class="ui-iconbtn" data-size="sm" data-copy="${encodeURIComponent(code)}" title="复制">${copySvg()}</button></div>` +
        `<code>${body}</code></pre>`
      );
    }
    case "table": {
      const rows = b.rows ?? [];
      if (!rows.length) return "";
      const head = rows[0]!.map((c) => `<th>${inline(c)}</th>`).join("");
      const body = rows
        .slice(1)
        .map((r) => `<tr>${r.map((c) => `<td>${inline(c)}</td>`).join("")}</tr>`)
        .join("");
      return `<table><thead><tr>${head}</tr></thead><tbody>${body}</tbody></table>`;
    }
  }
}

function copySvg(): string {
  return icon("copy", 12).outerHTML;
}

/**
 * 渲染成 HTML 字符串。
 *
 * 调用方用 `el.innerHTML = render(md)` 即可。所有文本节点都经过转义，属性值也
 * 经过转义（链接 href 还额外做了协议白名单）。
 */
export function render(src: string): string {
  if (!src.trim()) return "";
  return parseBlocks(src).map(blockHtml).join("");
}

/** 渲染到一个容器里，并接上代码块的复制按钮。 */
export function mount(el: HTMLElement, src: string): void {
  el.classList.add("ui-md");
  el.innerHTML = render(src);
  el.addEventListener("click", (e) => {
    const btn = (e.target as HTMLElement).closest<HTMLElement>("[data-copy]");
    if (!btn) return;
    const text = decodeURIComponent(btn.dataset.copy ?? "");
    void navigator.clipboard?.writeText(text);
    btn.dataset.copied = "1";
    setTimeout(() => delete btn.dataset.copied, 1200);
  });
}
