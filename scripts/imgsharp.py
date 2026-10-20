#!/usr/bin/env python3
# 粗测截图锐度：解 PNG -> 按网格算平均梯度，用来对比「模糊前 / 模糊后」哪个区域真的糊了
import sys
import struct
import zlib

def load(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n'
    i = 8
    w = h = bitd = ct = 0
    idat = b''
    while i < len(d):
        ln = struct.unpack('>I', d[i:i + 4])[0]
        typ = d[i + 4:i + 8]
        body = d[i + 8:i + 8 + ln]
        if typ == b'IHDR':
            w, h, bitd, ct = struct.unpack('>IIBB', body[:10])
        elif typ == b'IDAT':
            idat += body
        elif typ == b'IEND':
            break
        i += 12 + ln
    assert bitd == 8 and ct in (2, 6), (bitd, ct)
    ch = 3 if ct == 2 else 4
    raw = zlib.decompress(idat)
    stride = w * ch
    out = bytearray(w * h)
    prev = bytearray(stride)
    pos = 0
    for y in range(h):
        f = raw[pos]; pos += 1
        line = bytearray(raw[pos:pos + stride]); pos += stride
        if f == 1:
            for x in range(ch, stride):
                line[x] = (line[x] + line[x - ch]) & 0xFF
        elif f == 2:
            for x in range(stride):
                line[x] = (line[x] + prev[x]) & 0xFF
        elif f == 3:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                line[x] = (line[x] + ((a + prev[x]) >> 1)) & 0xFF
        elif f == 4:
            for x in range(stride):
                a = line[x - ch] if x >= ch else 0
                b = prev[x]
                c = prev[x - ch] if x >= ch else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[x] = (line[x] + pr) & 0xFF
        for x in range(w):
            o = x * ch
            out[y * w + x] = (line[o] * 299 + line[o + 1] * 587 + line[o + 2] * 114) // 1000
        prev = line
    return w, h, out

def grid(path, gx, gy):
    w, h, px = load(path)
    print(path, w, 'x', h)
    for j in range(gy):
        row = []
        for i in range(gx):
            x0, x1 = i * w // gx, (i + 1) * w // gx
            y0, y1 = j * h // gy, (j + 1) * h // gy
            s = 0
            n = 0
            for y in range(y0, y1 - 1):
                base = y * w
                nb = base + w
                for x in range(x0, x1 - 1):
                    s += abs(px[nb + x] - px[base + x]) + abs(px[base + x + 1] - px[base + x])
                    n += 2
            row.append('OK' if False else f'{s / max(n, 1):6.1f}')
        print(' '.join(row))

if __name__ == '__main__':
    for p in sys.argv[1:]:
        grid(p, 4, 6)
