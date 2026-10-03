<template>
  <div class="hs-page">
    <div class="hs-head">
      <div>
        <h2>报修工单看板 · 采暖费收缴台账</h2>
        <div class="hs-sub">
          上半：HTML5 原生拖拽把工单卡片在 待派工 / 处理中 / 已闭环 三列之间推进（跨级推进或回退会被业务规则拦截）。
          下半：收费台账按欠费金额降序，欠费行标红，行内可直接登记缴费。
        </div>
      </div>
      <div class="hs-head-ops">
        <el-button size="small" type="primary" plain @click="createVisible = true">新建报修工单</el-button>
        <el-button size="small" plain @click="reload">刷新</el-button>
      </div>
    </div>

    <el-tabs v-model="innerTab" class="hs-tabs">
      <el-tab-pane label="工单看板" name="board" />
      <el-tab-pane label="收费台账" name="fees" />
    </el-tabs>

    <!-- ---------- 看板 ---------- -->
    <template v-if="innerTab === 'board'">
      <div class="kanban">
        <div v-for="col in columns" :key="col.status" class="kanban-col" :class="{ 'drop-over': hover === col.status }"
             @dragover.prevent="hover = col.status" @dragleave="hover = ''" @drop="onDrop(col.status)">
          <div class="kanban-head" :class="col.cls">
            <span>{{ col.status }}</span>
            <span class="count">{{ grouped[col.status].length }}</span>
          </div>
          <div class="kanban-body">
            <div v-for="o in grouped[col.status]" :key="o.id" class="kanban-card" :class="faultClass(o.faultType)"
                 draggable="true" @dragstart="onDragStart(o)" @dragend="onDragEnd">
              <div class="card-top">
                <span class="order-no">{{ o.orderNo }}</span>
                <el-tag size="small" effect="light" :type="faultTag(o.faultType)">{{ o.faultType }}</el-tag>
              </div>
              <div class="card-line">换热站：{{ o.stationCode || '-' }}</div>
              <div class="card-line">用户：{{ o.residentLabel || '公共管网设施' }}</div>
              <div class="card-line muted">派工：{{ o.assignee || '待指派' }}</div>
              <div class="card-foot">
                <span class="muted">{{ o.reportedDate }}</span>
                <span class="next">→ {{ o.nextStatus || '已终点' }}</span>
              </div>
              <div v-if="o.handleResult" class="card-result">{{ o.handleResult }}</div>
            </div>
          </div>
        </div>
      </div>
    </template>

    <!-- ---------- 收费台账 ---------- -->
    <template v-else>
      <div class="hs-metrics">
        <div class="hs-metric"><div class="label">台账笔数</div><div class="value">{{ fees.length }}</div></div>
        <div class="hs-metric"><div class="label">应收合计</div><div class="value">{{ money(sum('receivable')) }}</div></div>
        <div class="hs-metric"><div class="label">实收合计</div><div class="value">{{ money(sum('paid')) }}</div></div>
        <div class="hs-metric danger"><div class="label">欠费合计</div><div class="value">{{ money(sum('arrears')) }}</div></div>
        <div class="hs-metric warn"><div class="label">欠费户数</div><div class="value">{{ fees.filter((f) => Number(f.arrears) > 0).length }}</div></div>
      </div>

      <div class="hs-panel">
        <div class="hs-panel-title">
          采暖费收缴台账（按欠费金额降序）
          <span class="hs-muted" style="font-weight: 400">缴费金额超过欠费会被拒绝</span>
        </div>
        <div class="hs-panel-body">
          <el-table :data="fees" border size="small" height="470" :row-class-name="rowClass">
            <el-table-column prop="feeYear" label="收费年度" width="90" />
            <el-table-column label="用户" min-width="190">
              <template #default="{ row }">
                {{ row.community }} {{ row.buildingNo }}号楼{{ row.unitNo }}单元{{ row.roomNo }}
              </template>
            </el-table-column>
            <el-table-column prop="stationCode" label="换热站" width="100" />
            <el-table-column prop="billingArea" label="计费面积 ㎡" width="100" align="right" />
            <el-table-column prop="unitPrice" label="单价 元/㎡" width="90" align="right" />
            <el-table-column prop="receivable" label="应收 元" width="110" align="right">
              <template #default="{ row }">{{ money(row.receivable) }}</template>
            </el-table-column>
            <el-table-column prop="paid" label="实收 元" width="110" align="right">
              <template #default="{ row }">{{ money(row.paid) }}</template>
            </el-table-column>
            <el-table-column prop="arrears" label="欠费 元" width="110" align="right">
              <template #default="{ row }">
                <b :class="{ 'arrear-text': Number(row.arrears) > 0 }">{{ money(row.arrears) }}</b>
              </template>
            </el-table-column>
            <el-table-column label="缴费状态" width="100">
              <template #default="{ row }">
                <el-tag size="small" effect="light" :type="row.payStatus === '已缴' ? 'success' : row.payStatus === '部分' ? 'warning' : 'danger'">
                  {{ row.payStatus }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="lastPayDate" label="最近缴费日" width="110" />
            <el-table-column label="行内缴费" width="210">
              <template #default="{ row }">
                <div class="pay-cell">
                  <el-input-number v-model="payAmounts[row.id]" size="small" :precision="2" :step="100" :min="0"
                                   controls-position="right" style="width: 110px" />
                  <el-button size="small" type="primary" plain :disabled="Number(row.arrears) <= 0"
                             @click="pay(row)">登记</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>

    <!-- ---------- 新建工单 ---------- -->
    <el-dialog v-model="createVisible" title="新建报修工单" width="460px">
      <el-form label-width="96px" size="small">
        <el-form-item label="换热站">
          <el-select v-model="createForm.stationId" filterable placeholder="选择换热站" style="width: 100%">
            <el-option v-for="s in stations" :key="s.id" :label="`${s.code} ${s.name}`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="故障类型">
          <el-select v-model="createForm.faultType" style="width: 100%">
            <el-option v-for="f in faultTypes" :key="f" :label="f" :value="f" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联用户">
          <el-select v-model="createForm.residentId" clearable filterable placeholder="可留空（公共管网设施）" style="width: 100%">
            <el-option v-for="r in residentOptions" :key="r.id" :label="r.label" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="派工人">
          <el-input v-model="createForm.assignee" placeholder="直接派工时填写" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitCreate">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { feeApi, monitorApi, orderApi, sourcesApi } from '../api'

const props = defineProps({ tab: { type: String, default: '' } })

const innerTab = ref(props.tab === 'fees' ? 'fees' : 'board')
watch(() => props.tab, (v) => {
  if (v === 'fees' || v === 'board') innerTab.value = v
})

const DEFAULT_COLUMNS = [
  { status: '待派工', cls: 'todo' },
  { status: '处理中', cls: 'doing' },
  { status: '已闭环', cls: 'done' }
]
const columns = ref(DEFAULT_COLUMNS)
const faultTypes = ref(['不热', '漏水', '异响', '气堵'])

const orders = ref([])
const fees = ref([])
const stations = ref([])
const residents = ref([])
const hover = ref('')
const dragging = ref(null)
const saving = ref(false)
const createVisible = ref(false)
const payAmounts = reactive({})
const createForm = ref({ stationId: null, residentId: null, faultType: '不热', assignee: '' })

const grouped = computed(() => {
  const map = {}
  columns.value.forEach((c) => (map[c.status] = []))
  orders.value.forEach((o) => {
    if (map[o.orderStatus]) map[o.orderStatus].push(o)
  })
  return map
})

const residentOptions = computed(() =>
  residents.value.map((r) => ({
    id: r.id,
    label: `${r.community} ${r.buildingNo}号楼${r.unitNo}单元${r.roomNo}　${r.meterNo}`
  }))
)

function rowClass({ row }) {
  return Number(row.arrears) > 0 ? 'hs-arrear-row' : ''
}

function faultTag(type) {
  if (type === '不热') return 'primary'
  if (type === '漏水') return 'info'
  if (type === '异响') return 'warning'
  return 'success'
}

function faultClass(type) {
  return {
    不热: 'fault-no-heat',
    漏水: 'fault-leak',
    异响: 'fault-noise',
    气堵: 'fault-air'
  }[type]
}

function money(value) {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function sum(field) {
  return fees.value.reduce((acc, cur) => acc + Number(cur[field] || 0), 0)
}

function onDragStart(order) {
  dragging.value = order
}

function onDragEnd() {
  dragging.value = null
  hover.value = ''
}

async function onDrop(target) {
  hover.value = ''
  const order = dragging.value
  if (!order || order.orderStatus === target) return
  const payload = { orderStatus: target }
  try {
    if (target === '处理中') {
      const { value } = await ElMessageBox.prompt('推进到「处理中」，请指定派工人', '派工', {
        inputValue: order.assignee || '',
        inputPlaceholder: '如：孙鹏'
      })
      payload.assignee = value
    }
    if (target === '已闭环') {
      const { value } = await ElMessageBox.prompt('闭环前请填写处理结果', '处理结果', {
        inputValue: order.handleResult || '',
        inputPlaceholder: '如：更换排气阀并排气'
      })
      payload.handleResult = value
    }
    await orderApi.updateStatus(order.id, payload)
    ElMessage.success(`${order.orderNo} 已推进到 ${target}`)
    await loadOrders()
  } catch (e) {
    if (e && e.message) ElMessage.error(e.message)
  }
}

async function pay(row) {
  const amount = payAmounts[row.id]
  if (amount === undefined || amount === null) {
    ElMessage.warning('请先录入缴费金额')
    return
  }
  try {
    await feeApi.pay(row.id, { amount })
    ElMessage.success(`已登记缴费 ${money(amount)} 元`)
    payAmounts[row.id] = undefined
    await loadFees()
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function submitCreate() {
  saving.value = true
  try {
    const payload = { ...createForm.value }
    if (payload.assignee) payload.orderStatus = '处理中'
    await orderApi.create(payload)
    ElMessage.success('报修工单已生成')
    createVisible.value = false
    createForm.value = { stationId: null, residentId: null, faultType: '不热', assignee: '' }
    await loadOrders()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function loadOrders() {
  try {
    orders.value = await orderApi.list(null)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadFees() {
  try {
    fees.value = await feeApi.list(null)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function loadMeta() {
  try {
    const [dict, stationList, residentList] = await Promise.all([
      orderApi.dict(),
      sourcesApi.stations(null),
      monitorApi.residents(null, null)
    ])
    if (dict.orderFlow && dict.orderFlow.length === 3) {
      columns.value = [
        { status: dict.orderFlow[0], cls: 'todo' },
        { status: dict.orderFlow[1], cls: 'doing' },
        { status: dict.orderFlow[2], cls: 'done' }
      ]
    }
    if (dict.faultTypes) faultTypes.value = dict.faultTypes
    stations.value = stationList
    residents.value = residentList
  } catch (e) {
    /* 元数据失败就用内置默认值 */
  }
}

async function reload() {
  await Promise.all([loadOrders(), loadFees()])
}

onMounted(async () => {
  await loadMeta()
  await reload()
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

.kanban {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.kanban-head .count {
  padding: 0 8px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.24);
  font-size: 12px;
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}

.order-no {
  font-size: 13px;
  font-weight: 700;
  color: var(--hs-primary-deep);
}

.card-line {
  font-size: 12px;
  color: #375073;
  line-height: 19px;
}

.card-line.muted,
.card-foot .muted {
  color: var(--hs-muted);
}

.card-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
  font-size: 11.5px;
}

.card-foot .next {
  color: var(--hs-primary);
  font-weight: 600;
}

.card-result {
  margin-top: 6px;
  padding: 4px 6px;
  border-radius: 4px;
  background: #f2f6fc;
  font-size: 11.5px;
  color: #4b6280;
}

.pay-cell {
  display: flex;
  align-items: center;
  gap: 6px;
}

.arrear-text {
  color: var(--hs-danger);
}
</style>
