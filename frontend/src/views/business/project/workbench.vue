<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/api/request'
import type {
  ConfirmationItem, InvoiceItem, ProjectItem, PageResult,
} from '@/types'

/** 项目工作台：项目一览 + 直接开票 / 收款 / 处理函证 */
const route = useRoute()
const router = useRouter()
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

function openInvoiceItemIssue(inv: InvoiceItem): void {
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
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button v-if="row.status === 0" v-permission="'business:invoice:status'" link type="success" size="small" @click="openInvoiceItemIssue(row)">开票</el-button>
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
  </div>
</template>

<style scoped>
.wb-head { display: flex; justify-content: space-between; align-items: center; }
.wb-title { font-size: 17px; font-weight: 600; }
.wb-actions { margin-top: 10px; display: flex; gap: 8px; }
.stat .num { font-size: 20px; font-weight: 600; color: #1f2937; }
.stat .lbl { font-size: 12px; color: #9ca3af; margin-top: 2px; }
</style>
