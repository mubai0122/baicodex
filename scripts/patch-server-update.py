#!/usr/bin/env python3
#在服务器上改 agent.ai.baiccb.cn/api/update/get.php 的 $UPDATE 块（只在远端跑，本地不会执行）
p = '/www/wwwroot/agent.ai.baiccb.cn/api/update/get.php'
s = open(p, encoding='utf-8').read()

start = s.index('$UPDATE = [')
end = s.index('];', start) + 2

new = """$UPDATE = [
    'code'            => 3,                       // 最新版本 code（纯数字，用于比较）
    'version'         => '1.0.2',                 // 最新版本号（纯数字）
    'updateDate'      => '2026-10-20',            // 更新的日期
    'updateEnabled'   => true,                    // 是否开启更新
    'forceUpdate'     => false,                   // 此版本是否开启强制更新
    'minForceCode'    => 1,                       // 最小强制更新 code（forceUpdate 为 false 时生效）
    'minForceVersion' => '1',                     // 最小强制更新版本号（纯数字）
    'title'           => 'v1.0.2 图片体验优化',    // 更新的标题
    'subtitle'        => '优化图片发送，优化图片预览以及展示界面样式', // 更新的副标题
    'content'         => "1. 优化图片发送：发出去按原图比例完整展示，不再裁成小方块\\n2. 优化图片预览：气泡内大图不裁切不变形，多图等比横排\\n3. 优化展示界面样式", // 更新内容，\\n 换行
    'downloadJumpUrl' => 'https://github.com/mubai0122/baicodex/releases/tag/v1.0.2', // 下载跳转链接（release 页）
    'downloadDirectUrl' => 'https://github.com/mubai0122/baicodex/releases/download/v1.0.2/BaiAgent-1.0.2.apk', // 直链下载链接
];"""

s = s[:start] + new + s[end:]
open('/tmp/get.php.new', 'w', encoding='utf-8').write(s)
print('已生成 /tmp/get.php.new，长度', len(s))

#更新历史：History.php 读 Update.json，第一条为最新
hist = """[
  {
    "code": 3,
    "version": "1.0.2",
    "date": "2026-10-20",
    "title": "优化图片发送与预览",
    "subtitle": "优化图片发送，优化图片预览以及展示界面样式",
    "content": "1. 优化图片发送：发出去按原图比例完整展示，不再裁成小方块\\n2. 优化图片预览：气泡内大图不裁切不变形，多图等比横排\\n3. 优化展示界面样式"
  },
  {
    "code": 2,
    "version": "1.0.1",
    "date": "2026-10-20",
    "title": "使用统计",
    "subtitle": "新增使用统计，优化使用界面",
    "content": "1. 新增使用统计\\n2. 优化使用界面"
  },
  {
    "code": 1,
    "version": "1.0.0",
    "date": "2026-10-20",
    "title": "首个版本发布",
    "subtitle": "Agent AI 正式上线",
    "content": "1. 首个正式版本发布"
  }
]
"""
open('/tmp/Update.json.new', 'w', encoding='utf-8').write(hist)
print('已生成 /tmp/Update.json.new，长度', len(hist))