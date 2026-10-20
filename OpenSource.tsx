import { defineComponent, ref, onMounted } from 'vue'

//仓库坐标：星级/分支/issue/贡献者全部走 GitHub 公开 API，匿名限速 60 次/小时，所以本地缓存 10 分钟
const REPO = 'mubai0122/baicodex'
const CACHE_KEY = 'gh_snapshot_' + REPO
const CACHE_TTL = 10 * 60 * 1000

type Snapshot = {
  stars: number
  forks: number
  issues: number
  contributors: number
  avatars: string[]
  ownerAvatar: string
  at: number
}

function readCache(): Snapshot | null {
  try {
    const raw = localStorage.getItem(CACHE_KEY)
    if (!raw) return null
    const s = JSON.parse(raw) as Snapshot
    if (!s || Date.now() - s.at > CACHE_TTL) return null
    return s
  } catch {
    return null
  }
}

async function fetchSnapshot(): Promise<Snapshot> {
  const headers = { Accept: 'application/vnd.github+json' }
  const r = await fetch('https://api.github.com/repos/' + REPO, { headers })
  if (!r.ok) throw new Error('repo ' + r.status)
  const d = await r.json()

  //空仓库的 contributors 返回 204（无内容），不能当失败处理
  let contributors = 0
  let avatars: string[] = []
  const c = await fetch('https://api.github.com/repos/' + REPO + '/contributors?per_page=100', { headers })
  if (c.status === 200) {
    const list = await c.json()
    if (Array.isArray(list)) {
      contributors = list.length
      avatars = list.slice(0, 5).map((u: { avatar_url: string }) => u.avatar_url)
    }
  }

  return {
    stars: d.stargazers_count || 0,
    forks: d.forks_count || 0,
    issues: d.open_issues_count || 0,
    contributors,
    avatars,
    ownerAvatar: d.owner && d.owner.avatar_url ? d.owner.avatar_url : '',
    at: Date.now(),
  }
} //按仓库拉一次快照，贡献者单独一趟请求

//大数字压成 1.2k；没数据就显示破折号，绝不写死假数字
function fmtNum(n: number | null) {
  if (n === null) return '—'
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(n)
}

