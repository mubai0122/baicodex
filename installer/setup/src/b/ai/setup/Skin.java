/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 03:20:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 安装向导的视觉层：配色、字体、圆角绘制，以及四种可交互控件。
 * 全部 Graphics2D 手绘，不依赖任何 Look&Feel——安装器只有这一套皮肤，
 * 和主程序亮色主题（#4C6B9E + 白 + 淡灰）保持一致。
 */

package b.ai.setup;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GradientPaint;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/** 配色 / 字体 / 绘制工具 */
final class Skin {
    static final Color PRIMARY = new Color(0x4C, 0x6B, 0x9E);
    static final Color PRIMARY_SOFT = new Color(0xE8, 0xEE, 0xF8);
    static final Color SIDE_TOP = new Color(0x46, 0x61, 0x8F);
    static final Color SIDE_BOTTOM = new Color(0x22, 0x2E, 0x42);
    static final Color TEXT = new Color(0x1F, 0x24, 0x2C);
    static final Color MUTED = new Color(0x8A, 0x91, 0x9C);
    static final Color LINE = new Color(0xE6, 0xEA, 0xF0);
    static final Color SOFT = new Color(0xF5, 0xF7, 0xFA);
    static final Color WHITE = Color.WHITE;
    static final Color DANGER = new Color(0xB3, 0x26, 0x1E);
    static final Color OK = new Color(0x2E, 0x7D, 0x52);

    private static final String FAMILY = pickFamily();

    private static String pickFamily() {
        String[] candidates = {"Microsoft YaHei UI", "Microsoft YaHei", "Segoe UI", "Dialog"};
        for (String name : candidates) {
            for (String installed : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames()) {
                if (installed.equalsIgnoreCase(name)) {
                    return name;
                }
            }
        }
        return "Dialog";
    }

    static Font font(int style, float size) {
        return new Font(FAMILY, style, 12).deriveFont(style, size);
    }

