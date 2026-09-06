<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import * as XLSX from 'xlsx'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  pageClients, createClient, updateClient, deleteClient,
  listClientContacts, addClientContact, updateClientContact, deleteClientContact,
} from '@/api/client'
import { getClientStatement } from '@/api/client'
import type { ClientContactItem, ClientContactRequest, ClientItem, ClientRequest, ClientStatementVO } from '@/types'

const typeLabels: Record<string, string> = { domestic: '境内', overseas: '境外' }
const typeTagTypes: Record<string, 'primary' | 'warning'> = { domestic: 'primary', overseas: 'warning' }

const loading = ref(false)
const records = ref<ClientItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1, size: 10,
  keyword: '', clientType: '',
})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageClients({
      ...query,
      keyword: query.keyword || undefined,
      clientType: query.clientType || undefined,
    })
    records.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

function handleSearch(): void { query.current = 1; fetchList() }
function handleReset(): void {
  query.keyword = ''; query.clientType = ''; handleSearch()
}

// ---------- 编辑弹窗（基本信息 / 开票信息 / 联系人） ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const activeTab = ref('basic')
const form = reactive<ClientRequest & { id?: number }>({
  clientName: '', clientType: 'domestic',
  creditCode: '', registeredCapital: '', registeredAddress: '',
  legalRepresentative: '', businessScope: '',
  contactPerson: '', contactPhone: '',
  invoiceTitle: '', invoiceTaxNo: '', invoiceBankName: '',
  invoiceBankAccount: '', invoiceAddress: '', invoicePhone: '',
  remark: '',
})

function resetForm(): void {
  Object.assign(form, {
    clientName: '', clientType: 'domestic',
    creditCode: '', registeredCapital: '', registeredAddress: '',
    legalRepresentative: '', businessScope: '',
    contactPerson: '', contactPhone: '',
    invoiceTitle: '', invoiceTaxNo: '', invoiceBankName: '',
    invoiceBankAccount: '', invoiceAddress: '', invoicePhone: '',
    remark: '',
  })
  form.id = undefined
}

function openCreate(): void {
  isEdit.value = false
  resetForm()
  activeTab.value = 'basic'
  contacts.value = []
  dialogVisible.value = true
}

function openEdit(row: ClientItem): void {
  isEdit.value = true
  Object.assign(form, {
    clientName: row.clientName, clientType: row.clientType,
    creditCode: row.creditCode || '', registeredCapital: row.registeredCapital || '',
    registeredAddress: row.registeredAddress || '', legalRepresentative: row.legalRepresentative || '',
    businessScope: row.businessScope || '',
    contactPerson: row.contactPerson || '', contactPhone: row.contactPhone || '',
    invoiceTitle: row.invoiceTitle || '', invoiceTaxNo: row.invoiceTaxNo || '',
    invoiceBankName: row.invoiceBankName || '', invoiceBankAccount: row.invoiceBankAccount || '',
    invoiceAddress: row.invoiceAddress || '', invoicePhone: row.invoicePhone || '',
    remark: row.remark || '',
  })
  form.id = row.id
  activeTab.value = 'basic'
  dialogVisible.value = true
  loadContacts()
}

async function handleSave(): Promise<void> {
  if (!form.clientName) { ElMessage.warning('请填写客户名称'); return }
  saving.value = true
  try {
    if (isEdit.value && form.id) {
      await updateClient(form as ClientRequest & { id: number })
      ElMessage.success('修改成功')
    } else {
      const newId = await createClient(form)
      // 登记成功后切换为编辑态并打开联系人页签，方便接着维护联系人
      form.id = newId
      isEdit.value = true
      activeTab.value = 'contacts'
      ElMessage.success('登记成功，可继续维护联系人')
      await loadContacts()
      fetchList()
      return
    }
    dialogVisible.value = false
    fetchList()
  } finally { saving.value = false }
}

// ---------- 联系人 ----------
const contacts = ref<ClientContactItem[]>([])
const contactsLoading = ref(false)
const contactDialogVisible = ref(false)
const contactSaving = ref(false)
const contactEditingId = ref<number | null>(null)
const contactForm = reactive<ClientContactRequest & { id?: number }>({
  contactName: '', position: '', phone: '', email: '', remark: '',
})

/** 拉取指定客户的联系人清单（失败不抛错，返回空列表） */
async function fetchContacts(clientId: number): Promise<ClientContactItem[]> {
  try {
    return await listClientContacts(clientId)
  } catch {
    // 后端尚未部署联系人接口等情况：不阻断客户编辑/详情
    return []
  }
}

