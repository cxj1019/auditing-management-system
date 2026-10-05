<script setup lang="ts">
import { useRouter } from 'vue-router'
import HelpContent from './HelpContent.vue'

const router = useRouter()

/** 打印：隐藏布局框架只留正文（配合下方 @media print 规则） */
function handlePrint(): void {
  window.print()
}
</script>

<template>
  <div class="page-container help-page">
    <el-card shadow="never" class="help-card">
      <template #header>
        <div class="help-header">
          <span>使用指引（按角色查看，打印即成一页纸）</span>
          <div>
            <el-button size="small" @click="router.back()">返回</el-button>
            <el-button type="primary" size="small" @click="handlePrint">打印本页</el-button>
          </div>
        </div>
      </template>
      <HelpContent />
    </el-card>
  </div>
</template>

<style scoped>
.help-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.help-card {
  max-width: 860px;
}
@media print {
  :global(.layout-header),
  :global(.layout-aside),
  .help-header {
    display: none !important;
  }
  :global(.layout-main) {
    padding: 0 !important;
  }
  :global(.el-card__body) {
    padding: 0 !important;
  }
}
</style>
