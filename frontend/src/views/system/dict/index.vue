<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listBusinessTypes,
  createBusinessType,
  updateBusinessType,
  deleteBusinessType,
} from '@/api/businessType'
import type { BusinessTypeItem } from '@/types'

const natures = ['收入型', '无收入型']
const noChars = ['审', '验', '咨', '代', '商', '评']
const feeFreqs = ['次', '月度', '季度', '年度']

const loading = ref(false)
const records = ref<BusinessTypeItem[]>([])
const query = reactive({ bizNature: '', keyword: '' })

const filtered = computed(() =>
  records.value.filter((r) =>
    (!query.bizNature || r.bizNature === query.bizNature)
    && (!query.keyword || r.projectType.includes(query.keyword) || r.bizType.includes(query.keyword))))

const natureCounts = computed(() => {
  const map: Record<string, number> = {}
  for (const r of records.value) map[r.bizNature] = (map[r.bizNature] || 0) + 1
  return map
})

async function fetchList(): Promise<void> {
  loading.value = true
  try {
    records.value = await listBusinessTypes()
  } finally {
    loading.value = false
  }
}

// ---------- 新增/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const form = reactive<Partial<BusinessTypeItem>>({})

function openCreate(): void {
  isEdit.value = false
  Object.assign(form, {
    id: undefined, bizNature: '收入型', projectType: '', bizType: '', bizDesc: '',
    noChar: '', feeFreq: '次', taxCode: '', taxClass: '', invoiceItem: '', sort: undefined,
  })
  dialogVisible.value = true
}

function openEdit(row: BusinessTypeItem): void {
  isEdit.value = true
  Object.assign(form, { ...row })
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  if (!form.bizNature || !form.projectType || !form.bizType) {
    ElMessage.warning('项目性质/项目类型/业务类型均不能为空')
    return
  }
  saving.value = true
  try {
    if (isEdit.value && form.id) {
      await updateBusinessType(form as BusinessTypeItem)
      ElMessage.success('修改成功')
    } else {
      await createBusinessType(form)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    fetchList()
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: BusinessTypeItem): Promise<void> {
  try {
    await ElMessageBox.confirm(`确定删除业务类型「${row.projectType} / ${row.bizType}」吗？`, '删除确认', { type: 'warning' })
    await deleteBusinessType(row.id)
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
          <el-select v-model="query.bizNature" placeholder="项目性质" clearable style="width: 120px">
            <el-option v-for="n in natures" :key="n" :label="`${n}（${natureCounts[n] || 0}）`" :value="n" />
          </el-select>
          <el-input v-model="query.keyword" placeholder="项目类型/业务类型" clearable style="width: 200px; margin-left: 8px" />
          <span class="total-hint">共 {{ filtered.length }} 条</span>
        </div>
        <el-button v-permission="'system:dict:add'" type="primary" @click="openCreate">新增字典</el-button>
      </div>

      <el-table v-loading="loading" :data="filtered" border stripe>
        <el-table-column prop="bizNature" label="项目性质" width="90">
          <template #default="{ row }">
            <el-tag :type="row.bizNature === '收入型' ? 'success' : 'info'" size="small">{{ row.bizNature }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="projectType" label="项目类型" min-width="130" show-overflow-tooltip />
        <el-table-column prop="bizType" label="业务类型" min-width="160" show-overflow-tooltip />
        <el-table-column prop="bizDesc" label="业务说明" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.bizDesc || '—' }}</template>
        </el-table-column>
        <el-table-column prop="noChar" label="字号" width="70" align="center">
          <template #default="{ row }">{{ row.noChar || '—' }}</template>
        </el-table-column>
        <el-table-column prop="feeFreq" label="收费频度" width="90" align="center">
          <template #default="{ row }">{{ row.feeFreq || '—' }}</template>
        </el-table-column>
        <el-table-column prop="invoiceItem" label="发票品名" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.invoiceItem || '—' }}</template>
        </el-table-column>
        <el-table-column prop="taxClass" label="税收分类" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taxClass || '—' }}</template>
        </el-table-column>
        <el-table-column prop="taxCode" label="税收编码" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ row.taxCode || '—' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'system:dict:edit'" link type="primary" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="'system:dict:delete'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑业务类型' : '新增业务类型'" width="640px">
      <el-form :model="form" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="项目性质" required label-width="90px">
              <el-select v-model="form.bizNature" style="width: 100%">
                <el-option v-for="n in natures" :key="n" :label="n" :value="n" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="项目类型" required label-width="90px">
              <el-input v-model="form.projectType" placeholder="如 财务报表审计" maxlength="50" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="业务类型" required>
          <el-input v-model="form.bizType" placeholder="如 年度审计" maxlength="100" />
        </el-form-item>
        <el-form-item label="业务说明">
          <el-input v-model="form.bizDesc" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="字号类型" label-width="90px">
              <el-select v-model="form.noChar" clearable placeholder="不开票可留空" style="width: 100%">
                <el-option v-for="c in noChars" :key="c" :label="c" :value="c" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="收费频度" label-width="90px">
              <el-select v-model="form.feeFreq" clearable style="width: 100%">
                <el-option v-for="f in feeFreqs" :key="f" :label="f" :value="f" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">开票要素</el-divider>
        <el-form-item label="发票品名">
          <el-input v-model="form.invoiceItem" maxlength="100" />
        </el-form-item>
        <el-form-item label="税收分类">
          <el-input v-model="form.taxClass" maxlength="100" />
        </el-form-item>
        <el-form-item label="税收编码">
          <el-input v-model="form.taxCode" maxlength="30" />
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
.toolbar-filters {
  display: flex;
  align-items: center;
}

.total-hint {
  font-size: 12px;
  color: #9ca3af;
  margin-left: 12px;
}
</style>
