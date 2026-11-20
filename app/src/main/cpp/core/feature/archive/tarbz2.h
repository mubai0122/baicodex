/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-20 22:50:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

#pragma once

//tar.bz2 选择性解包：官方模型（sherpa-onnx / 各家 ASR）发的都是 tar.bz2，
//Android 系统镜像不带 bzip2 命令行，这里把 bzip2 的库代码静态编进来，自己解。
//只按 basename 挑需要的几个文件平铺写入目标目录：不建子目录，也就不存在路径穿越问题。
extern "C" {

//src 是 tar.bz2 路径，dstDir 是落地目录（需已存在），names 是想要的文件名（按 basename 比）
//返回 >=0 实际写出的文件数；-1 打不开源包，-2 bz2 流错误，-3 tar 结构错误，-4 写盘失败
int bai_tar_bz2_extract(const char *src, const char *dstDir, const char *const *names, int count);

} //extern "C"
