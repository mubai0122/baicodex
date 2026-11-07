//! 应用装配。
//!
//! 把配置、存储、安全、网络、执行拼成一个"可用的 App"，供 Agent 循环与各个前端共用。
//!
//! ## 并发模型
//!
//! - 配置：[ConfigStore] 内部 `RwLock`，读多写少。
//! - 运行态：一把 `std::sync::Mutex<AgentState>`，只在改状态时短暂持有，**不跨 await**。
//! - 记忆 / 技能 / MCP：各自的 `RwLock`。
//! - 工具确认 / 提问 / 计划：`oneshot` 把协程挂起，界面回填后唤醒。
//!
//! ## 为什么 App 这么大还要集中
//!
//! 因为它就是"依赖注入容器"：所有模块都要拿到同一份配置、同一个数据库、同一个审计器。
//! 拆成多个小对象只会让调用方到处传参。真正该拆的是**行为**——那些在 `agent` 与
//! `tool` 里，App 只负责持有与转发。

pub mod event;
pub mod state;

use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, RwLock};

use tokio::sync::broadcast;

use crate::core::base::error::{BError, BResult};
use crate::core::feature::config::schema::Config;
use crate::core::feature::config::ConfigStore;
use crate::core::feature::mcp::Mcp;
use crate::core::feature::memory::Memory;
use crate::core::feature::model::message::{ChatMsg, ToolCall};
use crate::core::feature::model::session::Route;
use crate::core::feature::persona::Personas;
use crate::core::feature::workflow::Flows;
use crate::core::feature::skill::{SkillPacks, Skills};
use crate::core::feature::storage::Db;
use crate::core::security::audit::Audit;
use crate::core::security::policy::PolicySet;
use crate::core::security::route::Router;
use crate::core::security::sandbox::Sandbox;
use crate::core::security::vault::Vault;

pub use event::{AppEvent, EventSink};
pub use state::{AgentState, AskOption, AskQuestion, AskReq, PendingTool, PlanReq, PlanVerdict, RunningTool};

/// 应用。
pub struct App {
    /// 数据根（`~/.baicode`）。
    base: PathBuf,
    pub cfg: ConfigStore,
    pub db: Db,
    pub vault: Vault,
    pub audit: Audit,
    pub router: Router,
    pub sandbox: RwLock<Sandbox>,
    pub policy: RwLock<PolicySet>,
    pub memory: RwLock<Memory>,
    pub skills: RwLock<Skills>,
    pub packs: RwLock<SkillPacks>,
    pub mcp: RwLock<Mcp>,
    pub personas: RwLock<Personas>,
    pub flows: RwLock<Flows>,
    pub state: std::sync::Mutex<AgentState>,
    pub events: broadcast::Sender<AppEvent>,
    /// 直连接收器（JNI 用）。
    sinks: RwLock<Vec<Arc<dyn EventSink>>>,
    pub started_at: i64,
    /// 停止信号。
    pub stop_flag: AtomicBool,
    pub stop_notify: tokio::sync::Notify,
}

impl std::fmt::Debug for App {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("App")
            .field("base", &self.base)
            .field("session", &self.session_id())
            .finish_non_exhaustive()
    }
}

