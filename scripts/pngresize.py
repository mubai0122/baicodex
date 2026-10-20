#!/usr/bin/env python3
"""纯标准库 PNG 缩放：读 PNG → box 降采样 → 写 RGBA PNG（不依赖 PIL）"""
import struct
import sys
import zlib

PNG_SIG = b"\x89PNG\r\n\x1a\n"


def read_png(path):
    """读 PNG，返回 (宽, 高, 每像素 RGBA 字节串)"""
    raw = open(path, "rb").read()
    if raw[:8] != PNG_SIG:
        raise SystemExit("不是 PNG 文件")
    pos = 8
    w = h = bit = ctype = 0
    idat = b""
    plte = b""
    trns = b""
    while pos < len(raw):
        ln = struct.unpack(">I", raw[pos:pos + 4])[0]
        typ = raw[pos + 4:pos + 8]
        body = raw[pos + 8:pos + 8 + ln]
        pos += 12 + ln
        if typ == b"IHDR":
            w, h, bit, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            if interlace:
                raise SystemExit("不支持隔行扫描 PNG")
        elif typ == b"PLTE":
            plte = body
        elif typ == b"tRNS":
            trns = body
        elif typ == b"IDAT":
            idat += body
        elif typ == b"IEND":
            break
    if bit != 8:
        raise SystemExit("只支持 8bit 深度的 PNG（当前 %d）" % bit)
    data = zlib.decompress(idat)
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = w * ch
    out = bytearray(stride * h)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = data[p]
        p += 1
        line = bytearray(data[p:p + stride])
        p += stride
        if f == 1:
            for i in range(ch, stride):
                line[i] = (line[i] + line[i - ch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i - ch] if i >= ch else 0
                b = prev[i]
                c = prev[i - ch] if i >= ch else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line
    # 统一成 RGBA
    rgba = bytearray(w * h * 4)
    for i in range(w * h):
        if ctype == 6:
            rgba[i * 4:i * 4 + 4] = out[i * 4:i * 4 + 4]
        elif ctype == 2:
            r, g, b = out[i * 3:i * 3 + 3]
            rgba[i * 4:i * 4 + 4] = bytes((r, g, b, 255))
        elif ctype == 0:
            v = out[i]
            rgba[i * 4:i * 4 + 4] = bytes((v, v, v, 255))
        elif ctype == 4:
            v, a = out[i * 2], out[i * 2 + 1]
            rgba[i * 4:i * 4 + 4] = bytes((v, v, v, a))
        else:  # 调色板
            idx = out[i]
            r, g, b = plte[idx * 3:idx * 3 + 3]
            a = trns[idx] if idx < len(trns) else 255
            rgba[i * 4:i * 4 + 4] = bytes((r, g, b, a))
    return w, h, bytes(rgba)


def resize(w, h, rgba, side):
    """box 平均降采样到 side×side（放大用最近邻，图标用不到）"""
    if w == side and h == side:
        return w, h, rgba
    out = bytearray(side * side * 4)
    for y in range(side):
        y0 = y * h // side
        y1 = max(y0 + 1, (y + 1) * h // side)
        for x in range(side):
            x0 = x * w // side
            x1 = max(x0 + 1, (x + 1) * w // side)
            r = g = b = a = n = 0
            for sy in range(y0, y1):
                base = sy * w * 4
                for sx in range(x0, x1):
                    i = base + sx * 4
                    r += rgba[i]
                    g += rgba[i + 1]
                    b += rgba[i + 2]
                    a += rgba[i + 3]
                    n += 1
            o = (y * side + x) * 4
            out[o] = r // n
            out[o + 1] = g // n
            out[o + 2] = b // n
            out[o + 3] = a // n
    return side, side, bytes(out)


def write_png(path, w, h, rgba):
    """写 RGBA PNG（filter 0）"""
    raw = bytearray()
    stride = w * 4
    for y in range(h):
        raw.append(0)
        raw += rgba[y * stride:(y + 1) * stride]

    def chunk(typ, body):
        return struct.pack(">I", len(body)) + typ + body + struct.pack(">I", zlib.crc32(typ + body) & 0xFFFFFFFF)

    ihdr = struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(PNG_SIG)
        f.write(chunk(b"IHDR", ihdr))
        f.write(chunk(b"IDAT", zlib.compress(bytes(raw), 9)))
        f.write(chunk(b"IEND", b""))


def main():
    if len(sys.argv) < 2:
        raise SystemExit("用法: pngresize.py <源.png> [输出=side:路径 ...]")
    src = sys.argv[1]
    w, h, px = read_png(src)
    print("source %dx%d" % (w, h))
    for arg in sys.argv[2:]:
        side_s, out = arg.split(":", 1)
        side = int(side_s)
        nw, nh, npx = resize(w, h, px, side)
        write_png(out, nw, nh, npx)
        print("wrote %s %dx%d" % (out, nw, nh))


if __name__ == "__main__":
    main()
