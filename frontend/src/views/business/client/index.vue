<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageClients, createClient, updateClient, deleteClient } from '@/api/client'
import { getDepartmentOptions } from '@/api/user'
import type { ClientItem, ClientRequest, DepartmentItem } from '@/types'

const clientTypes = ['境内', '境外']
const typeLabels: Record<string, string> = { domestic: '境内', overseas: '境外' }
const typeTagTypes: Record<string, 'primary' | 'warning'> = { domestic: 'primary', overseas: 'warning' }

const loading = ref(false)
const records = ref<ClientItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1, size: 10,
  keyword: '', clientType: '', deptId: undefined as number | undefined,
})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageClients({
      ...query,
      keyword: query.keyword || undefined,
      clientType: query.clientType || undefined,
      deptId: query.deptId || undefined,
    })
    records.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}

function handleSearch(): void { query.current = 1; fetchList() }
function handleReset(): void {
  query.keyword = ''; query.clientType = ''; query.deptId = undefined; handleSearch()
}

const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<ClientRequest & { id?: number }>({
  clientName: '', clientType: 'domestic', deptId: 0,
  creditCode: '', registeredCapital: '', registeredAddress: '',
  legalRepresentative: '', businessScope: '',
  contactPerson: '', contactPhone: '',
  invoiceTitle: '', invoiceTaxNo: '', invoiceBankName: '',
  invoiceBankAccount: '', invoiceAddress: '', invoicePhone: '',
  remark: '',
})
const deptOptions = ref<DepartmentItem[]>([])

async function loadDeptOptions(): Promise<void> {
  // 用免权限的 /departments/options，普通员工也能打开本页
  deptOptions.value = await getDepartmentOptions()
}

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, {
    clientName: '', clientType: 'domestic', deptId: 0,
    creditCode: '', registeredCapital: '', registeredAddress: '',
    legalRepresentative: '', businessScope: '',
    contactPerson: '', contactPhone: '',
    invoiceTitle: '', invoiceTaxNo: '', invoiceBankName: '',
    invoiceBankAccount: '', invoiceAddress: '', invoicePhone: '',
    remark: '',
  })
  loadDeptOptions()
  dialogVisible.value = true
}

function openEdit(row: ClientItem): void {
  isEdit.value = true
  Object.assign(form, {
    clientName: row.clientName, clientType: row.clientType, deptId: row.deptId,
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
  loadDeptOptions()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    if (isEdit.value && form.id) {
      await updateClient(form as ClientRequest & { id: number })
      ElMessage.success('修改成功')
    } else {
      await createClient(form)
      ElMessage.success('登记成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally { saving.value = false }
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
            <el-option v-for="t in clientTypes" :key="t" :label="t" :value="t" />
          </el-select>
          <el-select v-model="query.deptId" placeholder="部门" clearable style="width: 140px; margin-left: 8px">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button v-permission="'business:client:add'" type="primary" @click="openCreate">登记客户</el-button>
      </div>

      <el-table v-loading="loading" :data="records" border stripe>
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
        <el-table-column prop="contactPerson" label="联系人" width="100" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status !== 3" v-permission="'business:client:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status !== 3" v-permission="'business:client:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
          @current-change="fetchList" @size-change="handleSearch" />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑客户' : '登记客户'" width="640px">
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
        <el-form-item label="所属部门" required>
          <el-select v-model="form.deptId" placeholder="选择部门" style="width: 100%">
            <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
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
        <el-form-item label="联系人">
          <el-input v-model="form.contactPerson" placeholder="联系人" maxlength="100" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" placeholder="联系电话" maxlength="50" />
        </el-form-item>
        <el-divider content-position="left">开票信息</el-divider>
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
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="备注（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-filters { display: flex; align-items: center; }
</style>
