<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'

async function exportArchive(): Promise<void> {
  const resp = await request.get(`/projects/${projectId}/archive`, { responseType: 'blob' })
  const blob = (resp as unknown as { data?: Blob }).data ?? (resp as unknown as Blob)
  const url = URL.createObjectURL(blob as Blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `项目档案_${projectId}.zip`
  link.click()
  URL.revokeObjectURL(url)
}
import { useUserStore } from '@/stores/user'
import type {
  ConfirmationAttachmentItem, ConfirmationItem, InvoiceItem, ProjectItem, PageResult,
} from '@/types'
import { listConfirmationAttachments, getConfirmationAttPreviewUrl, downloadConfirmationAttachment, updateConfirmation } from '@/api/confirmation'
import { updateInvoice as updateInvoiceApi } from '@/api/invoice'
import type { ConfirmationRequest, InvoiceRequest } from '@/types'
import AttachmentLink from '@/components/AttachmentLink.vue'

/** 项目工作台：项目一览 + 直接开票 / 收款 / 处理函证 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = Number(route.params.id)

const loading = ref(false)
const wb = ref<any>(null)
const project = ref<ProjectItem | null>(null)

const statusLabels: Record<number, string> = { 0: '进行中', 1: '已完成', 2: '已归档' }

// 快捷弹窗
const invoiceVisible = ref(false)
const invoiceSaving = ref(false)
const invoiceForm = reactive({
  invoiceNo: '', type: '增值税专用发票', amount: 0, taxRate: 6 as number | undefined,
  amountExTax: undefined as number | undefined, taxAmount: undefined as number | undefined,
  invoiceDate: new Date().toISOString().slice(0, 10), invoiceItem: '', isRecharge: false,
})

const paymentVisible = ref(false)
const paymentSaving = ref(false)
const paymentForm = reactive({
  amount: 0, paymentDate: new Date().toISOString().slice(0, 10), paymentMethod: '转账', payerName: '', remark: '',
})

const confirmationVisible = ref(false)
const confirmationSaving = ref(false)
const confirmationForm = reactive({
  confirmationNo: '', type: '银行函证', confirmationMethod: '邮寄', targetUnit: '', summary: '',
})

function money(v?: number | null): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function onInvoiceItemAmountChange(): void {
  if (invoiceForm.amount == null || invoiceForm.amount <= 0) return
  if (invoiceForm.taxRate == null) {
    invoiceForm.amountExTax = invoiceForm.amount
    invoiceForm.taxAmount = 0
    return
  }
  const ex = Math.round((invoiceForm.amount / (1 + invoiceForm.taxRate / 100)) * 100) / 100
  invoiceForm.amountExTax = ex
  invoiceForm.taxAmount = Math.round((invoiceForm.amount - ex) * 100) / 100
}

async function fetchWorkbench(): Promise<void> {
  loading.value = true
  try {
    wb.value = await request.get(`/projects/${projectId}/workbench`)
  } finally {
    loading.value = false
  }
}

async function fetchProject(): Promise<void> {
  const resp: any = await request.get('/projects', { params: { current: 1, size: 50, keyword: '' } })
  const data = resp && resp.records ? resp : (resp && resp.data) || { records: [] }
  project.value = (data.records || []).find((x: ProjectItem) => x.id === projectId) || null
}

// 快捷开票
async function handleInvoiceItemSave(): Promise<void> {
  if (!invoiceForm.amount || invoiceForm.amount <= 0) { ElMessage.warning('请填写价税合计'); return }
  invoiceSaving.value = true
  try {
    await request.post('/invoices', { ...invoiceForm, contractId: (wb.value?.invoices?.[0]?.contractId) || firstContractId() })
    ElMessage.success('发票已登记（待开票）')
    invoiceVisible.value = false
    fetchWorkbench()
  } finally {
    invoiceSaving.value = false
  }
}

function firstContractId(): number | null {
  const c = wb.value?.contractIds
  return c && c.length ? c[0] : null
}

// 快捷收款（预收，挂第一个合同）
async function handlePaymentSave(): Promise<void> {
  if (!paymentForm.amount || paymentForm.amount <= 0) { ElMessage.warning('请填写收款金额'); return }
  paymentSaving.value = true
  try {
    await request.post('/payments', { ...paymentForm, contractId: firstContractId() })
    ElMessage.success('收款已登记')
    paymentVisible.value = false
    fetchWorkbench()
  } finally {
    paymentSaving.value = false
  }
}

// 快捷登记函证
async function handleConfirmationItemSave(): Promise<void> {
  if (!confirmationForm.targetUnit.trim()) { ElMessage.warning('请填写被函证单位'); return }
  confirmationSaving.value = true
  try {
    await request.post('/confirmations', { ...confirmationForm, projectId })
    ElMessage.success('函证已登记')
    confirmationVisible.value = false
    fetchWorkbench()
  } finally {
    confirmationSaving.value = false
  }
}

function openInvoiceIssue(inv: InvoiceItem): void {
  ElMessageBox.prompt(`为「${inv.invoiceNo || '待开票'}」填写/确认发票号后开票`, '开票', {
    inputValue: inv.invoiceNo || '',
  }).then(async ({ value }) => {
    if (!value || !value.trim()) { ElMessage.warning('请填写发票号'); return }
    await request.put(`/invoices/${inv.id}`, { ...inv, invoiceNo: value.trim(), id: inv.id, contractId: inv.contractId })
    await request.put(`/invoices/${inv.id}/status?action=issue&invoiceDate=${new Date().toISOString().slice(0, 10)}`)
    ElMessage.success('已开票')
    fetchWorkbench()
  }).catch(() => { /* 取消 */ })
}

