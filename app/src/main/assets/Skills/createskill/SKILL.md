---
name: createskill
description: 按固定格式新建一个技能包骨架（skill.json、README.md、SKILL.md、function 模板）。用户说「帮我做一个技能包 / 新建技能 / 写个 skill」时使用，先建骨架再往里填内容。
---

# 做技能包

一个技能包是一个目录，两种格式可以共存，互不冲突：

```
/storage/emulated/0/baicode/skill/<id>/
├── skill.json            本项目权威定义（函数、参数正则、模板文件名）
├── README.md             本项目 skill_read 返回的正文
├── SKILL.md              通用 Agent Skills 格式（name / description + 正文）
└── function/<fn>.js      每个函数一个模板体（后缀 js 只是沿用，不是 JS 语法）
```

只写 `SKILL.md`、不写 `skill.json` 也能被识别：此时它是一个「只能被读、没有可调用函数」
的技能，id 取目录名，name 与 description 取 frontmatter。

## 通用格式硬要求

- 文件必须叫 `SKILL.md`，放在目录根部。
- 开头必须是 YAML frontmatter，两条必填：`name`（≤64 字符，只允许小写字母、数字、连字符，
  不能首尾是连字符，且与目录名一致）和 `description`（≤1024 字符，写清「做什么」+「何时用」）。
- 其余字段可选：`license`、`compatibility`、`metadata`、`allowed-tools`，不认识的字段会被忽略。
- 正文是 Markdown，建议 500 行以内；长内容拆到 `references/`、`scripts/`、`assets/` 里按需引用。

## 流程

1. 调 `skill_scaffold`，参数 `name` 是包 id（小写字母开头，可带数字、下划线、短横）。
2. 用 `write_file` 写入 `skill.json`、`README.md`、`function/<fn>.js`，需要跨运行时可用就补 `SKILL.md`。
3. 告诉用户「去 设置 → 技能 点重新扫描」，不要假装已经生效。
