<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import { changePassword, logout } from '@/api/auth'
import { getUnreadCount, listNotifications, markAllNotificationsRead, markNotificationRead } from '@/api/notification'
import type { NotificationItem } from '@/types'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const appStore = useAppStore()

/** 侧边栏固定 ⇄ 自动隐藏 */
function toggleSidebarPinned(): void {
  appStore.toggleSidebarPinned()
  ElMessage.success(appStore.sidebarPinned ? '侧边栏已固定' : '侧边栏已自动隐藏（鼠标移到左边缘可展开）')
}

/** 面包屑标题 */
const pageTitle = computed(() => (route.meta.title as string) || '')

/** 登出 */
async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userStore.logout()
    router.push('/login')
    ElMessage.success('已退出登录')
  } catch {
    // 用户取消
  }
}

// ---------- 站内通知 ----------
const notifLoading = ref(false)
const notifList = ref<NotificationItem[]>([])
const unreadCount = ref(0)
let pollTimer: number | undefined

const typeLabels: Record<string, string> = {
  receivable: '逾期应收',
  confirmation: '函证逾期',
  reimbursement: '报销滞留',
  contract: '合同到期',
}

async function refreshUnread(): Promise<void> {
  try {
    unreadCount.value = await getUnreadCount()
  } catch {
    // 忽略轮询失败
  }
}

async function toggleNotif(visible: boolean): Promise<void> {
  if (visible) {
    notifLoading.value = true
    try {
      const data = await listNotifications(20)
      notifList.value = data.list
      unreadCount.value = data.unread
    } finally {
      notifLoading.value = false
    }
  }
}

async function handleClickNotif(item: NotificationItem): Promise<void> {
  if (!item.isRead) {
    await markNotificationRead(item.id)
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  if (item.relatedPath) {
    router.push(item.relatedPath)
  }
}

async function handleReadAll(): Promise<void> {
  await markAllNotificationsRead()
  notifList.value = notifList.value.map((n) => ({ ...n, isRead: 1 }))
  unreadCount.value = 0
  ElMessage.success('已全部标记为已读')
}

onMounted(() => {
  refreshUnread()
  pollTimer = window.setInterval(refreshUnread, 60_000)
})
onBeforeUnmount(() => {
  if (pollTimer) window.clearInterval(pollTimer)
})

// ---------- 修改密码 ----------
const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

function openPasswordDialog(): void {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
}

async function handleChangePassword(): Promise<void> {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码长度至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdSaving.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    pwdVisible.value = false
    ElMessage.success('密码修改成功，请重新登录')
    await userStore.logout()
    router.push('/login')
  } finally {
    pwdSaving.value = false
  }
}
</script>

<template>
  <div class="navbar">
    <div class="navbar-left">
      <el-icon
        v-if="appStore.sidebarPinned"
        class="collapse-btn"
        title="自动隐藏侧边栏"
        @click="toggleSidebarPinned"
      >
        <Fold />
      </el-icon>
      <el-icon v-else class="collapse-btn" title="固定侧边栏" @click="toggleSidebarPinned">
        <Expand />
      </el-icon>
      <span class="page-title">{{ pageTitle }}</span>
    </div>

    <div class="navbar-right">
      <!-- 站内通知 -->
      <el-popover placement="bottom-end" :width="380" trigger="click" @show="toggleNotif(true)">
        <template #reference>
          <el-badge :value="unreadCount" :hidden="unreadCount <= 0" :max="99" class="notif-badge">
            <el-icon class="notif-bell"><Bell /></el-icon>
          </el-badge>
        </template>
        <div v-loading="notifLoading" class="notif-panel">
          <div class="notif-header">
            <span>通知</span>
            <el-button v-if="unreadCount > 0" link type="primary" size="small" @click="handleReadAll">全部已读</el-button>
          </div>
          <div class="notif-list">
            <div v-for="n in notifList" :key="n.id" class="notif-item" :class="{ unread: !n.isRead }"
              @click="handleClickNotif(n)">
              <div class="notif-title">
                <el-tag size="small" :type="n.isRead ? 'info' : 'danger'" class="notif-type">
                  {{ typeLabels[n.type] || n.type }}
                </el-tag>
                {{ n.title }}
              </div>
              <div class="notif-content">{{ n.content }}</div>
              <div class="notif-time">{{ (n.createTime || '').replace('T', ' ').slice(0, 16) }}</div>
            </div>
            <div v-if="!notifList.length" class="notif-empty">暂无通知</div>
          </div>
        </div>
      </el-popover>

      <el-dropdown>
        <span class="user-info">
          <el-avatar :size="28" class="user-avatar">{{ userStore.nickname.charAt(0) }}</el-avatar>
          <span class="user-name">{{ userStore.nickname }}</span>
          <el-icon><ArrowDown /></el-icon>
        </span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="openPasswordDialog">修改密码</el-dropdown-item>
            <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px">
      <el-form :model="pwdForm" label-width="90px">
        <el-form-item label="原密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="原密码" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.navbar {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  background-color: var(--app-header-bg);
  border-bottom: 1px solid var(--app-header-border);
}

.navbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  font-size: 20px;
  cursor: pointer;
  color: var(--el-text-color-secondary);
}

.page-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.navbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.notif-bell {
  font-size: 18px;
  color: #4b5563;
  cursor: pointer;
}

.notif-badge {
  display: flex;
}

.notif-panel {
  max-height: 420px;
  display: flex;
  flex-direction: column;
}

.notif-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 500;
  padding-bottom: 8px;
  border-bottom: 1px solid #f3f4f6;
}

.notif-list {
  overflow-y: auto;
}

.notif-item {
  padding: 8px 4px;
  border-bottom: 1px solid #f9fafb;
  cursor: pointer;
  border-radius: 4px;
}

.notif-item:hover {
  background: #f8fafc;
}

.notif-item.unread .notif-title {
  font-weight: 600;
}

.notif-title {
  font-size: 13px;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 6px;
}

.notif-type {
  flex-shrink: 0;
}

.notif-content {
  font-size: 12px;
  color: #6b7280;
  margin: 4px 0;
}

.notif-time {
  font-size: 11px;
  color: #9ca3af;
}

.notif-empty {
  text-align: center;
  color: #9ca3af;
  font-size: 13px;
  padding: 24px 0;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  color: var(--el-text-color-regular);
}

.user-avatar {
  background-color: #2563eb;
  color: #fff;
  font-size: 13px;
}

.user-name {
  font-size: 14px;
}
</style>
