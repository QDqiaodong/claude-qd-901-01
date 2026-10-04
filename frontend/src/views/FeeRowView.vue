<template>
  <div class="fee-row-inline">
    <div class="fee-row-line">
      应收 {{ money(row.receivable) }}　实收
      <b :class="{ paid: Number(row.paid) > 0 }">{{ money(row.paid) }}</b>　欠费 {{ money(row.arrears) }}
    </div>
    <div class="fee-row-sub">
      计费面积 {{ row.billingArea }} ㎡ · 单价 {{ row.unitPrice }} ·
      <el-tag size="small" :type="row.payStatus === '已缴' ? 'success' : row.payStatus === '部分' ? 'warning' : 'danger'">
        {{ row.payStatus }}
      </el-tag>
      · 最近缴费 {{ row.lastPayDate || '-' }}
      <el-tag v-if="row.reviewFlag" size="small" type="danger" effect="dark">待财务复核</el-tag>
    </div>
  </div>
</template>

<script setup>
defineProps({
  row: { type: Object, required: true }
})

function money(value) {
  if (value === null || value === undefined) return '-'
  return Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}
</script>

<style scoped>
.fee-row-inline {
  line-height: 19px;
}

.fee-row-line {
  font-weight: 600;
  color: #2d4670;
}

.fee-row-line .paid {
  color: #2c8b4d;
}

.fee-row-sub {
  font-size: 11.5px;
  color: var(--hs-muted);
  display: flex;
  gap: 6px;
  align-items: center;
  flex-wrap: wrap;
}
</style>
