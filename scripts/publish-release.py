#!/usr/bin/env python3
#发 GitHub Release：建 tag + 传 APK 资产；token 走环境变量 GH，不写进任何文件
import json
import os
import urllib.request

REPO = 'mubai0122/baicodex'
TAG = 'v1.2.0'
NAME = 'v1.2.0'
APK = '/storage/emulated/0/AndroidCSProjects/BaiAgent/.tmp/BaiAgent-1.2.0.apk'
ASSET = 'BaiAgent-1.2.0.apk'
BODY = ('v1.2.0 新增永久记忆与消息分支，分库提速，备份恢复\n\n'
        '新增永久记忆：模型自己判断值得长期记住的偏好与结论会存进「记忆」，侧边栏「记忆」可逐条删除，没删的每轮都随系统提示带上\n'
        '新增消息分支：重新生成 / 编辑重发不再删掉旧回答，而是长成第二支，聊天里 ‹ 1/2 › 来回切\n'
        '新增整库备份与恢复：关于页一键把会话库 / 统计库 / 配置 / 头像导出成一个 zip，导入后下次启动自动换上\n'
        '优化卡顿：用量账本搬进独立的 bai_stat.db，会话列表改为一次扫描，会话多了不再卡\n'
        '新增统计费用：输入 / 输出 / 缓存三项单价可填，统计页直接算出花了多少钱\n'
        '新增思考强度滑杆：淡青到淡粉渐变轨道、三档刻度、按住高光循环扫过，松手吸附档位\n'
        '新增质感外观：液态玻璃卡片 / 液态玻璃底栏 / 质感按钮 / 三种页面背景，都在设置 → 外观里\n'
        '新增 Linux 环境安装进度：白底等宽日志、阶段进度条与逐文件回显，日志自动贴底\n'
        '修复：输入面板关闭不跟手、启动页能力卡误触、执行命令卡片过宽、统计脏数据导致归零\n')
TOKEN = os.environ['GH']


def call(url, data=None, method='GET', ctype='application/json', raw=None):
    body = raw if raw is not None else (json.dumps(data).encode() if data is not None else None)
    r = urllib.request.Request(url, data=body, method=method)
    r.add_header('Authorization', 'Bearer ' + TOKEN)
    r.add_header('Accept', 'application/vnd.github+json')
    r.add_header('User-Agent', 'bai-release')
    if body is not None:
        r.add_header('Content-Type', ctype)
    with urllib.request.urlopen(r, timeout=300) as resp:
        return json.loads(resp.read().decode())


rel = call('https://api.github.com/repos/%s/releases' % REPO, data={
    'tag_name': TAG,
    'target_commitish': 'main',
    'name': NAME,
    'body': BODY,
    'draft': False,
    'prerelease': False
}, method='POST')
print('release id=%s url=%s' % (rel['id'], rel['html_url']))

size = os.path.getsize(APK)
with open(APK, 'rb') as f:
    blob = f.read()
up = call('%s?name=%s' % (rel['upload_url'].split('{')[0], ASSET),
          method='POST', ctype='application/vnd.android.package-archive', raw=blob)
print('asset name=%s size=%s state=%s' % (up['name'], up['size'], up['state']))
print('expect=%d actual=%d' % (size, up['size']))
print('direct=%s' % up['browser_download_url'])