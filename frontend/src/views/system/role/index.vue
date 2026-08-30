<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listRoles, createRole, updateRole, getRoleMenuIds, assignRoleMenus } from '@/api/role'
import { getMenuTree } from '@/api/menu'
import type { RoleItem, RoleRequest, MenuItem } from '@/types'

// ---------- 角色列表 ----------
const loading = ref(false)
const records = ref<RoleItem[]>([])

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    records.value = await listRoles()
  } finally {
    loading.value = false
  }
}

// ---------- 新增/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<RoleRequest>({
  id: undefined,
  roleCode: '',
  roleName: '',
  description: '',
  status: 1,
})

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, { id: undefined, roleCode: '', roleName: '', description: '', status: 1 })
  dialogVisible.value = true
}

function openEdit(row: RoleItem): void {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    roleCode: row.roleCode,
    roleName: row.roleName,
    description: row.description,
    status: row.status,
  })
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    if (isEdit.value) {
      await updateRole(form)
      ElMessage.success('修改成功')
    } else {
      await createRole(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

// ---------- 分配菜单权限 ----------
const permDialogVisible = ref(false)
const permSaving = ref(false)
const permRoleId = ref<number | null>(null)
const permRoleName = ref('')
const menuTree = ref<MenuItem[]>([])
const treeRef = ref()
const permTreeProps = { label: 'name', children: 'children' }

async function openAssignPerms(row: RoleItem): Promise<void> {
  permRoleId.value = row.id
  permRoleName.value = row.roleName
  permDialogVisible.value = true
  // 加载菜单树与已分配权限
  const [tree, menuIds] = await Promise.all([getMenuTree(), getRoleMenuIds(row.id)])
  menuTree.value = tree
  // 等待树渲染完成后回显勾选（仅勾选叶子节点，避免父节点全选）
  setTimeout(() => {
    const leafIds = menuIds.filter((id) => {
      const found = findMenu(tree, id)
      return found && (!found.children || found.children.length === 0)
    })
    leafIds.forEach((id) => treeRef.value?.setChecked(id, true, false))
  }, 0)
}

/** 在菜单树中查找节点 */
function findMenu(menus: MenuItem[], id: number): MenuItem | null {
  for (const menu of menus) {
    if (menu.id === id) return menu
    if (menu.children && menu.children.length > 0) {
      const found = findMenu(menu.children, id)
      if (found) return found
    }
  }
  return null
}

async function handleAssignPerms(): Promise<void> {
  if (!permRoleId.value) return
  permSaving.value = true
  try {
    // 勾选 + 半选的父节点都要提交，保证后端完整权限集合
    const checked = treeRef.value.getCheckedKeys() as number[]
    const halfChecked = treeRef.value.getHalfCheckedKeys() as number[]
    await assignRoleMenus(permRoleId.value, [...halfChecked, ...checked])
    ElMessage.success('权限分配成功')
    permDialogVisible.value = false
  } finally {
    permSaving.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 工具栏 -->
      <div class="table-toolbar">
        <span class="toolbar-title">角色列表</span>
        <el-button v-permission="'system:role:add'" type="primary" @click="openCreate">
          新增角色
        </el-button>
      </div>

      <!-- 角色表格 -->
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="roleCode" label="角色编码" min-width="120" />
        <el-table-column prop="roleName" label="角色名称" min-width="120" />
        <el-table-column prop="description" label="描述" min-width="200" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:role:edit'"
              link
              type="primary"
              size="small"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-permission="'system:role:edit'"
              link
              type="primary"
              size="small"
              @click="openAssignPerms(row)"
            >
              分配权限
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑角色' : '新增角色'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.roleCode" placeholder="如 finance-manager" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" placeholder="如 财务经理" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="角色描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限弹窗 -->
    <el-dialog v-model="permDialogVisible" :title="`分配权限 - ${permRoleName}`" width="480px">
      <el-tree
        ref="treeRef"
        :data="menuTree"
        :props="permTreeProps"
        node-key="id"
        show-checkbox
        default-expand-all
      />
      <template #footer>
        <el-button @click="permDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="permSaving" @click="handleAssignPerms">确定</el-button>
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
