<template>
  <div class="hs-page iso-page">
    <div class="hs-head">
      <div>
        <h2>管网隔离方案 · 故障关断闭环</h2>
        <div class="hs-sub">
          选故障管段 → 拓扑试算关断序列 / 保供路径 / 受影响换热站 → 人工调整重证 →
          现场逐步确认（偏差即停）→ 失败三种收口 → 复供按当前阀态重排。
        </div>
      </div>
      <div class="hs-head-ops">
        <el-button size="small" plain @click="reload">刷新</el-button>
      </div>
    </div>

    <!-- 阶段指示条 -->
    <div class="hs-panel">
      <div class="phase-bar">
        <template v-for="(p, i) in phaseChain" :key="p.key">
          <div class="phase-node" :class="phaseClass(p.key)">
            <i class="phase-dot">{{ i + 1 }}</i>
            <span>{{ p.label }}</span>
          </div>
          <div v-if="i < phaseChain.length - 1" class="phase-link"
               :class="{ on: phaseIndex > i }"></div>
        </template>
      </div>
    </div>

    <div class="iso-body">
      <!-- 左：方案列表 + 新建试算 -->
      <div class="iso-side">
        <div class="hs-panel">
          <div class="hs-panel-title">隔离方案</div>
          <div class="hs-panel-body side-body">
            <el-button type="primary" size="small" class="new-btn" @click="startNew">＋ 新建隔离试算</el-button>
            <div class="plan-list">
              <div v-for="p in plans" :key="p.id" class="plan-card"
                   :class="{ active: current && current.id === p.id }" @click="loadPlan(p.id)">
                <div class="plan-card-top">
                  <b>{{ p.planNo }}</b>
                  <el-tag size="small" :type="phaseTag(p.phase)" effect="dark">{{ p.phase }}</el-tag>
                </div>
                <div class="plan-card-seg">{{ p.faultSegmentLabel }}</div>
                <div class="plan-card-meta">
                  <el-tag size="small" :type="p.isolatedProven ? 'success' : 'danger'" effect="plain">
                    {{ p.isolatedProven ? '已证明隔开' : '未隔开' }}
                  </el-tag>
                  <span>{{ p.strategy }}</span>
                  <span v-if="p.affectedStationCount">停暖 {{ p.affectedStationCount }} 站</span>
                </div>
              </div>
              <div v-if="!plans.length" class="empty-tip">暂无隔离方案，选择故障管段开始试算</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 右：试算 / 执行 主体 -->
      <div class="iso-main">
        <!-- 新建试算：选故障管段 + 现场调整 -->
        <div v-if="!current" class="hs-panel">
          <div class="hs-panel-title">第一步 · 选择故障管段</div>
          <div class="hs-panel-body trial-body">
            <svg class="iso-map" viewBox="0 0 1040 500">
              <rect x="0" y="0" width="1040" height="500" fill="#f7fafd" rx="8" />
              <g v-for="seg in mapSegments" :key="seg.id">
                <polyline :points="seg.mapPoints" class="map-pipe"
                          :class="{ fault: form.segmentId === seg.id, disabled: seg.segmentStatus === '停用' }"
                          :stroke-width="segWidth(seg.diameterDn)"
                          @click="pickSegment(seg)" />
              </g>
              <g v-for="w in mapWells" :key="w.id" class="map-well" @click="pickWell(w)">
                <circle :cx="w.mapX" :cy="w.mapY" r="13" :class="['well-disc', wellClass(w)]" />
                <text :x="w.mapX" :y="w.mapY + 4" class="well-mark">{{ wellMark(w) }}</text>
                <text :x="w.mapX" :y="w.mapY - 20" class="well-name">
                  {{ w.wellNo }}<tspan v-if="w.inoperableFlag">⚠</tspan><tspan v-if="w.egressGuard">🛟</tspan>
                </text>
              </g>
            </svg>

            <div class="trial-form">
              <div class="form-row">
                <span class="form-label">故障管段</span>
                <el-select v-model="form.segmentId" placeholder="在图上点管段或下拉选择" filterable size="small"
                           style="width: 320px">
                  <el-option v-for="seg in selectableSegments" :key="seg.id"
                             :label="seg.segCode + '（' + seg.startWellNo + '→' + seg.endWellNo + '）' + seg.segmentStatus"
                             :value="seg.id" />
                </el-select>
              </div>
              <div class="form-row">
                <span class="form-label">故障现象</span>
                <el-input v-model="form.faultReason" size="small" style="width: 320px"
                          placeholder="如：DN500 补偿器处泄漏，现场有汽雾" />
              </div>

              <div class="adjust-block">
                <div class="adjust-title">现场情况人工调整（试算用，不直接改台账）
                  <el-button size="small" text type="primary" @click="addOverride">＋ 添加阀门</el-button>
                </div>
                <div v-for="(o, i) in form.overrides" :key="i" class="override-row">
                  <el-select v-model="o.wellNo" size="small" placeholder="阀门井" style="width: 110px">
                    <el-option v-for="w in mapWells" :key="w.id" :label="w.wellNo" :value="w.wellNo" />
                  </el-select>
                  <el-select v-model="o.openState" size="small" placeholder="现场阀态" clearable style="width: 110px">
                    <el-option v-for="s in openStates" :key="s" :label="s" :value="s" />
                  </el-select>
                  <el-checkbox v-model="o.inoperable">现场不可操作（卡涩/占压/泄漏区）</el-checkbox>
                  <el-button size="small" text type="danger" @click="form.overrides.splice(i, 1)">移除</el-button>
                </div>
              </div>

              <div class="form-row">
                <span class="form-label">追加关闭井</span>
                <el-input v-model="form.extraCloseText" size="small" style="width: 320px"
                          placeholder="多个井号逗号分隔，如 J109,J110" />
              </div>
              <div class="form-row">
                <span class="form-label">拒绝关阀井</span>
                <el-input v-model="form.excludedCloseText" size="small" style="width: 320px"
                          placeholder="人工不同意关闭的井（会触发越界扩张或证明失败）" />
              </div>
              <div class="form-row">
                <span class="form-label">调整说明</span>
                <el-input v-model="form.adjustNote" size="small" style="width: 320px"
                          placeholder="人工调整依据" />
              </div>

              <el-button type="primary" size="default" :loading="loading" @click="runTrial(null)">
                拓扑试算并重新证明
              </el-button>
            </div>
          </div>
        </div>

        <!-- 已有方案：详情与执行 -->
        <template v-else>
          <!-- 方案摘要 + 证明 + 取舍 -->
          <div class="hs-panel">
            <div class="hs-panel-title plan-head">
              <span>{{ current.planNo }} · {{ current.faultSegmentLabel }}</span>
              <div class="plan-head-tags">
                <el-tag :type="phaseTag(current.phase)" effect="dark">{{ current.phase }}</el-tag>
                <el-tag :type="current.strategy === '拒绝启用' ? 'info' : 'warning'" effect="plain">
                  {{ current.strategy }}
                </el-tag>
                <el-tag :type="current.isolatedProven ? 'success' : 'danger'" effect="dark">
                  {{ current.isolatedProven ? '证明：故障段已隔开' : '证明：未隔开' }}
                </el-tag>
              </div>
            </div>
            <div class="hs-panel-body">
              <div class="proof-box" :class="current.isolatedProven ? 'ok' : 'bad'">
                <b>隔离证明</b>
                <span>{{ current.proofNote }}</span>
              </div>
              <div v-if="current.faultReason" class="kv-line"><b>故障现象：</b>{{ current.faultReason }}</div>
              <div class="kv-grid">
                <div><b>关断边界井</b>
                  <el-tag v-for="w in current.blockedWellNos" :key="w" size="small" effect="plain"
                          class="kv-tag">{{ w }}</el-tag>
                </div>
                <div><b>围禁区管段</b>
                  <el-tag v-for="s in current.enclosedSegmentCodes" :key="s" size="small" type="warning"
                          effect="plain" class="kv-tag">{{ s }}</el-tag>
                </div>
                <div><b>必须开启阀</b>
                  <el-tag v-for="w in current.forcedOpenWellNos" :key="w" size="small" type="success"
                          effect="plain" class="kv-tag">{{ w }}</el-tag>
                  <span v-if="!current.forcedOpenWellNos.length" class="kv-none">无</span>
                </div>
              </div>

              <!-- 后果数字 -->
              <div class="impact-facts">
                <div class="impact-fact danger">
                  <b>{{ current.affectedStationCount ?? 0 }}</b><span>新增停暖换热站</span>
                </div>
                <div class="impact-fact danger">
                  <b>{{ current.affectedHouseholds ?? 0 }}</b><span>受影响居民户数</span>
                </div>
                <div class="impact-fact warn">
                  <b>{{ fmtArea(current.affectedAreaWanM2) }}</b><span>停暖面积 万㎡</span>
                </div>
                <div class="impact-fact ok">
                  <b>{{ current.maintainableStationCodes.length }}</b><span>仍有保供路径换热站</span>
                </div>
              </div>

              <!-- 两种取舍 -->
              <div v-if="canAdjust" class="choice-grid">
                <div class="choice-card expand" :class="{ disabled: !current.optionExpand }">
                  <div class="choice-title">扩大隔离 · 扩大停暖确保安全</div>
                  <div class="choice-text">{{ current.expandOption ? current.expandOption.consequence
                    : '当前边界不存在可行的扩大封口阀，无法通过扩大隔离实现安全关断。' }}</div>
                  <div v-if="current.expandOption" class="choice-tags">
                    追加关阀：
                    <el-tag v-for="w in current.expandOption.extraBlockedWells" :key="w" size="small"
                            type="danger" effect="plain" class="kv-tag">{{ w }}</el-tag>
                  </div>
                  <el-button size="small" type="warning" :disabled="!current.optionExpand" :loading="loading"
                             @click="runTrial('扩大隔离')">按扩大隔离重新试算</el-button>
                </div>
                <div class="choice-card reject">
                  <div class="choice-title">拒绝启用 · 等待替代路径</div>
                  <div class="choice-text">{{ current.rejectOption ? current.rejectOption.consequence
                    : '保持现状，不予下发。' }}</div>
                  <el-button size="small" :loading="loading" @click="runTrial('拒绝启用')">
                    标记拒绝启用
                  </el-button>
                </div>
              </div>

              <!-- 操作按钮区 -->
              <div class="action-bar">
                <template v-if="canAdjust">
                  <el-button size="small" @click="editTrial">人工调整后重新试算</el-button>
                  <el-button size="small" type="primary" :disabled="!current.isolatedProven"
                             @click="activate">激活方案并下发</el-button>
                  <span v-if="!current.isolatedProven" class="action-hint">
                    证明未通过：只能扩大隔离或拒绝启用，不能下发
                  </span>
                </template>
                <template v-if="current.phase === '待执行'">
                  <el-button size="small" type="primary" @click="startExec">开始现场关断</el-button>
                </template>
                <template v-if="['已暂停','已失败'].includes(current.phase)">
                  <el-button size="small" type="warning" @click="finishDialog('EXPAND')">① 扩大隔离重算</el-button>
                  <el-button size="small" type="danger" plain @click="finishDialog('REVERT')">
                    ② 按安全顺序恢复已动阀
                  </el-button>
                  <el-button size="small" @click="finishDialog('HOLD')">③ 保持现场等待处置</el-button>
                  <el-button size="small" @click="editTrial">重新试算入口</el-button>
                </template>
                <template v-if="current.phase === '已隔离'">
                  <el-button size="small" type="success" @click="planRestore">故障结束 · 生成复供顺序</el-button>
                </template>
              </div>
              <div v-if="current.trialNote" class="note-line"><b>试算记录：</b>{{ current.trialNote }}</div>
              <div v-if="current.finishNote" class="note-line"><b>收口记录：</b>{{ current.finishNote }}</div>
            </div>
          </div>

          <!-- 阀门步骤 + 换热站后果 + 阀门实况 -->
          <div class="detail-grid">
            <div class="hs-panel">
              <div class="hs-panel-title">阀门关断 / 复供序列</div>
              <div class="hs-panel-body step-body">
                <el-tabs v-model="stepStage">
                  <el-tab-pane label="隔离序列" name="隔离" />
                  <el-tab-pane label="复供序列" name="复供" />
                </el-tabs>
                <el-table :data="shownSteps" border size="small" max-height="420">
                  <el-table-column prop="stepNo" label="序" width="44" align="center" />
                  <el-table-column label="井号 / 位置" min-width="150">
                    <template #default="{ row }">
                      <b>{{ row.wellNo }}</b>
                      <span v-if="row.egressGuard" title="人员撤离安全门" class="egress-ico">⛑</span>
                      <div class="cell-sub">{{ row.wellLocation }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="actionKind" label="动作类别" width="92">
                    <template #default="{ row }">
                      <el-tag size="small" :type="kindTag(row.actionKind)" effect="light">
                        {{ row.actionKind }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="计划 / 基线" width="104">
                    <template #default="{ row }">
                      <span>{{ row.plannedState }}</span>
                      <div class="cell-sub">基线 {{ row.fromState || '-' }}</div>
                    </template>
                  </el-table-column>
                  <el-table-column prop="stepStatus" label="状态" width="78">
                    <template #default="{ row }">
                      <el-tag size="small" :type="stepTag(row.stepStatus)" effect="plain">
                        {{ row.stepStatus }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="现场确认" width="200">
                    <template #default="{ row }">
                      <template v-if="canConfirm(row)">
                        <el-radio-group v-model="confirmForms[row.id].actualState" size="small">
                          <el-radio-button label="全开">开</el-radio-button>
                          <el-radio-button label="半开">半</el-radio-button>
                          <el-radio-button label="关闭">关</el-radio-button>
                        </el-radio-group>
                        <el-input v-model="confirmForms[row.id].confirmedBy" size="small"
                                  placeholder="确认人" class="confirm-input" />
                        <el-input v-model="confirmForms[row.id].deviationReason" size="small"
                                  placeholder="偏差原因（不符时填）" class="confirm-input" />
                        <div class="confirm-btns">
                          <el-button size="small" type="primary"
                                     @click="confirm(row, true)">与计划相符</el-button>
                          <el-button size="small" type="danger" plain
                                     @click="confirm(row, false)">反馈不符</el-button>
                        </div>
                      </template>
                      <div v-else class="done-box">
                        <span>回报 {{ row.actualState || '-' }}</span>
                        <span v-if="row.confirmedBy">· {{ row.confirmedBy }}</span>
                        <div v-if="row.deviationReason" class="deviation-text">{{ row.deviationReason }}</div>
                        <div v-if="row.note" class="cell-sub">{{ row.note }}</div>
                      </div>
                    </template>
                  </el-table-column>
                </el-table>
                <div v-if="!shownSteps.length" class="empty-tip">
                  {{ stepStage === '复供' ? '故障隔离完成后可按当前阀门实况生成复供顺序' : '暂无步骤' }}
                </div>
                <div class="sequence-rule">
                  顺序约束：必须开启阀先动作打通保供路径；⛑ 人员撤离安全门在关断序列最后关闭、复供最先开启；
                  前一步未完成或反馈不符时，后续步骤一律暂停。
                </div>
              </div>
            </div>

            <div class="side-stack">
              <div class="hs-panel">
                <div class="hs-panel-title">换热站供热后果</div>
                <div class="hs-panel-body">
                  <el-table :data="current.impacts" border size="small" max-height="240">
                    <el-table-column prop="stationCode" label="编号" width="72" />
                    <el-table-column prop="stationName" label="换热站" min-width="110" />
                    <el-table-column label="并网井" prop="tapWellNo" width="70" />
                    <el-table-column label="结果" width="86">
                      <template #default="{ row }">
                        <el-tag size="small" :type="impactTag(row.result)" effect="dark">{{ row.result }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="households" label="户数" width="56" align="right" />
                  </el-table>
                  <div class="impact-legend">
                    <span><i class="dot ok"></i>保供：仍有热源路径</span>
                    <span><i class="dot bad"></i>停暖：本次方案导致</span>
                    <span><i class="dot mute"></i>原本停暖：试算前已不可达，不误判</span>
                  </div>
                </div>
              </div>

              <div class="hs-panel">
                <div class="hs-panel-title">相关阀门现场实况（与方案基线比对）</div>
                <div class="hs-panel-body">
                  <el-table :data="current.liveValves" border size="small" max-height="200">
                    <el-table-column prop="wellNo" label="井号" width="80" />
                    <el-table-column prop="openState" label="台账实况" width="90">
                      <template #default="{ row }">
                        <el-tag size="small" :type="liveTag(row.openState)" effect="plain">
                          {{ row.openState }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="repairStatus" label="井室" width="80" />
                    <el-table-column label="标记" min-width="90">
                      <template #default="{ row }">
                        <el-tag v-if="row.inoperableFlag" size="small" type="danger" effect="plain">不可操作</el-tag>
                        <el-tag v-if="row.egressGuard" size="small" type="warning" effect="plain">安全门</el-tag>
                      </template>
                    </el-table-column>
                  </el-table>
                </div>
              </div>
            </div>
          </div>
        </template>
      </div>
    </div>

    <!-- 失败收口说明弹窗 -->
    <el-dialog v-model="finishVisible" :title="finishTitle" width="480px">
      <div class="finish-explain">{{ finishExplain }}</div>
      <el-input v-model="finishNote" type="textarea" :rows="3" placeholder="处置备注（现场指挥 / 抢修负责人）" />
      <template #footer>
        <el-button size="small" @click="finishVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="loading" @click="submitFinish">确认处置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pipeApi, isolationApi } from '../api'

const openStates = ref(['全开', '半开', '关闭'])
const plans = ref([])
const current = ref(null)
const loading = ref(false)
const mapSegments = ref([])
const mapWells = ref([])
const stepStage = ref('隔离')

const form = reactive({
  segmentId: null,
  faultReason: '',
  overrides: [],
  extraCloseText: '',
  excludedCloseText: '',
  adjustNote: ''
})

const phaseChain = [
  { key: '试算中', label: '拓扑试算' },
  { key: '待执行', label: '待执行' },
  { key: '执行中', label: '现场关断' },
  { key: '已隔离', label: '已隔离' },
  { key: '复供中', label: '复供' },
  { key: '已复供', label: '已复供' }
]

const confirmForms = reactive({})
const finishVisible = ref(false)
const finishDisposition = ref('')
const finishNote = ref('')

const selectableSegments = computed(() =>
  mapSegments.value.filter((s) => s.segmentStatus !== '停用'))

const canAdjust = computed(() =>
  current.value && ['试算中', '已暂停', '已失败'].includes(current.value.phase))

const shownSteps = computed(() =>
  (current.value?.steps || []).filter((s) => s.stage === stepStage.value))

const phaseIndex = computed(() => {
  if (!current.value) return -1
  const order = ['试算中', '待执行', '执行中', '已暂停', '已失败', '已隔离', '复供中', '已复供', '已废止']
  const p = current.value.phase
  if (p === '已暂停' || p === '已失败') return 2
  if (p === '已废止') return 0
  return phaseChain.findIndex((c) => c.key === p)
})

function phaseClass(key) {
  const idx = phaseChain.findIndex((c) => c.key === key)
  if (current.value && current.value.phase === key) return 'current'
  if (idx >= 0 && idx < phaseIndex.value) return 'done'
  return ''
}

function phaseTag(phase) {
  return {
    试算中: 'info', 待执行: 'warning', 执行中: 'primary', 已隔离: 'success',
    复供中: 'primary', 已复供: 'success', 已暂停: 'danger', 已失败: 'danger', 已废止: 'info'
  }[phase] || 'info'
}

function kindTag(kind) {
  return { 计划关闭: 'danger', 原本关闭: 'info', 必须开启: 'success', 计划开启: 'success' }[kind] || 'info'
}

function stepTag(status) {
  return { 已完成: 'success', 跳过: 'info', 偏差: 'danger', 冲突: 'danger', 暂停: 'warning', 待执行: 'primary' }[status] || 'info'
}

function impactTag(result) {
  return { 保供: 'success', 停暖: 'danger', 原本停暖: 'info' }[result] || 'info'
}

function liveTag(state) {
  return { 全开: 'success', 半开: 'warning', 关闭: 'danger' }[state] || 'info'
}

function wellClass(w) {
  if (w.openState === '全开') return 'open'
  if (w.openState === '关闭') return 'closed'
  return 'half'
}

function wellMark(w) {
  return w.openState === '全开' ? '开' : w.openState === '关闭' ? '关' : '半'
}

function segWidth(dn) {
  return 4 + (Number(dn) / 100) * 1.6
}

function fmtArea(v) {
  if (v === null || v === undefined) return '0'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function pickSegment(seg) {
  if (seg.segmentStatus === '停用') {
    ElMessage.warning('停用管段不在运行管网上，无需隔离')
    return
  }
  form.segmentId = seg.id
}

function pickWell(w) {
  let row = form.overrides.find((o) => o.wellNo === w.wellNo)
  if (!row) {
    row = { wellNo: w.wellNo, openState: w.openState, inoperable: !!w.inoperableFlag }
    form.overrides.push(row)
  }
}

function addOverride() {
  form.overrides.push({ wellNo: null, openState: null, inoperable: false })
}

function buildTrialPayload(strategy) {
  return {
    segmentId: form.segmentId,
    faultReason: form.faultReason,
    strategy: strategy || null,
    valveOverrides: form.overrides
      .filter((o) => o.wellNo && (o.openState || o.inoperable))
      .map((o) => ({ wellNo: o.wellNo, openState: o.openState || null, inoperable: o.inoperable })),
    extraCloseWells: splitCsv(form.extraCloseText),
    excludedCloseWells: splitCsv(form.excludedCloseText),
    adjustNote: form.adjustNote
  }
}

function splitCsv(text) {
  return (text || '').split(/[,，\s]+/).map((s) => s.trim()).filter(Boolean)
}

async function runTrial(strategy) {
  if (!form.segmentId) {
    ElMessage.warning('请先选择故障管段')
    return
  }
  loading.value = true
  try {
    const payload = buildTrialPayload(strategy)
    payload.planId = editingPlanId.value
    const vo = await isolationApi.trial(payload)
    current.value = vo
    editingPlanId.value = vo.id
    seedConfirmForms(vo)
    await loadPlans()
    ElMessage.success(vo.isolatedProven ? '试算完成：故障段已证明可隔开' : '试算完成：证明未通过，请选择扩大隔离或拒绝启用')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const editingPlanId = ref(null)

function editTrial() {
  if (!current.value) return
  editingPlanId.value = current.value.id
  form.segmentId = current.value.faultSegmentId
  form.faultReason = current.value.faultReason || ''
  ElMessage.info('已载入该方案，可调整现场阀态/取舍后重新试算')
}

async function activate() {
  loading.value = true
  try {
    current.value = await isolationApi.activate(current.value.id)
    seedConfirmForms(current.value)
    ElMessage.success('方案已激活，进入待执行')
    await loadPlans()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function startExec() {
  loading.value = true
  try {
    current.value = await isolationApi.start(current.value.id)
    seedConfirmForms(current.value)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

function canConfirm(row) {
  if (!current.value) return false
  if (row.stepStatus === '已完成' || row.stepStatus === '跳过') return false
  const stageSteps = (current.value.steps || []).filter((s) => s.stage === row.stage)
  // 前面任一步未完成（尤其存在偏差/暂停）时，后续步骤一律不可操作
  const prior = stageSteps.filter((s) => s.stepNo < row.stepNo)
  if (!prior.every((s) => s.stepStatus === '已完成' || s.stepStatus === '跳过')) return false
  if (row.stage === '隔离') {
    return ['执行中', '已暂停'].includes(current.value.phase)
      && current.value.nextIsolateStepNo === row.stepNo
  }
  return ['已隔离', '复供中', '已暂停'].includes(current.value.phase)
    && current.value.nextRestoreStepNo === row.stepNo
}

function seedConfirmForms(vo) {
  for (const step of vo.steps || []) {
    if (!confirmForms[step.id]) {
      confirmForms[step.id] = { actualState: step.plannedState, confirmedBy: '', deviationReason: '' }
    }
  }
}

async function confirm(row, matchPlan) {
  const data = confirmForms[row.id] || {}
  if (matchPlan && !data.confirmedBy) {
    ElMessage.warning('请填写现场确认人')
    return
  }
  if (!matchPlan && !data.deviationReason) {
    ElMessage.warning('反馈与计划不符时必须填写偏差原因，后续步骤将暂停')
    return
  }
  loading.value = true
  try {
    const payload = {
      actualState: data.actualState,
      confirmedBy: data.confirmedBy,
      matchPlan,
      deviationReason: data.deviationReason
    }
    const vo = row.stage === '隔离'
      ? await isolationApi.confirmStep(current.value.id, row.id, payload)
      : await isolationApi.confirmRestoreStep(current.value.id, row.id, payload)
    current.value = vo
    seedConfirmForms(vo)
    await loadPlans()
    if (!matchPlan) {
      ElMessageBox.alert(row.deviationReason || '现场反馈与计划不符，后续步骤已暂停', '步骤暂停', { type: 'warning' })
    } else {
      ElMessage.success('步骤 ' + row.stepNo + ' 已确认')
    }
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

const finishTitle = computed(() => ({
  EXPAND: '扩大隔离 · 重新试算',
  REVERT: '按安全顺序恢复已动阀门',
  HOLD: '保持现场状态等待处置'
}[finishDisposition.value]))

const finishExplain = computed(() => ({
  EXPAND: '将在原方案基础上按「扩大隔离」重新拓扑试算：追加关闭外侧可操作阀，围禁范围与停暖换热站以新试算为准。',
  REVERT: '只回退已确认完成的阀门，按关断顺序逆向恢复到操作前状态；出现偏差的阀门保持现场不动，方案废止。',
  HOLD: '不再操作任何阀门，方案置为失败，保留现场隔离与完成记录，等待抢修或调度进一步处置。'
}[finishDisposition.value]))

function finishDialog(disposition) {
  finishDisposition.value = disposition
  finishNote.value = ''
  finishVisible.value = true
}

async function submitFinish() {
  loading.value = true
  try {
    const vo = await isolationApi.finishFailure(current.value.id, {
      disposition: finishDisposition.value,
      note: finishNote.value
    })
    finishVisible.value = false
    current.value = vo
    seedConfirmForms(vo)
    await loadPlans()
    ElMessage.success('处置完成')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function planRestore() {
  loading.value = true
  try {
    const vo = await isolationApi.planRestore(current.value.id)
    current.value = vo
    seedConfirmForms(vo)
    stepStage.value = '复供'
    await loadPlans()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function loadPlans() {
  try {
    plans.value = await isolationApi.plans('active')
  } catch (e) {
    plans.value = []
  }
}

async function loadPlan(id) {
  try {
    const vo = await isolationApi.plan(id)
    current.value = vo
    editingPlanId.value = id
    form.segmentId = vo.faultSegmentId
    form.faultReason = vo.faultReason || ''
    seedConfirmForms(vo)
    if ((vo.steps || []).some((s) => s.stage === '复供')) stepStage.value = '复供'
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function startNew() {
  current.value = null
  editingPlanId.value = null
  form.segmentId = null
  form.faultReason = ''
  form.overrides = []
  form.extraCloseText = ''
  form.excludedCloseText = ''
  form.adjustNote = ''
}

async function reload() {
  await Promise.all([loadPlans(), loadMap()])
  if (current.value) await loadPlan(current.value.id)
}

async function loadMap() {
  try {
    const [segments, wells] = await Promise.all([
      pipeApi.segments(null, 'with-valves'),
      pipeApi.valveWells(null, 'with-segment')
    ])
    mapSegments.value = segments
    mapWells.value = wells
  } catch (e) {
    ElMessage.error(e.message)
  }
}

onMounted(async () => {
  await loadMap()
  await loadPlans()
})
</script>

<style scoped>
.iso-page {
  padding-bottom: 24px;
}

.phase-bar {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 14px;
}

.phase-node {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: var(--hs-muted);
}

.phase-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: #d7e0ec;
  color: #5a6f8a;
  font-style: normal;
  font-size: 12px;
}

.phase-node.done .phase-dot {
  background: var(--hs-ok);
  color: #fff;
}

.phase-node.done {
  color: var(--hs-ok);
}

.phase-node.current {
  color: var(--hs-primary-deep);
  font-weight: 700;
}

.phase-node.current .phase-dot {
  background: var(--hs-primary);
  color: #fff;
  box-shadow: 0 0 0 4px rgba(13, 71, 161, 0.15);
}

.phase-link {
  flex: 1;
  height: 2px;
  background: #d7e0ec;
  border-radius: 1px;
  min-width: 18px;
}

.phase-link.on {
  background: var(--hs-ok);
}

.iso-body {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}

.iso-side {
  width: 282px;
  flex: 0 0 282px;
}

.iso-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.side-body {
  padding: 10px;
}

.new-btn {
  width: 100%;
  margin-bottom: 10px;
}

.plan-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 620px;
  overflow: auto;
}

.plan-card {
  border: 1px solid var(--hs-line);
  border-radius: 8px;
  padding: 8px 10px;
  cursor: pointer;
  background: #fff;
  transition: border-color 0.15s ease;
}

.plan-card:hover {
  border-color: var(--hs-primary-light-5);
}

.plan-card.active {
  border-color: var(--hs-primary);
  box-shadow: 0 0 0 2px rgba(13, 71, 161, 0.12);
}

.plan-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
}

.plan-card-seg {
  font-size: 12px;
  color: var(--hs-ink);
  margin: 4px 0;
}

.plan-card-meta {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 11px;
  color: var(--hs-muted);
  flex-wrap: wrap;
}

.empty-tip {
  color: var(--hs-muted);
  font-size: 12.5px;
  text-align: center;
  padding: 24px 8px;
}

.iso-map {
  width: 100%;
  height: 360px;
  background: #fff;
  border: 1px solid var(--hs-line);
  border-radius: 8px;
}

.map-pipe {
  fill: none;
  stroke: #2ea44f;
  stroke-linecap: round;
  cursor: pointer;
}

.map-pipe:hover {
  stroke: var(--hs-primary);
}

.map-pipe.fault {
  stroke: var(--hs-danger);
  stroke-dasharray: 10 6;
}

.map-pipe.disabled {
  stroke: #aab8c9;
  stroke-dasharray: 6 6;
  cursor: not-allowed;
}

.map-well {
  cursor: pointer;
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

.well-mark {
  font-size: 11px;
  fill: #fff;
  text-anchor: middle;
  pointer-events: none;
}

.well-name {
  font-size: 10px;
  fill: #16324f;
  text-anchor: middle;
  pointer-events: none;
}

.trial-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.trial-form {
  display: flex;
  flex-direction: column;
  gap: 10px;
  background: #f7fafd;
  border: 1px dashed var(--hs-line);
  border-radius: 8px;
  padding: 12px;
}

.form-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

.form-label {
  width: 92px;
  flex: 0 0 92px;
  font-size: 12.5px;
  color: var(--hs-muted);
  text-align: right;
}

.adjust-block {
  border-top: 1px dashed var(--hs-line);
  padding-top: 8px;
}

.adjust-title {
  font-size: 12.5px;
  font-weight: 600;
  color: var(--hs-primary-deep);
  margin-bottom: 8px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.override-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin-bottom: 6px;
}

.plan-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.plan-head-tags {
  display: flex;
  gap: 6px;
}

.proof-box {
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 12.5px;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  margin-bottom: 8px;
}

.proof-box.ok {
  background: #ecf8ef;
  border: 1px solid #b7e2c2;
  color: #1c6b34;
}

.proof-box.bad {
  background: #fdeeee;
  border: 1px solid #f3c2c2;
  color: #a12727;
}

.kv-line {
  font-size: 12.5px;
  margin: 4px 0;
}

.kv-grid {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 8px 0;
  font-size: 12.5px;
}

.kv-tag {
  margin: 2px 4px 2px 0;
}

.kv-none {
  color: var(--hs-muted);
}

.impact-facts {
  display: flex;
  gap: 10px;
  margin: 10px 0;
  flex-wrap: wrap;
}

.impact-fact {
  flex: 1;
  min-width: 120px;
  border-radius: 8px;
  padding: 8px 12px;
  background: #f4f7fb;
  border: 1px solid var(--hs-line);
}

.impact-fact b {
  display: block;
  font-size: 22px;
}

.impact-fact span {
  font-size: 11.5px;
  color: var(--hs-muted);
}

.impact-fact.danger b {
  color: var(--hs-danger);
}

.impact-fact.warn b {
  color: var(--hs-hot);
}

.impact-fact.ok b {
  color: var(--hs-ok);
}

.choice-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  margin: 10px 0;
}

.choice-card {
  border-radius: 8px;
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  border: 1px solid;
}

.choice-card.expand {
  background: #fff7ef;
  border-color: #f2c595;
}

.choice-card.reject {
  background: #f3f6fa;
  border-color: #c3d0e0;
}

.choice-card.disabled {
  opacity: 0.6;
}

.choice-title {
  font-weight: 700;
  font-size: 13px;
}

.choice-text {
  font-size: 12px;
  color: #4b5f79;
  line-height: 1.6;
}

.choice-tags {
  font-size: 12px;
}

.action-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  margin: 10px 0 4px;
}

.action-hint {
  font-size: 12px;
  color: var(--hs-danger);
}

.note-line {
  font-size: 12px;
  color: var(--hs-muted);
  background: #f7fafd;
  border-radius: 6px;
  padding: 6px 10px;
  margin-top: 6px;
  line-height: 1.6;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1.35fr 1fr;
  gap: 12px;
  align-items: flex-start;
}

.side-stack {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cell-sub {
  font-size: 11px;
  color: var(--hs-muted);
}

.egress-ico {
  margin-left: 4px;
}

.confirm-input {
  margin-top: 4px;
}

.confirm-btns {
  margin-top: 6px;
  display: flex;
  gap: 6px;
}

.done-box {
  font-size: 12px;
  color: var(--hs-ink);
}

.deviation-text {
  color: var(--hs-danger);
  margin-top: 2px;
}

.sequence-rule {
  font-size: 11.5px;
  color: var(--hs-muted);
  background: #f7fafd;
  border-radius: 6px;
  padding: 6px 10px;
  margin-top: 8px;
  line-height: 1.6;
}

.impact-legend {
  display: flex;
  gap: 10px;
  font-size: 11.5px;
  color: var(--hs-muted);
  margin-top: 8px;
  flex-wrap: wrap;
}

.impact-legend .dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.dot.ok {
  background: var(--hs-ok);
}

.dot.bad {
  background: var(--hs-danger);
}

.dot.mute {
  background: #aab8c9;
}

.finish-explain {
  font-size: 13px;
  line-height: 1.7;
  color: var(--hs-ink);
  margin-bottom: 12px;
  background: #f7fafd;
  padding: 10px;
  border-radius: 8px;
}
</style>
