<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import CaptureUpload from '@/components/CaptureUpload.vue'
import {
  pageVendorPayments, createVendorPayment, updateVendorPayment, submitVendorPayment,
  withdrawVendorPayment, deleteVendorPayment, approveVendorPayment, markVendorPaid,
  listVendorAttachments, uploadVendorAttachment, deleteVendorAttachment, downloadVendorAttachment,
} from '@/api/vendor'
import type { VendorAttachmentItem, VendorPaymentItem } from '@/api/vendor'
import { projectOptions as projectOptionsApi } from '@/api/project'
import type { ProjectItem } from '@/types'
import { useUserStore } from '@/stores/user'
import { compressImage } from '@/utils/imageCompress'

const userStore = useUserStore()

const statusLabels: Record<number, string> = { 0: '草稿', 1: '待审批', 2: '已批准', 3: '已驳回', 4: '已付款' }
const statusTagTypes: Record<number, 'info' | 'warning' | 'success' | 'danger' | 'primary'> = {
  0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'primary',
}

const canAdd = computed(() => userStore.hasPermission('business:vendor:add'))
const canEdit = computed(() => userStore.hasPermission('business:vendor:edit'))
const canDelete = computed(() => userStore.hasPermission('business:vendor:delete'))
const canApprove = computed(() => userStore.hasPermission('business:vendor:approve'))

const loading = ref(false)
const records = ref<VendorPaymentItem[]>([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, status: undefined as number | undefined, keyword: '' })

const projectOptions = ref<ProjectItem[]>([])

function money(v?: number | null): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageVendorPayments(query)
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

// ---------- 登记/编辑 ----------
const TAX_RATE_PRESETS = [13, 9, 6, 3, 1.5, 0]
const dialogVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const form = reactive({
  vendorName: '',
  summary: '',
  projectId: undefined as number | undefined,
  amount: 0,
  taxRate: undefined as number | undefined,
  taxAmount: undefined as number | undefined,
  amountExTax: undefined as number | undefined,
  paymentDate: new Date().toISOString().slice(0, 10),
  paymentMethod: '转账',
  invoiceNo: '',
  remark: '',
})

function onTaxRateChange(): void {
  if (form.amount == null || form.amount <= 0) return
  if (form.taxRate == null) {
    form.amountExTax = form.amount
    form.taxAmount = 0
    return
  }
  const ex = Math.round((form.amount / (1 + form.taxRate / 100)) * 100) / 100
  form.amountExTax = ex
  form.taxAmount = Math.round((form.amount - ex) * 100) / 100
}

function openCreate(): void {
  Object.assign(form, {
    vendorName: '', summary: '', projectId: undefined, amount: 0,
    taxRate: undefined, taxAmount: undefined, amountExTax: undefined,
    paymentDate: new Date().toISOString().slice(0, 10), paymentMethod: '转账', invoiceNo: '', remark: '',
  })
  editingId.value = null
  dialogVisible.value = true
}

function openEdit(row: VendorPaymentItem): void {
  Object.assign(form, {
    vendorName: row.vendorName,
    summary: row.summary || '',
    projectId: row.projectId ?? undefined,
    amount: Number(row.amount),
    taxRate: row.taxRate ?? undefined,
    taxAmount: row.taxAmount ?? undefined,
    amountExTax: row.amountExTax ?? undefined,
    paymentDate: row.paymentDate || new Date().toISOString().slice(0, 10),
    paymentMethod: row.paymentMethod || '转账',
    invoiceNo: row.invoiceNo || '',
    remark: '',
  })
  editingId.value = row.id
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.vendorName.trim()) { ElMessage.warning('请填写供应商'); return }
  if (!form.amount || form.amount <= 0) { ElMessage.warning('请填写付款金额'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateVendorPayment({ ...form, id: editingId.value })
      ElMessage.success('付款单已更新')
    } else {
      await createVendorPayment({ ...form })
      ElMessage.success('付款单已登记，提交审批后进入付款流程')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

// ---------- 生命周期 ----------
async function handleSubmit(row: VendorPaymentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`提交付款单「${row.paymentNo}」进入审批？`, '提交确认', { type: 'warning' })
  } catch { return }
  await submitVendorPayment(row.id)
  ElMessage.success('已提交')
  fetchList()
}

