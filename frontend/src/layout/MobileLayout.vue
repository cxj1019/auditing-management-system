<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { pageReimbursements } from '@/api/reimbursement'
import { useUserStore } from '@/stores/user'

/**
 * 手机端独立布局：无侧边栏，底部标签栏导航。
 * 子页面各自维护自己的顶部标题栏；本布局只负责底部 Tab。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const canApprove = computed(() => userStore.hasPermission('business:reimbursement:approve'))
const canCreate = computed(() => userStore.hasPermission('business:reimbursement:add'))

/** 审批 Tab 角标：待审批 + 待终审 总数 */
const pendingCount = ref(0)

onMounted(async () => {
  if (!canApprove.value) return
  try {
    const [first, final] = await Promise.all([
      pageReimbursements({ current: 1, size: 1, status: 1 }),
      pageReimbursements({ current: 1, size: 1, status: 4 }),
    ])
    pendingCount.value = first.total + final.total
  } catch { /* 角标失败忽略 */ }
})

const tabs = computed(() => [
  { path: '/m/home', label: '首页', icon: 'HomeFilled', show: true, badge: 0 },
  { path: '/m/bills', label: '报销单', icon: 'Tickets', show: true, badge: 0 },
  { path: '/m/reimburse', label: '拍照报销', icon: 'Camera', show: canCreate.value, badge: 0 },
  { path: '/m/approval', label: '审批', icon: 'Bell', show: canApprove.value, badge: pendingCount.value },
  { path: '/m/more', label: '更多', icon: 'Grid', show: true, badge: 0 },
].filter((t) => t.show))
</script>

<template>
  <div class="mob-shell">
    <div class="mob-body">
      <router-view />
    </div>

    <div class="mob-tabbar">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        class="mob-tab"
        :class="{ active: route.path.startsWith(tab.path) }"
        @click="router.push(tab.path)"
      >
        <el-badge :value="tab.badge" :hidden="!tab.badge" :max="99">
          <el-icon :size="20"><component :is="tab.icon" /></el-icon>
        </el-badge>
        <span class="mob-tab-label">{{ tab.label }}</span>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mob-shell {
  min-height: 100vh;
  background: #f5f6f8;
}

.mob-body {
  padding-bottom: 64px;
}

.mob-tabbar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1500;
  display: flex;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  padding-bottom: env(safe-area-inset-bottom);
}

.mob-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  padding: 7px 0 5px;
  color: #9ca3af;
  font-size: 11px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}

.mob-tab.active {
  color: #2563eb;
}

.mob-tab-label {
  line-height: 1.2;
}
</style>