impl App {
    /// 启动：读配置、开库、探测能力、加载技能/人设/MCP。
    pub fn boot(base: impl Into<PathBuf>) -> BResult<Arc<Self>> {
        let mut base = base.into();
        // 空串必须换成默认数据根。否则 `ensure_dir("")` 会成功、之后所有文件都
        // 落在进程当前目录——桌面上是启动目录，安卓上是 `/`，两者都不能写。
        if base.as_os_str().is_empty() {
            base = crate::core::base::dirs::default_base();
        }
        crate::core::base::fsx::ensure_dir(&base)?;
        // 数据根定一次，exec 层（截图、临时脚本、ui.xml）就都跟着走同一份。
        crate::core::base::dirs::set_base(&base);
        crate::core::base::dirs::sweep();

        let (cfg, loaded) = ConfigStore::open(&base);
        if loaded.migrated {
            // 旧配置迁移后立刻回写，避免下次启动又走一遍映射
            let _ = cfg.save();
        }

        let db = crate::core::feature::storage::open(&base)?;
        let vault = Vault::new(&base);

        // 旧配置里的明文 key → 凭据库。迁完就不再保留明文。
        let mut migrated_keys = 0;
        if !loaded.legacy_api_key.is_empty() {
            vault.put(crate::core::security::vault::BUCKET_API, "provider:default", &loaded.legacy_api_key)?;
            migrated_keys += 1;
        }
        for (i, k) in loaded.legacy_keys.iter().enumerate() {
            if !k.trim().is_empty() {
                vault.put(
                    crate::core::security::vault::BUCKET_API,
                    &format!("provider:key:{i}"),
                    k,
                )?;
                migrated_keys += 1;
            }
        }
        if migrated_keys > 0 {
            vault.flush_all()?;
            crate::log_info!("app", "迁移了 {migrated_keys} 个明文 key 到凭据库");
        }
        if cfg.get().provider.key_slots == 0 && migrated_keys > 0 {
            cfg.update(|c| c.provider.key_slots = migrated_keys);
            let _ = cfg.save();
        }

        let audit = Audit::open(base.join("audit.jsonl"));
        audit.set_enabled(cfg.get().security.audit);

        let perms = crate::core::security::perm::Perms::cached();
        let snap = cfg.get();
        let router = Router::new(snap.agent.work_mode, snap.security.zero_width, perms);
        let _ = router.probe();

        let mut sb = Sandbox::new(&base);
        for r in &snap.security.extra_roots {
            if !r.trim().is_empty() {
                let p = crate::core::base::path::expand_tilde(r);
                let _ = crate::core::base::fsx::ensure_dir(&p);
            }
        }
        for p in &snap.security.allow_sensitive {
            sb.allow_path(p);
        }

        let policy = crate::core::security::policy::store::load(crate::core::security::policy::store::path(&base));

        let memory = Memory::load(&db)?;
        let skills = Skills::load(&base, 0);
        let packs = SkillPacks::load_dir(&base.join("skill"));
        let mcp = Mcp::load(&base);
        let personas = Personas::load(&base);
        let flows = Flows::load(&base);

        let (tx, _) = broadcast::channel(512);
        let st = AgentState::default();

        let app = Arc::new(Self {
            base,
            cfg,
            db,
            vault,
            audit,
            router,
            sandbox: RwLock::new(sb),
            policy: RwLock::new(policy),
            memory: RwLock::new(memory),
            skills: RwLock::new(skills),
            packs: RwLock::new(packs),
            mcp: RwLock::new(mcp),
            personas: RwLock::new(personas),
            flows: RwLock::new(flows),
            state: std::sync::Mutex::new(st),
            events: tx,
            sinks: RwLock::new(Vec::new()),
            started_at: crate::core::base::clock::now_ms(),
            stop_flag: AtomicBool::new(false),
            stop_notify: tokio::sync::Notify::new(),
        });

        app.ensure_session()?;
        Ok(app)
    }

    pub fn base(&self) -> &Path {
        &self.base
    }

    /// 订阅事件。
    pub fn subscribe(&self) -> broadcast::Receiver<AppEvent> {
        self.events.subscribe()
    }

    /// 挂直连接收器。
    pub fn add_sink(&self, sink: Arc<dyn EventSink>) {
        if let Ok(mut s) = self.sinks.write() {
            s.push(sink);
        }
    }

    pub fn emit(&self, e: AppEvent) {
        let _ = self.events.send(e.clone());
        if let Ok(sinks) = self.sinks.read() {
            for s in sinks.iter() {
                s.on_event(e.clone());
            }
        }
    }

