<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import {
  pageInvoices,
  getInvoiceAging,
  createInvoice,
  updateInvoice,
  deleteInvoice,
  changeInvoiceStatus,
  listInvoiceAttachments,
  uploadInvoiceAttachment,
  downloadInvoiceAttachment,
  deleteInvoiceAttachment,
  getInvoiceAttPreviewUrl,
} from '@/api/invoice'
import { getContractOptions } from '@/api/contract'
import { getBocRates } from '@/api/exchangeRate'
import * as XLSX from 'xlsx'
import AttachmentLink from '@/components/AttachmentLink.vue'
import type { InvoiceAgingItem,  ContractOptionItem, InvoiceAttachmentItem, InvoiceItem, InvoiceRequest } from '@/types'
import { restoreQuery, saveQuery } from '@/utils/queryCache'

const invoiceTypes = ['增值税专用发票', '增值税普通发票']
const currencyNames: Record<string, string> = {
  美元: '美元', 日元: '日元', 欧元: '欧元', 港币: '港币', 英镑: '英镑',
}
const currencies = ['人民币', ...Object.keys(currencyNames)]
const rateInfo = ref<{ bocRate: string; publishTime: string; spotBuy: string } | null>(null)
const rateLoading = ref(false)
const statusLabels: Record<number, string> = { 0: '待开票', 1: '已开票', 2: '已作废' }
const statusTagTypes: Record<number, 'info' | 'success' | 'danger'> = {
  0: 'info', 1: 'success', 2: 'danger',
}

function money(v?: number): string {
  if (v === undefined || v === null) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

// ---------- 列表 ----------
const loading = ref(false)
const records = ref<InvoiceItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1, size: 10,
  keyword: '', type: '', status: undefined as number | undefined,
})
restoreQuery('invoice', query)
watch(query, () => saveQuery('invoice', query), { deep: true })

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageInvoices({ ...query, type: query.type || undefined })
    records.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

function handleSearch(): void { query.current = 1; fetchList() }
function handleReset(): void {
  query.keyword = ''; query.type = ''; query.status = undefined; handleSearch()
}

// ---------- 登记/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<InvoiceRequest>({
  contractId: 0, invoiceNo: '', type: '增值税专用发票',
  amount: 0, taxRate: undefined, amountExTax: undefined, taxAmount: undefined,
  currency: '人民币', foreignAmount: undefined, exchangeRate: undefined, ratePublishTime: undefined,
  invoiceItem: '', taxCode: '', taxClass: '', invoiceDate: '', remark: '', isRecharge: false,
})
/** 非草稿合同选项（带项目/客户/开票信息） */
const contractOptions = ref<ContractOptionItem[]>([])

// ---------- 应收账龄 ----------
const agingVisible = ref(false)
const agingLoading = ref(false)
const agingItems = ref<InvoiceAgingItem[]>([])

async function loadAging(): Promise<void> {
  agingLoading.value = true
  try {
    agingItems.value = await getInvoiceAging()
  } finally { agingLoading.value = false }
}

const agingBuckets = computed(() => {
  const order = ['0-30', '31-60', '61-90', '90+']
  return order.map((key) => {
    const rows = agingItems.value.filter((r) => r.bucket === key)
    return {
      key,
      count: rows.length,
      total: rows.reduce((sum, r) => sum + Number(r.outstanding || 0), 0),
    }
  })
})

function openAging(): void {
  agingVisible.value = true
  loadAging()
}
const selectedContract = computed(() =>
  contractOptions.value.find((c) => c.id === form.contractId))

// 切换合同时按业务类型字典带出发票品名/税收编码/税收分类；境外客户默认外币
watch(selectedContract, (c) => {
  form.invoiceItem = c?.invoiceItem || ''
  form.taxCode = c?.taxCode || ''
  form.taxClass = c?.taxClass || ''
  if (c?.clientType === 'overseas' && form.currency === '人民币' && !isEdit.value) {
    form.currency = '美元'
  }
})

const round2 = (v: number): number => Math.round((v + Number.EPSILON) * 100) / 100

const isFx = computed(() => !!form.currency && form.currency !== '人民币')

/** 币种变化：外币自动获取中国银行牌价 */
watch(() => form.currency, async (currency) => {
  if (!currency || currency === '人民币') {
    rateInfo.value = null
    form.exchangeRate = undefined
    form.ratePublishTime = undefined
    return
  }
  await fetchRate(currency)
})

