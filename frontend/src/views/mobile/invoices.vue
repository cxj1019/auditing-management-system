<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageInvoices, createInvoice, updateInvoice, changeInvoiceStatus } from '@/api/invoice'
import { getContractOptions } from '@/api/contract'
import { useUserStore } from '@/stores/user'
import type { ContractOptionItem, InvoiceItem, InvoiceRequest } from '@/types'

/** 手机端发票：列表 + 登记/编辑 + 开票流转（编辑不可换合同，作废后不可编辑，与桌面一致） */
const userStore = useUserStore()

const statusLabels: Record<number, string> = { 0: '待开票', 1: '已开票', 2: '已作废' }
const statusTypes: Record<number, 'info' | 'success' | 'danger'> = { 0: 'info', 1: 'success', 2: 'danger' }

const statusFilters = [
  { label: '全部', value: undefined as number | undefined },
  { label: '待开票', value: 0 },
  { label: '已开票', value: 1 },
  { label: '已作废', value: 2 },
]

const invoiceTypes = ['增值税专用发票', '增值税普通发票']
const TAX_RATE_PRESETS = [13, 9, 6, 3, 1.5, 0]

const canAdd = computed(() => userStore.hasPermission('business:invoice:add'))
const canEdit = computed(() => userStore.hasPermission('business:invoice:edit'))
const canTransit = computed(() => userStore.hasPermission('business:invoice:status'))

const loading = ref(false)
const keyword = ref('')
const activeStatus = ref<number | undefined>(undefined)
const records = ref<InvoiceItem[]>([])
const expandedId = ref<number | null>(null)

const contractOptions = ref<ContractOptionItem[]>([])

