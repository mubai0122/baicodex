#include "core/feature/miot/miot.h"

#include <arpa/inet.h>
#include <ifaddrs.h>
#include <net/if.h>
#include <netinet/in.h>
#include <poll.h>
#include <sys/socket.h>
#include <unistd.h>

#include <chrono>
#include <cstring>

namespace bai::feature::miot {

namespace {

/** 单调时钟毫秒：算截止时间用它，改系统时间不会把探测循环卡死 */
int64_t nowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}

struct Sock {
    int fd = -1;
    ~Sock() {
        if (fd >= 0) ::close(fd);
    }
}; //socket 守卫，出作用域即关

/** 建一个能广播的 UDP socket */
int openUdp() {
    const int fd = ::socket(AF_INET, SOCK_DGRAM, 0);
    if (fd < 0) return -1;
    const int on = 1;
    ::setsockopt(fd, SOL_SOCKET, SO_BROADCAST, &on, sizeof(on));
    return fd;
} //建 socket

/** 广播地址表：全局广播 + 每张网卡子网广播，个别 ROM 只吃后者 */
std::vector<uint32_t> broadcastAddrs() {
    std::vector<uint32_t> out;
    out.push_back(INADDR_BROADCAST);
    struct ifaddrs *list = nullptr;
    if (::getifaddrs(&list) != 0) return out;
    for (struct ifaddrs *it = list; it != nullptr; it = it->ifa_next) {
        if (it->ifa_addr == nullptr || it->ifa_addr->sa_family != AF_INET) continue;
        if ((it->ifa_flags & IFF_UP) == 0 || (it->ifa_flags & IFF_LOOPBACK) != 0) continue;
        if ((it->ifa_flags & IFF_BROADCAST) == 0 || it->ifa_broadaddr == nullptr) continue;
        const auto *b = reinterpret_cast<const struct sockaddr_in *>(it->ifa_broadaddr);
        const uint32_t addr = b->sin_addr.s_addr;
        bool dup = false;
        for (uint32_t x : out) {
            if (x == addr) {
                dup = true;
                break;
            }
        }
        if (!dup) out.push_back(addr);
    }
    ::freeifaddrs(list);
    return out;
} //广播地址

/** 发一个探测包到所有广播地址 */
void sendProbe(int fd, const uint8_t *probe, size_t n) {
    for (uint32_t addr : broadcastAddrs()) {
        struct sockaddr_in to{};
        to.sin_family = AF_INET;
        to.sin_port = htons(static_cast<uint16_t>(PORT));
        to.sin_addr.s_addr = addr;
        ::sendto(fd, probe, n, 0, reinterpret_cast<const struct sockaddr *>(&to), sizeof(to));
    }
} //发探测包

/** 等到下一次可读的时刻：返回 true 表示有包可收 */
bool waitReadable(int fd, int timeoutMs) {
    struct pollfd p{};
    p.fd = fd;
    p.events = POLLIN;
    return ::poll(&p, 1, timeoutMs) > 0;
} //等可读

/** 收一个包，返回字节数；来源 IP / 端口填进 from */
ssize_t recvFrom(int fd, uint8_t *buf, size_t cap, struct sockaddr_in *from) {
    socklen_t len = sizeof(*from);
    return ::recvfrom(fd, buf, cap, 0, reinterpret_cast<struct sockaddr *>(from), &len);
} //收包

/** 固定 IP 的发送目标；IP 非法返回 false */
bool target(const std::string &ip, struct sockaddr_in &to) {
    std::memset(&to, 0, sizeof(to));
    to.sin_family = AF_INET;
    to.sin_port = htons(static_cast<uint16_t>(PORT));
    return ::inet_pton(AF_INET, ip.c_str(), &to.sin_addr) == 1;
} //目标地址
} //命名空间

