<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageUsers, createUser, updateUser } from '@/api/user'
import { listRoles } from '@/api/role'
import { listDepartments } from '@/api/user'
import type { UserItem, UserRequest, RoleItem, DepartmentItem } from '@/types'

// ---------- 列表查询 ----------
const loading = ref(false)
const records = ref<UserItem[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageUsers(query)
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function handleSearch(): void {
  query.current = 1
  fetchList()
}

// ---------- 新增/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<UserRequest>({
  id: undefined,
  password: '',
  nickname: '',
  email: '',
  phone: '',
  deptId: undefined,
  status: 1,
  roleIds: [],
})
const roleOptions = ref<RoleItem[]>([])
const deptOptions = ref<DepartmentItem[]>([])

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    password: '',
    nickname: '',
    email: '',
    phone: '',
    deptId: undefined,
    status: 1,
    roleIds: [],
  })
  dialogVisible.value = true
}

function openEdit(row: UserItem): void {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    password: '',
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    deptId: row.deptId,
    status: row.status,
    roleIds: row.roleIds || [],
  })
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.deptId) {
    ElMessage.warning('请选择部门')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateUser(form)
      ElMessage.success('修改成功')
    } else {
      await createUser(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchList()
  listDepartments().then((depts) => {
    deptOptions.value = depts
  })
  listRoles().then((roles) => {
    roleOptions.value = roles
  })
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 工具栏 -->
      <div class="table-toolbar">
        <div class="toolbar-left">
          <el-input
            v-model="query.keyword"
            placeholder="用户名/姓名"
            clearable
            style="width: 220px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          />
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
        </div>
        <div class="toolbar-right">
          <el-button v-permission="'system:user:add'" type="primary" @click="openCreate">
            新增用户
          </el-button>
        </div>
      </div>

      <!-- 用户表格 -->
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="nickname" label="姓名" min-width="100" />
        <el-table-column prop="email" label="邮箱" min-width="160" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column prop="deptName" label="部门" min-width="120" show-overflow-tooltip />
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">
            <el-tag v-for="name in row.roleNames || []" :key="name" size="small" style="margin-right: 4px">
              {{ name }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              v-permission="'system:user:edit'"
              link
              type="primary"
              size="small"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.current"
          v-model:page-size="query.size"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList"
          @size-change="handleSearch"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑用户' : '新增用户'" width="520px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名" required>
        </el-form-item>
        <el-form-item label="密码" :required="!isEdit">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEdit ? '留空表示不修改密码' : '登录密码'"
          />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.nickname" placeholder="姓名/昵称" />
        </el-form-item>
        <el-form-item label="邮箱" required>
          <el-input v-model="form.email" placeholder="邮箱（可作为登录用户名，需唯一）" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" placeholder="手机号" />
        </el-form-item>
        <el-form-item label="部门" required>
          <el-select v-model="form.deptId" placeholder="选择部门" style="width: 100%">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleIds" multiple placeholder="选择角色" style="width: 100%">
            <el-option
              v-for="role in roleOptions"
              :key="role.id"
              :label="role.roleName"
              :value="role.id"
            />
          </el-select>
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
  </div>
</template>
