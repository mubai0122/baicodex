//! JSON 宽容取值。
//!
//! 模型的工具参数是"它自己编的 JSON"：字段可能叫 `path` 也可能叫 `file`，数字可能
//! 写成字符串（`"limit":"20"`），布尔可能写成 `1` / `"true"` / `"yes"`。
//!
//! 如果每个工具都手写一遍 `v.get("x").and_then(|x| x.as_str())`，代码会变成一锅粥，
//! 而且各处的容错口径不一致。这里把口径统一：
//!
//! - 字符串：接受 string / number / bool，转成文本
//! - 整数：接受 number / 数字字符串 / 浮点（截断）
//! - 布尔：接受 bool / 0/1 / "true"/"false"/"yes"/"no"/"on"/"off"
//!
//! 取不到时返回默认值，**不报错**。真正的"必填校验"由工具的 `require_*` 负责，
//! 那里才需要给出人话错误。

use serde_json::{Map, Value};

/// 按别名顺序取第一个存在的键。
fn pick<'a>(v: &'a Value, keys: &[&str]) -> Option<&'a Value> {
    let obj = v.as_object()?;
    keys.iter().find_map(|k| obj.get(*k))
}

/// 取字符串。`keys` 支持多个别名。
pub fn get_str(v: &Value, keys: &[&str], default: &str) -> String {
    match pick(v, keys) {
        Some(Value::String(s)) => s.clone(),
        Some(Value::Number(n)) => n.to_string(),
        Some(Value::Bool(b)) => b.to_string(),
        _ => default.to_string(),
    }
}

/// 取字符串，空串也当没取到（很多模型会用 `""` 表示"不填"）。
pub fn get_nonempty(v: &Value, keys: &[&str]) -> Option<String> {
    let s = get_str(v, keys, "");
    if s.trim().is_empty() {
        None
    } else {
        Some(s)
    }
}

/// 取整数。失败返回默认值。
pub fn get_i64(v: &Value, keys: &[&str], default: i64) -> i64 {
    match pick(v, keys) {
        Some(Value::Number(n)) => n
            .as_i64()
            .or_else(|| n.as_f64().map(|f| f as i64))
            .unwrap_or(default),
        Some(Value::String(s)) => s.trim().parse::<i64>().unwrap_or_else(|_| {
            // "20.5" 这种也认
            s.trim().parse::<f64>().map(|f| f as i64).unwrap_or(default)
        }),
        Some(Value::Bool(b)) => {
            if *b {
                1
            } else {
                0
            }
        }
        _ => default,
    }
}

/// 取浮点。
pub fn get_f64(v: &Value, keys: &[&str], default: f64) -> f64 {
    match pick(v, keys) {
        Some(Value::Number(n)) => n.as_f64().unwrap_or(default),
        Some(Value::String(s)) => s.trim().parse().unwrap_or(default),
        _ => default,
    }
}

/// 取布尔。`"false"` 不会因为非空被判成 true——这是手写容错最容易踩的坑。
pub fn get_bool(v: &Value, keys: &[&str], default: bool) -> bool {
    match pick(v, keys) {
        Some(Value::Bool(b)) => *b,
        Some(Value::Number(n)) => n.as_i64().map(|i| i != 0).unwrap_or(default),
        Some(Value::String(s)) => match s.trim().to_ascii_lowercase().as_str() {
            "true" | "1" | "yes" | "y" | "on" | "是" => true,
            "false" | "0" | "no" | "n" | "off" | "否" => false,
            _ => default,
        },
        _ => default,
    }
}

/// 取数组。单个值会包成单元素数组（模型经常把 `[1,2]` 写成 `1`）。
pub fn get_array(v: &Value, keys: &[&str]) -> Vec<Value> {
    match pick(v, keys) {
        Some(Value::Array(a)) => a.clone(),
        Some(Value::Null) | None => Vec::new(),
        Some(other) => vec![other.clone()],
    }
}

/// 取字符串数组。数组元素里的对象会尝试取 `path` / `name` / `text`。
pub fn get_str_array(v: &Value, keys: &[&str]) -> Vec<String> {
    get_array(v, keys)
        .into_iter()
        .filter_map(|x| match x {
            Value::String(s) => Some(s),
            Value::Number(n) => Some(n.to_string()),
            Value::Object(_) => {
                let s = get_str(&x, &["path", "name", "text", "value"], "");
                if s.is_empty() {
                    None
                } else {
                    Some(s)
                }
            }
            _ => None,
        })
        .collect()
}

/// 取对象。
pub fn get_obj(v: &Value, keys: &[&str]) -> Map<String, Value> {
    pick(v, keys)
        .and_then(|x| x.as_object().cloned())
        .unwrap_or_default()
}

/// 取任意对象并转成"扁平字符串表"，给技能参数与密钥表用。
pub fn flat_table(v: &Value) -> std::collections::BTreeMap<String, String> {
    let mut out = std::collections::BTreeMap::new();
    if let Some(obj) = v.as_object() {
        for (k, val) in obj {
            let s = match val {
                Value::String(s) => s.clone(),
                Value::Null => String::new(),
                other => other.to_string(),
            };
            out.insert(k.clone(), s);
        }
    }
    out
}

/// 把任意值压成文本。对象/数组用紧凑 JSON。
pub fn as_text(v: &Value) -> String {
    match v {
        Value::Null => String::new(),
        Value::String(s) => s.clone(),
        Value::Bool(b) => b.to_string(),
        Value::Number(n) => n.to_string(),
        other => other.to_string(),
    }
}

/// 解析失败就用默认值。用于"配置文件坏了也得能启动"。
pub fn parse_or(s: &str, default: Value) -> Value {
    serde_json::from_str(s).unwrap_or(default)
}

/// 深度合并：`patch` 里的对象逐键覆盖 `base`，数组整体替换。
///
/// 配置升级用得上：新版本只写新增字段，旧配置里的用户设置不该被抹掉。
pub fn merge(base: &mut Value, patch: &Value) {
    match (base, patch) {
        (Value::Object(b), Value::Object(p)) => {
            for (k, v) in p {
                match b.get_mut(k) {
                    Some(existing) => merge(existing, v),
                    None => {
                        b.insert(k.clone(), v.clone());
                    }
                }
            }
        }
        (b, p) => *b = p.clone(),
    }
}

/// 必填字符串：缺失或空白都报"缺参数"。
pub fn require_str(v: &Value, keys: &[&str], what: &str) -> Result<String, String> {
    match get_nonempty(v, keys) {
        Some(s) => Ok(s),
        None => Err(format!("缺少参数 {what}")),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn tolerant_types() {
        let v = json!({"n": "12", "b": "false", "list": 3, "flag": 0});
        assert_eq!(get_i64(&v, &["n"], 0), 12);
        assert!(!get_bool(&v, &["b"], true));
        assert_eq!(get_str_array(&v, &["list"]), vec!["3"]);
        assert!(!get_bool(&v, &["flag"], true));
    }

    #[test]
    fn aliases() {
        let v = json!({"file": "/tmp/a"});
        assert_eq!(get_str(&v, &["path", "file"], ""), "/tmp/a");
    }

    #[test]
    fn deep_merge() {
        let mut a = json!({"x": {"y": 1, "z": 2}, "keep": true});
        let b = json!({"x": {"y": 9}});
        merge(&mut a, &b);
        assert_eq!(a["x"]["y"], 9);
        assert_eq!(a["x"]["z"], 2);
        assert_eq!(a["keep"], true);
    }
}
