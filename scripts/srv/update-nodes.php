<?php
/**
 * update-nodes.php — 更新包镜像清单（客户端「检查更新」弹窗测速用）
 *
 * 部署路径：/www/wwwroot/agent.ai.baiccb.cn/api/update/nodes.php
 * 安装包放置：/www/wwwroot/agent.ai.baiccb.cn/apk/*.apk（放进去就生效，不用改本文件）
 *
 * 规则：
 * 1) 扫描 apk 目录，按文件名识别 ABI（-v8a / -v7a / arm64 / armeabi…），每个 ABI 取 mtime 最新的那个包；
 *    size / sha256 由本文件现算，不用手填；
 * 2) 同时输出旧格式（顶层 file + nodes，取 arm64 优先的默认包）与按 ABI 分块的 abis，
 *    老客户端读顶层，新客户端按本机 ABI 读 abis[abi]；
 * 3) 节点顺序与内容全部在本文件里配，客户端不硬编码任何镜像地址；
 * 4) 某个加速节点失效了，删掉那一行即可，客户端下次拉清单就换，不用发版。
 */
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

//站点根目录：本文件在 /api/update/ 下，往上两级
$SITE_ROOT = dirname(__DIR__, 2);
$APK_DIR   = $SITE_ROOT . '/apk';
$SITE      = 'https://agent.ai.baiccb.cn';
$GH_REPO   = 'mubai0122/baicodex';

/** 子串判断：服务器是 PHP 7，str_contains 得自己包一层 */
function has(string $hay, string $needle): bool
{
    return strpos($hay, $needle) !== false;
} //子串判断

/** 从文件名识别 ABI；通用包（不带宽高标识）返回空串 */
function abi_of(string $name): string
{
    $n = strtolower($name);
    if (has($n, 'arm64') || has($n, 'v8a') || has($n, 'aarch64')) {
        return 'arm64-v8a';
    }
    if (has($n, 'v7a') || has($n, 'armeabi')) {
        return 'armeabi-v7a';
    }
    if (has($n, 'x86_64')) {
        return 'x86_64';
    }
    if (has($n, 'x86')) {
        return 'x86';
    }
    return '';
} //文件名 → ABI

/** 文件名 → 版本标签：BaicodexV1.18.3-v8a.apk → v1.18.3 */
function tag_of(string $name): string
{
    if (preg_match('/(\d+(?:\.\d+)+)/', $name, $m)) {
        return 'v' . $m[1];
    }
    return '';
} //文件名 → tag

/** 为一个包生成节点列表：官方源（若本机有文件）+ GitHub 直连 + 常用加速前缀 */
function nodes_of(string $name, string $tag, string $site, bool $isSelf): array
{
    $out = [];
    if ($isSelf) {
        $out[] = ['id' => 'self', 'name' => '官方源', 'url' => $site . '/apk/' . rawurlencode($name)];
    }
    if ($tag === '') {
        return $out;
    }
    $rel = 'https://github.com/mubai0122/baicodex/releases/download/' . $tag . '/' . $name;
    $out[] = ['id' => 'gh',      'name' => 'GitHub',       'url' => $rel];
    $out[] = ['id' => 'ghproxy', 'name' => 'gh-proxy.com', 'url' => 'https://gh-proxy.com/' . $rel];
    $out[] = ['id' => 'ghfast',  'name' => 'ghfast.top',   'url' => 'https://ghfast.top/' . $rel];
    $out[] = ['id' => 'ghpnet',  'name' => 'ghproxy.net',  'url' => 'https://ghproxy.net/' . $rel];
    return $out;
} //包 → 节点列表

$files = glob($APK_DIR . '/*.apk') ?: [];
if (count($files) === 0) {
    http_response_code(200);
    echo json_encode(
        ['ok' => false, 'msg' => 'apk 目录里还没有安装包'],
        JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES
    );
    exit;
}

//按 ABI 分组，每组保留 mtime 最新的那个
$best = [];
$newestMtime = 0;
foreach ($files as $p) {
    $abi = abi_of(basename($p));
    $mt  = filemtime($p);
    $newestMtime = max($newestMtime, $mt);
    if (!isset($best[$abi]) || $mt > filemtime($best[$abi])) {
        $best[$abi] = $p;
    }
} //分组取最新

$abis = [];
$filesMeta = [];
foreach ($best as $abi => $path) {
    $name = basename($path);
    $tag  = tag_of($name);
    $one  = [
        'file'  => [
            'name'   => $name,
            'size'   => filesize($path),
            'sha256' => hash_file('sha256', $path), //现算，不放缓存
        ],
        'nodes' => nodes_of($name, $tag, $SITE, true),
    ];
    if ($abi !== '') {
        $abis[$abi] = $one;
        $filesMeta[$abi] = $one['file'];
    }
    //兜底块：通用包或第一个包，给老客户端用
    if (!isset($abis['_default'])) {
        $abis['_default'] = $one;
    }
}

//老客户端只认顶层 file + nodes：优先 arm64，其次 armeabi-v7a，最后兜底块
$default = $abis['arm64-v8a'] ?? ($abis['armeabi-v7a'] ?? $abis['_default']);
unset($abis['_default']);

echo json_encode([
    'ok'        => true,
    'version'   => date('Ymd.Hi', $newestMtime),
    'file'      => $default['file'],   //兼容：旧客户端读这里
    'nodes'     => $default['nodes'],  //兼容：旧客户端读这里
    'files'     => $filesMeta,         //按 ABI 的包元信息
    'abis'      => $abis,              //按 ABI 的 {file,nodes}，新客户端按本机 ABI 取
    'threshold' => ['ttfbMs' => 200],
], JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES); //清单输出