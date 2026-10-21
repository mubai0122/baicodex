#!/usr/bin/env python3
#发 GitHub Release：建 tag + 传 APK 资产；token 走环境变量 GH，不写进任何文件
import json
import os
import urllib.request

REPO = 'mubai0122/baicodex'
TAG = 'v1.1.1'
NAME = 'v1.1.1'
APK = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/build/outputs/apk/release/BaiAgent-1.1.1.apk'
ASSET = 'BaiAgent-1.1.1.apk'
BODY = ('新增消息收藏、单条删除、朗读与输入草稿自动保存\n\n'
        '1. 新增消息收藏：长按消息可收藏 / 取消收藏，侧边栏「收藏」跨会话翻回来，点一条直接跳回原会话\n'
        '2. 新增单条消息删除：长按菜单两段确认后从会话里移除，会话预览同步更新\n'
        '3. 新增消息朗读：长按菜单用系统 TTS 念这一条，不受「自动朗读回复」开关限制\n'
        '4. 新增输入草稿自动保存：每个会话各自一份草稿，切会话、重进 App 都还在\n'
        '5. 修复收藏夹弹窗层级：面板提到根弹窗层，打开时自动收起侧边栏，点外部或返回键都能关')

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