    /// 状态栏。
    pub fn status(&self, text: impl Into<String>) {
        let t = text.into();
        if let Ok(mut st) = self.state.lock() {
            st.status = t.clone();
        }
        self.emit(AppEvent::Status { text: t });
    }

    /// 一次性提示。
    pub fn notice(&self, text: impl Into<String>) {
        self.emit(AppEvent::Notice { text: text.into() });
    }

    /// 配置快照。
    pub fn cfg(&self) -> Config {
        self.cfg.get()
    }

    /// 改配置（不自动落盘）。
    pub fn update_cfg(&self, f: impl FnOnce(&mut Config)) -> Config {
        self.cfg.update(f)
    }

    /// 改配置并落盘。
    pub fn save_cfg(&self, f: impl FnOnce(&mut Config)) -> BResult<Config> {
        let c = self.cfg.update(f);
        self.cfg.save()?;
        Ok(c)
    }

    // —— 会话 ——

    /// 确保有当前会话。
    pub fn ensure_session(&self) -> BResult<()> {
        if self.lock_state().session_id.is_some() {
            return Ok(());
        }
        let s = match self.db.latest_session()? {
            Some(s) => s,
            None => {
                let s = self.db.new_session("新会话", "默认")?;
                let ws = self.attach_workspace(&s.id)?;
                let _ = ws;
                self.db.session(&s.id)?.unwrap_or(s)
            }
        };
        {
            let mut st = self.lock_state();
            st.session_id = Some(s.id.clone());
            st.session_title = s.title.clone();
        }
        if !s.persona.is_empty() {
            self.cfg.update(|c| c.agent.persona = s.persona.clone());
        }
        if !s.workspace.is_empty() {
            self.cfg.update(|c| c.workspace.current = s.workspace.clone());
        }
        let msgs = self.db.messages(&s.id)?;
        {
            let mut st = self.lock_state();
            st.messages = msgs;
        }
        self.refresh_sessions();
        Ok(())
    }

    /// 给会话分配工作区。
    pub fn attach_workspace(&self, sid: &str) -> BResult<String> {
        let (ws, new_default) = {
            let cfg = self.cfg();
            crate::exec::workspace::new_session_workspace(&cfg)
        };
        if let Some(d) = new_default {
            self.cfg.update(|c| c.workspace.root = d);
        }
        self.cfg.update(|c| c.workspace.current = ws.clone());
        self.db.set_workspace(sid, &ws)?;
        let persona = self.cfg().agent.persona;
        self.db.set_persona(sid, &persona)?;
        crate::exec::workspace::invalidate();
        Ok(ws)
    }

    /// 新会话。
    pub fn new_session(&self) -> BResult<()> {
        let s = self.db.new_session("新会话", "默认")?;
        self.attach_workspace(&s.id)?;
        let opener = {
            let cfg = self.cfg();
            self.personas.read().map(|p| p.opener(&cfg.agent.persona)).unwrap_or_default()
        };
        let mut msgs = Vec::new();
        if !opener.is_empty() {
            let m = self.db.append(&s.id, &ChatMsg::assistant(opener))?;
            msgs.push(m);
        }
        {
            let mut st = self.lock_state();
            st.session_id = Some(s.id.clone());
            st.session_title = s.title.clone();
            st.messages = msgs;
            st.status.clear();
            st.last_error = None;
        }
        self.refresh_sessions();
        Ok(())
    }

    /// 打开会话。
    pub fn open_session(&self, id: &str) -> BResult<()> {
        let Some(row) = self.db.session(id)? else {
            return Err(BError::not_found(format!("会话 {id} 不存在")));
        };
        let msgs = self.db.messages(id)?;
        if !row.persona.is_empty() {
            self.cfg.update(|c| c.agent.persona = row.persona.clone());
        }
        if !row.workspace.is_empty() {
            self.cfg.update(|c| c.workspace.current = row.workspace.clone());
        }
        crate::exec::workspace::invalidate();
        {
            let mut st = self.lock_state();
            st.session_id = Some(id.to_string());
            st.session_title = row.title.clone();
            st.messages = msgs;
            st.status.clear();
            st.last_error = None;
        }
        self.refresh_sessions();
        Ok(())
    }

