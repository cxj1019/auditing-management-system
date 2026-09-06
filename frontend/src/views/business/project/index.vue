<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageProjects, createProject, updateProject, deleteProject, changeProjectStatus, listProjectMembers, addProjectMember, removeProjectMember } from '@/api/project'
import { pageClients } from '@/api/client'
import { getUserOptions, getDepartmentOptions } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { listBusinessTypes } from '@/api/businessType'
import type { BusinessTypeItem, ClientItem, DepartmentItem, ProjectItem, ProjectMemberItem, ProjectRequest, ProjectStatus, UserOption } from '@/types'

/** 业务类型字典（项目性质 → 项目类型 → 业务类型，按附件配置导入） */
const bizDict = ref<BusinessTypeItem[]>([])
const bizNatures = computed(() => [...new Set(bizDict.value.map((b) => b.bizNature))])
const projectTypeOptions = computed(() =>
  [...new Set(bizDict.value.map((b) => b.projectType))])
const dialogProjectTypes = computed(() =>
  [...new Set(bizDict.value.filter((b) => !form.bizNature || b.bizNature === form.bizNature).map((b) => b.projectType))])
const dialogBizOptions = computed(() =>
  bizDict.value.filter((b) => (!form.bizNature || b.bizNature === form.bizNature)
    && (!form.type || b.projectType === form.type)))

async function loadBizDict(): Promise<void> {
  bizDict.value = await listBusinessTypes()
}

function onBizNatureChange(): void {
  // 切换性质后，原项目类型/业务类型可能不属于该性质，清空重选
  const typeOk = bizDict.value.some((b) => b.bizNature === form.bizNature && b.projectType === form.type)
  if (!typeOk) {
    form.type = ''
    form.bizType = ''
  }
}

function onProjectTypeChange(): void {
  form.bizType = ''
}

function onBizTypeChange(): void {
  // 业务类型决定项目类型与性质
  const row = bizDict.value.find((b) => b.bizType === form.bizType)
  if (row) {
    form.type = row.projectType
    form.bizNature = row.bizNature
  }
}
const statusLabels: Record<number, string> = { 0: '进行中', 1: '已完成', 2: '已归档' }
const statusTagTypes: Record<number, 'primary' | 'success' | 'info'> = { 0: 'primary', 1: 'success', 2: 'info' }

// ---------- 列表查询 ----------
const loading = ref(false)
const records = ref<ProjectItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 10,
  status: undefined as ProjectStatus | undefined,
  type: '',
  keyword: '',
  dateRange: [] as string[],
})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageProjects({
      current: query.current,
      size: query.size,
      status: query.status,
      type: query.type || undefined,
      keyword: query.keyword || undefined,
      startDate: query.dateRange?.[0],
      endDate: query.dateRange?.[1],
    })
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

function handleReset(): void {
  query.status = undefined
  query.type = ''
  query.keyword = ''
  query.dateRange = []
  handleSearch()
}

// ---------- 登记/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<ProjectRequest>({
  id: undefined,
  name: '',
  type: '',
  bizNature: '',
  bizType: '',
  clientId: 0,
  deptId: 0,
  partnerName: '',
  managerName: '',
  siteLeaderName: '',
  startDate: '',
  endDate: '',
  remark: '',
})
/** 在册人员选项（供项目经理/现场负责人下拉选择） */
const userOptions = ref<UserOption[]>([])
const clientOptions = ref<ClientItem[]>([])
const deptOptions = ref<DepartmentItem[]>([])
const userStore = useUserStore()
/** 部门名称映射（列表展示用） */
const deptNameMap = computed(() => {
  const map: Record<number, string> = {}
  deptOptions.value.forEach(d => { map[d.id] = d.deptName })
  return map
})

async function loadDeptOptions(): Promise<void> {
  // 用免权限的 /departments/options，普通员工也能打开本页
  deptOptions.value = await getDepartmentOptions()
}

async function loadClientOptions(): Promise<void> {
  const data = await pageClients({ current: 1, size: 200 })
  clientOptions.value = data.records
}

async function loadUserOptions(): Promise<void> {
  userOptions.value = await getUserOptions()
}

function openCreate(): void {
  isEdit.value = false
  // 默认归属创建人所在部门，可改选
  Object.assign(form, { id: undefined, name: '', type: '', bizNature: '收入型', bizType: '', clientId: 0, deptId: userStore.deptId ?? 0, managerName: '', siteLeaderName: '', startDate: '', endDate: '', remark: '' })
  loadUserOptions()
  loadClientOptions()
  loadDeptOptions()
  dialogVisible.value = true
}

