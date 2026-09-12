<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageClients, listClientContacts, createClient, updateClient, addClientContact, deleteClientContact } from '@/api/client'
import { useUserStore } from '@/stores/user'
import type { ClientContactItem, ClientItem, ClientRequest } from '@/types'

/** 手机端客户：卡片列表 + 新建/编辑客户 + 联系人添加/删除 */
const userStore = useUserStore()

const canAdd = computed(() => userStore.hasPermission('business:client:add'))
const canEdit = computed(() => userStore.hasPermission('business:client:edit'))

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

// ---------- 客户新建/编辑 ----------
const formVisible = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)

const emptyForm = (): ClientRequest & { id?: number } => ({
  clientName: '', clientType: 'domestic',
  creditCode: '', registeredCapital: '', registeredAddress: '',
  legalRepresentative: '', businessScope: '',
  contactPerson: '', contactPhone: '',
  invoiceTitle: '', invoiceTaxNo: '', invoiceBankName: '',
  invoiceBankAccount: '', invoiceAddress: '', invoicePhone: '',
  remark: '',
})
const form = reactive<ClientRequest & { id?: number }>(emptyForm())

function openCreate(): void {
  Object.assign(form, emptyForm())
  editingId.value = null
  formVisible.value = true
}

function openEdit(c: ClientItem): void {
  Object.assign(form, {
    id: c.id,
    clientName: c.clientName,
    clientType: c.clientType,
    creditCode: c.creditCode || '',
    registeredCapital: c.registeredCapital || '',
    registeredAddress: c.registeredAddress || '',
    legalRepresentative: c.legalRepresentative || '',
    businessScope: c.businessScope || '',
    contactPerson: c.contactPerson || '',
    contactPhone: c.contactPhone || '',
    invoiceTitle: c.invoiceTitle || '',
    invoiceTaxNo: c.invoiceTaxNo || '',
    invoiceBankName: c.invoiceBankName || '',
    invoiceBankAccount: c.invoiceBankAccount || '',
    invoiceAddress: c.invoiceAddress || '',
    invoicePhone: c.invoicePhone || '',
    remark: c.remark || '',
  })
  editingId.value = c.id
  formVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.clientName.trim()) {
    ElMessage.warning('请填写客户名称')
    return
  }
  saving.value = true
  try {
    if (editingId.value) {
      await updateClient({ ...form, id: editingId.value })
      ElMessage.success('客户已更新')
    } else {
      await createClient(form)
      ElMessage.success('客户已创建')
    }
    formVisible.value = false
    await fetchList()
  } finally {
    saving.value = false
  }
}

// ---------- 联系人 ----------
const contactVisible = ref(false)
const contactSaving = ref(false)
const contactClientId = ref<number | null>(null)
const contactForm = reactive({ contactName: '', position: '', phone: '', email: '' })

function openContactCreate(clientId: number): void {
  contactClientId.value = clientId
  Object.assign(contactForm, { contactName: '', position: '', phone: '', email: '' })
  contactVisible.value = true
}

async function handleContactSave(): Promise<void> {
  if (!contactClientId.value || !contactForm.contactName.trim()) {
    ElMessage.warning('请填写联系人姓名')
    return
  }
  contactSaving.value = true
  try {
    await addClientContact(contactClientId.value, { ...contactForm })
    ElMessage.success('联系人已添加')
    contactVisible.value = false
    contactCache.value[contactClientId.value] = await listClientContacts(contactClientId.value)
  } finally {
    contactSaving.value = false
  }
}

async function handleContactDelete(clientId: number, ct: ClientContactItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`删除联系人「${ct.contactName}」？`, '删除确认', { type: 'warning' })
  } catch { return }
  await deleteClientContact(clientId, ct.id)
  ElMessage.success('已删除')
  contactCache.value[clientId] = await listClientContacts(clientId)
}

onMounted(fetchList)
</script>

