<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createReimbursement, updateReimbursement, submitReimbursement, getReimbItems,
  listReimbAttachments, uploadReimbAttachment, deleteReimbAttachment,
} from '@/api/reimbursement'
import { listExpenseCategories } from '@/api/expenseCategory'
import { projectOptions as projectOptionsApi } from '@/api/project'
import { compressImage } from '@/utils/imageCompress'
import { useUserStore } from '@/stores/user'
import type { ProjectItem, ReimbursementAttachmentItem, ReimbursementItemData } from '@/types'

const router = useRouter()
const userStore = useUserStore()

const FALLBACK_CATEGORIES = ['差旅费', '交通费', '办公费', '餐饮费', '其他']
const TAX_RATE_PRESETS = [13, 9, 6, 3, 1.5, 0]

const categories = ref<string[]>([...FALLBACK_CATEGORIES])
const projectOptions = ref<ProjectItem[]>([])

const saving = ref(false)
const submitting = ref(false)
const draftId = ref<number | null>(null)
/** 已保存的明细行 ID（附件按行关联） */
const itemIds = ref<number[]>([])
const attachments = ref<ReimbursementAttachmentItem[]>([])
const uploadingItem = ref<number | null>(null)

const form = reactive({
  projectId: undefined as number | undefined,
  title: '',
  items: [] as ReimbursementItemData[],
})

const canCreate = computed(() => userStore.hasPermission('business:reimbursement:add'))

function emptyItem(): ReimbursementItemData {
  return {
    category: categories.value[0] || '其他',
    amount: 0,
    expenseDate: new Date().toISOString().slice(0, 10),
    description: '',
    invoiceNumber: '',
    invoiceType: 'none',
    taxRate: undefined,
    taxAmount: undefined,
    taxAmountManual: false,
    projectId: form.projectId ?? undefined,
    billable: false,
  }
}

function addItem(): void {
  form.items.push(emptyItem())
}

function removeItem(index: number): void {
  form.items.splice(index, 1)
}

function onSpecialChange(item: ReimbursementItemData, checked: boolean): void {
  item.invoiceType = checked ? 'vat_special' : 'none'
  if (!checked) {
    item.taxRate = undefined
    item.taxAmount = undefined
    item.taxAmountManual = false
  } else {
    autoTaxAmount(item)
  }
}

function onTaxRateSelect(item: ReimbursementItemData, value: unknown): void {
  item.taxRate = value == null || value === '' ? undefined : Number(value)
  autoTaxAmount(item)
}

function autoTaxAmount(item: ReimbursementItemData): void {
  if (item.taxAmountManual) return
  if (item.invoiceType !== 'vat_special' || !item.taxRate || !item.amount) {
    item.taxAmount = undefined
    return
  }
  item.taxAmount = Math.round((item.amount / (1 + item.taxRate / 100)) * item.taxRate * 100) / 100
}

const totalAmount = computed(() => form.items.reduce((s, i) => s + Number(i.amount || 0), 0))

function projectLabel(p: ProjectItem): string {
  return `${p.projectNo} | ${p.name}`
}

async function loadDicts(): Promise<void> {
  try {
    const list = await listExpenseCategories()
    if (list.length) categories.value = list.filter((c) => c.status === 1).map((c) => c.name)
  } catch { /* 回退内置清单 */ }
  try {
    projectOptions.value = await projectOptionsApi()
  } catch { /* 项目选项失败时允许单头不带项目 */ }
}

/** 同步后端明细行 ID，保证附件按行关联 */
async function syncItemIds(id: number): Promise<void> {
  const items = await getReimbItems(id)
  itemIds.value = items.map((i) => i.id)
  attachments.value = await listReimbAttachments(id)
}

function itemAtts(itemIndex: number): ReimbursementAttachmentItem[] {
  const itemId = itemIds.value[itemIndex]
  if (!itemId) return []
  return attachments.value.filter((a) => a.itemId === itemId)
}

/** 保存草稿：新建后切换为编辑模式，行上传按钮随之出现 */
async function handleSave(): Promise<void> {
  if (!form.items.length) {
    ElMessage.warning('至少需要一条费用明细')
    return
  }
  saving.value = true
  try {
    if (draftId.value) {
      await updateReimbursement(draftId.value, form)
      await syncItemIds(draftId.value)
      ElMessage.success('草稿已更新')
    } else {
      draftId.value = await createReimbursement(form)
      await syncItemIds(draftId.value)
      ElMessage.success('草稿已保存，现在可以为每行拍照上传发票')
    }
  } finally {
    saving.value = false
  }
}

