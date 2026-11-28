/*
 * Project:    Baicodex
 * Author:     by 沐白/Baix
 * License:    AGPL-3.0 (GNU Affero General Public License v3.0)
 * Date:       2026-11-27 03:30:00
 * Repository: https://github.com/mubai0122/Baicodex
 * Website:    https://agent.ai.baiccb.cn/
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * 系统集成：注册表、快捷方式、开机启动、UAC 提权。
 * 全部走 PowerShell（-EncodedCommand，不弹黑框）；实在没有 PowerShell 才退化到 reg.exe。
 */

package b.ai.setup;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class Sys {

    private static Boolean adminCache;

    /** 是否已经是管理员（后台探测，首次调用阻塞 0.3~0.6 秒） */
    static boolean isAdmin() {
        if (adminCache != null) {
            return adminCache;
        }
        String script =
                "try{$p=New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent());"
                        + "if($p.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)){'1'}else{'0'}}catch{'0'}";
        String output = capture(script);
        adminCache = "1".equals(output.trim());
        return adminCache;
    }

    static CompletableFuture<Boolean> isAdminAsync() {
        return CompletableFuture.supplyAsync(Sys::isAdmin);
    }

    /** 管理员探测还没出结果时同步来一次（EDT 上最多卡半秒，好过弹一次多余的 UAC） */
    static boolean isAdminBlocking() {
        if (adminCache == null) {
            isAdmin();
        }
        return adminCache;
    }

    private static ProcessBuilder powerShell(String script) {
        // 关掉进度流：否则 CLIXML 噪音会混进 stdout，把「path|version」这种输出污染掉
        String wrapped = "$ProgressPreference='SilentlyContinue';" + script;
        String encoded = Base64.getEncoder()
                .encodeToString(wrapped.getBytes(StandardCharsets.UTF_16LE));
        return new ProcessBuilder(
                "powershell",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy",
                "Bypass",
                "-WindowStyle",
                "Hidden",
                "-EncodedCommand",
                encoded);
    }

    /** 跑一段 PowerShell，返回退出码；失败时把输出漏到 stderr */
    static int run(String script) throws IOException, InterruptedException {
        ProcessBuilder pb = powerShell(script);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int code = process.waitFor();
        if (code != 0 && !output.isEmpty()) {
            System.err.println("[setup] " + output);
        }
        return code;
    }

    /** 跑一段 PowerShell 并拿 stdout；stderr（含 CLIXML 噪音）直接丢弃 */
    static String capture(String script) {
        try {
            ProcessBuilder pb = powerShell(script);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            return out;
        } catch (Exception e) {
            return "";
        }
    }

    private static String ps(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    /** 默认安装目录：所有人 → Program Files；仅我 → LocalAppData\Programs */
    static Path defaultDir(boolean allUsers) {
        if (allUsers) {
            String pf = System.getenv("ProgramFiles");
            if (pf == null || pf.isBlank()) {
                pf = "C:\\Program Files";
            }
            return Path.of(pf, "Baicodex");
        }
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isBlank()) {
            local = System.getProperty("user.home") + "\\AppData\\Local";
        }
        return Path.of(local, "Programs", "Baicodex");
    }

    /** 目标目录是否已经有一份安装 */
    static boolean looksInstalled(Path dir) {
        return Files.exists(dir.resolve("Baicodex.exe"));
    }

    /**
     * 写卸载信息 + App Paths + 开机启动。全部一个脚本搞定，只调用一次 PowerShell。
     *
     * @param allUsers 写 HKLM（所有人）还是 HKCU（仅当前用户）
     * @param startup  是否加开机启动
     */
    static int writeRegistry(boolean allUsers, Path dir, String version, boolean startup)
            throws IOException, InterruptedException {
        String exe = dir.resolve("Baicodex.exe").toString();
        String uninstaller = dir.resolve("uninstall.exe").toString();
        String hive = allUsers
                ? "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Baicodex"
                : "HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Baicodex";
        String appPaths = allUsers
                ? "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\Baicodex.exe"
                : "HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\Baicodex.exe";
        long sizeKb = Math.max(1, directorySizeKb(dir));

        StringBuilder sb = new StringBuilder();
        sb.append("$ProgressPreference='SilentlyContinue';$ErrorActionPreference='Stop';");
        sb.append("New-Item -Path ").append(ps(hive)).append(" -Force | Out-Null;");
        sb.append("$k=").append(ps(hive)).append(";");
        sb.append("Set-ItemProperty -Path $k -Name DisplayName -Value ").append(ps("Baicodex")).append(";");
        sb.append("Set-ItemProperty -Path $k -Name DisplayVersion -Value ").append(ps(version)).append(";");
        sb.append("Set-ItemProperty -Path $k -Name Publisher -Value ").append(ps("沐白/Baix")).append(";");
        sb.append("Set-ItemProperty -Path $k -Name DisplayIcon -Value ").append(ps(exe)).append(";");
        sb.append("Set-ItemProperty -Path $k -Name InstallLocation -Value ").append(ps(dir.toString())).append(";");
        sb.append("Set-ItemProperty -Path $k -Name UninstallString -Value ").append(ps("\"" + uninstaller + "\" --uninstall")).append(";");
        sb.append("Set-ItemProperty -Path $k -Name QuietUninstallString -Value ").append(ps("\"" + uninstaller + "\" --uninstall")).append(";");
        sb.append("New-ItemProperty -Path $k -Name NoModify -Value 1 -PropertyType DWord -Force | Out-Null;");
        sb.append("New-ItemProperty -Path $k -Name NoRepair -Value 1 -PropertyType DWord -Force | Out-Null;");
        sb.append("New-ItemProperty -Path $k -Name EstimatedSize -Value ").append(sizeKb).append(" -PropertyType DWord -Force | Out-Null;");
        sb.append("Set-ItemProperty -Path $k -Name InstallDate -Value ").append(ps(java.time.LocalDate.now().toString().replace("-", ""))).append(";");
        sb.append("New-Item -Path ").append(ps(appPaths)).append(" -Force | Out-Null;");
        sb.append("Set-Item -Path ").append(ps(appPaths)).append(" -Value ").append(ps(exe)).append(";");
        sb.append("Set-ItemProperty -Path ").append(ps(appPaths)).append(" -Name Path -Value ").append(ps(dir.toString())).append(";");

        String runKey = allUsers
                ? "HKLM:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run"
                : "HKCU:\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Run";
        if (startup) {
            sb.append("New-Item -Path ").append(ps(runKey)).append(" -Force | Out-Null;");
            sb.append("Set-ItemProperty -Path ").append(ps(runKey)).append(" -Name Baicodex -Value ")
                    .append(ps("\"" + exe + "\" --tray")).append(";");
        } else {
            sb.append("try{Remove-ItemProperty -Path ").append(ps(runKey)).append(" -Name Baicodex -ErrorAction SilentlyContinue}catch{};");
        }
        // 显式 exit 0：PowerShell 默认拿最后一条命令的成败当退出码，
        // Remove-ItemProperty 找不到值时会返回 false，会把成功的安装误判成失败。
        sb.append("exit 0;");
        return run(sb.toString());
    }

    /** 桌面 / 开始菜单快捷方式 */
    static int createShortcuts(
            boolean allUsers, Path dir, boolean desktop, boolean startMenu)
            throws IOException, InterruptedException {
        String exe = dir.resolve("Baicodex.exe").toString();
        StringBuilder sb = new StringBuilder();
        sb.append("$W=New-Object -ComObject WScript.Shell;");
        sb.append("function Mk($p){$s=$W.CreateShortcut($p);$s.TargetPath=").append(ps(exe)).append(";");
        sb.append("$s.WorkingDirectory=").append(ps(dir.toString())).append(";");
        sb.append("$s.IconLocation=").append(ps(exe + ",0")).append(";");
        sb.append("$s.Description=").append(ps("Baicodex 桌面端")).append(";$s.Save()};");

        if (desktop) {
            String base = allUsers ? System.getenv("PUBLIC") : System.getenv("USERPROFILE");
            if (base != null && !base.isBlank()) {
                sb.append("Mk(").append(ps(base + "\\Desktop\\Baicodex.lnk")).append(");");
            }
        }
        if (startMenu) {
            String base = allUsers ? System.getenv("ProgramData") : System.getenv("APPDATA");
            if (base != null && !base.isBlank()) {
                String folder = base + "\\Microsoft\\Windows\\Start Menu\\Programs\\Baicodex";
                sb.append("New-Item -ItemType Directory -Path ").append(ps(folder)).append(" -Force | Out-Null;");
                sb.append("Mk(").append(ps(folder + "\\Baicodex.lnk")).append(");");
            }
        }
        sb.append("exit 0;");
        return run(sb.toString());
    }

    /** 清掉上一次安装留下的快捷方式（换范围重装时避免重复） */
    static void cleanShortcuts(Path dir) {
        String exe = dir.resolve("Baicodex.exe").toString();
        String[] roots = {
            System.getenv("PUBLIC") + "\\Desktop",
            System.getenv("USERPROFILE") + "\\Desktop",
            System.getenv("ProgramData") + "\\Microsoft\\Windows\\Start Menu\\Programs",
            System.getenv("APPDATA") + "\\Microsoft\\Windows\\Start Menu\\Programs",
        };
        StringBuilder sb = new StringBuilder();
        sb.append("$W=New-Object -ComObject WScript.Shell;");
        for (String root : roots) {
            if (root.startsWith("null")) {
                continue;
            }
            sb.append("$p=").append(ps(root + "\\Baicodex.lnk")).append(";");
            sb.append("if(Test-Path $p){try{$t=$W.CreateShortcut($p).TargetPath;if($t -eq ")
                    .append(ps(exe)).append("){Remove-Item $p -Force}}catch{}};");
            sb.append("$d=").append(ps(root + "\\Baicodex")).append(";");
            sb.append("if(Test-Path $d){try{Remove-Item $d -Recurse -Force}catch{}};");
        }
        try {
            run(sb.toString());
        } catch (Exception ignored) {
            // 快捷方式清不掉不影响安装，安装时还会覆盖
        }
    }

    /** 用 UAC 重新拉起自己（等待子进程结束再返回退出码） */
    static int relaunchElevated(List<String> args) throws IOException, InterruptedException {
        String self = ProcessHandle.current().info().command().orElse(null);
        if (self == null) {
            throw new IOException("拿不到自己的路径，无法提权");
        }
        // PowerShell 5.1 的 Start-Process 会把 -ArgumentList 按空格硬拆，
        // 「--dir=C:\Program Files\Baicodex」会断成两半；所以拼成单个字符串，
        // 含空格的参数自己加双引号，交给 Windows 命令行规则去解析。
        StringBuilder list = new StringBuilder();
        for (String arg : args) {
            if (list.length() > 0) {
                list.append(" ");
            }
            if (arg.contains(" ")) {
                list.append('"').append(arg).append('"');
            } else {
                list.append(arg);
            }
        }
        String script = "$ErrorActionPreference='Stop';"
                + "$p=Start-Process -FilePath " + ps(self) + " -ArgumentList " + ps(list.toString())
                + " -Verb RunAs -PassThru;"
                + "$p.WaitForExit();"
                + "if($p.ExitCode -ne $null){exit $p.ExitCode}else{exit 1}";
        ProcessBuilder pb = powerShell(script);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = pb.start();
        return process.waitFor();
    }

    /** 上一次安装留下的线索（注册表里读出来的） */
    static final class Existing {
        final Path location;
        final String version;
        final boolean allUsers;

        Existing(Path location, String version, boolean allUsers) {
            this.location = location;
            this.version = version;
            this.allUsers = allUsers;
        }
    }

    /**
     * 找已装的 Baicodex：先 HKCU（仅我）再 HKLM（所有人）。
     * 键在但程序文件已经不在的，当作没装过（残留清理场景）。
     */
    static Existing detectExisting() {
        for (boolean allUsers : new boolean[]{false, true}) {
            String hive = allUsers ? "HKLM:" : "HKCU:";
            String key = hive + "\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\Baicodex";
            String script =
                    "$ErrorActionPreference='SilentlyContinue';"
                            + "$k=" + ps(key) + ";"
                            + "if(Test-Path $k){$p=Get-ItemProperty $k;"
                            + "$i=$p.InstallLocation;$v=$p.DisplayVersion;"
                            + "if($i){Write-Output ($i+'|'+$v)}}";
            String out = capture(script).trim();
            if (out.isEmpty()) {
                continue;
            }
            int sep = out.lastIndexOf('|');
            String loc = sep > 0 ? out.substring(0, sep) : out;
            String ver = sep > 0 ? out.substring(sep + 1) : "";
            try {
                Path location = Path.of(loc);
                if (Files.exists(location.resolve("Baicodex.exe"))) {
                    return new Existing(location, ver, allUsers);
                }
            } catch (Exception ignored) {
                // 注册表里是个非法路径，当没装过
            }
        }
        return null;
    }

    /** 结束正在运行的 Baicodex，避免覆盖文件时被占用 */    static void stopRunningApp() {
        capture("Get-Process Baicodex -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; 'ok'");
    }

    static void launch(Path dir) {
        try {
            new ProcessBuilder(dir.resolve("Baicodex.exe").toString())
                    .directory(dir.toFile())
                    .start();
        } catch (IOException ignored) {
            // 启动失败不影响安装结果，用户可以从快捷方式打开
        }
    }

    static long directorySizeKb(Path dir) {
        try (var stream = Files.walk(dir)) {
            long bytes = stream.filter(Files::isRegularFile).mapToLong(p -> {
                try {
                    return Files.size(p);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum();
            return bytes / 1024;
        } catch (IOException e) {
            return 0L;
        }
    }

    private Sys() {
    }
}
