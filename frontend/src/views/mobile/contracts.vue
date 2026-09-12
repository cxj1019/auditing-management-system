<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageContracts, createContract, updateContract, listAttachments, uploadAttachment, deleteAttachment } from '@/api/contract'
import { projectOptions as projectOptionsApi } from '@/api/project'
import { listBusinessTypes } from '@/api/businessType'
import { useUserStore } from '@/stores/user'
import CaptureUpload from '@/components/CaptureUpload.vue'
import type { BusinessTypeItem, ContractAttachmentItem, ContractItem, ContractRequest, ProjectItem } from '@/types'

/** 手机端合同：列表 + 登记/编辑（编辑仅草稿，与桌面一致；不含税主输入自动算税） */
const userStore = useUserStore()

const statusLabels: Record<number, string> = { 0: '草稿', 1: '执行中', 2: '已完成', 3: '已终止' }
const statusTypes: Record<number, 'info' | 'primary' | 'success' | 'danger'> = {
  0: 'info', 1: 'primary', 2: 'success', 3: 'danger',
}

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '执行中', value: 1 },
  { label: '已完成', value: 2 },
  { label: '已终止', value: 3 },
  { label: '草稿', value: 0 },
]

const canAdd = computed(() => userStore.hasPermission('business:contract:add'))
const canEdit = computed(() => userStore.hasPermission('business:contract:edit'))

const TAX_RATE_PRESETS = [13, 9, 6, 3, 1.5, 0]

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const projectFilter = ref<number | undefined>(undefined)
const records = ref<ContractItem[]>([])
const expandedId = ref<number | null>(null)

/** 项目筛选项 */
const projectList = ref<ProjectItem[]>([])
const filteredRecords = computed(() =>
  projectFilter.value ? records.value.filter((c) => c.projectId === projectFilter.value) : records.value,
)

const projectOptions = ref<ProjectItem[]>([])
const bizDict = ref<BusinessTypeItem[]>([])

// ---------- 扫描件附件（拍照/相册，压缩后上传） ----------
const attCache = ref<Record<number, ContractAttachmentItem[]>>({})
const attUploading = ref(false)

async function loadAtts(contractId: number): Promise<void> {
  attCache.value[contractId] = await listAttachments(contractId).catch(() => [])
}

async function uploadAtts(c: ContractItem, files: File[]): Promise<void> {
  if (!files.length) return
  attUploading.value = true
  try {
    for (const f of files) await uploadAttachment(c.id, f)
    ElMessage.success(`已上传 ${files.length} 个扫描件`)
    await loadAtts(c.id)
  } finally {
    attUploading.value = false
  }
}

async function removeAtt(c: ContractItem, att: ContractAttachmentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除扫描件「${att.fileName}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteAttachment(c.id, att.id)
  ElMessage.success('已删除')
  await loadAtts(c.id)
}

function money(v?: number): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function round2(v: number): number {
  return Math.round(v * 100) / 100
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageContracts({
      current: 1, size: 200,
      status: activeStatus.value,
      clientName: keyword.value || undefined,
    })
    records.value = data.records
  } finally {
    loading.value = false
  }
}

async function loadProjects(): Promise<void> {
  if (!projectList.value.length) {
    try {
      projectList.value = await projectOptionsApi()
    } catch { /* 忽略 */ }
  }
}

function switchStatus(v: number | undefined): void {
  activeStatus.value = v
  expandedId.value = null
  fetchList()
}

function toggle(c: ContractItem): void {
  if (expandedId.value === c.id) {
    expandedId.value = null
    return
  }
  expandedId.value = c.id
  if (!attCache.value[c.id]) loadAtts(c.id)
}

// ---------- 登记/编辑 ----------
const formVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const emptyForm = (): ContractRequest => ({
  projectId: 0,
  contractType: '',
  bizType: '',
  amount: 0,
  taxRate: undefined,
  amountExTax: undefined,
  taxAmount: undefined,
  signDate: '',
  serviceStart: '',
  serviceEnd: '',
  keeperName: '',
  remark: '',
  currency: '人民币',
})
const form = reactive<ContractRequest>(emptyForm())

const projectBizTypes = computed(() =>
  bizDict.value.filter((b) => !form.contractType || b.projectType === form.contractType).map((b) => b.bizType),
)

