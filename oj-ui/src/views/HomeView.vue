<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const featureSectionRef = ref<HTMLElement | null>(null)

// 处理主按钮跳转到题库页。
function handleStartPractice() {
  void router.push('/problems')
}

// 处理次按钮滚动到能力介绍区域。
function handleLearnMore() {
  featureSectionRef.value?.scrollIntoView({
    behavior: 'smooth',
    block: 'start',
  })
}
</script>

<template>
  <div class="home-page">
    <section class="hero">
      <p class="hero-badge">
        <span class="hero-badge-dot" aria-hidden="true"></span>
        Agent 驱动在线刷题
      </p>
      <h1 class="hero-title">
        用<span>Agent OJ</span>
        <br />
        持续提升算法实战力
      </h1>
      <p class="hero-description">
        AgentOJ 将动态出题、难度调节、代码执行与智能评审串成同一条链路，
        让你在一次会话里完成从练题到复盘的完整闭环。
      </p>

      <div class="hero-actions">
        <button type="button" class="hero-action hero-action-primary" @click="handleStartPractice">
          开始刷题 ->
        </button>
        <button type="button" class="hero-action hero-action-secondary" @click="handleLearnMore">
          查看核心能力
        </button>
      </div>

      <div class="hero-stats" aria-label="平台核心指标">
        <article class="hero-stat">
          <p class="hero-stat-value">3</p>
          <p class="hero-stat-label">语言模板</p>
        </article>
        <article class="hero-stat">
          <p class="hero-stat-value">4</p>
          <p class="hero-stat-label">核心链路</p>
        </article>
        <article class="hero-stat">
          <p class="hero-stat-value">实时</p>
          <p class="hero-stat-label">流式反馈</p>
        </article>
      </div>
    </section>

    <section ref="featureSectionRef" class="feature-grid" aria-label="平台能力">
      <article class="feature-card">
        <span class="feature-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="5" y="3" width="14" height="18" rx="2.5" stroke="currentColor" stroke-width="1.8" />
            <path d="M9 8H15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M9 12H15" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M9 16H13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
          </svg>
        </span>
        <h2>动态出题与调难度</h2>
        <p>根据你的目标生成新题，并在同一会话中按反馈动态调整难度与题型。</p>
      </article>

      <article class="feature-card">
        <span class="feature-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M13 3L6 13H12L11 21L18 11H12L13 3Z" stroke="currentColor" stroke-width="1.8" stroke-linejoin="round" />
          </svg>
        </span>
        <h2>代码执行 + 智能评审</h2>
        <p>提交后自动运行测试用例，返回问题定位、复杂度建议与可执行的优化方向。</p>
      </article>

      <article class="feature-card">
        <span class="feature-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M5 18V10" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M11.5 18V6" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <path d="M18 18V13" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
            <rect x="4" y="18" width="15" height="2" rx="1" fill="currentColor" />
          </svg>
        </span>
        <h2>链路可追踪可复盘</h2>
        <p>从意图识别到结果回复全程可追踪，支持多轮上下文记忆与训练过程复盘。</p>
      </article>
    </section>
  </div>
</template>

<style scoped>
@import url('https://fonts.googleapis.com/css2?family=Noto+Sans+SC:wght@400;500;600;700;800&family=Plus+Jakarta+Sans:wght@600;700;800&display=swap');

.home-page {
  --home-bg: #f5f7fc;
  --home-bg-soft: #eef2ff;
  --home-surface: #ffffff;
  --home-text: #1d2139;
  --home-muted: #626a81;
  --home-primary: #3558f0;
  --home-primary-deep: #2746d0;
  --home-primary-soft: rgba(53, 88, 240, 0.12);
  --home-border: #dde3f2;
  --home-card-shadow: 0 18px 40px rgba(27, 35, 70, 0.08);
  position: relative;
  min-height: 100%;
  padding: clamp(1.4rem, 4vw, 3rem) clamp(1rem, 5vw, 4rem) clamp(2.4rem, 5vw, 4rem);
  color: var(--home-text);
  font-family: 'Noto Sans SC', var(--font-ui);
  background: #ffffff;
}

.hero {
  max-width: 860px;
  margin: 0 auto;
  text-align: center;
  display: grid;
  justify-items: center;
  gap: 1.2rem;
  animation: hero-fade-up 0.55s ease-out both;
}

.hero-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.46rem;
  margin: 0;
  padding: 0.44rem 0.96rem;
  border-radius: 999px;
  background: var(--home-primary-soft);
  color: var(--home-primary);
  font-size: 1.05rem;
  font-weight: 600;
}

