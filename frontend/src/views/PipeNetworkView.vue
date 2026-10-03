<template>
  <div class="hs-page">
    <div class="hs-head">
      <div>
        <h2>管网平面示意图 · 阀门井远控</h2>
        <div class="hs-sub">
          线宽按管径、颜色按保温状况（完好绿 / 破损橙），虚线表示该管段非运行状态；点阀门井打开就地操作面板直接改开关与检修状态。
        </div>
      </div>
      <div class="hs-head-ops">
        <el-button size="small" type="warning" plain @click="goIsolation">管网隔离方案</el-button>
        <el-button size="small" plain @click="reload">刷新</el-button>
      </div>
    </div>

    <el-tabs v-model="innerTab" class="hs-tabs">
      <el-tab-pane label="管网平面示意图" name="map" />
      <el-tab-pane label="阀门井清单" name="wells" />
    </el-tabs>

    <!-- 顶部管径筛选条 -->
    <div class="hs-panel">
      <div class="hs-panel-body dn-bar-body">
        <span class="dn-label">管径筛选</span>
        <div class="dn-bar">
          <span class="dn-chip" :class="{ active: activeDn === null }" @click="activeDn = null">
            全部 {{ segments.length }} 段
          </span>
          <span v-for="d in diameters" :key="d" class="dn-chip" :class="{ active: activeDn === d }"
                @click="activeDn = d">
            <i class="dn-pipe" :style="{ height: pipeWidth(d) + 'px' }"></i>DN{{ d }} · {{ countByDn(d) }}
          </span>
        </div>
        <div class="legend-line">
          <span><i class="ln ok"></i>保温完好</span>
          <span><i class="ln bad"></i>保温破损</span>
          <span><i class="ln dash"></i>非运行状态</span>
        </div>
      </div>
    </div>

    <div v-if="innerTab === 'map'" class="map-wrap" ref="mapRef">
      <svg class="pipe-map" viewBox="0 0 1040 520" preserveAspectRatio="xMidYMid meet">
        <defs>
          <linearGradient id="hsBgGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#f7fafd" />
            <stop offset="100%" stop-color="#eaeff6" />
          </linearGradient>
        </defs>
        <rect x="0" y="0" width="1040" height="520" fill="url(#hsBgGrad)" rx="8" />

        <!-- 管段：外被 off shadow + 主线 -->
        <g v-for="seg in visibleSegments" :key="seg.id">
          <polyline class="pipe-casing" :points="seg.mapPoints" :stroke-width="pipeWidth(seg.diameterDn) + 8" />
          <polyline :points="seg.mapPoints" :stroke="insulColor(seg)" :stroke-width="pipeWidth(seg.diameterDn)"
                    :class="['pipe-line', { flowing: seg.segmentStatus === '运行' }]"
                    :stroke-dasharray="seg.segmentStatus === '运行' ? null : '14 9'"
                    @click="focusSegment(seg)">
            <title>{{ seg.segCode }}　DN{{ seg.diameterDn }}　{{ seg.lengthM }}m　{{ seg.layingType }}　保温{{ seg.insulationStatus }}　{{ seg.segmentStatus }}</title>
          </polyline>
          <text v-if="seg.startWellNo" :x="labelPos(seg).x" :y="labelPos(seg).y - pipeWidth(seg.diameterDn) - 6"
                class="seg-label">{{ seg.segCode }} · DN{{ seg.diameterDn }}</text>
        </g>

        <!-- 阀门井 -->
        <g v-for="w in visibleWells" :key="w.id" :class="['well-node', { active: popWell && popWell.id === w.id }]"
           @click="openPop($event, w)">
          <circle :cx="w.mapX" :cy="w.mapY" r="17" class="well-plate" />
          <circle :cx="w.mapX" :cy="w.mapY" r="13" class="well-disc" :class="openClass(w)" />
          <text :x="w.mapX" :y="w.mapY + 4" class="well-mark">{{ w.openState === '全开' ? '开' : w.openState === '关闭' ? '关' : '半' }}</text>
          <text :x="w.mapX" :y="w.mapY - 22" class="well-name">
            {{ w.wellNo }}<tspan v-if="w.inoperableFlag" class="well-flag bad">⚠不可操作</tspan><tspan
              v-if="w.egressGuard" class="well-flag guard">⛑安全门</tspan>
          </text>
          <title>{{ w.wellNo }}　{{ w.location }}　DN{{ w.valveCaliberDn }}　{{ w.openState }}　{{ w.repairStatus }}</title>
        </g>
      </svg>

      <!-- 就地操作浮层 -->
      <transition name="popfade">
        <div v-if="popWell" class="well-pop" :style="popStyle">
          <div class="pop-head">
            <span class="well-no">{{ popWell.wellNo }}</span>
            <el-button size="small" text @click="popWell = null">关闭</el-button>
          </div>
          <div class="pop-row">{{ popWell.location }}</div>
          <div class="pop-row">
            <el-tag size="small" effect="plain">DN{{ popWell.valveCaliberDn }}</el-tag>
            <el-tag size="small" :type="popWell.repairStatus === '检修中' ? 'warning' : 'success'" effect="light">
              井室{{ popWell.repairStatus }}
            </el-tag>
          </div>
          <div class="pop-row">
            <span class="pop-field">所属管段</span>
            <b>{{ popWell.segCode || '-' }}</b>
            <el-tag v-if="popWell.segmentStatus && popWell.segmentStatus !== '运行'" size="small" type="warning">
              {{ popWell.segmentStatus }}
            </el-tag>
          </div>
          <div class="pop-row">
            <span class="pop-field">开关状态</span>
            <el-radio-group v-model="popForm.openState" size="small">
              <el-radio-button v-for="o in openStates" :key="o" :label="o">{{ o }}</el-radio-button>
            </el-radio-group>
          </div>
          <div class="pop-row">
            <span class="pop-field">检修状态</span>
            <el-radio-group v-model="popForm.repairStatus" size="small">
              <el-radio-button v-for="r in repairStates" :key="r" :label="r">{{ r }}</el-radio-button>
            </el-radio-group>
          </div>
          <el-input v-model="popForm.remark" size="small" placeholder="操作备注" />
          <div class="pop-foot">
            <el-button type="primary" size="small" :loading="saving" @click="submitWell">下发操作</el-button>
          </div>
        </div>
      </transition>
    </div>

    <!-- 阀门井清单 -->
    <div v-else class="hs-panel">
      <div class="hs-panel-title">阀门井清单</div>
      <div class="hs-panel-body">
        <el-table :data="wells" border size="small" height="480">
          <el-table-column prop="wellNo" label="井号" width="90" />
          <el-table-column prop="location" label="位置" min-width="200" />
          <el-table-column prop="valveCaliberDn" label="阀门口径" width="90" align="right">
            <template #default="{ row }">DN{{ row.valveCaliberDn }}</template>
          </el-table-column>
          <el-table-column prop="segCode" label="所属管段" width="100" />
          <el-table-column prop="segmentStatus" label="管段状态" width="90" />
          <el-table-column label="开关状态" width="150">
            <template #default="{ row }">
              <el-select v-model="row.openState" size="small" style="width: 100px"
                         @change="(val) => quickUpdate(row, { openState: val })">
                <el-option v-for="o in openStates" :key="o" :label="o" :value="o" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="检修状态" width="150">
            <template #default="{ row }">
              <el-select v-model="row.repairStatus" size="small" style="width: 100px"
                         @change="(val) => quickUpdate(row, { repairStatus: val })">
                <el-option v-for="r in repairStates" :key="r" :label="r" :value="r" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column prop="lastCheckDate" label="上次巡检" width="110" />
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pipeApi } from '../api'