async function handleWithdraw(row: VendorPaymentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`撤回付款单「${row.paymentNo}」回到草稿？`, '撤回确认', { type: 'warning' })
  } catch { return }
  await withdrawVendorPayment(row.id)
  ElMessage.success('已撤回')
  fetchList()
}

async function handleDelete(row: VendorPaymentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除付款单「${row.paymentNo}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteVendorPayment(row.id)
  ElMessage.success('已删除')
  fetchList()
}

async function handleApprove(row: VendorPaymentItem, action: 'approve' | 'reject'): Promise<void> {
  let comment = ''
  try {
    const input = await ElMessageBox.prompt(
      `${action === 'approve' ? '批准' : '驳回'}付款单「${row.paymentNo}」（${money(row.amount)} 元），可填写意见`,
      action === 'approve' ? '批准确认' : '驳回确认',
      { inputPlaceholder: '审批意见（可空）', confirmButtonText: action === 'approve' ? '批准' : '驳回' },
    )
    comment = input.value || ''
  } catch { return }
  await approveVendorPayment(row.id, action, comment)
  ElMessage.success(action === 'approve' ? '已批准' : '已驳回')
  fetchList()
}

async function handleMarkPaid(row: VendorPaymentItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确认付款单「${row.paymentNo}」已完成付款（${money(row.amount)} 元）？`, '付款确认', { type: 'warning' })
  } catch { return }
  await markVendorPaid(row.id)
  ElMessage.success('已标记付款')
  fetchList()
}

// ---------- 详情 + 附件 ----------
const drawerVisible = ref(false)
const detail = ref<VendorPaymentItem | null>(null)
const attachments = ref<VendorAttachmentItem[]>([])
const attLoading = ref(false)
const attUploading = ref(false)
const currentBillId = computed(() => detail.value?.id || 0)

async function openDetail(row: VendorPaymentItem): Promise<void> {
  detail.value = row
  drawerVisible.value = true
  attLoading.value = true
  try {
    attachments.value = await listVendorAttachments(row.id)
  } finally {
    attLoading.value = false
  }
}

async function handleUpload(files: File[]): Promise<void> {
  if (!detail.value || !files.length) return
  attUploading.value = true
  try {
    for (const file of files) await uploadVendorAttachment(detail.value.id, file)
    ElMessage.success(`已上传 ${files.length} 个附件`)
    attachments.value = await listVendorAttachments(detail.value.id)
  } finally {
    attUploading.value = false
  }
}

async function handleDeleteAtt(att: VendorAttachmentItem): Promise<void> {
  if (!detail.value) return
  try {
    await ElMessageBox.confirm(`确定删除附件「${att.fileName}」吗？`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteVendorAttachment(detail.value.id, att.id)
  ElMessage.success('已删除')
  attachments.value = await listVendorAttachments(detail.value.id)
}

async function handleDownload(att: VendorAttachmentItem): Promise<void> {
  if (!detail.value) return
  await downloadVendorAttachment(detail.value.id, att.id, att.fileName)
}

onMounted(async () => {
  fetchList()
  try {
    projectOptions.value = await projectOptionsApi()
  } catch { /* 项目选项失败不影响列表 */ }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px">
            <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="Number(code)" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="编号/供应商/摘要/发票号" clearable style="width: 200px; margin-left: 8px" @keyup.enter="handleSearch" />
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <div>
          <el-button v-if="canAdd" type="primary" @click="openCreate">登记付款</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="paymentNo" label="付款编号" min-width="140" />
        <el-table-column prop="vendorName" label="供应商" min-width="140" show-overflow-tooltip />
        <el-table-column prop="summary" label="摘要" min-width="150" show-overflow-tooltip />
        <el-table-column label="金额（元）" min-width="110" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="paymentDate" label="付款日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagTypes[row.status]" size="small">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="归集项目" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="creatorName" label="登记人" width="90" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <template v-if="row.status === 0 || row.status === 3">
              <el-button v-if="canEdit" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
              <el-button v-if="canEdit" link type="success" size="small" @click="handleSubmit(row)">提交</el-button>
              <el-button v-if="canDelete" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
            </template>
            <template v-else-if="row.status === 1">
              <el-button v-if="canApprove" link type="success" size="small" @click="handleApprove(row, 'approve')">批准</el-button>
              <el-button v-if="canApprove" link type="danger" size="small" @click="handleApprove(row, 'reject')">驳回</el-button>
              <el-button v-if="canEdit" link type="warning" size="small" @click="handleWithdraw(row)">撤回</el-button>
            </template>
            <template v-else-if="row.status === 2">
              <el-button v-if="canApprove" link type="success" size="small" @click="handleMarkPaid(row)">标记已付款</el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="query.current"
          :page-size="query.size"
          :total="total"
          layout="total, prev, pager, next"
          @current-change="fetchList"
        />
      </div>
    </el-card>

    <!-- 登记/编辑付款 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑付款' : '登记付款'" width="560px">
      <el-form label-width="110px">
        <el-form-item label="供应商" required>
          <el-input v-model="form.vendorName" maxlength="200" placeholder="收款方名称" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" maxlength="300" placeholder="付款事由，如 购买办公用品" />
        </el-form-item>
        <el-form-item label="付款金额" required>
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false" style="width: 220px" @change="onTaxRateChange" />
        </el-form-item>
        <el-form-item label="税率（%）">
          <el-select v-model="form.taxRate" clearable filterable allow-create placeholder="可选，专票填税率" style="width: 220px" @change="onTaxRateChange">
            <el-option v-for="r in TAX_RATE_PRESETS" :key="r" :label="r + '%'" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="不含税 / 税额">
          <div style="display: flex; gap: 8px">
            <el-input-number v-model="form.amountExTax" :min="0" :precision="2" :controls="false" style="width: 130px" />
            <el-input-number v-model="form.taxAmount" :min="0" :precision="2" :controls="false" style="width: 130px" />
          </div>
        </el-form-item>
        <el-form-item label="付款日期" required>
          <el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
        </el-form-item>
        <el-form-item label="付款方式">
          <el-select v-model="form.paymentMethod" style="width: 220px">
            <el-option label="转账" value="转账" />
            <el-option label="现金" value="现金" />
            <el-option label="支票" value="支票" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商发票号">
          <el-input v-model="form.invoiceNo" maxlength="50" placeholder="可选" />
        </el-form-item>
        <el-form-item label="归集项目">
          <el-select v-model="form.projectId" clearable filterable placeholder="可选，计入项目成本" style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存草稿</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉（含附件） -->
    <el-drawer v-model="drawerVisible" :title="detail ? `付款单 ${detail.paymentNo}` : '付款单'" size="560px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="供应商" :span="2">{{ detail.vendorName }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabels[detail.status] }}</el-descriptions-item>
          <el-descriptions-item label="付款日期">{{ detail.paymentDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="金额">{{ money(detail.amount) }} 元</el-descriptions-item>
          <el-descriptions-item label="付款方式">{{ detail.paymentMethod || '—' }}</el-descriptions-item>
          <el-descriptions-item label="不含税 / 税额">{{ money(detail.amountExTax) }} / {{ money(detail.taxAmount) }}</el-descriptions-item>
          <el-descriptions-item label="供应商发票号">{{ detail.invoiceNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="归集项目" :span="2">{{ detail.projectName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="摘要" :span="2">{{ detail.summary || '—' }}</el-descriptions-item>
          <el-descriptions-item label="审批意见" :span="2">
            {{ detail.approveComment ? `${detail.approverName}：${detail.approveComment}` : '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="登记人">{{ detail.creatorName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="付款确认人">{{ detail.paidBy || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div class="items-header" style="margin-top: 14px">
          <span class="section-title">附件（发票扫描件 / 付款凭证）</span>
          <div v-if="canEdit && (detail.status === 0 || detail.status === 3)">
            <CaptureUpload :uploading="attUploading" text="选择文件" @pick="handleUpload" />
          </div>
        </div>
        <el-table v-loading="attLoading" :data="attachments" border size="small">
          <el-table-column label="文件名" min-width="200">
            <template #default="{ row }">{{ row.fileName }}</template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
          </el-table-column>
          <el-table-column label="操作" width="130">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleDownload(row)">下载</el-button>
              <el-button v-if="canEdit && detail.status === 0" link type="danger" size="small" @click="handleDeleteAtt(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.stat-row { margin-bottom: 0; }
</style>
