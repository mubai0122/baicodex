# GitHub 技能包

全部走 GitHub REST v3，不依赖本机 git，也不进终端。

## 先配密钥

1. 设置 → 技能 → 密钥，新增一条：名字 `gh`，值填 GitHub Personal Access Token（fine-grained，勾选需要的仓库权限即可）。
2. 没有这个密钥时所有函数都会返回「$secret.gh 未配置」。

## 函数

| 函数 | 参数 | 说明 |
|---|---|---|
| gh_repo | repo | 仓库信息，repo 写成 `owner/name` |
| gh_content | repo, path | 读文件或目录；读根目录 path 传 `.`；文件返回 base64 内容，其中 sha 字段是更新文件时用的 |
| gh_issues | repo, state | issue 列表，state 取 open / closed / all |
| gh_prs | repo, state | PR 列表，state 取 open / closed / all |
| gh_search | q | 搜仓库；多个词用 `+` 连接，不要写空格 |
| gh_file_new | repo, path, msg, content | 新建文件，content 必须是 base64 |
| gh_file_update | repo, path, msg, content, sha | 更新已有文件，sha 从 gh_content 结果里取，content 是 base64 |

## base64 怎么来

模型自己算 base64 不靠谱，用 exec_shell 生成：

```
printf '%s' '文件正文' | base64 -w0
```

把输出整串作为 content 传进来。中文按 UTF-8 编码即可。

## 注意

- 更新文件前必须先调 gh_content 拿到 sha，否则会 409。
- 一次只能提交一个文件；多文件请逐个调用。
- 返回体是 GitHub 原始 JSON，字段很多，回答用户时只挑需要的说。
