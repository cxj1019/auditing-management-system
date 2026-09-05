<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import * as XLSX from 'xlsx'
import html2canvas from 'html2canvas'
import { jsPDF } from 'jspdf'
import {
  pageReimbursements,
  createReimbursement,
  updateReimbursement,
  submitReimbursement,
  withdrawReimbursement,
  deleteReimbursement,
  approveReimbursement,
  financeReimbursement,
  getExportItems,
  listReimbAttachments,
  uploadReimbAttachment,
  downloadReimbAttachment,
  deleteReimbAttachment,
  getReimbItems,
  getReimbAttPreviewUrl,
} from '@/api/reimbursement'
import AttachmentLink from '@/components/AttachmentLink.vue'
import { pageProjects } from '@/api/project'
import { useUserStore } from '@/stores/user'
import type {
  ProjectItem,
  ReimbursementAttachmentItem,
  ReimbursementItem,
  ReimbursementItemData,
  ReimbursementRequest,
  ReimbursementStatus,
} from '@/types'

const userStore = useUserStore()
const categories = ['差旅费', '交通费', '办公费', '餐饮费', '其他']
const statusLabels: Record<number, string> = { 0: '草稿', 1: '待审批', 2: '已批准', 3: '已驳回', 4: '待终审' }
const statusTagTypes: Record<number, 'info' | 'warning' | 'success' | 'danger' | 'primary'> = {
  0: 'info',
  1: 'warning',
  2: 'success',
  3: 'danger',
  4: 'primary',
}

function money(v: number | null | undefined): string {
  if (v === null || v === undefined) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

// ---------- 列表查询 ----------
const loading = ref(false)
const records = ref<ReimbursementItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 10,
  status: undefined as ReimbursementStatus | undefined,
  keyword: '',
})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageReimbursements(query)
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
  query.keyword = ''
  handleSearch()
}

function isOwner(row: ReimbursementItem): boolean {
  // 优先按用户 ID 判断（用户名随邮箱编辑可能变化）；历史数据回退按账号忽略大小写比较
  if (row.applicantId != null) {
    return row.applicantId === userStore.userId
  }
  return (row.applicantUsername || '').toLowerCase() === (userStore.username || '').toLowerCase()
}

/** 详情抽屉里可维护（上传/删除附件）：草稿或已驳回 且 申请人本人 */
const canMaintainDetail = computed(
  () => !!detail.value && (detail.value.status === 0 || detail.value.status === 3) && isOwner(detail.value)
)

// ---------- 新建/编辑（基本信息 + 明细行编辑器） ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<ReimbursementRequest>({
  projectId: undefined,
  title: '',
  items: [],
})
const projectOptions = ref<ProjectItem[]>([])

function emptyItem(): ReimbursementItemData {
  return { category: '差旅费', amount: 0, expenseDate: new Date().toISOString().slice(0, 10), description: '', invoiceNumber: '', isVatInvoice: false }
}

function addItem(): void {
  form.items.push(emptyItem())
}

function removeItem(index: number): void {
  form.items.splice(index, 1)
}

const currentBillId = computed(() => editingId.value || detail.value?.id || 0)

const itemsTotal = computed(() => form.items.reduce((sum, i) => sum + Number(i.amount || 0), 0))

async function loadProjectOptions(): Promise<void> {
  const data = await pageProjects({ current: 1, size: 200 })
  projectOptions.value = data.records
}

function openCreate(): void {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, { projectId: undefined, title: '', items: [emptyItem()] })
  loadProjectOptions()
  dialogVisible.value = true
}

