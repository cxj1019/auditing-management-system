<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  pageConfirmations,
  createConfirmation,
  updateConfirmation,
  deleteConfirmation,
  changeConfirmationStatus,
  listConfirmationAttachments,
  uploadConfirmationAttachment,
  downloadConfirmationAttachment,
  deleteConfirmationAttachment,
  getConfirmationAttPreviewUrl,
  trackConfirmationLogistics,
} from '@/api/confirmation'
import AttachmentLink from '@/components/AttachmentLink.vue'
import { pageProjects } from '@/api/project'
import type {
  ConfirmationAttachmentItem,
  ConfirmationItem,
  ConfirmationRequest,
  ConfirmationStatus,
  ProjectItem,
} from '@/types'

const types = ['银行函证', '往来款函证', '其他']
const methods = ['邮寄', '电子', '现场', '其他']
const statusLabels: Record<number, string> = { 0: '未发出', 1: '已发出', 2: '已回函', 3: '已作废' }
const statusTagTypes: Record<number, 'info' | 'primary' | 'success' | 'danger'> = {
  0: 'info', 1: 'primary', 2: 'success', 3: 'danger',
}

function money(v?: number): string {
  if (v === undefined || v === null) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

// ---------- list ----------
const loading = ref(false)
const records = ref<ConfirmationItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1, size: 10,
  status: undefined as ConfirmationStatus | undefined,
  type: '', keyword: '',
  projectId: undefined as number | undefined,
})
const projectOptions = ref<ProjectItem[]>([])

async function loadProjectOptions(): Promise<void> {
  const data = await pageProjects({ current: 1, size: 200 })
  projectOptions.value = data.records
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageConfirmations({ ...query, type: query.type || undefined })
    records.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

function handleSearch(): void { query.current = 1; fetchList() }
function handleReset(): void {
  query.status = undefined; query.type = ''; query.keyword = ''; query.projectId = undefined; handleSearch()
}

// ---------- create/edit ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<ConfirmationRequest>({
  confirmationNo: '', type: '银行函证', confirmationMethod: '邮寄',
  targetUnit: '', summary: '', projectId: undefined,
  sendTrackingNo: '', replyTrackingNo: '', replyMatched: undefined, discrepancyReason: '',
})

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, {
    confirmationNo: '', type: '银行函证', confirmationMethod: '邮寄',
    targetUnit: '', summary: '', projectId: undefined,
    sendTrackingNo: '', replyTrackingNo: '', replyMatched: undefined, discrepancyReason: '',
  })
  loadProjectOptions()
  dialogVisible.value = true
}