function openEdit(row: ProjectItem): void {
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    name: row.name,
    type: row.type,
    bizNature: row.bizNature || '',
    bizType: row.bizType || '',
    clientId: row.clientId,
    deptId: row.deptId,
    partnerName: row.partnerName || '',
    managerName: row.managerName,
    siteLeaderName: row.siteLeaderName,
    startDate: row.startDate,
    endDate: row.endDate,
    remark: row.remark,
  })
  loadUserOptions()
  loadClientOptions()
  loadDeptOptions()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.clientId) { ElMessage.warning('请选择客户'); return }
  if (!form.deptId) { ElMessage.warning('请选择归属部门'); return }
  if (form.startDate && form.endDate && form.startDate > form.endDate) {
    ElMessage.warning('项目开始日期不能晚于结束日期')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateProject(form)
      ElMessage.success('修改成功')
    } else {
      await createProject(form)
      ElMessage.success('登记成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

// ---------- 删除 ----------
async function handleDelete(row: ProjectItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除项目「${row.name}」吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteProject(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 用户取消
  }
}

// ---------- 状态流转 ----------
async function handleTransit(row: ProjectItem, action: 'finish' | 'reopen' | 'archive'): Promise<void> {
  const label = action === 'finish' ? '标记为已完成' : action === 'reopen' ? '重开' : '归档'
  try {
    await ElMessageBox.confirm(`确定将项目「${row.name}」${label}吗？`, '状态流转', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await changeProjectStatus(row.id, action)
    ElMessage.success('操作成功')
    fetchList()
  } catch {
    // 用户取消
  }
}

onMounted(() => {
  fetchList()
  loadBizDict()
  loadDeptOptions()
})
// ---------- 参与人员 ----------
const memberRoles = ['合伙人', '项目经理', '现场负责人', '组员']
const memberDialogVisible = ref(false)
const memberTargetId = ref<number | null>(null)
const memberTargetName = ref('')
const memberLoading = ref(false)
const memberList = ref<ProjectMemberItem[]>([])
const newMemberName = ref('')
const newMemberRole = ref('组员')

async function openMembers(row: ProjectItem): Promise<void> {
  memberTargetId.value = row.id
  memberTargetName.value = row.name
  memberDialogVisible.value = true
  await fetchMembers()
}

async function fetchMembers(): Promise<void> {
  if (!memberTargetId.value) return
  memberLoading.value = true
  try {
    memberList.value = await listProjectMembers(memberTargetId.value)
  } finally {
    memberLoading.value = false
  }
}

async function handleAddMember(): Promise<void> {
  if (!memberTargetId.value || !newMemberName.value) {
    ElMessage.warning('请选择人员')
    return
  }
  await addProjectMember(memberTargetId.value, newMemberName.value, newMemberRole.value)
  ElMessage.success('已添加')
  newMemberName.value = ''
  newMemberRole.value = '组员'
  fetchMembers()
}

async function handleRemoveMember(m: ProjectMemberItem): Promise<void> {
  if (!memberTargetId.value) return
  await removeProjectMember(memberTargetId.value, m.id)
  ElMessage.success('已移除')
  fetchMembers()
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选栏 -->
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
            <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="Number(code)" />
          </el-select>
          <el-select v-model="query.type" placeholder="项目类型" clearable filterable style="width: 150px; margin-left: 8px">
            <el-option v-for="t in projectTypeOptions" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="编号/名称/客户" clearable style="width: 200px; margin-left: 8px" @keyup.enter="handleSearch" />
          <el-date-picker v-model="query.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px; margin-left: 8px" />
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button v-permission="'business:project:add'" type="primary" @click="openCreate">登记项目</el-button>
      </div>

      <!-- 项目表格 -->
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="projectNo" label="项目编号" min-width="150" />
        <el-table-column prop="name" label="项目名称" min-width="190" show-overflow-tooltip />
        <el-table-column prop="type" label="项目类型" width="120" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizType || '—' }}</template>
        </el-table-column>
        <el-table-column prop="clientName" label="客户" min-width="140" show-overflow-tooltip />
        <el-table-column label="归属部门" width="100">
          <template #default="{ row }">{{ deptNameMap[row.deptId] || '—' }}</template>
        </el-table-column>
        <el-table-column prop="managerName" label="项目经理" width="100" />
        <el-table-column prop="partnerName" label="合伙人" width="100" />
        <el-table-column prop="siteLeaderName" label="现场负责人" width="110" />
        <el-table-column label="项目期间" min-width="200">
          <template #default="{ row }">{{ row.startDate }} ~ {{ row.endDate }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagTypes[row.status]" size="small">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click="openMembers(row)">人员</el-button>
            <template v-if="row.status !== 2">
              <el-button v-if="row.status === 0" v-permission="'business:project:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="row.status === 0" v-permission="'business:project:status'" link type="success" size="small" @click="handleTransit(row, 'finish')">完成</el-button>
              <el-button v-if="row.status === 1" v-permission="'business:project:status'" link type="primary" size="small" @click="handleTransit(row, 'reopen')">重开</el-button>
              <el-button v-if="row.status === 1" v-permission="'business:project:status'" link type="warning" size="small" @click="handleTransit(row, 'archive')">归档</el-button>
              <el-button v-if="row.status === 0" v-permission="'business:project:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
            <span v-else style="color: #9ca3af; font-size: 12px">已归档</span>
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

    <!-- 登记/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑项目' : '登记项目'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="项目名称" required>
          <el-input v-model="form.name" placeholder="如 XX公司2026年度审计" maxlength="200" />
        </el-form-item>
        <el-form-item label="项目性质" required>
          <el-select v-model="form.bizNature" style="width: 100%" @change="onBizNatureChange">
            <el-option v-for="n in bizNatures" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型" required>
          <el-select v-model="form.type" style="width: 100%" @change="onProjectTypeChange">
            <el-option v-for="t in dialogProjectTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型" required>
          <el-select v-model="form.bizType" placeholder="选择业务类型" filterable style="width: 100%" @change="onBizTypeChange">
            <el-option-group v-for="pt in dialogProjectTypes" :key="pt" :label="pt">
              <el-option
                v-for="b in dialogBizOptions.filter((x) => x.projectType === pt)"
                :key="b.id"
                :label="b.bizType"
                :value="b.bizType"
              />
            </el-option-group>
          </el-select>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="form.clientId" placeholder="选择客户" filterable style="width: 100%">
            <el-option v-for="c in clientOptions" :key="c.id" :label="`${c.clientNo} | ${c.clientName}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属部门" required>
          <el-select v-model="form.deptId" placeholder="选择归属部门（部门内成员共同维护本项目）" style="width: 100%">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目合伙人" required>
          <el-select v-model="form.partnerName" placeholder="选择项目合伙人" filterable style="width: 100%">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.nickname ? `${u.nickname} (${u.username})` : u.username"
              :value="u.nickname || u.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目经理" required>
          <el-select v-model="form.managerName" placeholder="选择项目经理" filterable style="width: 100%">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.nickname ? `${u.nickname} (${u.username})` : u.username"
              :value="u.nickname || u.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="现场负责人" required>
          <el-select v-model="form.siteLeaderName" placeholder="选择项目现场负责人" filterable style="width: 100%">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.nickname ? `${u.nickname} (${u.username})` : u.username"
              :value="u.nickname || u.username"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="项目期间">
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" placeholder="开始日期（可选）" style="width: 48%" />
          <span style="margin: 0 4px">至</span>
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" placeholder="结束日期（可选）" style="width: 48%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 参与人员弹窗 -->
    <el-dialog v-model="memberDialogVisible" :title="`参与人员 - ${memberTargetName}`" width="560px">
      <div class="items-header">
        <span class="section-title">添加人员</span>
      </div>
      <el-form :inline="true" size="small" style="margin-bottom: 12px">
        <el-form-item label="人员">
          <el-select v-model="newMemberName" filterable placeholder="选择人员" style="width: 180px">
            <el-option v-for="u in userOptions" :key="u.id" :label="u.nickname ? `${u.nickname} (${u.username})` : u.username" :value="u.nickname || u.username" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="newMemberRole" style="width: 140px">
            <el-option v-for="r in memberRoles" :key="r" :label="r" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleAddMember">添加</el-button>
        </el-form-item>
      </el-form>
      <el-table v-loading="memberLoading" :data="memberList" border size="small">
        <el-table-column prop="memberName" label="姓名" min-width="120" />
        <el-table-column prop="memberRole" label="角色" width="120" />
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button v-permission="'business:project:edit'" link type="danger" size="small" @click="handleRemoveMember(row)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
}
</style>
