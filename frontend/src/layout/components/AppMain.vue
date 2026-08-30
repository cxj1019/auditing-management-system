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
  padding: 16px;
  height: 100%;
  overflow-y: auto;
}
</style>
