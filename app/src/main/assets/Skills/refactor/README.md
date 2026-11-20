# 项目重构专家（refactor）

用户说「重构 / 换语言 / 换架构 / 迁移 / 这项目太乱帮我理一下」时按这份说明执行。
先建索引，再查引用，最后一次只改一个文件或模块——**不要一上手就改代码**。

## 一、工作区里会生成什么
全部在 `<工作区>/.docs/` 下，都由 `rf_scan` / `rf_plan` 写出，不用手工编辑：

| 文件 | 内容 | 一行是什么样 |
|---|---|---|
| `function.txt` | 每个代码文件的作用 | `app/.../Cfg.kt \| Kotlin \| 812行 \| 键值存盘：所有开关与凭据都从这里读写` |
| `imports.txt` | 谁依赖谁 | `app/.../Agent.kt → b.ai.core.Cfg, b.ai.net.LlmClient, java.io.File` |
| `index.txt` | 规模与入口 | 文件数 / 总行数 / 语言分布 / 疑似入口 / 按目录分组 / 最大的 10 个文件 |
| `refactor.txt` | 重构计划 | 换语言或换架构的六步，带 `- [ ]` 勾选项，做完一条删一条 |
| `.files` | 扫描清单（内部用） | 绝对路径，一行一个；`rf_refs` 兜底搜源码时读它 |

扫盘规则：跳过 `.git` / `build` / `.gradle` / `node_modules` / `.cxx` / `dist` / `target` 这类
构建产物与依赖缓存，**任何层级上 `_` 开头的目录整体跳过**（`_operit`、`_third_party` 这种参考/第三方源码）。
所以索引里只有你自己的工作区代码；要分析第三方，就把 `ws` 指到那个子目录。

## 二、六个函数
参数都是必填（引擎限制），`ws` 一律给绝对路径。

| 函数 | 参数 | 干什么 |
|---|---|---|
| `rf_scan` | `ws` | 建三份索引，返回摘要 + `function.txt` 前 40 条 |
| `rf_refs` | `ws`、`target` | 反查：谁 import 了我；再加一段「源码里整词出现类名」的兜底 |
| `rf_deps` | `ws`、`file` | 正查：我依赖谁，分「内部 / 项目内 / 外部」三类 |
| `rf_plan` | `ws`、`mode`(lang\|arch) | 写 `.docs/refactor.txt` 六步计划 |
| `rf_grep` | `ws`、`kw` | 在索引里搜关键字，定位「哪个文件干这件事」 |
| `rf_show` | `ws`、`doc`、`page` | 翻页看一份索引，一次 100 行 |

调法（`ws` 换成真实工作区路径）：
```
rf_scan(ws="/sdcard/AndroidCSProjects/BaiAgent")
rf_grep(ws="...", kw="玻璃")
rf_show(ws="...", doc="function", page=2)
rf_refs(ws="...", target="app/src/main/kotlin/b/ai/core/Cfg.kt")
rf_deps(ws="...", file="app/src/main/kotlin/b/ai/agent/Agent.kt")
rf_plan(ws="...", mode="arch")
```

## 三、重构纪律
1. **一次只做一件事**：翻一个文件就编译；搬一个模块就跑一次真实入口，绿了再进下一步。
2. **每一步都能单独回滚**：不要攒一大坨改动再验证，出问题要能一步退回去。
3. **动手前查两次**：`rf_refs` 看谁会因为我改动而崩，`rf_deps` 看我还要顺着改哪些。
4. **先看索引再读源码**：`rf_grep` 能定位就别全文 grep，`function.txt` 能看清就别一个个文件翻。
5. **改完重跑 `rf_scan`**：索引过期比没有索引更坑。

## 四、两条路线的节奏
- **换语言**（`mode=lang`）：先定后缀/构建/依赖/包结构的映射表 → 从依赖链末端逐个翻 →
  每翻完更新索引并 `rf_refs` 复核 → 接口层最后翻且保签名 → 收尾换构建脚本、删旧文件。
- **换架构**（`mode=arch`）：先用 `imports.txt` 找循环依赖与上帝文件 → 定层与契约 →
  先抽接口/适配层（只加不删，编译一直绿）→ 一个模块一个模块搬 → 每搬完 `rf_refs` 查残留 →
  收尾删残留、统一目录、重跑 `rf_scan`。

## 五、坑
- `rf_refs` 的 import 匹配是「包含文件名/短名」，同名文件会一起命中；结果里要自己核对路径。
- Kotlin / Java 同包调用不写 import，全限定名调用（`b.ai.core.Sum.llm()`）也不写 import，
  这两种只能靠类名整词搜源码，兜底结果里已经包含了，但可能混进同名的无关文件。
- `rf_deps` 的「项目内」表示 import 的类在本项目里但没对上同名文件（一个文件里放了多个类），
  这不是错误，别当成外部库。
- `function.txt` 的作用是从文件头 600 行里第一条注释（或第一条 class/fun/val 声明）抠的，
  没注释的文件会显示「（没写说明）」，那就直接看源码，别瞎猜。
- 单文件超过 800KB、含 NUL 的二进制不会进索引。

## 六、实现结构（要改引擎时看）
包目录 `skill.json` + `function/*.js`（每个函数一行，调 `scripts/` 里的块）+
`scripts/` 分块引擎：

```
scripts/lib.sh      公共块：语言表、代码文件清单、索引目录
scripts/function.awk 块：文件作用表
scripts/imports.awk  块：依赖表
scripts/index.awk    块：规模表（读 function.txt 汇总，不再扫盘）
scripts/scan.sh      编排：清单扫一次 → 三个块各生成一份表 → 打印摘要
scripts/refs.sh      反查（imports.txt + 整词搜源码兜底）
scripts/deps.sh      正查（imports.txt + .files 比对）
scripts/plan.sh      出计划（lang / arch 两套步骤）
scripts/grep.sh      搜索引
scripts/show.sh      翻页看索引
```
改引擎只动 `scripts/`，`function/*.js` 与 `skill.json` 不用动；改完记得在设置里点「重新扫描技能包」。