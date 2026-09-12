<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listSchedules, createSchedule } from '@/api/schedule'
import { getUserOptions } from '@/api/user'
import { projectOptions as projectOptionsApi } from '@/api/project'
import { useUserStore } from '@/stores/user'
import type { ProjectItem, ScheduleItem, UserOption } from '@/types'

/**
 * 手机端日程周板：成员 × 日期矩阵，日期列可左右滑动，左列成员固定。
 * 点日程块看详情，点空格直接新建该成员当天日程。
 */
const userStore = useUserStore()

const scheduleTypes = ['会议', '现场审计', '报告编制', '差旅', '加班', '访问', '内勤', '居家', '休假', '其他']

const canCreate = computed(() => userStore.hasPermission('business:schedule:add'))

// ---------- 周-------------
const weekStart = ref<Date>(mondayOf(new Date()))

function mondayOf(d: Date): Date {
  const date = new Date(d.getFullYear(), d.getMonth(), d.getDate())
  const day = date.getDay() || 7 // 周日算第 7 天
  date.setDate(date.getDate() - (day - 1))
  return date
}

function fmt(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const todayStr = fmt(new Date())

const days = computed(() =>
  Array.from({ length: 7 }, (_, i) => {
    const d = new Date(weekStart.value)
    d.setDate(d.getDate() + i)
    return {
      date: fmt(d),
      md: `${String(d.getMonth() + 1).padStart(2, '0')}/${String(d.getDate()).padStart(2, '0')}`,
      week: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][i],
      isToday: fmt(d) === todayStr,
    }
  }),
)

const weekLabel = computed(() => {
  const s = days.value[0]
  const e = days.value[6]
  return `${s.md.slice(0, 2)}月${Number(s.md.slice(3))}日 - ${Number(e.md.slice(3))}日`
})

function shiftWeek(delta: number): void {
  const d = new Date(weekStart.value)
  d.setDate(d.getDate() + delta * 7)
  weekStart.value = d
}

function goToday(): void {
  weekStart.value = mondayOf(new Date())
}

// ---------- 成员 ----------
interface Member { id: number; name: string; color: string; initial: string }

function avatarColor(name: string): string {
  let hash = 0
  for (const ch of name) hash = (hash * 31 + ch.charCodeAt(0)) % 360
  return `hsl(${hash}, 55%, 55%)`
}

const members = ref<Member[]>([])

// ---------- 日程 ----------
const schedules = ref<ScheduleItem[]>([])
const loading = ref(false)

/** key: `${userId}|${date}` */
const cellMap = ref<Map<string, ScheduleItem[]>>(new Map())

async function fetchAll(): Promise<void> {
  loading.value = true
  try {
    const [list, users] = await Promise.all([
      listSchedules({ startDate: days.value[0].date, endDate: days.value[6].date }),
      getUserOptions().catch(() => [] as UserOption[]),
    ])
    schedules.value = list

    const map = new Map<number, Member>()
    const optById = new Map(users.map((u) => [u.id, u]))
    for (const s of list) {
      if (!s.userId || map.has(s.userId)) continue
      const opt = optById.get(s.userId)
      const name = opt?.nickname || s.creatorName || `用户${s.userId}`
      map.set(s.userId, { id: s.userId, name, color: avatarColor(name), initial: name.charAt(0) })
    }
    for (const u of users) {
      if (!map.has(u.id)) {
        const name = u.nickname || u.username
        map.set(u.id, { id: u.id, name, color: avatarColor(name), initial: name.charAt(0) })
      }
    }
    // 本人置顶
    const arr = Array.from(map.values())
    arr.sort((a, b) => (a.id === userStore.userId ? -1 : b.id === userStore.userId ? 1 : a.name.localeCompare(b.name, 'zh')))
    members.value = arr

    const cells = new Map<string, ScheduleItem[]>()
    for (const s of list) {
      // 跨天日程按起止范围铺到每一天
      const start = s.scheduleDate
      const end = s.endDate || s.scheduleDate
      for (const d of days.value) {
        if (d.date >= start && d.date <= end) {
          const key = `${s.userId}|${d.date}`
          if (!cells.has(key)) cells.set(key, [])
          cells.get(key)!.push(s)
        }
      }
    }
    cellMap.value = cells
  } finally {
    loading.value = false
  }
}

function cellOf(userId: number, date: string): ScheduleItem[] {
  return cellMap.value.get(`${userId}|${date}`) || []
}

function itemTime(s: ScheduleItem): string {
  if (s.startTime) return s.startTime.slice(0, 5) + (s.endTime ? '-' + s.endTime.slice(0, 5) : '')
  return '全天'
}