function openEdit(row: ConfirmationItem): void {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, confirmationNo: row.confirmationNo, type: row.type,
    confirmationMethod: row.confirmationMethod || '邮寄',
    targetUnit: row.targetUnit, summary: row.summary, projectId: row.projectId,
    sendTrackingNo: row.sendTrackingNo || '', replyTrackingNo: row.replyTrackingNo || '',
    replyMatched: row.replyMatched, discrepancyReason: row.discrepancyReason || '',
  })
  loadProjectOptions()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.projectId) {
    ElMessage.warning('请选择关联项目')
    return
  }
  saving.value = true
  try {
    if (isEdit.value) {
      await updateConfirmation(form)
      ElMessage.success('修改成功')
    } else {
      await createConfirmation(form)
      ElMessage.success('登记成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally { saving.value = false }
}

// ---------- delete ----------
async function handleDelete(row: ConfirmationItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除函证「${row.confirmationNo}」吗？`, '删除确认', { type: 'warning' })
    await deleteConfirmation(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* cancel */ }
}

// ---------- status transit ----------
const transitDialogVisible = ref(false)
const transitingId = ref<number | null>(null)
const transitAction = ref<'send' | 'confirm' | 'void'>('send')
const transitActionLabel = ref('')
const transitDate = ref('')
const transiting = ref(false)

function openTransit(row: ConfirmationItem, action: 'send' | 'confirm' | 'void'): void {
  transitingId.value = row.id
  transitAction.value = action
  transitActionLabel.value = action === 'send' ? '发出' : action === 'confirm' ? '回函' : '作废'
  transitDate.value = ''
  transitDialogVisible.value = true
}

async function handleTransit(): Promise<void> {
  if (!transitingId.value) return
  if (transitAction.value !== 'void' && !transitDate.value) {
    ElMessage.warning('请选择日期')
    return
  }
  transiting.value = true
  try {
    await changeConfirmationStatus(transitingId.value, transitAction.value, transitDate.value || undefined)
    ElMessage.success('操作成功')
    transitDialogVisible.value = false
    fetchList()
  } finally { transiting.value = false }
}

// ---------- attachments ----------
const attDialogVisible = ref(false)
const attTargetId = ref<number | null>(null)
const attTargetNo = ref('')
const attLoading = ref(false)
const attUploading = ref(false)
const attOriginals = ref<ConfirmationAttachmentItem[]>([])
const attReplies = ref<ConfirmationAttachmentItem[]>([])
const attLogistics = ref<ConfirmationAttachmentItem[]>([])

async function openAttachments(row: ConfirmationItem): Promise<void> {
  attTargetId.value = row.id
  attTargetNo.value = row.confirmationNo
  attDialogVisible.value = true
  await fetchAttachments()
}

async function fetchAttachments(): Promise<void> {
  if (!attTargetId.value) return
  attLoading.value = true
  try {
    const all = await listConfirmationAttachments(attTargetId.value)
    attOriginals.value = all.filter((a) => a.attachmentType === 'original')
    attReplies.value = all.filter((a) => a.attachmentType === 'reply')
    attLogistics.value = all.filter((a) => a.attachmentType.includes('logistics'))
  } finally { attLoading.value = false }
}

function makeUploader(attachmentType: string) {
  return async (options: UploadRequestOptions) => {
    if (!attTargetId.value) return
    attUploading.value = true
    try {
      await uploadConfirmationAttachment(attTargetId.value, attachmentType, options.file)
      ElMessage.success('上传成功')
      fetchAttachments()
    } finally { attUploading.value = false }
  }
}

async function handleDownloadAtt(att: ConfirmationAttachmentItem): Promise<void> {
  if (!attTargetId.value) return
  await downloadConfirmationAttachment(attTargetId.value, att.id, att.fileName)
}

async function handleDeleteAtt(att: ConfirmationAttachmentItem): Promise<void> {
  if (!attTargetId.value) return
  try {
    await ElMessageBox.confirm(`确定删除附件「${att.fileName}」吗？`, '删除确认', { type: 'warning' })
    await deleteConfirmationAttachment(attTargetId.value, att.id)
    ElMessage.success('删除成功')
    fetchAttachments()
  } catch { /* cancel */ }
}

// ---------- 物流截图 ----------
const logisticsDialogVisible = ref(false)
const logisticsTargetId = ref<number | null>(null)
const logisticsTargetNo = ref('')
const logisticsLoading = ref('')

function openLogistics(row: ConfirmationItem): void {
  logisticsTargetId.value = row.id
  logisticsTargetNo.value = row.confirmationNo
  logisticsDialogVisible.value = true
}

async function handleLogistics(action: 'send' | 'reply'): Promise<void> {
    if (!logisticsTargetId.value) return
    const label = action === 'send' ? '发出' : '回函'
    logisticsLoading.value = action
    try {
      await trackConfirmationLogistics(logisticsTargetId.value, action)
      ElMessage.success(`${label}物流截图已保存到附件`)
      // 同步附件目标 ID 并刷新列表
      attTargetId.value = logisticsTargetId.value
      attTargetNo.value = logisticsTargetNo.value
      attDialogVisible.value = true
      await fetchAttachments()
    } catch {
      // 错误由拦截器处理
    } finally {
      logisticsLoading.value = ''
    }
  }

onMounted(() => { fetchList(); loadProjectOptions() })

function rowClass({ row }: { row: ConfirmationItem }): string {
  return row.overdue ? 'overdue-row' : ''
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- filter -->
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
            <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="Number(code)" />
          </el-select>
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 140px; margin-left: 8px">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="编号/被函证单位/摘要" clearable style="width: 220px; margin-left: 8px" @keyup.enter="handleSearch" />
          <el-select v-model="query.projectId" placeholder="项目" clearable filterable style="width: 180px; margin-left: 8px">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button v-permission="'business:confirmation:add'" type="primary" @click="openCreate">登记函证</el-button>
      </div>

      <!-- table -->
      <el-table v-loading="loading" :data="records" border stripe
        :row-class-name="rowClass">
        <el-table-column prop="confirmationNo" label="函证编号" min-width="140" />
        <el-table-column label="所属项目" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100" />
        <el-table-column prop="confirmationMethod" label="方式" width="80" />
        <el-table-column prop="targetUnit" label="被函证单位" min-width="150" show-overflow-tooltip />
        <el-table-column prop="summary" label="内容摘要" min-width="170" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagTypes[row.status]" size="small">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="回函" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.hasReply" type="success" size="small">已回</el-tag>
            <span v-else style="color: #9ca3af">—</span>
          </template>
        </el-table-column>
        <el-table-column label="相符" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.replyMatched === true" type="success" size="small">相符</el-tag>
            <el-tag v-else-if="row.replyMatched === false" type="danger" size="small">不符</el-tag>
            <span v-else style="color: #9ca3af">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="sentDate" label="发出日期" width="110" />
        <el-table-column prop="sendTrackingNo" label="发出单号" min-width="130" show-overflow-tooltip />
        <el-table-column prop="confirmedDate" label="回函日期" width="110" />
        <el-table-column prop="replyTrackingNo" label="回函单号" min-width="130" show-overflow-tooltip />
        <el-table-column label="逾期" width="60" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.overdue" type="danger" size="small">逾期</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click="openAttachments(row)">附件</el-button>
            <el-button link type="info" size="small" @click="openLogistics(row)">物流</el-button>
            <el-button v-if="row.status !== 3" v-permission="'business:confirmation:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0" v-permission="'business:confirmation:status'" link type="primary" size="small" @click="openTransit(row, 'send')">发出</el-button>
            <el-button v-if="row.status === 1" v-permission="'business:confirmation:status'" link type="success" size="small" @click="openTransit(row, 'confirm')">回函</el-button>
            <el-button v-if="row.status === 0 || row.status === 1" v-permission="'business:confirmation:status'" link type="warning" size="small" @click="openTransit(row, 'void')">作废</el-button>
            <el-button v-if="row.status === 0" v-permission="'business:confirmation:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList" @size-change="handleSearch" />
      </div>
    </el-card>

    <!-- create/edit dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑函证' : '登记函证'" width="640px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="函证编号" required>
          <el-input v-model="form.confirmationNo" :disabled="isEdit" placeholder="人工填写编号" maxlength="30" />
        </el-form-item>
        <el-form-item label="函证类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in types" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="函证方式">
          <el-select v-model="form.confirmationMethod" style="width: 100%">
            <el-option v-for="m in methods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="被函证单位" required>
          <el-input v-model="form.targetUnit" placeholder="被函证单位名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="内容摘要" required>
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" placeholder="函证内容摘要" />
        </el-form-item>
        <el-form-item label="关联项目" required>
          <el-select v-model="form.projectId" placeholder="请选择项目" filterable style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发出快递单号">
          <el-input v-model="form.sendTrackingNo" placeholder="发出快递单号" maxlength="100" />
        </el-form-item>
        <el-form-item label="回函快递单号">
          <el-input v-model="form.replyTrackingNo" placeholder="回函快递单号" maxlength="100" />
        </el-form-item>
        <el-form-item label="回函是否相符">
          <el-select v-model="form.replyMatched" clearable placeholder="待确认" style="width: 100%">
            <el-option :value="true" label="相符" />
            <el-option :value="false" label="不相符" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.replyMatched === false" label="不符原因">
          <el-input v-model="form.discrepancyReason" type="textarea" :rows="2" maxlength="500" placeholder="请填写不符原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- status transit dialog -->
    <el-dialog v-model="transitDialogVisible" :title="`${transitActionLabel}函证`" width="420px">
      <el-form label-width="90px">
        <el-form-item v-if="transitAction !== 'void'" :label="transitAction === 'send' ? '发出日期' : '回函日期'" required>
          <el-date-picker v-model="transitDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item v-else label="确认">
          <span>确定作废该函证吗？作废后不可恢复。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="transiting" @click="handleTransit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 物流截图弹窗 -->
    <el-dialog v-model="logisticsDialogVisible" :title="`查询物流 - ${logisticsTargetNo}`" width="420px">
      <p style="margin: 0 0 16px; color: #6b7280; font-size: 13px">
        系统将自动打开快递100 查询页面，截图物流状态并保存到附件。耗时约 10-30 秒。
      </p>
      <div style="display: flex; gap: 12px; justify-content: center">
        <el-button
          type="primary"
          :loading="logisticsLoading === 'send'"
          @click="handleLogistics('send')"
        >
          查询发出物流
        </el-button>
        <el-button
          type="success"
          :loading="logisticsLoading === 'reply'"
          @click="handleLogistics('reply')"
        >
          查询回函物流
        </el-button>
      </div>
      <p style="margin: 12px 0 0; color: #9ca3af; font-size: 12px; text-align: center">
        截图将自动保存到该函证的附件列表中
      </p>
    </el-dialog>

    <!-- attachment dialog -->
    <el-dialog v-model="attDialogVisible" :title="`附件管理 - ${attTargetNo}`" width="680px">
      <!-- original -->
      <div class="att-section">
        <div class="items-header">
          <span class="section-title">原始函证扫描件</span>
          <el-upload :show-file-list="false" :http-request="makeUploader('original')" accept=".pdf,.jpg,.jpeg,.png">
            <el-button size="small" type="primary" plain :loading="attUploading">上传原始函证</el-button>
          </el-upload>
        </div>
        <el-table v-loading="attLoading" :data="attOriginals" border size="small">
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">
              <AttachmentLink :file-name="row.fileName" :content-type="row.contentType" :fetch-signed-url="() => getConfirmationAttPreviewUrl(attTargetId!, row.id)" />
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleDownloadAtt(row)">下载</el-button>
              <el-button v-permission="'business:confirmation:edit'" link type="danger" size="small" @click="handleDeleteAtt(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <!-- reply -->
      <div class="att-section" style="margin-top: 16px">
        <div class="items-header">
          <span class="section-title">回函扫描件</span>
          <el-upload :show-file-list="false" :http-request="makeUploader('reply')" accept=".pdf,.jpg,.jpeg,.png">
            <el-button size="small" type="primary" plain :loading="attUploading">上传回函</el-button>
          </el-upload>
        </div>
        <el-table :data="attReplies" border size="small">
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">
              <AttachmentLink :file-name="row.fileName" :content-type="row.contentType" :fetch-signed-url="() => getConfirmationAttPreviewUrl(attTargetId!, row.id)" />
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleDownloadAtt(row)">下载</el-button>
              <el-button v-permission="'business:confirmation:edit'" link type="danger" size="small" @click="handleDeleteAtt(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <!-- logistics -->
      <div class="att-section" style="margin-top: 16px">
        <div class="items-header">
          <span class="section-title">物流截图</span>
        </div>
        <el-table :data="attLogistics" border size="small">
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">
              <AttachmentLink :file-name="row.fileName" :content-type="row.contentType" :fetch-signed-url="() => getConfirmationAttPreviewUrl(attTargetId!, row.id)" />
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
          </el-table-column>
          <el-table-column prop="createTime" label="截图时间" width="170" />
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleDownloadAtt(row)">下载</el-button>
              <el-button v-permission="'business:confirmation:edit'" link type="danger" size="small" @click="handleDeleteAtt(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-filters { display: flex; align-items: center; }
.items-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.section-title { font-size: 14px; font-weight: 500; color: #1f2937; }
:deep(.overdue-row) { background-color: #fef2f2; }
</style>
