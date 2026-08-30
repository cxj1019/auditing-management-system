<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { applyTheme, THEMES } from '@/styles/theme'

const visible = ref(false)
const current = ref(localStorage.getItem('app_theme') || 'default')
const rootRef = ref<HTMLDivElement>()

function pick(key: string): void {
  current.value = key
  applyTheme(key)
}

function toggle(): void {
  visible.value = !visible.value
}

function onDocMouseDown(e: MouseEvent): void {
  if (visible.value && rootRef.value && !rootRef.value.contains(e.target as Node)) {
    visible.value = false
  }
}

onMounted(() => document.addEventListener('mousedown', onDocMouseDown))
onBeforeUnmount(() => document.removeEventListener('mousedown', onDocMouseDown))
</script>

<template>
  <div ref="rootRef" class="theme-root">
    <!-- 风格面板 -->
    <div v-if="visible" class="theme-panel">
      <div class="theme-title">界面风格</div>
      <div
        v-for="t in THEMES"
        :key="t.key"
        class="theme-row"
        :class="{ active: current === t.key }"
        @click="pick(t.key)"
      >
        <div class="swatches">
          <span class="swatch" :style="{ background: t.primary }" />
          <span class="swatch" :style="{ background: t.sidebar }" />
          <span class="swatch swatch-page" :style="{ background: t.pageBg }" />
        </div>
        <span class="theme-name">{{ t.name }}</span>
        <el-icon v-if="current === t.key" class="check"><Check /></el-icon>
      </div>
      <div class="theme-tip">选择后立即生效并自动记住</div>
    </div>
    <!-- 悬浮按钮 -->
    <el-tooltip content="界面风格" placement="left">
      <div class="theme-fab" @click.stop="toggle">
        <el-icon :size="18"><Brush /></el-icon>
      </div>
    </el-tooltip>
  </div>
</template>

<style scoped>
.theme-root {
  position: fixed;
  right: 20px;
  bottom: 24px;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
}

.theme-fab {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  background: var(--el-bg-color-overlay);
  color: var(--el-color-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px rgba(0, 0, 0, 0.18);
  cursor: pointer;
  transition: transform 0.15s;
}

.theme-fab:hover {
  transform: scale(1.08);
}

.theme-panel {
  width: 216px;
  padding: 10px 8px;
  border-radius: 8px;
  background: var(--el-bg-color-overlay);
  border: 1px solid var(--el-border-color-light);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.15);
}

.theme-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  padding-bottom: 8px;
}

.theme-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 7px 6px;
  border-radius: 6px;
  cursor: pointer;
}

.theme-row:hover {
  background: var(--el-fill-color-light);
}

.theme-row.active {
  background: var(--el-color-primary-light-9);
}

.swatches {
  display: flex;
  gap: 3px;
}

.swatch {
  width: 14px;
  height: 14px;
  border-radius: 4px;
  border: 1px solid rgba(0, 0, 0, 0.08);
}

.swatch-page {
  border: 1px solid rgba(0, 0, 0, 0.15);
}

.theme-name {
  flex: 1;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.check {
  color: var(--el-color-primary);
}

.theme-tip {
  font-size: 11px;
  color: var(--el-text-color-placeholder);
  padding-top: 8px;
  border-top: 1px solid var(--el-border-color-lighter);
}
</style>
