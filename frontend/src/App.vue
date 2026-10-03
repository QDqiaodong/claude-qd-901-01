<template>
  <div class="shell">
    <!-- 左侧：竖向管道式导航（主干 + 五个阀门节点） -->
    <aside class="pipeline">
      <div class="pipeline-inner">
        <div class="trunk">
          <div class="trunk-flow"></div>
        </div>

        <div class="valve-list">
          <div class="valve-row" v-for="(node, i) in navNodes" :key="node.key">
            <div class="valve-assembly" :class="{ active: currentKey === node.key }"
                 @click="choose(node)" :title="node.label">
              <svg class="valve-svg" viewBox="0 0 46 46">
                <!-- 阀体 -->
                <path class="body-a" d="M4 22 L16 14 L16 32 L4 34 Z"></path>
                <path class="body-b" d="M42 22 L30 14 L30 32 L42 34 Z"></path>
                <rect class="body-c" x="14" y="16" width="18" height="14" rx="2"></rect>
                <!-- 阀杆 -->
                <rect class="stem" x="21.5" y="8" width="3" height="9" rx="1"></rect>
                <!-- 手轮 -->
                <g class="wheel" :class="{ spin: currentKey === node.key }">
                  <circle cx="23" cy="6" r="7.5"></circle>
                  <circle cx="23" cy="6" r="2.2" class="hub"></circle>
                  <line x1="15.5" y1="6" x2="30.5" y2="6"></line>
                  <line x1="23" y1="-1.5" x2="23" y2="13.5"></line>
                  <line x1="17.2" y1="0.2" x2="28.8" y2="11.8"></line>
                  <line x1="28.8" y1="0.2" x2="17.2" y2="11.8"></line>
                </g>
              </svg>
              <span class="valve-tag">V0{{ i + 1 }}</span>
            </div>

            <div class="valve-card">
              <div class="valve-title" @click="choose(node)">{{ node.label }}</div>
              <div class="valve-desc">{{ node.desc }}</div>
              <transition name="fold">
                <div class="valve-children" v-show="currentKey === node.key">
                  <a v-for="child in node.children" :key="child.hash" class="child-item"
                     :class="{ active: currentHash === child.hash }" @click="goto(child.hash)">
                    <span class="child-branch"></span>
                    <span class="child-label">{{ child.label }}</span>
                  </a>
                </div>
              </transition>
            </div>
          </div>
        </div>
      </div>

      <div class="pipeline-foot">
        <span class="flow-badge">管网在运 {{ overview.runningStationCount ?? '-' }} 台换热机组</span>
      </div>
    </aside>

    <!-- 右侧：标题 + 内容区 -->
    <main class="content">
      <header class="topbar">
        <div class="brand">
          <span class="brand-ring"></span>
          <div>
            <h1>城市集中供热 · 热源与管网调度管理</h1>
            <p>热源 → 管网 → 用户 → 服务　全流程运行调度</p>
          </div>
        </div>
        <div class="facts">
          <div class="fact"><b>{{ overview.sourceCount ?? '-' }}</b><span>热源厂</span></div>
          <div class="fact"><b>{{ overview.stationCount ?? '-' }}</b><span>换热站</span></div>
          <div class="fact"><b>{{ fmt(overview.totalAreaWanM2) }}</b><span>供热面积 万㎡</span></div>
          <div class="fact"><b>{{ overview.residentCount ?? '-' }}</b><span>在册热用户</span></div>
          <div class="fact warn"><b>{{ overview.unclosedOrderCount ?? '-' }}</b><span>未闭环工单</span></div>
          <div class="fact danger"><b>{{ fmt(overview.unqualifiedRate) }}%</b><span>室温不达标率</span></div>
          <div class="fact danger"><b>{{ fmt(overview.totalArrears) }}</b><span>累计欠费 元</span></div>
        </div>
      </header>

      <div class="viewport">
        <component :is="current.view" :key="current.path" :tab="current.query.tab" />
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { navNodes, resolveHash } from './router'
import { statsApi } from './api'

const current = ref(resolveHash())
const overview = ref({})

const currentKey = computed(() => {
  const { path, query } = current.value
  if (path === '/stations') return 'source'
  if (path === '/network' || path === '/isolation') return 'pipe'
  if (path === '/temperature') return 'resident'
  if (path === '/service') return query.tab === 'fees' ? 'fee' : 'order'
  return 'source'
})

const currentHash = computed(() => window.location.hash || '#/stations')

function goto(hash) {
  window.location.hash = hash
  sync()
}

function choose(node) {
  goto(node.children[0].hash)
}

function sync() {
  current.value = resolveHash()
}

function fmt(value) {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

async function loadOverview() {
  try {
    overview.value = await statsApi.overview()
  } catch (e) {
    overview.value = {}
  }
}

onMounted(() => {
  window.addEventListener('hashchange', sync)
  loadOverview()
})
</script>

<style scoped>
.shell {
  display: flex;
  height: 100%;
  min-height: 100vh;
  background: var(--hs-bg);
}

/* ---------------- 左侧管道式导航 ---------------- */
.pipeline {
  position: relative;
  width: 268px;
  flex: 0 0 268px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #0a3981 0%, #0d47a1 45%, #1661b8 100%);
  color: #fff;
  box-shadow: 2px 0 12px rgba(10, 57, 129, 0.25);
  overflow: hidden;
}

.pipeline::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(circle at 18% 12%, rgba(255, 255, 255, 0.12), transparent 45%);
  pointer-events: none;
}