    static Graphics2D aa(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    static void fill(Graphics2D g, Paint paint, double x, double y, double w, double h, double r) {
        g.setPaint(paint);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, r, r));
    }

    static void stroke(Graphics2D g, Color color, double width, double x, double y, double w, double h, double r) {
        g.setColor(color);
        g.setStroke(new BasicStroke((float) width));
        g.draw(new RoundRectangle2D.Double(x + width / 2, y + width / 2, w - width, h - width, r, r));
    }

    static void text(Graphics2D g, String s, Font f, Color c, double x, double baseline) {
        g.setFont(f);
        g.setColor(c);
        g.drawString(s, (float) x, (float) baseline);
    }

    /** 从顶部往下画多行（自动折行），返回结束的 y */
    static int paragraph(Graphics2D g, String s, Font f, Color c, double x, double y, double maxWidth, double lineHeight) {
        g.setFont(f);
        g.setColor(c);
        var metrics = g.getFontMetrics();
        double cursorX = x;
        double cursorY = y;
        for (String rawLine : s.split("\n", -1)) {
            if (rawLine.isEmpty()) {
                cursorY += lineHeight;
                cursorX = x;
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (int i = 0; i < rawLine.length(); i++) {
                char ch = rawLine.charAt(i);
                String probe = line.toString() + ch;
                if (metrics.stringWidth(probe) > maxWidth && line.length() > 0) {
                    g.drawString(line.toString(), (float) cursorX, (float) cursorY);
                    cursorY += lineHeight;
                    line.setLength(0);
                    cursorX = x;
                }
                line.append(ch);
            }
            if (line.length() > 0) {
                g.drawString(line.toString(), (float) cursorX, (float) cursorY);
                cursorY += lineHeight;
                cursorX = x;
            }
        }
        return (int) Math.round(cursorY);
    }

    static void check(Graphics2D g, double x, double y, double size, boolean on) {
        if (on) {
            fill(g, PRIMARY, x, y, size, size, 4);
            g.setColor(WHITE);
            g.setStroke(new BasicStroke((float) (size * 0.16), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            Path2D p = new Path2D.Double();
            p.moveTo(x + size * 0.26, y + size * 0.53);
            p.lineTo(x + size * 0.44, y + size * 0.71);
            p.lineTo(x + size * 0.75, y + size * 0.31);
            g.draw(p);
        } else {
            stroke(g, new Color(0xC5, 0xCD, 0xD8), 1.4, x, y, size, size, 4);
        }
    }

    /** 品牌图标：圆角主色块 + 白色终端提示符（与主程序 AppIcon 同一套画法） */
    static void logo(Graphics2D g, double x, double y, double size, Color bg, Color fg) {
        fill(g, bg, x, y, size, size, size * 0.26);
        double pad = size * 0.22;
        double cx = x + pad;
        double cy = y + pad;
        double w = size - pad * 2;
        g.setColor(fg);
        g.setStroke(new BasicStroke((float) (size * 0.1), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D chevron = new Path2D.Double();
        chevron.moveTo(cx + w * 0.06, cy + w * 0.26);
        chevron.lineTo(cx + w * 0.38, cy + w * 0.5);
        chevron.lineTo(cx + w * 0.06, cy + w * 0.74);
        g.draw(chevron);
        Path2D bar = new Path2D.Double();
        bar.moveTo(cx + w * 0.52, cy + w * 0.74);
        bar.lineTo(cx + w * 0.96, cy + w * 0.74);
        g.draw(bar);
    }

    static void circleIcon(Graphics2D g, double cx, double cy, double r, Color bg) {
        g.setColor(bg);
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    private Skin() {
    }
}

/** 可交互控件基类：自己管命中、hover、点击 */
abstract class Node {
    Rectangle bounds = new Rectangle();
    boolean hover;
    boolean enabled = true;
    boolean visible = true;

    abstract void paint(Graphics2D g);

    void onClick() {
    }
}

/** 实心 / 描边按钮 */
class Btn extends Node {
    String label;
    boolean primary;
    Runnable action;

    Btn(String label, boolean primary, Runnable action) {
        this.label = label;
        this.primary = primary;
        this.action = action;
    }

    @Override
    void paint(Graphics2D g) {
        Color bg;
        Color fg;
        if (!enabled) {
            bg = new Color(0xE9, 0xED, 0xF3);
            fg = new Color(0xA9, 0xB1, 0xBD);
        } else if (primary) {
            bg = hover ? Skin.PRIMARY.darker() : Skin.PRIMARY;
            fg = Color.WHITE;
        } else {
            bg = hover ? new Color(0xEC, 0xEF, 0xF4) : Skin.SOFT;
            fg = Skin.TEXT;
        }
        Skin.fill(g, bg, bounds.x, bounds.y, bounds.width, bounds.height, 9);
        Font f = Skin.font(Font.PLAIN, 12.5f);
        g.setFont(f);
        int textWidth = g.getFontMetrics().stringWidth(label);
        Skin.text(
                g,
                label,
                f,
                fg,
                bounds.x + (bounds.width - textWidth) / 2.0,
                bounds.y + bounds.height / 2.0 + 4.4);
    }

    @Override
    void onClick() {
        if (enabled && action != null) {
            action.run();
        }
    }
}

/** 大号单选卡：安装范围用（所有用户 / 仅当前用户） */
class OptionCard extends Node {
    String title;
    String desc;
    String badge;
    boolean selected;
    Runnable action;

    OptionCard(String title, String desc, String badge, Runnable action) {
        this.title = title;
        this.desc = desc;
        this.badge = badge;
        this.action = action;
    }

    @Override
    void paint(Graphics2D g) {
        Color fillColor = selected ? new Color(0xF2, 0xF6, 0xFC) : Skin.WHITE;
        if (hover && !selected) {
            fillColor = new Color(0xFA, 0xFB, 0xFD);
        }
        Skin.fill(g, fillColor, bounds.x, bounds.y, bounds.width, bounds.height, 12);
        Skin.stroke(
                g,
                selected ? Skin.PRIMARY : new Color(0xD8, 0xDF, 0xE8),
                selected ? 1.6 : 1.0,
                bounds.x,
                bounds.y,
                bounds.width,
                bounds.height,
                12);

        double dotX = bounds.x + 18;
        double dotY = bounds.y + 20;
        g.setColor(selected ? Skin.PRIMARY : new Color(0xCB, 0xD3, 0xDE));
        g.fill(new Ellipse2D.Double(dotX, dotY, 13, 13));
        if (selected) {
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Double(dotX + 4, dotY + 4, 5, 5));
        }

        double textX = dotX + 24;
        Skin.text(g, title, Skin.font(Font.PLAIN, 13.5f), Skin.TEXT, textX, dotY + 11);
        if (badge != null) {
            Font bf = Skin.font(Font.PLAIN, 10f);
            g.setFont(bf);
            int bw = g.getFontMetrics().stringWidth(badge) + 12;
            double bx = bounds.x + bounds.width - bw - 14;
            Skin.fill(g, new Color(0xEF, 0xF2, 0xF7), bx, dotY - 1, bw, 16, 8);
            Skin.text(g, badge, bf, Skin.MUTED, bx + 6, dotY + 10.6);
        }
        Skin.paragraph(
                g,
                desc,
                Skin.font(Font.PLAIN, 11f),
                Skin.MUTED,
                textX,
                dotY + 30,
                bounds.width - (textX - bounds.x) - 18,
                16);
    }

    @Override
    void onClick() {
        if (action != null) {
            action.run();
        }
    }
}

/** 复选项：一行标题 + 说明 + 右侧方框 */
class CheckRow extends Node {
    String title;
    String desc;
    boolean checked;
    Runnable action;

    CheckRow(String title, String desc, boolean checked, Runnable action) {
        this.title = title;
        this.desc = desc;
        this.checked = checked;
        this.action = action;
    }

    @Override
    void paint(Graphics2D g) {
        if (hover) {
            Skin.fill(g, Skin.SOFT, bounds.x - 8, bounds.y - 2, bounds.width + 16, bounds.height + 4, 8);
        }
        Skin.text(g, title, Skin.font(Font.PLAIN, 12.5f), Skin.TEXT, bounds.x, bounds.y + 15);
        if (desc != null) {
            Skin.text(g, desc, Skin.font(Font.PLAIN, 10.5f), Skin.MUTED, bounds.x, bounds.y + 31);
        }
        Skin.check(g, bounds.x + bounds.width - 17, bounds.y + 7, 15, checked);
    }

    @Override
    void onClick() {
        if (action != null) {
            action.run();
        }
    }
}

/** 可点的文字链（侧栏步骤、跳过等） */
class LinkNode extends Node {
    String label;
    boolean strong;
    Runnable action;

    LinkNode(String label, boolean strong, Runnable action) {
        this.label = label;
        this.strong = strong;
        this.action = action;
    }

    @Override
    void paint(Graphics2D g) {
        Skin.text(
                g,
                label,
                Skin.font(strong ? Font.PLAIN : Font.PLAIN, 11.5f),
                hover ? Skin.PRIMARY : (strong ? Skin.TEXT : Skin.MUTED),
                bounds.x,
                bounds.y + 14);
        if (hover) {
            g.setColor(Skin.PRIMARY);
            g.setStroke(new BasicStroke(1f));
            g.drawLine(bounds.x, bounds.y + 17, bounds.x + g.getFontMetrics().stringWidth(label), bounds.y + 17);
        }
    }

    @Override
    void onClick() {
        if (action != null) {
            action.run();
        }
    }
}

/** 纯装饰块（分隔线、提示条、进度条、静态卡片），不参与交互 */
class PaintNode extends Node {
    interface Painter {
        void paint(Graphics2D g, Rectangle bounds);
    }

    private final Painter painter;

    PaintNode(Painter painter) {
        this.painter = painter;
    }

    @Override
    void paint(Graphics2D g) {
        painter.paint(g, bounds);
    }
}

/**
 * 整个向导的绘制画布：左侧渐变步骤栏 + 右侧内容 + 底部按钮条。
 * 所有可点元素都是 Node，统一在一个列表里做命中测试，不用 Swing 的布局管理器。
 */
class Canvas extends JPanel {
    static final int SIDE = 300;
    static final int FOOT_BAR = 78;

    String title = "";
    String subtitle = "";
    String footnote = "";
    List<String> steps = List.of();
    int stepIndex = 0;
    boolean closeEnabled = true;
    Runnable onClose = () -> {};
    final List<Node> nodes = new ArrayList<>();
    private Node hot;
    private java.awt.Point dragAnchor;

    Canvas() {
        setOpaque(true);
        setBackground(Skin.WHITE);
        setLayout(null);
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                Node hit = pick(e.getPoint());
                if (hit != hot) {
                    if (hot != null) {
                        hot.hover = false;
                    }
                    hot = hit;
                    if (hot != null) {
                        hot.hover = true;
                    }
                    repaint();
                }
                setCursor(java.awt.Cursor.getPredefinedCursor(
                        hit != null && hit.enabled ? java.awt.Cursor.HAND_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                dragAnchor = pick(e.getPoint()) == null ? e.getPoint() : null;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (dragAnchor == null) {
                    return;
                }
                java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(Canvas.this);
                if (window != null) {
                    java.awt.Point p = e.getLocationOnScreen();
                    window.setLocation(p.x - dragAnchor.x, p.y - dragAnchor.y);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                dragAnchor = null;
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (hot != null) {
                    hot.hover = false;
                    hot = null;
                    repaint();
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                Node hit = pick(e.getPoint());
                if (hit != null && hit.enabled && hit.visible) {
                    hit.onClick();
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    private Node pick(Point p) {
        for (int i = nodes.size() - 1; i >= 0; i--) {
            Node node = nodes.get(i);
            if (node.visible && node.enabled && node.bounds.contains(p)) {
                return node;
            }
        }
        return null;
    }

    void reset(String title, String subtitle) {
        this.title = title;
        this.subtitle = subtitle == null ? "" : subtitle;
        this.footnote = "";
        this.nodes.clear();
        this.hot = null;
        removeAll();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = Skin.aa((Graphics2D) graphics.create());
        try {
            int w = getWidth();
            int h = getHeight();

            // ---- 左侧步骤栏
            g.setPaint(new GradientPaint(0, 0, Skin.SIDE_TOP, 0, h, Skin.SIDE_BOTTOM));
            g.fillRect(0, 0, SIDE, h);
            Skin.logo(g, 34, 30, 34, Skin.WHITE, Skin.SIDE_TOP);
            Skin.text(g, "Baicodex", Skin.font(Font.PLAIN, 18f), Skin.WHITE, 80, 54);
            Skin.text(g, "Windows 桌面端安装程序", Skin.font(Font.PLAIN, 10f), new Color(255, 255, 255, 145), 80, 72);

            int sy = 148;
            for (int i = 0; i < steps.size(); i++) {
                boolean active = i == stepIndex;
                boolean passed = i < stepIndex;
                Color dotFill = active ? Skin.WHITE : new Color(255, 255, 255, passed ? 60 : 0);
                Color ring = new Color(255, 255, 255, active ? 255 : 110);
                double cx = 44;
                double cy = sy + i * 38;
                if (active) {
                    g.setColor(dotFill);
                    g.fill(new Ellipse2D.Double(cx - 11, cy - 11, 22, 22));
                } else {
                    g.setColor(ring);
                    g.setStroke(new BasicStroke(1.2f));
                    g.draw(new Ellipse2D.Double(cx - 11, cy - 11, 22, 22));
                }
                if (passed) {
                    g.setColor(new Color(255, 255, 255, 200));
                    g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    Path2D tick = new Path2D.Double();
                    tick.moveTo(cx - 4, cy);
                    tick.lineTo(cx - 1, cy + 3.4);
                    tick.lineTo(cx + 4.4, cy - 3.4);
                    g.draw(tick);
                } else {
                    Skin.text(
                            g,
                            String.valueOf(i + 1),
                            Skin.font(Font.PLAIN, 10f),
                            active ? Skin.SIDE_TOP : new Color(255, 255, 255, 170),
                            cx - 3,
                            cy + 3.6);
                }
                Skin.text(
                        g,
                        steps.get(i),
                        Skin.font(Font.PLAIN, 12.5f),
                        active ? Skin.WHITE : new Color(255, 255, 255, 140),
                        68,
                        cy + 4.4);
            }

            Skin.text(g, "© 2026 沐白/Baix · AGPL-3.0", Skin.font(Font.PLAIN, 9.5f), new Color(255, 255, 255, 110), 34, h - 26);

            // ---- 右侧内容
            g.setColor(Skin.WHITE);
            g.fillRect(SIDE, 0, w - SIDE, h);
            int pad = SIDE + 44;
            Skin.text(g, title, Skin.font(Font.PLAIN, 21f), Skin.TEXT, pad, 58);
            if (!subtitle.isEmpty()) {
                Skin.paragraph(g, subtitle, Skin.font(Font.PLAIN, 11.5f), Skin.MUTED, pad, 80, w - pad - 44, 17);
            }

            // ---- 底部按钮条（必须先于 nodes 画，否则会盖住按钮）
            g.setColor(Skin.LINE);
            g.fillRect(SIDE, h - FOOT_BAR, w - SIDE, 1);
            g.setColor(Skin.WHITE);
            g.fillRect(SIDE, h - FOOT_BAR + 1, w - SIDE, FOOT_BAR);

            if (!footnote.isEmpty()) {
                Skin.text(g, footnote, Skin.font(Font.PLAIN, 10f), Skin.MUTED, pad, h - FOOT_BAR / 2.0 + 4);
            }

            for (Node node : nodes) {
                if (node.visible) {
                    node.paint(g);
                }
            }

            // 关闭按钮（右上角）
            if (closeEnabled) {
                Color cc = new Color(0x9A, 0xA2, 0xAE);
                g.setColor(cc);
                g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                double bx = w - 32;
                double by = 26;
                g.draw(new java.awt.geom.Line2D.Double(bx - 5, by - 5, bx + 5, by + 5));
                g.draw(new java.awt.geom.Line2D.Double(bx + 5, by - 5, bx - 5, by + 5));
            }
        } finally {
            g.dispose();
        }
    }
}