async function openEdit(row: ReimbursementItem): Promise<void> {
  isEdit.value = true
  editingId.value = row.id
  form.projectId = row.projectId
  form.title = row.title
  // 加载已有明细行（含 ID），保证编辑后增量更新不丢数据
  await syncItemIds(row.id)
  loadProjectOptions()
  await refreshBillAtts()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.items.length) {
    ElMessage.warning('至少需要一条费用明细')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && editingId.value) {
      await updateReimbursement(editingId.value, form)
      ElMessage.success('保存成功')
      await syncItemIds(editingId.value)
    } else {
      // 创建草稿：保持弹窗打开并切换为编辑模式，便于逐行上传发票附件
      editingId.value = await createReimbursement(form)
      isEdit.value = true
      await syncItemIds(editingId.value)
      ElMessage.success('草稿已保存，可为每行明细上传发票')
    }
    fetchList()
  } finally {
    saving.value = false
  }
}

/** 同步后端明细行 ID（保留行级附件关联） */
async function syncItemIds(billId: number): Promise<void> {
  const items = await getReimbItems(billId)
  form.items = items.map((i) => ({
    id: i.id,
    category: i.category,
    amount: Number(i.amount),
    expenseDate: i.expenseDate,
    description: i.description || '',
    invoiceNumber: i.invoiceNumber || '',
    isVatInvoice: !!i.isVatInvoice,
  }))
}
// ---------- 行级发票附件 ----------
const billAtts = ref<ReimbursementAttachmentItem[]>([])
const rowAttVisible = ref(false)
const rowAttTarget = ref<ReimbursementItemData | null>(null)
const rowAttUploading = ref(false)

function rowCountAtts(itemId?: number): ReimbursementAttachmentItem[] {
  if (!itemId) return []
  return billAtts.value.filter((a) => a.itemId === itemId)
}

async function refreshBillAtts(): Promise<void> {
  if (!editingId.value) return
  billAtts.value = await listReimbAttachments(editingId.value)
}

function openRowAtt(row: ReimbursementItemData): void {
  rowAttTarget.value = row
  rowAttVisible.value = true
  refreshBillAtts()
}

async function handleRowUpload(options: UploadRequestOptions): Promise<void> {
  if (!editingId.value || !rowAttTarget.value?.id) return
  rowAttUploading.value = true
  try {
    await uploadReimbAttachment(editingId.value, options.file, rowAttTarget.value.id)
    ElMessage.success('上传成功')
    refreshBillAtts()
  } finally {
    rowAttUploading.value = false
  }
}

async function handleRowDownload(att: ReimbursementAttachmentItem): Promise<void> {
  if (!editingId.value) return
  await downloadReimbAttachment(editingId.value, att.id, att.fileName)
}

async function handleRowDelete(att: ReimbursementAttachmentItem): Promise<void> {
  if (!editingId.value) return
  try {
    await ElMessageBox.confirm(`确定删除附件「${att.fileName}」吗？`, '删除确认', { type: 'warning' })
    await deleteReimbAttachment(editingId.value, att.id)
    ElMessage.success('删除成功')
    refreshBillAtts()
  } catch { /* 取消 */ }
}

// ---------- 生命周期操作 ----------
async function handleSubmit(row: ReimbursementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定提交报销单「${row.reimbursementNo}」进入审批吗？`, '提交确认', { type: 'warning' })
    await submitReimbursement(row.id)
    ElMessage.success('已提交')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleWithdraw(row: ReimbursementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定撤回报销单「${row.reimbursementNo}」回到草稿吗？`, '撤回确认', { type: 'warning' })
    await withdrawReimbursement(row.id)
    ElMessage.success('已撤回')
    fetchList()
  } catch { /* 取消 */ }
}

