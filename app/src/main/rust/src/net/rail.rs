//! 12306 查询：余票 / 中转 / 经停。
//!
//! 只发只读请求，不需要登录。站点名先查内置表，查不到再调 12306 的站点检索接口。
//! 余票接口返回的是 `|` 分隔的老格式，**字段位置多年没变**，按位置取比按名字稳。
//!
//! ## 风控
//!
//! 12306 对高频请求会直接给一段 HTML 错误页而不是 JSON。遇到这种情况明确说
//! "可能触发了风控"，不要重试三次——重试只会让风控更久。

use std::collections::HashMap;
use std::sync::OnceLock;

use serde_json::Value;

use super::client;
use crate::core::base::text;

/// 常用站点。`名字 → 电报码`。
///
/// 只收直辖市 / 省会 / 主要枢纽。其余靠 12306 的检索接口，不在这里堆一张
/// 几千行的表——那张表一年改两次，维护成本大于收益。
fn stations() -> &'static HashMap<&'static str, &'static str> {
    static M: OnceLock<HashMap<&'static str, &'static str>> = OnceLock::new();
    M.get_or_init(|| {
        let mut m = HashMap::new();
        for (name, code) in [
            ("北京", "BJP"), ("北京南", "VNP"), ("北京西", "BXP"), ("北京北", "VAP"), ("北京朝阳", "IFP"),
            ("上海", "SHH"), ("上海虹桥", "AOH"), ("上海南", "SNH"), ("上海西", "SXH"),
            ("广州", "GZQ"), ("广州南", "IZQ"), ("广州东", "GGQ"), ("广州北", "GBQ"),
            ("深圳", "SZQ"), ("深圳北", "IOQ"), ("深圳东", "BJQ"), ("福田", "NZQ"),
            ("杭州", "HZH"), ("杭州东", "HGH"), ("杭州西", "HVU"), ("南京", "NJH"), ("南京南", "NKH"),
            ("武汉", "WHN"), ("汉口", "HKN"), ("武昌", "WCN"), ("成都", "CDW"), ("成都东", "ICW"),
            ("重庆", "CQW"), ("重庆北", "CUW"), ("重庆西", "CXW"), ("西安", "XAY"), ("西安北", "EAY"),
            ("郑州", "ZZF"), ("郑州东", "ZAF"), ("长沙", "CSQ"), ("长沙南", "CWQ"),
            ("济南", "JNK"), ("济南西", "JGK"), ("青岛", "QDK"), ("青岛北", "QHK"),
            ("天津", "TJP"), ("天津西", "TXP"), ("天津南", "TIP"), ("沈阳", "SYT"), ("沈阳北", "SBT"),
            ("哈尔滨", "HBB"), ("哈尔滨西", "HXB"), ("长春", "CCT"), ("大连", "DLT"), ("大连北", "DFT"),
            ("福州", "FZS"), ("福州南", "FYS"), ("厦门", "XMS"), ("厦门北", "XKS"),
            ("南昌", "NCG"), ("南昌西", "NXG"), ("合肥", "HFH"), ("合肥南", "ENH"),
            ("昆明", "KMM"), ("昆明南", "KOM"), ("贵阳", "GIW"), ("贵阳北", "KQW"),
            ("南宁", "NNZ"), ("南宁东", "NFZ"), ("海口", "VUQ"), ("三亚", "SEQ"),
            ("兰州", "LZJ"), ("西宁", "XNO"), ("银川", "YIJ"), ("乌鲁木齐", "WMR"),
            ("太原", "TYV"), ("石家庄", "SJP"), ("苏州", "SZH"), ("苏州北", "OHH"),
            ("无锡", "WXH"), ("宁波", "NGH"), ("温州", "RZH"), ("东莞", "RTQ"), ("佛山", "FSQ"),
            ("珠海", "ZHQ"), ("桂林", "GLZ"), ("洛阳", "LYF"), ("徐州", "XCH"), ("常州", "CZH"),
            ("泉州", "QYS"), ("烟台", "YAK"), ("威海", "WKK"), ("唐山", "TSP"), ("秦皇岛", "QTP"),
        ] {
            m.insert(name, code);
        }
        m
    })
}

fn valid_date(d: &str) -> bool {
    let Ok(date) = chrono::NaiveDate::parse_from_str(d.trim(), "%Y-%m-%d") else {
        return false;
    };
    let today = chrono::Local::now().date_naive();
    let diff = (date - today).num_days();
    (0..=15).contains(&diff)
}

