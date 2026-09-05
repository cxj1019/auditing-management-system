<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getBocRates, getBocHistory } from '@/api/exchangeRate'

interface Row {
  currencyName: string
  rate: string
  publishTime?: string
  spotBuy?: string
}

const loading = ref(false)
const rows = ref<Row[]>([])
const isHistory = ref(false)
const query = reactive({ date: '' })

const dayStr = (): string => new Date().toISOString().slice(0, 10)

async function loadLatest(): Promise<void> {
  loading.value = true
  isHistory.value = false
  try {
    const rates = await getBocRates()
    rows.value = rates.map((r) => ({
      currencyName: r.currencyName,
      rate: r.bocRate,
      publishTime: r.publishTime,
      spotBuy: r.spotBuy,
    }))
  } finally {
    loading.value = false
  }
}

async function loadHistory(): Promise<void> {
  if (!query.date) {
    ElMessage.warning('请选择日期')
    return
  }
  loading.value = true
  isHistory.value = true
  try {
    const details = await getBocHistory(query.date)
    rows.value = details
      .map((d) => ({ currencyName: d.currencyName, pair: d.pair, rate: d.rate, publishTime: d.date }))
      .sort((a, b) => a.currencyName.localeCompare(b.currencyName, 'zh-CN'))
  } finally {
    loading.value = false
  }
}

function backToLatest(): void {
  query.date = ''
  loadLatest()
}

onMounted(loadLatest)

/** 货币代码 → 中文名 */
const CN: Record<string, string> = {
  USD: '美元', EUR: '欧元', JPY: '日元', HKD: '港币', GBP: '英镑',
  AUD: '澳大利亚元', NZD: '新西兰元', SGD: '新加坡元', CHF: '瑞士法郎',
  CAD: '加拿大元', THB: '泰铢', KRW: '韩元', MOP: '澳门元', MYR: '林吉特',
  RUB: '卢布', ZAR: '南非兰特', AED: '阿联酋迪拉姆', SAR: '沙特里亚尔',
  HUF: '匈牙利福林', PLN: '兹罗提', DKK: '丹麦克朗', SEK: '瑞典克朗',
  NOK: '挪威克朗', TRY: '土耳其里拉', MXN: '墨西哥比索', CNY: '人民币',
  INR: '印度卢比', BRL: '巴西雷亚尔', PHP: '菲律宾比索', IDR: '印尼卢比',
}

/** 货币对显示名:USD/CNY → 美元;CNY/MOP → 人民币兑澳门元 */
function pairLabel(pair: string): string {
  const [base, quote] = pair.split('/')
  const name = (code: string) => CN[code] || code
  return base === 'CNY' ? `人民币兑${name(quote)}` : name(base)
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-date-picker
            v-model="query.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期查询历史中间价"
            style="width: 180px"
            :clearable="false"
          />
          <el-button type="primary" style="margin-left: 8px" @click="query.date ? loadHistory() : loadLatest()">
            {{ query.date ? '按日期查询' : '刷新最新牌价' }}
          </el-button>
          <el-button v-if="query.date" @click="backToLatest">返回最新</el-button>
          <span class="source-tip">
            {{ query.date
              ? '历史日期为：中国外汇交易中心人民币汇率中间价'
              : '当前展示：中国银行今日外汇牌价（每 100 外币兑人民币）' }}
          </span>
        </div>
      </div>

      <el-alert
        v-if="isHistory"
        type="info"
        :closable="false"
        show-icon
        title="历史日期展示中国外汇交易中心人民币汇率中间价；当日实时牌价请返回最新视图查看中国银行外汇牌价"
        style="margin-bottom: 12px"
      />

      <el-table v-loading="loading" :data="rows" border stripe>
        <el-table-column label="货币" min-width="190">
          <template #default="{ row }">
            {{ row.currencyName ? pairLabel(row.pair || row.currencyName) : row.currencyName }}
          </template>
        </el-table-column>
        <el-table-column label="现汇买入价" min-width="120" align="right">
          <template #default="{ row }">{{ row.spotBuy || '—' }}</template>
        </el-table-column>
        <el-table-column label="折算价 / 中间价" min-width="140" align="right">
          <template #default="{ row }">{{ row.rate }}</template>
        </el-table-column>
        <el-table-column label="数据日期" min-width="170">
          <template #default="{ row }">{{ row.publishTime || '—' }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
}

.source-tip {
  font-size: 12px;
  color: #9ca3af;
  margin-left: 12px;
}
</style>
