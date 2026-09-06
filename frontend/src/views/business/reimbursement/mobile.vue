<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageReimbursements, approveReimbursement } from '@/api/reimbursement'
import { getReimbItems } from '@/api/reimbursement'
import { useUserStore } from '@/stores/user'
import type { ReimbursementItem } from '@/types'

const userStore = useUserStore()
const loading = ref(false)
const bills = ref<ReimbursementItem[]>([])
const expandedId = ref<number | null>(null)
const detailCache = ref<Record<number, { category: string; amount: number; description?: string }[]>>({})
const acting = ref(false)

const canApprove = computed(() => userStore.hasPermission('business:reimbursement:approve'))
const canFinalReview = computed(() => userStore.hasRole('partner') || userStore.isAdmin)

const statusLabels: Record<number, string> = { 1: '待审批', 4: '待终审' }
const statusTypes: Record<number, 'warning' | 'danger'> = { 1: 'warning', 4: 'danger' }

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const [pending, finalReview] = await Promise.all([
      pageReimbursements({ current: 1, size: 50, status: 1 }),
      pageReimbursements({ current: 1, size: 50, status: 4 }),
    ])
    bills.value = [...pending.records, ...finalReview.records]
  } finally { loading.value = false }
}

async function toggleDetail(bill: ReimbursementItem): Promise<void> {
  if (expandedId.value === bill.id) {
    expandedId.value = null
    return
  }
  expandedId.value = bill.id
  if (!detailCache.value[bill.id]) {
    const items = await getReimbItems(bill.id)
    detailCache.value[bill.id] = items.map((i) => ({
      category: i.category, amount: Number(i.amount), description: i.description,
    }))
  }
}

async function handleApprove(bill: ReimbursementItem, action: 'approve' | 'reject'): Promise<void> {
  const isFinal = bill.status === 4
  const actionName = action === 'approve' ? '批准' : '驳回'
  let comment = ''
  try {
    const input = await ElMessageBox.prompt(
      `${isFinal ? '终审' : ''}${actionName}报销单「${bill.reimbursementNo}」（${Number(bill.totalAmount).toFixed(2)} 元），可填写审批意见`,
      `${isFinal ? '终审' : ''}${actionName}确认`,
      { confirmButtonText: '确定', cancelButtonText: '取消', inputPlaceholder: '审批意见（可空）' },
    )
    comment = input.value || ''
  } catch { return }
  acting.value = true
  try {
    await approveReimbursement(bill.id, { action, comment })
    ElMessage.success(`已${actionName}`)
    await fetchList()
  } finally { acting.value = false }
}

onMounted(fetchList)
</script>

<template>
  <div class="m-page">
    <div class="m-header">
      <span class="m-title">报销审批</span>
      <el-button size="small" text @click="fetchList">刷新</el-button>
    </div>

    <div v-if="!canApprove" class="m-empty">您没有报销审批权限</div>

    <template v-else>
      <div v-if="!loading && !bills.length" class="m-empty">暂无待审批的报销单</div>

      <div v-for="bill in bills" :key="bill.id" class="m-card">
        <div class="m-card-head" @click="toggleDetail(bill)">
          <div>
            <el-tag :type="statusTypes[bill.status]" size="small">{{ statusLabels[bill.status] }}</el-tag>
            <span class="m-no">{{ bill.reimbursementNo }}</span>
          </div>
          <div class="m-amount">{{ Number(bill.totalAmount).toFixed(2) }} 元</div>
        </div>
        <div class="m-card-body" @click="toggleDetail(bill)">
          <div>{{ bill.title || '（无标题）' }}</div>
          <div class="m-sub">申请人：{{ bill.applicantName || bill.applicantUsername }}</div>
        </div>

        <div v-if="expandedId === bill.id" class="m-detail">
          <div v-for="(item, idx) in detailCache[bill.id] || []" :key="idx" class="m-item">
            <span>{{ item.category }}</span>
            <span class="m-item-desc">{{ item.description || '' }}</span>
            <span class="m-item-amount">{{ item.amount.toFixed(2) }}</span>
          </div>
          <div v-if="!detailCache[bill.id]?.length" class="m-empty">加载中…</div>
        </div>

        <div class="m-actions">
          <el-button type="danger" plain :disabled="acting" @click="handleApprove(bill, 'reject')">驳回</el-button>
          <el-button type="primary" :disabled="acting" @click="handleApprove(bill, 'approve')">
            {{ bill.status === 4 ? '终审批准' : '批准' }}
          </el-button>
        </div>
        <div v-if="bill.status === 4 && !canFinalReview" class="m-final-tip">待合伙人或管理员终审</div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.m-page { max-width: 640px; margin: 0 auto; padding: 12px; }
.m-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.m-title { font-size: 18px; font-weight: 600; }
.m-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 12px; }
.m-card-head { display: flex; justify-content: space-between; align-items: center; }
.m-no { margin-left: 8px; font-weight: 600; }
.m-amount { color: #f56c6c; font-weight: 600; }
.m-card-body { margin-top: 8px; color: #374151; }
.m-sub { color: #9ca3af; font-size: 13px; margin-top: 4px; }
.m-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.m-item { display: flex; justify-content: space-between; gap: 8px; font-size: 13px; padding: 3px 0; }
.m-item-desc { flex: 1; color: #6b7280; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-actions { display: flex; gap: 8px; margin-top: 10px; }
.m-actions .el-button { flex: 1; }
.m-empty { text-align: center; color: #9ca3af; padding: 24px 0; }
.m-final-tip { color: #e6a23c; font-size: 12px; margin-top: 8px; }
</style>
