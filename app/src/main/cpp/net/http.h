#pragma once

#include <cstdint>
#include <string>
#include <vector>

namespace net {

struct Resp {
    int code = 0;                 //HTTP 状态码，0 表示没拿到响应
    std::vector<uint8_t> body;    //响应体（已解 chunked）
    std::string err;              //失败原因，成功时为空
};

// 极简 GET：只走 TLS/明文 TCP + HTTP/1.1，够抓 favicon 这类小文件，不处理 gzip
Resp get(const std::string& url, int timeoutMs, int maxRedirect = 3);

} //namespace net