function pickCamera(itemIndex: number): void {
  const input = document.querySelector<HTMLInputElement>(`#m-cam-${itemIndex}`)
  input?.click()
}

function pickGallery(itemIndex: number): void {
  const input = document.querySelector<HTMLInputElement>(`#m-gallery-${itemIndex}`)
  input?.click()
}

async function onFilesPicked(event: Event, itemIndex: number): Promise<void> {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  input.value = ''
  if (!draftId.value || !files.length) return
  const itemId = itemIds.value[itemIndex]
  if (!itemId) {
    ElMessage.warning('请先保存草稿再上传发票')
    return
  }
  uploadingItem.value = itemIndex
  try {
    for (const raw of files) {
      const file = await compressImage(raw)
      await uploadReimbAttachment(draftId.value, file, itemId)
    }
    ElMessage.success(`已上传 ${files.length} 张`)
    attachments.value = await listReimbAttachments(draftId.value)
  } finally {
    uploadingItem.value = null
  }
}

async function handleDeleteAtt(att: ReimbursementAttachmentItem): Promise<void> {
  if (!draftId.value) return
  try {
    await ElMessageBox.confirm(`确定删除「${att.fileName}」吗？`, '删除确认', { type: 'warning' })
    await deleteReimbAttachment(draftId.value, att.id)
    attachments.value = await listReimbAttachments(draftId.value)
  } catch { /* 取消 */ }
}

async function handleSubmit(): Promise<void> {
  if (!draftId.value) {
    ElMessage.warning('请先保存草稿')
    return
  }
  const noRate = form.items.find((i) => i.invoiceType === 'vat_special' && !(i.taxRate && i.taxRate > 0))
  if (noRate) {
    ElMessage.warning(`增值税专用发票的明细「${noRate.category}」必须填写税率`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `提交后进入审批流程，确定提交报销单吗？`, '提交确认', { type: 'warning' },
    )
  } catch { return }
  submitting.value = true
  try {
    await submitReimbursement(draftId.value)
    ElMessage.success('已提交审批')
    router.push('/m/approval')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  if (!canCreate.value) return
  loadDicts()
  addItem()
})
</script>

