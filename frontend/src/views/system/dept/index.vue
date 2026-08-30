<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listDepartments, createDepartment, updateDepartment, deleteDepartment } from '@/api/user'
import type { DepartmentItem } from '@/types'

const loading = ref(false)
const records = ref<DepartmentItem[]>([])

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    records.value = await listDepartments()
  } finally {
    loading.value = false
  }
}

const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<{ id?: number; deptName: string; sort: number }>({
  id: undefined, deptName: '', sort: 0,
})

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, { id: undefined, deptName: '', sort: 0 })
  dialogVisible.value = true
}

function openEdit(row: DepartmentItem): void {
  isEdit.value = true
  Object.assign(form, { id: row.id, deptName: row.deptName, sort: row.sort })
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    if (isEdit.value) {
      await updateDepartment(form as { id: number; deptName: string; sort?: number })
      ElMessage.success('修改成功')
    } else {
      await createDepartment(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally { saving.value = false }
}

async function handleDelete(row: DepartmentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除部门「${row.deptName}」吗？`, '删除确认', { type: 'warning' })
    await deleteDepartment(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* cancel */ }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <span style="font-size: 15px; font-weight: 500; color: #1f2937">部门列表</span>
        <el-button v-permission="'system:dept:add'" type="primary" @click="openCreate">新增部门</el-button>
      </div>
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="deptName" label="部门名称" min-width="200" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:dept:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:dept:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑部门' : '新增部门'" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="部门名称" required>
          <el-input v-model="form.deptName" placeholder="部门名称" maxlength="100" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
