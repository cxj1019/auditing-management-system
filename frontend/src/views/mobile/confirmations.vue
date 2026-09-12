<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageConfirmations, createConfirmation, updateConfirmation, changeConfirmationStatus, listConfirmationAttachments, uploadConfirmationAttachment, deleteConfirmationAttachment } from '@/api/confirmation'
import { projectOptions as projectOptionsApi } from '@/api/project'
import { useUserStore } from '@/stores/user'
import CaptureUpload from '@/components/CaptureUpload.vue'
import type { ConfirmationAttachmentItem, ConfirmationItem, ConfirmationRequest, ProjectItem } from '@/types'

/** 手机端函证：列表 + 登记/编辑 + 发出/回函/作废流转（与桌面权限一致） */
const userStore = useUserStore()

const statusLabels: Record<number, string> = { 0: '未发出', 1: '已发出', 2: '已回函', 3: '已作废' }
const statusTypes: Record<number, 'info' | 'primary' | 'success' | 'danger'> = {
  0: 'info', 1: 'primary', 2: 'success', 3: 'danger',
}

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '未发出', value: 0 },
  { label: '已发出', value: 1 },
  { label: '已回函', value: 2 },
]

const types = ['银行函证', '往来款函证', '其他']
const methods = ['邮寄', '电子', '跟函', '其他']

const canAdd = computed(() => userStore.hasPermission('business:confirmation:add'))
const canEdit = computed(() => userStore.hasPermission('business:confirmation:edit'))
const canTransit = computed(() => userStore.hasPermission('business:confirmation:status'))

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const projectFilter = ref<number | undefined>(undefined)
const records = ref<ConfirmationItem[]>([])
const expandedId = ref<number | null>(null)

const projectOptions = ref<ProjectItem[]>([])

// ---------- 附件（原始函证/回函 拍照上传） ----------
const attCache = ref<Record<number, ConfirmationAttachmentItem[]>>({})
const attUploading = ref(false)

function attsOf(id: number, type: string): ConfirmationAttachmentItem[] {
  return (attCache.value[id] || []).filter((a) => a.attachmentType === type)
}

async function loadAtts(id: number): Promise<void> {
  attCache.value[id] = await listConfirmationAttachments(id).catch(() => [])
}

async function uploadAtts(c: ConfirmationItem, attachmentType: 'original' | 'reply', files: File[]): Promise<void> {
  if (!files.length) return
  attUploading.value = true
  try {
    for (const f of files) await uploadConfirmationAttachment(c.id, attachmentType, f)
    ElMessage.success(`已上传 ${files.length} 个文件`)
    await loadAtts(c.id)
  } finally {
    attUploading.value = false
  }
}

