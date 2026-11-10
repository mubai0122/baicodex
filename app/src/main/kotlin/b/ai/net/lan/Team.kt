package b.ai.net.lan

import b.ai.core.cfg.app.Cfg
import b.ai.net.api.a2a.A2A as A2aClient
import b.ai.net.api.a2a.A2aNode
import b.ai.net.api.a2a.A2aRes
import b.ai.net.api.a2a.A2aSrv

//面对面团队协作：把局域网里配对过的设备当成本机的 A2A 对端，一端派活、一端接活
object Team {
    fun self(): String {
        val c = LanCfg.load()
        val tail = if (Cfg.a2aOn) "在 " + Cfg.a2aPort + " 端口接活（" + A2aSrv.baseUrl() + "）" else "没开协作，只能派活不能接活"
        return "本机 " + c.name + "，设备号 " + LanCfg.selfId() + "，" + tail
    } //本机协作状态：给页面与模型一句话说清楚

    fun ensureServe() {
        if (Cfg.a2aOn && !A2aSrv.running) runCatching { A2aSrv.start(Cfg.a2aPort) }
    } //配对完成或进程重启后补一次监听，避免开关开着却没人接

    /** 已配对设备 → A2A 对端；协作端口为 0 说明对方没开协作，返回 null */
    fun node(p: LanCfg.Peer): A2aNode? {
        if (p.a2aPort !in 1..65535) return null
        if (p.ip.isBlank()) return null
        return A2aNode(
            id = "lan-" + p.id,
            name = p.name.ifBlank { p.id },
            url = "http://" + p.ip + ":" + p.a2aPort,
            token = LanCfg.peerToken(p.id),
            on = true
        )
    } //地址用配对时记下的 IP，不走 DNS、也不联网

    fun find(key: String): LanCfg.Peer? {
        val k = key.trim()
        if (k.isEmpty()) return null
        val all = LanCfg.peers()
        return all.firstOrNull { it.id.equals(k, true) }
            ?: all.firstOrNull { it.name.equals(k, true) }
            ?: all.firstOrNull { it.ip.equals(k, true) }
            ?: all.firstOrNull { it.name.contains(k, true) }
    } //设备号 / 名字 / IP 三种写法都认，模型随手给一个就行

    fun offMsg(p: LanCfg.Peer): String =
        p.name.ifBlank { p.ip } + " 没开协作通道：让对方在「面对面团队协作」里把接活开关打开再配对一次"

    fun card(p: LanCfg.Peer): A2aRes {
        val n = node(p) ?: return A2aRes(false, offMsg(p))
        return A2aClient.card(n)
    } //看对面是谁、能干什么

    fun assign(p: LanCfg.Peer, text: String, waitMs: Long = 120_000L): A2aRes {
        val n = node(p) ?: return A2aRes(false, offMsg(p))
        if (text.isBlank()) return A2aRes(false, "要派的任务是空的")
        return A2aClient.send(n, text, waitMs)
    } //派活：对面一个分智能体跑完，结果原样回来

    fun tasks(p: LanCfg.Peer): A2aRes {
        val n = node(p) ?: return A2aRes(false, offMsg(p))
        return A2aClient.listTasks(n)
    }

    fun cancel(p: LanCfg.Peer, id: String): A2aRes {
        val n = node(p) ?: return A2aRes(false, offMsg(p))
        return A2aClient.cancelTask(n, id)
    }
} //面对面协作