async function loadContacts(): Promise<void> {
  if (!form.id) return
  contactsLoading.value = true
  try {
    contacts.value = await fetchContacts(form.id)
  } finally { contactsLoading.value = false }
}

function openContactCreate(): void {
  contactEditingId.value = null
  Object.assign(contactForm, { contactName: '', position: '', phone: '', email: '', remark: '' })
  contactDialogVisible.value = true
}

function openContactEdit(row: ClientContactItem): void {
  contactEditingId.value = row.id
  Object.assign(contactForm, {
    contactName: row.contactName, position: row.position || '',
    phone: row.phone || '', email: row.email || '', remark: row.remark || '',
  })
  contactDialogVisible.value = true
}

async function handleContactSave(): Promise<void> {
  if (!contactForm.contactName) { ElMessage.warning('请填写联系人姓名'); return }
  if (!form.id) return
  contactSaving.value = true
  try {
    if (contactEditingId.value) {
      await updateClientContact(form.id, contactEditingId.value, contactForm)
      ElMessage.success('联系人已更新')
    } else {
      await addClientContact(form.id, contactForm)
      ElMessage.success('联系人已添加')
    }
    contactDialogVisible.value = false
    loadContacts()
  } finally { contactSaving.value = false }
}

async function handleContactDelete(row: ClientContactItem): Promise<void> {
  if (!form.id) return
  try {
    await ElMessageBox.confirm(`确定删除联系人「${row.contactName}」吗？`, '删除确认', { type: 'warning' })
    await deleteClientContact(form.id, row.id)
    ElMessage.success('删除成功')
    loadContacts()
  } catch { /* 取消 */ }
}

// ---------- 客户详情抽屉 ----------
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailClient = ref<ClientItem | null>(null)
const detailContacts = ref<ClientContactItem[]>([])

async function openDetail(row: ClientItem): Promise<void> {
  detailClient.value = row
  detailVisible.value = true
  detailLoading.value = true
  try {
    detailContacts.value = await fetchContacts(row.id)
  } finally { detailLoading.value = false }
}

/** 详情抽屉里直接进入编辑 */
function editFromDetail(): void {
  if (!detailClient.value) return
  detailVisible.value = false
  openEdit(detailClient.value)
}