.hero-badge-dot {
  width: 0.66rem;
  height: 0.66rem;
  border-radius: 50%;
  background: #6f8fff;
  animation: badge-dot-pulse 1.8s ease-in-out infinite;
}

.hero-title {
  margin: 0;
  color: var(--home-text);
  font-family: 'Plus Jakarta Sans', 'Noto Sans SC', var(--font-heading);
  font-size: clamp(2.25rem, 8vw, 5rem);
  font-weight: 800;
  line-height: 1.08;
  letter-spacing: -0.03em;
}

.hero-title span {
  color: var(--home-primary);
}

.hero-description {
  margin: 0;
  max-width: 760px;
  color: var(--home-muted);
  font-size: clamp(1.02rem, 2.6vw, 1.32rem);
  line-height: 1.65;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 0.86rem;
}

.hero-action {
  min-height: 56px;
  min-width: 160px;
  padding: 0 1.4rem;
  border-radius: 12px;
  border: 1px solid transparent;
  font-size: 1.02rem;
  font-weight: 600;
  cursor: pointer;
  transition: background-color 0.22s ease, border-color 0.22s ease, color 0.22s ease, transform 0.22s ease;
}

.hero-action-primary {
  background: var(--home-primary);
  color: #ffffff;
  box-shadow: 0 12px 28px rgba(53, 88, 240, 0.28);
}

.hero-action-primary:hover {
  background: var(--home-primary-deep);
  transform: translateY(-2px);
}

.hero-action-secondary {
  background: transparent;
  border-color: var(--home-primary);
  color: var(--home-primary);
}

.hero-action-secondary:hover {
  background: var(--home-primary-soft);
  transform: translateY(-2px);
}

.hero-action:focus-visible {
  outline: 2px solid rgba(53, 88, 240, 0.4);
  outline-offset: 2px;
}

.hero-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(1rem, 3vw, 2.2rem);
  width: min(620px, 100%);
  margin-top: 0.4rem;
}

.hero-stat {
  display: grid;
  gap: 0.12rem;
  justify-items: center;
}

.hero-stat-value {
  margin: 0;
  font-family: 'Plus Jakarta Sans', 'Noto Sans SC', var(--font-heading);
  font-size: clamp(2rem, 4.2vw, 3.2rem);
  font-weight: 700;
  line-height: 1;
}

.hero-stat-label {
  margin: 0;
  color: #8a92a8;
  font-size: clamp(0.92rem, 1.9vw, 1.28rem);
  font-weight: 500;
}

.feature-grid {
  width: min(1320px, 100%);
  margin: clamp(2rem, 5vw, 3.8rem) auto 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(0.8rem, 2vw, 1.2rem);
}

.feature-card {
  border-radius: 20px;
  border: 1px solid var(--home-border);
  background: var(--home-surface);
  box-shadow: var(--home-card-shadow);
  padding: clamp(1rem, 2.2vw, 1.5rem);
  display: grid;
  gap: 0.72rem;
  animation: card-fade-up 0.5s ease-out both;
}

.feature-card:nth-child(2) {
  animation-delay: 0.06s;
}

.feature-card:nth-child(3) {
  animation-delay: 0.12s;
}

.feature-icon {
  width: 50px;
  height: 50px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--home-primary);
  background: var(--home-primary-soft);
}

.feature-icon svg {
  width: 26px;
  height: 26px;
}

.feature-card h2 {
  margin: 0;
  color: #1e243f;
  font-family: 'Plus Jakarta Sans', 'Noto Sans SC', var(--font-heading);
  font-size: clamp(1.35rem, 2.2vw, 1.9rem);
  line-height: 1.25;
}

.feature-card p {
  margin: 0;
  color: var(--home-muted);
  font-size: clamp(0.94rem, 1.5vw, 1.12rem);
  line-height: 1.72;
}

@keyframes hero-fade-up {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes badge-dot-pulse {
  0% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(111, 143, 255, 0.42);
  }
  60% {
    transform: scale(1.2);
    box-shadow: 0 0 0 10px rgba(111, 143, 255, 0);
  }
  100% {
    transform: scale(1);
    box-shadow: 0 0 0 0 rgba(111, 143, 255, 0);
  }
}

@keyframes card-fade-up {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 1000px) {
  .feature-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .hero-actions {
    width: 100%;
    display: grid;
    grid-template-columns: 1fr;
  }

  .hero-action {
    width: 100%;
  }

  .hero-stats {
    grid-template-columns: 1fr;
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero,
  .feature-card {
    animation: none;
  }

  .hero-action {
    transition: none;
  }

  .hero-badge-dot {
    animation: none;
  }
}
</style>
