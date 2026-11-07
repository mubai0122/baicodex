/**
 * 弹簧。
 *
 * 玻璃的按压回弹不能用 CSS 的 `transition`：transition 是时间驱动的，手指按住
 * 不动时它会自己走完；弹簧是状态驱动的，按住就停在目标值上。手感差得很明显。
 *
 * 积分用固定步长（1/240 s），这样 60Hz 和 165Hz 屏幕上的回弹曲线一致——按帧
 * 积分的话，高刷屏上弹簧会变得又快又硬。
 */

export type SpringListener = (value: number) => void;

export class Spring {
  private value: number;
  private velocity = 0;
  private target: number;
  private raf = 0;
  private last = 0;
  private acc = 0;
  private listeners = new Set<SpringListener>();

  constructor(
    private readonly stiffness: number = 420,
    private readonly damping: number = 34,
    initial = 1,
  ) {
    this.value = initial;
    this.target = initial;
  }

  get current(): number {
    return this.value;
  }

  /** 目标值。不会立刻跳过去，会带着速度追过去。 */
  to(target: number, immediate = false): void {
    this.target = target;
    if (immediate) {
      this.value = target;
      this.velocity = 0;
      this.emit();
      return;
    }
    this.start();
  }

  /** 给一个瞬时速度（比如从拖拽松手时把手速带进弹簧）。 */
  kick(v: number): void {
    this.velocity += v;
    this.start();
  }

  onChange(fn: SpringListener): () => void {
    this.listeners.add(fn);
    return () => this.listeners.delete(fn);
  }

  stop(): void {
    if (this.raf) cancelAnimationFrame(this.raf);
    this.raf = 0;
  }

  private start(): void {
    if (this.raf) return;
    this.last = performance.now();
    this.acc = 0;
    this.raf = requestAnimationFrame(this.tick);
  }

  private tick = (now: number): void => {
    const dt = Math.min(64, now - this.last) / 1000;
    this.last = now;
    this.acc += dt;

    const step = 1 / 240;
    let guard = 0;
    while (this.acc >= step && guard++ < 64) {
      this.integrate(step);
      this.acc -= step;
    }
    this.emit();

    // 足够接近且几乎不动了：钉住，停帧
    if (Math.abs(this.value - this.target) < 0.0008 && Math.abs(this.velocity) < 0.004) {
      this.value = this.target;
      this.velocity = 0;
      this.emit();
      this.raf = 0;
      return;
    }
    this.raf = requestAnimationFrame(this.tick);
  };

  private integrate(dt: number): void {
    // 半隐式欧拉：先更新速度再更新位置，比显式欧拉稳定得多
    const a = -this.stiffness * (this.value - this.target) - this.damping * this.velocity;
    this.velocity += a * dt;
    this.value += this.velocity * dt;
  }

  private emit(): void {
    for (const fn of this.listeners) fn(this.value);
  }
}

/** 临界阻尼系数。给定刚度求"刚好不抖"的阻尼。 */
export function criticalDamping(stiffness: number): number {
  return 2 * Math.sqrt(stiffness);
}
