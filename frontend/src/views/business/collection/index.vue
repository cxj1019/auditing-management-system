<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pagePayments,
  getCollectionSummary,
  addPayment,
  updatePayment,
  deletePayment,
  writeOffPayment,
} from '@/api/collection'
import { getInvoiceOptions, getInvoiceSummary } from '@/api/invoice'
import { getRechargeLedger } from '@/api/collection'
import { pageContracts } from '@/api/contract'
import type {
  CollectionSummaryItem,
  RechargeLedgerItem,
  ContractItem,
  InvoiceOptionItem,
  InvoiceSummaryItem,
  PaymentItem,
  PaymentRequest,
} from '@/types'
import { restoreQuery, saveQuery } from '@/utils/queryCache'

const paymentMethods = ['转账', '现金', '支票', '其他']
const activeTab = ref('records')
watch(activeTab, (tab) => {
  if (tab === 'recharge' && !rechargeRows.value.length) fetchRechargeLedger()
})

function money(v?: number): string {
  if (v === undefined || v === null) return '—'
  return Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

// ---------- 收款记录列表 ----------
const loading = ref(false)
const records = ref<PaymentItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 10,
  keyword: '',
  dateRange: [] as string[],
})
restoreQuery('collection', query)
watch(query, () => saveQuery('collection', query), { deep: true })

function buildDateParams() {
  return {
    startDate: query.dateRange?.[0],
    endDate: query.dateRange?.[1],
  }
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pagePayments({ current: query.current, size: query.size, keyword: query.keyword || undefined, ...buildDateParams() })
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

// ---------- 登记/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
/** 收款性质：writeoff=核销已开票发票；prepay=预收（挂合同） */
const payMode = ref<'writeoff' | 'prepay'>('writeoff')
const form = reactive<PaymentRequest>({
  invoiceId: undefined,
  contractId: undefined,
  amount: 0,
  paymentDate: '',
  paymentMethod: '转账',
  payerName: '',
  remark: '',
})
/** 可选发票（已开票） */
const invoiceOptions = ref<InvoiceOptionItem[]>([])
/** 可选合同（非草稿，供预收） */
const contractOptions = ref<ContractItem[]>([])
const selectedInvoice = computed(() =>
  invoiceOptions.value.find((i) => i.id === form.invoiceId))
const selectedContract = computed(() =>
  contractOptions.value.find((c) => c.id === form.contractId))

async function loadInvoiceOptions(): Promise<void> {
  invoiceOptions.value = await getInvoiceOptions()
}

async function loadContractOptions(): Promise<void> {
  const data = await pageContracts({ current: 1, size: 200 })
  contractOptions.value = data.records.filter((c) => c.status !== 0)
}

function openCreate(): void {
  isEdit.value = false
  editingId.value = null
  payMode.value = 'writeoff'
  Object.assign(form, {
    invoiceId: undefined, contractId: undefined, amount: 0,
    paymentDate: '', paymentMethod: '转账', payerName: '', remark: '',
  })
  loadInvoiceOptions()
  loadContractOptions()
  dialogVisible.value = true
}

function openEdit(row: PaymentItem): void {
  isEdit.value = true
  editingId.value = row.id
  payMode.value = row.invoiceId ? 'writeoff' : 'prepay'
  Object.assign(form, {
    invoiceId: row.invoiceId,
    contractId: row.contractId,
    amount: row.amount,
    paymentDate: row.paymentDate,
    paymentMethod: row.paymentMethod,
    payerName: row.payerName,
    remark: row.remark,
  })
  loadInvoiceOptions()
  loadContractOptions()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!isEdit.value) {
    if (payMode.value === 'writeoff' && !form.invoiceId) {
      ElMessage.warning('请选择所属发票（仅已开票发票可核销收款）')
      return
    }
    if (payMode.value === 'prepay' && !form.contractId) {
      ElMessage.warning('请选择预收合同')
      return
    }
  }
  saving.value = true
  try {
    const payload: PaymentRequest = { ...form }
    if (payMode.value === 'writeoff') payload.contractId = undefined
    else payload.invoiceId = undefined
    if (isEdit.value && editingId.value) {
      await updatePayment(editingId.value, payload)
      ElMessage.success('修改成功')
    } else {
      await addPayment(payload)
      ElMessage.success(payMode.value === 'prepay' ? '预收款登记成功' : '登记成功')
    }
    dialogVisible.value = false
    fetchList()
    fetchSummary()
    fetchInvoiceSummary()
  } finally {
    saving.value = false
  }
}

