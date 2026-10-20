#!/usr/bin/env python3
"""把源图裁到实际内容范围、留边距居中，再导出各密度 mipmap 图标"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngresize import read_png, resize, write_png  # noqa: E402

# 各密度目标边长（mdpi 48dp 起算）
DPIS = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]


def bbox(w, h, px, step=2):
    """找不透明内容的范围，透明画布上直接缩放会把图标缩得很小"""
    x0, y0, x1, y1 = w, h, -1, -1
    for y in range(0, h, step):
        base = y * w * 4
        for x in range(0, w, step):
            if px[base + x * 4 + 3] > 8:
                if x < x0:
                    x0 = x
                if x > x1:
                    x1 = x
                if y < y0:
                    y0 = y
                if y > y1:
                    y1 = y
    if x1 < 0:
        return 0, 0, w, h
    return x0, y0, x1 + 1, y1 + 1


def square(w, h, px, x0, y0, x1, y1, pad):
    """裁出内容，按最大边开正方形画布，内容居中，四周留 pad 比例的空"""
    cw, ch = x1 - x0, y1 - y0
    side = int(max(cw, ch) * (1 + pad * 2)) + 2
    out = bytearray(side * side * 4)
    ox = (side - cw) // 2
    oy = (side - ch) // 2
    for y in range(ch):
        src = ((y0 + y) * w + x0) * 4
        dst = ((oy + y) * side + ox) * 4
        out[dst:dst + cw * 4] = px[src:src + cw * 4]
    return side, side, bytes(out)


def main():
    src = sys.argv[1] if len(sys.argv) > 1 else "icon.png"
    res = sys.argv[2] if len(sys.argv) > 2 else "app/src/main/res"
    name = sys.argv[3] if len(sys.argv) > 3 else "app_icon"
    w, h, px = read_png(src)
    x0, y0, x1, y1 = bbox(w, h, px)
    print("source %dx%d 内容 %dx%d" % (w, h, x1 - x0, y1 - y0))
    sw, sh, spx = square(w, h, px, x0, y0, x1, y1, 0.06)
    for dpi, side in DPIS:
        nw, nh, npx = resize(sw, sh, spx, side)
        out = os.path.join(res, "mipmap-" + dpi, name + ".png")
        os.makedirs(os.path.dirname(out), exist_ok=True)
        write_png(out, nw, nh, npx)
        print("wrote %s %dx%d" % (out, nw, nh))


if __name__ == "__main__":
    main()