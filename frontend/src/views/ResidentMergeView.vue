<template>
  <div class="hs-page merge-page">
    <div class="hs-head">
      <div>
        <h2>重复热用户档案归并</h2>
        <div class="hs-sub">
          选择保留档案与待归并档案后先做归并预检：地址、热表号、换热站、供热状态，以及双方的室温抽测、报修记录、各年度采暖费一次看清。
          只有地址确实指向同一住房才能继续；热表号或换热站不同必须停止归并；供热状态不一致须明确归并后状态。
        </div>
      </div>
      <div class="hs-head-ops">
        <el-button size="small" plain @click="reloadAll">刷新</el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="hs-tabs">
      <el-tab-pane label="档案归并" name="wizard" />
      <el-tab-pane label="落档查询" name="resolve" />
      <el-tab-pane label="财务复核" name="review" />
      <el-tab-pane label="归并审计" name="audits" />
    </el-tabs>

    <!-- ================= 归并向导 ================= -->
    <template v-if="activeTab === 'wizard'">
      <el-steps :active="step" align-center finish-status="success" class="merge-steps">
        <el-step title="选择档案" description="保留档案 + 待归并档案" />
        <el-step title="归并预检" description="地址/热表/换热站/状态/引用" />
        <el-step title="冲突决策" description="供热状态选择 / 收费处置确认" />
        <el-step title="确认归并" description="引用改挂、重复档只读" />
      </el-steps>

      <!-- 步骤 1：选档 -->
      <div v-if="step === 0" class="hs-panel">
        <div class="hs-panel-title">
          疑似重复档案（同址在档 ≥ 2 户）
          <span class="hs-muted" style="font-weight: 400">也可在下方手动指定两个档案编号</span>
        </div>
        <div class="hs-panel-body">
          <el-table :data="candidates" border size="small" empty-text="未发现同址重复建档">
            <el-table-column label="同一住房" min-width="220">
              <template #default="{ row }">
                <b>{{ row.fullAddress }}</b>
              </template>
            </el-table-column>
            <el-table-column label="在档档案" min-width="520">
              <template #default="{ row }">
                <div v-for="p in row.profiles" :key="p.residentId" class="cand-row">
                  <el-tag size="small" effect="plain" type="info">#{{ p.residentId }}</el-tag>
                  <span class="cand-meter">热表 {{ p.meterNo || '-' }}</span>
                  <span>面积 {{ p.areaM2 }} ㎡</span>
                  <span>{{ p.stationCode }}</span>
                  <el-tag size="small" :type="p.supplyStatus === '正常供热' ? 'success' : 'warning'">
                    {{ p.supplyStatus }}
                  </el-tag>
                  <span class="hs-muted">抽测 {{ p.sampleCount }} · 报修 {{ p.orderCount }} · 收费 {{ p.feeCount }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="归并预检" width="220" align="center">
              <template #default="{ row }">
                <template v-for="(p, idx) in row.profiles" :key="p.residentId">
                  <el-button v-if="idx === 0" size="small" type="primary" plain
                             @click="pairFromCandidate(row, p, otherOf(row, p))">
                    保留 #{{ p.residentId }} → 归并 #{{ otherOf(row, p).residentId }}
                  </el-button>
                  <el-button v-else size="small" plain
                             @click="pairFromCandidate(row, p, otherOf(row, p))">
                    反向：保留 #{{ p.residentId }}
                  </el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="hs-panel">
        <div class="hs-panel-title">手动指定档案</div>
        <div class="hs-panel-body manual-pick">
          <div class="pick-item">
            <label>保留档案编号</label>
            <el-input-number v-model="keeperId" :min="1" controls-position="right" style="width: 150px" />
            <span class="hs-muted" v-if="profileById(keeperId)">{{ profileLabel(profileById(keeperId)) }}</span>
          </div>
          <div class="pick-item">
            <label>待归并档案编号</label>
            <el-input-number v-model="mergedId" :min="1" controls-position="right" style="width: 150px" />
            <span class="hs-muted" v-if="profileById(mergedId)">{{ profileLabel(profileById(mergedId)) }}</span>
          </div>
          <el-button type="primary" :loading="previewLoading" @click="runPreview">开始归并预检</el-button>
        </div>
      </div>

      <!-- 步骤 2/3：预检结果 -->
      <template v-if="preview">
        <!-- 硬阻断 -->
        <div class="hs-panel" v-if="!preview.mergeable">
          <div class="hs-panel-title danger-title">预检未通过 · 必须停止归并</div>
          <div class="hs-panel-body">
            <el-alert v-for="(b, i) in preview.blockers" :key="i" :title="b" type="error"
                      :closable="false" show-icon class="blocker-alert" />
            <div class="step-ops">
              <el-button @click="backToPick">返回重选</el-button>
            </div>
          </div>
        </div>

        <template v-else>
          <!-- 档案对照 -->
          <div class="hs-panel">
            <div class="hs-panel-title">
              归并预检对照
              <el-tag size="small" type="success" effect="dark" class="ok-tag">地址同一住房 · 允许归并</el-tag>
            </div>
            <div class="hs-panel-body">
              <table class="compare-table">
                <thead>
                  <tr>
                    <th style="width: 130px">比对项</th>
                    <th>保留档案</th>
                    <th style="width: 70px">结论</th>
                    <th>待归并档案</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td class="cmp-key">档案编号</td>
                    <td><b>#{{ preview.keeper.residentId }}</b></td>
                    <td></td>
                    <td><b>#{{ preview.merged.residentId }}</b></td>
                  </tr>
                  <tr>
                    <td class="cmp-key">地址</td>
                    <td>{{ preview.keeper.fullAddress }}</td>
                    <td><el-tag size="small" type="success">同一住房</el-tag></td>
                    <td>{{ preview.merged.fullAddress }}</td>
                  </tr>
                  <tr>
                    <td class="cmp-key">热表号</td>
                    <td>{{ preview.keeper.meterNo || '-' }}</td>
                    <td><el-tag size="small" :type="preview.meterMatch ? 'success' : 'danger'">
                      {{ preview.meterMatch ? '一致' : '不一致' }}
                    </el-tag></td>
                    <td>{{ preview.merged.meterNo || '-' }}</td>
                  </tr>
                  <tr>
                    <td class="cmp-key">所属换热站</td>
                    <td>{{ preview.keeper.stationCode }} {{ preview.keeper.stationName }}</td>
                    <td><el-tag size="small" :type="preview.stationMatch ? 'success' : 'danger'">
                      {{ preview.stationMatch ? '一致' : '不一致' }}
                    </el-tag></td>
                    <td>{{ preview.merged.stationCode }} {{ preview.merged.stationName }}</td>
                  </tr>
                  <tr>
                    <td class="cmp-key">建筑面积</td>
                    <td>{{ preview.keeper.areaM2 }} ㎡</td>
                    <td></td>
                    <td>{{ preview.merged.areaM2 }} ㎡</td>
                  </tr>
                  <tr>
                    <td class="cmp-key">供热状态</td>
                    <td>
                      <el-tag size="small" :type="preview.keeper.supplyStatus === '正常供热' ? 'success' : 'warning'">
                        {{ preview.keeper.supplyStatus }}
                      </el-tag>
                    </td>
                    <td>
                      <el-tag size="small" :type="preview.supplyStatusMatch ? 'success' : 'danger'">
                        {{ preview.supplyStatusMatch ? '一致' : '不一致' }}
                      </el-tag>
                    </td>
                    <td>
                      <el-tag size="small" :type="preview.merged.supplyStatus === '正常供热' ? 'success' : 'warning'">
                        {{ preview.merged.supplyStatus }}
                      </el-tag>
                    </td>
                  </tr>
                  <tr>
                    <td class="cmp-key">联系电话</td>
                    <td>{{ preview.keeper.contactPhone || '-' }}</td>
                    <td></td>
                    <td>{{ preview.merged.contactPhone || '-' }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>

          <!-- 供热状态决策 -->
          <div class="hs-panel" v-if="!preview.supplyStatusMatch">
            <div class="hs-panel-title warn-title">
              供热状态不一致 · 必须明确归并后采用的状态
            </div>
            <div class="hs-panel-body">
              <el-radio-group v-model="chosenSupplyStatus" class="status-radio">
                <el-radio-button v-for="im in preview.supplyStatusImpacts" :key="im.supplyStatus"
                                 :label="im.supplyStatus">
                  归并后「{{ im.supplyStatus }}」
                </el-radio-button>
              </el-radio-group>
              <div v-if="currentImpact" class="impact-box">
                <div class="impact-line"><span class="impact-tag temp">室温抽测</span>{{ currentImpact.tempImpact }}</div>
                <div class="impact-line"><span class="impact-tag order">报修</span>{{ currentImpact.orderImpact }}</div>
                <div class="impact-line"><span class="impact-tag fee">缴费</span>{{ currentImpact.feeImpact }}</div>
              </div>
              <el-alert v-if="!chosenSupplyStatus" type="warning" :closable="false" show-icon
                        title="未选择归并后供热状态前不能确认归并" />
            </div>
          </div>

          <!-- 室温抽测引用 -->
          <div class="hs-panel">
            <div class="hs-panel-title">
              关联室温抽测（共 {{ preview.samples.length }} 条，归并后全部改挂保留档案；抽测日期、室温、结论不变）
            </div>
            <div class="hs-panel-body">
              <el-table :data="preview.samples" border size="small" max-height="240">
                <el-table-column label="原属" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.side === 'keeper' ? 'primary' : 'warning'">
                      {{ row.side === 'keeper' ? '保留档案' : '待归并' }} #{{ sideId(row.side) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="sampleDate" label="抽测日期" width="110" />
                <el-table-column prop="temperature" label="室温 ℃" width="90" align="right" />
                <el-table-column label="结论" width="80">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.qualified ? 'success' : 'danger'">
                      {{ row.qualified ? '达标' : '不达标' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="householdName" label="住户" width="100" />
                <el-table-column prop="surveyor" label="抽测人" width="90" />
                <el-table-column prop="id" label="记录编号" width="90" />
              </el-table>
            </div>
          </div>

          <!-- 报修引用 -->
          <div class="hs-panel">
            <div class="hs-panel-title">
              关联报修工单（共 {{ preview.orders.length }} 条，归并后全部改挂保留档案；工单号、时间、处理结果不变）
            </div>
            <div class="hs-panel-body">
              <el-table :data="preview.orders" border size="small" max-height="240">
                <el-table-column label="原属" width="110">
                  <template #default="{ row }">
                    <el-tag size="small" :type="row.side === 'keeper' ? 'primary' : 'warning'">
                      {{ row.side === 'keeper' ? '保留档案' : '待归并' }} #{{ sideId(row.side) }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="orderNo" label="工单号" width="140" />
                <el-table-column prop="reportedDate" label="报修日期" width="110" />
                <el-table-column prop="faultType" label="故障" width="80" />
                <el-table-column label="状态" width="90">
                  <template #default="{ row }">
                    <el-tag size="small" :type="orderTag(row.orderStatus)">{{ row.orderStatus }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="assignee" label="派工人" width="90" />
                <el-table-column prop="handleResult" label="处理结果" min-width="200" show-overflow-tooltip />
              </el-table>
            </div>
          </div>

          <!-- 收费年度 -->
          <div class="hs-panel">
            <div class="hs-panel-title">
              各年度采暖费比对与处置方式（同年度不得简单相加）
            </div>
            <div class="hs-panel-body">
              <el-table :data="preview.feeYears" border size="small" row-key="feeYear">
                <el-table-column prop="feeYear" label="收费年度" width="90" />
                <el-table-column label="保留档案台账" min-width="230">
                  <template #default="{ row }">
                    <fee-row-view v-if="row.keeperRow" :row="row.keeperRow" />
                    <span v-else class="hs-muted">无台账</span>
                  </template>
                </el-table-column>
                <el-table-column label="待归并档案台账" min-width="230">
                  <template #default="{ row }">
                    <fee-row-view v-if="row.mergedRow" :row="row.mergedRow" />
                    <span v-else class="hs-muted">无台账</span>
                  </template>
                </el-table-column>
                <el-table-column label="拟处置" min-width="260">
                  <template #default="{ row }">
                    <el-tag size="small" :type="actionTag(row.action)" effect="dark">{{ row.actionLabel }}</el-tag>
                    <div class="action-note">{{ row.actionNote }}</div>
                    <el-alert v-if="row.payBlockedUntilReview" type="error" :closable="false" show-icon
                              title="财务复核完成前，禁止继续登记该年度缴费" style="margin-top: 4px" />
                    <div v-if="row.action === 'RECOMPUTE'" class="recompute-box">
                      重算应收：<b>{{ money(row.recomputedReceivable) }} 元</b>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>

          <!-- 确认 -->
          <div class="hs-panel">
            <div class="hs-panel-title">确认归并</div>
            <div class="hs-panel-body">
              <el-alert type="info" :closable="false" show-icon
                        title="确认后：室温抽测、报修记录、收费记录全部改挂保留档案（业务编号、时间、处理结果不变）；待归并档案转为只读并可一键跳转到保留档案；按地址、热表号、旧档案编号查询都只落到保留档案。任一引用迁移、冲突处理或审计写入失败，整次归并回退。" />
              <div class="confirm-line">
                <span>经办人（可选）</span>
                <el-input v-model="operator" placeholder="如：营业厅 刘敏" style="width: 200px" />
                <el-checkbox v-model="acknowledged">
                  我已核对地址为同一住房、热表号/换热站一致{{ preview.supplyStatusMatch ? '' : '、已明确归并后供热状态' }}
                </el-checkbox>
              </div>
              <div class="step-ops">
                <el-button @click="backToPick">返回重选</el-button>
                <el-button type="primary" :loading="confirmLoading" :disabled="!canConfirm" @click="doConfirm">
                  确认归并
                </el-button>
              </div>
            </div>
          </div>
        </template>
      </template>
    </template>

    <!-- ================= 落档查询 ================= -->
    <template v-if="activeTab === 'resolve'">
      <div class="hs-panel">
        <div class="hs-panel-title">按地址 / 热表号 / 旧档案编号查询（已归并档案自动跳到保留档案，只落到同一户）</div>
        <div class="hs-panel-body resolve-bar">
          <el-input v-model="resolveKeyword" placeholder="如：朝阳里小区1号楼1单元101 / RB20260001 / 16"
                    clearable style="width: 420px" @keyup.enter="doResolve" />
          <el-button type="primary" :loading="resolveLoading" @click="doResolve">查询落档</el-button>
        </div>
        <div class="hs-panel-body" v-if="resolveResult">
          <el-alert v-if="resolveResult.redirected" type="warning" :closable="false" show-icon
                    :title="resolveResult.redirectNote" class="resolve-alert" />
          <el-descriptions :column="3" border size="small" class="resolve-desc">
            <el-descriptions-item label="落点档案编号">#{{ resolveResult.resident.id }}</el-descriptions-item>
            <el-descriptions-item label="地址">{{ resolveAddress(resolveResult.resident) }}</el-descriptions-item>
            <el-descriptions-item label="热表号">{{ resolveResult.resident.meterNo }}</el-descriptions-item>
            <el-descriptions-item label="换热站">{{ resolveResult.resident.stationCode }}</el-descriptions-item>
            <el-descriptions-item label="供热状态">{{ resolveResult.resident.supplyStatus }}</el-descriptions-item>
            <el-descriptions-item label="欠费 元">{{ money(resolveResult.resident.arrears) }}</el-descriptions-item>
            <el-descriptions-item label="归并单号" v-if="resolveResult.mergeNo">
              {{ resolveResult.mergeNo }}
            </el-descriptions-item>
            <el-descriptions-item label="原重复档案" v-if="resolveResult.sourceResidentId">
              #{{ resolveResult.sourceResidentId }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </div>

      <div class="hs-panel">
        <div class="hs-panel-title">已归并只读档案（点击跳转到保留档案）</div>
        <div class="hs-panel-body">
          <el-table :data="mergedProfiles" border size="small" empty-text="暂无已归并档案">
            <el-table-column label="旧档案编号" width="110">
              <template #default="{ row }">#{{ row.id }}</template>
            </el-table-column>
            <el-table-column label="地址" min-width="220">
              <template #default="{ row }">{{ resolveAddress(row) }}</template>
            </el-table-column>
            <el-table-column prop="meterNo" label="热表号" width="130" />
            <el-table-column prop="mergeNo" label="归并单号" width="160" />
            <el-table-column label="状态" width="100">
              <template #default>
                <el-tag size="small" type="info">只读</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="170">
              <template #default="{ row }">
                <el-button size="small" type="primary" plain
                           @click="jumpToKeeper(row.mergedIntoId)">跳转保留档案 #{{ row.mergedIntoId }}</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>

    <!-- ================= 财务复核 ================= -->
    <template v-if="activeTab === 'review'">
      <div class="hs-panel">
        <div class="hs-panel-title">
          归并待财务复核台账
          <span class="hs-muted" style="font-weight: 400">同年度两边均有实收，保留原始金额；复核前该年度禁止登记缴费</span>
        </div>
        <div class="hs-panel-body">
          <el-table :data="reviewGroups" border size="small" empty-text="暂无待复核记录" :row-class-name="reviewRowClass">
            <el-table-column type="expand">
              <template #default="{ row }">
                <div class="review-expand">
                  <div v-for="r in row.rows" :key="r.id" class="review-sub">
                    <el-tag size="small" :type="r.id === row.keepId ? 'success' : 'info'">
                      {{ r.id === row.keepId ? '保留台账' : '重复台账（将作废冻结）' }} #{{ r.id }}
                    </el-tag>
                    <span>{{ r.feeYear }} 年度</span>
                    <span>应收 {{ money(r.receivable) }}</span>
                    <span>实收 <b>{{ money(r.paid) }}</b></span>
                    <span>欠费 {{ money(r.arrears) }}</span>
                    <span class="hs-muted">最近缴费 {{ r.lastPayDate || '-' }}</span>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="feeYear" label="收费年度" width="90" />
            <el-table-column label="保留档案" min-width="200">
              <template #default="{ row }">
                #{{ row.keeperId }} {{ row.community }}{{ row.buildingNo }}号楼{{ row.unitNo }}单元{{ row.roomNo }}
              </template>
            </el-table-column>
            <el-table-column prop="stationCode" label="换热站" width="100" />
            <el-table-column label="台账笔数" width="90" align="center">
              <template #default="{ row }">{{ row.rows.length }}</template>
            </el-table-column>
            <el-table-column label="两边实收合计" width="130" align="right">
              <template #default="{ row }"><b>{{ money(row.totalPaid) }} 元</b></template>
            </el-table-column>
            <el-table-column label="保留台账原始应收" width="140" align="right">
              <template #default="{ row }">{{ money(row.keeperReceivable) }} 元</template>
            </el-table-column>
            <el-table-column label="财务复核" width="240">
              <template #default="{ row }">
                <div class="review-ops">
                  <el-input-number v-model="confirmedMap[row.key]" :precision="2" :step="100" :min="0"
                                   size="small" controls-position="right" style="width: 120px"
                                   :placeholder="String(row.keeperReceivable)" />
                  <el-button size="small" type="primary" plain :loading="reviewLoading[row.key]"
                             @click="resolveReview(row)">
                    复核完成
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </template>

    <!-- ================= 审计 ================= -->
    <template v-if="activeTab === 'audits'">
      <div class="hs-panel">
        <div class="hs-panel-title">归并审计（一次归并一行，迁移条数 / 重算年度 / 复核年度 / 逐类处置明细）</div>
        <div class="hs-panel-body">
          <el-table :data="audits" border size="small" empty-text="暂无归并记录">
            <el-table-column type="expand">
              <template #default="{ row }">
                <pre class="audit-json">{{ formatDetails(row.detailsJson) }}</pre>
              </template>
            </el-table-column>
            <el-table-column prop="mergeNo" label="归并单号" width="150" />
            <el-table-column label="保留 → 待归并" width="150">
              <template #default="{ row }">#{{ row.keeperId }} → #{{ row.mergedId }}</template>
            </el-table-column>
            <el-table-column prop="keeperSnapshot" label="保留档案快照" min-width="220" show-overflow-tooltip />
            <el-table-column prop="mergedSnapshot" label="重复档案快照" min-width="220" show-overflow-tooltip />
            <el-table-column prop="chosenSupplyStatus" label="采用状态" width="100" />
            <el-table-column label="抽测改挂" width="90" align="right">
              <template #default="{ row }">{{ row.sampleMovedCount }}</template>
            </el-table-column>
            <el-table-column label="报修改挂" width="90" align="right">
              <template #default="{ row }">{{ row.orderMovedCount }}</template>
            </el-table-column>
            <el-table-column label="收费改挂" width="90" align="right">
              <template #default="{ row }">{{ row.feeMovedCount }}</template>
            </el-table-column>
            <el-table-column label="重算年度" width="100">
              <template #default="{ row }">
                <el-tag v-if="row.feeRecomputeYear" size="small" type="success">{{ row.feeRecomputeYear }}</el-tag>
                <span v-else class="hs-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column label="待复核年度" width="110">
              <template #default="{ row }">
                <el-tag v-if="row.feeReviewYear" size="small" type="danger">{{ row.feeReviewYear }}</el-tag>
                <span v-else class="hs-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="operator" label="经办人" width="100" />
            <el-table-column prop="createdAt" label="归并时间" width="170" />
          </el-table>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { monitorApi, residentMergeApi, feeApi } from '../api'
import FeeRowView from './FeeRowView.vue'

const props = defineProps({
  /** 从已归并只读档案跳转时携带：#/merge?jump=旧档案编号 */
  jump: { type: [String, Number], default: '' },
  /** 初始子页：wizard / resolve / review / audits */
  tab: { type: String, default: '' }
})

const activeTab = ref(props.tab === 'resolve' || props.tab === 'review' || props.tab === 'audits'
  ? props.tab
  : 'wizard')
const step = ref(0)
const candidates = ref([])
const allResidents = ref([])
const keeperId = ref(null)
const mergedId = ref(null)
const preview = ref(null)
const chosenSupplyStatus = ref('')
const operator = ref('')
const acknowledged = ref(false)
const previewLoading = ref(false)
const confirmLoading = ref(false)

const resolveKeyword = ref('')
const resolveResult = ref(null)
const resolveLoading = ref(false)
const mergedProfiles = ref([])

const reviewRows = ref([])
const reviewLoading = ref({})
const confirmedMap = ref({})

const audits = ref([])

const currentImpact = computed(() => {
  if (!preview.value || !preview.value.supplyStatusImpacts) return null
  return preview.value.supplyStatusImpacts.find((i) => i.supplyStatus === chosenSupplyStatus.value) || null
})

const canConfirm = computed(() => {
  if (!preview.value || !preview.value.mergeable) return false
  if (!preview.value.supplyStatusMatch && !chosenSupplyStatus.value) return false
  return acknowledged.value
})

const reviewGroups = computed(() => {
  const map = new Map()
  for (const r of reviewRows.value) {
    const key = `${r.residentId}-${r.feeYear}`
    if (!map.has(key)) {
      map.set(key, {
        key,
        feeYear: r.feeYear,
        keeperId: r.residentId,
        community: r.community,
        buildingNo: r.buildingNo,
        unitNo: r.unitNo,
        roomNo: r.roomNo,
        stationCode: r.stationCode,
        rows: []
      })
    }
    map.get(key).rows.push(r)
  }
  const groups = []
  for (const g of map.values()) {
    g.rows.sort((a, b) => a.id - b.id)
    g.keepId = g.rows[0].id
    g.totalPaid = g.rows.reduce((s, x) => s + Number(x.paid || 0), 0)
    g.keeperReceivable = g.rows[0].receivable
    groups.push(g)
  }
  return groups.sort((a, b) => a.feeYear - b.feeYear || a.keeperId - b.keeperId)
})

function money(value) {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function resolveAddress(r) {
  return `${r.community || ''}${r.buildingNo || ''}号楼${r.unitNo || ''}单元${r.roomNo || ''}`
}

function profileById(id) {
  return allResidents.value.find((r) => r.id === id)
}

function profileLabel(r) {
  return `${resolveAddress(r)}　热表 ${r.meterNo || '-'}　${r.stationCode || '-'}　${r.supplyStatus || ''}${r.merged ? '（已归并只读）' : ''}`
}

function otherOf(row, p) {
  return row.profiles.find((x) => x.residentId !== p.residentId)
}

function sideId(side) {
  return side === 'keeper' ? keeperId.value : mergedId.value
}

function pairFromCandidate(row, keeperProfile, mergedProfile) {
  keeperId.value = keeperProfile.residentId
  mergedId.value = mergedProfile.residentId
  runPreview()
}

async function runPreview() {
  if (!keeperId.value || !mergedId.value) {
    ElMessage.warning('请先选择保留档案与待归并档案')
    return
  }
  if (keeperId.value === mergedId.value) {
    ElMessage.warning('保留档案与待归并档案不能是同一档案')
    return
  }
  previewLoading.value = true
  try {
    preview.value = await residentMergeApi.preview(keeperId.value, mergedId.value)
    chosenSupplyStatus.value = ''
    acknowledged.value = false
  } catch (e) {
    preview.value = null
    ElMessage.error(e.message)
  } finally {
    previewLoading.value = false
  }
}

function backToPick() {
  preview.value = null
  chosenSupplyStatus.value = ''
  acknowledged.value = false
  step.value = 0
}

async function doConfirm() {
  try {
    await ElMessageBox.confirm(
      `确认把 #${mergedId.value} 归并到保留档案 #${keeperId.value}？该操作将改挂全部抽测、报修、收费记录，且待归并档案转为只读。`,
      '归并确认',
      { type: 'warning', confirmButtonText: '确认归并', cancelButtonText: '再核对一下' }
    )
  } catch (e) {
    return
  }
  confirmLoading.value = true
  try {
    const audit = await residentMergeApi.confirm({
      keeperId: keeperId.value,
      mergedId: mergedId.value,
      chosenSupplyStatus: chosenSupplyStatus.value || null,
      operator: operator.value || null
    })
    ElMessage.success(`归并成功，归并单号 ${audit.mergeNo}：抽测 ${audit.sampleMovedCount} 条、报修 ${audit.orderMovedCount} 条、收费 ${audit.feeMovedCount} 条已改挂保留档案`)
    preview.value = null
    acknowledged.value = false
    chosenSupplyStatus.value = ''
    step.value = 0
    await reloadAll()
    activeTab.value = 'audits'
  } catch (e) {
    ElMessage.error(e.message || '归并失败，数据已回退到归并前状态')
  } finally {
    confirmLoading.value = false
  }
}

async function doResolve() {
  if (!resolveKeyword.value) {
    ElMessage.warning('请输入地址、热表号或旧档案编号')
    return
  }
  resolveLoading.value = true
  try {
    resolveResult.value = await residentMergeApi.resolve(resolveKeyword.value.trim())
  } catch (e) {
    resolveResult.value = null
    ElMessage.error(e.message)
  } finally {
    resolveLoading.value = false
  }
}

function jumpToKeeper(id) {
  resolveKeyword.value = String(id)
  doResolve()
}

async function resolveReview(group) {
  const receivable = confirmedMap.value[group.key]
  try {
    await ElMessageBox.confirm(
      `确认完成 ${group.feeYear} 年度财务复核？两笔实收 ${money(group.totalPaid)} 元据实合并到 #${group.keepId}，` +
      `另一条台账作废冻结，确认应收 ${money(receivable ?? group.keeperReceivable)} 元。`,
      '财务复核',
      { type: 'warning', confirmButtonText: '复核完成', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  reviewLoading.value[group.key] = true
  try {
    await feeApi.resolveReview({
      feeId: group.keepId,
      confirmedReceivable: receivable ?? null,
      reviewNote: ''
    })
    ElMessage.success('财务复核完成，重复台账已作废，该年度恢复缴费登记')
    await loadReviews()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    reviewLoading.value[group.key] = false
  }
}

function actionTag(action) {
  if (action === 'RECOMPUTE') return 'success'
  if (action === 'REVIEW') return 'danger'
  return 'primary'
}

function orderTag(status) {
  if (status === '已闭环') return 'success'
  if (status === '处理中') return 'warning'
  return 'info'
}

function reviewRowClass() {
  return 'review-row'
}

function formatDetails(json) {
  if (!json) return '-'
  try {
    const arr = JSON.parse(json)
    return arr.map((x) => `[${x.type}${x.feeYear ? ' ' + x.feeYear : ''}] ${x.text}`).join('\n')
  } catch (e) {
    return json
  }
}

async function loadCandidates() {
  try {
    candidates.value = await residentMergeApi.candidates()
  } catch (e) {
    candidates.value = []
  }
}

async function loadResidents() {
  try {
    allResidents.value = await monitorApi.residents(null, null, true)
    mergedProfiles.value = allResidents.value.filter((r) => r.merged)
  } catch (e) {
    allResidents.value = []
  }
}

async function loadReviews() {
  try {
    reviewRows.value = await feeApi.reviews()
  } catch (e) {
    reviewRows.value = []
  }
}

async function loadAudits() {
  try {
    audits.value = await residentMergeApi.audits()
  } catch (e) {
    audits.value = []
  }
}

async function reloadAll() {
  await Promise.all([loadCandidates(), loadResidents(), loadAudits()])
  if (activeTab.value === 'review') await loadReviews()
}

onMounted(async () => {
  await Promise.all([loadCandidates(), loadResidents(), loadReviews(), loadAudits()])
  if (props.jump) {
    activeTab.value = 'resolve'
    resolveKeyword.value = String(props.jump)
    await doResolve()
  }
})

watch(() => props.jump, async (v) => {
  if (v) {
    activeTab.value = 'resolve'
    resolveKeyword.value = String(v)
    await doResolve()
  }
})
</script>

<style scoped>
.merge-steps {
  margin: 6px 0 14px;
  padding: 0 40px;
}

.cand-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 3px 0;
  font-size: 12.5px;
}

.cand-meter {
  color: var(--hs-primary-deep);
  font-weight: 600;
}

.manual-pick {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-wrap: wrap;
}

.pick-item {
  display: flex;
  align-items: center;
  gap: 8px;
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
  padding: 8px 10px;
  text-align: left;
  vertical-align: middle;
}

.compare-table thead th {
  background: #f4f7fb;
  color: #375073;
}

.cmp-key {
  color: var(--hs-muted);
  background: #fafcff;
  font-weight: 600;
}

.ok-tag {
  margin-left: 8px;
}

.danger-title {
  color: var(--hs-danger);
}

.warn-title {
  color: var(--hs-hot);
}

.blocker-alert {
  margin-bottom: 8px;
}

.status-radio {
  margin-bottom: 10px;
}

.impact-box {
  border: 1px solid var(--hs-line);
  border-radius: 8px;
  padding: 10px 12px;
  background: #fbfdff;
}

.impact-line {
  font-size: 13px;
  line-height: 24px;
  color: #375073;
}

.impact-tag {
  display: inline-block;
  min-width: 64px;
  text-align: center;
  border-radius: 4px;
  padding: 0 6px;
  margin-right: 8px;
  font-size: 11.5px;
  color: #fff;
}

.impact-tag.temp {
  background: #2f6fd0;
}

.impact-tag.order {
  background: #e6a23c;
}

.impact-tag.fee {
  background: #c45656;
}

.action-note {
  margin-top: 4px;
  font-size: 12px;
  line-height: 18px;
  color: #5b6f88;
}

.recompute-box {
  margin-top: 4px;
  font-size: 12.5px;
  color: #2c8b4d;
}

.step-ops {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.confirm-line {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  font-size: 13px;
  flex-wrap: wrap;
}

.resolve-bar {
  display: flex;
  gap: 10px;
}

.resolve-alert {
  margin-bottom: 10px;
}

.resolve-desc {
  margin-top: 4px;
}

.review-ops {
  display: flex;
  gap: 6px;
  align-items: center;
}

.review-expand {
  padding: 8px 12px;
  background: #f8fafd;
}

.review-sub {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 4px 0;
  font-size: 12.5px;
}

:deep(.review-row) td.el-table__cell {
  background: #fff7f0;
}

.audit-json {
  margin: 0;
  padding: 10px 12px;
  background: #f6f8fb;
  border-radius: 6px;
  font-size: 12px;
  line-height: 19px;
  white-space: pre-wrap;
  word-break: break-all;
  color: #375073;
}

:deep(.fee-row-inline) {
  line-height: 18px;
}

:deep(.fee-row-line) {
  font-weight: 600;
  color: #2d4670;
}

:deep(.fee-row-sub) {
  font-size: 11.5px;
  color: var(--hs-muted);
}
</style>
