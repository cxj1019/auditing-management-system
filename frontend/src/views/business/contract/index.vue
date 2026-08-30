<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { pageProjects } from '@/api/project'
import { listBusinessTypes } from '@/api/businessType'
import { getUserOptions } from '@/api/user'
import {
  pageContracts,
  createContract,
  updateContract,
  changeContractStatus,
  deleteContract,
  listAttachments,
  uploadAttachment,
  downloadAttachment,
  deleteAttachment,
} from '@/api/contract'
import { getContractAttPreviewUrl } from '@/api/contract'
import AttachmentLink from '@/components/AttachmentLink.vue'
import type { ContractAttachmentItem, ContractItem, ContractRequest, ContractStatus, ProjectItem, UserOption, BusinessTypeItem } from '@/types'

// ---------- 状态展示 ----------
const statusLabels: Record<number, string> = { 0: '草稿', 1: '执行中', 2: '已完成', 3: '已终止' }
const statusTagTypes: Record<number, 'info' | 'primary' | 'success' | 'danger'> = {
  0: 'info',
  1: 'primary',
  2: 'success',
  3: 'danger',
}
/** 业务类型字典（收入型且有字号的业务，字号按其解析） */
const bizDict = ref<BusinessTypeItem[]>([])
const bizOptions = computed(() => bizDict.value.filter((b) => b.bizNature === '收入型' && b.noChar))
const projectTypesOfBiz = computed(() => [...new Set(bizOptions.value.map((b) => b.projectType))])

async function loadBizDict(): Promise<void> {
  bizDict.value = await listBusinessTypes('收入型')
}

function onBizTypeChange(): void {
  // 业务类型决定合同类型（=项目类型）
  const row = bizOptions.value.find((b) => b.bizType === form.bizType)
  if (row) {
    form.contractType = row.projectType
  }
}

// ---------- 列表查询 ----------
const loading = ref(false)
const records = ref<ContractItem[]>([])
const total = ref(0)
const query = reactive({
  current: 1,
  size: 10,
  name: '',
  clientName: '',
  keeperName: '',
  status: undefined as ContractStatus | undefined,
})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    const data = await pageContracts(query)
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

function handleReset(): void {
  query.name = ''
  query.clientName = ''
  query.keeperName = ''
  query.status = undefined
  handleSearch()
}

// ---------- 新增/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<ContractRequest>({
  id: undefined,
  projectId: 0,
  name: '',
  contractType: '审计',
  amount: 0,
  signDate: '',
  serviceStart: '',
  serviceEnd: '',
  keeperName: '',
  remark: '',
})
/** 可选项目（进行中） */
const projectOptions = ref<ProjectItem[]>([])
/** 在册人员选项（供合同保管人下拉选择） */
const userOptions = ref<UserOption[]>([])
/** 客户随项目带出：当前所选项目的客户名称 */
const selectedProjectClient = computed(() =>
  projectOptions.value.find((p) => p.id === form.projectId)?.clientName || '')

async function loadProjectOptions(): Promise<void> {
  const data = await pageProjects({ current: 1, size: 200, status: 0 })
  projectOptions.value = data.records
}

async function loadUserOptions(): Promise<void> {
  userOptions.value = await getUserOptions()
}

function openCreate(): void {
  loadBizDict()
  isEdit.value = false
  Object.assign(form, {
    id: undefined,
    projectId: undefined,
    name: '',
    contractType: '审计',
    amount: 0,
    signDate: '',
    serviceStart: '',
    serviceEnd: '',
    keeperName: '',
    remark: '',
  })
  loadProjectOptions()
  loadUserOptions()
  dialogVisible.value = true
}