export default defineComponent({
  name: 'OpenSource',
  setup() {

    // 移除了 GSAP 动画，防止底部高度计算错误导致闪烁
    const snap = ref<Snapshot | null>(readCache())
    const failed = ref(false)

    onMounted(async () => {
      if (snap.value) return
      try {
        snap.value = await fetchSnapshot()
        localStorage.setItem(CACHE_KEY, JSON.stringify(snap.value))
      } catch {
        failed.value = true
      }
    }) //挂载后拉一次真实数据，命中缓存就不重复请求

    return () => (
      <section id="opensource" style={{ background: 'var(--bg)', padding: '100px 0' }}>
        <div class="container os-content" style={{
          display: 'grid',
          gridTemplateColumns: '1fr 1fr',
          gap: '60px',
          alignItems: 'center',
        }}>
          {/* 左侧文字 */}
          <div>
            <div style={{
              display: 'inline-flex',
              alignItems: 'center',
              gap: '8px',
              padding: '6px 14px',
              borderRadius: '999px',
              background: 'var(--bg-soft)',
              border: '1px solid var(--border)',
              fontSize: '13px',
              fontWeight: 600,
              color: 'var(--text)',
              marginBottom: '20px'
            }}>
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
                <path d="M9 18c-4.51 2-5-2-7-2" />
              </svg>
              开源项目
            </div>
            <h2 class="section-title">完全免费<br />永久开源</h2>
            <p class="section-sub" style={{ maxWidth: '400px' }}>
              BaiCode 不为盈利而生。代码全部公开，没有任何隐藏收费、次数限制或高级解锁。你可以自由查看、修改甚至分发它。
            </p>

            <div style={{ marginTop: '32px', display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
              <a href="/api/download/app-release-sign.apk" style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '14px 28px',
                borderRadius: '12px',
                background: 'var(--accent)',
                color: '#fff',
                textDecoration: 'none',
                fontSize: '15px',
                fontWeight: 600,
                transition: 'opacity 0.2s'
              }}
              onMouseEnter={(e) => (e.currentTarget.style.opacity = '0.8')}
              onMouseLeave={(e) => (e.currentTarget.style.opacity = '1')}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3" />
                </svg>
                下载 APK
              </a>
              <a href="https://github.com/mubai0122/baicodex" style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: '8px',
                padding: '14px 28px',
                borderRadius: '12px',
                background: 'var(--bg-soft)',
                border: '1px solid var(--border)',
                color: 'var(--text)',
                textDecoration: 'none',
                fontSize: '15px',
                fontWeight: 600,
                transition: 'background 0.2s'
              }}
              onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--border)')}
              onMouseLeave={(e) => (e.currentTarget.style.background = 'var(--bg-soft)')}
              >
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
                  <path d="M9 18c-4.51 2-5-2-7-2" />
                </svg>
                浏览源码
              </a>
            </div>
            
            {/* 协议与社区标签 */}
            <div style={{ marginTop: '40px', display: 'flex', alignItems: 'center', gap: '16px', flexWrap: 'wrap' }}>
              <span style={{ fontSize: '13px', fontWeight: 600, color: 'var(--text-mute)' }}>开源协议：</span>
              <span style={{ fontSize: '12px', padding: '4px 10px', background: 'var(--bg-soft)', border: '1px solid var(--border)', borderRadius: '6px', fontWeight: 500 }}>GPL-3.0 License</span>
              <span style={{ fontSize: '12px', padding: '4px 10px', background: 'rgba(52,199,89,0.1)', color: 'var(--green)', borderRadius: '6px', fontWeight: 500 }}>Active Community</span>
            </div>
          </div>

          {/* 右侧：数据统计面板 */}
          <div class="os-github-card" style={{
            background: 'var(--bg-soft)',
            borderRadius: '24px',
            border: '1px solid var(--border)',
            padding: '32px',
            boxShadow: '0 20px 40px rgba(0,0,0,0.05)'
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '24px' }}>
              <div style={{ width: '40px', height: '40px', borderRadius: '10px', background: '#fff', border: '1px solid var(--border)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" color="var(--text)">
                   <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z" />
                </svg>
              </div>
              <div>
                <div style={{ fontSize: '16px', fontWeight: 700 }}>BaiCode / App</div>
                <div style={{ fontSize: '12px', color: 'var(--text-sub)' }}>{REPO}</div>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '12px', marginBottom: '24px' }}>
              {[
                { label: 'Stars', value: fmtNum(snap.value ? snap.value.stars : null), id: 'github-stars' },
                { label: 'Forks', value: fmtNum(snap.value ? snap.value.forks : null), id: 'github-forks' },
                { label: 'Issues', value: fmtNum(snap.value ? snap.value.issues : null), id: 'github-issues' },
              ].map(stat => (
                <div key={stat.label} style={{ background: 'var(--bg)', padding: '16px', borderRadius: '12px', border: '1px solid var(--border)', textAlign: 'center' }}>
                  <div id={stat.id} style={{ fontSize: '20px', fontWeight: 800 }}>{stat.value}</div>
                  <div style={{ fontSize: '12px', color: 'var(--text-sub)', marginTop: '4px' }}>{stat.label}</div>
                </div>
              ))}
            </div>

            {failed.value && (
              <div style={{ fontSize: '12px', color: 'var(--text-mute)', margin: '-12px 0 16px' }}>
                GitHub 数据没拿到（可能触发了匿名限流），刷新页面重试
              </div>
            )}

            <div style={{
              fontFamily: 'monospace',
              background: '#1d1d1f',
              borderRadius: '12px',
              padding: '16px',
              color: '#a1a1a6',
              fontSize: '12.5px',
              lineHeight: 1.8,
              border: '1px solid #333',
              overflowX: 'auto', /* 兜底：如果实在放不下就横向滑动 */
              whiteSpace: 'pre-wrap', /* 核心：遇到长链接强制换行 */
              wordBreak: 'break-all' /* 强制截断长单词/链接 */
            }}>
              <div><span style={{ color: '#34c759' }}>$</span> git clone https://github.com/mubai0122/baicodex.git</div>
              <div><span style={{ color: '#34c759' }}>$</span> cd baicodex</div>
              <div><span style={{ color: '#34c759' }}>$</span> ./gradlew assembleRelease</div>
              <div style={{ color: '#6e6e73' }}>&gt; Building release APK...</div>
              <div style={{ color: '#0071e3' }}>✓ app/build/outputs/apk/release/</div>
            </div>
            
            {/* 底部贡献者信息 */}
            <div style={{ display: 'flex', alignItems: 'center', marginTop: '24px', gap: '12px' }}>
            <div id="github-avatars" style={{ display: 'flex' }}>
              {(snap.value && snap.value.avatars.length
                ? snap.value.avatars
                : snap.value && snap.value.ownerAvatar
                  ? [snap.value.ownerAvatar]
                  : []
              ).map((avatar, idx) => (
                <img
                  key={avatar + idx}
                  src={avatar}
                  alt="Contributor"
                  style={{
                    width: '28px', height: '28px', borderRadius: '50%',
                    border: '2px solid var(--bg-soft)',
                    objectFit: 'cover',
                    marginLeft: idx === 0 ? 0 : '-8px'
                  }}
                  onError={(e) => {
                    //兜底：头像加载失败就隐藏，不显示裂图
                    (e.target as HTMLImageElement).style.display = 'none';
                  }}
                />
              ))}
            </div>
            <span id="github-contributors-count" style={{ fontSize: '13px', color: 'var(--text-sub)', fontWeight: 500 }}>
              {snap.value
                ? snap.value.contributors > 0
                  ? `${snap.value.contributors} 位贡献者正在参与`
                  : '等待第一位贡献者'
                : '读取中…'}
            </span>
            </div>
          </div>
        </div>
      </section>
    )
  }
})