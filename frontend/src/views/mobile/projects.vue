<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { pageProjects, createProject, updateProject } from '@/api/project'
import { listBusinessTypes } from '@/api/businessType'
import { getDepartmentOptions } from '@/api/user'
import { pageClients } from '@/api/client'
import { useUserStore } from '@/stores/user'
import type { BusinessTypeItem, ClientItem, DepartmentItem, ProjectItem, ProjectRequest } from '@/types'

/** 手机端项目：列表 + 登记/编辑（编辑仅限进行中，与桌面一致） */
const userStore = useUserStore()

const statusLabels: Record<number, string> = { 0: '进行中', 1: '已完成', 2: '已归档' }
const statusTypes: Record<number, 'primary' | 'success' | 'info'> = { 0: 'primary', 1: 'success', 2: 'info' }

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '进行中', value: 0 },
  { label: '已完成', value: 1 },
  { label: '已归档', value: 2 },
]

const canAdd = computed(() => userStore.hasPermission('business:project:add'))
const canEdit = computed(() => userStore.hasPermission('business:project:edit'))

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const records = ref<ProjectItem[]>([])
const expandedId = ref<number | null>(null)

// 字典与选项
const bizDict = ref<BusinessTypeItem[]>([])
const bizNatures = computed(() => [...new Set(bizDict.value.map((b) => b.bizNature))])
const natureTypes = computed(() => [...new Set(bizDict.value.filter((b) => !form.bizNature || b.bizNature === form.bizNature).map((b) => b.projectType))])
const natureBizTypes = computed(() => bizDict.value.filter((b) => (!form.bizNature || b.bizNature === form.bizNature) && (!form.type || b.projectType === form.type)).map((b) => b.bizType))
const clientOptions = ref<ClientItem[]>([])
const deptOptions = ref<DepartmentItem[]>([])

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageProjects({
      current: 1, size: 50,
      status: activeStatus.value,
      keyword: keyword.value || undefined,
    })
    records.value = data.records
  } finally {
    loading.value = false
  }
}

function switchStatus(v: number | undefined): void {
  activeStatus.value = v
  expandedId.value = null
  fetchList()
}

// ---------- 登记/编辑 ----------
const formVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const emptyForm = (): ProjectRequest => ({
  name: '', type: '', bizNature: '收入型', bizType: '',
  clientId: 0, deptId: userStore.deptId ?? 0,
  partnerName: '', managerName: '', siteLeaderName: '',
  startDate: '', endDate: '', budgetHours: undefined, remark: '',
})
const form = reactive<ProjectRequest>(emptyForm())

function onNatureChange(): void {
  form.type = ''
  form.bizType = ''
}

function onTypeChange(): void {
  form.bizType = ''
}

async function loadOptions(): Promise<void> {
  if (!bizDict.value.length) {
    try {
      bizDict.value = await listBusinessTypes()
    } catch { /* 字典失败仍可手填 */ }
  }
  if (!clientOptions.value.length) {
    try {
      const data = await pageClients({ current: 1, size: 200 })
      clientOptions.value = data.records
    } catch { /* 忽略 */ }
  }
  if (!deptOptions.value.length) {
    try {
      deptOptions.value = await getDepartmentOptions()
    } catch { /* 忽略 */ }
  }
}

function openCreate(): void {
  Object.assign(form, emptyForm())
  editingId.value = null
  loadOptions()
  formVisible.value = true
}

