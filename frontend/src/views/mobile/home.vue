<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getDashboard } from '@/api/dashboard'
import { getUnreadCount, listNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notification'
import { useUserStore } from '@/stores/user'
import type { DashboardSummary, NotificationItem } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const canViewFinance = computed(() => userStore.hasRole('admin') || userStore.hasRole('manager'))

const summary = ref<DashboardSummary>()

const greeting = computed(() => {
  const hour = new Date().getHours()
  if (hour < 6) return '凌晨好'
  if (hour < 12) return '上午好'
  if (hour < 14) return '中午好'
  if (hour < 18) return '下午好'
  return '晚上好'
})

function money(v?: number): string {
  if (v === undefined || v === null) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 待办卡片：数值 + 点击跳转（报销走移动页，其余走桌面页） */
const todoCards = computed(() => {
  const todo = summary.value?.todo
  return [
    { label: '待审批报销', value: todo?.pendingReimbursement ?? 0, path: '/m/approval', color: '#f56c6c' },
    { label: '待开发票', value: todo?.pendingInvoice ?? 0, path: '/business/invoice', color: '#e6a23c' },
    { label: '逾期应收', value: todo?.overdueReceivable ?? 0, path: '/business/invoice', color: '#f56c6c' },
    { label: '逾期函证', value: todo?.overdueConfirmation ?? 0, path: '/business/confirmation', color: '#e6a23c' },
    { label: '合同将到期', value: todo?.expiringContract ?? 0, path: '/business/contract', color: '#8b5cf6' },
  ]
})

const quickActions = computed(() => {
  const base = [
    { label: '我要报销', icon: 'Camera', path: '/m/reimburse', color: '#2563eb' },
    { label: '我的报销单', icon: 'Tickets', path: '/m/bills', color: '#16a34a' },
    { label: '项目管理', icon: 'Notebook', path: '/m/projects', color: '#8b5cf6' },
    { label: '客户管理', icon: 'User', path: '/m/clients', color: '#0ea5e9' },
    { label: '合同管理', icon: 'Document', path: '/m/contracts', color: '#f59e0b' },
    { label: '发票管理', icon: 'Postcard', path: '/m/invoices', color: '#ef4444' },
    { label: '收款管理', icon: 'Money', path: '/m/collections', color: '#10b981' },
    { label: '函证管理', icon: 'Memo', path: '/m/confirmations', color: '#6366f1' },
    { label: '汇率牌价', icon: 'Coin', path: '/m/fx', color: '#0d9488' },
    { label: '日程周板', icon: 'Calendar', path: '/m/schedule', color: '#ec4899' },
  ]
  if (canViewFinance.value) {
    base.push({ label: '成本分析', icon: 'DataAnalysis', path: '/business/cost', color: '#f97316' })
  }
  return base
})

// ---------- 站内通知 ----------
const notifVisible = ref(false)
const notifLoading = ref(false)
const notifList = ref<NotificationItem[]>([])
const unreadCount = ref(0)

const typeLabels: Record<string, string> = {
  receivable: '逾期应收',
  confirmation: '函证逾期',
  reimbursement: '报销滞留',
  contract: '合同到期',
  'payment-plan': '收款计划',
}

async function openNotif(): Promise<void> {
  notifVisible.value = true
  notifLoading.value = true
  try {
    const data = await listNotifications(20)
    notifList.value = data.list
    unreadCount.value = data.unread
  } finally {
    notifLoading.value = false
  }
}

async function handleClickNotif(item: NotificationItem): Promise<void> {
  if (!item.isRead) {
    await markNotificationRead(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  if (item.relatedPath) {
    notifVisible.value = false
    router.push(item.relatedPath)
  }
}

async function handleReadAll(): Promise<void> {
  await markAllNotificationsRead()
  notifList.value = notifList.value.map((n) => ({ ...n, isRead: 1 }))
  unreadCount.value = 0
  ElMessage.success('已全部标记为已读')
}

onMounted(async () => {
  summary.value = await getDashboard()
  try {
    unreadCount.value = await getUnreadCount()
  } catch { /* 忽略 */ }
})
</script>

<template>
  <div class="mh-page">
    <div class="mh-header">
      <div>
        <div class="mh-greeting">{{ greeting }}，{{ userStore.nickname }}</div>
        <div class="mh-sub">本周工时 <b style="color:#2563eb">{{ Number(summary?.weekHours ?? 0).toFixed(1) }}</b> 小时</div>
      </div>
      <el-badge :value="unreadCount" :hidden="unreadCount <= 0" :max="99">
        <el-icon :size="22" class="mh-bell" @click="openNotif"><Bell /></el-icon>
      </el-badge>
    </div>

    <!-- 待办 -->
    <div class="mh-section-title">待办提醒</div>
    <div class="mh-todo-grid">
      <div v-for="card in todoCards" :key="card.label" class="mh-todo-card" @click="router.push(card.path)">
        <div class="mh-todo-value" :style="{ color: card.value > 0 ? card.color : '#9ca3af' }">{{ card.value }}</div>
        <div class="mh-todo-label">{{ card.label }}</div>
      </div>
    </div>

    <!-- 经营数据（管理员/经理） -->
    <template v-if="canViewFinance">
      <div class="mh-section-title">开票与回款</div>
      <div class="mh-finance-card">
        <div class="mh-finance-cell">
          <div class="mh-finance-value">{{ money(summary?.receivable?.invoicedAmount) }}</div>
          <div class="mh-finance-label">已开票（元）</div>
        </div>
        <div class="mh-finance-cell">
          <div class="mh-finance-value" style="color: #16a34a">{{ money(summary?.receivable?.collectedAmount) }}</div>
          <div class="mh-finance-label">已回款（元）</div>
        </div>
        <div class="mh-finance-cell">
          <div class="mh-finance-value" style="color: #f56c6c">{{ money(summary?.receivable?.outstanding) }}</div>
          <div class="mh-finance-label">未核销（元）</div>
        </div>
      </div>
    </template>

    <!-- 快捷入口 -->
    <div class="mh-section-title">常用功能</div>
    <div class="mh-action-grid">
      <div v-for="action in quickActions" :key="action.label" class="mh-action" @click="router.push(action.path)">
        <el-icon :size="22" :style="{ color: action.color }"><component :is="action.icon" /></el-icon>
        <span>{{ action.label }}</span>
      </div>
    </div>

    <!-- 通知弹层 -->
    <el-dialog v-model="notifVisible" title="通知" :width="320">
      <div v-loading="notifLoading" class="mh-notif-list">
        <div class="mh-notif-head" v-if="unreadCount > 0">
          <el-button link type="primary" size="small" @click="handleReadAll">全部已读</el-button>
        </div>
        <div
          v-for="n in notifList"
          :key="n.id"
          class="mh-notif-item"
          :class="{ unread: !n.isRead }"
          @click="handleClickNotif(n)"
        >
          <div class="mh-notif-title">
            <el-tag size="small" :type="n.isRead ? 'info' : 'danger'">{{ typeLabels[n.type] || n.type }}</el-tag>
            {{ n.title }}
          </div>
          <div class="mh-notif-content">{{ n.content }}</div>
          <div class="mh-notif-time">{{ (n.createTime || '').replace('T', ' ').slice(0, 16) }}</div>
        </div>
        <div v-if="!notifLoading && !notifList.length" class="mh-empty">暂无通知</div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.mh-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mh-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.mh-greeting { font-size: 18px; font-weight: 600; }
.mh-sub { font-size: 12px; color: #9ca3af; margin-top: 3px; }
.mh-bell { color: #4b5563; cursor: pointer; }
.mh-section-title { font-size: 14px; font-weight: 600; margin: 14px 0 8px; }
.mh-todo-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.mh-todo-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; text-align: center; padding: 12px 4px; cursor: pointer; }
.mh-todo-value { font-size: 22px; font-weight: 600; color: #2563eb; }
.mh-todo-label { font-size: 12px; color: #6b7280; margin-top: 3px; }
.mh-finance-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; display: flex; padding: 12px 0; }
.mh-finance-cell { flex: 1; text-align: center; }
.mh-finance-value { font-size: 15px; font-weight: 600; }
.mh-finance-label { font-size: 11px; color: #9ca3af; margin-top: 3px; }
.mh-action-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.mh-action { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 14px 4px; font-size: 12px; color: #374151; cursor: pointer; }
.mh-notif-head { text-align: right; margin-bottom: 4px; }
.mh-notif-item { padding: 8px 4px; border-bottom: 1px solid #f3f4f6; cursor: pointer; }
.mh-notif-item.unread .mh-notif-title { font-weight: 600; }
.mh-notif-title { font-size: 13px; display: flex; align-items: center; gap: 6px; color: #1f2937; }
.mh-notif-content { font-size: 12px; color: #6b7280; margin: 4px 0; }
.mh-notif-time { font-size: 11px; color: #9ca3af; }
.mh-empty { text-align: center; color: #9ca3af; padding: 24px 0; font-size: 13px; }
</style>