function openEdit(row: ContractItem): void {
  loadBizDict()
  isEdit.value = true
  Object.assign(form, {
    id: row.id,
    projectId: row.projectId,
    name: row.name,
    contractType: row.contractType,
    amount: row.amount,
    signDate: row.signDate,
    serviceStart: row.serviceStart || '',
    serviceEnd: row.serviceEnd || '',
    keeperName: row.keeperName,
    remark: row.remark,
  })
  loadUserOptions()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    // 服务期间未填的字段归一化为 undefined(未约定期间)
    const payload = {
      ...form,
      serviceStart: form.serviceStart || undefined,
      serviceEnd: form.serviceEnd || undefined,
    }
    if (isEdit.value) {
      await updateContract(payload)
      ElMessage.success('修改成功')
    } else {
      await createContract(payload)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

// ---------- 状态流转 ----------
async function handleChangeStatus(row: ContractItem, target: number, actionName: string): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定将合同「${row.name}」${actionName}吗？`, '状态流转', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await changeContractStatus(row.id, target)
    ElMessage.success('操作成功')
    fetchList()
  } catch {
    // 用户取消
  }
}

// ---------- 删除 ----------
async function handleDelete(row: ContractItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除草稿合同「${row.name}」吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteContract(row.id)
    ElMessage.success('删除成功')
    fetchList()
  } catch {
    // 用户取消
  }
}

onMounted(fetchList)

// ---------- 附件管理 ----------
const attDialogVisible = ref(false)
const attContractId = ref<number | null>(null)
const attContractNo = ref('')
const attLoading = ref(false)
const attUploading = ref(false)
const attList = ref<ContractAttachmentItem[]>([])

function openAttachments(row: ContractItem): void {
  attContractId.value = row.id
  attContractNo.value = row.contractNo
  attDialogVisible.value = true
  fetchAttachments()
}

async function fetchAttachments(): Promise<void> {
  if (!attContractId.value) return
  attLoading.value = true
  try {
    attList.value = await listAttachments(attContractId.value)
  } finally {
    attLoading.value = false
  }
}

async function handleUpload(options: UploadRequestOptions): Promise<void> {
  if (!attContractId.value) return
  attUploading.value = true
  try {
    await uploadAttachment(attContractId.value, options.file)
    ElMessage.success('上传成功')
    fetchAttachments()
  } finally {
    attUploading.value = false
  }
}

async function handleDownloadAtt(att: ContractAttachmentItem): Promise<void> {
  if (!attContractId.value) return
  await downloadAttachment(attContractId.value, att.id, att.fileName)
}

async function handleDeleteAtt(att: ContractAttachmentItem): Promise<void> {
  if (!attContractId.value) return
  try {
    await ElMessageBox.confirm(`确定删除附件「${att.fileName}」吗？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await deleteAttachment(attContractId.value, att.id)
    ElMessage.success('删除成功')
    fetchAttachments()
  } catch {
    // 用户取消
  }
}
</script>

