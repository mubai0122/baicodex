---
name: gh
description: 查 GitHub 仓库信息、issue 列表、PR 列表与仓库文件内容，并新建或更新单个文件（走 REST API，不需要本地 git）。要看仓库概况、翻 issue/PR、读仓库里的文件、提交一处文件改动时使用。
---

# GitHub

本目录同时兼容两种读法：`skill.json` 是 BaiAgent 技能包的权威定义（函数模板在
`function/*.js`，属于本项目的 DSL，不是 JS 语法）；这份 `SKILL.md` 是通用 Agent Skills
格式，给 Claude Code / Codex / Cursor 这类运行时读说明用。

在没有 BaiAgent DSL 的环境里，等价做法是用 `gh` CLI 或直接打 REST：

```bash
gh api repos/<owner>/<repo>
gh api repos/<owner>/<repo>/issues?state=open
curl -H "Authorization: Bearer $GITHUB_TOKEN" https://api.github.com/repos/<owner>/<repo>
```

## 函数

| 函数 | 参数 | 作用 |
|---|---|---|
| `gh_repo` | `repo`（owner/name） | 仓库概况：stars、主语言、默认分支、是否私有 |
| `gh_content` | `repo`、`path` | 读文件或目录（目录返回条目列表，文件返回 base64 内容） |
| `gh_issues` | `repo`、`state` | 列 issue，`state` 取 open / closed / all |
| `gh_prs` | `repo`、`state` | 列 PR，`state` 取 open / closed / all |
| `gh_search` | `q` | 搜仓库或代码，`q` 就是搜索框里那句话 |
| `gh_file_new` | `repo`、`path`、`msg`、`content` | 新建文件，`content` 必须是 base64 |
| `gh_file_update` | `repo`、`path`、`msg`、`content`、`sha` | 更新文件，`sha` 取自 `gh_content` |

## 注意

- 更新文件必须先拿到当前 `sha`，否则 API 返回 409。
- `content` 一律 base64，换行要编码进去，不要传原始文本。
- 需要 token 时配 `$secret.github_token`（BaiAgent 侧）或环境变量 `GITHUB_TOKEN`。