function onProjectChange(): void {
  // 合同类型随项目类型带出（与桌面一致）
  const p = projectOptions.value.find((x) => x.id === form.projectId)
  form.contractType = p?.type || ''
  form.bizType = ''
}

/** 不含税金额变化：税额 = 不含税 × 税率，含税 = 不含税 + 税额 */
function onAmountExTaxChange(): void {
  if (form.amountExTax == null || form.amountExTax <= 0) return
  const tax = form.taxRate == null ? 0 : round2((form.amountExTax * form.taxRate) / 100)
  form.taxAmount = tax
  form.amount = round2(form.amountExTax + tax)
}

function onTaxRateChange(): void {
  onAmountExTaxChange()
}

async function loadOptions(): Promise<void> {
  if (!projectOptions.value.length) {
    try {
      projectOptions.value = await projectOptionsApi()
    } catch { /* 忽略 */ }
  }
  if (!bizDict.value.length) {
    try {
      bizDict.value = await listBusinessTypes()
    } catch { /* 忽略 */ }
  }
}

function openCreate(): void {
  Object.assign(form, emptyForm())
  editingId.value = null
  loadOptions()
  formVisible.value = true
}

function openEdit(c: ContractItem): void {
  Object.assign(form, {
    id: c.id,
    projectId: c.projectId,
    contractType: c.contractType,
    bizType: c.bizType || '',
    amount: c.amount,
    taxRate: c.taxRate ?? undefined,
    amountExTax: c.amountExTax ?? undefined,
    taxAmount: c.taxAmount ?? undefined,
    signDate: c.signDate || '',
    serviceStart: c.serviceStart || '',
    serviceEnd: c.serviceEnd || '',
    keeperName: c.keeperName || '',
    remark: c.remark || '',
    currency: c.currency || '人民币',
  })
  editingId.value = c.id
  loadOptions()
  formVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.projectId) { ElMessage.warning('请选择关联项目'); return }
  if (!form.amountExTax || form.amountExTax <= 0) { ElMessage.warning('请填写不含税金额'); return }
  if (!form.keeperName.trim()) { ElMessage.warning('请填写合同保管人'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateContract({ ...form, id: editingId.value })
      ElMessage.success('合同已更新')
    } else {
      await createContract(form)
      ElMessage.success('合同已登记')
    }
    formVisible.value = false
    await fetchList()
  } finally {
    saving.value = false
  }
}

onMounted(() => { fetchList(); loadProjects() })
</script>