<template>
  <div class="mc-page">
    <div class="mc-header">
      <span class="mc-title">客户</span>
      <el-button v-if="canAdd" type="primary" size="small" @click="openCreate">＋ 登记</el-button>
    </div>

    <div class="mc-search">
      <el-input v-model="keyword" placeholder="客户名称/编号" clearable @keyup.enter="fetchList" @clear="fetchList" />
      <el-button type="primary" @click="fetchList">查询</el-button>
    </div>

    <div v-if="!loading && !records.length" class="mc-empty">没有找到客户</div>

    <div v-for="c in records" :key="c.id" class="mc-card">
      <div class="mc-card-head" @click="toggleDetail(c)">
        <span class="mc-name">{{ c.clientName }}</span>
        <el-tag size="small" type="primary">{{ c.clientType === 'overseas' ? '境外' : '境内' }}</el-tag>
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
          <div class="mc-contact-line">
            <b>{{ ct.contactName }}</b><template v-if="ct.position"> · {{ ct.position }}</template>
            <el-button v-if="canEdit" link type="danger" size="small" @click="handleContactDelete(c.id, ct)">删除</el-button>
          </div>
          <div class="mc-contact-sub">{{ ct.phone || '—' }} {{ ct.email || '' }}</div>
        </div>
        <div v-if="!contactCache[c.id]?.length" class="mc-row"><span>联系人</span>暂无</div>
        <el-button v-if="canAdd" size="small" class="mc-contact-add" @click="openContactCreate(c.id)">＋ 添加联系人</el-button>

        <div class="mc-actions">
          <el-button v-if="canEdit" size="small" type="primary" plain @click="openEdit(c)">编辑客户</el-button>
        </div>
      </div>
    </div>

    <!-- 客户新建/编辑 -->
    <el-dialog v-model="formVisible" :title="editingId ? '编辑客户' : '登记客户'" :width="360">
      <el-form label-width="100px">
        <el-form-item label="客户名称" required>
          <el-input v-model="form.clientName" maxlength="200" />
        </el-form-item>
        <el-form-item label="客户类型" required>
          <el-select v-model="form.clientType" style="width: 100%">
            <el-option label="境内" value="domestic" />
            <el-option label="境外" value="overseas" />
          </el-select>
        </el-form-item>
        <el-form-item label="信用代码"><el-input v-model="form.creditCode" maxlength="50" /></el-form-item>
        <el-form-item label="法定代表人"><el-input v-model="form.legalRepresentative" /></el-form-item>
        <el-form-item label="注册资本"><el-input v-model="form.registeredCapital" /></el-form-item>
        <el-form-item label="注册地址"><el-input v-model="form.registeredAddress" /></el-form-item>
        <el-form-item label="联系人"><el-input v-model="form.contactPerson" /></el-form-item>
        <el-form-item label="联系电话"><el-input v-model="form.contactPhone" /></el-form-item>
        <el-form-item label="开票抬头"><el-input v-model="form.invoiceTitle" /></el-form-item>
        <el-form-item label="开票税号"><el-input v-model="form.invoiceTaxNo" /></el-form-item>
        <el-form-item label="开户银行"><el-input v-model="form.invoiceBankName" /></el-form-item>
        <el-form-item label="银行账号"><el-input v-model="form.invoiceBankAccount" /></el-form-item>
        <el-form-item label="开票地址"><el-input v-model="form.invoiceAddress" /></el-form-item>
        <el-form-item label="开票电话"><el-input v-model="form.invoicePhone" /></el-form-item>
        <el-form-item label="经营范围"><el-input v-model="form.businessScope" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 添加联系人 -->
    <el-dialog v-model="contactVisible" title="添加联系人" :width="320">
      <el-form label-width="72px">
        <el-form-item label="姓名" required><el-input v-model="contactForm.contactName" maxlength="50" /></el-form-item>
        <el-form-item label="职务"><el-input v-model="contactForm.position" maxlength="50" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="contactForm.phone" maxlength="30" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="contactForm.email" maxlength="100" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactVisible = false">取消</el-button>
        <el-button type="primary" :loading="contactSaving" @click="handleContactSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mc-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mc-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
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
.mc-contact-line { display: flex; justify-content: space-between; align-items: center; }
.mc-contact-sub { color: #6b7280; font-size: 12px; margin-top: 2px; }
.mc-contact-add { margin-top: 8px; width: 100%; }
.mc-actions { margin-top: 10px; display: flex; justify-content: flex-end; }
.mc-empty { text-align: center; color: #9ca3af; padding: 32px 0; }
</style>