const cfStatusLabels: Record<number, string> = { 0: '未发出', 1: '已发出', 2: '已回函', 3: '已作废' }

// ---------- 函证详情（物流 + 附件） ----------
const cfDetailVisible = ref(false)
const cfDetail = ref<ConfirmationItem | null>(null)
const cfAtts = ref<ConfirmationAttachmentItem[]>([])
const cfAttLoading = ref(false)

function openCfDetail(cf: ConfirmationItem): void {
  cfDetail.value = cf
  cfDetailVisible.value = true
  cfAttLoading.value = true
  listConfirmationAttachments(cf.id)
    .then((rows) => { cfAtts.value = rows })
    .finally(() => { cfAttLoading.value = false })
}

async function handleCfDownload(att: ConfirmationAttachmentItem): Promise<void> {
  if (!cfDetail.value) return
  await downloadConfirmationAttachment(cfDetail.value.id, att.id, att.fileName)
}

// ---------- 函证编辑 ----------
const canEditCf = computed(() => {
  // 与桌面端一致：有确认/编辑权限且未作废即可改
  return userStore.hasPermission('business:confirmation:edit') && cfDetail.value?.status !== 3
})
const cfEditVisible = ref(false)
const cfEditSaving = ref(false)
const cfEditForm = reactive({
  confirmationNo: '', type: '', confirmationMethod: '', targetUnit: '', summary: '',
  sendTrackingNo: '', replyTrackingNo: '', discrepancyReason: '',
})

function openCfEdit(): void {
  if (!cfDetail.value) return
  Object.assign(cfEditForm, {
    confirmationNo: cfDetail.value.confirmationNo || '',
    type: cfDetail.value.type || '其他',
    confirmationMethod: cfDetail.value.confirmationMethod || '',
    targetUnit: cfDetail.value.targetUnit || '',
    summary: cfDetail.value.summary || '',
    sendTrackingNo: cfDetail.value.sendTrackingNo || '',
    replyTrackingNo: cfDetail.value.replyTrackingNo || '',
    discrepancyReason: cfDetail.value.discrepancyReason || '',
  })
  cfEditVisible.value = true
}

