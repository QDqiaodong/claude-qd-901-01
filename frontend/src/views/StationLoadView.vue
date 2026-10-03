<template>
  <div class="hs-page">
    <div class="hs-head">
      <div>
        <h2>换热站负载环阵列 · {{ filtered.length }} 座</h2>
        <div class="hs-sub">
          环 = 一次侧实际供回温差 / 设计温差（负荷率）；环心为供热面积。点击环可在右侧抽屉登记一次/二次侧温度与循环泵状态。
        </div>
      </div>
      <div class="hs-head-ops">
        <el-select v-model="sourceId" clearable placeholder="按热源厂筛选" style="width: 190px">
          <el-option v-for="s in sources" :key="s.id" :label="s.name" :value="s.id" />
        </el-select>
        <el-button type="primary" plain :loading="profileLoading" @click="loadProfile">查看抓取档案对照</el-button>
      </div>
    </div>

    <el-tabs v-model="innerTab" class="hs-tabs">
      <el-tab-pane label="负载环阵列" name="rings" />
      <el-tab-pane label="热源厂台账" name="sources" />
    </el-tabs>

    <!-- 负载环阵列 -->
    <template v-if="innerTab === 'rings'">
      <div class="hs-panel legend-panel">
        <div class="hs-panel-body legend">
          <span><i class="dot low"></i>低负荷 &lt; 60%</span>
          <span><i class="dot ok"></i>正常 60% ~ 100%</span>
          <span><i class="dot over"></i>超负荷 &gt; 100%</span>
          <span class="hs-muted">一次侧设计温差 {{ designDeltaT }} ℃</span>
        </div>
      </div>

      <div class="ring-grid">
        <div v-for="st in filtered" :key="st.id" class="ring-card" @click="openDrawer(st)">
          <svg class="ring" viewBox="0 0 120 120">
            <g transform="rotate(-90 60 60)">
              <circle class="ring-bg" cx="60" cy="60" r="48"></circle>
              <circle class="ring-fg" cx="60" cy="60" r="48" :stroke="ringColor(st)"
                      :stroke-dasharray="CIRC" :stroke-dashoffset="dashOffset(st)"></circle>
            </g>
            <text class="ring-num" x="60" y="54">{{ fmtNum(st.areaWanM2) }}</text>
            <text class="ring-unit" x="60" y="70">万㎡</text>
            <text class="ring-rate" x="60" y="100">{{ pct(st.loadRate) }}%</text>
          </svg>
          <div class="ring-meta">
            <div class="code">{{ st.code }}</div>
            <div class="name">{{ st.name }}</div>
            <div class="tags">
              <el-tag size="small" effect="light" :type="levelTag(st.loadLevel)">{{ st.loadLevel }}</el-tag>
              <el-tag size="small" effect="plain">{{ st.status }}</el-tag>
            </div>
            <div class="delta">一次温差 {{ fmtNum(st.primaryDelta) }}℃ / 二次温差 {{ fmtNum(st.secondaryDelta) }}℃</div>
            <div class="pump">循环泵 {{ st.runningPumpCount ?? 0 }}/{{ st.pumpCount ?? 0 }} 台　·　{{ st.sourceName }}</div>
            <div v-if="st.openOrderCount > 0" class="order-tip">未闭环工单 {{ st.openOrderCount }} 条</div>
          </div>
        </div>
      </div>
    </template>

    <!-- 热源厂台账 -->
    <template v-else>
      <div class="hs-panel">
        <div class="hs-panel-title">热源厂与按热源汇总（手写 JPQL 投影）</div>
        <div class="hs-panel-body">
          <el-table :data="summary" border size="small">
            <el-table-column prop="sourceName" label="热源厂" min-width="140" />
            <el-table-column prop="fuelType" label="燃料类型" width="100" />
            <el-table-column prop="capacityMw" label="装机容量 MW" width="110" align="right" />
            <el-table-column prop="stationCount" label="换热站数" width="90" align="right" />
            <el-table-column prop="totalAreaWanM2" label="供热面积 万㎡" width="120" align="right" />
            <el-table-column label="一次侧平均温差 ℃" width="150" align="right">
              <template #default="{ row }">{{ fmtNum(row.avgPrimaryDelta) }}</template>
            </el-table-column>
            <el-table-column label="面积占比" min-width="180">
              <template #default="{ row }">
                <el-progress :percentage="areaPercent(row)" :stroke-width="12" text-inside />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>

    <!-- 抓取档案对照 -->
    <el-dialog v-model="profileVisible" title="@FetchProfile 启用前后对照" width="620px">
      <el-table :data="profileRows" border size="small">
        <el-table-column prop="profile" label="抓取档案" width="130" />
        <el-table-column prop="note" label="加载方式" min-width="230" />
        <el-table-column label="rows" width="70" align="right">
          <template #default="{ row }">{{ row.rowCount }}</template>
        </el-table-column>
        <el-table-column label="stations 已预加载" width="150" align="center">
          <template #default="{ row }">
            <el-tag :type="row.associationEagerLoaded ? 'success' : 'info'" size="small">
              {{ row.associationEagerLoaded ? 'JOIN 一次装载' : '延迟 / 逐条触发' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="profileVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 运行参数登记抽屉 -->
    <el-drawer v-model="drawer" :title="drawerTitle" size="430px">
      <el-form v-if="current" label-width="120px" size="small">
        <el-form-item label="换热站编号">
          <el-input :model-value="current.code" disabled />
        </el-form-item>
        <el-form-item label="所属热源">
          <el-input :model-value="current.sourceName" disabled />
        </el-form-item>
        <el-form-item label="一次侧供水 ℃">
          <el-input-number v-model="form.primarySupplyTemp" :precision="1" :step="0.5" :min="0" :max="160" />
        </el-form-item>
        <el-form-item label="一次侧回水 ℃">
          <el-input-number v-model="form.primaryReturnTemp" :precision="1" :step="0.5" :min="0" :max="160" />
        </el-form-item>
        <el-form-item label="二次侧供水 ℃">
          <el-input-number v-model="form.secondarySupplyTemp" :precision="1" :step="0.5" :min="0" :max="120" />
        </el-form-item>
        <el-form-item label="二次侧回水 ℃">
          <el-input-number v-model="form.secondaryReturnTemp" :precision="1" :step="0.5" :min="0" :max="120" />
        </el-form-item>
        <el-form-item label="设计温差 ℃">
          <el-input-number v-model="form.designDeltaT" :precision="1" :step="1" :min="1" :max="60" />
        </el-form-item>
        <el-form-item label="运行循环泵">
          <el-input-number v-model="form.runningPumpCount" :min="0" :max="current.pumpCount || 0" />
        </el-form-item>
        <el-form-item label="负责人">
          <el-input v-model="form.manager" />
        </el-form-item>
        <el-alert type="info" :closable="false" show-icon
                  title="负荷率 = (一次侧供水 - 一次侧回水) ÷ 设计温差，超 100% 判定为超负荷" />
      </el-form>

      <template #footer>
        <div class="drawer-foot">
          <el-select v-model="statusValue" placeholder="变更状态" style="width: 130px" size="small">
            <el-option v-for="s in stationStatusOptions" :key="s" :label="s" :value="s" />
          </el-select>
          <el-button :type="statusValue === '停用' ? 'danger' : 'warning'" plain size="small" @click="submitStatus">
            状态变更
          </el-button>
          <el-button type="primary" size="small" :loading="saving" @click="submitRuntime">保存运行参数</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { sourcesApi } from '../api'

const props = defineProps({ tab: { type: String, default: '' } })

const CIRC = 2 * Math.PI * 48
const DEFAULT_STATUSES = ['运行', '检修', '停用']

const innerTab = ref(props.tab === 'sources' ? 'sources' : 'rings')
watch(() => props.tab, (v) => {
  if (v === 'sources' || v === 'rings') innerTab.value = v
})

const sources = ref([])
const stations = ref([])
const summary = ref([])
const sourceId = ref(null)
const dict = ref({})
const stationStatusOptions = ref(DEFAULT_STATUSES)

const drawer = ref(false)
const saving = ref(false)
const current = ref(null)
const form = ref({})
const statusValue = ref('')

const profileVisible = ref(false)
const profileLoading = ref(false)
const profileRows = ref([])

const filtered = computed(() =>
  sourceId.value ? stations.value.filter((s) => s.sourceId === sourceId.value) : stations.value
)
const designDeltaT = computed(() => dict.value.designDeltaT ?? 25)
const drawerTitle = computed(() => (current.value ? `运行参数登记 · ${current.value.code} ${current.value.name}` : ''))

function ringColor(st) {
  const rate = Number(st.loadRate || 0)
  if (rate > 1) return '#e34d4d'
  if (rate < 0.6) return '#2f80ed'
  return '#2ea44f'
}

function dashOffset(st) {
  const raw = Number(st.loadRate || 0)
  const rate = Math.max(0, Math.min(1.2, raw)) / 1.2
  return CIRC * (1 - rate)
}

function pct(value) {
  return Number((Number(value || 0) * 100).toFixed(0))
}

function levelTag(level) {
  if (level === '超负荷') return 'danger'
  if (level === '低负荷') return 'primary'
  return 'success'
}

function fmtNum(value) {
  if (value === null || value === undefined) return '-'
  const n = Number(value)
  return Number.isNaN(n) ? '-' : n.toFixed(1)
}

function areaPercent(row) {
  const total = summary.value.reduce((acc, cur) => acc + Number(cur.totalAreaWanM2 || 0), 0)
  if (!total) return 0
  return Math.round((Number(row.totalAreaWanM2 || 0) * 100) / total)
}

function openDrawer(st) {
  current.value = st
  statusValue.value = st.status
  form.value = {
    primarySupplyTemp: st.primarySupplyTemp,
    primaryReturnTemp: st.primaryReturnTemp,
    secondarySupplyTemp: st.secondarySupplyTemp,
    secondaryReturnTemp: st.secondaryReturnTemp,
    designDeltaT: st.designDeltaT ?? 25,
    runningPumpCount: st.runningPumpCount ?? 0,
    manager: st.manager
  }
  drawer.value = true
}

async function submitRuntime() {
  if (!current.value) return
  saving.value = true
  try {
    await sourcesApi.registerRuntime(current.value.id, form.value)
    ElMessage.success('运行参数已登记')
    drawer.value = false
    loadStations()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function submitStatus() {
  if (!current.value) return
  try {
    await sourcesApi.changeStatus(current.value.id, statusValue.value)
    ElMessage.success(`换热站已${statusValue.value}`)
    loadStations()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadProfile() {
  profileLoading.value = true
  try {
    profileRows.value = await sourcesApi.compareProfile()
    profileVisible.value = true
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    profileLoading.value = false
  }
}

async function loadSources() {
  sources.value = await sourcesApi.listAll()
}

async function loadStations() {
  stations.value = await sourcesApi.stations(null)
}

async function loadSummary() {
  summary.value = await sourcesApi.loadSummary()
}

async function loadDict() {
  try {
    dict.value = await sourcesApi.dict()
    if (dict.value.stationStatus) stationStatusOptions.value = dict.value.stationStatus
  } catch (e) {
    dict.value = {}
  }
}

onMounted(async () => {
  await Promise.all([loadSources(), loadStations(), loadSummary(), loadDict()])
})
</script>

<style scoped>
.hs-head-ops {
  display: flex;
  gap: 8px;
}

.hs-tabs {
  margin-top: -6px;
}

.legend-panel {
  flex: 0 0 auto;
}

.legend {
  display: flex;
  gap: 16px;
  align-items: center;
  font-size: 12px;
  color: #375073;
}

.legend .dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-right: 5px;
}

.dot.low {
  background: #2f80ed;
}

.dot.ok {
  background: #2ea44f;
}

.dot.over {
  background: #e34d4d;
}

.ring-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.ring-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: #fff;
  border: 1px solid var(--hs-line);
  border-radius: 10px;
  cursor: pointer;
  box-shadow: 0 2px 8px rgba(13, 71, 161, 0.06);
  transition: transform 0.16s ease, box-shadow 0.16s ease;
}

.ring-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 8px 18px rgba(13, 71, 161, 0.16);
  border-color: var(--hs-primary-light-5);
}

.ring {
  width: 118px;
  height: 118px;
  flex: 0 0 118px;
}

.ring-bg {
  fill: none;
  stroke: #e8edf5;
  stroke-width: 12;
}

.ring-fg {
  fill: none;
  stroke-width: 12;
  stroke-linecap: round;
  transition: stroke-dashoffset 0.6s ease;
}

.ring-num {
  font-size: 20px;
  font-weight: 700;
  fill: #16324f;
  text-anchor: middle;
}

.ring-unit,
.ring-rate {
  font-size: 11px;
  fill: #6b7f99;
  text-anchor: middle;
}

.ring-meta {
  min-width: 0;
}

.ring-meta .code {
  font-size: 14px;
  font-weight: 700;
  color: var(--hs-primary-deep);
}

.ring-meta .name {
  font-size: 12px;
  color: #375073;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ring-meta .tags {
  display: flex;
  gap: 4px;
  margin-bottom: 4px;
}

.ring-meta .delta,
.ring-meta .pump {
  font-size: 11.5px;
  color: var(--hs-muted);
  line-height: 18px;
}

.order-tip {
  margin-top: 3px;
  font-size: 11.5px;
  color: var(--hs-hot);
}

.drawer-foot {
  display: flex;
  gap: 8px;
  justify-content: flex-end;
}
</style>
