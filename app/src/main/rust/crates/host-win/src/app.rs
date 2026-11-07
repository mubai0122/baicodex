//! 装配：解析参数 → 起内核 → 开窗口。
//!
//! 顺序有讲究：**内核先于窗口**。反过来的话，窗口先显示一帧空白，再因为内核
//! 启动失败（数据目录没权限、加密库损坏）弹一个错误框，用户看到的是"闪一下白屏
//! 然后报错"。先起内核，失败就直接退出，窗口都不出现。

use crate::cli::Cli;
use crate::win;

/// 进程入口。返回退出码。
pub fn run() -> i32 {
    let cli = match Cli::parse(std::env::args().skip(1)) {
        Ok(c) => c,
        Err(e) => {
            eprintln!("{}", e.hint());
            // --help / --version 也走这里，用 2 区分"参数错"和"运行失败"
            return 2;
        }
    };

    if cli.dry_run {
        println!("{cli:#?}");
        return 0;
    }

    // —— 内核 ——
    let base = cli.base.as_ref().map(|p| p.to_string_lossy().to_string()).unwrap_or_default();
    if let Err(e) = baicodex::ffi::boot(&base) {
        eprintln!("内核启动失败：{e}");
        return 1;
    }

    // —— 窗口 ——
    match win::run(cli) {
        Ok(code) => {
            baicodex::ffi::shutdown();
            code
        }
        Err(e) => {
            eprintln!("{}", e.hint());
            eprintln!("详情：{e}");
            1
        }
    }
}