const props = defineProps({ tab: { type: String, default: '' } })

const innerTab = ref(props.tab === 'wells' ? 'wells' : 'map')
watch(() => props.tab, (v) => {
  if (v === 'wells' || v === 'map') innerTab.value = v
})

const segments = ref([])
const allSegments = ref([])
const wells = ref([])
const diameters = ref([])
const activeDn = ref(null)
const mapRef = ref(null)

const popWell = ref(null)
const popStyle = ref({})
const popForm = ref({ openState: '', repairStatus: '', remark: '' })
const saving = ref(false)

const openStates = ref(['全开', '半开', '关闭'])
const repairStates = ref(['正常', '检修中'])

watch(activeDn, loadSegments)

const visibleSegments = computed(() => segments.value)
const visibleWells = computed(() => {
  const ids = new Set(segments.value.map((s) => s.id))
  return wells.value.filter((w) => ids.has(w.segmentId))
})

function pipeWidth(dn) {
  return 4 + (Number(dn) / 100) * 1.6
}

function insulColor(seg) {
  return seg.insulationStatus === '破损' ? '#f08c2a' : '#2ea44f'
}

function openClass(w) {
  if (w.openState === '全开') return 'open'
  if (w.openState === '关闭') return 'closed'
  return 'half'
}

function countByDn(dn) {
  return allSegments.value.filter((s) => s.diameterDn === dn).length
}