async function removeAtt(c: ConfirmationItem, att: ConfirmationAttachmentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除「${att.fileName}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteConfirmationAttachment(c.id, att.id)
  ElMessage.success('已删除')
  await loadAtts(c.id)
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageConfirmations({
      current: 1, size: 200,
      status: activeStatus.value,
      keyword: keyword.value || undefined,
      projectId: projectFilter.value,
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

function onProjectFilter(): void {
  expandedId.value = null
  fetchList()
}

function toggle(c: ConfirmationItem): void {
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

const emptyForm = (): ConfirmationRequest => ({
  confirmationNo: '',
  type: '银行函证',
  confirmationMethod: '邮寄',
  targetUnit: '',
  summary: '',
  projectId: undefined,
})
const form = reactive<ConfirmationRequest>(emptyForm())

function openCreate(): void {
  Object.assign(form, emptyForm())
  editingId.value = null
  loadProjects()
  formVisible.value = true
}

function openEdit(c: ConfirmationItem): void {
  Object.assign(form, {
    id: c.id,
    confirmationNo: c.confirmationNo,
    type: c.type,
    confirmationMethod: c.confirmationMethod || '',
    targetUnit: c.targetUnit,
    summary: c.summary,
    projectId: c.projectId ?? undefined,
  })
  editingId.value = c.id
  loadProjects()
  formVisible.value = true
}

async function loadProjects(): Promise<void> {
  if (!projectOptions.value.length) {
    try {
      projectOptions.value = await projectOptionsApi()
    } catch { /* 忽略 */ }
  }
}

async function handleSave(): Promise<void> {
  if (!form.confirmationNo.trim()) { ElMessage.warning('请填写函证编号'); return }
  if (!form.targetUnit.trim()) { ElMessage.warning('请填写函证单位'); return }
  if (!form.summary.trim()) { ElMessage.warning('请填写函证内容'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateConfirmation({ ...form, id: editingId.value })
      ElMessage.success('函证已更新')
    } else {
      await createConfirmation(form)
      ElMessage.success('函证已登记')
    }
    formVisible.value = false
    await fetchList()
  } finally {
    saving.value = false
  }
}

// ---------- 状态流转 ----------
const transiting = ref(false)

async function handleSend(c: ConfirmationItem): Promise<void> {
  let date = today()
  try {
    const input = await ElMessageBox.prompt(`发出函证「${c.confirmationNo}」，确认发出日期`, '发出函证', {
      inputValue: date, confirmButtonText: '发出', cancelButtonText: '取消',
    })
    date = input.value || date
  } catch { return }
  transiting.value = true
  try {
    await changeConfirmationStatus(c.id, 'send', date)
    ElMessage.success('已发出')
    await fetchList()
  } finally {
    transiting.value = false
  }
}

async function handleConfirm(c: ConfirmationItem): Promise<void> {
  let date = today()
  try {
    const input = await ElMessageBox.prompt(`函证「${c.confirmationNo}」收到回函，确认回函日期`, '回函确认', {
      inputValue: date, confirmButtonText: '确定', cancelButtonText: '取消',
    })
    date = input.value || date
  } catch { return }
  transiting.value = true
  try {
    await changeConfirmationStatus(c.id, 'confirm', date)
    ElMessage.success('已登记回函')
    await fetchList()
  } finally {
    transiting.value = false
  }
}

async function handleVoid(c: ConfirmationItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`作废函证「${c.confirmationNo}」？`, '作废确认', { type: 'warning' })
  } catch { return }
  transiting.value = true
  try {
    await changeConfirmationStatus(c.id, 'void')
    ElMessage.success('已作废')
    await fetchList()
  } finally {
    transiting.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="mf-page">
    <div class="mf-header">
      <span class="mf-title">函证</span>
      <el-button v-if="canAdd" type="primary" size="small" @click="openCreate">＋ 登记</el-button>
    </div>

    <div class="mf-search">
      <el-input v-model="keyword" placeholder="函证号/单位" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mf-project">
      <el-select v-model="projectFilter" clearable filterable placeholder="按项目筛选" style="width: 100%" @change="onProjectFilter">
        <el-option v-for="pj in projectOptions" :key="pj.id" :label="`${pj.projectNo} | ${pj.name}`" :value="pj.id" />
      </el-select>
    </div>

    <div class="mf-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mf-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !records.length" class="mf-empty">没有找到函证</div>

    <div v-for="c in records" :key="c.id" class="mf-card">
      <div class="mf-card-head" @click="toggle(c)">
        <span class="mf-name">{{ c.targetUnit }}</span>
        <el-tag :type="statusTypes[c.status]" size="small">{{ statusLabels[c.status] }}</el-tag>
      </div>
      <div class="mf-sub">
        {{ c.confirmationNo }} · {{ c.type }}<el-tag v-if="c.overdue" type="danger" size="small" style="margin-left: 6px">逾期</el-tag>
        <el-tag v-if="c.replyMatched === false" type="warning" size="small" style="margin-left: 6px">回函不符</el-tag>
      </div>

      <div v-if="expandedId === c.id" class="mf-detail">
        <div class="mf-row"><span>函证方式</span>{{ c.confirmationMethod || '—' }}</div>
        <div class="mf-row"><span>函证内容</span>{{ c.summary || '—' }}</div>
        <div class="mf-row"><span>关项目</span>{{ c.projectName || '—' }}</div>
        <div class="mf-row"><span>发出日期</span>{{ c.sentDate || '—' }}</div>
        <div class="mf-row" v-if="c.sendTrackingNo"><span>发出快递</span>{{ c.sendTrackingNo }}</div>
        <div class="mf-row"><span>回函日期</span>{{ c.confirmedDate || '—' }}</div>
        <div class="mf-row" v-if="c.replyTrackingNo"><span>回函快递</span>{{ c.replyTrackingNo }}</div>
        <div class="mf-row" v-if="c.discrepancyReason"><span>差异原因</span>{{ c.discrepancyReason }}</div>

        <div class="mf-sec">原始函证（{{ attsOf(c.id, 'original').length }}）</div>
        <div v-for="att in attsOf(c.id, 'original')" :key="att.id" class="mf-att">
          <span class="mf-att-name">{{ att.fileName }}</span>
          <el-button v-if="canEdit" link type="danger" size="small" @click="removeAtt(c, att)">删除</el-button>
        </div>
        <div v-if="canEdit" class="mf-upload">
          <CaptureUpload :uploading="attUploading" text="选择文件" @pick="(files: File[]) => uploadAtts(c, 'original', files)" />
        </div>

        <div class="mf-sec">回函扫描件（{{ attsOf(c.id, 'reply').length }}）</div>
        <div v-for="att in attsOf(c.id, 'reply')" :key="att.id" class="mf-att">
          <span class="mf-att-name">{{ att.fileName }}</span>
          <el-button v-if="canEdit" link type="danger" size="small" @click="removeAtt(c, att)">删除</el-button>
        </div>
        <div v-if="canEdit" class="mf-upload">
          <CaptureUpload :uploading="attUploading" text="选择文件" @pick="(files: File[]) => uploadAtts(c, 'reply', files)" />
        </div>

        <div class="mf-actions">
          <el-button v-if="canEdit && c.status !== 3" size="small" plain @click="openEdit(c)">编辑</el-button>
          <el-button v-if="canTransit && c.status === 0" size="small" type="primary" :disabled="transiting" @click="handleSend(c)">发出</el-button>
          <el-button v-if="canTransit && c.status === 1" size="small" type="success" :disabled="transiting" @click="handleConfirm(c)">登记回函</el-button>
          <el-button v-if="canTransit && c.status !== 3" size="small" type="danger" plain :disabled="transiting" @click="handleVoid(c)">作废</el-button>
        </div>
      </div>
    </div>

    <!-- 登记/编辑函证 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑函证' : '登记函证'" :width="340">
      <el-form label-width="80px">
        <el-form-item label="函证编号" required><el-input v-model="form.confirmationNo" maxlength="50" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="函证方式">
          <el-select v-model="form.confirmationMethod" style="width: 100%" clearable>
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="函证单位" required><el-input v-model="form.targetUnit" maxlength="200" /></el-form-item>
        <el-form-item label="函证内容" required><el-input v-model="form.summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="关联项目">
          <el-select v-model="form.projectId" style="width: 100%" clearable filterable>
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mf-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mf-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mf-title { font-size: 18px; font-weight: 600; }
.mf-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mf-search .el-input { flex: 1; }
.mf-project { margin-bottom: 8px; }
.mf-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mf-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mf-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mf-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mf-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mf-name { font-weight: 600; font-size: 14px; }
.mf-sub { color: #6b7280; font-size: 12px; margin-top: 5px; display: flex; align-items: center; flex-wrap: wrap; }
.mf-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mf-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mf-row span { display: inline-block; width: 68px; color: #9ca3af; }
.mf-sec { font-size: 12px; color: #2563eb; font-weight: 600; margin: 10px 0 4px; }
.mf-att { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: #374151; padding: 4px 0; border-bottom: 1px solid #f9fafb; }
.mf-att-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.mf-upload { margin-top: 8px; }
.mf-upload .cap-upload { display: flex; gap: 8px; width: 100%; }
.mf-upload .el-button { flex: 1; }
.mf-actions { margin-top: 10px; display: flex; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.mf-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
