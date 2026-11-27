/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 03:20:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * 自检小工具：打印升级检测结果（注册表里能不能找到已装的 Baicodex）。
 */

package b.ai.setup;

public final class DetectTest {
    public static void main(String[] args) throws Exception {
        // 先直接看 capture 的原始输出，好区分是注册表读取问题还是判断问题
        String key = "HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Baicodex";
        String script =
                "$ErrorActionPreference='SilentlyContinue';"
                        + "$k='" + key + "';"
                        + "if(Test-Path $k){$p=Get-ItemProperty $k;"
                        + "$i=$p.InstallLocation;$v=$p.DisplayVersion;"
                        + "if($i){Write-Output ($i+'|'+$v)}}";
        java.lang.reflect.Method cap = Sys.class.getDeclaredMethod("capture", String.class);
        cap.setAccessible(true);
        System.out.println("RAW=[" + cap.invoke(null, script) + "]");

        Sys.Existing found = Sys.detectExisting();
        if (found == null) {
            System.out.println("NO-EXISTING");
        } else {
            System.out.println("FOUND " + found.location + " | v" + found.version
                    + " | allUsers=" + found.allUsers);
        }
    }
}
