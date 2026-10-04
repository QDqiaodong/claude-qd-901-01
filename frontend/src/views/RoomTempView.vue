<template>
  <div class="hs-page">
    <div class="hs-head">
      <div>
        <h2>室温抽测热力格 · {{ grid.community || '-' }}</h2>
        <div class="hs-sub">
          横轴单元、纵轴楼栋；格子按最近一次室温染色（&lt;18℃ 蓝、18~22℃ 绿、&gt;22℃ 红），不达标格子闪烁。点格子录入抽测。
        </div>
      </div>
      <div class="hs-head-ops">
        <el-select v-model="community" placeholder="选择小区" style="width: 180px" @change="loadGrid">
          <el-option v-for="c in communities" :key="c" :label="c" :value="c" />
        </el-select>
        <el-button size="small" plain @click="loadGrid">刷新</el-button>
      </div>
    </div>

    <el-tabs v-model="innerTab" class="hs-tabs">
      <el-tab-pane label="室温抽测热力格" name="grid" />
      <el-tab-pane label="用户档案与报停" name="residents" />
      <el-tab-pane label="重复档案归并" name="merge" />
    </el-tabs>

    <template v-if="innerTab === 'grid'">
      <div class="hs-metrics">
        <div class="hs-metric"><div class="label">在册户数</div><div class="value">{{ grid.totalHouseholds ?? 0 }}</div></div>
        <div class="hs-metric"><div class="label">已抽测</div><div class="value">{{ grid.sampledHouseholds ?? 0 }}</div></div>
        <div class="hs-metric"><div class="label">达标</div><div class="value">{{ grid.qualifiedCount ?? 0 }}</div></div>
        <div class="hs-metric danger"><div class="label">不达标</div><div class="value">{{ grid.unqualifiedCount ?? 0 }}</div></div>
        <div class="hs-metric danger"><div class="label">不达标率</div><div class="value">{{ grid.unqualifiedRate ?? 0 }}%</div></div>
      </div>

      <div class="hs-panel">
        <div class="hs-panel-title">
          楼栋 × 单元 抽测矩阵
          <span class="hs-muted" style="font-weight: 400">（最低达标室温 {{ minTemp }}℃，舒适上限 {{ maxTemp }}℃）</span>
        </div>
        <div class="hs-panel-body">
          <div class="grid-wrap">
            <table class="temp-grid">
              <thead>
                <tr>
                  <th class="corner">楼栋 / 单元</th>
                  <th v-for="u in grid.units" :key="u">{{ u }} 单元</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="b in grid.buildings" :key="b">
                  <th class="row-head">{{ b }} 号楼</th>
                  <td v-for="u in grid.units" :key="u">
                    <div v-if="cellAt(b, u)" class="cell" :class="[cellClass(cellAt(b, u)), { 'cell-blink': !cellAt(b, u).qualified }]"
                         @click="openRecord(cellAt(b, u))">
                      <div class="temp">{{ fmt(cellAt(b, u).latestTemp) }}<span>℃</span></div>
                      <div class="who">{{ cellAt(b, u).households?.[0] || '未录入' }}</div>
                      <div class="date">{{ cellAt(b, u).latestDate || '-' }}</div>
                    </div>
                    <div v-else class="cell empty">—</div>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </div>
      </div>

      <div class="hs-panel">
        <div class="hs-panel-title">换热站不达标户数统计（手写 JPQL）</div>
        <div class="hs-panel-body">
          <el-table :data="statRows" border size="small">
            <el-table-column prop="stationCode" label="换热站" width="110" />
            <el-table-column prop="stationName" label="名称" min-width="150" />
            <el-table-column prop="householdCount" label="抽测户数" width="100" align="right" />
            <el-table-column prop="sampleCount" label="抽测次数" width="100" align="right" />
            <el-table-column prop="unqualifiedCount" label="不达标次数" width="110" align="right" />
            <el-table-column label="不达标占比" min-width="200">
              <template #default="{ row }">
                <el-progress :percentage="badRate(row)" :stroke-width="12" text-inside
                             :status="badRate(row) > 30 ? 'exception' : badRate(row) > 10 ? 'warning' : 'success'" />
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="action-bar">
        <span class="hs-muted">共 {{ unqualifiedCells.length }} 个不达标格子需要跟踪</span>
        <el-button type="danger" plain :loading="batchLoading" @click="batchCreateOrder">
          一键生成报修工单
        </el-button>
      </div>
    </template>

    <template v-else-if="innerTab === 'residents'">
      <div class="hs-panel">
        <div class="hs-panel-title">用户档案（欠费用户不允许办理报停 / 恢复；归档档案已归并到保留档案，不在此展示）</div>
        <div class="hs-panel-body">
          <el-table :data="residents" border size="small" height="520">
            <el-table-column label="用户" min-width="180">
              <template #default="{ row }">
                {{ row.community }} {{ row.buildingNo }}号楼{{ row.unitNo }}单元{{ row.roomNo }}
              </template>
            </el-table-column>
            <el-table-column prop="areaM2" label="面积 ㎡" width="90" align="right" />
            <el-table-column prop="meterNo" label="热表编号" width="120" />
            <el-table-column prop="totalHeatGj" label="累计用热 GJ" width="110" align="right" />
            <el-table-column prop="stationCode" label="换热站" width="100" />
            <el-table-column prop="contactPhone" label="联系电话" width="120" />
            <el-table-column label="供热状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.supplyStatus === '正常供热' ? 'success' : 'info'" effect="light">
                  {{ row.supplyStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="欠费 元" width="110" align="right">
              <template #default="{ row }">
                <span :class="{ 'arrear-text': Number(row.arrears) > 0 }">{{ fmtMoney(row.arrears) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190">
              <template #default="{ row }">
                <el-button size="small" :type="row.supplyStatus === '正常供热' ? 'warning' : 'success'" plain
                           @click="toggleSupply(row)">
                  {{ row.supplyStatus === '正常供热' ? '报停' : '恢复' }}
                </el-button>
                <el-button size="small" type="primary" plain @click="gotoMerge(row)">归并</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>

    <template v-else-if="innerTab === 'merge'">
      <ResidentMergePanel :key="mergePanelKey" :residents="residents" :initial-kept-id="mergeKeptId"
                          @changed="loadResidents" />
    </template>

    <!-- 录入抽测 -->
    <el-dialog v-model="recordVisible" title="室温抽测录入" width="440px">
      <el-form v-if="currentCell" label-width="96px" size="small">
        <el-form-item label="位置">
          <span>{{ grid.community }} {{ currentCell.buildingNo }}号楼{{ currentCell.unitNo }}单元</span>
        </el-form-item>
        <el-form-item label="住户">
          <el-select v-model="recordForm.residentId" placeholder="选择住户" style="width: 100%">
            <el-option v-for="id in currentCell.residentIds" :key="id" :label="residentLabel(id)" :value="id" />
          </el-select>
        </el-form-item>
        <el-form-item label="住户姓名">
          <el-input v-model="recordForm.householdName" placeholder="如：张建平" />
        </el-form-item>
        <el-form-item label="抽测日期">
          <el-date-picker v-model="recordForm.sampleDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="室温 ℃">
          <el-input-number v-model="recordForm.temperature" :precision="1" :step="0.1" :min="-30" :max="50" />
        </el-form-item>
        <el-form-item label="抽测人">
          <el-input v-model="recordForm.surveyor" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="recordForm.remark" />
        </el-form-item>
        <el-form-item label="自动生成工单">
          <el-checkbox v-model="recordForm.autoOrder" label="室温不达标时自动生成报修工单" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitRecord">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { monitorApi } from '../api'
import ResidentMergePanel from '../components/ResidentMergePanel.vue'

const props = defineProps({ tab: { type: String, default: '' } })

const innerTab = ref(props.tab === 'residents' ? 'residents' : props.tab === 'merge' ? 'merge' : 'grid')
watch(() => props.tab, (v) => {
  if (v === 'residents' || v === 'grid' || v === 'merge') innerTab.value = v
})

const grid = ref({ community: '', buildings: [], units: [], cells: [] })
const statRows = ref([])
const communities = ref([])
const community = ref('')
const residents = ref([])
const mergeKeptId = ref(null)
const mergePanelKey = ref(0)

const recordVisible = ref(false)
const saving = ref(false)
const batchLoading = ref(false)
const currentCell = ref(null)
const minTemp = ref(18)
const maxTemp = ref(22)
const recordForm = ref({})

const unqualifiedCells = computed(() =>
  grid.value.cells.filter((c) => c.latestTemp !== null && Number(c.latestTemp) < Number(minTemp.value))
)

function cellAt(building, unit) {
  return grid.value.cells.find((c) => c.buildingNo === building && c.unitNo === unit)
}

function cellClass(cell) {
  const temp = Number(cell.latestTemp)
  if (Number.isNaN(temp)) return 'none'
  if (temp < 18) return 'cold'
  if (temp > 22) return 'hot'
  return 'ok'
}

function badRate(row) {
  const total = Number(row.sampleCount || 0)
  if (!total) return 0
  return Math.round((Number(row.unqualifiedCount || 0) * 100) / total)
}

function fmt(value) {
  if (value === null || value === undefined) return '--'
  return Number(value).toFixed(1)
}

function fmtMoney(value) {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function residentLabel(id) {
  const r = residents.value.find((x) => x.id === id)
  return r ? `${r.roomNo}　${r.meterNo}` : String(id)
}

function openRecord(cell) {
  currentCell.value = cell
  recordForm.value = {
    residentId: cell.residentIds?.[0] ?? null,
    householdName: cell.households?.[0] ?? '',
    sampleDate: new Date().toISOString().slice(0, 10),
    temperature: 20,
    surveyor: '',
    remark: '',
    autoOrder: false
  }
  recordVisible.value = true
}

async function submitRecord() {
  saving.value = true
  try {
    const saved = await monitorApi.record(recordForm.value)
    ElMessage.success(
      saved.linkedOrderNo ? `抽测已录入，自动生成工单 ${saved.linkedOrderNo}` : '室温抽测已录入'
    )
    recordVisible.value = false
    await loadGrid()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function batchCreateOrder() {
  if (!unqualifiedCells.value.length) {
    ElMessage.info('当前没有不达标格子')
    return
  }
  batchLoading.value = true
  let ok = 0
  const failures = []
  for (const cell of unqualifiedCells.value) {
    if (!cell.latestSampleId) continue
    try {
      await monitorApi.createOrder(cell.latestSampleId)
      ok++
    } catch (e) {
      failures.push(`${cell.buildingNo}号楼${cell.unitNo}单元：${e.message}`)
    }
  }
  batchLoading.value = false
  if (ok) ElMessage.success(`已生成 ${ok} 条报修工单`)
  if (failures.length) ElMessage.warning(failures.slice(0, 3).join('；'))
}

async function toggleSupply(row) {
  const target = row.supplyStatus === '正常供热' ? '已报停' : '正常供热'
  try {
    await monitorApi.changeSupply(row.id, target)
    ElMessage.success(`用户已${target === '已报停' ? '报停' : '恢复供热'}`)
    loadResidents()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function gotoMerge(row) {
  mergeKeptId.value = row.id
  mergePanelKey.value++
  innerTab.value = 'merge'
  window.location.hash = '#/temperature?tab=merge'
}

async function loadGrid() {
  try {
    grid.value = await monitorApi.grid(community.value)
    if (grid.value.community) community.value = grid.value.community
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadStat() {
  try {
    statRows.value = await monitorApi.stationStat()
  } catch (e) {
    statRows.value = []
  }
}

async function loadResidents() {
  try {
    residents.value = await monitorApi.residents(null, null)
  } catch (e) {
    residents.value = []
  }
}

async function loadDict() {
  try {
    const dict = await monitorApi.dict()
    minTemp.value = Number(dict.minQualifiedTemp ?? 18)
    maxTemp.value = Number(dict.maxComfortTemp ?? 22)
  } catch (e) {
    /* 词典取不到就用默认 18 / 22 */
  }
}

onMounted(async () => {
  await loadDict()
  await Promise.all([loadGrid(), loadStat(), loadResidents()])
  try {
    communities.value = await monitorApi.communities()
  } catch (e) {
    communities.value = []
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

.grid-wrap {
  overflow: auto;
}

.temp-grid {
  border-collapse: separate;
  border-spacing: 6px;
  min-width: 100%;
}

.temp-grid th {
  font-size: 12px;
  color: var(--hs-muted);
  font-weight: 500;
}

.temp-grid .corner {
  text-align: left;
  padding-left: 4px;
}

.temp-grid .row-head {
  width: 92px;
  text-align: left;
  padding-left: 4px;
  color: #375073;
  font-weight: 600;
  font-size: 13px;
}

.cell {
  min-width: 104px;
  height: 78px;
  padding: 6px;
  border-radius: 8px;
  cursor: pointer;
  color: #fff;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.cell:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 14px rgba(22, 50, 79, 0.22);
}

.cell.cold {
  background: linear-gradient(160deg, #4f96ef, #2f6fd0);
}

.cell.ok {
  background: linear-gradient(160deg, #4cbb70, #2c8b4d);
}

.cell.hot {
  background: linear-gradient(160deg, #f2695f, #d13f3f);
}

.cell.none {
  background: #c9d3e2;
}

.cell.empty {
  background: #f2f5f9;
  color: #a8b6c8;
  border: 1px dashed #dbe2ec;
  align-items: center;
  justify-content: center;
  cursor: default;
}

.cell .temp {
  font-size: 20px;
  font-weight: 700;
  line-height: 1.1;
}

.cell .temp span {
  font-size: 11px;
  font-weight: 400;
  margin-left: 2px;
}

.cell .who {
  font-size: 12px;
  margin-top: 2px;
  opacity: 0.92;
}

.cell .date {
  font-size: 10.5px;
  opacity: 0.78;
}

.action-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid var(--hs-line);
  border-radius: 10px;
  font-size: 13px;
}

.arrear-text {
  color: var(--hs-danger);
  font-weight: 600;
}
</style>