function openEdit(p: ProjectItem): void {
  Object.assign(form, {
    id: p.id,
    name: p.name,
    type: p.type,
    bizNature: p.bizNature || '收入型',
    bizType: p.bizType || '',
    clientId: p.clientId,
    deptId: p.deptId,
    partnerName: p.partnerName || '',
    managerName: p.managerName || '',
    siteLeaderName: p.siteLeaderName || '',
    startDate: p.startDate || '',
    endDate: p.endDate || '',
    budgetHours: p.budgetHours ?? undefined,
    remark: p.remark || '',
  })
  editingId.value = p.id
  loadOptions()
  formVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.name.trim()) { ElMessage.warning('请填写项目名称'); return }
  if (!form.type) { ElMessage.warning('请选择项目类型'); return }
  if (!form.clientId) { ElMessage.warning('请选择客户'); return }
  if (!form.deptId) { ElMessage.warning('请选择归属部门'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateProject({ ...form, id: editingId.value })
      ElMessage.success('项目已更新')
    } else {
      await createProject(form)
      ElMessage.success('项目已登记')
    }
    formVisible.value = false
    await fetchList()
  } finally {
    saving.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="mp-page">
    <div class="mp-header">
      <span class="mp-title">项目</span>
      <el-button v-if="canAdd" type="primary" size="small" @click="openCreate">＋ 登记</el-button>
    </div>

    <div class="mp-search">
      <el-input v-model="keyword" placeholder="项目名称/编号" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mp-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mp-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !records.length" class="mp-empty">没有找到项目</div>

    <div v-for="p in records" :key="p.id" class="mp-card">
      <div class="mp-card-head" @click="expandedId = expandedId === p.id ? null : p.id">
        <span class="mp-name">{{ p.name }}</span>
        <el-tag :type="statusTypes[p.status]" size="small">{{ statusLabels[p.status] }}</el-tag>
      </div>
      <div class="mp-sub" @click="expandedId = expandedId === p.id ? null : p.id">
        {{ p.projectNo }} · {{ p.clientName || '—' }} · {{ p.type }}
      </div>

      <div v-if="expandedId === p.id" class="mp-detail">
        <div class="mp-row"><span>业务类型</span>{{ [p.bizNature, p.bizType].filter(Boolean).join(' / ') || '—' }}</div>
        <div class="mp-row"><span>项目合伙人</span>{{ p.partnerName || '—' }}</div>
        <div class="mp-row"><span>项目负责人</span>{{ p.managerName || '—' }}</div>
        <div class="mp-row"><span>现场负责人</span>{{ p.siteLeaderName || '—' }}</div>
        <div class="mp-row"><span>归属部门</span>{{ p.deptName || '—' }}</div>
        <div class="mp-row"><span>项目期间</span>{{ p.startDate || '—' }} ~ {{ p.endDate || '—' }}</div>
        <div class="mp-row" v-if="p.budgetHours"><span>预算工时</span>{{ p.budgetHours }} h</div>
        <div class="mp-row" v-if="p.reportNo"><span>报告文号</span>{{ p.reportNo }}（{{ p.reportDate || '' }}）</div>
        <div class="mp-row" v-if="p.remark"><span>备注</span>{{ p.remark }}</div>
        <div v-if="canEdit && p.status === 0" class="mp-actions">
          <el-button size="small" type="primary" plain @click="openEdit(p)">编辑</el-button>
        </div>
      </div>
    </div>

    <!-- 登记/编辑项目 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑项目' : '登记项目'" :width="360">
      <el-form label-width="88px">
        <el-form-item label="项目名称" required><el-input v-model="form.name" maxlength="120" /></el-form-item>
        <el-form-item label="业务性质">
          <el-select v-model="form.bizNature" style="width: 100%" clearable @change="onNatureChange">
            <el-option v-for="n in bizNatures" :key="n" :label="n" :value="n" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目类型" required>
          <el-select v-model="form.type" style="width: 100%" @change="onTypeChange">
            <el-option v-for="t in natureTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="form.bizType" style="width: 100%" clearable>
            <el-option v-for="t in natureBizTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="客户" required>
          <el-select v-model="form.clientId" style="width: 100%" filterable>
            <el-option v-for="c in clientOptions" :key="c.id" :label="`${c.clientNo} | ${c.clientName}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="归属部门" required>
          <el-select v-model="form.deptId" style="width: 100%">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目合伙人"><el-input v-model="form.partnerName" maxlength="30" /></el-form-item>
        <el-form-item label="项目负责人"><el-input v-model="form.managerName" maxlength="30" /></el-form-item>
        <el-form-item label="现场负责人"><el-input v-model="form.siteLeaderName" maxlength="30" /></el-form-item>
        <el-form-item label="开始日期"><el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结束日期"><el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="预算工时">
          <el-input-number v-model="form.budgetHours" :min="0" :max="99999" :precision="1" :controls="false" style="width: 100%" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mp-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mp-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mp-title { font-size: 18px; font-weight: 600; }
.mp-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mp-search .el-input { flex: 1; }
.mp-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mp-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mp-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mp-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mp-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mp-name { font-weight: 600; font-size: 14px; }
.mp-sub { color: #6b7280; font-size: 12px; margin-top: 5px; }
.mp-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mp-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mp-row span { display: inline-block; width: 76px; color: #9ca3af; }
.mp-actions { margin-top: 10px; display: flex; justify-content: flex-end; }
.mp-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