<template>
  <div class="mt-page">
    <div class="mt-header">
      <span class="mt-title">合同</span>
      <el-button v-if="canAdd" type="primary" size="small" @click="openCreate">＋ 登记</el-button>
    </div>

    <div class="mt-search">
      <el-input v-model="keyword" placeholder="客户名称" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mt-project">
      <el-select v-model="projectFilter" clearable filterable placeholder="按项目筛选" style="width: 100%" @change="expandedId = null">
        <el-option v-for="pj in projectList" :key="pj.id" :label="`${pj.projectNo} | ${pj.name}`" :value="pj.id" />
      </el-select>
    </div>

    <div class="mt-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mt-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !filteredRecords.length" class="mt-empty">没有找到合同</div>

    <div v-for="c in filteredRecords" :key="c.id" class="mt-card">
      <div class="mt-card-head" @click="toggle(c)">
        <span class="mt-name">{{ c.clientName || c.contractNo }}</span>
        <el-tag :type="statusTypes[c.status]" size="small">{{ statusLabels[c.status] }}</el-tag>
      </div>
      <div class="mt-sub" @click="toggle(c)">
        {{ c.contractNo }}
      </div>
      <div class="mt-amount">
        <b>¥ {{ money(c.amount) }}</b>
        <span class="mt-amount-sub">签约 {{ c.signDate || '—' }}</span>
      </div>

      <div v-if="expandedId === c.id" class="mt-detail">
        <div class="mt-row"><span>业务类型</span>{{ c.bizType || c.contractType || '—' }}</div>
        <div class="mt-row"><span>关联项目</span>{{ c.projectNo || '—' }} {{ c.projectName || '' }}</div>
        <div class="mt-row"><span>不含税金额</span>{{ money(c.amountExTax) }}</div>
        <div class="mt-row"><span>税率 / 税额</span>{{ c.taxRate != null ? c.taxRate + '%' : '—' }} / {{ money(c.taxAmount) }}</div>
        <div class="mt-row" v-if="c.currency && c.currency !== '人民币'"><span>币种</span>{{ c.currency }} {{ c.foreignAmount ?? '' }} @ {{ c.exchangeRate ?? '' }}</div>
        <div class="mt-row"><span>服务期间</span>{{ c.serviceStart || '—' }} ~ {{ c.serviceEnd || '—' }}</div>
        <div class="mt-row"><span>经办人</span>{{ c.keeperName || '—' }}</div>
        <div class="mt-row" v-if="c.remark"><span>备注</span>{{ c.remark }}</div>
        <div class="mt-sec">扫描件（{{ attCache[c.id]?.length ?? 0 }}）</div>
        <div v-for="att in attCache[c.id] || []" :key="att.id" class="mt-att">
          <span class="mt-att-name">{{ att.fileName }}</span>
          <el-button v-if="canEdit" link type="danger" size="small" @click="removeAtt(c, att)">删除</el-button>
        </div>
        <div v-if="canEdit" class="mt-upload">
          <CaptureUpload :uploading="attUploading" text="选择文件" @pick="(files: File[]) => uploadAtts(c, files)" />
        </div>

        <div v-if="canEdit && c.status === 0" class="mt-actions">
          <el-button size="small" type="primary" plain @click="openEdit(c)">编辑</el-button>
        </div>
      </div>
    </div>

    <!-- 登记/编辑合同 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑合同' : '登记合同'" :width="360">
      <el-form label-width="96px">
        <el-form-item label="关联项目" required>
          <el-select v-model="form.projectId" style="width: 100%" filterable @change="onProjectChange">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同类型">
          <el-input :model-value="form.contractType" placeholder="随项目类型带出" disabled />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="form.bizType" style="width: 100%" clearable>
            <el-option v-for="t in projectBizTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="不含税金额" required>
          <el-input-number v-model="form.amountExTax" :min="0" :precision="2" :controls="false" style="width: 100%" @change="onAmountExTaxChange" />
        </el-form-item>
        <el-form-item label="税率（%）">
          <el-select :model-value="form.taxRate" style="width: 100%" clearable allow-create filterable placeholder="选择或输入" @update:model-value="(v: unknown) => { form.taxRate = v == null || v === '' ? undefined : Number(v); onTaxRateChange() }">
            <el-option v-for="r in TAX_RATE_PRESETS" :key="r" :label="r + '%'" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="form.taxAmount" :min="0" :precision="2" :controls="false" style="width: 100%" @change="onAmountExTaxChange" />
        </el-form-item>
        <el-form-item label="含税金额">
          <el-input :model-value="money(form.amount)" disabled />
        </el-form-item>
        <el-form-item label="签约日期"><el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="开始执行"><el-date-picker v-model="form.serviceStart" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="结束执行"><el-date-picker v-model="form.serviceEnd" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="合同保管人" required><el-input v-model="form.keeperName" maxlength="30" /></el-form-item>
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
.mt-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mt-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mt-title { font-size: 18px; font-weight: 600; }
.mt-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mt-search .el-input { flex: 1; }
.mt-project { margin-bottom: 8px; }
.mt-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mt-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mt-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mt-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mt-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mt-name { font-weight: 600; font-size: 14px; }
.mt-sub { color: #9ca3af; font-size: 12px; margin-top: 4px; }
.mt-amount { margin-top: 6px; display: flex; justify-content: space-between; align-items: baseline; }
.mt-amount b { color: #f56c6c; font-size: 15px; }
.mt-amount-sub { color: #9ca3af; font-size: 11px; }
.mt-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mt-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mt-row span { display: inline-block; width: 90px; color: #9ca3af; }
.mt-sec { font-size: 12px; color: #2563eb; font-weight: 600; margin: 10px 0 4px; }
.mt-att { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: #374151; padding: 4px 0; border-bottom: 1px solid #f9fafb; }
.mt-att-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mt-upload { margin-top: 8px; }
.mt-upload .cap-upload { display: flex; gap: 8px; width: 100%; }
.mt-upload .el-button { flex: 1; }
.mt-actions { margin-top: 10px; display: flex; justify-content: flex-end; }
.mt-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
