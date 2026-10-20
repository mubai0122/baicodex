package b.ai.core

/** 首页的一张能力卡：点一下把 [prompt] 填进输入框（或打开对应面板）；icon 对应自绘矢量 */
data class FeatureCard(
    val id: String,
    val icon: String,
    val title: String,
    val desc: String,
    val prompt: String
)

/** 首页能力卡：只提供「起手式」，具体动作仍由模型按工具清单自己决定 */
object Cards {
    val all: List<FeatureCard> = listOf(
        FeatureCard(
            id = "build",
            icon = "cube",
            title = "一键构建",
            desc = "编译并安装 APK",
            prompt = "帮我在当前工作区里构建并安装这个 Android 工程。步骤：\n" +
                "1) 先用 exec_shell 跑 ls 看工作区里有什么，确认工程根（有 gradlew 或 build.gradle）；\n" +
                "2) 在工程根执行 ./gradlew assembleDebug（有 app/build.sh 就用 bash ./app/build.sh -d </dev/null），" +
                "把完整报错原文贴给我，别猜；\n" +
                "3) 构建成功后 pm install -r 安装并用 am start 启动；\n" +
                "4) 工具链不在 PATH 里时先 which java gradle 查一遍，缺什么告诉我怎么装；\n" +
                "5) 每一步先说结论再说命令，失败就停在报错处等我处理。"
        ),
        FeatureCard(
            id = "reverse",
            icon = "search",
            title = "逆向分析",
            desc = "看包名、权限、dex、硬编码",
            prompt = "逆向分析这个 APK：先用 aapt dump badging 看包名、版本、权限、入口 Activity；" +
                "再解包看 classes.dex 与 lib/ 下的 so；重点找硬编码的域名、密钥、接口路径与签名校验点。" +
                "产物和中间文件都放工作区的 reverse/ 下，重要结论先落文件再回我。\n" +
                "APK 路径："
        ),
        FeatureCard(
            id = "plan",
            icon = "layers",
            title = "编译策划",
            desc = "一句话出完整开发计划",
            prompt = "帮我把下面这个需求做成能直接执行的开发计划：范围与不做的事、技术选型、" +
                "分阶段步骤（每步一个可编译的增量）、每步的验证方式、以及主要风险与回退方案。\n" +
                "需求："
        ),
        FeatureCard(
            id = "rail",
            icon = "train",
            title = "行程规划",
            desc = "12306 余票 + 中转",
            prompt = "帮我规划行程：查 12306 余票（用 rail_query），给 3 个方案，直达优先、没有再给中转组合，" +
                "每个方案写清出发到达时间、历时、车次与二等座余票。\n" +
                "出发地：杭州，目的地：上海，日期：明天"
        ),
        FeatureCard(
            id = "ui",
            icon = "tap",
            title = "操作助手",
            desc = "看屏幕并点按应用",
            prompt = "帮我操作手机完成这件事：先用 exec_shell 跑 uiautomator dump ui.xml（落在工作区），" +
                "再用 read_file 读它，按里面的 text/resource-id/bounds 判断界面结构与可点位置；" +
                "要点击就用 input tap X Y（或 input text 输入），每做一步先说明再执行，" +
                "遇到登录、支付、删数据的动作停下来问我。shizuku/root 权限下这些命令都能直接用。\n" +
                "目标："
        ),
        FeatureCard(
            id = "code",
            icon = "code",
            title = "写代码",
            desc = "工作区里写并跑脚本",
            prompt = "在工作区里写并跑一个小工具：先 ls 看现有结构，再 write_file 落代码，然后 exec_shell 跑起来，把真实输出贴给我；" +
                "报错就贴原文、定位到具体行，不要猜；产物和中间文件都留在工作区里。\n" +
                "需求："
        )
    )

    fun byId(id: String): FeatureCard? = all.firstOrNull { it.id == id }
} //首页能力卡清单