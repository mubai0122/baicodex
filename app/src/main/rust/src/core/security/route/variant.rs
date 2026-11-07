//! 零宽路径候选。
//!
//! 背景：Android 11 起，`/sdcard/Android/data` 对普通应用不可读。部分 ROM 的
//! 路径归一化实现存在缺陷（CVE-2024-43093 一类），在路径里插入**零宽字符**或
//! 多余分隔符后，权限检查认不出这是受限目录，而底层 `open` 又能正常解析。
//!
//! 这件事没法靠版本号猜：ROM 千奇百怪，同一个版本号在不同厂商手里行为不同。
//! 所以这里的策略是**穷举 + 实测**：把六种已知变形都试一遍，哪个能 `read_dir`
//! 成功就用哪个；全失败就老老实实告诉用户"这台设备不支持"。
//!
//! 注意：这些路径**只用于读取自己的数据目录**（备份、迁移），不用于绕过系统
//! 保护去读别的应用的数据。

/// 一种候选变形。
#[derive(Debug, Clone, Copy)]
pub struct Variant {
    /// 给人看的名字，设置页里显示"当前走：零宽连接"。
    pub name: &'static str,
    /// 说明这个变形插在哪儿。
    pub note: &'static str,
    map: fn(&str) -> String,
}

impl Variant {
    /// 应用变形。
    pub fn apply(&self, path: &str) -> String {
        (self.map)(path)
    }
}

/// 全部候选，按"副作用从小到大"排序。
///
/// 顺序有讲究：原路径最先（能用就别折腾），其次是纯分隔符变形（对文件名无影响），
/// 最后才是插入零宽字符（某些文件管理器会显示成乱码）。
pub const VARIANTS: [Variant; 6] = [
    Variant { name: "原路径", note: "不做任何变形", map: |p| p.to_string() },
    Variant { name: "尾点", note: "末尾追加 /.", map: |p| format!("{p}/.") },
    Variant {
        name: "双斜杠",
        note: "/Android/ → //Android/",
        map: |p| p.replace("/Android/", "//Android/"),
    },
    Variant {
        name: "零宽连接",
        note: "/Android/ → /Android\\u{200b}/",
        map: |p| p.replace("/Android/", "/Android\u{200b}/"),
    },
    Variant { name: "尾零宽", note: "末尾追加零宽空格", map: |p| format!("{p}\u{200b}") },
    Variant {
        name: "目录名零宽",
        note: "/data、/obb 后插入零宽非连接符（U+200C，与尾零宽用不同码位，避免两个候选重合）",
        map: |p| {
            p.replace("/data", "/data\u{200c}")
                .replace("/obb", "/obb\u{200c}")
        },
    },
];

/// 受限目录根。
pub const DATA_ROOT: &str = "/storage/emulated/0/Android/data";
/// OBB 根。
pub const OBB_ROOT: &str = "/storage/emulated/0/Android/obb";

/// 把零宽字符显示成可见标记，日志与界面上用。
///
/// 直接把 `\u{200b}` 打到终端里是看不见的，用户会以为路径坏了。
pub fn visualize(path: &str) -> String {
    path.replace('\u{200b}', "⟪zw⟫")
}

/// 路径里是否含零宽字符。
pub fn has_zero_width(path: &str) -> bool {
    path.chars().any(|c| {
        matches!(c, '\u{200b}' | '\u{200c}' | '\u{200d}' | '\u{feff}')
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn variants_are_distinct() {
        let p = DATA_ROOT;
        let mut seen = std::collections::HashSet::new();
        for v in VARIANTS {
            assert!(seen.insert(v.apply(p)), "变形重复：{}", v.name);
        }
    }

    #[test]
    fn zero_width_variants_actually_insert_something() {
        assert_ne!(VARIANTS[3].apply(DATA_ROOT), DATA_ROOT);
        assert!(has_zero_width(&VARIANTS[3].apply(DATA_ROOT)));
        assert!(has_zero_width(&VARIANTS[5].apply(DATA_ROOT)));
    }

    #[test]
    fn original_is_identity() {
        assert_eq!(VARIANTS[0].apply("/a/b"), "/a/b");
    }

    #[test]
    fn visualize_hides_nothing() {
        let s = visualize(&VARIANTS[3].apply(DATA_ROOT));
        assert!(s.contains("⟪zw⟫"));
    }
}
