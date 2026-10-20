#!/usr/bin/env python3
#发 GitHub Release：建 tag + 传 APK 资产；token 走环境变量 GH，不写进任何文件
import json
import os
import urllib.request

REPO = 'mubai0122/baicodex'
TAG = 'v1.0.3'
NAME = 'v1.0.3'
APK = '/storage/emulated/0/AndroidCSProjects/BaiAgent/app/build/outputs/apk/release/BaiAgent-1.0.3.apk'
ASSET = 'BaiAgent-1.0.3.apk'
BODY = ('新增多语言支持\n\n'
        '1. 新增多语言支持：简体中文 / 繁體中文 / English / 日本語 / Français，可跟随系统或手动切换\n'
        '2. 优化更多多语言内容支持：首页卡片、角色卡与开场白、工作方式与权限路径、'
        '新会话标题、设置页、语音报错、工具链检测、预置技能都按当前语言显示')

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