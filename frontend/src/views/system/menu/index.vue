<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getMenuTree, createMenu, updateMenu } from '@/api/menu'
import type { MenuItem, MenuType } from '@/types'

// ---------- 菜单树 ----------
const loading = ref(false)
const records = ref<MenuItem[]>([])

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    records.value = await getMenuTree()
  } finally {
    loading.value = false
  }
}

const typeLabels: Record<number, string> = { 0: '目录', 1: '菜单', 2: '按钮' }

// ---------- 新增/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<{
  id?: number
  parentId: number
  name: string
  path: string
  component: string
  perm: string
  icon: string
  type: MenuType
  sort: number
}>({
  id: undefined,
  parentId: 0,
  name: '',
  path: '',
  component: '',
  perm: '',
  icon: '',
  type: 1,
  sort: 0,
})

function openCreate(parentId = 0, type: MenuType = 1): void {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    parentId,
    name: '',
    path: '',
    component: '',
    perm: '',
    icon: '',
    type,
    sort: 0,
  })
  dialogVisible.value = true
}

function openEdit(row: MenuItem): void {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    parentId: row.parentId,
    name: row.name,
    path: row.path || '',
    component: row.component || '',
    perm: row.perm || '',
    icon: row.icon || '',
    type: row.type,
    sort: row.sort,
  })
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    const payload = {
      ...form,
      path: form.path || undefined,
      component: form.component || undefined,
      perm: form.perm || undefined,
      icon: form.icon || undefined,
    }
    if (isEdit.value) {
      await updateMenu(payload)
      ElMessage.success('修改成功')
    } else {
      await createMenu(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 工具栏 -->
      <div class="table-toolbar">
        <span class="toolbar-title">菜单管理</span>
        <div>
          <el-button v-permission="'system:menu:add'" @click="openCreate(0, 0)">新增目录</el-button>
          <el-button v-permission="'system:menu:add'" type="primary" @click="openCreate(0, 1)">
            新增菜单
          </el-button>
        </div>
      </div>

      <!-- 菜单树表格 -->
      <el-table
        v-loading="loading"
        :data="records"
        border
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
      >
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.type === 0 ? 'warning' : row.type === 1 ? 'success' : 'info'" size="small">
              {{ typeLabels[row.type] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="140" />
        <el-table-column prop="component" label="组件路径" min-width="160" />
        <el-table-column prop="perm" label="权限标识" min-width="160" />
        <el-table-column prop="sort" label="排序" width="70" align="center" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:menu:edit'"
              link
              type="primary"
              size="small"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="row.type !== 2"
              v-permission="'system:menu:add'"
              link
              type="primary"
              size="small"
              @click="openCreate(row.id, row.type === 0 ? 1 : 2)"
            >
              {{ row.type === 0 ? '加菜单' : '加按钮' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜单' : '新增菜单'" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.type" :disabled="isEdit">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.name" placeholder="菜单/按钮名称" />
        </el-form-item>
        <el-form-item v-if="form.type !== 2" label="路由路径">
          <el-input v-model="form.path" placeholder="如 /system/user" />
        </el-form-item>
        <el-form-item v-if="form.type === 1" label="组件路径">
          <el-input v-model="form.component" placeholder="如 system/user/index" />
        </el-form-item>
        <el-form-item label="权限标识">
          <el-input v-model="form.perm" placeholder="如 system:user:add" />
        </el-form-item>
        <el-form-item v-if="form.type !== 2" label="图标">
          <el-input v-model="form.icon" placeholder="Element Plus 图标名，如 Setting" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-title {
  font-size: 15px;
  font-weight: 500;
  color: #1f2937;
}
</style>
