# Project:    Baicodex
# Author:     by 沐白/Baix
# License:    AGPL-3.0 (GNU Affero General Public License v3.0)
# Date:       2026-11-27 03:52:00
# Repository: https://github.com/mubai0122/Baicodex
# Website:    https://agent.ai.baiccb.cn/
#
# 打包小工具：目录打 zip / 三段合成自解压 exe。
# 单独拿出来是因为 bash 里做二进制拼接和 footer 计算太别扭。
#
# 用法：
#   python tools/pack.py zip   <源目录> <输出.zip>
#   python tools/pack.py merge <stub.exe> <installer.zip> <app.zip> <输出.exe>
#   python tools/pack.py fresh <目录>...      把上一轮的中间产物挪去临时目录（不删除）

import os
import shutil
import struct
import tempfile
import time
import sys
import zipfile

MAGIC = b"BAICODEXSFX01\x00\x00\x00"
assert len(MAGIC) == 16


def zip_dir(src: str, dst: str) -> None:
    src = os.path.abspath(src)
    if not os.path.isdir(src):
        raise SystemExit(f"目录不存在：{src}")
    total = 0
    with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as zf:
        for root, _, files in os.walk(src):
            for name in sorted(files):
                full = os.path.join(root, name)
                rel = os.path.relpath(full, src).replace("\\", "/")
                zf.write(full, rel)
                total += 1
    size = os.path.getsize(dst)
    print(f"  zip {os.path.basename(dst)} · {total} 个文件 · {size / 1048576:.1f} MB")


def merge(stub: str, installer_zip: str, app_zip: str, out: str) -> None:
    for path in (stub, installer_zip, app_zip):
        if not os.path.isfile(path):
            raise SystemExit(f"缺少输入文件：{path}")

    stub_size = os.path.getsize(stub)
    installer_size = os.path.getsize(installer_zip)
    app_size = os.path.getsize(app_zip)

    os.makedirs(os.path.dirname(os.path.abspath(out)) or ".", exist_ok=True)
    with open(out, "wb") as dst:
        for path in (stub, installer_zip, app_zip):
            with open(path, "rb") as src:
                while True:
                    chunk = src.read(1 << 20)
                    if not chunk:
                        break
                    dst.write(chunk)
        footer = MAGIC + struct.pack(
            "<QQQQ",
            stub_size,
            installer_size,
            stub_size + installer_size,
            app_size,
        )
        assert len(footer) == 48
        dst.write(footer)

    size = os.path.getsize(out)
    print(f"  合成 {os.path.basename(out)} · {size / 1048576:.1f} MB")
    print(
        "  布局 stub={:.1f}MB installer={:.1f}MB app={:.1f}MB".format(
            stub_size / 1048576, installer_size / 1048576, app_size / 1048576
        )
    )


def fresh(paths) -> None:
    """把上一轮的产物挪走而不是删掉：jlink/jpackage 要求输出目录不存在，
    而构建脚本不应该顺手做批量删除。旧目录进系统临时目录，交还给磁盘清理。"""
    base = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "build"))
    stash = os.path.join(tempfile.gettempdir(), "baicodex-build-" + time.strftime("%Y%m%d-%H%M%S"))
    for path in paths:
        absolute = os.path.abspath(path)
        if not (absolute == base or absolute.startswith(base + os.sep)):
            raise SystemExit(f"拒绝处理 build/ 之外的路径：{path}")
        if not os.path.exists(absolute):
            continue
        os.makedirs(stash, exist_ok=True)
        target = os.path.join(stash, os.path.basename(absolute))
        suffix = 0
        while os.path.exists(target):
            suffix += 1
            target = os.path.join(stash, f"{os.path.basename(absolute)}-{suffix}")
        shutil.move(absolute, target)
        print(f"  move {os.path.basename(absolute)} → {target}")


def main() -> None:
    if len(sys.argv) < 2:
        raise SystemExit(__doc__ or "缺少子命令")
    command = sys.argv[1]
    if command == "zip" and len(sys.argv) == 4:
        zip_dir(sys.argv[2], sys.argv[3])
    elif command == "merge" and len(sys.argv) == 6:
        merge(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
    elif command == "fresh" and len(sys.argv) >= 3:
        fresh(sys.argv[2:])
    else:
        raise SystemExit("参数不对，见文件头的用法说明")


if __name__ == "__main__":
    main()