// ---------- 详情 ----------
const detailVisible = ref(false)
const detail = ref<ScheduleItem>()

function openDetail(s: ScheduleItem): void {
  detail.value = s
  detailVisible.value = true
}

// ---------- 新建 ----------
const formVisible = ref(false)
const saving = ref(false)
const projectOptions = ref<ProjectItem[]>([])
const form = reactive({
  userId: undefined as number | undefined,
  projectId: undefined as number | undefined,
  title: '',
  description: '',
  scheduleDate: todayStr,
  startTime: '',
  endTime: '',
  hours: 7,
  type: '会议',
})

function openCreate(userId?: number, date?: string): void {
  Object.assign(form, {
    userId: userId ?? userStore.userId ?? undefined,
    projectId: undefined,
    title: '',
    description: '',
    scheduleDate: date ?? todayStr,
    startTime: '',
    endTime: '',
    hours: 7,
    type: '会议',
  })
  formVisible.value = true
  if (!projectOptions.value.length) {
    projectOptionsApi().then((p) => { projectOptions.value = p }).catch(() => { /* 无项目选项也可建日程 */ })
  }
}

async function handleSave(): Promise<void> {
  if (!form.title.trim()) {
    ElMessage.warning('请填写日程标题')
    return
  }
  saving.value = true
  try {
    await createSchedule({
      userId: form.userId,
      projectId: form.projectId,
      title: form.title,
      description: form.description,
      scheduleDate: form.scheduleDate,
      endDate: form.scheduleDate,
      startTime: form.startTime,
      endTime: form.endTime,
      hours: form.hours,
      type: form.type,
    })
    ElMessage.success('日程已创建')
    formVisible.value = false
    await fetchAll()
  } finally {
    saving.value = false
  }
}

onMounted(fetchAll)
</script>

<template>
  <div class="ms-page">
    <div class="ms-header">
      <span class="ms-title">日程</span>
      <el-button v-if="canCreate" type="primary" circle icon="Plus" @click="openCreate()" />
    </div>

    <!-- 周切换 -->
    <div class="ms-week-bar">
      <el-icon @click="shiftWeek(-1)"><ArrowLeft /></el-icon>
      <span class="ms-week-label" @click="goToday">{{ weekLabel }}</span>
      <el-icon @click="shiftWeek(1)"><ArrowRight /></el-icon>
    </div>

    <!-- 成员 × 日期矩阵：横向滑动 -->
    <div class="ms-board" v-loading="loading">
      <table class="ms-table">
        <thead>
          <tr>
            <th class="ms-member-col">成员</th>
            <th v-for="d in days" :key="d.date" class="ms-day-col" :class="{ today: d.isToday }">
              <div class="ms-day-md" :class="{ today: d.isToday }">{{ d.md }}</div>
              <div class="ms-day-week">{{ d.week }}</div>
            </th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="m in members" :key="m.id">
            <td class="ms-member-col">
              <div class="ms-member">
                <span class="ms-avatar" :style="{ background: m.color }">{{ m.initial }}</span>
                <span class="ms-member-name">{{ m.name }}</span>
              </div>
            </td>
            <td
              v-for="d in days"
              :key="d.date"
              class="ms-day-col"
              :class="{ today: d.isToday }"
              @click="cellOf(m.id, d.date).length === 0 && canCreate && openCreate(m.id, d.date)"
            >
              <div
                v-for="s in cellOf(m.id, d.date)"
                :key="s.id"
                class="ms-chip"
                @click.stop="openDetail(s)"
              >
                <div class="ms-chip-title">{{ s.title }}</div>
                <div class="ms-chip-time">{{ itemTime(s) }}<template v-if="s.hours"> · {{ s.hours }}h</template></div>
              </div>
            </td>
          </tr>
          <tr v-if="!members.length && !loading">
            <td class="ms-empty" :colspan="8">本周暂无成员日程</td>
          </tr>
        </tbody>
      </table>
    </div>
    <div class="ms-hint">← 日期可左右滑动 · 点空格新建 · 点色块看详情 →</div>

    <!-- 详情 -->
    <el-dialog v-model="detailVisible" :title="detail?.title || '日程详情'" :width="320">
      <template v-if="detail">
        <div class="ms-detail-row"><span class="ms-detail-label">日期</span>{{ detail.scheduleDate }}<template v-if="detail.endDate && detail.endDate !== detail.scheduleDate"> ~ {{ detail.endDate }}</template></div>
        <div class="ms-detail-row"><span class="ms-detail-label">时间</span>{{ itemTime(detail) }}<template v-if="detail.hours">（{{ detail.hours }} 小时）</template></div>
        <div class="ms-detail-row"><span class="ms-detail-label">类型</span>{{ detail.type }}</div>
        <div class="ms-detail-row"><span class="ms-detail-label">项目</span>{{ detail.projectName || '—' }}</div>
        <div class="ms-detail-row"><span class="ms-detail-label">说明</span>{{ detail.description || '—' }}</div>
        <div class="ms-detail-row"><span class="ms-detail-label">创建人</span>{{ detail.creatorName || '—' }}</div>
      </template>
    </el-dialog>

    <!-- 新建 -->
    <el-dialog v-model="formVisible" title="新建日程" :width="340">
      <el-form label-width="72px">
        <el-form-item label="成员">
          <el-select v-model="form.userId" style="width: 100%" filterable>
            <el-option v-for="m in members" :key="m.id" :label="m.name" :value="m.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题" required>
          <el-input v-model="form.title" placeholder="日程标题" maxlength="60" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%">
            <el-option v-for="t in scheduleTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="日期">
          <el-date-picker v-model="form.scheduleDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时间">
          <div style="display: flex; gap: 6px; width: 100%">
            <el-time-select v-model="form.startTime" start="08:00" step="00:30" end="21:00" placeholder="开始" style="flex: 1" />
            <el-time-select v-model="form.endTime" start="08:00" step="00:30" end="21:00" placeholder="结束" style="flex: 1" />
          </div>
        </el-form-item>
        <el-form-item label="工时">
          <el-input-number v-model="form.hours" :min="0" :max="24" :precision="1" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="项目">
          <el-select v-model="form.projectId" style="width: 100%" clearable filterable placeholder="可选">
            <el-option v-for="p in projectOptions" :key="p.id" :label="`${p.projectNo} | ${p.name}`" :value="p.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="200" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ms-page { max-width: 640px; margin: 0 auto; padding: 14px 0 10px; }