/// 站名 → 电报码。
async fn resolve(name: &str) -> Result<String, String> {
    let n = name.trim();
    if n.is_empty() {
        return Err("站名是空的".into());
    }
    if n.len() == 3 && n.chars().all(|c| c.is_ascii_uppercase()) {
        return Ok(n.to_string());
    }
    if let Some(code) = stations().get(n) {
        return Ok((*code).to_string());
    }
    let alt = n.trim_end_matches('站');
    if let Some(code) = stations().get(alt) {
        return Ok((*code).to_string());
    }

    let url = format!(
        "https://search.12306.cn/search/v1/station/query?keyword={}",
        text::url_encode(alt)
    );
    let resp = client::shared()
        .get(&url)
        .header("Referer", "https://www.12306.cn/")
        .send()
        .await
        .map_err(|e| format!("站点检索失败：{e}"))?;
    let v: Value = resp.json().await.map_err(|e| format!("站点检索返回异常：{e}"))?;
    for item in v["data"].as_array().cloned().unwrap_or_default() {
        let station = item["station"].as_str().unwrap_or("");
        let code = item["code"].as_str().unwrap_or("");
        if !code.is_empty() && (station == alt || station.starts_with(alt)) {
            return Ok(code.to_string());
        }
    }
    Err(format!("找不到车站「{name}」，换个写法（如「上海虹桥」）再试"))
}

/// 一行车次。
#[derive(Debug, Clone)]
struct Row {
    /// 车次号，如 G1234。
    code: String,
    /// 内部车次号，查经停要用。
    train_no: String,
    start: String,
    arrive: String,
    duration: String,
    duration_min: i32,
    /// 座位余票，拼好的一行。
    seats: String,
    second: String,
    first: String,
    none_seat: String,
}

/// 查余票。
///
/// - `train_type`：只看某几类车（如 `"GD"` 只看高铁动车）；
/// - `sort`：`start` / `arrive` / `duration`。
pub async fn tickets(
    from: &str,
    to: &str,
    date: &str,
    train_type: Option<&str>,
    sort: Option<&str>,
    limit: usize,
) -> String {
    if !valid_date(date) {
        return "日期格式不对：要 yyyy-MM-dd，且只能是今天起 15 天内".into();
    }
    if from.trim().eq_ignore_ascii_case(to.trim()) {
        return "出发站和到达站不能是同一个".into();
    }
    let (fc, tc) = match (resolve(from).await, resolve(to).await) {
        (Ok(a), Ok(b)) => (a, b),
        (Err(e), _) | (_, Err(e)) => return e,
    };

    let url = format!(
        "https://kyfw.12306.cn/otn/leftTicket/query?leftTicketDTO.train_date={date}\
         &leftTicketDTO.from_station={fc}&leftTicketDTO.to_station={tc}&purpose_codes=ADULT"
    );
    let resp = client::shared()
        .get(&url)
        .header("Referer", "https://kyfw.12306.cn/otn/leftTicket/init")
        .header("Cookie", "JSESSIONID=x; RAIL_EXPIRATION=0; RAIL_DEVICEID=x")
        .send()
        .await;
    let v: Value = match resp {
        Ok(r) if r.status().is_success() => match r.json().await {
            Ok(v) => v,
            Err(_) => return "查询失败：12306 返回的不是 JSON（可能触发了风控，过几分钟再试）".into(),
        },
        Ok(r) => return format!("查询失败：HTTP {}", r.status().as_u16()),
        Err(e) => return format!("查询失败：{e}"),
    };

    let rows = v["data"]["result"].as_array().cloned().unwrap_or_default();
    if rows.is_empty() {
        return "没查到车次：可能当天无票、站名不对，或 12306 临时限流".into();
    }

    let mut list: Vec<Row> = rows
        .iter()
        .filter_map(|r| r.as_str())
        .filter_map(parse_row)
        .filter(|r| match train_type {
            Some(t) if !t.trim().is_empty() => t.chars().any(|c| r.code.starts_with(c)),
            _ => true,
        })
        .collect();

    match sort.unwrap_or("start") {
        "arrive" => list.sort_by(|a, b| a.arrive.cmp(&b.arrive)),
        "duration" => list.sort_by(|a, b| a.duration_min.cmp(&b.duration_min)),
        _ => list.sort_by(|a, b| a.start.cmp(&b.start)),
    }

    let n = limit.clamp(1, 40);
    let mut out = format!("{} → {} {}（{} 趟）：\n", from, to, date, list.len());
    for r in list.iter().take(n) {
        out.push_str(&format!(
            "{} {}→{} 历时{}  {}  二等{} 一等{} 无座{}\n",
            r.code, r.start, r.arrive, r.duration, r.seats, r.second, r.first, r.none_seat
        ));
        out.push_str(&format!("   车次号（查经停用）：{}\n", r.train_no));
    }
    if list.len() > n {
        out.push_str(&format!("…（共 {} 趟，只列前 {n}）\n", list.len()));
    }
    out.push_str("\n提示：查经停用 action=route 并带车次号；查中转用 action=transfer。");
    out
}

