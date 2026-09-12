<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { pageClients, listClientContacts } from '@/api/client'
import type { ClientContactItem, ClientItem } from '@/types'

/** 手机端客户列表：卡片 + 展开基本信息/开票信息/联系人 */
const loading = ref(false)
const keyword = ref('')
const records = ref<ClientItem[]>([])
const expandedId = ref<number | null>(null)
const contactCache = ref<Record<number, ClientContactItem[]>>({})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageClients({ current: 1, size: 50, keyword: keyword.value || undefined })
    records.value = data.records
  } finally {
    loading.value = false
  }
}

async function toggleDetail(c: ClientItem): Promise<void> {
  if (expandedId.value === c.id) {
    expandedId.value = null
    return
  }
  expandedId.value = c.id
  if (!contactCache.value[c.id]) {
    contactCache.value[c.id] = await listClientContacts(c.id).catch(() => [])
  }
}

onMounted(fetchList)
</script>

<template>
  <div class="mc-page">
    <div class="mc-header"><span class="mc-title">客户</span></div>

    <div class="mc-search">
      <el-input v-model="keyword" placeholder="客户名称/编号" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div v-if="!loading && !records.length" class="mc-empty">没有找到客户</div>

    <div v-for="c in records" :key="c.id" class="mc-card">
      <div class="mc-card-head" @click="toggleDetail(c)">
        <span class="mc-name">{{ c.clientName }}</span>
        <el-tag size="small" type="primary">{{ c.clientType }}</el-tag>
      </div>
      <div class="mc-sub" @click="toggleDetail(c)">{{ c.clientNo }}<template v-if="c.contactPerson"> · {{ c.contactPerson }} {{ c.contactPhone }}</template></div>

      <div v-if="expandedId === c.id" class="mc-detail">
        <div class="mc-sec">基本信息</div>
        <div class="mc-row"><span>统一社会信用代码</span>{{ c.creditCode || '—' }}</div>
        <div class="mc-row"><span>法定代表人</span>{{ c.legalRepresentative || '—' }}</div>
        <div class="mc-row"><span>注册资本</span>{{ c.registeredCapital || '—' }}</div>
        <div class="mc-row"><span>注册地址</span>{{ c.registeredAddress || '—' }}</div>

        <div class="mc-sec">开票信息</div>
        <div class="mc-row"><span>开票抬头</span>{{ c.invoiceTitle || '—' }}</div>
        <div class="mc-row"><span>开票税号</span>{{ c.invoiceTaxNo || '—' }}</div>
        <div class="mc-row"><span>开户银行</span>{{ c.invoiceBankName || '—' }}</div>
        <div class="mc-row"><span>银行账号</span>{{ c.invoiceBankAccount || '—' }}</div>
        <div class="mc-row"><span>开票地址电话</span>{{ c.invoiceAddress || '—' }} {{ c.invoicePhone || '' }}</div>

        <div class="mc-sec">联系人（{{ contactCache[c.id]?.length ?? 0 }}）</div>
        <div v-for="ct in contactCache[c.id] || []" :key="ct.id" class="mc-contact">
          <b>{{ ct.contactName }}</b><template v-if="ct.position"> · {{ ct.position }}</template>
          <div class="mc-contact-sub">{{ ct.phone || '—' }} {{ ct.email || '' }}</div>
        </div>
        <div v-if="!contactCache[c.id]?.length" class="mc-row"><span>联系人</span>暂无</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mc-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mc-header { margin-bottom: 10px; }
.mc-title { font-size: 18px; font-weight: 600; }
.mc-search { display: flex; gap: 8px; margin-bottom: 10px; }
.mc-search .el-input { flex: 1; }
.mc-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.mc-card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.mc-name { font-weight: 600; font-size: 14px; }
.mc-sub { color: #6b7280; font-size: 12px; margin-top: 5px; }
.mc-detail { margin-top: 10px; border-top: 1px dashed #e5e7eb; padding-top: 8px; }
.mc-sec { font-size: 12px; color: #2563eb; font-weight: 600; margin: 8px 0 4px; }
.mc-row { font-size: 12px; color: #374151; padding: 2px 0; word-break: break-all; }
.mc-row span { display: inline-block; width: 104px; color: #9ca3af; }
.mc-contact { font-size: 13px; padding: 4px 0; border-bottom: 1px solid #f9fafb; }
.mc-contact-sub { color: #6b7280; font-size: 12px; margin-top: 2px; }
.mc-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