.ms-header { display: flex; justify-content: space-between; align-items: center; padding: 0 12px; margin-bottom: 8px; }
.ms-title { font-size: 18px; font-weight: 600; }
.ms-week-bar { display: flex; justify-content: center; align-items: center; gap: 20px; margin-bottom: 10px; }
.ms-week-bar .el-icon { font-size: 18px; color: #4b5563; cursor: pointer; padding: 6px; }
.ms-week-label { font-size: 15px; font-weight: 600; cursor: pointer; }
.ms-board { overflow-x: auto; background: #fff; border-top: 1px solid #e5e7eb; border-bottom: 1px solid #e5e7eb; -webkit-overflow-scrolling: touch; }
.ms-table { border-collapse: separate; border-spacing: 0; min-width: 100%; }
.ms-table th, .ms-table td { border-right: 1px solid #f0f1f3; border-bottom: 1px solid #f0f1f3; padding: 0; vertical-align: top; }
.ms-member-col { position: sticky; left: 0; z-index: 2; background: #fafbfc; min-width: 96px; max-width: 96px; }
.ms-day-col { min-width: 132px; }
th.ms-day-col { padding: 6px 4px; text-align: center; background: #fafbfc; }
.ms-day-md { font-size: 14px; font-weight: 600; color: #374151; }
.ms-day-md.today { color: #2563eb; }
.ms-day-week { font-size: 11px; color: #9ca3af; }
th.ms-day-col.today { background: #eef4ff; }
td.ms-day-col { padding: 4px; min-height: 64px; height: 64px; }
td.ms-member-col { padding: 10px 8px; }
.ms-member { display: flex; align-items: center; gap: 8px; }
.ms-avatar { width: 32px; height: 32px; border-radius: 50%; color: #fff; font-size: 13px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.ms-member-name { font-size: 13px; color: #374151; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ms-chip { background: #eef4ff; border-left: 3px solid #2563eb; border-radius: 6px; padding: 4px 6px; margin-bottom: 4px; cursor: pointer; }
.ms-chip-title { font-size: 12px; color: #1f2937; font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ms-chip-time { font-size: 10px; color: #6b7280; margin-top: 1px; }
.ms-empty { text-align: center; color: #9ca3af; padding: 32px 0; font-size: 13px; }
.ms-hint { text-align: center; color: #c0c4cc; font-size: 11px; margin-top: 8px; }
.ms-detail-row { font-size: 13px; color: #374151; padding: 5px 0; }
.ms-detail-label { display: inline-block; width: 52px; color: #9ca3af; }
</style>