    /// 刷新会话列表并广播。
    pub fn refresh_sessions(&self) {
        if let Ok(list) = self.db.sessions() {
            if let Ok(mut st) = self.state.lock() {
                st.sessions = list.clone();
            }
            self.emit(AppEvent::Sessions { list });
        }
    }

    pub fn messages(&self) -> Vec<ChatMsg> {
        self.lock_state().messages.clone()
    }

    pub fn session_id(&self) -> Option<String> {
        self.lock_state().session_id.clone()
    }

    pub fn is_busy(&self) -> bool {
        self.lock_state().busy
    }

    /// 记一条本地消息（不走模型）。
    pub fn append_local(&self, m: ChatMsg) -> BResult<ChatMsg> {
        let sid = match self.session_id() {
            Some(s) => s,
            None => {
                self.ensure_session()?;
                self.session_id().unwrap_or_default()
            }
        };
        if sid.is_empty() {
            return Err(BError::internal("没有可用会话"));
        }
        let saved = self.db.append(&sid, &m)?;
        let list = self.db.messages(&sid)?;
        {
            let mut st = self.lock_state();
            if st.session_id.as_deref() == Some(sid.as_str()) {
                st.messages = list;
            }
        }
        self.emit(AppEvent::Message { message: Box::new(saved.clone()) });
        Ok(saved)
    }

    // —— 交互闸门 ——

    /// 工具确认：策略为 allow / deny 直接定，否则挂起等用户。
    pub async fn confirm_tool(
        &self,
        call: &ToolCall,
        args: &serde_json::Value,
        route: Route,
        high_risk: bool,
    ) -> bool {
        let risk = crate::core::feature::tool::registry::risk_of(&call.name);
        let decision = self
            .policy
            .read()
            .map(|p| p.decide(&call.name, &args.to_string(), risk))
            .unwrap_or_else(|_| crate::core::security::policy::Decision::deny("策略表不可用"));
        match decision.policy {
            crate::core::security::policy::Policy::Allow => return true,
            crate::core::security::policy::Policy::Deny => {
                self.notice(format!("{} 被拒绝：{}", call.name, decision.reason));
                self.audit.log("agent", "tool.deny", &call.name, false, decision.reason);
                return false;
            }
            crate::core::security::policy::Policy::Ask => {}
        }
        let (tx, rx) = tokio::sync::oneshot::channel();
        {
            let mut st = self.lock_state();
            st.pending = Some(PendingTool {
                call: call.clone(),
                args: args.clone(),
                route,
                high_risk,
                tx,
            });
        }
        self.emit(AppEvent::ConfirmRequest);
        rx.await.unwrap_or(false)
    }

    /// 界面回填确认结果。
    pub fn resolve_tool(&self, allow: bool, permanent: bool) {
        let pending = {
            let mut st = self.lock_state();
            st.pending.take()
        };
        if let Some(p) = pending {
            if permanent {
                if let Ok(mut pol) = self.policy.write() {
                    pol.set(
                        p.call.name.clone(),
                        if allow {
                            crate::core::security::policy::Policy::Allow
                        } else {
                            crate::core::security::policy::Policy::Deny
                        },
                    );
                    let _ = crate::core::security::policy::store::save(
                        crate::core::security::policy::store::path(&self.base),
                        &pol,
                    );
                }
            }
            self.audit.log(
                "user",
                "tool.confirm",
                &p.call.name,
                allow,
                if permanent { "永久" } else { "本次" },
            );
            let _ = p.tx.send(allow);
        }
    }