async function fetchRate(currency: string): Promise<void> {
  const name = currencyNames[currency]
  if (!name) return
  rateLoading.value = true
  try {
    const rows = await getBocRates(name)
    if (rows.length) {
      rateInfo.value = { bocRate: rows[0].bocRate, publishTime: rows[0].publishTime, spotBuy: rows[0].spotBuy }
      form.exchangeRate = Number(rows[0].bocRate)
      form.ratePublishTime = rows[0].publishTime
      deriveFromForeign()
    }
  } finally {
    rateLoading.value = false
  }
}

/** 外币折算：价税合计（元） = 外币金额 ÷ 100 × 中行牌价 */
function deriveFromForeign(): void {
  if (!isFx.value || !form.foreignAmount || !form.exchangeRate) return
  form.amount = round2((form.foreignAmount / 100) * form.exchangeRate)
  deriveByRate()
}

/** 按税率从价税合计反拆不含税/税额 */
function deriveByRate(): void {
  if (!form.amount || form.amount <= 0) return
  if (form.taxRate == null) {
    form.amountExTax = form.amount
    form.taxAmount = 0
    return
  }
  const ex = round2(form.amount / (1 + form.taxRate / 100))
  form.amountExTax = ex
  form.taxAmount = round2(form.amount - ex)
}

// 人民币模式：以不含税金额为主输入（外币模式由外币金额×牌价折算，见 deriveFromForeign）
watch([() => form.amountExTax, () => form.taxRate], ([ex, rate]) => {
  if (isFx.value) return
  if (ex == null || ex <= 0) return
  const tax = rate == null ? 0 : round2((ex * rate) / 100)
  form.taxAmount = tax
  form.amount = round2(ex + tax)
})

// 外币模式：外币金额/汇率/税率变化时重算
watch([() => form.foreignAmount, () => form.exchangeRate, () => form.taxRate], () => {
  if (!isFx.value) return
  deriveFromForeign()
})

function onAmountChange(): void {
  // 手动改价税合计：税额 = 价税合计 − 不含税
  if (form.amount != null && form.amountExTax != null) {
    form.taxAmount = round2(form.amount - form.amountExTax)
  }
}

function onTaxAmountChange(): void {
  // 手动改税额：价税合计 = 不含税 + 税额
  if (form.amountExTax != null && form.taxAmount != null) {
    form.amount = round2(form.amountExTax + form.taxAmount)
  }
}

async function loadContractOptions(): Promise<void> {
  contractOptions.value = await getContractOptions()
}

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, {
    contractId: 0, invoiceNo: '', type: '增值税专用发票',
    amount: 0, taxRate: undefined, amountExTax: undefined, taxAmount: undefined, isRecharge: false,
    currency: '人民币', foreignAmount: undefined, exchangeRate: undefined, ratePublishTime: undefined,
    invoiceItem: '', taxCode: '', taxClass: '', invoiceDate: '', remark: '',
  })
  loadContractOptions()
  dialogVisible.value = true
}