<template>
  <div class="m-page">
    <div class="m-header">
      <span class="m-title">我要报销</span>
      <el-button v-if="canCreate" size="small" text type="primary" @click="router.push('/m/approval')">我的审批</el-button>
    </div>

    <div v-if="!canCreate" class="m-empty">您没有新建报销的权限</div>

    <template v-else>
      <div v-if="draftId" class="m-draft-tip">草稿已保存，补充完发票照片后即可提交</div>

      <!-- 单头 -->
      <div class="m-card">
        <div class="m-field">
          <label>报销标题</label>
          <el-input v-model="form.title" placeholder="如：7月差旅报销" maxlength="60" />
        </div>
        <div class="m-field">
          <label>关联项目（可选）</label>
          <el-select v-model="form.projectId" clearable filterable placeholder="选择项目" style="width: 100%">
            <el-option v-for="p in projectOptions" :key="p.id" :label="projectLabel(p)" :value="p.id" />
          </el-select>
        </div>
      </div>

      <!-- 费用明细 -->
      <div v-for="(item, idx) in form.items" :key="idx" class="m-card">
        <div class="m-item-head">
          <span class="m-item-no">明细 {{ idx + 1 }}</span>
          <el-button v-if="form.items.length > 1" link type="danger" size="small" @click="removeItem(idx)">删除</el-button>
        </div>

        <div class="m-field-row">
          <div class="m-field">
            <label>类别</label>
            <el-select v-model="item.category" style="width: 100%">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
          </div>
          <div class="m-field">
            <label>金额（元）</label>
            <el-input-number v-model="item.amount" :min="0" :precision="2" :controls="false" style="width: 100%" placeholder="0.00" />
          </div>
        </div>

        <div class="m-field">
          <label>费用日期</label>
          <el-date-picker v-model="item.expenseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </div>

        <div class="m-field">
          <label>事由说明</label>
          <el-input v-model="item.description" placeholder="简述费用用途" maxlength="200" />
        </div>

        <div class="m-field-row">
          <div class="m-field m-check">
            <el-checkbox :model-value="item.invoiceType === 'vat_special'" @change="(v: boolean) => onSpecialChange(item, v)">增值税专票</el-checkbox>
          </div>
          <div class="m-field" v-if="item.invoiceType === 'vat_special'">
            <label>税率</label>
            <el-select :model-value="item.taxRate" allow-create filterable placeholder="必选" style="width: 100%" @update:model-value="(v: unknown) => onTaxRateSelect(item, v)">
              <el-option v-for="r in TAX_RATE_PRESETS" :key="r" :label="r + '%'" :value="r" />
            </el-select>
          </div>
          <div class="m-field m-check">
            <el-checkbox v-model="item.billable">可向客户收</el-checkbox>
          </div>
        </div>

        <!-- 拍照 / 相册上传（仅草稿保存后可用） -->
        <div class="m-photo-section">
          <template v-if="draftId">
            <div class="m-att-list">
              <div v-for="att in itemAtts(idx)" :key="att.id" class="m-att">
                <span class="m-att-name">{{ att.fileName }}</span>
                <el-button link type="danger" size="small" @click="handleDeleteAtt(att)">删除</el-button>
              </div>
            </div>
            <div class="m-photo-btns">
              <el-button size="small" type="primary" :loading="uploadingItem === idx" @click="pickCamera(idx)">📷 拍照传发票</el-button>
              <el-button size="small" :loading="uploadingItem === idx" @click="pickGallery(idx)">从相册选择</el-button>
            </div>
            <input :id="`m-cam-${idx}`" type="file" accept="image/*" capture="environment" multiple hidden @change="onFilesPicked($event, idx)">
            <input :id="`m-gallery-${idx}`" type="file" accept="image/*" multiple hidden @change="onFilesPicked($event, idx)">
          </template>
          <div v-else class="m-photo-tip">保存草稿后可拍照上传发票</div>
        </div>
      </div>

      <el-button class="m-add" plain @click="addItem">＋ 加一行明细</el-button>

      <div class="m-footer">
        <div class="m-total">合计：<b>{{ totalAmount.toFixed(2) }}</b> 元</div>
        <div class="m-footer-btns">
          <el-button :loading="saving" @click="handleSave">保存草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交审批</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<style scoped>
.m-page { max-width: 640px; margin: 0 auto; padding: 12px; padding-bottom: 90px; }
.m-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.m-title { font-size: 18px; font-weight: 600; }
.m-draft-tip { color: #e6a23c; font-size: 13px; margin-bottom: 10px; }
.m-card { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px; margin-bottom: 10px; }
.m-item-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.m-item-no { font-weight: 600; font-size: 14px; }
.m-field { flex: 1; margin-bottom: 10px; }
.m-field label { display: block; font-size: 12px; color: #6b7280; margin-bottom: 4px; }
.m-field-row { display: flex; gap: 10px; }
.m-check { display: flex; align-items: flex-end; padding-bottom: 10px; }
.m-photo-section { border-top: 1px dashed #e5e7eb; padding-top: 8px; margin-top: 2px; }
.m-photo-btns { display: flex; gap: 8px; }
.m-photo-btns .el-button { flex: 1; }
.m-photo-tip { color: #9ca3af; font-size: 12px; text-align: center; }
.m-att-list { margin-bottom: 6px; }
.m-att { display: flex; justify-content: space-between; align-items: center; font-size: 13px; color: #374151; padding: 3px 0; }
.m-att-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.m-add { width: 100%; margin-bottom: 12px; }
.m-footer { position: fixed; bottom: 0; left: 0; right: 0; max-width: 640px; margin: 0 auto; background: #fff; border-top: 1px solid #e5e7eb; padding: 10px 12px; display: flex; justify-content: space-between; align-items: center; }
.m-total { font-size: 14px; }
.m-total b { color: #f56c6c; font-size: 17px; }
.m-footer-btns { display: flex; gap: 8px; }
.m-empty { text-align: center; color: #9ca3af; padding: 24px 0; }
</style>
