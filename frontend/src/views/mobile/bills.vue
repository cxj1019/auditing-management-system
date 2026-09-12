<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageReimbursements, getReimbItems, submitReimbursement,
  deleteReimbursement, withdrawReimbursement, listReimbAttachments,
} from '@/api/reimbursement'
import { useUserStore } from '@/stores/user'
import type { ReimbursementAttachmentItem, ReimbursementItem } from '@/types'

/** 手机端"报销单"列表：卡片式展示 + 展开明细/附件 + 草稿提交/删除、待审批撤回 */
const router = useRouter()
const userStore = useUserStore()

const statusLabels: Record<number, string> = { 0: '草稿', 1: '待审批', 2: '已批准', 3: '已驳回', 4: '待终审' }
const statusTypes: Record<number, 'info' | 'warning' | 'success' | 'danger' | 'primary'> = {
  0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'primary',
}

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '草稿', value: 0 },
  { label: '待审批', value: 1 },
  { label: '已批准', value: 2 },
  { label: '已驳回', value: 3 },
  { label: '待终审', value: 4 },
]

const loading = ref(false)
const bills = ref<ReimbursementItem[]>([])
const activeStatus = ref<number | undefined>(undefined)
const expandedId = ref<number | null>(null)
const acting = ref(false)

const detailCache = ref<Record<number, { items: { category: string; amount: number; description?: string }[]; attCount: number }>>({})

const canCreate = computed(() => userStore.hasPermission('business:reimbursement:add'))

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageReimbursements({ current: 1, size: 50, status: activeStatus.value })
    bills.value = data.records
  } finally {
    loading.value = false
  }
}

function switchStatus(status: number | undefined): void {
  activeStatus.value = status
  expandedId.value = null
  fetchList()
}

async function toggleDetail(bill: ReimbursementItem): Promise<void> {
  if (expandedId.value === bill.id) {
    expandedId.value = null
    return
  }
  expandedId.value = bill.id
  if (!detailCache.value[bill.id]) {
    const [items, atts] = await Promise.all([
      getReimbItems(bill.id),
      listReimbAttachments(bill.id).catch(() => [] as ReimbursementAttachmentItem[]),
    ])
    detailCache.value[bill.id] = {
      items: items.map((i) => ({ category: i.category, amount: Number(i.amount), description: i.description })),
      attCount: atts.length,
    }
  }
}

async function handleSubmit(bill: ReimbursementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`提交报销单「${bill.reimbursementNo}」进入审批？`, '提交确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await submitReimbursement(bill.id)
    ElMessage.success('已提交')
    await fetchList()
  } finally { acting.value = false }
}

async function handleWithdraw(bill: ReimbursementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`撤回报销单「${bill.reimbursementNo}」回到草稿？`, '撤回确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await withdrawReimbursement(bill.id)
    ElMessage.success('已撤回')
    await fetchList()
  } finally { acting.value = false }
}

async function handleDelete(bill: ReimbursementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除草稿「${bill.reimbursementNo}」？删除后不可恢复。`, '删除确认', { type: 'warning' })
  } catch { return }
  acting.value = true
  try {
    await deleteReimbursement(bill.id)
    ElMessage.success('已删除')
    await fetchList()
  } finally { acting.value = false }
}

function openEdit(bill: ReimbursementItem): void {
  // 手机内直接编辑：带单据信息进拍照报销页（草稿/已驳回均可改后重新提交）
  router.push({
    path: '/m/reimburse',
    query: { id: String(bill.id), title: bill.title || '', projectId: bill.projectId != null ? String(bill.projectId) : '', from: 'bills' },
  })
}

onMounted(fetchList)
</script>

<template>
  <div class="mb-page">
    <div class="mb-header">
      <span class="mb-title">报销单</span>
      <el-button v-if="canCreate" size="small" type="primary" @click="router.push('/m/reimburse')">＋ 新建</el-button>
    </div>

    <!-- 状态筛选 -->
    <div class="mb-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mb-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !bills.length" class="mb-empty">暂无报销单</div>

    <div v-for="bill in bills" :key="bill.id" class="mb-card">
      <div class="mb-card-head" @click="toggleDetail(bill)">
        <div class="mb-card-title">
          <el-tag :type="statusTypes[bill.status]" size="small">{{ statusLabels[bill.status] }}</el-tag>
          <span class="mb-no">{{ bill.reimbursementNo }}</span>
        </div>
        <div class="mb-amount">{{ Number(bill.totalAmount).toFixed(2) }} 元</div>
      </div>
      <div class="mb-card-sub" @click="toggleDetail(bill)">
        {{ bill.title || '（无标题）' }} · {{ bill.applicantName || bill.applicantUsername }}
      </div>

      <div v-if="expandedId === bill.id" class="mb-detail">
        <div v-for="(item, idx) in detailCache[bill.id]?.items || []" :key="idx" class="mb-item">
          <span>{{ item.category }}</span>
          <span class="mb-item-desc">{{ item.description || '' }}</span>
          <span class="mb-item-amount">{{ item.amount.toFixed(2) }}</span>
        </div>
        <div class="mb-att-line">发票附件：{{ detailCache[bill.id]?.attCount ?? 0 }} 个</div>

        <!-- 草稿：提交 / 删除 / 桌面编辑；待审批：撤回；已驳回：去桌面改后重新提交 -->
        <div class="mb-actions">
          <template v-if="bill.status === 0">
            <el-button size="small" :disabled="acting" @click="openEdit(bill)">编辑</el-button>
            <el-button size="small" type="danger" plain :disabled="acting" @click="handleDelete(bill)">删除</el-button>
            <el-button size="small" type="primary" :disabled="acting" @click="handleSubmit(bill)">提交</el-button>
          </template>
          <template v-else-if="bill.status === 1">
            <el-button size="small" type="warning" plain :disabled="acting" @click="handleWithdraw(bill)">撤回</el-button>
          </template>
          <template v-else-if="bill.status === 3">
            <el-button size="small" type="primary" plain :disabled="acting" @click="openEdit(bill)">修改后重新提交</el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mb-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mb-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mb-title { font-size: 18px; font-weight: 600; }
.mb-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mb-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mb-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mb-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mb-card-head { display: flex; justify-content: space-between; align-items: center; }
.mb-card-title { display: flex; align-items: center; gap: 8px; }
.mb-no { font-weight: 600; font-size: 13px; }
.mb-amount { color: #f56c6c; font-weight: 600; }
.mb-card-sub { color: #6b7280; font-size: 13px; margin-top: 6px; }
.mb-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mb-item { display: flex; justify-content: space-between; gap: 8px; font-size: 13px; padding: 3px 0; }
.mb-item-desc { flex: 1; color: #6b7280; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mb-item-amount { color: #374151; }
.mb-att-line { font-size: 12px; color: #9ca3af; margin-top: 6px; }
.mb-actions { display: flex; gap: 8px; margin-top: 10px; justify-content: flex-end; }
.mb-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
