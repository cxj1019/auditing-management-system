<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getCollectionSummary, addPayment } from '@/api/collection'
import { getContractOptions } from '@/api/contract'
import { useUserStore } from '@/stores/user'
import type { CollectionSummaryItem, ContractOptionItem } from '@/types'

/** 手机端收款：合同维度汇总 + 登记收款 */
const userStore = useUserStore()

const canAdd = computed(() => userStore.hasPermission('business:collection:add'))

const paymentMethods = ['转账', '现金', '支票', '其他']

const loading = ref(false)
const keyword = ref('')
const records = ref<CollectionSummaryItem[]>([])
const expandedId = ref<number | null>(null)

const contractOptions = ref<ContractOptionItem[]>([])

function money(v?: number): string {
  return v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    records.value = await getCollectionSummary(keyword.value || undefined)
  } finally {
    loading.value = false
  }
}

// ---------- 登记收款 ----------
const formVisible = ref(false)
const saving = ref(false)

const form = reactive({
  contractId: undefined as number | undefined,
  amount: 0,
  paymentDate: today(),
  paymentMethod: '转账',
  payerName: '',
  remark: '',
})

async function loadContractOptions(): Promise<void> {
  if (!contractOptions.value.length) {
    try {
      contractOptions.value = await getContractOptions()
    } catch { /* 忽略 */ }
  }
}

function openCreate(contractId?: number, payerName?: string): void {
  Object.assign(form, { contractId: contractId ?? undefined, amount: 0, paymentDate: today(), paymentMethod: '转账', payerName: payerName || '', remark: '' })
  loadContractOptions()
  formVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.contractId) { ElMessage.warning('请选择合同'); return }
  if (!form.amount || form.amount <= 0) { ElMessage.warning('请填写收款金额'); return }
  if (!form.paymentDate) { ElMessage.warning('请选择收款日期'); return }
  saving.value = true
  try {
    await addPayment({ ...form })
    ElMessage.success('收款已登记')
    formVisible.value = false
    await fetchList()
  } finally {
    saving.value = false
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="ml-page">
    <div class="ml-header">
      <span class="ml-title">收款</span>
      <el-button v-if="canAdd" type="primary" size="small" @click="openCreate()">＋ 登记</el-button>
    </div>

    <div class="ml-search">
      <el-input v-model="keyword" placeholder="合同号/客户/项目" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div v-if="!loading && !records.length" class="ml-empty">暂无收款数据</div>

    <div v-for="(c, i) in records" :key="i" class="ml-card">
      <div class="ml-card-head" @click="expandedId = expandedId === i ? null : i">
        <span class="ml-name">{{ c.clientName || c.contractNo }}</span>
        <span class="ml-amount">¥ {{ money(c.totalCollected) }}</span>
      </div>
      <div class="ml-sub">{{ c.contractNo }}<template v-if="c.contractName"> · {{ c.contractName }}</template></div>

      <div v-if="expandedId === i" class="ml-detail">
        <div class="ml-row"><span>合同金额</span>{{ money(c.contractAmount) }}</div>
        <div class="ml-row"><span>已回款</span>{{ money(c.totalCollected) }}</div>
        <div class="ml-row"><span>未核销</span>{{ money(c.outstanding) }}</div>
        <div class="ml-row" v-if="c.plannedTotal != null"><span>计划回款</span>{{ money(c.plannedTotal) }}</div>
        <div class="ml-row"><span>回款进度</span>{{ (c.progressPercent ?? 0).toFixed(0) }}%</div>
        <div v-if="canAdd" class="ml-actions">
          <el-button size="small" type="primary" plain @click="openCreate(c.contractId, c.clientName)">＋ 该合同记一笔收款</el-button>
        </div>
      </div>
    </div>

    <!-- 登记收款 -->
    <el-dialog v-model="formVisible" title="登记收款" :width="340">
      <el-form label-width="80px">
        <el-form-item label="合同" required>
          <el-select v-model="form.contractId" style="width: 100%" filterable>
            <el-option
              v-for="c in contractOptions"
              :key="c.id"
              :label="`${c.contractNo} | ${c.clientName || ''}`"
              :value="c.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="收款金额" required>
          <el-input-number v-model="form.amount" :min="0" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款日期" required>
          <el-date-picker v-model="form.paymentDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收款方式">
          <el-select v-model="form.paymentMethod" style="width: 100%">
            <el-option v-for="m in paymentMethods" :key="m" :label="m" :value="m" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款方"><el-input v-model="form.payerName" maxlength="100" /></el-form-item>
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
.ml-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.ml-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.ml-title { font-size: 18px; font-weight: 600; }
.ml-search { display: flex; gap: 8px; margin-bottom: 10px; }
.ml-search .el-input { flex: 1; }
.ml-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.ml-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.ml-name { font-weight: 600; font-size: 14px; }
.ml-amount { color: #16a34a; font-weight: 600; font-size: 14px; flex-shrink: 0; }
.ml-sub { color: #9ca3af; font-size: 12px; margin-top: 4px; }
.ml-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.ml-row { display: flex; justify-content: space-between; font-size: 13px; color: #374151; padding: 3px 0; }
.ml-row span { color: #9ca3af; }
.ml-actions { margin-top: 10px; display: flex; justify-content: flex-end; }
.ml-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