function labelPos(seg) {
  const pts = (seg.mapPoints || '').trim().split(/\s+/)
  if (!pts.length || !pts[0]) return { x: 0, y: 0 }
  const mid = pts[Math.floor(pts.length / 2)].split(',')
  return { x: Number(mid[0]) || 0, y: Number(mid[1]) || 0 }
}

function focusSegment(seg) {
  ElMessage.info(`${seg.segCode}　DN${seg.diameterDn}　${seg.layingType}　保温${seg.insulationStatus}　${seg.segmentStatus}`)
}

function goIsolation() {
  window.location.hash = '#/isolation'
}

function openPop(event, well) {
  const rect = event.currentTarget.getBoundingClientRect()
  popWell.value = well
  popForm.value = { openState: well.openState, repairStatus: well.repairStatus, remark: well.remark || '' }
  popStyle.value = {
    position: 'fixed',
    left: Math.min(window.innerWidth - 300, rect.left + 28) + 'px',
    top: Math.min(window.innerHeight - 330, rect.top - 10) + 'px'
  }
}

async function submitWell() {
  if (!popWell.value) return
  saving.value = true
  try {
    await pipeApi.updateValveWell(popWell.value.id, { ...popForm.value })
    ElMessage.success(`${popWell.value.wellNo} 操作已下发`)
    popWell.value = null
    await Promise.all([loadWells(), loadSegments()])
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function quickUpdate(row, patch) {
  const backup = { openState: row.openState, repairStatus: row.repairStatus }
  try {
    await pipeApi.updateValveWell(row.id, patch)
    ElMessage.success(`${row.wellNo} 已更新`)
  } catch (e) {
    row.openState = backup.openState
    row.repairStatus = backup.repairStatus
    ElMessage.error(e.message)
  }
}

async function loadSegments() {
  try {
    segments.value = await pipeApi.segments(activeDn.value, 'with-valves')
    if (activeDn.value === null) allSegments.value = segments.value
    else if (!allSegments.value.length) allSegments.value = await pipeApi.segments(null, 'with-valves')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadWells() {
  try {
    wells.value = await pipeApi.valveWells(null, 'with-segment')
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadDict() {
  try {
    const dict = await pipeApi.dict()
    if (dict.valveOpenState) openStates.value = dict.valveOpenState
    if (dict.valveRepairStatus) repairStates.value = dict.valveRepairStatus
  } catch (e) {
    /* 词典取不到就用默认值 */
  }
}

async function reload() {
  await Promise.all([loadSegments(), loadWells()])
}

onMounted(async () => {
  await loadDict()
  await reload()
  try {
    diameters.value = await pipeApi.diameters()
  } catch (e) {
    diameters.value = []
  }
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

.dn-bar-body {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.dn-label {
  font-size: 13px;
  font-weight: 600;
  color: var(--hs-primary-deep);
}

.dn-pipe {
  display: inline-block;
  width: 14px;
  margin-right: 6px;
  vertical-align: middle;
  border-radius: 4px;
  background: linear-gradient(90deg, #0d47a1, #4a86d6);
}

.legend-line {
  display: flex;
  gap: 14px;
  font-size: 12px;
  color: var(--hs-muted);
}

.legend-line .ln {
  display: inline-block;
  width: 22px;
  height: 6px;
  border-radius: 3px;
  margin-right: 6px;
  vertical-align: middle;
}

.ln.ok {
  background: #2ea44f;
}

.ln.bad {
  background: #f08c2a;
}

.ln.dash {
  background: repeating-linear-gradient(90deg, #8a9cb5 0 6px, transparent 6px 11px);
}

.map-wrap {
  position: relative;
  flex: 1;
  min-height: 460px;
  background: #fff;
  border: 1px solid var(--hs-line);
  border-radius: 10px;
  overflow: hidden;
}

.pipe-map {
  width: 100%;
  height: 460px;
}

.pipe-casing {
  fill: none;
  stroke: rgba(13, 71, 161, 0.1);
  stroke-linecap: round;
  stroke-linejoin: round;
}

.pipe-line {
  fill: none;
  stroke-linecap: round;
  stroke-linejoin: round;
  cursor: pointer;
  transition: opacity 0.2s ease;
}

.pipe-line:hover {
  opacity: 0.72;
}

.pipe-line.flowing {
  animation: hs-flow 2.4s linear infinite;
}

@keyframes hs-flow {
  from {
    stroke-dasharray: 26 10;
    stroke-dashoffset: 36;
  }
  to {
    stroke-dasharray: 26 10;
    stroke-dashoffset: 0;
  }
}

.seg-label {
  font-size: 11px;
  fill: #4b6280;
  text-anchor: middle;
}

.well-node {
  cursor: pointer;
}

.well-plate {
  fill: rgba(255, 255, 255, 0.9);
  stroke: #93a7c0;
  stroke-width: 1.5;
}

.well-disc {
  stroke-width: 2;
  stroke: #3c597e;
}

.well-disc.open {
  fill: #2ea44f;
}

.well-disc.half {
  fill: #f0b62a;
}

.well-disc.closed {
  fill: #e34d4d;
}

.well-node.active .well-plate {
  stroke: var(--hs-primary);
  stroke-width: 3;
}

.well-mark {
  font-size: 12px;
  fill: #fff;
  text-anchor: middle;
  pointer-events: none;
}

.well-name {
  font-size: 11px;
  fill: #16324f;
  text-anchor: middle;
  pointer-events: none;
}

.well-flag {
  font-size: 10px;
  margin-left: 2px;
}

.well-flag.bad {
  fill: var(--hs-danger);
}

.well-flag.guard {
  fill: var(--hs-hot);
}

.well-pop {
  z-index: 30;
  width: 288px;
  padding: 12px;
  background: #fff;
  border: 1px solid var(--hs-primary-light-7);
  border-radius: 10px;
  box-shadow: 0 10px 28px rgba(13, 71, 161, 0.22);
}

.pop-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.well-no {
  font-size: 15px;
  font-weight: 700;
  color: var(--hs-primary-deep);
}

.pop-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 12px;
  color: #375073;
}

.pop-field {
  width: 62px;
  flex: 0 0 62px;
  color: var(--hs-muted);
}

.pop-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 8px;
}

.popfade-enter-active,
.popfade-leave-active {
  transition: all 0.16s ease;
}

.popfade-enter-from,
.popfade-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}
</style>
