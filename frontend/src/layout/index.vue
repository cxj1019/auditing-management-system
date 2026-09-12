<script setup lang="ts">
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import AppMain from './components/AppMain.vue'
import { useAppStore } from '@/stores/app'
import { computed, onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'

const appStore = useAppStore()
const route = useRoute()

/** 固定模式：侧边栏常驻；自动隐藏模式：内容占满，悬停左边缘浮层展开 */
const pinned = computed(() => appStore.sidebarPinned)
const hovered = computed(() => appStore.sidebarHovered)
/** 手机/窄屏：侧边栏变抽屉，不参与文档流，也不靠悬停 */
const isMobile = computed(() => appStore.isMobile)

const asideVisible = computed(() =>
  isMobile.value ? appStore.sidebarMobileOpen : pinned.value || hovered.value,
)

/** 路由切换后自动收回浮层/抽屉 */
watch(
  () => route.path,
  () => {
    appStore.setSidebarHovered(false)
    appStore.sidebarMobileOpen = false
  },
)

onMounted(() => {
  window.addEventListener('resize', appStore.updateIsMobile)
  appStore.updateIsMobile()
})
onBeforeUnmount(() => {
  window.removeEventListener('resize', appStore.updateIsMobile)
})
</script>

<template>
  <el-container class="layout">
    <!-- 自动隐藏模式下的左边缘悬停热区（触屏无悬停，仅桌面） -->
    <div v-if="!isMobile && !pinned" class="sidebar-hover-zone" @mouseenter="appStore.setSidebarHovered(true)" />
    <!-- 移动端抽屉遮罩 -->
    <div v-if="isMobile && asideVisible" class="sidebar-mask" @click="appStore.sidebarMobileOpen = false" />
    <el-aside
      v-show="asideVisible"
      width="220px"
      class="layout-aside"
      :class="{ 'layout-aside-overlay': !pinned || isMobile }"
      @mouseleave="!isMobile && appStore.setSidebarHovered(false)"
    >
      <Sidebar />
    </el-aside>
    <el-container>
      <el-header class="layout-header">
        <Navbar />
      </el-header>
      <el-main class="layout-main">
        <AppMain />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  height: 100%;
}

.layout-aside {
  background-color: var(--app-sidebar-bg);
  transition: width 0.2s, transform 0.2s, box-shadow 0.2s;
  overflow-x: hidden;
}

/* 自动隐藏模式或移动端下临时展开：浮层覆盖在内容上 */
.layout-aside-overlay {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  z-index: 1001;
  box-shadow: 4px 0 16px rgba(0, 0, 0, 0.25);
}

.sidebar-hover-zone {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: 6px;
  z-index: 1000;
  background: transparent;
}

.sidebar-hover-zone:hover {
  background: rgba(37, 99, 235, 0.15);
}

/* 移动端抽屉遮罩 */
.sidebar-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.4);
}

.layout-header {
  height: 56px;
  padding: 0;
  background-color: var(--app-header-bg);
  border-bottom: 1px solid var(--app-header-border);
}

.layout-main {
  background-color: var(--app-page-bg);
  padding: 0;
}
</style>
