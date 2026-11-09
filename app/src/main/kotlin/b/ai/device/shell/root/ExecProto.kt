package b.ai.device.shell.root

/** 用户服务与客户端约好的事务号：单独放一个空对象，服务端类加载时不会把整个 Shiz 拽进来 */
internal object ExecProto {
    const val DESC = "b.ai.exec.IExec"
    const val TX_EXEC = 1
    const val TX_UID = 2
} //用户服务协议常量
