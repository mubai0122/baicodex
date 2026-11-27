/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 04:05:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * 小工具：截一张全屏图，用来核对安装向导与主界面的真实渲染效果。
 * 用法：java Shot.java <输出.png>
 */

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public final class Shot {
    public static void main(String[] args) throws Exception {
        String target = args.length > 0 ? args[0] : "shot.png";
        Rectangle screen = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage image = new Robot().createScreenCapture(screen);
        ImageIO.write(image, "png", new File(target));
        System.out.println("saved " + target + " " + screen.width + "x" + screen.height);
    }
}