async function handleDelete(row: ReimbursementItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除报销单「${row.reimbursementNo}」吗？`, '删除确认', { type: 'warning' })
    await deleteReimbursement(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* 取消 */ }
}

// ---------- 审批 ----------
const approveDialogVisible = ref(false)
const approvingId = ref<number | null>(null)
const approvingNo = ref('')
const approveAction = ref<'approve' | 'reject'>('approve')
const approveComment = ref('')
const approving = ref(false)

function openApprove(row: ReimbursementItem, action: 'approve' | 'reject'): void {
  approvingId.value = row.id
  approvingNo.value = row.reimbursementNo
  approveAction.value = action
  approveComment.value = ''
  approveDialogVisible.value = true
}

async function handleApprove(): Promise<void> {
  if (!approvingId.value || !approveComment.value.trim()) {
    ElMessage.warning('请填写审批意见')
    return
  }
  approving.value = true
  try {
    await approveReimbursement(approvingId.value, { action: approveAction.value, comment: approveComment.value })
    ElMessage.success('操作成功')
    approveDialogVisible.value = false
    fetchList()
  } finally {
    approving.value = false
  }
}

// ---------- 详情抽屉（明细 + 附件 + 财务 + PDF） ----------
const drawerVisible = ref(false)
const detail = ref<ReimbursementItem | null>(null)
const detailLoading = ref(false)
const detailAttachments = ref<ReimbursementAttachmentItem[]>([])
const detailItems = ref<{ id: number; category: string; amount: number; expenseDate: string; description?: string; invoiceNumber?: string; isVatInvoice?: boolean }[]>([])

function detailItemAtts(itemId?: number): ReimbursementAttachmentItem[] {
  if (!itemId) return []
  return detailAttachments.value.filter((a) => a.itemId === itemId)
}

async function fetchDetailItems(id: number): Promise<void> {
  detailItems.value = await getReimbItems(id)
}
const attUploading = ref(false)

async function openDetail(row: ReimbursementItem): Promise<void> {
  detail.value = row
  drawerVisible.value = true
  await Promise.all([fetchAttachments(row.id), fetchDetailItems(row.id)])
}

async function fetchAttachments(id: number): Promise<void> {
  detailLoading.value = true
  try {
    detailAttachments.value = await listReimbAttachments(id)
  } finally {
    detailLoading.value = false
  }
}

async function handleUploadAtt(options: UploadRequestOptions): Promise<void> {
  if (!detail.value) return
  attUploading.value = true
  try {
    await uploadReimbAttachment(detail.value.id, options.file)
    ElMessage.success('上传成功')
    fetchAttachments(detail.value.id)
  } finally {
    attUploading.value = false
  }
}

async function handleDownloadAtt(att: ReimbursementAttachmentItem): Promise<void> {
  if (!detail.value) return
  await downloadReimbAttachment(detail.value.id, att.id, att.fileName)
}

async function handleDeleteAtt(att: ReimbursementAttachmentItem): Promise<void> {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(`确定删除附件「${att.fileName}」吗？`, '删除确认', { type: 'warning' })
    await deleteReimbAttachment(detail.value.id, att.id)
    ElMessage.success('删除成功')
    fetchAttachments(detail.value.id)
  } catch { /* 取消 */ }
}

// ---------- 财务操作 ----------
const financing = ref('')

async function handleFinance(action: 'receive-invoice' | 'mark-paid'): Promise<void> {
  if (!detail.value) return
  const label = action === 'receive-invoice' ? '标记为已收发票' : '标记为已付款'
  try {
    await ElMessageBox.confirm(`确定对该报销单${label}吗？`, '财务操作', { type: 'warning' })
    financing.value = action
    await financeReimbursement(detail.value.id, action)
    ElMessage.success('操作成功')
    // 刷新该行数据
    const fresh = (await pageReimbursements({ current: 1, size: 1, keyword: detail.value.reimbursementNo })).records[0]
    if (fresh) detail.value = fresh
    fetchList()
  } catch { /* 取消 */ }
  finally {
    financing.value = ''
  }
}

// ---------- Excel 导出 ----------
const exportRange = ref<string[]>([])
const exporting = ref(false)

async function handleExportExcel(): Promise<void> {
  exporting.value = true
  try {
    const rows = await getExportItems({
      startDate: exportRange.value?.[0],
      endDate: exportRange.value?.[1],
    })
    if (!rows.length) {
      ElMessage.warning('筛选范围内没有费用明细')
      return
    }
    const aoa = [
      ['报销编号', '申请人', '项目', '报销标题', '费用类别', '金额（元）', '费用日期', '事由说明', '发票号', '增值税发票', '单据状态', '审批人'],
      ...rows.map((r) => [
        r.reimbursementNo, r.applicantName || '', r.projectName || '', r.title,
        r.itemCategory, Number(r.itemAmount), r.itemExpenseDate, r.itemDescription || '',
        r.invoiceNumber || '', r.isVatInvoice ? '是' : '否', r.statusLabel, r.approverName || '',
      ]),
    ]
    const ws = XLSX.utils.aoa_to_sheet(aoa)
    ws['!cols'] = [{ wch: 16 }, { wch: 10 }, { wch: 18 }, { wch: 24 }, { wch: 10 }, { wch: 12 }, { wch: 12 }, { wch: 24 }, { wch: 16 }, { wch: 10 }, { wch: 10 }, { wch: 10 }]
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '费用明细')
    XLSX.writeFile(wb, `费用明细_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

// ---------- PDF 导出 ----------
const pdfTemplateRef = ref<HTMLDivElement>()
const exportingPdf = ref(false)

async function handleExportPdf(): Promise<void> {
  if (!detail.value) return
  exportingPdf.value = true
  try {
    await new Promise((r) => setTimeout(r, 100))
    const el = pdfTemplateRef.value
    if (!el) return
    const canvas = await html2canvas(el, { scale: 2, backgroundColor: '#ffffff' })
    const img = canvas.toDataURL('image/png')
    const pdf = new jsPDF('p', 'mm', 'a4')
    const pageW = 210
    const imgH = (canvas.height * (pageW - 20)) / canvas.width
    pdf.addImage(img, 'PNG', 10, 10, pageW - 20, imgH)
    pdf.save(`报销单_${detail.value.reimbursementNo}.pdf`)
    ElMessage.success('PDF 已生成')
  } finally {
    exportingPdf.value = false
  }
}

async function handleDetailRowUpload(options: UploadRequestOptions, itemId: number): Promise<void> {
  if (!detail.value) return
  attUploading.value = true
  try {
    await uploadReimbAttachment(detail.value.id, options.file, itemId)
    ElMessage.success('上传成功')
    await fetchAttachments(detail.value.id)
  } finally {
    attUploading.value = false
  }
}

onMounted(fetchList)

function makeDetailRowUploader(itemId: number) {
  return (options: UploadRequestOptions) => handleDetailRowUpload(options, itemId)
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
          <el-input v-model="query.keyword" placeholder="申请人/标题" clearable style="width: 180px; margin-left: 8px" @keyup.enter="handleSearch" />
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <div>
          <el-date-picker v-model="exportRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="导出开始" end-placeholder="导出结束" style="width: 260px; margin-right: 8px" />
          <el-button :loading="exporting" @click="handleExportExcel">导出 Excel</el-button>
          <el-button v-permission="'business:reimbursement:add'" type="primary" @click="openCreate">新建报销</el-button>
        </div>
      </div>

      <!-- 报销单表格 -->
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="reimbursementNo" label="报销编号" min-width="140" />
        <el-table-column prop="applicantName" label="申请人" width="100" />
        <el-table-column prop="title" label="标题" min-width="170" show-overflow-tooltip />
        <el-table-column label="总额（元）" min-width="110" align="right">
          <template #default="{ row }">{{ money(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagTypes[row.status]" size="small">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="财务" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isPaid" type="success" size="small">已付款</el-tag>
            <el-tag v-else-if="row.isInvoiceReceived" type="primary" size="small">已收票</el-tag>
            <span v-else style="color: #9ca3af">—</span>
          </template>
        </el-table-column>
        <el-table-column label="审批意见" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.approveComment">{{ row.approverName }}：{{ row.approveComment }}</span>
            <span v-else style="color: #9ca3af">—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="300" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 0 && isOwner(row)">
              <el-button v-permission="'business:reimbursement:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-permission="'business:reimbursement:edit'" link type="success" size="small" @click="handleSubmit(row)">提交</el-button>
              <el-button v-permission="'business:reimbursement:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-if="row.status === 3 && isOwner(row)">
              <el-button v-permission="'business:reimbursement:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-permission="'business:reimbursement:edit'" link type="success" size="small" @click="handleSubmit(row)">重新提交</el-button>
              <el-button v-permission="'business:reimbursement:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-if="row.status === 1">
              <el-button v-if="isOwner(row)" v-permission="'business:reimbursement:edit'" link type="warning" size="small" @click="handleWithdraw(row)">撤回</el-button>
              <el-button v-permission="'business:reimbursement:approve'" link type="success" size="small" @click="openApprove(row, 'approve')">批准</el-button>
              <el-button v-permission="'business:reimbursement:approve'" link type="danger" size="small" @click="openApprove(row, 'reject')">驳回</el-button>
            </template>
            <template v-if="row.status === 4">
              <el-button v-permission="'business:reimbursement:approve'" link type="success" size="small" @click="openApprove(row, 'approve')">终审批准</el-button>
              <el-button v-permission="'business:reimbursement:approve'" link type="danger" size="small" @click="openApprove(row, 'reject')">终审驳回</el-button>
            </template>
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

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑报销草稿' : '新建报销'" width="860px" top="5vh">
      <el-form :model="form" label-width="90px">
        <el-form-item label="报销标题" required>
          <el-input v-model="form.title" placeholder="如 8月差旅报销" maxlength="200" />
        </el-form-item>
        <el-form-item label="关联项目">
          <el-select v-model="form.projectId" placeholder="选择项目（可选，用于成本归集）" filterable clearable style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
      </el-form>

      <!-- 明细行编辑器 -->
      <div class="items-editor">
        <div class="items-header">
          <span class="section-title">费用明细</span>
          <el-button size="small" type="primary" plain @click="addItem">加一行</el-button>
        </div>
        <el-table :data="form.items" border size="small">
          <el-table-column label="类别" width="120">
            <template #default="{ row }">
              <el-select v-model="row.category" size="small">
                <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="金额（元）" width="140" align="right">
            <template #default="{ row }">
              <el-input-number v-model="row.amount" :min="0.01" :precision="2" :step="100" size="small" controls-position="right" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="费用日期" width="150">
            <template #default="{ row }">
              <el-date-picker v-model="row.expenseDate" type="date" value-format="YYYY-MM-DD" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="事由说明" min-width="180">
            <template #default="{ row }">
              <el-input v-model="row.description" size="small" maxlength="300" />
            </template>
          </el-table-column>
          <el-table-column label="发票号" width="130">
            <template #default="{ row }">
              <el-input v-model="row.invoiceNumber" size="small" maxlength="50" />
            </template>
          </el-table-column>
          <el-table-column label="增值税票" width="90" align="center">
            <template #default="{ row }">
              <el-checkbox v-model="row.isVatInvoice" />
            </template>
          </el-table-column>
        <el-table-column label="发票" width="90" align="center">
          <template #default="{ row }">
            <el-button v-if="row.id" link type="primary" size="small" @click="openRowAtt(row)">
              发票({{ rowCountAtts(row.id).length }})
            </el-button>
            <el-tooltip v-else content="保存草稿后可上传" placement="top">
              <span style="color: #9ca3af; font-size: 12px">未保存</span>
            </el-tooltip>
          </template>
        </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeItem($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="items-total">合计：<b>{{ itemsTotal.toFixed(2) }}</b> 元</div>
      </div>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 审批弹窗 -->
    <el-dialog v-model="approveDialogVisible" :title="`${approveAction === 'approve' ? '批准' : '驳回'} - ${approvingNo}`" width="460px">
      <el-form label-width="90px">
        <el-form-item label="审批意见" required>
          <el-input v-model="approveComment" type="textarea" :rows="3" maxlength="300" placeholder="请填写审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialogVisible = false">取消</el-button>
        <el-button :type="approveAction === 'approve' ? 'primary' : 'danger'" :loading="approving" @click="handleApprove">确定</el-button>
      </template>
    </el-dialog>

    <!-- 行级发票附件弹窗 -->
    <el-dialog v-model="rowAttVisible" title="明细发票附件" width="560px">
      <p style="margin: 0 0 8px; color: #6b7280; font-size: 13px">
        类别：{{ rowAttTarget?.category }} · 金额：{{ rowAttTarget?.amount }} 元
      </p>
      <div class="items-header">
        <span class="section-title">附件清单</span>
        <el-upload :show-file-list="false" :http-request="handleRowUpload" accept=".pdf,.jpg,.jpeg,.png">
          <el-button size="small" type="primary" plain :loading="rowAttUploading">上传发票</el-button>
        </el-upload>
      </div>
      <el-table :data="rowCountAtts(rowAttTarget?.id)" border size="small">
        <el-table-column label="file name" min-width="180">
          <template #default="{ row }">
            <AttachmentLink :file-name="row.fileName" :content-type="row.contentType" :fetch-signed-url="() => getReimbAttPreviewUrl(currentBillId, row.id)" />
          </template>
        </el-table-column>
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleRowDownload(row)">下载</el-button>
            <el-button link type="danger" size="small" @click="handleRowDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="drawerVisible" :title="detail ? `报销单 ${detail.reimbursementNo}` : '报销单'" size="620px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="申请人">{{ detail.applicantName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabels[detail.status] }}</el-descriptions-item>
          <el-descriptions-item label="标题" :span="2">{{ detail.title }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ money(detail.totalAmount) }} 元</el-descriptions-item>
          <el-descriptions-item label="财务标记">
            <el-tag v-if="detail.isPaid" type="success" size="small">已付款</el-tag>
            <el-tag v-else-if="detail.isInvoiceReceived" size="small">已收发票</el-tag>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">
            {{ detail.approveComment ? `${detail.approverName}：${detail.approveComment}` : '—' }}
          </el-descriptions-item>
        </el-descriptions>

        <!-- 财务面板 -->
        <div v-if="detail.status === 2" class="finance-panel">
          <span class="section-title">财务操作</span>
          <el-button
            v-permission="'business:reimbursement:finance'"
            size="small"
            :disabled="detail.isInvoiceReceived"
            :loading="financing === 'receive-invoice'"
            @click="handleFinance('receive-invoice')"
          >
            {{ detail.isInvoiceReceived ? '已收发票' : '标记已收发票' }}
          </el-button>
          <el-button
            v-permission="'business:reimbursement:finance'"
            size="small"
            type="success"
            :disabled="!detail.isInvoiceReceived || detail.isPaid"
            :loading="financing === 'mark-paid'"
            @click="handleFinance('mark-paid')"
          >
            {{ detail.isPaid ? '已付款' : '标记已付款' }}
          </el-button>
        </div>

        <!-- 明细清单 -->
        <div class="drawer-section">
          <div class="items-header">
            <span class="section-title">费用明细</span>
            <el-button size="small" @click="handleExportPdf" :loading="exportingPdf">导出 PDF</el-button>
          </div>
          <el-table :data="detailItems" border size="small">
            <el-table-column prop="category" label="类别" width="90" />
            <el-table-column label="金额（元）" align="right" width="110">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="expenseDate" label="费用日期" width="110" />
            <el-table-column prop="description" label="事由说明" min-width="140" show-overflow-tooltip />
            <el-table-column prop="invoiceNumber" label="发票号" width="100" />
            <el-table-column label="增值税" width="70" align="center">
              <template #default="{ row }">{{ row.isVatInvoice ? '是' : '否' }}</template>
            </el-table-column>
            <el-table-column label="发票附件" min-width="200">
              <template #default="{ row }">
                <div v-if="detailItemAtts(row.id).length" style="margin-bottom: 4px">
                  <el-button v-for="att in detailItemAtts(row.id)" :key="att.id" link type="primary" size="small" @click="handleDownloadAtt(att)">
                    {{ att.fileName }}
                  </el-button>
                </div>
                <span v-else style="color: #9ca3af; display: inline-block; margin-bottom: 4px">暂无</span>
                <el-upload
                  v-if="canMaintainDetail"
                  v-permission="'business:reimbursement:edit'"
                  :show-file-list="false"
                  :http-request="makeDetailRowUploader(row.id)"
                  accept=".pdf,.jpg,.jpeg,.png"
                >
                  <el-button size="small" type="primary" plain :loading="attUploading">上传发票</el-button>
                </el-upload>
              </template>
            </el-table-column>
          </el-table>
          <div style="text-align: right; margin-top: 8px; color: #374151">
            合计：<b>{{ money(detail?.totalAmount) }}</b> 元
          </div>
        </div>

        <!-- 发票附件 -->
        <div class="drawer-section">
          <div class="items-header">
            <span class="section-title">发票附件</span>
            <el-upload
              v-if="detail.status === 0 && isOwner(detail)"
              v-permission="'business:reimbursement:edit'"
              :show-file-list="false"
              :http-request="handleUploadAtt"
              accept=".pdf,.jpg,.jpeg,.png"
            >
              <el-button size="small" type="primary" plain :loading="attUploading">上传发票</el-button>
            </el-upload>
          </div>
          <el-table v-loading="detailLoading" :data="detailAttachments" border size="small">
            <el-table-column label="file name" min-width="180">
              <template #default="{ row }">
                <AttachmentLink :file-name="row.fileName" :content-type="row.contentType" :fetch-signed-url="() => getReimbAttPreviewUrl(currentBillId, row.id)" />
              </template>
            </el-table-column>
            <el-table-column label="大小" width="90">
              <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
            </el-table-column>
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="handleDownloadAtt(row)">下载</el-button>
                <el-button
                  v-if="canMaintainDetail"
                  v-permission="'business:reimbursement:edit'"
                  link
                  type="danger"
                  size="small"
                  @click="handleDeleteAtt(row)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- PDF 导出模板（离屏渲染） -->
        <div ref="pdfTemplateRef" class="pdf-template">
          <h2 style="text-align: center">费用报销单</h2>
          <p v-if="detail">编号：{{ detail.reimbursementNo }}</p>
          <table v-if="detail" border="1" style="width: 100%; border-collapse: collapse; font-size: 12px">
            <tbody>
              <tr><td style="width: 20%">申请人</td><td>{{ detail.applicantName }}</td><td style="width: 20%">标题</td><td>{{ detail.title }}</td></tr>
              <tr><td>总金额</td><td>{{ money(detail.totalAmount) }} 元</td><td>状态</td><td>{{ statusLabels[detail.status] }}</td></tr>
            </tbody>
          </table>
          <table v-if="detailItems.length" border="1" style="width: 100%; border-collapse: collapse; font-size: 11px; margin-top: 8px">
            <thead><tr><td>类别</td><td>金额</td><td>日期</td><td>事由</td><td>发票号</td></tr></thead>
            <tbody>
              <tr v-for="i in detailItems" :key="i.id">
                <td>{{ i.category }}</td><td>{{ money(i.amount) }}</td><td>{{ i.expenseDate }}</td><td>{{ i.description }}</td><td>{{ i.invoiceNumber || '—' }}</td>
              </tr>
            </tbody>
          </table>
          <p v-if="detail" style="text-align: right">合计：{{ money(detail.totalAmount) }} 元</p>
          <p v-if="detail && detail.approveComment">审批意见：{{ detail.approverName }}：{{ detail.approveComment }}</p>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
}

.items-editor {
  margin-top: 8px;
}

.items-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.items-total {
  text-align: right;
  margin-top: 8px;
  color: #374151;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: #1f2937;
}

.finance-panel {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 12px 0;
  padding: 10px;
  background: #f9fafb;
  border-radius: 6px;
}

.drawer-section {
  margin-top: 16px;
}

.pdf-template {
  position: fixed;
  left: -9999px;
  top: 0;
  width: 700px;
  background: #fff;
  padding: 20px;
}
</style>
