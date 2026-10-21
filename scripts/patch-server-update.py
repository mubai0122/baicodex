#!/usr/bin/env python3
#在服务器上改 agent.ai.baiccb.cn/api/update/get.php 的 $UPDATE 块（只在远端跑，本地不会执行）
p = '/www/wwwroot/agent.ai.baiccb.cn/api/update/get.php'
s = open(p, encoding='utf-8').read()

start = s.index('$UPDATE = [')
end = s.index('];', start) + 2

new = """$UPDATE = [
    'code'            => 5,                       // 最新版本 code（纯数字，用于比较）
    'version'         => '1.1.0',                 // 最新版本号（纯数字）
    'updateDate'      => '2026-10-21',            // 更新的日期
    'updateEnabled'   => true,                    // 是否开启更新
    'forceUpdate'     => false,                   // 此版本是否开启强制更新
    'minForceCode'    => 1,                       // 最小强制更新 code（forceUpdate 为 false 时生效）
    'minForceVersion' => '1',                     // 最小强制更新版本号（纯数字）
    'title'           => 'v1.1.0',                 // 更新的标题（只写版本号）
    'subtitle'        => '新增 MCP 客户端与技能面板，使用统计改独立累积账本', // 更新的副标题
    'content'         => "1. 新增 MCP 客户端：JSON-RPC 2.0 over HTTP，支持会话初始化、SSE 流式响应与能力拉取\\n2. 新增 MCP 服务管理：设置 → 扩展 → MCP 服务，可增删改、测试连通、刷新能力、单服务开关\\n3. 新增 MCP 工具接入：MCP 能力自动并入工具表与 @ 面板，调用同走确认弹窗与权限路径\\n4. 新增 REST 桥支持：termux-mcp 的 /tools 取能力、/run 执行命令\\n5. 重构使用统计：改用独立累积账本，删除会话不再丢历史用量", // 更新内容，\\n 换行
    'downloadJumpUrl' => 'https://github.com/mubai0122/baicodex/releases/tag/v1.1.0', // 下载跳转链接（release 页）
    'downloadDirectUrl' => 'https://github.com/mubai0122/baicodex/releases/download/v1.1.0/BaiAgent-1.1.0.apk', // 直链下载链接
];"""

s = s[:start] + new + s[end:]
open('/tmp/get.php.new', 'w', encoding='utf-8').write(s)
print('已生成 /tmp/get.php.new，长度', len(s))

#更新历史：History.php 读 Update.json，第一条为最新
hist = """[
  {
    "code": 5,
    "version": "1.1.0",
    "date": "2026-10-21",
    "title": "v1.1.0",
    "subtitle": "新增 MCP 客户端与技能面板，使用统计改独立累积账本",
    "content": "1. 新增 MCP 客户端：JSON-RPC 2.0 over HTTP，支持会话初始化、SSE 流式响应与能力拉取\\n2. 新增 MCP 服务管理：设置 → 扩展 → MCP 服务，可增删改、测试连通、刷新能力、单服务开关\\n3. 新增 MCP 工具接入：MCP 能力自动并入工具表与 @ 面板，调用同走确认弹窗与权限路径\\n4. 新增 REST 桥支持：termux-mcp 的 /tools 取能力、/run 执行命令\\n5. 重构使用统计：改用独立累积账本，删除会话不再丢历史用量"
  },
  {
    "code": 4,
    "version": "1.0.3",
    "date": "2026-10-21",
    "title": "v1.0.3",
    "subtitle": "新增多语言支持，补全界面多语文案",
    "content": "1. 新增多语言支持：简体中文 / 繁體中文 / English / 日本語 / Français，可跟随系统或手动切换\\n2. 优化更多多语言内容支持：首页卡片、角色卡与开场白、工作方式与权限路径、新会话标题、设置页、语音报错、工具链检测、预置技能都按当前语言显示"
  },
  {
    "code": 3,
    "version": "1.0.2",
    "date": "2026-10-20",
    "title": "v1.0.2",
    "subtitle": "优化图片发送，优化图片预览以及展示界面样式",
    "content": "1. 优化图片发送：发出去按原图比例完整展示，不再裁成小方块\\n2. 优化图片预览：气泡内大图不裁切不变形，多图等比横排\\n3. 优化展示界面样式"
  },
  {
    "code": 2,
    "version": "1.0.1",
    "date": "2026-10-20",
    "title": "v1.0.1",
    "subtitle": "新增使用统计，优化使用界面",
    "content": "1. 新增使用统计\\n2. 优化使用界面"
  },
  {
    "code": 1,
    "version": "1.0.0",
    "date": "2026-10-20",
    "title": "v1.0.0",
    "subtitle": "Agent AI 正式上线",
    "content": "1. 首个正式版本发布"
  }
]
"""
open('/tmp/Update.json.new', 'w', encoding='utf-8').write(hist)
print('已生成 /tmp/Update.json.new，长度', len(hist))