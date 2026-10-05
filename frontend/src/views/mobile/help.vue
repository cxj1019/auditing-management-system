<script setup lang="ts">
import { useRouter } from 'vue-router'
import HelpContent from '../help/HelpContent.vue'

const router = useRouter()
</script>

<template>
  <div class="mh-page">
    <div class="mh-topbar">
      <span class="mh-back" @click="router.back()">← 返回</span>
      <span class="mh-title">使用指引</span>
    </div>
    <div class="mh-body">
      <HelpContent />
    </div>
  </div>
</template>

<style scoped>
.mh-page {
  min-height: 100vh;
  background: #f3f4f6;
}
.mh-topbar {
  position: sticky;
  top: 0;
  z-index: 5;
  background: #fff;
  display: flex;
  align-items: center;
  padding: 12px 14px;
  border-bottom: 1px solid #e5e7eb;
}
.mh-back {
  color: #2563eb;
  font-size: 14px;
  margin-right: 12px;
  cursor: pointer;
}
.mh-title {
  font-size: 15px;
  font-weight: 600;
}
.mh-body {
  padding: 12px;
}
@media (max-width: 480px) {
  .help-content :deep(.hc-card ol),
  .help-content :deep(.hc-card ul) {
    padding-left: 16px;
    font-size: 13px;
  }
}
</style>