function money(v?: number): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function round2(v: number): number {
  return Math.round(v * 100) / 100
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageInvoices({
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

const emptyForm = (): InvoiceRequest => ({
  contractId: 0,
  invoiceNo: '',
  type: '增值税专用发票',
  amount: 0,
  taxRate: undefined,
  amountExTax: undefined,
  taxAmount: undefined,
  invoiceDate: '',
  invoiceItem: '',
  taxCode: '',
  taxClass: '',
  remark: '',
  isRecharge: false,
})
const form = reactive<InvoiceRequest>(emptyForm())

async function loadContractOptions(): Promise<void> {
  if (!contractOptions.value.length) {
    try {
      contractOptions.value = await getContractOptions()
    } catch { /* 忽略 */ }
  }
}

function onContractChange(): void {
  // 带出合同上的发票品名/税分类（与桌面一致）
  const c = contractOptions.value.find((x) => x.id === form.contractId)
  form.invoiceItem = c?.invoiceItem || ''
  form.taxCode = c?.taxCode || ''
}

/** 含税金额主输入：不含税 = 含税 ÷ (1+税率)，税额 = 含税 - 不含税 */
function onAmountChange(): void {
  if (form.amount == null || form.amount <= 0) return
  if (form.taxRate == null) {
    form.amountExTax = form.amount
    form.taxAmount = 0
    return
  }
  const ex = round2(form.amount / (1 + form.taxRate / 100))
  form.amountExTax = ex
  form.taxAmount = round2(form.amount - ex)
}

/** 不含税变化：税额 = 不含税 × 税率，含税 = 不含税 + 税额 */
function onAmountExTaxChange(): void {
  if (form.amountExTax == null || form.amountExTax <= 0) return
  const tax = form.taxRate == null ? 0 : round2((form.amountExTax * form.taxRate) / 100)
  form.taxAmount = tax
  form.amount = round2(form.amountExTax + tax)
}

function openCreate(): void {
  Object.assign(form, emptyForm())
  editingId.value = null
  loadContractOptions()
  formVisible.value = true
}

function openEdit(inv: InvoiceItem): void {
  Object.assign(form, {
    id: inv.id,
    contractId: inv.contractId,
    invoiceNo: inv.invoiceNo || '',
    type: inv.type,
    amount: inv.amount,
    taxRate: inv.taxRate ?? undefined,
    amountExTax: inv.amountExTax ?? undefined,
    taxAmount: inv.taxAmount ?? undefined,
    invoiceDate: inv.invoiceDate || '',
    invoiceItem: inv.invoiceItem || '',
    taxCode: inv.taxCode || '',
    taxClass: inv.taxClass || '',
    remark: inv.remark || '',
    isRecharge: !!inv.isRecharge,
  })
  editingId.value = inv.id
  loadContractOptions()
  formVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.contractId) { ElMessage.warning('请选择合同'); return }
  if (!form.amount || form.amount <= 0) { ElMessage.warning('请填写价税合计'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateInvoice(editingId.value, form)
      ElMessage.success('发票已更新')
    } else {
      await createInvoice(form)
      ElMessage.success('发票已登记')
    }
    formVisible.value = false
    await fetchList()
  } finally {
    saving.value = false
  }
}

// ---------- 开票流转 ----------
const transiting = ref(false)

async function handleIssue(inv: InvoiceItem): Promise<void> {
  let invoiceNo = inv.invoiceNo || ''
  // 与桌面一致：开票前必须有发票号；没有则先让用户补填
  if (!invoiceNo) {
    try {
      const input = await ElMessageBox.prompt(`「${inv.clientName || inv.contractNo}」尚未填写发票号，请输入后开票`, '开票', {
        inputPlaceholder: '发票号码',
        confirmButtonText: '下一步',
        cancelButtonText: '取消',
      })
      invoiceNo = (input.value || '').trim()
      if (!invoiceNo) {
        ElMessage.warning('请填写发票号')
        return
      }
    } catch { return }
  }
  let date = inv.invoiceDate || today()
  try {
    const input = await ElMessageBox.prompt('确认开票日期', '开票', {
      inputValue: date, confirmButtonText: '开票', cancelButtonText: '取消',
    })
    date = input.value || date
  } catch { return }
  transiting.value = true
  try {
    if (invoiceNo !== inv.invoiceNo || date !== inv.invoiceDate) {
      await updateInvoice(inv.id, { ...formFromRow(inv), invoiceNo, invoiceDate: date })
    }
    await changeInvoiceStatus(inv.id, 'issue', date)
    ElMessage.success('已开票')
    await fetchList()
  } finally {
    transiting.value = false
  }
}

function formFromRow(inv: InvoiceItem): InvoiceRequest {
  return {
    id: inv.id,
    contractId: inv.contractId,
    invoiceNo: inv.invoiceNo || '',
    type: inv.type,
    amount: inv.amount,
    taxRate: inv.taxRate ?? undefined,
    amountExTax: inv.amountExTax ?? undefined,
    taxAmount: inv.taxAmount ?? undefined,
    invoiceDate: inv.invoiceDate || '',
    invoiceItem: inv.invoiceItem || '',
    taxCode: inv.taxCode || '',
    taxClass: inv.taxClass || '',
    remark: inv.remark || '',
    isRecharge: !!inv.isRecharge,
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="mi-page">
    <div class="mi-header">
      <span class="mi-title">发票</span>
      <el-button v-if="canAdd" type="primary" size="small" @click="openCreate">＋ 登记</el-button>
    </div>

    <div class="mi-search">
      <el-input v-model="keyword" placeholder="发票号/客户/合同" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div class="mi-chips">
      <span
        v-for="f in statusFilters"
        :key="f.label"
        class="mi-chip"
        :class="{ active: activeStatus === f.value }"
        @click="switchStatus(f.value)"
      >{{ f.label }}</span>
    </div>

    <div v-if="!loading && !records.length" class="mi-empty">没有找到发票</div>

    <div v-for="inv in records" :key="inv.id" class="mi-card">
      <div class="mi-card-head" @click="expandedId = expandedId === inv.id ? null : inv.id">
        <span class="mi-name">{{ inv.clientName || inv.contractNo }}</span>
        <el-tag :type="statusTypes[inv.status]" size="small">{{ statusLabels[inv.status] }}</el-tag>
      </div>
      <div class="mi-sub">
        {{ inv.invoiceNo || '待开票' }} · {{ inv.type }}
        <el-tag v-if="inv.isRecharge" type="warning" size="small" style="margin-left: 6px">垫付</el-tag>
      </div>
      <div class="mi-amount">
        <b>¥ {{ money(inv.amount) }}</b>
        <span class="mi-amount-sub">已收 {{ money(inv.collectedAmount) }}</span>
      </div>

      <div v-if="expandedId === inv.id" class="mi-detail">
        <div class="mi-row"><span>关联合同</span>{{ inv.contractNo }} {{ inv.contractName || '' }}</div>
        <div class="mi-row" v-if="inv.projectNo"><span>项目</span>{{ inv.projectNo }} {{ inv.projectName || '' }}</div>
        <div class="mi-row"><span>不含税 / 税额</span>{{ money(inv.amountExTax) }} / {{ money(inv.taxAmount) }}</div>
        <div class="mi-row" v-if="inv.taxRate != null"><span>税率</span>{{ inv.taxRate }}%</div>
        <div class="mi-row"><span>开票日期</span>{{ inv.invoiceDate || '—' }}</div>
        <div class="mi-row" v-if="inv.invoiceItem"><span>发票品名</span>{{ inv.invoiceItem }}</div>
        <div class="mi-row" v-if="inv.remark"><span>备注</span>{{ inv.remark }}</div>

        <div class="mi-actions">
          <el-button v-if="canEdit && inv.status !== 2" size="small" plain @click="openEdit(inv)">编辑</el-button>
          <el-button v-if="canTransit && inv.status === 0" size="small" type="success" :disabled="transiting" @click="handleIssue(inv)">开票</el-button>
        </div>
      </div>
    </div>

    <!-- 登记/编辑发票 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑发票' : '登记发票'" :width="360">
      <el-form label-width="96px">
        <el-form-item label="合同" required>
          <el-select v-model="form.contractId" style="width: 100%" filterable :disabled="!!editingId" @change="onContractChange">
            <el-option v-for="c in contractOptions" :key="c.id" :label="`${c.contractNo} | ${c.clientName || ''}`" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in invoiceTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="价税合计" required>
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false" style="width: 100%" @change="onAmountChange" />
        </el-form-item>
        <el-form-item label="税率（%）">
          <el-select :model-value="form.taxRate" style="width: 100%" clearable allow-create filterable placeholder="选择或输入" @update:model-value="(v: unknown) => { form.taxRate = v == null || v === '' ? undefined : Number(v); form.amountExTax ? onAmountExTaxChange() : onAmountChange() }">
            <el-option v-for="r in TAX_RATE_PRESETS" :key="r" :label="r + '%'" :value="r" />
          </el-select>
        </el-form-item>
        <el-form-item label="不含税金额">
          <el-input-number v-model="form.amountExTax" :min="0" :precision="2" :controls="false" style="width: 100%" @change="onAmountExTaxChange" />
        </el-form-item>
        <el-form-item label="税额">
          <el-input-number v-model="form.taxAmount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="发票号"><el-input v-model="form.invoiceNo" maxlength="50" placeholder="待开票可先留空" /></el-form-item>
        <el-form-item label="开票日期"><el-date-picker v-model="form.invoiceDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="发票品名"><el-input v-model="form.invoiceItem" maxlength="100" /></el-form-item>
        <el-form-item label="垫付">
          <el-checkbox v-model="form.isRecharge">向客户收取的代垫费用</el-checkbox>
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
.mi-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mi-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.mi-title { font-size: 18px; font-weight: 600; }
.mi-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mi-search .el-input { flex: 1; }
.mi-chips { display: flex; gap: 8px; overflow-x: auto; padding-bottom: 4px; margin-bottom: 10px; }
.mi-chip { flex-shrink: 0; padding: 4px 12px; border-radius: 999px; background: #fff; border: 1px solid #e5e7eb; font-size: 12px; color: #6b7280; cursor: pointer; }
.mi-chip.active { background: #2563eb; border-color: #2563eb; color: #fff; }
.mi-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mi-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mi-name { font-weight: 600; font-size: 14px; }
.mi-sub { color: #6b7280; font-size: 12px; margin-top: 5px; display: flex; align-items: center; }
.mi-amount { margin-top: 6px; display: flex; justify-content: space-between; align-items: baseline; }
.mi-amount b { color: #f56c6c; font-size: 15px; }
.mi-amount-sub { color: #16a34a; font-size: 11px; }
.mi-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mi-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mi-row span { display: inline-block; width: 92px; color: #9ca3af; }
.mi-actions { margin-top: 10px; display: flex; justify-content: flex-end; gap: 8px; }
.mi-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