.pipeline-inner {
  position: relative;
  flex: 1;
  padding: 20px 14px 10px 10px;
  overflow: auto;
}

/* 竖向主干：粗管 + 法兰 */
.trunk {
  position: absolute;
  left: 40px;
  top: 18px;
  bottom: 6px;
  width: 22px;
}

.trunk-flow {
  width: 100%;
  height: 100%;
  border-radius: 11px;
  background:
    repeating-linear-gradient(180deg, rgba(255, 255, 255, 0.16) 0 2px, rgba(255, 255, 255, 0.05) 2px 16px),
    linear-gradient(90deg, #0b2f6b 0%, #2b6fc9 35%, #6fb3e8 55%, #1f5aa8 100%);
  box-shadow: inset 0 0 6px rgba(0, 0, 0, 0.35), 0 0 10px rgba(95, 170, 230, 0.35);
}

.valve-list {
  position: relative;
  z-index: 1;
  padding-left: 6px;
}

.valve-row {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 6px;
}

.valve-assembly {
  position: relative;
  width: 46px;
  height: 46px;
  flex: 0 0 46px;
  cursor: pointer;
  filter: drop-shadow(0 2px 4px rgba(0, 0, 0, 0.28));
}

.valve-svg .body-a,
.valve-svg .body-b {
  fill: #173f74;
  stroke: rgba(255, 255, 255, 0.35);
  stroke-width: 1;
}

.valve-svg .body-c {
  fill: #26558f;
  stroke: rgba(255, 255, 255, 0.4);
  stroke-width: 1;
}

.valve-svg .stem {
  fill: #9fb6d6;
}

.valve-svg .wheel circle {
  fill: none;
  stroke: #9fb6d6;
  stroke-width: 3;
}

.valve-svg .wheel .hub {
  fill: #cbd8ea;
  stroke: none;
}

.valve-svg .wheel line {
  stroke: #9fb6d6;
  stroke-width: 2.4;
}

.valve-assembly.active .valve-svg .wheel circle,
.valve-assembly.active .valve-svg .wheel line {
  stroke: #ffcf6b;
}

.valve-assembly.active .valve-svg .wheel .hub {
  fill: #ffcf6b;
}

.valve-assembly.active .valve-svg .body-c {
  fill: #ff9d2e;
}

.wheel {
  transform-origin: 23px 6px;
  transition: transform 0.3s ease;
}

.wheel.spin {
  animation: hs-wheel 8s linear infinite;
}

@keyframes hs-wheel {
  from {
    transform: rotate(0deg);
  }
  to {
    transform: rotate(360deg);
  }
}

.valve-tag {
  position: absolute;
  right: -2px;
  bottom: -1px;
  font-size: 9px;
  padding: 0 4px;
  border-radius: 6px;
  background: rgba(4, 24, 54, 0.75);
  color: #cfe0f5;
  letter-spacing: 0.5px;
}

.valve-assembly.active .valve-tag {
  background: #ff9d2e;
  color: #2a1a02;
}

.valve-card {
  flex: 1;
  min-width: 0;
  padding: 4px 0 2px;
}

.valve-title {
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  letter-spacing: 0.4px;
  line-height: 22px;
}

.valve-title:hover {
  color: #ffd591;
}

.valve-desc {
  font-size: 11px;
  color: rgba(214, 231, 250, 0.7);
  margin-bottom: 2px;
}

.valve-children {
  margin-top: 4px;
  padding-left: 2px;
  border-left: 2px solid rgba(255, 208, 130, 0.5);
}

.child-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  margin: 2px 0;
  border-radius: 5px;
  font-size: 12.5px;
  color: rgba(226, 238, 252, 0.86);
  cursor: pointer;
}

.child-item:hover {
  background: rgba(255, 255, 255, 0.12);
}

.child-item.active {
  background: rgba(255, 208, 130, 0.22);
  color: #fff;
  font-weight: 600;
}

.child-branch {
  width: 12px;
  height: 2px;
  background: rgba(255, 208, 130, 0.7);
  border-radius: 1px;
}

.fold-enter-active,
.fold-leave-active {
  transition: all 0.2s ease;
}

.fold-enter-from,
.fold-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.pipeline-foot {
  padding: 10px 14px 14px;
  font-size: 11px;
  color: rgba(214, 231, 250, 0.75);
  border-top: 1px solid rgba(255, 255, 255, 0.15);
}

.flow-badge::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: #56d07a;
  box-shadow: 0 0 6px #56d07a;
}

/* ---------------- 右侧内容 ---------------- */
.content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid var(--hs-line);
  flex-wrap: wrap;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-ring {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 5px solid var(--hs-primary);
  box-shadow: inset 0 0 0 3px #fff, 0 0 0 3px rgba(255, 157, 46, 0.35);
}

.brand h1 {
  margin: 0;
  font-size: 18px;
  color: var(--hs-primary-deep);
  letter-spacing: 0.6px;
}

.brand p {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--hs-muted);
}

.facts {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.fact {
  min-width: 88px;
  padding: 4px 10px;
  border-radius: 6px;
  background: #f4f7fb;
  border: 1px solid var(--hs-line);
  text-align: right;
}

.fact b {
  display: block;
  font-size: 16px;
  color: var(--hs-primary-deep);
}

.fact.warn b {
  color: var(--hs-hot);
}

.fact.danger b {
  color: var(--hs-danger);
}

.fact span {
  font-size: 11px;
  color: var(--hs-muted);
}

.viewport {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
</style>