<template>
  <div class="page-container">
    <el-card shadow="never">
      <!-- 筛选栏 -->
      <div class="table-toolbar">
        <div class="toolbar-filters">
          <el-input v-model="query.name" placeholder="合同名称" clearable style="width: 160px" @keyup.enter="handleSearch" />
          <el-input v-model="query.clientName" placeholder="客户名称" clearable style="width: 160px; margin-left: 8px" @keyup.enter="handleSearch" />
          <el-input v-model="query.keeperName" placeholder="合同保管人" clearable style="width: 140px; margin-left: 8px" @keyup.enter="handleSearch" />
          <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px; margin-left: 8px">
            <el-option v-for="(label, code) in statusLabels" :key="code" :label="label" :value="Number(code)" />
          </el-select>
          <el-button type="primary" style="margin-left: 8px" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
        <el-button v-permission="'business:contract:add'" type="primary" @click="openCreate">
          新增合同
        </el-button>
      </div>

      <!-- 合同表格 -->
      <el-table v-loading="loading" :data="records" border stripe>
        <el-table-column prop="contractNo" label="合同字号" min-width="190" show-overflow-tooltip />
        <el-table-column label="所属项目" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">{{ row.projectNo ? `${row.projectNo} ${row.projectName}` : '—' }}</template>
        </el-table-column>
        <el-table-column prop="name" label="合同名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="clientName" label="客户名称" min-width="150" show-overflow-tooltip />
        <el-table-column label="业务类型" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizType || row.contractType }}</template>
        </el-table-column>
        <el-table-column label="金额（元）" min-width="120" align="right">
          <template #default="{ row }">{{ Number(row.amount).toLocaleString('zh-CN', { minimumFractionDigits: 2 }) }}</template>
        </el-table-column>
        <el-table-column prop="keeperName" label="合同保管人" width="110" />
        <el-table-column prop="signDate" label="签约日期" width="110" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagTypes[row.status]" size="small">{{ statusLabels[row.status] }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" size="small" @click="openAttachments(row)">附件</el-button>
            <el-button v-if="row.status === 0" v-permission="'business:contract:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0" v-permission="'business:contract:status'" link type="primary" size="small" @click="handleChangeStatus(row, 1, '开始执行')">开始执行</el-button>
            <el-button v-if="row.status === 1" v-permission="'business:contract:status'" link type="success" size="small" @click="handleChangeStatus(row, 2, '标记为已完成')">完成</el-button>
            <el-button v-if="row.status === 1" v-permission="'business:contract:status'" link type="warning" size="small" @click="handleChangeStatus(row, 3, '终止')">终止</el-button>
            <el-button v-if="row.status === 0" v-permission="'business:contract:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑合同' : '新增合同'" width="640px">
      <el-form :model="form" label-width="100px">
        <el-form-item label="所属项目" required>
          <el-select v-model="form.projectId" placeholder="选择进行中的项目" filterable style="width: 100%">
            <el-option
              v-for="p in projectOptions"
              :key="p.id"
              :label="`${p.projectNo} | ${p.name}`"
              :value="p.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="所属客户">
          <el-input :model-value="selectedProjectClient" readonly placeholder="选择项目后自动带出" />
        </el-form-item>
        <el-form-item label="合同名称" required>
          <el-input v-model="form.name" placeholder="合同名称" maxlength="200" />
        </el-form-item>
        <el-form-item label="业务类型" required>
          <div style="width: 100%">
            <el-select v-model="form.bizType" placeholder="选择业务类型（按附件业务配置）" filterable style="width: 100%" @change="onBizTypeChange">
              <el-option-group v-for="pt in projectTypesOfBiz" :key="pt" :label="pt">
                <el-option
                  v-for="b in bizOptions.filter((x) => x.projectType === pt)"
                  :key="b.id"
                  :label="b.bizType"
                  :value="b.bizType"
                />
              </el-option-group>
            </el-select>
            <div class="field-tip">合同类型随业务类型带出（{{ form.contractType || '未选择' }}）；字号按业务类型的字号类型自动编号，流水按字号+年份独立递增</div>
          </div>
        </el-form-item>
        <el-form-item label="合同金额（元）" required>
          <el-input-number v-model="form.amount" :min="0" :precision="2" :step="1000" style="width: 100%" />
        </el-form-item>
        <el-form-item label="签约日期" required>
          <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="服务期限" required>
          <el-date-picker v-model="form.serviceStart" type="date" value-format="YYYY-MM-DD" placeholder="开始日期" style="width: 48%" />
          <span style="margin: 0 4px">至</span>
          <el-date-picker v-model="form.serviceEnd" type="date" value-format="YYYY-MM-DD" placeholder="结束日期" style="width: 48%" />
        </el-form-item>
        <el-form-item label="合同保管人" required>
          <el-select v-model="form.keeperName" placeholder="选择合同保管人" filterable style="width: 100%">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.nickname ? `${u.nickname} (${u.username})` : u.username"
              :value="u.nickname || u.username"
            />
          </el-select>
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
    <!-- 附件管理弹窗 -->
    <el-dialog v-model="attDialogVisible" :title="`附件管理 - ${attContractNo}`" width="680px">
      <div class="table-toolbar">
        <span class="section-title">扫描件清单</span>
        <el-upload
          v-permission="'business:contract:edit'"
          :show-file-list="false"
          :http-request="handleUpload"
          accept=".pdf,.jpg,.jpeg,.png,.doc,.docx"
        >
          <el-button type="primary" :loading="attUploading">上传扫描件</el-button>
        </el-upload>
      </div>

      <el-table v-loading="attLoading" :data="attList" border stripe>
        <el-table-column label="文件名" min-width="220">
          <template #default="{ row }">
            <AttachmentLink :file-name="row.fileName" :content-type="row.contentType" :fetch-signed-url="() => getContractAttPreviewUrl(attContractId!, row.id)" />
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100" align="right">
          <template #default="{ row }">{{ (row.fileSize / 1024).toFixed(1) }} KB</template>
        </el-table-column>
        <el-table-column prop="createBy" label="上传人" width="100" />
        <el-table-column prop="createTime" label="上传时间" width="170" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleDownloadAtt(row)">下载</el-button>
            <el-button v-permission="'business:contract:edit'" link type="danger" size="small" @click="handleDeleteAtt(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar-filters {
  display: flex;
  align-items: center;
}
.field-tip {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
