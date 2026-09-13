<script setup lang="ts">
// 内容区：路由视图
</script>

<template>
  <div class="app-main">
    <router-view v-slot="{ Component }">
      <component :is="Component" />
    </router-view>
  </div>
</template>

<style scoped>
.app-main {
  /* 全窗口铺满：无内边距；滚动统一交给外层 el-main（唯一滚动条），sticky 表头相对它吸附 */
  padding: 0;
  height: 100%;
  overflow-y: visible;
}
</style>
