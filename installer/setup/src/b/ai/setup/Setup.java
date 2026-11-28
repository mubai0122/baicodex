/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 03:38:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 安装向导：欢迎 → 安装范围（所有人 / 仅我）→ 位置 → 选项 → 进度 → 完成。
 *
 * 由自解压头解到临时目录后启动，靠 --sfx <安装包路径> 找到尾部的大块数据；
 * 选「所有人」而当前不是管理员时，用 UAC 重新拉起自己（带 --elevated）。
 */

package b.ai.setup;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public final class Setup extends JFrame {

    static final String VERSION = "0.1.0";
    static final String APP_NAME = "Baicodex";
    static final int WIDTH = 940;
    static final int HEIGHT = 606;
    static final int PAD = Canvas.SIDE + 44;

    private final Canvas canvas = new Canvas();
    private final Path sfx;

    private boolean allUsers;
    private Path target;
    private boolean desktopShortcut = true;
    private boolean startMenu = true;
    private boolean startup;
    private boolean launchAfter = true;
    private final boolean elevated;
    private final boolean demo;
    /** 上一次安装的线索；null = 全新安装，非 null = 升级模式 */
    private final Sys.Existing existing;
    private volatile boolean admin;

    private int page;

    private JTextField pathField;
    private Timer progressTimer;

    private volatile long progressDone;
    private volatile long progressTotal;
    private volatile String progressName = "";
    private volatile String phase = "";
    private volatile boolean finished;
    private volatile boolean failed;
    private volatile String failure = "";

    private Setup(Path sfx, boolean elevated, boolean demo, Sys.Existing existing) {
        this.sfx = sfx;
        this.elevated = elevated;
        this.demo = demo;
        this.existing = existing;
        // 升级模式沿用上次安装的范围与位置；提权重启强制所有用户
        this.allUsers = elevated || (existing != null && existing.allUsers);
        this.admin = elevated;

        setTitle(APP_NAME + " 安装程序");
        setUndecorated(true);
        setResizable(false);
        setSize(WIDTH, HEIGHT);
        setContentPane(canvas);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);

        try {
            setBackground(new Color(0, 0, 0, 0));
            setShape(new RoundRectangle2D.Double(0, 0, WIDTH, HEIGHT, 16, 16));
        } catch (Throwable ignored) {
            // 老系统不支持无边框圆角，退化成直角窗口，不影响功能
        }

        canvas.steps = isUpgrade()
                ? List.of("欢迎", "升级确认", "原地更新", "完成")
                : List.of("欢迎", "安装范围", "安装位置", "准备安装");
        canvas.onClose = this::close;

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "baicodex-close");
        getRootPane().getActionMap().put("baicodex-close", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                close();
            }
        });

        target = Sys.defaultDir(allUsers);
        if (existing != null) {
            // 升级：装回上一次的位置，不问用户
            target = existing.location;
        }

        // 权限探测放后台：绘制线程里调 PowerShell 会卡住界面
        CompletableFuture.runAsync(() -> admin = Sys.isAdmin());

        showPage(elevated ? 3 : 0);
    }

    // ------------------------------------------------------------------ 启动

    public static void main(String[] args) {
        // 任何线程的未捕获异常都弹窗说明，杜绝"闪退没提示"
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            String detail = error.toString();
            if (error.getCause() != null) {
                detail += "\n  由 " + error.getCause();
            }
            JOptionPane.showMessageDialog(
                    null,
                    "安装程序遇到错误，即将退出：\n\n" + detail,
                    "Baicodex 安装程序",
                    JOptionPane.ERROR_MESSAGE);
            System.exit(2);
        });

        Path sfx = null;
        boolean elevated = false;
        boolean demo = false;
        Path forcedDir = null;
        Path shotDir = null;
        boolean silent = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--sfx".equals(arg) && i + 1 < args.length) {
                sfx = Path.of(args[++i]);
            } else if ("--elevated".equals(arg)) {
                elevated = true;
            } else if ("--demo".equals(arg)) {
                demo = true;
            } else if (arg.startsWith("--dir=")) {
                forcedDir = Path.of(arg.substring("--dir=".length()));
            } else if (arg.startsWith("--shot=")) {
                shotDir = Path.of(arg.substring("--shot=".length()));
            } else if ("/S".equalsIgnoreCase(arg) || "--silent".equalsIgnoreCase(arg)) {
                silent = true;
            } else if (arg.startsWith("--all-users")) {
                elevated = true;
            }
        }

        final Path sfxPath = sfx;
        final boolean elevatedFlag = elevated;
        final boolean demoFlag = demo;
        final Path dir = forcedDir;
        final Path shots = shotDir;
        final boolean silentFlag = silent;

        if (silentFlag) {
            // 静默安装：不建界面，跑完直接给退出码（企业分发 / 脚本用）；
            // 也会做升级检测，沿用已有位置（除非 --dir 显式指定）
            final Sys.Existing found = Sys.detectExisting();
            Setup setup = new Setup(sfxPath, elevatedFlag, false, found);
            if (dir != null) {
                setup.target = dir;
            } else if (found != null) {
                setup.target = found.location;
            }
            setup.install();
            System.exit(setup.finished ? 0 : 1);
            return;
        }

        // 升级检测放在出窗口之前：结果决定走安装流程还是升级流程
        final Sys.Existing found = Sys.detectExisting();

        SwingUtilities.invokeLater(() -> {
            Setup setup = new Setup(sfxPath, elevatedFlag, demoFlag, found);
            if (dir != null) {
                setup.target = dir;
                setup.showPage(setup.page);
            }
            setup.setVisible(true);
            if (shots != null) {
                try {
                    setup.shootAll(shots);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                System.exit(0);
            }
        });
    }

    /** 出图用：把每一页画到 PNG，方便核对版式（不参与正常安装流程） */
    private void shootAll(Path dir) throws Exception {
        Files.createDirectories(dir);
        canvas.setSize(WIDTH, HEIGHT);
        canvas.doLayout();
        for (int i = 0; i <= 3; i++) {
            showPage(i);
            canvas.doLayout();
            capture(dir.resolve("page-" + i + ".png"));
        }

        phase = "正在解压程序文件…";
        progressDone = 420;
        progressTotal = 1000;
        progressName = "app/skiko-awt-runtime-windows-x64-0.9.22.2.jar";
        showPage(4);
        canvas.doLayout();
        capture(dir.resolve("page-4-progress.png"));

        finished = true;
        failed = false;
        onInstallSettled();
        canvas.doLayout();
        capture(dir.resolve("page-5-done.png"));

        finished = false;
        failed = true;
        failure = "写注册表失败（退出码 1）。选「所有人」安装需要管理员权限。";
        showPage(4);
        canvas.doLayout();
        capture(dir.resolve("page-6-failed.png"));
    }

    private void capture(Path file) throws IOException {
        var image = new java.awt.image.BufferedImage(
                canvas.getWidth(), canvas.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        canvas.paint(g);
        g.dispose();
        javax.imageio.ImageIO.write(image, "png", file.toFile());
    }

    // ------------------------------------------------------------------ 页面切换

    private void showPage(int index) {
        page = index;
        // 升级流程跳过了「范围 / 路径」两页，步骤条要对上号
        canvas.stepIndex = isUpgrade()
                ? (index == 0 ? 0 : index >= 4 ? 3 : 2)
                : Math.min(index, 3);
        pathField = null;
        switch (index) {
            case 0 -> buildWelcome();
            case 1 -> buildScope();
            case 2 -> buildPath();
            case 3 -> buildOptions();
            default -> buildProgress();
        }
        canvas.revalidate();
        canvas.repaint();
    }

    private void close() {
        if (page == 4 && !finished && !failed) {
            return; // 正在解压，别让用户误关
        }
        dispose();
        System.exit(0);
    }

    // ------------------------------------------------------------------ 通用控件

    /** 右上角的关闭热区（✕ 由 Canvas 自己画） */
    private void addClose() {
        Btn close = new Btn("", false, this::close) {
            @Override
            void paint(Graphics2D g) {
            }
        };
        close.bounds = new Rectangle(WIDTH - 54, 8, 44, 36);
        canvas.nodes.add(close);
    }

    /** 底部按钮从右往左排，返回加进去的按钮（方便后续改状态） */
    private Btn[] footer(Btn... buttons) {
        int right = WIDTH - 36;
        int y = HEIGHT - 56;
        for (int i = buttons.length - 1; i >= 0; i--) {
            Btn button = buttons[i];
            int width = Math.max(96, buttonWidth(button.label));
            button.bounds = new Rectangle(right - width, y, width, 34);
            canvas.nodes.add(button);
            right -= width + 10;
        }
        return buttons;
    }

    private int buttonWidth(String label) {
        return canvas.getFontMetrics(Skin.font(Font.PLAIN, 12.5f)).stringWidth(label) + 44;
    }

    private Btn primary(String label, Runnable action) {
        return new Btn(label, true, action);
    }

    private Btn ghost(String label, Runnable action) {
        return new Btn(label, false, action);
    }

    private PaintNode paragraph(int x, int y, int width, int height, String content, Font font, Color color) {
        PaintNode node = new PaintNode((g, b) -> Skin.paragraph(g, content, font, color, b.x, b.y + 14, width, 20));
        node.bounds = new Rectangle(x, y, width, height);
        return node;
    }

    // ------------------------------------------------------------------ 页 0：欢迎

    private boolean isUpgrade() {
        return existing != null;
    }

    private String oldVersionText() {
        String v = existing == null ? "" : existing.version;
        return v.isBlank() ? "未知版本" : v;
    }

    private void buildWelcome() {
        boolean upgrade = isUpgrade();
        canvas.reset(
                upgrade ? "更新 " + APP_NAME : "安装 " + APP_NAME,
                upgrade
                        ? ("检测到已安装 " + oldVersionText() + " · 将更新到 " + VERSION
                                + "，装在哪、给谁用都沿用上次的选择")
                        : "桌面端 Agent 工作台 · 版本 " + VERSION);
        canvas.footnote = "AGPL-3.0 开源 · github.com/mubai0122/Baicodex";

        if (upgrade) {
            canvas.nodes.add(paragraph(PAD, 116, 552, 40,
                    "这次更新只重写程序文件，不问安装范围和路径；"
                            + "模型连接、沙箱档位、工具确认策略全部照旧。",
                    Skin.font(Font.PLAIN, 12.5f), new Color(0x4A, 0x51, 0x5C)));

            int y = 190;
            String[][] rows = {
                {"01", "原地更新", "程序文件就地覆盖：" + existing.location.toAbsolutePath().toString()},
                {"02", "数据不受影响", "会话、设置与工作区在 %APPDATA%\\Baicodex，原样保留"},
                {"03", "无需重新配置", "启动后直接就是熟悉的界面，不用再走引导"},
            };
            for (String[] row : rows) {
                canvas.nodes.add(featureRow(y, row[0], row[1], row[2]));
                y += 66;
            }
        } else {
            canvas.nodes.add(paragraph(PAD, 116, 552, 60,
                    "Baicodex 把模型、文件工具和命令执行放在同一张桌面里：给它一个工作区，"
                            + "它会自己读文件、改文件、跑命令，每一步都在你眼前发生。",
                    Skin.font(Font.PLAIN, 12.5f), new Color(0x4A, 0x51, 0x5C)));

            int y = 196;
            String[][] features = {
                {"01", "本地文件沙箱", "文件工具被锁在你指定的工作区里，越界路径与系统目录一律拒绝"},
                {"02", "工具调用要你确认", "读文件不打扰；写文件、删东西、跑命令先弹一张确认卡"},
                {"03", "装完就能用", "首次启动有四屏引导，填一条 OpenAI 兼容连接即可开始"},
            };
            for (String[] feature : features) {
                canvas.nodes.add(featureRow(y, feature[0], feature[1], feature[2]));
                y += 66;
            }
        }

        footer(
                ghost("取消", this::close),
                primary(upgrade ? "开始更新" : "下一步", () -> showPage(isUpgrade() ? 3 : 1)));
        addClose();
    }

    private Node featureRow(int y, String index, String title, String desc) {
        PaintNode row = new PaintNode((g, b) -> {
            Skin.fill(g, new Color(0xF2, 0xF6, 0xFC), b.x, b.y, 34, 34, 10);
            Skin.text(g, index, Skin.font(Font.PLAIN, 11f), Skin.PRIMARY, b.x + 10, b.y + 22);
            Skin.text(g, title, Skin.font(Font.PLAIN, 13f), Skin.TEXT, b.x + 48, b.y + 14);
            Skin.paragraph(g, desc, Skin.font(Font.PLAIN, 11f), Skin.MUTED, b.x + 48, b.y + 32, 500, 16);
        });
        row.bounds = new Rectangle(PAD, y, 560, 58);
        return row;
    }

    // ------------------------------------------------------------------ 页 1：安装范围

    private void buildScope() {
        canvas.reset("选择安装范围", "决定装到哪、谁能用；选「所有人」需要管理员权限");

        OptionCard everyone = new OptionCard(
                "为这台电脑上的所有用户安装",
                "装到 Program Files\\Baicodex，本机所有账户都能用。需要管理员权限（点下一步会弹 UAC）。",
                "推荐",
                () -> {
                    allUsers = true;
                    target = Sys.defaultDir(true);
                    showPage(1);
                });
        everyone.bounds = new Rectangle(PAD, 118, 552, 104);
        everyone.selected = allUsers;
        canvas.nodes.add(everyone);

        OptionCard justMe = new OptionCard(
                "仅为我安装",
                "装到当前用户目录（%LOCALAPPDATA%\\Programs\\Baicodex），不需要管理员权限，也不影响其他账户。",
                null,
                () -> {
                    allUsers = false;
                    target = Sys.defaultDir(false);
                    showPage(1);
                });
        justMe.bounds = new Rectangle(PAD, 236, 552, 104);
        justMe.selected = !allUsers;
        canvas.nodes.add(justMe);

        canvas.nodes.add(new PaintNode((g, b) -> {
            String line;
            if (!allUsers) {
                line = "当前账户安装：只有你能看到快捷方式与卸载入口，其他账户不受影响。";
            } else if (admin) {
                line = "当前已是管理员，安装过程不会再弹权限窗口。";
            } else {
                line = "当前不是管理员：点「下一步」时 Windows 会弹出 UAC 授权窗口，允许后才继续。";
            }
            Skin.paragraph(g, line, Skin.font(Font.PLAIN, 11f), Skin.MUTED, b.x, b.y + 14, 540, 17);
        }) {
            {
                bounds = new Rectangle(PAD, 360, 552, 44);
            }
        });

        footer(
                ghost("上一步", () -> showPage(0)),
                primary("下一步", () -> {
                    if (allUsers && !elevated && !Sys.isAdminBlocking()) {
                        elevateAndContinue();
                    } else {
                        showPage(2);
                    }
                }));
        addClose();
    }

    private void elevateAndContinue() {
        // 后台线程里做：PowerShell 启动要 1~2 秒，在 EDT 上等会被当成卡死
        setVisible(false);
        Thread worker = new Thread(() -> {
            try {
                int code = Sys.relaunchElevated(List.of(
                        "--elevated",
                        "--sfx", sfx == null ? "" : sfx.toAbsolutePath().toString(),
                        "--dir=" + target.toAbsolutePath()));
                if (code == 0) {
                    // 提权实例已经把界面走完，这里才退出
                    System.exit(0);
                }
                // 没拿到权限（UAC 点了否 / 被策略拦下）：把窗口放回来，别闪退
                SwingUtilities.invokeAndWait(() -> {
                    setVisible(true);
                    toFront();
                    JOptionPane.showMessageDialog(
                            this,
                            "没有获得管理员权限（可能是 UAC 里点了「否」，或被安全软件拦下）。\n"
                                    + "可以继续用「仅为我安装」，不需要管理员权限。",
                            "权限不足",
                            JOptionPane.WARNING_MESSAGE);
                });
            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> {
                    setVisible(true);
                    toFront();
                    JOptionPane.showMessageDialog(
                            this,
                            "无法发起提权：" + e.getMessage() + "\n\n可以改选「仅为我安装」，不需要管理员权限。",
                            "权限不足",
                            JOptionPane.WARNING_MESSAGE);
                });
            }
        }, "baicodex-elevate");
        worker.setDaemon(true);
        worker.start();
    }

    // ------------------------------------------------------------------ 页 2：安装位置

    private void buildPath() {
        canvas.reset("安装位置", "装到一个空目录；已有同名程序会被覆盖");

        canvas.nodes.add(new PaintNode((g, b) ->
                Skin.text(g, "安装到", Skin.font(Font.PLAIN, 11.5f), Skin.MUTED, b.x, b.y + 12)) {
            {
                bounds = new Rectangle(PAD, 112, 200, 20);
            }
        });

        pathField = new JTextField(target.toAbsolutePath().toString());
        pathField.setBounds(PAD, 132, 452, 36);
        pathField.setFont(Skin.font(Font.PLAIN, 12f));
        pathField.setForeground(Skin.TEXT);
        pathField.setBackground(Skin.SOFT);
        pathField.setCaretColor(Skin.TEXT);
        pathField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Skin.LINE),
                BorderFactory.createEmptyBorder(0, 10, 0, 10)));
        canvas.add(pathField);

        Btn browse = new Btn("浏览…", false, () -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setDialogTitle("选择安装位置");
            Path parent = target.toAbsolutePath().getParent();
            if (parent != null && Files.exists(parent)) {
                chooser.setCurrentDirectory(parent.toFile());
            }
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                target = chooser.getSelectedFile().toPath().resolve(APP_NAME);
                pathField.setText(target.toAbsolutePath().toString());
                canvas.repaint();
            }
        });
        browse.bounds = new Rectangle(PAD + 464, 132, 88, 36);
        canvas.nodes.add(browse);

        canvas.nodes.add(new PaintNode((g, b) -> {
            Path current = currentTarget();
            int lineY = b.y + 14;
            Skin.paragraph(g, "需要约 250 MB 磁盘空间 · " + freeSpaceText(current),
                    Skin.font(Font.PLAIN, 11f), Skin.MUTED, b.x, lineY, 552, 17);
            if (Sys.looksInstalled(current)) {
                Skin.paragraph(g,
                        "这个位置已经有一份 Baicodex，继续会覆盖旧文件（会话与设置保留在 %APPDATA%\\Baicodex）。",
                        Skin.font(Font.PLAIN, 11f), new Color(0xB3, 0x7A, 0x1E), b.x, lineY + 24, 552, 17);
            }
        }) {
            {
                bounds = new Rectangle(PAD, 182, 552, 80);
            }
        });

        canvas.nodes.add(new PaintNode((g, b) -> {
            Skin.fill(g, Skin.SOFT, b.x, b.y, 552, 132, 12);
            Skin.text(g, "这个目录里会有", Skin.font(Font.PLAIN, 11f), Skin.TEXT, b.x + 18, b.y + 26);
            String[][] rows = {
                {"Baicodex.exe", "主程序，约 1 MB"},
                {"runtime\\", "精简过的 Java 运行时，约 120 MB"},
                {"app\\", "程序本体与依赖，约 130 MB"},
                {"uninstall.exe", "卸载入口（控制面板里也看得到）"},
            };
            int rowY = b.y + 50;
            for (String[] row : rows) {
                Skin.text(g, row[0], Skin.font(Font.PLAIN, 11f), Skin.PRIMARY, b.x + 18, rowY);
                Skin.text(g, row[1], Skin.font(Font.PLAIN, 10.5f), Skin.MUTED, b.x + 150, rowY);
                rowY += 21;
            }
        }) {
            {
                bounds = new Rectangle(PAD, 288, 552, 132);
            }
        });

        footer(
                ghost("上一步", () -> showPage(1)),
                primary("下一步", () -> {
                    String typed = pathField.getText().trim();
                    if (typed.isEmpty()) {
                        return;
                    }
                    try {
                        Path chosen = Path.of(typed);
                        if (chosen.getParent() == null) {
                            JOptionPane.showMessageDialog(this, "不要直接装在盘符根目录，请再建一层文件夹。",
                                    "路径无效", JOptionPane.WARNING_MESSAGE);
                            return;
                        }
                        Files.createDirectories(chosen);
                        target = chosen;
                        showPage(3);
                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(this, "这个路径不可用：" + e.getMessage(),
                                "路径无效", JOptionPane.WARNING_MESSAGE);
                    }
                }));
        addClose();
    }

    private Path currentTarget() {
        if (pathField != null) {
            try {
                String typed = pathField.getText().trim();
                if (!typed.isEmpty()) {
                    return Path.of(typed);
                }
            } catch (Exception ignored) {
                // 输入到一半的非法路径，退回默认值
            }
        }
        return target;
    }

    private String freeSpaceText(Path path) {
        try {
            Path root = path.toAbsolutePath().getRoot();
            var store = Files.getFileStore(root);
            long freeGb = store.getUsableSpace() / 1024 / 1024 / 1024;
            return "该分区剩余 " + freeGb + " GB";
        } catch (Exception e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ 页 3：选项

    private void buildOptions() {
        boolean upgrade = isUpgrade();
        canvas.reset(upgrade ? "确认更新" : "准备安装",
                upgrade
                        ? "程序文件就地覆盖；会话与设置不动"
                        : "确认一下要做什么，然后开始");

        PaintNode summary = new PaintNode((g, b) -> {
            Skin.fill(g, Skin.SOFT, b.x, b.y, 552, 116, 12);
            Skin.text(g, upgrade ? "即将更新到" : "即将安装到",
                    Skin.font(Font.PLAIN, 10.5f), Skin.MUTED, b.x + 18, b.y + 26);
            Skin.text(g, target.toAbsolutePath().toString(), Skin.font(Font.PLAIN, 12f), Skin.TEXT, b.x + 18, b.y + 48);
            Skin.text(g, allUsers ? "范围：本机所有用户" : "范围：仅当前用户",
                    Skin.font(Font.PLAIN, 10.5f), Skin.PRIMARY, b.x + 18, b.y + 74);
            Skin.text(g,
                    upgrade
                            ? "版本 " + oldVersionText() + " → " + VERSION + " · 设置与会话保留"
                            : "版本 " + VERSION + " · AGPL-3.0 · 约 250 MB",
                    Skin.font(Font.PLAIN, 10.5f), Skin.MUTED, b.x + 18, b.y + 96);
        });
        summary.bounds = new Rectangle(PAD, 104, 552, 116);
        canvas.nodes.add(summary);

        int y = 244;
        canvas.nodes.add(check(y, "创建桌面快捷方式", null, desktopShortcut, () -> {
            desktopShortcut = !desktopShortcut;
            showPage(3);
        }));
        canvas.nodes.add(check(y + 44, "添加到开始菜单", null, startMenu, () -> {
            startMenu = !startMenu;
            showPage(3);
        }));
        canvas.nodes.add(check(y + 88, "开机时自动启动", "在后台待命，需要时从托盘唤出", startup, () -> {
            startup = !startup;
            showPage(3);
        }));
        canvas.nodes.add(check(y + 132, "安装完成后启动 Baicodex", null, launchAfter, () -> {
            launchAfter = !launchAfter;
            showPage(3);
        }));

        // 升级也可能需要权限：上次装在 Program Files，这次就得提权
        footer(
                ghost("上一步", () -> showPage(isUpgrade() ? 0 : 2)),
                primary(upgrade ? "开始更新" : "安装", () -> {
                    if (allUsers && !elevated && !Sys.isAdminBlocking()) {
                        elevateAndContinue();
                        return;
                    }
                    startInstall();
                }));
        addClose();
    }

    private CheckRow check(int y, String title, String desc, boolean checked, Runnable action) {
        CheckRow row = new CheckRow(title, desc, checked, action);
        row.bounds = new Rectangle(PAD, y, 552, 40);
        return row;
    }

    // ------------------------------------------------------------------ 页 4：进度 / 完成

    private void buildProgress() {
        canvas.reset(isUpgrade() ? "正在更新" : "正在安装",
                isUpgrade()
                        ? "覆盖程序文件，再刷新卸载信息与快捷方式"
                        : "把程序文件写到目标目录，再登记卸载信息与快捷方式");
        canvas.closeEnabled = false;

        PaintNode painter = new PaintNode((g, b) -> {
            int barY = b.y + 72;
            int barW = 552;
            if (failed) {
                Skin.fill(g, new Color(0xFB, 0xEC, 0xEA), b.x, barY - 62, barW, 132, 12);
                Skin.text(g, "安装没有完成", Skin.font(Font.PLAIN, 13.5f), Skin.DANGER, b.x + 18, barY - 28);
                Skin.paragraph(g, failure, Skin.font(Font.PLAIN, 11f), new Color(0x6E, 0x45, 0x40),
                        b.x + 18, barY - 6, barW - 36, 17);
                return;
            }
            if (finished) {
                Skin.circleIcon(g, b.x + 26, barY - 24, 18, new Color(0xE4, 0xF3, 0xEA));
                g.setColor(Skin.OK);
                g.setStroke(new java.awt.BasicStroke(2f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                Path2D tick = new Path2D.Double();
                tick.moveTo(b.x + 18, barY - 24);
                tick.lineTo(b.x + 24, barY - 18);
                tick.lineTo(b.x + 34, barY - 31);
                g.draw(tick);
                Skin.text(g, "安装完成", Skin.font(Font.PLAIN, 16f), Skin.TEXT, b.x + 56, barY - 18);
                Skin.paragraph(g, "程序已装在 " + target.toAbsolutePath(),
                        Skin.font(Font.PLAIN, 11f), Skin.MUTED, b.x + 56, barY, barW - 56, 17);
                return;
            }

            double ratio = progressTotal > 0 ? Math.min(1.0, (double) progressDone / progressTotal) : 0;
            Skin.fill(g, new Color(0xEC, 0xEF, 0xF4), b.x, barY, barW, 8, 4);
            Skin.fill(g, Skin.PRIMARY, b.x, barY, Math.max(8, barW * ratio), 8, 4);
            Skin.text(g, phase, Skin.font(Font.PLAIN, 12.5f), Skin.TEXT, b.x, barY - 18);
            Skin.text(g, String.format(Locale.ROOT, "%d%%", (int) Math.round(ratio * 100)),
                    Skin.font(Font.PLAIN, 12.5f), Skin.PRIMARY, b.x + barW - 40, barY - 18);
            String name = progressName;
            if (name.length() > 64) {
                name = "…" + name.substring(name.length() - 63);
            }
            Skin.text(g, name, Skin.font(Font.PLAIN, 10.5f), Skin.MUTED, b.x, barY + 26);
        });
        painter.bounds = new Rectangle(PAD, 116, 552, 230);
        canvas.nodes.add(painter);

        Btn[] buttons = footer(primary("请稍候…", () -> {
        }));
        buttons[0].enabled = false;

        progressTimer = new Timer(90, e -> {
            canvas.repaint();
            if (failed || finished) {
                stopTimer();
                onInstallSettled();
            }
        });
        progressTimer.start();
    }

    private void onInstallSettled() {
        if (failed) {
            canvas.nodes.removeIf(node -> node instanceof Btn);
            footer(ghost("关闭", () -> System.exit(1)), primary("重试", () -> {
                failed = false;
                failure = "";
                startInstall();
            }));
            canvas.repaint();
            return;
        }

        canvas.reset(isUpgrade() ? "更新完成" : "安装完成", "最后一步了");
        canvas.closeEnabled = true;
        canvas.steps = isUpgrade()
                ? List.of("欢迎", "升级确认", "原地更新", "完成")
                : List.of("欢迎", "安装范围", "安装位置", "完成");
        canvas.stepIndex = 3;
        canvas.nodes.add(doneCard());
        footer(primary("完成", () -> {
            if (launchAfter) {
                Sys.launch(target);
            }
            dispose();
            System.exit(0);
        }));
        addClose();
        canvas.revalidate();
        canvas.repaint();
    }

    private Node doneCard() {
        PaintNode card = new PaintNode((g, b) -> {
            Skin.circleIcon(g, b.x + 30, b.y + 34, 24, new Color(0xE4, 0xF3, 0xEA));
            g.setColor(Skin.OK);
            g.setStroke(new java.awt.BasicStroke(2.4f, java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
            Path2D tick = new Path2D.Double();
            tick.moveTo(b.x + 20, b.y + 34);
            tick.lineTo(b.x + 28, b.y + 42);
            tick.lineTo(b.x + 41, b.y + 25);
            g.draw(tick);
            Skin.text(g, isUpgrade() ? "Baicodex 已更新到 " + VERSION : "Baicodex 已经装好了",
                    Skin.font(Font.PLAIN, 16f), Skin.TEXT, b.x + 70, b.y + 30);
            Skin.paragraph(g, "位置：" + target.toAbsolutePath(),
                    Skin.font(Font.PLAIN, 11f), Skin.MUTED, b.x + 70, b.y + 50, 470, 17);
            Skin.paragraph(g,
                    isUpgrade()
                            ? "你的设置、会话与工作区没有动，直接启动就是熟悉的界面。"
                            : "第一次启动会有四屏引导：选沙箱档位、确认策略，再填一条模型连接。",
                    Skin.font(Font.PLAIN, 11f), Skin.MUTED, b.x + 70, b.y + 70, 470, 17);

            Skin.fill(g, Skin.SOFT, b.x, b.y + 110, 552, 92, 12);
            Skin.text(g, "卸载与更新", Skin.font(Font.PLAIN, 11.5f), Skin.TEXT, b.x + 18, b.y + 136);
            Skin.text(g, "在「设置 → 应用」里找 Baicodex，或运行安装目录下的 uninstall.exe。",
                    Skin.font(Font.PLAIN, 10.5f), Skin.MUTED, b.x + 18, b.y + 158);
            Skin.text(g, "会话、设置与工作区在 %APPDATA%\\Baicodex，卸载时可以选择一起删除。",
                    Skin.font(Font.PLAIN, 10.5f), Skin.MUTED, b.x + 18, b.y + 178);
        });
        card.bounds = new Rectangle(PAD, 104, 552, 214);
        return card;
    }

    private void stopTimer() {
        if (progressTimer != null) {
            progressTimer.stop();
            progressTimer = null;
        }
    }

    // ------------------------------------------------------------------ 安装

    private void startInstall() {
        finished = false;
        failed = false;
        failure = "";
        progressDone = 0;
        progressTotal = 1000;
        phase = "准备中…";
        progressName = "";
        showPage(4);
        if (demo) {
            runDemo();
            return;
        }
        Thread worker = new Thread(this::install, "baicodex-install");
        worker.setDaemon(true);
        worker.start();
    }

    /** --demo：只跑进度条，用来检查界面与动效，不动磁盘 */
    private void runDemo() {
        Thread worker = new Thread(() -> {
            String[] fake = {
                "runtime/bin/server/jvm.dll", "runtime/lib/modules", "app/baicodex-windows-0.1.0.jar",
                "app/skiko-awt-runtime-windows-x64-0.9.22.2.jar", "Baicodex.exe",
            };
            for (int i = 0; i < 1000; i += 7) {
                progressDone = i;
                progressName = fake[(i / 7) % fake.length];
                phase = "正在解压程序文件…";
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            progressDone = 1000;
            progressName = "";
            finished = true;
        }, "baicodex-demo");
        worker.setDaemon(true);
        worker.start();
    }

    private void install() {
        try {
            if (sfx == null || !Files.exists(sfx)) {
                throw new IOException("找不到安装包本体（缺少 --sfx 参数）。请直接双击安装包运行。");
            }
            Path dir = target.toAbsolutePath();

            phase = "准备目录…";
            Sys.stopRunningApp();
            Files.createDirectories(dir);

            phase = "正在解压程序文件…";
            Payload.extractApp(sfx, dir, (done, total, current) -> {
                progressName = current == null ? "" : current;
                progressDone = total > 0 ? Math.min(880, done * 880 / total) : 0;
            });

            phase = "写入卸载程序…";
            progressDone = 900;
            Payload.writeUninstaller(sfx, dir.resolve("uninstall.exe"));

            phase = "登记卸载信息…";
            progressDone = 940;
            Sys.cleanShortcuts(dir);
            int regCode = Sys.writeRegistry(allUsers, dir, VERSION, startup);
            if (regCode != 0) {
                throw new IOException("写注册表失败（退出码 " + regCode + "）。选「所有人」安装需要管理员权限。");
            }

            phase = "创建快捷方式…";
            progressDone = 970;
            Sys.createShortcuts(allUsers, dir, desktopShortcut, startMenu);

            progressDone = 1000;
            progressName = "";
            phase = "完成";
            finished = true;
        } catch (Throwable t) {
            failure = t.getMessage() == null ? t.toString() : t.getMessage();
            failed = true;
            // 静默安装没有界面，栈直接打到 stderr，方便脚本排错
            t.printStackTrace();
        }
    }

    static {
        System.setProperty("sun.java2d.uiScale.enabled", "true");
    }
}
