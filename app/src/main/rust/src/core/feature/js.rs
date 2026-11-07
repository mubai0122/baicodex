//! 技能包里的 JS 函数：把调用参数变成脚本能读的 `input`。
//!
//! 真正的执行在 [crate::exec::js]（引擎选择、超时、输出收集都在那）。
//! 这一层只做一件事：**把参数以 JSON 形式放进脚本作用域**，让技能作者能写
//!
//! ```js
//! const city = input.city;
//! console.log(`天气：${city}`);
//! ```
//!
//! 而不是自己去解析命令行参数。约定只有一个 `input` 变量，比 "argv / env / stdin
//! 三种传参方式" 好记。
//!
//! ## 为什么参数用 JSON 而不是字符串替换
//!
//! 字符串替换会让 `"北京"` 变成裸标识符，作者得自己加引号；更糟的是参数里带
//! 引号或换行时，脚本直接语法错。JSON 序列化一次解决所有转义问题。

use serde_json::Value;

use crate::core::base::text;

/// 生成前置片段。`input` 用 `const`：作者不该改它（改了后面读到的是改过的值，
/// 排查起来会怀疑人生）。
pub fn prelude(input: &Value) -> String {
    let json = serde_json::to_string(input).unwrap_or_else(|_| "{}".into());
    format!("const input = Object.freeze({json});\n")
}

/// 把前置片段拼到脚本前面。
pub fn wrap(code: &str, input: &Value) -> String {
    format!("{}{}", prelude(input), code)
}

/// 执行一段带参数的脚本。
///
/// `timeout_ms` 会被 [crate::exec::js] 再夹一遍，这里不做二次限制。
pub async fn run_with_input(code: &str, input: &Value, timeout_ms: u64) -> crate::exec::js::JsOut {
    let full = wrap(code, input);
    crate::exec::js::run(&full, timeout_ms).await
}

/// 从函数参数里抠出 `input`：
///
/// - 有 `input` / `args` 对象 → 用它；
/// - 否则把除保留键之外的所有字段当参数。
pub fn input_of(args: &Value) -> Value {
    if let Some(v) = args.get("input") {
        if v.is_object() || v.is_array() {
            return v.clone();
        }
    }
    if let Some(v) = args.get("args") {
        if v.is_object() || v.is_array() {
            return v.clone();
        }
    }
    let mut m = serde_json::Map::new();
    if let Some(obj) = args.as_object() {
        for (k, v) in obj {
            if matches!(k.as_str(), "script" | "code" | "js" | "timeout") {
                continue;
            }
            m.insert(k.clone(), v.clone());
        }
    }
    Value::Object(m)
}

/// 结果整理：截断 + 去掉首尾空白。空输出给一句人话。
pub fn tidy(text_in: &str, max: usize) -> String {
    let t = text_in.trim();
    if t.is_empty() {
        return "（脚本没有输出）".into();
    }
    text::truncate(t, max.clamp(200, 200_000))
}

/// 给工具定义用的说明（技能包 js 函数会用到）。
pub fn usage_hint(name: &str) -> String {
    format!(
        "脚本里可以用 input 读参数，用 console.log 输出。例如：\n  const {{ city }} = input;\n  console.log(city);\n（函数名：{name}）"
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn prelude_is_valid_json() {
        let p = prelude(&json!({"a": "带\"引号\"的值", "b": [1,2]}));
        assert!(p.starts_with("const input = Object.freeze("));
        assert!(p.contains("\\\""));
    }

    #[test]
    fn input_extraction() {
        let v = json!({"script": "x", "city": "北京"});
        let i = input_of(&v);
        assert_eq!(i["city"], "北京");
        assert!(i.get("script").is_none());
    }

    #[test]
    fn explicit_input_wins() {
        let v = json!({"input": {"a": 1}, "b": 2});
        assert_eq!(input_of(&v)["a"], 1);
    }
}
