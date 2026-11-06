package b.ai.desktop

import androidx.compose.ui.renderComposeScene
import androidx.compose.ui.unit.Density
import b.ai.desktop.core.Intent
import b.ai.desktop.core.ShellCore
import b.ai.desktop.ui.TitleBarPreview
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

//离屏渲染：不开窗口、不需要显示服务器，直接把同一份 Compose 画成 PNG
//用法: gradlew -p desktop shot -PshotOut=/path/ui.png
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "ui.png")
    val w = args.getOrNull(1)?.toIntOrNull() ?: 1280
    val h = args.getOrNull(2)?.toIntOrNull() ?: 820

    //预置两个标签，截图里能看到选中态和未选中态
    val core = ShellCore()
    core.dispatch(Intent.NewTab)
    core.dispatch(Intent.NewTab)
    core.state.tabs.firstOrNull()?.let { core.dispatch(Intent.SelectTab(it.id)) }

    val active = core.state.tabs.firstOrNull { it.id == core.state.active }?.title ?: "桌面版"
    val img = renderComposeScene(w, h, Density(1f)) { ShellBody(core) { TitleBarPreview(active) } }
    val data = img.encodeToData(EncodedImageFormat.PNG) ?: error("PNG 编码失败")
    out.parentFile?.mkdirs()
    out.writeBytes(data.bytes)
    println("SHOT_OK ${out.absolutePath} ${out.length()} bytes")
} //截图入口