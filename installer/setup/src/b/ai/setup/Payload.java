/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 03:26:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 安装包读取：定位附加在 exe 尾部的 zip 段，流式解到目标目录。
 * 这一段是「自解压」的另一半——stub 只解压安装向导，真正的大块由这里处理。
 */

package b.ai.setup;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class Payload {

    static final byte[] MAGIC = "BAICODEXSFX01\u0000\u0000\u0000".getBytes(StandardCharsets.ISO_8859_1);
    private static final int FOOTER_LEN = 48;

    static final class Footer {
        long installerOff;
        long installerLen;
        long appOff;
        long appLen;
    }

    interface Progress {
        void onProgress(long done, long total, String current);
    }

    static Footer readFooter(Path exe) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(exe.toFile(), "r")) {
            long size = raf.length();
            if (size < FOOTER_LEN) {
                throw new IOException("安装包不完整");
            }
            raf.seek(size - FOOTER_LEN);
            byte[] buf = new byte[FOOTER_LEN];
            raf.readFully(buf);
            for (int i = 0; i < 16; i++) {
                if (buf[i] != MAGIC[i]) {
                    throw new IOException("这不是完整的 Baicodex 安装包（尾部标识缺失）");
                }
            }
            Footer footer = new Footer();
            footer.installerOff = le64(buf, 16);
            footer.installerLen = le64(buf, 24);
            footer.appOff = le64(buf, 32);
            footer.appLen = le64(buf, 40);
            if (footer.appOff + footer.appLen > size - FOOTER_LEN) {
                throw new IOException("安装包被截断，请重新下载");
            }
            return footer;
        }
    }

    private static long le64(byte[] b, int at) {
        long value = 0;
        for (int i = 7; i >= 0; i--) {
            value = (value << 8) | (b[at + i] & 0xFFL);
        }
        return value;
    }

    /**
     * 解压 app 段到 target。
     * ZipFile 需要随机访问，而我们的 zip 埋在 exe 中间，所以先把这一段顺序拷成临时文件，
     * 换到准确的条目总大小后再解压——进度条才不会来回跳。
     */
    static void extractApp(Path exe, Path target, Progress progress) throws IOException {
        Footer footer = readFooter(exe);
        Path tmp = Files.createTempFile("baicodex-app-", ".zip");
        try {
            copyRange(exe, footer.appOff, footer.appLen, tmp);

            try (ZipFile zip = new ZipFile(tmp.toFile(), StandardCharsets.UTF_8)) {
                long total = 0;
                Enumeration<? extends ZipEntry> count = zip.entries();
                while (count.hasMoreElements()) {
                    ZipEntry entry = count.nextElement();
                    if (!entry.isDirectory()) {
                        total += Math.max(entry.getSize(), 0);
                    }
                }

                long done = 0;
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    Path out = target.resolve(entry.getName()).normalize();
                    if (!out.startsWith(target)) {
                        throw new IOException("压缩包路径越界：" + entry.getName());
                    }
                    if (entry.isDirectory()) {
                        Files.createDirectories(out);
                        continue;
                    }
                    Path parent = out.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    // 覆盖安装时旧文件可能带只读位（jpackage 产物就是这样），先放开
                    if (Files.exists(out) && !Files.isWritable(out)) {
                        out.toFile().setWritable(true);
                    }
                    try (InputStream in = zip.getInputStream(entry);
                         OutputStream os = new BufferedOutputStream(Files.newOutputStream(out), 1 << 16)) {
                        in.transferTo(os);
                    }
                    done += Math.max(entry.getSize(), 0);
                    if (progress != null) {
                        progress.onProgress(done, total, entry.getName());
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** 把 exe 开头那段（自解压头本体）单独存成卸载器：安装目录里的 uninstall.exe */
    static void writeUninstaller(Path exe, Path destination) throws IOException {
        Footer footer = readFooter(exe);
        copyRange(exe, 0, footer.installerOff, destination);
    }

    private static void copyRange(Path src, long offset, long length, Path dst) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(src.toFile(), "r")) {
            raf.seek(offset);
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(dst), 1 << 20)) {
                byte[] buf = new byte[1 << 20];
                long left = length;
                while (left > 0) {
                    int read = raf.read(buf, 0, (int) Math.min(left, buf.length));
                    if (read < 0) {
                        throw new IOException("安装包被截断");
                    }
                    out.write(buf, 0, read);
                    left -= read;
                }
            }
        }
    }

    /** app 段解压后占多少空间，用于空间检查 */
    static long appSize(Path exe) throws IOException {
        Footer footer = readFooter(exe);
        return footer.appLen;
    }

    private Payload() {
    }
}