std::vector<Found> discover(int timeoutMs) {
    const int fd = openUdp();
    if (fd < 0) return {};
    Sock guard{fd};
    uint8_t probe[HEAD];
    bai_miot_probe(static_cast<uint64_t>(nowMs()), probe);
    sendProbe(fd, probe, sizeof(probe));

    std::vector<Found> out;
    uint8_t buf[1400];
    const int64_t deadline = nowMs() + timeoutMs;
    while (true) {
        const int64_t left = deadline - nowMs();
        if (left <= 0) break;
        if (!waitReadable(fd, static_cast<int>(left))) break;
        struct sockaddr_in from{};
        const ssize_t n = recvFrom(fd, buf, sizeof(buf), &from);
        if (n < static_cast<ssize_t>(HEAD)) continue;
        if (ntohs(from.sin_port) != PORT) continue;
        const int64_t did = bai_miot_head_did(buf, static_cast<size_t>(n));
        if (did <= 0) continue;
        char ip[INET_ADDRSTRLEN] = {0};
        if (::inet_ntop(AF_INET, &from.sin_addr, ip, sizeof(ip)) == nullptr) continue;
        bool seen = false;
        for (auto &f : out) {
            if (f.did != did) continue;
            f.ip = ip;
            f.stamp = bai_miot_head_stamp(buf, static_cast<size_t>(n));
            seen = true;
            break;
        }
        if (seen) continue;
        Found f;
        f.did = did;
        f.ip = ip;
        f.stamp = bai_miot_head_stamp(buf, static_cast<size_t>(n));
        out.push_back(f);
    }
    return out;
} //扫一遍局域网

std::string call(const std::string &ip, int64_t did, const std::vector<uint8_t> &token, uint32_t stamp,
                 const std::string &json, int timeoutMs, uint32_t *stampOut) {
    struct sockaddr_in to{};
    if (!target(ip, to)) return {};
    const int fd = openUdp();
    if (fd < 0) return {};
    Sock guard{fd};
    const std::vector<uint8_t> packet = pack(token, did, stamp, json);
    if (packet.empty()) return {};
    if (::sendto(fd, packet.data(), packet.size(), 0, reinterpret_cast<struct sockaddr *>(&to), sizeof(to)) < 0) {
        return {};
    }
    const int64_t want = bai_miot_json_int(reinterpret_cast<const uint8_t *>(json.data()), json.size(), "id", 2);
    uint8_t buf[1400];
    const int64_t deadline = nowMs() + timeoutMs;
    while (true) {
        const int64_t left = deadline - nowMs();
        if (left <= 0) return {};
        if (!waitReadable(fd, static_cast<int>(left))) return {};
        struct sockaddr_in from{};
        const ssize_t n = recvFrom(fd, buf, sizeof(buf), &from);
        if (n <= static_cast<ssize_t>(HEAD)) continue;
        if (bai_miot_head_did(buf, static_cast<size_t>(n)) != did) continue;
        const uint32_t stamp = bai_miot_head_stamp(buf, static_cast<size_t>(n));
        const std::string text = unpack(token, buf, static_cast<size_t>(n));
        if (text.empty()) continue;
        if (bai_miot_json_int(reinterpret_cast<const uint8_t *>(text.data()), text.size(), "id", 2) != want) continue;
        if (stampOut != nullptr) *stampOut = stamp;
        return text;
    }
} //一次请求-应答

bool alive(const std::string &ip, int64_t did, int timeoutMs, uint32_t *stampOut) {
    struct sockaddr_in to{};
    if (!target(ip, to)) return false;
    const int fd = openUdp();
    if (fd < 0) return false;
    Sock guard{fd};
    uint8_t probe[HEAD];
    bai_miot_probe(static_cast<uint64_t>(nowMs()), probe);
    if (::sendto(fd, probe, sizeof(probe), 0, reinterpret_cast<struct sockaddr *>(&to), sizeof(to)) < 0) return false;
    uint8_t buf[1400];
    if (!waitReadable(fd, timeoutMs)) return false;
    struct sockaddr_in from{};
    const ssize_t n = recvFrom(fd, buf, sizeof(buf), &from);
    if (n < static_cast<ssize_t>(HEAD)) return false;
    if (bai_miot_head_did(buf, static_cast<size_t>(n)) != did) return false;
    if (stampOut != nullptr) *stampOut = bai_miot_head_stamp(buf, static_cast<size_t>(n));
    return true;
} //单台探活

} //namespace bai::feature::miot