<template>
  <div class="merge-panel">
    <!-- 步骤 1：选档案 -->
    <div class="hs-panel">
      <div class="hs-panel-title">
        第 1 步 · 选择保留档案与待归并档案
        <span class="hs-muted" style="font-weight: 400">只有地址确实指向同一住房、热表号与换热站一致的档案才能继续</span>
      </div>
      <div class="hs-panel-body">
        <div class="selectors">
          <div class="selector">
            <label>保留档案（归并后的主档案）</label>
            <el-select v-model="keptId" filterable placeholder="选择保留档案" style="width: 100%" @change="onKeptChange">
              <el-option v-for="r in residents" :key="'k' + r.id" :label="residentLabel(r)" :value="r.id" />
            </el-select>
          </div>
          <div class="selector">
            <label>待归并档案（归并后转只读）</label>
            <el-select v-model="awayId" filterable :disabled="!keptId" :placeholder="awayPlaceholder" style="width: 100%">
              <el-option v-for="r in awayOptions" :key="'a' + r.id"
                         :label="`${residentLabel(r)}（同地址候选）`" :value="r.id" />
            </el-select>
          </div>
          <el-button type="primary" plain :disabled="!keptId || !awayId || prechecking" :loading="prechecking"
                     @click="runPrecheck">归并预检</el-button>
        </div>
        <div class="hs-muted" style="font-size: 12px; margin-top: 8px">
          待归并候选只列出与保留档案同地址（小区/楼栋/单元/房号完全一致）的档案；预检会再次核验热表号与所属换热站。
        </div>
      </div>
    </div>

    <!-- 预检结果 -->
    <template v-if="pre">
      <!-- 硬性阻断 -->
      <div v-if="pre.blockers.length" class="block-box">
        <div class="block-title">⛔ 存在必须停止归并的硬性冲突（{{ pre.blockers.length }}）</div>
        <ul>
          <li v-for="(b, i) in pre.blockers" :key="i">{{ b }}</li>
        </ul>
        <div class="hs-muted" style="font-size: 12px">热表号或换热站不一致时不能归并，请先在线下核实热表与换热站归属。</div>
      </div>

      <div v-else class="ok-banner">✅ 地址、热表号、所属换热站一致，可进入归并确认。</div>

      <!-- 档案对照 -->
      <div class="hs-panel">
        <div class="hs-panel-title">第 2 步 · 预检对照：档案主信息</div>
        <div class="hs-panel-body">
          <table class="compare-table">
            <thead>
              <tr>
                <th style="width: 110px">核对项</th>
                <th>保留档案</th>
                <th style="width: 70px">一致性</th>
                <th>待归并档案</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <th>地址</th>
                <td>{{ pre.kept.addressText }}</td>
                <td><el-tag size="small" :type="pre.addressSame ? 'success' : 'danger'">{{ pre.addressSame ? '一致' : '不一致' }}</el-tag></td>
                <td>{{ pre.away.addressText }}</td>
              </tr>
              <tr>
                <th>热表号</th>
                <td>{{ pre.kept.meterNo || '-' }}</td>
                <td><el-tag size="small" :type="pre.meterSame ? 'success' : 'danger'">{{ pre.meterSame ? '一致' : '不一致' }}</el-tag></td>
                <td>{{ pre.away.meterNo || '-' }}</td>
              </tr>
              <tr>
                <th>所属换热站</th>
                <td>{{ pre.kept.stationCode }} {{ pre.kept.stationName }}</td>
                <td><el-tag size="small" :type="pre.stationSame ? 'success' : 'danger'">{{ pre.stationSame ? '一致' : '不一致' }}</el-tag></td>
                <td>{{ pre.away.stationCode }} {{ pre.away.stationName }}</td>
              </tr>
              <tr>
                <th>供热状态</th>
                <td>
                  <el-tag size="small" :type="pre.kept.supplyStatus === '正常供热' ? 'success' : 'info'">{{ pre.kept.supplyStatus }}</el-tag>
                </td>
                <td>
                  <el-tag size="small" :type="pre.supplyConflict ? 'warning' : 'success'">{{ pre.supplyConflict ? '冲突' : '一致' }}</el-tag>
                </td>
                <td>
                  <el-tag size="small" :type="pre.away.supplyStatus === '正常供热' ? 'success' : 'info'">{{ pre.away.supplyStatus }}</el-tag>
                </td>
              </tr>
              <tr>
                <th>计费面积 ㎡</th>
                <td>{{ fmt(pre.kept.areaM2) }}</td>
                <td>-</td>
                <td>{{ fmt(pre.away.areaM2) }}</td>
              </tr>
              <tr>
                <th>联系电话</th>
                <td>{{ pre.kept.contactPhone || '-' }}</td>
                <td>-</td>
                <td>{{ pre.away.contactPhone || '-' }}</td>
              </tr>
              <tr>
                <th>旧档案编号</th>
                <td>{{ pre.kept.legacyCode }}</td>
                <td>-</td>
                <td>{{ pre.away.legacyCode }}</td>
              </tr>
              <tr>
                <th>欠费合计 元</th>
                <td :class="{ 'arrear-text': num(pre.kept.totalArrears) > 0 }">{{ money(pre.kept.totalArrears) }}</td>
                <td>-</td>
                <td :class="{ 'arrear-text': num(pre.away.totalArrears) > 0 }">{{ money(pre.away.totalArrears) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- 供热状态裁决 -->
      <div v-if="pre.supplyConflict" class="hs-panel conflict-panel">
        <div class="hs-panel-title">供热状态不一致 · 必须明确采用哪种状态</div>
        <div class="hs-panel-body">
          <el-radio-group v-model="chosenSupply" class="supply-radios">
            <el-radio label="正常供热">采用「正常供热」</el-radio>
            <el-radio label="已报停">采用「已报停」</el-radio>
          </el-radio-group>
          <div class="impact-box">
            <div class="impact-item" :class="{ active: chosenSupply === '正常供热' }">
              <b>选择正常供热的影响：</b>{{ pre.impactActive }}
            </div>
            <div class="impact-item" :class="{ active: chosenSupply === '已报停' }">
              <b>选择已报停的影响：</b>{{ pre.impactSuspended }}
            </div>
          </div>
        </div>
      </div>

      <!-- 关联业务：抽测 / 报修 -->
      <div class="biz-grid">
        <div class="hs-panel">
          <div class="hs-panel-title">室温抽测（保留 {{ pre.keptSampleCount }} 条 / 待归并 {{ pre.awaySampleCount }} 条）</div>
          <div class="hs-panel-body">
            <el-table :data="sampleRows" size="small" border max-height="220">
              <el-table-column prop="side" label="来源" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.side === '保留' ? 'primary' : 'warning'">{{ row.side }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="sampleDate" label="抽测日期" width="100" />
              <el-table-column prop="temperature" label="室温 ℃" width="80" align="right" />
              <el-table-column label="达标" width="70">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.qualified ? 'success' : 'danger'">{{ row.qualified ? '达标' : '不达标' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="householdName" label="住户" width="90" />
              <el-table-column prop="surveyor" label="抽测人" min-width="80" />
            </el-table>
            <div class="hs-muted" style="font-size: 12px; margin-top: 6px">归并后全部改挂保留档案，记录编号、抽测时间、室温结果不变。</div>
          </div>
        </div>
        <div class="hs-panel">
          <div class="hs-panel-title">报修记录（保留 {{ pre.keptOrderCount }} 条 / 待归并 {{ pre.awayOrderCount }} 条）</div>
          <div class="hs-panel-body">
            <el-table :data="orderRows" size="small" border max-height="220">
              <el-table-column prop="side" label="来源" width="80">
                <template #default="{ row }">
                  <el-tag size="small" :type="row.side === '保留' ? 'primary' : 'warning'">{{ row.side }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="orderNo" label="工单号" width="130" />
              <el-table-column prop="reportedDate" label="报修日期" width="100" />
              <el-table-column prop="faultType" label="故障" width="70" />
              <el-table-column prop="orderStatus" label="状态" width="80" />
              <el-table-column prop="handleResult" label="处理结果" min-width="120" show-overflow-tooltip />
            </el-table>
            <div class="hs-muted" style="font-size: 12px; margin-top: 6px">归并后全部改挂保留档案，工单号、报修时间、派工人与处理结果不变。</div>
          </div>
        </div>
      </div>

      <!-- 各年度采暖费处置 -->
      <div class="hs-panel">
        <div class="hs-panel-title">
          第 3 步 · 各年度采暖费对照与处置预案
          <span class="hs-muted" style="font-weight: 400">同一年度两边都有记录时绝不简单相加</span>
        </div>
        <div class="hs-panel-body">
          <el-table :data="pre.feePlans" size="small" border>
            <el-table-column prop="feeYear" label="收费年度" width="90" />
            <el-table-column label="保留档案 应收/实收" width="180">
              <template #default="{ row }">
                {{ row.keptReceivable != null ? money(row.keptReceivable) : '无记录' }} /
                {{ row.keptPaid != null ? money(row.keptPaid) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="待归并 应收/实收" width="180">
              <template #default="{ row }">
                {{ row.awayReceivable != null ? money(row.awayReceivable) : '无记录' }} /
                {{ row.awayPaid != null ? money(row.awayPaid) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="处置方式" width="200">
              <template #default="{ row }">
                <el-tag size="small" :type="actionType(row.action)">{{ row.actionText }}</el-tag>
                <div v-if="row.action === 'RECALC'" class="hs-muted" style="font-size: 12px">
                  重算应收 <b>{{ money(row.recalcReceivable) }}</b>（{{ fmt(row.recalcBillingArea) }} ㎡）
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="说明" min-width="280" show-overflow-tooltip />
          </el-table>
        </div>
      </div>

      <!-- 确认区 -->
      <div v-if="!pre.blockers.length" class="confirm-bar hs-panel">
        <div class="hs-panel-body">
          <el-checkbox v-model="addressConfirmed" size="small">
            我已核对，两边地址（{{ pre.kept.addressText }}）确实指向<b>同一住房</b>
          </el-checkbox>
          <div class="confirm-row">
            <div class="hs-muted" style="font-size: 12px">
              归并后待归并档案转为只读并可一键跳转到保留档案；室温抽测、报修、收费记录全部改挂。
              任一引用迁移或审计写入失败，整次归并回退。
            </div>
            <div>
              <el-input v-model="operator" size="small" placeholder="经办人" style="width: 130px; margin-right: 8px" />
              <el-button type="primary" size="small" :loading="confirming"
                         :disabled="!addressConfirmed || (pre.supplyConflict && !chosenSupply)"
                         @click="confirmMerge">
                确认归并
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- 查询落点 + 审计 -->
    <div class="bottom-grid">
      <div class="hs-panel">
        <div class="hs-panel-title">归并后查询验证：地址 / 热表号 / 旧档案编号只能落到同一户</div>
        <div class="hs-panel-body">
          <div class="resolve-row">
            <el-select v-model="resolveType" size="small" style="width: 130px">
              <el-option label="按热表号" value="meter" />
              <el-option label="按旧档案编号" value="legacy" />
              <el-option label="按完整地址" value="address" />
            </el-select>
            <template v-if="resolveType !== 'address'">
              <el-input v-model="resolveKey" size="small" :placeholder="resolveType === 'meter' ? '如 RB20260016' : '如 R17 / r017'"
                        style="width: 200px" @keyup.enter="runResolve" />
            </template>
            <template v-else>
              <el-input v-model="addrForm.community" size="small" placeholder="小区" style="width: 120px" />
              <el-input v-model="addrForm.buildingNo" size="small" placeholder="楼栋" style="width: 80px" />
              <el-input v-model="addrForm.unitNo" size="small" placeholder="单元" style="width: 80px" />
              <el-input v-model="addrForm.roomNo" size="small" placeholder="房号" style="width: 80px" @keyup.enter="runResolve" />
            </template>
            <el-button size="small" type="primary" plain @click="runResolve">查询落点</el-button>
          </div>
          <div v-if="resolveResult" class="resolve-result" :class="{ redirected: resolveResult.redirected }">
            <el-tag size="small" :type="resolveResult.redirected ? 'warning' : 'success'">
              {{ resolveResult.redirected ? '从归档档案自动跳转' : '直接命中' }}
            </el-tag>
            <span v-if="resolveResult.redirected" class="hs-muted">
              命中归档档案 {{ resolveResult.redirectedFromCode }} → 跳转到
            </span>
            <b>保留档案 #{{ resolveResult.residentId }}（{{ resolveResult.legacyCode }}）</b>
            {{ resolveResult.addressText }}｜{{ resolveResult.meterNo }}｜{{ resolveResult.stationCode }}
            <el-tag size="small" :type="resolveResult.supplyStatus === '正常供热' ? 'success' : 'info'">
              {{ resolveResult.supplyStatus }}
            </el-tag>
          </div>
        </div>
      </div>

      <div class="hs-panel">
        <div class="hs-panel-title">归并审计（含失败回退记录）
          <el-button size="small" text @click="loadLogs">刷新</el-button>
        </div>
        <div class="hs-panel-body">
          <el-table :data="logs" size="small" border max-height="260">
            <el-table-column prop="mergeNo" label="归并单号" width="150" />
            <el-table-column label="保留 → 归档" width="110">
              <template #default="{ row }">#{{ row.keptResidentId }} → #{{ row.awayResidentId }}</template>
            </el-table-column>
            <el-table-column prop="chosenSupplyStatus" label="采用状态" width="90" />
            <el-table-column label="抽测/报修/收费" width="120">
              <template #default="{ row }">{{ row.sampleCount }} / {{ row.orderCount }} / {{ row.feeCount }}</template>
            </el-table-column>
            <el-table-column label="结果" width="90">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === '已归并' ? 'success' : 'danger'">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="failReason" label="失败原因" min-width="180" show-overflow-tooltip />
            <el-table-column prop="createdAt" label="时间" width="170">
              <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { monitorApi } from '../api'

const props = defineProps({
  residents: { type: Array, default: () => [] },
  initialKeptId: { type: Number, default: null }
})
const emit = defineEmits(['changed'])

const keptId = ref(props.initialKeptId)
const awayId = ref(null)
const pre = ref(null)
const prechecking = ref(false)
const confirming = ref(false)
const chosenSupply = ref('')
const addressConfirmed = ref(false)
const operator = ref('')
const logs = ref([])

const resolveType = ref('meter')
const resolveKey = ref('')
const addrForm = ref({ community: '', buildingNo: '', unitNo: '', roomNo: '' })
const resolveResult = ref(null)

const awayOptions = ref([])

// 从用户档案页带保留档案跳进来时自动加载同地址候选
watch(
  () => props.initialKeptId,
  (id) => {
    if (id && id !== keptId.value) {
      keptId.value = id
      onKeptChange()
    }
  }
)
if (keptId.value) {
  onKeptChange()
}

const awayPlaceholder = computed(() => (keptId.value ? '仅显示同地址候选' : '请先选择保留档案'))

const sampleRows = computed(() => {
  if (!pre.value) return []
  const k = pre.value.kept.samples.map((x) => ({ ...x, side: '保留' }))
  const a = pre.value.away.samples.map((x) => ({ ...x, side: '待归并' }))
  return a.concat(k)
})

const orderRows = computed(() => {
  if (!pre.value) return []
  const k = pre.value.kept.orders.map((x) => ({ ...x, side: '保留' }))
  const a = pre.value.away.orders.map((x) => ({ ...x, side: '待归并' }))
  return a.concat(k)
})

function residentLabel(r) {
  return `${r.community} ${r.buildingNo}号楼${r.unitNo}单元${r.roomNo}　${r.meterNo || '无热表'}　${r.legacyCode || 'R' + r.id}`
}

function num(v) {
  return Number(v || 0)
}

function fmt(v) {
  return v === null || v === undefined ? '-' : Number(v).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function money(v) {
  if (v === null || v === undefined) return '-'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function actionType(action) {
  if (action === 'RECALC') return 'primary'
  if (action === 'KEEP_REVIEW') return 'danger'
  return 'info'
}

function formatTime(v) {
  if (!v) return ''
  return String(v).replace('T', ' ').substring(0, 19)
}

async function onKeptChange() {
  awayId.value = null
  pre.value = null
  addressConfirmed.value = false
  chosenSupply.value = ''
  resolveResult.value = null
  if (!keptId.value) {
    awayOptions.value = []
    return
  }
  try {
    awayOptions.value = await monitorApi.mergeCandidates(keptId.value)
    if (!awayOptions.value.length) {
      ElMessage.info('该档案没有同地址的未归档候选，无需归并')
    }
  } catch (e) {
    ElMessage.error(e.message)
    awayOptions.value = []
  }
}

async function runPrecheck() {
  if (!keptId.value || !awayId.value) return
  prechecking.value = true
  pre.value = null
  try {
    pre.value = await monitorApi.mergePrecheck(keptId.value, awayId.value)
    chosenSupply.value = pre.value.supplyConflict ? '' : pre.value.kept.supplyStatus
    addressConfirmed.value = false
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    prechecking.value = false
  }
}

async function confirmMerge() {
  if (!addressConfirmed.value) {
    ElMessage.warning('请先勾选确认两边地址指向同一住房')
    return
  }
  if (pre.value.supplyConflict && !chosenSupply.value) {
    ElMessage.warning('供热状态不一致，必须选择采用哪种状态')
    return
  }
  confirming.value = true
  try {
    const result = await monitorApi.mergeConfirm({
      keptResidentId: keptId.value,
      awayResidentId: awayId.value,
      chosenSupplyStatus: pre.value.supplyConflict ? chosenSupply.value : null,
      addressConfirmed: true,
      operator: operator.value || null
    })
    if (result.idempotent) {
      ElMessage.warning(`该归并已成功过（${result.mergeNo}），本次为幂等返回，未再次搬移数据或重算金额`)
    } else {
      const reviewYears = (result.feeResults || []).filter((f) => f.reviewRequired).map((f) => f.feeYear)
      ElMessage.success(
        `归并完成 ${result.mergeNo}：抽测 ${result.sampleMigrated} 条、报修 ${result.orderMigrated} 条已改挂`
        + (reviewYears.length ? `；年度 ${reviewYears.join('、')} 待财务复核，复核前禁止缴费` : '')
      )
    }
    pre.value = null
    keptId.value = null
    awayId.value = null
    addressConfirmed.value = false
    await loadLogs()
    emit('changed')
  } catch (e) {
    ElMessage.error(e.message)
    await loadLogs()
  } finally {
    confirming.value = false
  }
}

async function runResolve() {
  resolveResult.value = null
  try {
    const params = resolveType.value === 'address'
      ? { type: 'address', ...addrForm.value }
      : { type: resolveType.value, key: resolveKey.value }
    resolveResult.value = await monitorApi.resolveResident(params)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadLogs() {
  try {
    logs.value = await monitorApi.mergeLogs()
  } catch (e) {
    logs.value = []
  }
}

loadLogs()
</script>

<style scoped>
.merge-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.selectors {
  display: flex;
  gap: 12px;
  align-items: flex-end;
}

.selector {
  flex: 1;
  min-width: 0;
}

.selector label {
  display: block;
  font-size: 12px;
  color: var(--hs-muted);
  margin-bottom: 4px;
}

.block-box {
  background: #fff6f5;
  border: 1px solid #f3b9b4;
  border-radius: 10px;
  padding: 12px 16px;
}

.block-title {
  color: var(--hs-danger);
  font-weight: 700;
  margin-bottom: 6px;
}

.block-box ul {
  margin: 0 0 6px;
  padding-left: 20px;
  color: #a1342d;
  font-size: 13px;
  line-height: 22px;
}

.ok-banner {
  background: #f0faf2;
  border: 1px solid #a8ddb6;
  color: #1f7a38;
  border-radius: 10px;
  padding: 10px 16px;
  font-weight: 600;
  font-size: 13px;
}

.compare-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.compare-table th,
.compare-table td {
  border: 1px solid var(--hs-line);
  padding: 7px 10px;
  text-align: left;
}

.compare-table thead th {
  background: #f4f7fb;
  color: #375073;
}

.compare-table tbody th {
  background: #fafbfd;
  color: var(--hs-muted);
  font-weight: 500;
  width: 110px;
}

.conflict-panel {
  border-color: #f2c879;
}

.supply-radios {
  display: flex;
  gap: 24px;
  margin-bottom: 10px;
}

.impact-box {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.impact-item {
  font-size: 12.5px;
  color: #5a6c83;
  background: #f7f9fc;
  border: 1px dashed var(--hs-line);
  border-radius: 8px;
  padding: 8px 10px;
  line-height: 20px;
}

.impact-item.active {
  color: #23426b;
  background: #eef5ff;
  border: 1px solid #9cc2ef;
}

.biz-grid,
.bottom-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.confirm-bar .confirm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 10px;
}

.resolve-row {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.resolve-result {
  margin-top: 10px;
  font-size: 13px;
  line-height: 26px;
  padding: 8px 12px;
  border-radius: 8px;
  background: #f0faf2;
  border: 1px solid #a8ddb6;
}

.resolve-result.redirected {
  background: #fff8ea;
  border-color: #f2c879;
}

.arrear-text {
  color: var(--hs-danger);
  font-weight: 600;
}
</style>
