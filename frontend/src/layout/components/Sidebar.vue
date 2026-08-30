<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAppStore } from '@/stores/app'
import type { MenuItem } from '@/types'

const route = useRoute()
const userStore = useUserStore()
const appStore = useAppStore()

/** 当前激活菜单 */
const activeMenu = computed(() => route.path)

/** 菜单树：目录(0)/菜单(1)，按钮(2)不渲染 */
const menuTree = computed<MenuItem[]>(() => userStore.menus)

/** 菜单项是否可见 */
function isVisible(menu: MenuItem): boolean {
  return menu.visible === 1
}
</script>

<template>
  <div class="sidebar">
    <!-- 系统标识 -->
    <div class="sidebar-logo">
      <span class="logo-title">会计师事务所管理系统</span>
    </div>

    <!-- 动态菜单：根据用户权限返回的菜单树渲染 -->
    <el-menu
      :default-active="activeMenu"
      :collapse="false"
      :collapse-transition="false"
      router
      class="sidebar-menu"
    >
      <template v-for="menu in menuTree" :key="menu.id">
        <!-- 目录：含子菜单 -->
        <el-sub-menu v-if="menu.type === 0 && isVisible(menu)" :index="menu.path || String(menu.id)">
          <template #title>
            <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
            <span>{{ menu.name }}</span>
          </template>
          <el-menu-item
            v-for="child in menu.children.filter(isVisible)"
            :key="child.id"
            :index="child.path || String(child.id)"
          >
            <el-icon v-if="child.icon"><component :is="child.icon" /></el-icon>
            <template #title>{{ child.name }}</template>
          </el-menu-item>
        </el-sub-menu>

        <!-- 一级菜单 -->
        <el-menu-item v-else-if="menu.type === 1 && isVisible(menu)" :index="menu.path || String(menu.id)">
          <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
          <template #title>{{ menu.name }}</template>
        </el-menu-item>
      </template>
    </el-menu>

    <!-- 固定/自动隐藏切换 -->
    <div class="sidebar-footer" @click="appStore.toggleSidebarPinned()">
      <el-icon v-if="appStore.sidebarPinned"><Fold /></el-icon>
      <el-icon v-else><Expand /></el-icon>
      <span class="sidebar-footer-text">{{ appStore.sidebarPinned ? '自动隐藏' : '固定侧栏' }}</span>
    </div>
  </div>
</template>

<style scoped>
.sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.sidebar-logo {
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  border-bottom: 1px solid var(--app-sidebar-border);
}

.logo-title {
  font-size: 14px;
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  flex: 1;
  overflow-y: auto;
  --el-menu-bg-color: var(--app-sidebar-bg);
  --el-menu-text-color: var(--app-sidebar-text);
  --el-menu-hover-bg-color: var(--app-sidebar-hover-bg);
  --el-menu-active-color: #ffffff;
}

.sidebar-menu :deep(.el-menu-item.is-active) {
  background-color: var(--app-sidebar-active-bg);
  color: #ffffff;
}

.sidebar-menu :deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #ffffff;
}

.sidebar-footer {
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: var(--app-sidebar-text);
  font-size: 13px;
  cursor: pointer;
  border-top: 1px solid var(--app-sidebar-border);
}

.sidebar-footer:hover {
  color: #fff;
  background: rgba(255, 255, 255, 0.06);
}

.sidebar-footer-text {
  white-space: nowrap;
}
</style>