    /// 挂起提问。
    pub async fn ask_user(&self, questions: Vec<AskQuestion>) -> Vec<String> {
        let (tx, rx) = tokio::sync::oneshot::channel();
        {
            let mut st = self.lock_state();
            st.ask = Some(AskReq { questions, tx });
        }
        self.emit(AppEvent::AskRequest);
        rx.await.unwrap_or_default()
    }

    pub fn answer_ask(&self, answers: Vec<String>) {
        let req = {
            let mut st = self.lock_state();
            st.ask.take()
        };
        if let Some(r) = req {
            let _ = r.tx.send(answers);
        }
    }

    /// 计划确认。
    pub async fn approve_plan(&self, text: &str) -> PlanVerdict {
        let (tx, rx) = tokio::sync::oneshot::channel();
        {
            let mut st = self.lock_state();
            st.plan = Some(PlanReq { text: text.to_string(), tx });
        }
        self.emit(AppEvent::PlanRequest);
        rx.await.unwrap_or(PlanVerdict::Dismiss)
    }

    pub fn answer_plan(&self, v: PlanVerdict) {
        let req = {
            let mut st = self.lock_state();
            st.plan.take()
        };
        if let Some(r) = req {
            let _ = r.tx.send(v);
        }
    }

    /// 工具开始。
    pub fn begin_tool(&self, name: &str, args: &str, from_sub: bool) -> u64 {
        let mut st = self.lock_state();
        st.run_seq += 1;
        let seq = st.run_seq;
        st.running.push(RunningTool {
            seq,
            name: name.to_string(),
            args: args.to_string(),
            from_sub,
        });
        drop(st);
        self.emit(AppEvent::ToolStart {
            seq,
            name: name.to_string(),
            args: args.to_string(),
            from_sub,
        });
        seq
    }

    pub fn end_tool(&self, seq: u64) {
        if let Ok(mut st) = self.state.lock() {
            st.running.retain(|t| t.seq != seq);
        }
        self.emit(AppEvent::ToolEnd { seq });
    }

    pub fn clear_tools(&self) {
        if let Ok(mut st) = self.state.lock() {
            st.running.clear();
        }
    }

    /// 停止：把挂起的等待都放掉，别让协程永远挂着。
    pub fn stop(&self) {
        self.stop_flag.store(true, Ordering::SeqCst);
        self.stop_notify.notify_waiters();
        {
            let mut st = self.lock_state();
            if let Some(p) = st.pending.take() {
                let _ = p.tx.send(false);
            }
            if let Some(a) = st.ask.take() {
                let _ = a.tx.send(Vec::new());
            }
            if let Some(p) = st.plan.take() {
                let _ = p.tx.send(PlanVerdict::Dismiss);
            }
            st.busy = false;
            st.running.clear();
            st.clear_stream();
            st.status = "已停止".into();
        }
        self.emit(AppEvent::Busy { busy: false });
    }

    /// 每轮开始前清掉停止标志。
    pub fn clear_stop(&self) {
        self.stop_flag.store(false, Ordering::SeqCst);
    }

    pub fn stopped(&self) -> bool {
        self.stop_flag.load(Ordering::SeqCst)
    }

    /// 退出前收尾：落盘配置与凭据、刷 WAL。
    pub fn shutdown(&self) {
        let _ = self.cfg.save();
        let _ = self.vault.flush_all();
        self.db.checkpoint();
        self.audit.log("system", "app.exit", "", true, "");
    }

    pub(crate) fn lock_state(&self) -> std::sync::MutexGuard<'_, AgentState> {
        self.state.lock().unwrap_or_else(|e| e.into_inner())
    }
}

/// 默认数据根。Android 上由外壳传入，桌面用 `~/.baicode`。
///
/// 真正的判定在 [crate::core::base::dirs::default_base]——那里是进程级事实，
/// 这里只是给 App 的调用方一个语义更明确的入口。
pub fn default_base() -> PathBuf {
    crate::core::base::dirs::default_base()
}
