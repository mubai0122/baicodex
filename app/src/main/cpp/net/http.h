#pragma once
#include <cstdint>
#include <string>
#include <vector>
namespace net {
struct Resp {
    int code = 0;                 //HTTP 状态码，0 表示没拿到响应
    std::vector<uint8_t> body;    //响应体（已解 chunked）
    std::string head;             //原始响应头，curl 原样回显用
    std::string err;              //失败原因，成功时为空
};
// 极简 GET：只走 TLS/明文 TCP + HTTP/1.1，够抓 favicon 这类小文件，不处理 gzip
Resp get(const std::string& url, int timeoutMs, int maxRedirect = 3);
// 任意方法的一次请求：headers 每行「名: 值」，maxBody=0 用默认 1MB
Resp curl(const std::string& method, const std::string& url, const std::string& headers,
          const std::string& body, int timeoutMs, size_t maxBody, int maxRedirect = 5);
struct Ping {
    std::string ip;               //解析到的地址
    std::vector<int> rtt;         //每次建连的毫秒
    int sent = 0;                 //发了多少次
    std::string err;              //失败原因
};
// TCP 建连计时：ICMP 需要 root，这里用目标端口的握手时间当延迟，普通权限也能跑
Ping ping(const std::string& host, int port, int count, int timeoutMs);
// HTML → 纯文本：去 script/style/注释/标签，解常见实体，压掉连续空白
std::string text(const std::string& html);
} //namespace net