async function handleCfEditSave(): Promise<void> {
  if (!cfDetail.value) return
  if (!cfEditForm.confirmationNo.trim()) { ElMessage.warning('请填写函证编号'); return }
  if (!cfEditForm.targetUnit.trim()) { ElMessage.warning('请填写被函证单位'); return }
  cfEditSaving.value = true
  try {
    await updateConfirmation({
      id: cfDetail.value.id,
      confirmationNo: cfEditForm.confirmationNo,
      type: cfEditForm.type,
      confirmationMethod: cfEditForm.confirmationMethod,
      targetUnit: cfEditForm.targetUnit,
      summary: cfEditForm.summary,
      projectId,
      sendTrackingNo: cfEditForm.sendTrackingNo,
      replyTrackingNo: cfEditForm.replyTrackingNo,
      discrepancyReason: cfEditForm.discrepancyReason,
    })
    ElMessage.success('函证已更新')
    cfEditVisible.value = false
    cfDetailVisible.value = false
    fetchWorkbench()
  } finally {
    cfEditSaving.value = false
  }
}

// ---------- 发票编辑 ----------
const canEditInv = computed(() =>
  userStore.hasPermission('business:invoice:edit') && invoiceEditTarget.value?.status !== 2)
const invEditVisible = ref(false)
const invEditSaving = ref(false)
const invoiceEditTarget = ref<InvoiceItem | null>(null)
const invEditForm = reactive({
  invoiceNo: '', type: '增值税专用发票', amount: 0, taxRate: undefined as number | undefined,
  amountExTax: undefined as number | undefined, taxAmount: undefined as number | undefined,
  invoiceDate: '', invoiceItem: '', isRecharge: false, remark: '',
})

function canEditInvRow(row: InvoiceItem): boolean {
  return userStore.hasPermission('business:invoice:edit') && row.status !== 2
}

function openInvEdit(row: InvoiceItem): void {
  invoiceEditTarget.value = row
  Object.assign(invEditForm, {
    invoiceNo: row.invoiceNo || '',
    type: row.type,
    amount: Number(row.amount),
    taxRate: row.taxRate ?? undefined,
    amountExTax: row.amountExTax != null ? Number(row.amountExTax) : undefined,
    taxAmount: row.taxAmount != null ? Number(row.taxAmount) : undefined,
    invoiceDate: row.invoiceDate || '',
    invoiceItem: row.invoiceItem || '',
    isRecharge: !!row.isRecharge,
    remark: row.remark || '',
  })
  invEditVisible.value = true
}

function onInvEditTaxChange(): void {
  const f = invEditForm
  if (f.amount == null || f.amount <= 0) return
  if (f.taxRate == null) { f.amountExTax = f.amount; f.taxAmount = 0; return }
  const ex = Math.round((f.amount / (1 + f.taxRate / 100)) * 100) / 100
  f.amountExTax = ex
  f.taxAmount = Math.round((f.amount - ex) * 100) / 100
}

function onInvEditExTaxChange(): void {
  const f = invEditForm
  if (f.amountExTax == null || f.amountExTax <= 0) return
  const tax = f.taxRate == null ? 0 : Math.round(f.amountExTax * f.taxRate) / 100
  f.taxAmount = Math.round(tax * 100) / 100
  f.amount = Math.round((f.amountExTax + tax) * 100) / 100
}

async function handleInvEditSave(): Promise<void> {
  const target = invoiceEditTarget.value
  if (!target) return
  invEditSaving.value = true
  try {
    await updateInvoiceApi(target.id, {
      id: target.id,
      contractId: target.contractId,
      invoiceNo: invEditForm.invoiceNo,
      type: invEditForm.type,
      amount: invEditForm.amount,
      taxRate: invEditForm.taxRate,
      amountExTax: invEditForm.amountExTax,
      taxAmount: invEditForm.taxAmount,
      invoiceDate: invEditForm.invoiceDate,
      invoiceItem: invEditForm.invoiceItem,
      isRecharge: invEditForm.isRecharge,
      remark: invEditForm.remark,
    })
    ElMessage.success('发票已更新')
    invEditVisible.value = false
    fetchWorkbench()
  } finally {
    invEditSaving.value = false
  }
}