/** 导出客户对账单 Excel（汇总/发票/回款 三个 Sheet） */
const statementLoadingId = ref<number | null>(null)
async function exportStatement(row: ClientItem): Promise<void> {
  statementLoadingId.value = row.id
  try {
    const st = await getClientStatement(row.id)
    const money = (v: unknown) => Number(v ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
    const wb = XLSX.utils.book_new()

    const overview = [
      ['客户对账单'],
      ['客户编号', st.clientNo],
      ['客户名称', st.clientName],
      ['导出日期', new Date().toISOString().slice(0, 10)],
      [],
      ['合同总额（元）', money(st.contractTotal)],
      ['已开票总额（元）', money(st.invoiceIssuedTotal)],
      ['已回款总额（元）', money(st.collectedTotal)],
      ['未收余额（元）', money(st.outstanding)],
    ]
    const ws1 = XLSX.utils.aoa_to_sheet(overview)
    XLSX.utils.book_append_sheet(wb, ws1, '汇总')

    const ws2 = XLSX.utils.aoa_to_sheet([
      ['合同字号', '合同名称', '所属项目', '金额（元）', '状态'],
      ...st.contracts.map((c) => [c.contractNo, c.name || '', c.projectName || '', Number(c.amount), c.statusLabel]),
    ])
    XLSX.utils.book_append_sheet(wb, ws2, '合同')

    const ws3 = XLSX.utils.aoa_to_sheet([
      ['发票号', '合同字号', '开票日期', '价税合计（元）', '不含税（元）', '税额（元）', '状态'],
      ...st.invoices.map((i) => [i.invoiceNo, i.contractNo || '', i.invoiceDate || '', Number(i.amount),
        Number(i.amountExTax ?? 0), Number(i.taxAmount ?? 0), i.statusLabel]),
    ])
    XLSX.utils.book_append_sheet(wb, ws3, '发票')

    const ws4 = XLSX.utils.aoa_to_sheet([
      ['回款日期', '合同字号', '回款金额（元）', '方式'],
      ...st.payments.map((p) => [p.paymentDate || '', p.contractNo || '', Number(p.amount), p.paymentMethod || '']),
    ])
    XLSX.utils.book_append_sheet(wb, ws4, '回款')

    XLSX.writeFile(wb, `对账单_${st.clientName}_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('对账单已导出')
  } finally {
    statementLoadingId.value = null
  }
}

async function handleDelete(row: ClientItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除客户「${row.clientName}」吗？`, '删除确认', { type: 'warning' })
    await deleteClient(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch { /* cancel */ }
}

onMounted(fetchList)
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-input v-model="query.keyword" placeholder="客户编号/名称" clearable style="width: 200px" @keyup.enter="handleSearch" />
          <el-select v-model="query.clientType" placeholder="类型" clearable style="width: 100px; margin-left: 8px">
            <el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" />
          </el-select>
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button v-permission="'business:client:add'" type="primary" @click="openCreate">登记客户</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe class="client-table" @row-click="openDetail">
        <el-table-column prop="clientNo" label="客户编号" min-width="150" />
        <el-table-column prop="clientName" label="客户名称" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTagTypes[row.clientType]" size="small">{{ typeLabels[row.clientType] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creditCode" label="统一信用代码" min-width="180" show-overflow-tooltip />
        <el-table-column prop="registeredCapital" label="注册资本" min-width="110" />
        <el-table-column prop="legalRepresentative" label="法定代表人" width="110" />
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click.stop="openDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" :loading="statementLoadingId === row.id" @click.stop="exportStatement(row)">对账单</el-button>
            <el-button v-if="row.status !== 3" v-permission="'business:client:edit'" link type="primary" size="small" @click.stop="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 3" v-permission="'business:client:delete'" link type="danger" size="small" @click.stop="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList" @size-change="handleSearch" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑客户' : '登记客户'" width="680px">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-form :model="form" label-width="130px">
            <el-form-item label="客户名称" required>
              <el-input v-model="form.clientName" placeholder="客户名称" maxlength="200" />
            </el-form-item>
            <el-form-item label="客户类型" required>
              <el-select v-model="form.clientType" style="width: 100%">
                <el-option label="境内" value="domestic" />
                <el-option label="境外" value="overseas" />
              </el-select>
            </el-form-item>
            <el-form-item label="统一信用代码">
              <el-input v-model="form.creditCode" placeholder="统一社会信用代码" maxlength="50" />
            </el-form-item>
            <el-form-item label="注册资本">
              <el-input v-model="form.registeredCapital" placeholder="注册资本" maxlength="100" />
            </el-form-item>
            <el-form-item label="注册地">
              <el-input v-model="form.registeredAddress" placeholder="注册地址" maxlength="500" />
            </el-form-item>
            <el-form-item label="法定代表人">
              <el-input v-model="form.legalRepresentative" placeholder="法定代表人" maxlength="100" />
            </el-form-item>
            <el-form-item label="经营范围">
              <el-input v-model="form.businessScope" type="textarea" :rows="2" placeholder="经营范围" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="备注（可选）" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane label="开票信息" name="invoice">
          <el-form :model="form" label-width="130px">
            <el-form-item label="开票抬头">
              <el-input v-model="form.invoiceTitle" placeholder="发票抬头（默认同客户名称）" maxlength="200" />
            </el-form-item>
            <el-form-item label="纳税人识别号">
              <el-input v-model="form.invoiceTaxNo" placeholder="纳税人识别号（通常同统一信用代码）" maxlength="50" />
            </el-form-item>
            <el-form-item label="开户银行">
              <el-input v-model="form.invoiceBankName" placeholder="开户银行及网点" maxlength="200" />
            </el-form-item>
            <el-form-item label="银行账号">
              <el-input v-model="form.invoiceBankAccount" placeholder="银行账号" maxlength="100" />
            </el-form-item>
            <el-form-item label="开票地址">
              <el-input v-model="form.invoiceAddress" placeholder="开票地址" maxlength="500" />
            </el-form-item>
            <el-form-item label="开票电话">
              <el-input v-model="form.invoicePhone" placeholder="开票电话" maxlength="50" />
            </el-form-item>
          </el-form>
        </el-tab-pane>

        <el-tab-pane :label="`联系人（${contacts.length}）`" name="contacts">
          <template v-if="isEdit && form.id">
            <div style="margin-bottom: 8px; text-align: right">
              <el-button v-permission="'business:client:add'" type="primary" size="small" @click="openContactCreate">+ 添加联系人</el-button>
            </div>
            <el-table v-loading="contactsLoading" :data="contacts" border size="small" max-height="320">
              <el-table-column prop="contactName" label="姓名" min-width="90" />
              <el-table-column prop="position" label="职务" min-width="90" show-overflow-tooltip />
              <el-table-column prop="phone" label="电话" min-width="120" show-overflow-tooltip />
              <el-table-column prop="email" label="邮箱" min-width="140" show-overflow-tooltip />
              <el-table-column prop="remark" label="备注" min-width="100" show-overflow-tooltip />
              <el-table-column label="操作" width="110" align="center">
                <template #default="{ row }">
                  <el-button v-permission="'business:client:edit'" link type="primary" size="small" @click="openContactEdit(row)">编辑</el-button>
                  <el-button v-permission="'business:client:edit'" link type="danger" size="small" @click="handleContactDelete(row)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
          <el-empty v-else description="保存客户后即可维护联系人" :image-size="80" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="contactDialogVisible" :title="contactEditingId ? '编辑联系人' : '添加联系人'" width="460px" append-to-body>
      <el-form :model="contactForm" label-width="90px">
        <el-form-item label="姓名" required>
          <el-input v-model="contactForm.contactName" placeholder="联系人姓名" maxlength="100" />
        </el-form-item>
        <el-form-item label="职务">
          <el-input v-model="contactForm.position" placeholder="职务（如 财务负责人）" maxlength="100" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="contactForm.phone" placeholder="联系电话" maxlength="50" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="contactForm.email" placeholder="电子邮箱" maxlength="100" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="contactForm.remark" type="textarea" :rows="2" maxlength="200" placeholder="备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="contactDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="contactSaving" @click="handleContactSave">确定</el-button>
      </template>
    </el-dialog>

    <!-- 客户详情抽屉 -->
    <el-drawer v-model="detailVisible" :title="detailClient?.clientName || '客户详情'" size="560px">
      <div v-if="detailClient" v-loading="detailLoading">
        <div class="detail-actions">
          <el-button v-permission="'business:client:edit'" type="primary" size="small" @click="editFromDetail">编辑客户</el-button>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">基本信息</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="客户编号">{{ detailClient.clientNo }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ detailClient.clientName }}</el-descriptions-item>
            <el-descriptions-item label="客户类型">
              <el-tag :type="typeTagTypes[detailClient.clientType]" size="small">{{ typeLabels[detailClient.clientType] }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="统一信用代码">{{ detailClient.creditCode || '—' }}</el-descriptions-item>
            <el-descriptions-item label="注册资本">{{ detailClient.registeredCapital || '—' }}</el-descriptions-item>
            <el-descriptions-item label="注册地">{{ detailClient.registeredAddress || '—' }}</el-descriptions-item>
            <el-descriptions-item label="法定代表人">{{ detailClient.legalRepresentative || '—' }}</el-descriptions-item>
            <el-descriptions-item label="经营范围">{{ detailClient.businessScope || '—' }}</el-descriptions-item>
            <el-descriptions-item label="备注">{{ detailClient.remark || '—' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">开票信息</div>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="开票抬头">{{ detailClient.invoiceTitle || '—' }}</el-descriptions-item>
            <el-descriptions-item label="纳税人识别号">{{ detailClient.invoiceTaxNo || '—' }}</el-descriptions-item>
            <el-descriptions-item label="开户银行">{{ detailClient.invoiceBankName || '—' }}</el-descriptions-item>
            <el-descriptions-item label="银行账号">{{ detailClient.invoiceBankAccount || '—' }}</el-descriptions-item>
            <el-descriptions-item label="开票地址">{{ detailClient.invoiceAddress || '—' }}</el-descriptions-item>
            <el-descriptions-item label="开票电话">{{ detailClient.invoicePhone || '—' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div class="detail-section">
          <div class="detail-section-title">联系人（{{ detailContacts.length }}）</div>
          <el-table v-if="detailContacts.length" :data="detailContacts" border size="small">
            <el-table-column prop="contactName" label="姓名" min-width="80" />
            <el-table-column prop="position" label="职务" min-width="80" show-overflow-tooltip />
            <el-table-column prop="phone" label="电话" min-width="110" show-overflow-tooltip />
            <el-table-column prop="email" label="邮箱" min-width="130" show-overflow-tooltip />
            <el-table-column prop="remark" label="备注" min-width="80" show-overflow-tooltip />
          </el-table>
          <div v-else class="detail-empty">暂无联系人</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.toolbar-filters { display: flex; align-items: center; }
.client-table :deep(tbody tr) { cursor: pointer; }
.detail-actions { margin-bottom: 12px; text-align: right; }
.detail-section { margin-bottom: 20px; }
.detail-section-title { font-weight: 600; color: #1f2937; margin-bottom: 8px; }
.detail-empty { color: #9ca3af; font-size: 13px; }
</style>