function openEdit(row: InvoiceItem): void {
  isEdit.value = true
  Object.assign(form, {
    id: row.id, contractId: row.contractId, invoiceNo: row.invoiceNo,
    type: row.type, amount: row.amount, taxRate: row.taxRate,
    amountExTax: row.amountExTax, taxAmount: row.taxAmount,
    currency: row.currency || '人民币', foreignAmount: row.foreignAmount,
    exchangeRate: row.exchangeRate, ratePublishTime: row.ratePublishTime || '',
    invoiceItem: row.invoiceItem || '', taxCode: row.taxCode || '', taxClass: row.taxClass || '',
    invoiceDate: row.invoiceDate || '', remark: row.remark || '',
    isRecharge: !!row.isRecharge,
  })
  loadContractOptions()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.contractId) {
    ElMessage.warning('请选择所属合同')
    return
  }
  saving.value = true
  try {
    const payload = {
      ...form,
      taxRate: form.taxRate || undefined,
      amountExTax: form.amountExTax || undefined,
      taxAmount: form.taxAmount || undefined,
      invoiceDate: form.invoiceDate || undefined,
    }
    if (isEdit.value && form.id) {
      await updateInvoice(form.id, payload)
      ElMessage.success('修改成功')
    } else {
      await createInvoice(payload)
      ElMessage.success('登记成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally { saving.value = false }
}

// ---------- 删除/状态流转 ----------
async function handleDelete(row: InvoiceItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除发票「${row.invoiceNo}」吗？`, '删除确认', { type: 'warning' })
    await deleteInvoice(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* cancel */ }
}

const transitDialogVisible = ref(false)
const transitId = ref<number | null>(null)
const transitAction = ref<'issue' | 'void'>('issue')
const transitDate = ref('')
const transiting = ref(false)

function openTransit(row: InvoiceItem, action: 'issue' | 'void'): void {
  if (action === 'issue' && !row.invoiceNo) {
    ElMessage.warning('该发票尚未填写发票号码，请先编辑补充再开票')
    return
  }
  transitId.value = row.id
  transitAction.value = action
  transitDate.value = action === 'issue' ? (row.invoiceDate || '') : ''
  transitDialogVisible.value = true
}

async function handleTransit(): Promise<void> {
  if (!transitId.value) return
  if (transitAction.value === 'issue' && !transitDate.value) {
    ElMessage.warning('请选择开票日期')
    return
  }
  transiting.value = true
  try {
    await changeInvoiceStatus(transitId.value, transitAction.value, transitDate.value || undefined)
    ElMessage.success(transitAction.value === 'issue' ? '开票成功' : '已作废')
    transitDialogVisible.value = false
    fetchList()
  } finally { transiting.value = false }
}

// ---------- 附件（发票扫描件） ----------
const attDialogVisible = ref(false)
const attTargetId = ref<number | null>(null)
const attTargetNo = ref('')
const attLoading = ref(false)
const attUploading = ref(false)
const attList = ref<InvoiceAttachmentItem[]>([])

function openAttachments(row: InvoiceItem): void {
  attTargetId.value = row.id
  attTargetNo.value = row.invoiceNo
  attDialogVisible.value = true
  fetchAttachments()
}

async function fetchAttachments(): Promise<void> {
  if (!attTargetId.value) return
  attLoading.value = true
  try {
    attList.value = await listInvoiceAttachments(attTargetId.value)
  } finally { attLoading.value = false }
}

function makeUploader() {
  return async (options: UploadRequestOptions) => {
    if (!attTargetId.value) return
    attUploading.value = true
    try {
      await uploadInvoiceAttachment(attTargetId.value, options.file)
      ElMessage.success('上传成功')
      fetchAttachments()
    } finally { attUploading.value = false }
  }
}

async function handleDownloadAtt(att: InvoiceAttachmentItem): Promise<void> {
  if (!attTargetId.value) return
  await downloadInvoiceAttachment(attTargetId.value, att.id, att.fileName)
}

async function handleDeleteAtt(att: InvoiceAttachmentItem): Promise<void> {
  if (!attTargetId.value) return
  try {
    await ElMessageBox.confirm(`确定删除附件「${att.fileName}」吗？`, '删除确认', { type: 'warning' })
    await deleteInvoiceAttachment(attTargetId.value, att.id)
    ElMessage.success('删除成功')
    fetchAttachments()
  } catch { /* cancel */ }
}

// ---------- 导出待开票清单（交给财务开票） ----------
const exporting = ref(false)

async function handleExportPending(): Promise<void> {
  exporting.value = true
  try {
    // 待开票发票 + 合同选项（含客户开票六要素）按合同合并
    const pending = await pageInvoices({ current: 1, size: 1000, status: 0 })
    const contracts = await getContractOptions()
    const contractMap = new Map(contracts.map((c) => [c.id, c]))
    const header = ['开票抬头', '纳税人识别号', '开户银行', '银行账号', '开票地址', '开票电话',
      '发票类型', '币种', '外币金额', '汇率（每100）', '发票品名', '税收分类', '税收编码', '不含税金额（元）', '税额（元）', '价税合计（元）', '税率（%）',
      '合同字号', '合同名称', '所属项目', '业务类型', '备注']
    const rows = pending.records.map((inv) => {
      const c = contractMap.get(inv.contractId)
      return [
        c?.invoiceTitle || c?.clientName || '',
        c?.invoiceTaxNo || '',
        c?.invoiceBankName || '',
        c?.invoiceBankAccount || '',
        c?.invoiceAddress || '',
        c?.invoicePhone || '',
        inv.type,
        inv.currency || '人民币',
        inv.foreignAmount != null ? Number(inv.foreignAmount) : '',
        inv.exchangeRate != null ? Number(inv.exchangeRate) : '',
        inv.invoiceItem || '',
        inv.taxClass || '',
        inv.taxCode || '',
        inv.amountExTax != null ? Number(inv.amountExTax) : '',
        inv.taxAmount != null ? Number(inv.taxAmount) : '',
        Number(inv.amount),
        inv.taxRate ?? '',
        inv.contractNo || '',
        inv.contractName || '',
        inv.projectName || '',
        inv.remark || '',
      ]
    })
    if (!rows.length) {
      ElMessage.info('当前没有待开票的发票')
      return
    }
    const ws = XLSX.utils.aoa_to_sheet([header, ...rows])
    ws['!cols'] = [
      { wch: 30 }, { wch: 24 }, { wch: 26 }, { wch: 24 }, { wch: 30 }, { wch: 16 },
      { wch: 14 }, { wch: 10 }, { wch: 12 }, { wch: 12 }, { wch: 16 }, { wch: 16 }, { wch: 22 }, { wch: 14 }, { wch: 12 }, { wch: 14 }, { wch: 8 },
      { wch: 24 }, { wch: 26 }, { wch: 24 }, { wch: 18 }, { wch: 20 },
    ]
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '待开发票')
    XLSX.writeFile(wb, `待开发票清单_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success(`已导出 ${rows.length} 条待开票发票`)
  } finally {
    exporting.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选 -->
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-input v-model="query.keyword" placeholder="发票号/合同字号/名称/客户" clearable style="width: 220px" @keyup.enter="handleSearch" />
          <el-select v-model="query.type" placeholder="类型" clearable style="width: 150px; margin-left: 8px">
            <el-option v-for="t in invoiceTypes" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 110px; margin-left: 8px">
            <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="Number(code)" />
          </el-select>
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <div>
          <el-button :loading="exporting" @click="handleExportPending">导出待开票清单</el-button>
          <el-button @click="agingVisible = true">应收账龄</el-button>
        <el-button v-permission="'business:invoice:add'" type="primary" @click="openCreate">登记发票</el-button>
        </div>
      </div>

      <!-- 列表 -->
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="invoiceNo" label="发票号码" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.invoiceNo">{{ row.invoiceNo }}</span>
            <span v-else style="color: #9ca3af">待补</span>
          </template>
        </el-table-column>
        <el-table-column label="垫" width="50" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.isRecharge" type="warning" size="small">垫</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contractNo" label="合同字号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="contractName" label="合同名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="所属项目" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectName || '—' }}</template>
        </el-table-column>
        <el-table-column prop="clientName" label="客户" min-width="130" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="130" show-overflow-tooltip />
        <el-table-column prop="currency" label="币种" width="80" align="center">
          <template #default="{ row }">
            <span v-if="row.currency && row.currency !== '人民币'" style="color: #e6a23c">{{ row.currency }}</span>
            <span v-else style="color: #9ca3af">人民币</span>
          </template>
        </el-table-column>
        <el-table-column prop="invoiceItem" label="发票品名" min-width="110" show-overflow-tooltip>
          <template #default="{ row }">{{ row.invoiceItem || '—' }}</template>
        </el-table-column>
        <el-table-column label="不含税（元）" min-width="105" align="right">
          <template #default="{ row }">{{ money(row.amountExTax) }}</template>
        </el-table-column>
        <el-table-column label="税额（元）" min-width="95" align="right">
          <template #default="{ row }">{{ money(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column label="价税合计（元）" min-width="115" align="right">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column label="税率" width="80" align="right">
          <template #default="{ row }">{{ row.taxRate != null ? `${row.taxRate}%` : '—' }}</template>
        </el-table-column>
        <el-table-column label="已收核销（元）" min-width="120" align="right">
          <template #default="{ row }">{{ money(row.collectedAmount) }}</template>
        </el-table-column>
        <el-table-column prop="invoiceDate" label="开票日期" width="110">
          <template #default="{ row }">{{ row.invoiceDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagTypes[row.status]" size="small">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click="openAttachments(row)">附件</el-button>
            <el-button v-if="row.status !== 2" v-permission="'business:invoice:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0" v-permission="'business:invoice:status'" link type="success" size="small" @click="openTransit(row, 'issue')">开票</el-button>
            <el-button v-if="row.status !== 2" v-permission="'business:invoice:status'" link type="warning" size="small" @click="openTransit(row, 'void')">作废</el-button>
            <el-button v-if="row.status !== 1" v-permission="'business:invoice:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList" @size-change="handleSearch" />
      </div>
    </el-card>

    <!-- 登记/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑发票' : '登记发票'" width="640px">
      <el-form :model="form" label-width="120px">
        <el-form-item label="所属合同" required>
          <el-select v-model="form.contractId" :disabled="isEdit" placeholder="选择合同" filterable style="width: 100%">
            <el-option v-for="c in contractOptions" :key="c.id" :label="`${c.contractNo} | ${c.name}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="所属项目">
          <el-input :model-value="selectedContract?.projectName" readonly placeholder="选择合同后自动带出" />
        </el-form-item>
        <el-form-item label="垫付开票">
          <el-checkbox v-model="form.isRecharge">向客户收取的代垫费用（计入垫付台账，如差旅）</el-checkbox>
        </el-form-item>
        <el-form-item label="所属客户">
          <el-input :model-value="selectedContract?.clientName" readonly placeholder="选择合同后自动带出" />
        </el-form-item>
        <!-- 客户开票信息校对 -->
        <el-divider content-position="left">客户开票信息（自动带出）</el-divider>
        <el-form-item label="开票抬头">
          <el-input :model-value="selectedContract?.invoiceTitle || selectedContract?.clientName || ''" readonly />
        </el-form-item>
        <el-form-item label="纳税人识别号">
          <el-input :model-value="selectedContract?.invoiceTaxNo || ''" readonly />
        </el-form-item>
        <el-form-item label="开户银行">
          <el-input :model-value="selectedContract?.invoiceBankName || ''" readonly />
        </el-form-item>
        <el-form-item label="银行账号">
          <el-input :model-value="selectedContract?.invoiceBankAccount || ''" readonly />
        </el-form-item>
        <el-form-item label="开票地址">
          <el-input :model-value="selectedContract?.invoiceAddress || ''" readonly />
        </el-form-item>
        <el-form-item label="开票电话">
          <el-input :model-value="selectedContract?.invoicePhone || ''" readonly />
        </el-form-item>
        <el-divider content-position="left">发票信息</el-divider>
        <el-form-item label="发票品名">
          <el-input v-model="form.invoiceItem" placeholder="按业务类型自动带出，可修改" maxlength="100" />
        </el-form-item>
        <el-form-item label="税收编码">
          <el-input v-model="form.taxCode" placeholder="按业务类型自动带出，可修改" maxlength="30" />
        </el-form-item>
        <el-form-item label="税收分类">
          <el-input v-model="form.taxClass" placeholder="按业务类型自动带出，可修改" maxlength="100" />
        </el-form-item>
        <el-form-item label="发票号码">
          <el-input v-model="form.invoiceNo" placeholder="可登记后补填，开票前须填写" maxlength="50" />
        </el-form-item>
        <el-form-item label="发票类型" required>
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in invoiceTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="币种">
          <el-select v-model="form.currency" style="width: 100%" :disabled="isEdit">
            <el-option v-for="c in currencies" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <template v-if="isFx">
          <el-form-item label="外币金额" required>
            <div style="width: 100%; display: flex; gap: 8px; align-items: center">
              <el-input-number v-model="form.foreignAmount" :min="0.01" :precision="2" :step="1000" style="flex: 1" />
              <span style="flex-shrink: 0; color: #6b7280; font-size: 13px">{{ form.currency }}</span>
            </div>
          </el-form-item>
          <el-form-item label="汇率牌价">
            <div style="width: 100%">
              <div style="display: flex; gap: 8px; align-items: center">
                <el-input-number v-model="form.exchangeRate" :min="0.0001" :precision="4" :step="0.01" style="flex: 1" />
                <el-button size="small" :loading="rateLoading" @click="fetchRate(form.currency!)">刷新牌价</el-button>
              </div>
              <div v-if="rateInfo" class="field-tip">
                中国银行牌价（{{ rateInfo.publishTime }}）：每 100{{ form.currency }} 中行折算价 {{ rateInfo.bocRate }} 元，现汇买入 {{ rateInfo.spotBuy }} 元
              </div>
            </div>
          </el-form-item>
        </template>
        <el-form-item :label="isFx ? '不含税金额（元，自动折算）' : '不含税金额（元）'" :required="!isFx">
          <el-input-number v-model="form.amountExTax" :min="0.01" :precision="2" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税率（%）">
          <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" :step="1" style="width: 100%" />
        </el-form-item>
        <el-form-item label="税额（元）">
          <el-input-number v-model="form.taxAmount" :min="0" :precision="2" :step="100" style="width: 100%" @change="onTaxAmountChange" />
        </el-form-item>
        <el-form-item label="价税合计（元）">
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="1000" style="width: 100%" @change="onAmountChange" />
        </el-form-item>
        <el-form-item label="开票日期">
          <el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" placeholder="可开票时补填" style="width: 100%" />
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

    <!-- 开票/作废弹窗 -->
    <el-dialog v-model="transitDialogVisible" :title="transitAction === 'issue' ? '开票' : '作废发票'" width="420px">
      <el-form label-width="90px">
        <el-form-item v-if="transitAction === 'issue'" label="开票日期" required>
          <el-date-picker v-model="transitDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item v-else label="确认">
          <span>确定作废该发票吗？作废后不可恢复。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transitDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="transiting" @click="handleTransit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 附件弹窗 -->
    <el-dialog v-model="attDialogVisible" :title="`发票扫描件 - ${attTargetNo}`" width="640px">
      <div class="items-header">
        <span class="section-title">发票扫描件</span>
        <el-upload :show-file-list="false" :http-request="makeUploader()" accept=".pdf,.jpg,.jpeg,.png">
          <el-button size="small" type="primary" plain :loading="attUploading">上传扫描件</el-button>
        </el-upload>
      </div>
      <el-table v-loading="attLoading" :data="attList" border size="small">
        <el-table-column label="文件名" min-width="220">
          <template #default="{ row }">
            <AttachmentLink :file-name="row.fileName" :content-type="row.contentType" :fetch-signed-url="() => getInvoiceAttPreviewUrl(attTargetId!, row.id)" />
          </template>
        </el-table-column>
        <el-table-column label="大小" width="90">
          <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="130">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDownloadAtt(row)">下载</el-button>
            <el-button v-permission="'business:invoice:edit'" link type="danger" size="small" @click="handleDeleteAtt(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 应收账龄 -->
    <el-dialog v-model="agingVisible" title="应收账龄" width="860px">
      <div class="aging-cards">
        <div v-for="b in agingBuckets" :key="b.key" class="aging-card">
          <div class="aging-bucket">{{ b.key }} 天</div>
          <div class="aging-count">{{ b.count }} 张</div>
          <div class="aging-total">{{ b.total.toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }} 元</div>
        </div>
      </div>
      <el-table v-loading="agingLoading" :data="agingItems" border size="small" max-height="380">
        <el-table-column prop="invoiceNo" label="发票号" min-width="140" show-overflow-tooltip />
        <el-table-column prop="clientName" label="客户" min-width="130" show-overflow-tooltip />
        <el-table-column prop="contractNo" label="合同字号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="invoiceDate" label="开票日期" width="100" />
        <el-table-column label="账龄" width="90" align="center">
          <template #default="{ row }">{{ row.agingDays }} 天</template>
        </el-table-column>
        <el-table-column label="发票金额" width="110" align="right">
          <template #default="{ row }">{{ Number(row.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
        </el-table-column>
        <el-table-column label="已收" width="110" align="right">
          <template #default="{ row }">{{ Number(row.collectedAmount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
        </el-table-column>
        <el-table-column label="未收余额" width="110" align="right">
          <template #default="{ row }">
            <span style="color: #f56c6c">{{ Number(row.outstanding).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="账龄段" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.bucket === '90+' ? 'danger' : row.bucket === '61-90' ? 'warning' : 'info'" size="small">{{ row.bucket }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-filters { display: flex; align-items: center; }
.aging-cards { display: flex; gap: 12px; margin-bottom: 12px; }
.aging-card { flex: 1; border: 1px solid #e5e7eb; border-radius: 8px; padding: 10px; text-align: center; }
.aging-bucket { font-weight: 600; color: #374151; }
.aging-count { color: #6b7280; font-size: 13px; margin: 4px 0; }
.aging-total { color: #f56c6c; font-weight: 600; }
.items-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.section-title { font-size: 14px; font-weight: 500; color: #1f2937; }
</style>