/** 当前用户权限集合（供模板/逻辑使用） */
function userPerms(): Set<string> {
  return new Set(userStore.permissions || [])
}

onMounted(() => {
  fetchWorkbench()
  fetchProject()
})
</script>

<template>
  <div class="page-container" v-loading="loading">
    <!-- 项目信息 -->
    <el-card shadow="never" style="margin-bottom: 12px">
      <div class="wb-head">
        <div>
          <span class="wb-title">{{ wb?.projectName || '...' }}</span>
          <el-tag size="small" style="margin-left: 10px">{{ wb?.statusLabel === 'IN_PROGRESS' ? '进行中' : wb?.statusLabel === 'FINISHED' ? '已完成' : '已归档' }}</el-tag>
        </div>
        <el-button @click="router.push('/business/project')">返回项目列表</el-button>
        <el-button type="primary" plain @click="exportArchive">导出项目档案</el-button>
      </div>
      <el-descriptions :column="4" size="small" style="margin-top: 8px">
        <el-descriptions-item label="项目编号">{{ wb?.projectNo }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ wb?.clientName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="项目负责人">{{ wb?.managerName || '—' }}</el-descriptions-item>
        <el-descriptions-item label="项目期间">{{ wb?.startDate || '—' }} ~ {{ wb?.endDate || '—' }}</el-descriptions-item>
      </el-descriptions>
      <div class="wb-actions">
        <el-button v-permission="'business:invoice:add'" type="primary" size="small" @click="invoiceVisible = true">开票登记</el-button>
        <el-button v-permission="'business:collection:add'" type="success" size="small" @click="paymentVisible = true">登记收款</el-button>
        <el-button v-permission="'business:confirmation:add'" size="small" @click="confirmationVisible = true">登记函证</el-button>
        <el-button size="small" @click="router.push('/business/vendor')">对公付款</el-button>
      </div>
    </el-card>

    <!-- 指标 -->
    <el-row :gutter="12" style="margin-bottom: 12px">
      <el-col :xs="12" :sm="8" :lg="4"><el-card shadow="never" class="stat"><div class="num">{{ money(wb?.contractAmount) }}</div><div class="lbl">合同总额</div></el-card></el-col>
      <el-col :xs="12" :sm="8" :lg="4"><el-card shadow="never" class="stat"><div class="num" style="color: #2563eb">{{ money(wb?.totalCollected) }}</div><div class="lbl">收入（不含税）</div></el-card></el-col>
      <el-col :xs="12" :sm="8" :lg="4"><el-card shadow="never" class="stat"><div class="num" style="color: #e6a23c">{{ money(wb?.expenseCost) }}</div><div class="lbl">直接成本</div></el-card></el-col>
      <el-col :xs="12" :sm="8" :lg="4"><el-card shadow="never" class="stat"><div class="num" style="color: #e6a23c">{{ money(wb?.laborCost) }}</div><div class="lbl">人工合计</div></el-card></el-col>
      <el-col :xs="12" :sm="8" :lg="4"><el-card shadow="never" class="stat"><div class="num" :style="{ color: Number(wb?.grossProfit) >= 0 ? '#67c23a' : '#f56c6c' }">{{ money(wb?.grossProfit) }}</div><div class="lbl">毛利</div></el-card></el-col>
      <el-col :xs="12" :sm="8" :lg="4"><el-card shadow="never" class="stat"><div class="num">{{ Number(wb?.actualHours || 0).toFixed(1) }}<span style="font-size: 12px; color: #9ca3af"> / {{ Number(wb?.budgetHours || 0).toFixed(1) }}h</span></div><div class="lbl">实际 / 预算工时</div></el-card></el-col>
    </el-row>

    <el-row :gutter="12">
      <!-- 左列 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header><span>预算工时（按级别）</span></template>
          <el-table :data="wb?.budgetLines || []" border size="small">
            <el-table-column prop="levelName" label="级别" min-width="90" />
            <el-table-column prop="headcount" label="人数" width="60" align="right" />
            <el-table-column prop="hoursPerPerson" label="每人工时" width="90" align="right" />
            <el-table-column label="预算（h）" width="90" align="right">
              <template #default="{ row }">{{ Number(row.totalHours).toFixed(1) }}</template>
            </el-table-column>
            <el-table-column label="实际（h）" width="90" align="right">
              <template #default="{ row }">{{ Number(row.actualHours).toFixed(1) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header><span>发票</span></template>
          <el-table :data="wb?.invoices || []" border size="small">
            <el-table-column label="发票号" min-width="110">
              <template #default="{ row }">{{ row.invoiceNo || '待开票' }}</template>
            </el-table-column>
            <el-table-column label="价税合计" width="100" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column label="已收" width="100" align="right">
              <template #default="{ row }">{{ money(row.collectedAmount) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" v-permission="'business:invoice:status'" link type="success" size="small" @click="openInvoiceIssue(row)">开票</el-button>
                <el-button v-if="canEditInvRow(row)" link type="primary" size="small" @click="openInvEdit(row)">编辑</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header><span>收款记录</span></template>
          <el-table :data="wb?.payments || []" border size="small">
            <el-table-column prop="paymentDate" label="日期" width="110" />
            <el-table-column label="金额" width="110" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="paymentMethod" label="方式" width="80" />
            <el-table-column label="不含税" width="110" align="right">
              <template #default="{ row }">{{ money(row.amountExTax) }}</template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右列 -->
      <el-col :xs="24" :lg="12">
        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header><span>函证</span></template>
          <el-table :data="wb?.confirmations || []" border size="small">
            <el-table-column prop="confirmationNo" label="编号" min-width="110" />
            <el-table-column prop="targetUnit" label="被函证单位" min-width="130" show-overflow-tooltip />
            <el-table-column label="状态" width="80" align="center">
              <template #default="{ row }">{{ cfStatusLabels[row.status] }}</template>
            </el-table-column>
            <el-table-column prop="sentDate" label="发出" width="100" />
            <el-table-column label="操作" width="70">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="openCfDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" style="margin-bottom: 12px">
          <template #header><span>已批准报销（计入成本）</span></template>
          <el-table :data="wb?.reimbursements || []" border size="small" max-height="300">
            <el-table-column prop="reimbursementNo" label="报销单" min-width="130" />
            <el-table-column prop="category" label="类别" width="80" />
            <el-table-column label="不含税" width="100" align="right">
              <template #default="{ row }">{{ money(row.amountExTax) }}</template>
            </el-table-column>
            <el-table-column prop="description" label="事由" min-width="120" show-overflow-tooltip />
          </el-table>
        </el-card>

        <el-card shadow="never">
          <template #header><span>对公付款（已批准/已付款）</span></template>
          <el-table :data="wb?.vendorPayments || []" border size="small" max-height="300">
            <el-table-column prop="paymentNo" label="编号" min-width="120" />
            <el-table-column prop="vendorName" label="供应商" min-width="120" show-overflow-tooltip />
            <el-table-column label="不含税" width="100" align="right">
              <template #default="{ row }">{{ money(row.amountExTax) }}</template>
            </el-table-column>
            <el-table-column prop="paymentDate" label="日期" width="100" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷开票 -->
    <el-dialog v-model="invoiceVisible" title="开票登记" width="520px">
      <el-form label-width="100px">
        <el-form-item label="发票号"><el-input v-model="invoiceForm.invoiceNo" placeholder="可留空，待开票" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="invoiceForm.type" style="width: 220px">
            <el-option label="增值税专用发票" value="增值税专用发票" />
            <el-option label="增值税普通发票" value="增值税普通发票" />
          </el-select>
        </el-form-item>
        <el-form-item label="价税合计" required>
          <el-input-number v-model="invoiceForm.amount" :min="0" :precision="2" :controls="false" style="width: 220px" @change="onInvoiceItemAmountChange" />
        </el-form-item>
        <el-form-item label="税率（%）">
          <el-select v-model="invoiceForm.taxRate" clearable allow-create filterable style="width: 220px" @change="onInvoiceItemAmountChange">
            <el-option v-for="r in [13, 9, 6, 3, 1.5, 0]" :key="r" :label="r + '%'" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票品名"><el-input v-model="invoiceForm.invoiceItem" maxlength="100" /></el-form-item>
        <el-form-item label="垫付">
          <el-checkbox v-model="invoiceForm.isRecharge">向客户收取的代垫费用</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invoiceVisible = false">取消</el-button>
        <el-button type="primary" :loading="invoiceSaving" @click="handleInvoiceItemSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 快捷收款 -->
    <el-dialog v-model="paymentVisible" title="登记收款（预收，可后核销到发票）" width="480px">
      <el-form label-width="90px">
        <el-form-item label="金额" required>
          <el-input-number v-model="paymentForm.amount" :min="0" :precision="2" :controls="false" style="width: 220px" />
        </el-form-item>
        <el-form-item label="日期" required>
          <el-date-picker v-model="paymentForm.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
        </el-form-item>
        <el-form-item label="方式">
          <el-select v-model="paymentForm.paymentMethod" style="width: 220px">
            <el-option label="转账" value="转账" /><el-option label="现金" value="现金" />
            <el-option label="支票" value="支票" /><el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款方"><el-input v-model="paymentForm.payerName" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paymentVisible = false">取消</el-button>
        <el-button type="primary" :loading="paymentSaving" @click="handlePaymentSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 快捷函证 -->
    <el-dialog v-model="confirmationVisible" title="登记函证" width="480px">
      <el-form label-width="100px">
        <el-form-item label="编号" required><el-input v-model="confirmationForm.confirmationNo" maxlength="50" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="confirmationForm.type" style="width: 220px">
            <el-option label="银行函证" value="银行函证" /><el-option label="往来款函证" value="往来款函证" /><el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="函证单位" required><el-input v-model="confirmationForm.targetUnit" maxlength="200" /></el-form-item>
        <el-form-item label="函证内容" required><el-input v-model="confirmationForm.summary" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="confirmationVisible = false">取消</el-button>
        <el-button type="primary" :loading="confirmationSaving" @click="handleConfirmationItemSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 函证详情（物流 + 附件） -->
    <el-drawer v-model="cfDetailVisible" :title="cfDetail ? `函证 ${cfDetail.confirmationNo}` : '函证详情'" size="520px">
      <template v-if="cfDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="被函证单位" :span="2">{{ cfDetail.targetUnit }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ cfDetail.type }}</el-descriptions-item>
          <el-descriptions-item label="方式">{{ cfDetail.confirmationMethod || '—' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ cfStatusLabels[cfDetail.status] }}</el-descriptions-item>
          <el-descriptions-item label="函证内容" :span="2">{{ cfDetail.summary || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发出日期">{{ cfDetail.sentDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="发出快递">{{ cfDetail.sendTrackingNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="回函日期">{{ cfDetail.confirmedDate || '—' }}</el-descriptions-item>
          <el-descriptions-item label="回函快递">{{ cfDetail.replyTrackingNo || '—' }}</el-descriptions-item>
          <el-descriptions-item label="差异原因" :span="2">{{ cfDetail.discrepancyReason || '—' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="canEditCf" style="margin-top: 10px">
          <el-button type="primary" size="small" @click="openCfEdit">编辑函证</el-button>
        </div>
        <div class="items-header" style="margin-top: 14px">
          <span class="section-title">附件（{{ cfAtts.length }}）</span>
        </div>
        <el-table v-loading="cfAttLoading" :data="cfAtts" border size="small">
          <el-table-column label="文件名" min-width="220">
            <template #default="{ row }">
              <AttachmentLink
                :file-name="row.fileName"
                :content-type="row.contentType"
                :fetch-signed-url="() => getConfirmationAttPreviewUrl(cfDetail!.id, row.id)"
              />
            </template>
          </el-table-column>
          <el-table-column label="大小" width="90">
            <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="handleCfDownload(row)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-alert
          v-if="cfDetail.replyMatched === false"
          type="warning" :closable="false" show-icon style="margin-top: 10px"
          title="回函不符" :description="cfDetail.discrepancyReason || '回函与账面存在差异，请查看差异原因'" />
      </template>
    </el-drawer>

    <!-- 函证编辑 -->
    <el-dialog v-model="cfEditVisible" title="编辑函证" width="520px">
      <el-form label-width="100px">
        <el-form-item label="编号" required><el-input v-model="cfEditForm.confirmationNo" maxlength="50" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="cfEditForm.type" style="width: 220px">
            <el-option label="银行函证" value="银行函证" /><el-option label="往来款函证" value="往来款函证" /><el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="方式">
          <el-select v-model="cfEditForm.confirmationMethod" clearable style="width: 220px">
            <el-option label="邮寄" value="邮寄" /><el-option label="电子" value="电子" /><el-option label="跟函" value="跟函" /><el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="单位" required><el-input v-model="cfEditForm.targetUnit" maxlength="200" /></el-form-item>
        <el-form-item label="内容" required><el-input v-model="cfEditForm.summary" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="发出快递"><el-input v-model="cfEditForm.sendTrackingNo" maxlength="50" /></el-form-item>
        <el-form-item label="回函快递"><el-input v-model="cfEditForm.replyTrackingNo" maxlength="50" /></el-form-item>
        <el-form-item label="差异原因"><el-input v-model="cfEditForm.discrepancyReason" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cfEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="cfEditSaving" @click="handleCfEditSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 发票编辑 -->
    <el-dialog v-model="invEditVisible" title="编辑发票" width="520px">
      <el-form label-width="100px">
        <el-form-item label="发票号"><el-input v-model="invEditForm.invoiceNo" maxlength="50" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="invEditForm.type" style="width: 220px">
            <el-option label="增值税专用发票" value="增值税专用发票" /><el-option label="增值税普通发票" value="增值税普通发票" />
          </el-select>
        </el-form-item>
        <el-form-item label="价税合计" required>
          <el-input-number v-model="invEditForm.amount" :min="0" :precision="2" :controls="false" style="width: 220px" />
        </el-form-item>
        <el-form-item label="税率（%）">
          <el-select v-model="invEditForm.taxRate" clearable allow-create filterable style="width: 220px" @change="onInvEditTaxChange">
            <el-option v-for="r in [13, 9, 6, 3, 1.5, 0]" :key="r" :label="r + '%'" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="不含税">
          <el-input-number v-model="invEditForm.amountExTax" :min="0" :precision="2" :controls="false" style="width: 220px" @change="onInvEditExTaxChange" />
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="invEditForm.taxAmount" :min="0" :precision="2" :controls="false" style="width: 220px" />
        </el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker v-model="invEditForm.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 220px" />
        </el-form-item>
        <el-form-item label="发票品名"><el-input v-model="invEditForm.invoiceItem" maxlength="100" /></el-form-item>
        <el-form-item label="垫付">
          <el-checkbox v-model="invEditForm.isRecharge">向客户收取的代垫费用</el-checkbox>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="invEditForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="invEditVisible = false">取消</el-button>
        <el-button type="primary" :loading="invEditSaving" @click="handleInvEditSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.wb-head { display: flex; justify-content: space-between; align-items: center; }
.wb-title { font-size: 17px; font-weight: 600; }
.wb-actions { margin-top: 10px; display: flex; gap: 8px; }
.stat .num { font-size: 20px; font-weight: 600; color: #1f2937; }
.stat .lbl { font-size: 12px; color: #9ca3af; margin-top: 2px; }
</style>
