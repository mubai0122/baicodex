//! 用量账本。
//!
//! 每次请求一行，与会话解耦：删会话、清消息都**不回滚**账本。
//! 花掉的钱不会因为删记录而退回来，账本要是能"删掉就消失"，统计页的数字就不可信。
//!
//! 写入前做三道清洗（历史 bug 留下的脏数据会让统计离谱）：
//! 1. token 数为负或总量为 0 → 不记。
//! 2. 超过 500 万 token 的单次请求 → 不记（明显是解析错误）。
//! 3. 时间戳不在"最近 7 天 ~ 未来 1 分钟"内 → 改成当前时间。
//!
//! 读的时候按本地日聚合（`strftime('%Y-%m-%d', at/1000, 'unixepoch', 'localtime')`），
//! 与用户看到的"今天用了多少"一致。

use rusqlite::params;

use super::schema::Db;

/// 按日 × 模型聚合的一行：日期、模型、输入、命中缓存、输出。
pub type DayModel = (String, String, i64, i64, i64);
use crate::core::base::error::BResult;

impl Db {
    /// 记一笔。
    ///
    /// 参数与 `usage` 表的列一一对应；调用点传的都是有名字段（`t.prompt` 等），
    /// 拆成结构体只是把参数搬家，反而多一层间接。
    #[allow(clippy::too_many_arguments)]
    pub fn record_usage(
        &self,
        at: i64,
        provider: &str,
        model: &str,
        session_id: &str,
        prompt: i64,
        cached: i64,
        completion: i64,
    ) -> BResult<()> {
        if prompt + completion <= 0 || prompt < 0 || completion < 0 {
            return Ok(());
        }
        if prompt > 5_000_000 || completion > 5_000_000 {
            return Ok(());
        }
        let now = crate::core::base::clock::now_ms();
        let stamp = if at >= now - 7 * 86_400_000 && at <= now + 60_000 { at } else { now };
        let sid = if crate::core::base::ids::IdGen::is_session_id(session_id) || looks_uuid(session_id) {
            session_id
        } else {
            ""
        };
        self.exec(
            "INSERT INTO usage(at,provider,model,session_id,prompt,cached,completion)
             VALUES(?1,?2,?3,?4,?5,?6,?7)",
            params![stamp, provider, model, sid, prompt, cached.clamp(0, prompt), completion],
        )?;
        Ok(())
    }

    /// 总量：行数 / prompt / cached / completion。
    pub fn usage_totals(&self) -> BResult<(i64, i64, i64, i64)> {
        Ok(self
            .one(
                "SELECT COUNT(*),IFNULL(SUM(prompt),0),IFNULL(SUM(MIN(cached,prompt)),0),IFNULL(SUM(completion),0)
                 FROM usage WHERE prompt+completion>0",
                [],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
            )?
            .unwrap_or((0, 0, 0, 0)))
    }

    /// 按本地日聚合。
    pub fn usage_daily(&self) -> BResult<Vec<(String, i64, i64, i64)>> {
        self.all(
            "SELECT strftime('%Y-%m-%d',at/1000,'unixepoch','localtime') d,
                    IFNULL(SUM(prompt),0),IFNULL(SUM(MIN(cached,prompt)),0),IFNULL(SUM(completion),0)
             FROM usage WHERE prompt+completion>0 AND at>0 GROUP BY d ORDER BY d",
            [],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?)),
        )
    }

    /// 按日 × 模型聚合（费用要按各模型单价分开算）。
    pub fn usage_day_model(&self) -> BResult<Vec<DayModel>> {
        self.all(
            "SELECT strftime('%Y-%m-%d',at/1000,'unixepoch','localtime') d,IFNULL(model,'') m,
                    IFNULL(SUM(prompt),0),IFNULL(SUM(MIN(cached,prompt)),0),IFNULL(SUM(completion),0)
             FROM usage WHERE prompt+completion>0 AND at>0 GROUP BY d,m",
            [],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?)),
        )
    }

    /// 单会话累计用量。
    pub fn usage_session(&self, sid: &str) -> BResult<(i64, i64)> {
        if sid.is_empty() {
            return Ok((0, 0));
        }
        Ok(self
            .one(
                "SELECT IFNULL(SUM(prompt),0),IFNULL(SUM(completion),0) FROM usage
                 WHERE session_id=?1 AND prompt+completion>0",
                [sid],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )?
            .unwrap_or((0, 0)))
    }

    /// 最近 N 天每天的总量（趋势图）。
    pub fn usage_recent_days(&self, days: i64) -> BResult<Vec<(String, i64, i64)>> {
        self.all(
            "SELECT strftime('%Y-%m-%d',at/1000,'unixepoch','localtime') d,
                    IFNULL(SUM(prompt),0)+IFNULL(SUM(completion),0) total,
                    COUNT(*)
             FROM usage
             WHERE prompt+completion>0 AND at >= ?1
             GROUP BY d ORDER BY d",
            [crate::core::base::clock::now_ms() - days.max(1) * 86_400_000],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
    }

    /// 清空账本（用户主动"重置统计"）。
    pub fn usage_clear(&self) -> BResult<usize> {
        self.exec("DELETE FROM usage", [])
    }

    /// 删掉某天之前的账（保留最近 N 天）。
    pub fn usage_trim_before(&self, at: i64) -> BResult<usize> {
        self.exec("DELETE FROM usage WHERE at < ?1", [at])
    }
}

/// 兼容两种 id：标准 UUID 与 24 位六段会话 id。
fn looks_uuid(s: &str) -> bool {
    let parts: Vec<&str> = s.split('-').collect();
    parts.len() == 5
        && parts.iter().map(|p| p.len()).eq([8, 4, 4, 4, 12])
        && s.chars().all(|c| c == '-' || c.is_ascii_hexdigit())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn record_and_aggregate() {
        let db = Db::memory().unwrap();
        let sid = db.new_session("s", "").unwrap().id;
        db.record_usage(crate::core::base::clock::now_ms(), "deepseek", "chat", &sid, 100, 20, 50)
            .unwrap();
        db.record_usage(crate::core::base::clock::now_ms(), "deepseek", "chat", &sid, 200, 0, 100)
            .unwrap();
        let (n, p, c, o) = db.usage_totals().unwrap();
        assert_eq!(n, 2);
        assert_eq!(p, 300);
        assert_eq!(c, 20);
        assert_eq!(o, 150);
        assert_eq!(db.usage_session(&sid).unwrap(), (300, 150));
        assert_eq!(db.usage_daily().unwrap().len(), 1);
    }

    #[test]
    fn bad_rows_are_dropped() {
        let db = Db::memory().unwrap();
        let now = crate::core::base::clock::now_ms();
        db.record_usage(now, "x", "m", "", 0, 0, 0).unwrap();
        db.record_usage(now, "x", "m", "", -1, 0, 5).unwrap();
        db.record_usage(now, "x", "m", "", 9_000_000, 0, 1).unwrap();
        assert_eq!(db.usage_totals().unwrap().0, 0);
    }

    #[test]
    fn future_timestamp_is_corrected() {
        let db = Db::memory().unwrap();
        let far = crate::core::base::clock::now_ms() + 30 * 86_400_000;
        db.record_usage(far, "x", "m", "", 10, 0, 10).unwrap();
        let daily = db.usage_daily().unwrap();
        assert_eq!(daily[0].0, crate::core::base::clock::today());
    }
}
