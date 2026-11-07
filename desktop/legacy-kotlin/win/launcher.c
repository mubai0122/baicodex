// Windows 启动器：被 mingw 交叉编译成 baicodex.exe，只做一件事——
// 找到自己所在目录，启动 runtime\bin\javaw.exe -jar app\baicodex.jar
#include <windows.h>
#include <wchar.h>

int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE hPrev, PWSTR args, int show) {
    wchar_t dir[MAX_PATH];
    GetModuleFileNameW(NULL, dir, MAX_PATH);
    wchar_t *p = wcsrchr(dir, L'\\');
    if (p) *p = 0;

    wchar_t java[MAX_PATH * 2];
    wchar_t jar[MAX_PATH * 2];
    wchar_t cmd[MAX_PATH * 6];
    _snwprintf(java, MAX_PATH * 2, L"%s\\runtime\\bin\\javaw.exe", dir);
    _snwprintf(jar, MAX_PATH * 2, L"%s\\app\\baicodex.jar", dir);
    //中文 Windows 默认 GBK：不显式指定 UTF-8，读写文件和日志都会乱码
    _snwprintf(cmd, MAX_PATH * 6,
               L"\"%s\" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 "
               L"-Dsun.stderr.encoding=UTF-8 -jar \"%s\"",
               java, jar);

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    //工作目录设成 exe 所在目录：应用里所有相对路径都以此为根
    if (!CreateProcessW(java, cmd, NULL, NULL, FALSE, 0, NULL, dir, &si, &pi)) {
        MessageBoxW(NULL, L"启动失败：找不到 runtime\\bin\\javaw.exe\n请确认解压完整。",
                    L"BaiCodex", MB_ICONERROR | MB_OK);
        return 1;
    }
    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return 0;
} //入口：拉起同目录的 javaw