// ---------- 预收核销到发票 ----------
const writeOffVisible = ref(false)
const writeOffTarget = ref<PaymentItem | null>(null)
const writeOffInvoiceId = ref<number | undefined>(undefined)
const writeOffOptions = ref<InvoiceOptionItem[]>([])
const writeOffSaving = ref(false)

async function openWriteOff(row: PaymentItem): Promise<void> {
  writeOffTarget.value = row
  writeOffInvoiceId.value = undefined
  const all = await getInvoiceOptions()
  writeOffOptions.value = all.filter((i) => i.contractId === row.contractId)
  if (!writeOffOptions.value.length) {
    ElMessage.info('该合同下暂无已开票发票')
    return
  }
  writeOffVisible.value = true
}

async function confirmWriteOff(): Promise<void> {
  if (!writeOffTarget.value || !writeOffInvoiceId.value) {
    ElMessage.warning('请选择要核销的发票')
    return
  }
  writeOffSaving.value = true
  try {
    await writeOffPayment(writeOffTarget.value.id, writeOffInvoiceId.value)
    ElMessage.success('核销成功')
    writeOffVisible.value = false
    fetchList()
    fetchSummary()
    fetchInvoiceSummary()
  } finally {
    writeOffSaving.value = false
  }
}

async function handleDelete(row: PaymentItem): Promise<void> {
  const label = row.invoiceNo || row.contractNo
  try {
    await ElMessageBox.confirm(`确定删除该笔收款（${label}，${row.amount} 元）吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deletePayment(row.id)
    ElMessage.success('删除成功')
    fetchList()
    fetchSummary()
    fetchInvoiceSummary()
  } catch {
    // 用户取消
  }
}

// ---------- 发票核销汇总 ----------
const invoiceSummaryLoading = ref(false)
const invoiceSummaryRows = ref<InvoiceSummaryItem[]>([])
const invoiceSummaryKeyword = ref('')

async function fetchInvoiceSummary(): Promise<void> {
  invoiceSummaryLoading.value = true
  try {
    invoiceSummaryRows.value = await getInvoiceSummary(invoiceSummaryKeyword.value || undefined)
  } finally {
    invoiceSummaryLoading.value = false
  }
}

// ---------- 垫付台账 ----------
const rechargeLoading = ref(false)
const rechargeRows = ref<RechargeLedgerItem[]>([])

async function fetchRechargeLedger(): Promise<void> {
  rechargeLoading.value = true
  try {
    rechargeRows.value = await getRechargeLedger()
  } finally { rechargeLoading.value = false }
}

const rechargeStatusLabels: Record<string, string> = {
  'pending-invoice': '待开票',
  'pending-collect': '待收回',
  settled: '已结清',
}
const rechargeStatusTypes: Record<string, 'warning' | 'danger' | 'success'> = {
  'pending-invoice': 'warning',
  'pending-collect': 'danger',
  settled: 'success',
}

// ---------- 合同收款汇总 ----------
const summaryLoading = ref(false)
const summaryRows = ref<CollectionSummaryItem[]>([])
const summaryKeyword = ref('')

async function fetchSummary(): Promise<void> {
  summaryLoading.value = true
  try {
    summaryRows.value = await getCollectionSummary(summaryKeyword.value || undefined)
  } finally {
    summaryLoading.value = false
  }
}

function progressColor(percent: number): string {
  if (percent >= 100) return '#67c23a'
  if (percent >= 60) return '#409eff'
  return '#e6a23c'
}

onMounted(() => {
  fetchList()
  fetchSummary()
  fetchInvoiceSummary()
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <el-tabs v-model="activeTab">
        <!-- 页签一：收款记录 -->
        <el-tab-pane label="收款记录" name="records">
          <div class="table-toolbar">
            <div class="toolbar-filters">
              <el-input v-model="query.keyword" placeholder="发票号/合同编号/名称" clearable style="width: 220px" @keyup.enter="handleSearch" />
              <el-date-picker
                v-model="query.dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 260px; margin-left: 8px"
              />
              <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
            </div>
            <el-button v-permission="'business:collection:add'" type="primary" @click="openCreate">登记收款</el-button>
          </div>

          <el-table v-loading="loading" :data="records" border stripe>
            <el-table-column prop="invoiceNo" label="发票号码" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.invoiceNo">{{ row.invoiceNo }}</span>
                <el-tag v-else type="warning" size="small">预收·未核销</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="contractNo" label="合同字号" min-width="170" show-overflow-tooltip />
            <el-table-column label="所属项目" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ row.projectNo ? `${row.projectNo} ${row.projectName}` : '—' }}</template>
            </el-table-column>
            <el-table-column prop="contractName" label="合同名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="clientName" label="客户" min-width="120" show-overflow-tooltip />
            <el-table-column label="收款金额（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.amount) }}</template>
            </el-table-column>
            <el-table-column prop="paymentDate" label="收款日期" width="110" />
            <el-table-column prop="paymentMethod" label="方式" width="80" />
            <el-table-column prop="payerName" label="付款方" min-width="110" show-overflow-tooltip />
            <el-table-column label="操作" width="170" fixed="right">
              <template #default="{ row }">
                <el-button v-if="!row.invoiceId" v-permission="'business:collection:edit'" link type="success" size="small" @click="openWriteOff(row)">核销</el-button>
                <el-button v-permission="'business:collection:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button v-permission="'business:collection:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>

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
        </el-tab-pane>

        <!-- 页签二：发票核销汇总 -->
        <el-tab-pane label="发票核销" name="invoiceSummary">
          <div class="table-toolbar">
            <span class="summary-title">按发票核销：开票金额 vs 已收</span>
            <div>
              <el-input v-model="invoiceSummaryKeyword" placeholder="发票号/合同/客户" clearable style="width: 220px" @keyup.enter="fetchInvoiceSummary" />
              <el-button type="primary" style="margin-left: 8px" @click="fetchInvoiceSummary">查询</el-button>
            </div>
          </div>

          <el-table v-loading="invoiceSummaryLoading" :data="invoiceSummaryRows" border stripe>
            <el-table-column prop="invoiceNo" label="发票号码" min-width="150" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="130" show-overflow-tooltip />
            <el-table-column prop="contractNo" label="合同字号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="contractName" label="合同名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="clientName" label="客户" min-width="120" show-overflow-tooltip />
            <el-table-column label="发票金额（元）" min-width="130" align="right">
              <template #default="{ row }">{{ money(row.invoiceAmount) }}</template>
            </el-table-column>
            <el-table-column label="已收核销（元）" min-width="130" align="right">
              <template #default="{ row }">{{ money(row.collectedAmount) }}</template>
            </el-table-column>
            <el-table-column label="未核销余额（元）" min-width="140" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.outstanding) <= 0 ? '#67c23a' : '#f56c6c' }">
                  {{ money(row.outstanding) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="核销进度" min-width="160">
              <template #default="{ row }">
                <el-progress :percentage="Math.min(row.progressPercent, 100)" :color="progressColor(row.progressPercent)" />
                <span class="progress-text">{{ row.progressPercent }}%</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 页签三：合同收款汇总 -->
        <el-tab-pane label="合同汇总" name="summary">
          <div class="table-toolbar">
            <span class="summary-title">按合同汇总收款进度（含历史未挂发票收款）</span>
            <div>
              <el-input v-model="summaryKeyword" placeholder="合同编号/名称/客户" clearable style="width: 220px" @keyup.enter="fetchSummary" />
              <el-button type="primary" style="margin-left: 8px" @click="fetchSummary">查询</el-button>
            </div>
          </div>

          <el-table v-loading="summaryLoading" :data="summaryRows" border stripe>
            <el-table-column prop="contractNo" label="合同字号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="contractName" label="合同名称" min-width="180" show-overflow-tooltip />
            <el-table-column prop="clientName" label="客户" min-width="130" show-overflow-tooltip />
            <el-table-column label="合同金额（元）" min-width="130" align="right">
              <template #default="{ row }">{{ money(row.contractAmount) }}</template>
            </el-table-column>
            <el-table-column label="已收合计（元）" min-width="130" align="right">
              <template #default="{ row }">{{ money(row.totalCollected) }}</template>
            </el-table-column>
            <el-table-column label="未收余额（元）" min-width="130" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.outstanding) <= 0 ? '#67c23a' : '#f56c6c' }">
                  {{ money(row.outstanding) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="进度" min-width="160">
              <template #default="{ row }">
                <el-progress :percentage="Math.min(row.progressPercent, 100)" :color="progressColor(row.progressPercent)" />
                <span class="progress-text">{{ row.progressPercent }}%</span>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 垫付台账 -->
        <el-tab-pane label="垫付台账" name="recharge">
          <div class="table-toolbar">
            <span class="summary-title">可向客户收取费用闭环：垫付（报销）→ 垫付开票 → 收回（按项目归集）</span>
            <el-button type="primary" plain @click="fetchRechargeLedger">刷新</el-button>
          </div>
          <el-table v-loading="rechargeLoading" :data="rechargeRows" border stripe>
            <el-table-column label="项目" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.projectNo ? `${row.projectNo} ${row.projectName}` : '—' }}</template>
            </el-table-column>
            <el-table-column prop="clientName" label="客户" min-width="130" show-overflow-tooltip />
            <el-table-column label="垫付合计（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.rechargeTotal) }}</template>
            </el-table-column>
            <el-table-column label="已开票（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.invoicedTotal) }}</template>
            </el-table-column>
            <el-table-column label="待开票（元）" min-width="120" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.pendingInvoice) > 0 ? '#e6a23c' : '#67c23a' }">{{ money(row.pendingInvoice) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="已收回（元）" min-width="120" align="right">
              <template #default="{ row }">{{ money(row.collectedTotal) }}</template>
            </el-table-column>
            <el-table-column label="待收回（元）" min-width="120" align="right">
              <template #default="{ row }">
                <span :style="{ color: Number(row.pendingCollect) > 0 ? '#f56c6c' : '#67c23a' }">{{ money(row.pendingCollect) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="rechargeStatusTypes[row.status] || 'info'" size="small">{{ rechargeStatusLabels[row.status] || row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- 登记/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑收款' : '登记收款'" width="560px">
      <el-form :model="form" label-width="100px">
        <el-form-item v-if="!isEdit" label="收款性质" required>
          <el-radio-group v-model="payMode">
            <el-radio-button value="writeoff">核销发票</el-radio-button>
            <el-radio-button value="prepay">预收款</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="payMode === 'writeoff'" label="所属发票" :required="!isEdit">
          <el-select v-model="form.invoiceId" :disabled="isEdit" placeholder="选择已开票发票" filterable style="width: 100%">
            <el-option
              v-for="i in invoiceOptions"
              :key="i.id"
              :label="`${i.invoiceNo} | ${i.contractName}`"
              :value="i.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="预收合同" :required="!isEdit">
          <el-select v-model="form.contractId" :disabled="isEdit" placeholder="选择合同（预付款暂挂合同）" filterable style="width: 100%">
            <el-option
              v-for="c in contractOptions"
              :key="c.id"
              :label="`${c.contractNo} | ${c.name}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="合同/客户">
          <el-input
            :model-value="payMode === 'writeoff'
              ? (selectedInvoice ? `${selectedInvoice.contractNo || ''} ${selectedInvoice.contractName || ''}（${selectedInvoice.clientName || '—'}）` : '')
              : (selectedContract ? `${selectedContract.contractNo || ''} ${selectedContract.name || ''}（${selectedContract.clientName || '—'}）` : '')"
            readonly
            placeholder="选择后自动带出"
          />
        </el-form-item>
        <el-form-item v-if="payMode === 'writeoff'" label="发票金额">
          <el-input :model-value="selectedInvoice ? `${money(selectedInvoice.amount)}（已收 ${money(selectedInvoice.collectedAmount)}）` : ''" readonly placeholder="选择发票后自动带出" />
        </el-form-item>
        <el-form-item label="收款金额（元）" required>
          <el-input-number v-model="form.amount" :min="0.01" :precision="2" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款日期" required>
          <el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款方式" required>
          <el-select v-model="form.paymentMethod" style="width: 100%">
            <el-option v-for="m in paymentMethods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款方">
          <el-input v-model="form.payerName" placeholder="付款方（可选）" maxlength="100" />
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

    <!-- 预收核销弹窗 -->
    <el-dialog v-model="writeOffVisible" title="预收核销到发票" width="520px">
      <p v-if="writeOffTarget" style="margin: 0 0 12px; color: #6b7280; font-size: 13px">
        将预收款（{{ money(writeOffTarget.amount) }} 元）核销到该合同的已开票发票：
      </p>
      <el-select v-model="writeOffInvoiceId" placeholder="选择已开票发票" filterable style="width: 100%">
        <el-option
          v-for="i in writeOffOptions"
          :key="i.id"
          :label="`${i.invoiceNo} | ${i.contractName}（发票 ${money(i.amount)}，已收 ${money(i.collectedAmount)}）`"
          :value="i.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="writeOffVisible = false">取消</el-button>
        <el-button type="primary" :loading="writeOffSaving" @click="confirmWriteOff">核销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
}

.summary-title {
  font-size: 15px;
  font-weight: 500;
  color: #1f2937;
}

.progress-text {
  font-size: 12px;
  color: #6b7280;
  margin-left: 4px;
}
</style>