/// 中转：把两段拼起来。
pub async fn transfer(from: &str, to: &str, date: &str, middle: &str, limit: usize) -> String {
    if middle.trim().is_empty() {
        return "中转要指定 middle（中转城市，如 南京、武汉）".into();
    }
    let a = tickets(from, middle, date, None, Some("start"), 5).await;
    if a.starts_with("查询失败") || a.starts_with("没查到") || a.starts_with("日期格式") {
        return format!("第一段没查到：{a}");
    }
    let b = tickets(middle, to, date, None, Some("start"), 5).await;
    if b.starts_with("查询失败") || b.starts_with("没查到") || b.starts_with("日期格式") {
        return format!("第二段没查到：{b}");
    }
    let n = limit.clamp(1, 20);
    format!(
        "中转方案（{from} → {middle} → {to}，{date}）：\n\n【第一段】\n{}\n\n【第二段】\n{}\n\n（同站换乘至少留 20 分钟，异站换乘留 1 小时以上）",
        clip(&a, n),
        clip(&b, n)
    )
}

/// 经停站。
pub async fn route(train_no: &str, from: &str, to: &str, date: &str) -> String {
    let no = train_no.trim();
    if no.is_empty() {
        return "查经停要车次号：从 tickets 结果里取（形如 6i000G12340G）".into();
    }
    if !valid_date(date) {
        return "日期格式不对：要 yyyy-MM-dd".into();
    }
    let url = format!(
        "https://kyfw.12306.cn/otn/czxx/queryByTrainNo?train_no={}&from_station_telecode={}&to_station_telecode={}&depart_date={}",
        text::url_encode(no),
        text::url_encode(from),
        text::url_encode(to),
        date
    );
    let resp = client::shared()
        .get(&url)
        .header("Referer", "https://kyfw.12306.cn/otn/czxx/init")
        .send()
        .await;
    let v: Value = match resp {
        Ok(r) if r.status().is_success() => match r.json().await {
            Ok(v) => v,
            Err(_) => return "没查到经停站：接口返回异常（可能风控）".into(),
        },
        Ok(r) => return format!("查询失败：HTTP {}", r.status().as_u16()),
        Err(e) => return format!("查询失败：{e}"),
    };
    let arr = v["data"]["data"].as_array().cloned().unwrap_or_default();
    if arr.is_empty() {
        return "没查到经停站".into();
    }
    let mut out = format!("车次 {train_no} 经停：\n");
    for (i, s) in arr.iter().enumerate() {
        let name = s["station_name"].as_str().unwrap_or("");
        let arrive = s["arrive_time"].as_str().unwrap_or("----");
        let start = s["start_time"].as_str().unwrap_or("----");
        let stop = s["stopover_time"].as_str().unwrap_or("");
        out.push_str(&format!("{:>2}. {name:<10} 到 {arrive} 开 {start} {stop}\n", i + 1));
    }
    out
}

/// 老格式：`|` 分隔。位置对不上就返回 None（宁可少一条，也不要错位的数据）。
fn parse_row(s: &str) -> Option<Row> {
    let f: Vec<&str> = s.split('|').collect();
    if f.len() < 34 {
        return None;
    }
    let get = |i: usize| -> String {
        let v = f.get(i).copied().unwrap_or("");
        if v.is_empty() || v == "无" {
            "--".into()
        } else {
            v.to_string()
        }
    };
    let duration = get(10);
    Some(Row {
        code: get(3),
        train_no: get(2),
        start: get(8),
        arrive: get(9),
        duration_min: parse_duration(&duration),
        duration,
        seats: format!("硬座{} 硬卧{} 软卧{}", get(29), get(28), get(23)),
        second: get(30),
        first: get(31),
        none_seat: get(26),
    })
}

fn parse_duration(s: &str) -> i32 {
    let parts: Vec<i32> = s.split(':').filter_map(|x| x.trim().parse().ok()).collect();
    match parts.as_slice() {
        [h, m] => h * 60 + m,
        _ => 9999,
    }
}

fn clip(s: &str, n: usize) -> String {
    s.lines().take(n + 6).collect::<Vec<_>>().join("\n")
}

/// 一次性查好并格式化（给"我想去哪里"这种模糊请求兜底）。
pub async fn suggest(from: &str, to: &str, date: &str) -> String {
    let t = tickets(from, to, date, None, Some("start"), 8).await;
    if t.starts_with("查询失败") || t.starts_with("没查到") {
        return format!("{t}\n（可以换个站名再试，比如「上海虹桥」而不是「上海」）");
    }
    t
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn duration_parse() {
        assert_eq!(parse_duration("04:35"), 275);
        assert_eq!(parse_duration("----"), 9999);
    }

    #[test]
    fn row_parse_short_is_none() {
        assert!(parse_row("a|b|c").is_none());
    }

    #[test]
    fn date_window() {
        assert!(!valid_date("1999-01-01"));
        assert!(!valid_date("bad"));
    }
}
