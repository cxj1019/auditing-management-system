<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listSchedules, createSchedule, updateSchedule, deleteSchedule, exitSchedule, getHoursSummary } from '@/api/schedule'
import { pageProjects } from '@/api/project'
import { getUserOptions, getDepartmentOptions } from '@/api/user'
import { useUserStore } from '@/stores/user'
import { holidayOf, isHoliday, isMakeupWorkday } from '@/utils/holiday'
import type { ScheduleItem, ScheduleRequest, UserOption, DepartmentItem } from '@/types'

const userStore = useUserStore()
/** 工时统计仅管理员/项目经理可见 */
const canViewHours = computed(() => userStore.hasPermission('business:schedule:hours'))
const scheduleTypes = ['会议', '现场审计', '报告编制', '差旅', '加班', '访问', '内勤', '居家', '休假', '其他']
/** 时间选项:0:00–24:00,每 30 分钟(结束可选 24:00 表示当日零点整) */
const timeOptions = Array.from({ length: 49 }, (_, i) => {
  const h = Math.floor(i / 2)
  const m = i % 2 === 0 ? '00' : '30'
  return `${String(h).padStart(2, '0')}:${m}`
})

/** 本地日期格式化（避免 toISOString 的 UTC 时区偏移） */
function formatDate(d: Date): string {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function todayStr(): string {
  return formatDate(new Date())
}

// ---------- 视图模式 ----------
const viewMode = ref<'week' | 'day' | 'month'>('week')

// ---------- 月视图（年历） ----------
const year = ref(new Date().getFullYear())
const yearSchedules = ref<ScheduleItem[]>([])
const yearLoaded = ref('')

/** 月视图进入/换年时拉取全年日程（用于“有日程”标记） */
async function loadYearSchedules(): Promise<void> {
  const key = String(year.value)
  yearSchedules.value = await listSchedules({ startDate: `${key}-01-01`, endDate: `${key}-12-31` })
  yearLoaded.value = key
}

watch([viewMode, year], () => {
  if (viewMode.value === 'month' && yearLoaded.value !== String(year.value)) {
    loadYearSchedules()
  }
})

function shiftYear(delta: number): void {
  year.value += delta
}

// ---------- 周导航 ----------
const weekStart = ref(getMonday(new Date()))

function getMonday(d: Date): string {
  const day = new Date(d)
  const diff = day.getDate() - day.getDay() + (day.getDay() === 0 ? -6 : 1)
  day.setDate(diff)
  return formatDate(day)
}

const weekDays = computed(() => {
  const start = new Date(weekStart.value + 'T00:00:00')
  const days: { date: string; label: string; dayName: string; isToday: boolean }[] = []
  const dayNames = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  for (let i = 0; i < 7; i++) {
    const d = new Date(start)
    d.setDate(d.getDate() + i)
    days.push({
      date: formatDate(d),
      label: `${d.getMonth() + 1}/${d.getDate()}`,
      dayName: dayNames[i],
      isToday: formatDate(d) === todayStr(),
    })
  }
  return days
})

const weekRange = computed(() => {
  if (!weekDays.value.length) return ''
  return `${weekDays.value[0].label} - ${weekDays.value[6].label}`
})

function prevWeek(): void { shiftWeek(-7) }
function nextWeek(): void { shiftWeek(7) }
function shiftWeek(days: number): void {
  const d = new Date(weekStart.value + 'T00:00:00')
  d.setDate(d.getDate() + days)
  weekStart.value = formatDate(d)
  fetchSchedules()
}

/** 工具栏导航：月视图按年，其余按天 */
function navStep(days: number): void {
  if (viewMode.value === 'month') {
    shiftYear(days < 0 ? -1 : 1)
  } else {
    shiftWeek(days)
  }
}

function goToday(): void {
  if (viewMode.value === 'month') {
    year.value = new Date().getFullYear()
  } else {
    weekStart.value = getMonday(new Date())
    fetchSchedules()
  }
}

const rangeLabel = computed(() =>
  viewMode.value === 'month' ? `${year.value}年` : weekRange.value)

// ---------- 成员 ----------
const userOptions = ref<UserOption[]>([])
const memberSearch = ref('')
const memberDeptFilter = ref<number | undefined>(undefined)
const deptOptions = ref<DepartmentItem[]>([])

const availableMembers = computed(() => {
  return userOptions.value.filter((u) => {
    if (form.userIds.includes(u.id)) return false
    if (memberDeptFilter.value && u.id) {
      const dept = deptOptions.value.find((d) => d.id === memberDeptFilter.value)
      if (dept && !u.nickname?.includes(dept.deptName)) return false
    }
    if (memberSearch.value && !u.nickname?.includes(memberSearch.value) && !u.username?.includes(memberSearch.value)) return false
    return true
  })
})

function userNameById(id: number): string {
  const u = userOptions.value.find((u) => u.id === id)
  return u ? (u.nickname || u.username) : `用户${id}`
}

function addMember(id: number): void {
  if (!form.userIds.includes(id)) {
    form.userIds.push(id)
  }
}
const memberList = computed(() => {
  const map = new Map<number, { id: number; name: string; dept: string; color: string; initial: string }>()
  const optById = new Map(userOptions.value.map((u) => [u.id, u]))
  schedules.value.forEach((s) => {
    if (s.userId && !map.has(s.userId)) {
      // 姓名优先取人员选项中的昵称，其次后端填充的 creatorName
      const name = optById.get(s.userId)?.nickname || s.creatorName || `用户${s.userId}`
      map.set(s.userId, { id: s.userId, name, dept: optById.get(s.userId)?.deptName || '', color: getAvatarColor(name), initial: name.charAt(0) })
    }
  })
  userOptions.value.forEach((u) => {
    if (!map.has(u.id)) {
      const name = u.nickname || u.username
      map.set(u.id, { id: u.id, name, dept: u.deptName || '', color: getAvatarColor(name), initial: name.charAt(0) })
    }
  })
  return Array.from(map.values())
})

function getAvatarColor(name: string): string {
  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#909399', '#8b5cf6', '#ec4899']
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = name.charCodeAt(i) + hash
  return colors[hash % colors.length]
}

// ---------- 日程数据 ----------
const schedules = ref<ScheduleItem[]>([])
const loading = ref(false)

async function fetchSchedules(): Promise<void> {
  loading.value = true
  try {
    const end = new Date(weekStart.value + 'T00:00:00')
    end.setDate(end.getDate() + 6)
    schedules.value = await listSchedules({
      startDate: weekStart.value,
      endDate: formatDate(end),
    })
  } finally { loading.value = false }
}

// ---------- 周历色带布局 ----------
// 跨天日程渲染为一条横跨多列的连续色带；同行日程按“泳道”上下错开
const bandColors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c', '#8b5cf6', '#ec4899', '#14b8a6', '#f97316']
const BAND_HEIGHT = 24
const BAND_GAP = 4
const ROW_PADDING = 6

/** 工时推算（与后端 ScheduleHoursCalculator 同口径）：
    全天 7 小时（9:00–17:00 扣午休 1 小时）；有时间按净工时；加班每满 4 小时强制休息 1 小时 */
const toMinutes = (t?: string): number | null => {
  if (!t) return null
  const [h, m] = t.split(':').map(Number)
  return h * 60 + (m || 0)
}

// ---------- 日视图（本周 7 天 × 24 小时时间网格） ----------
function coversDay(s: ScheduleItem, dateStr: string): boolean {
  return s.scheduleDate <= dateStr && dateStr <= (s.endDate || s.scheduleDate)
}

/** 某天的全天日程（无起止时间） */
function dayAllDay(dateStr: string): ScheduleItem[] {
  return schedules.value.filter((s) => coversDay(s, dateStr) && !s.startTime)
}

/** 某天的定时日程（含开始时间），按开始分钟排序并计算定位 */
function dayTimed(dateStr: string): { s: ScheduleItem; start: number; dur: number }[] {
  return schedules.value
    .filter((s) => coversDay(s, dateStr) && s.startTime)
    .map((s) => {
      const start = toMinutes(s.startTime)!
      const end = toMinutes(s.endTime) ?? Math.min(start + 60, 1440)
      return { s, start, dur: Math.max(30, end - start) }
    })
    .sort((a, b) => a.start - b.start)
}

/** 点击日视图空白格创建日程（预填点击处的小时） */
function openCreateAt(dateStr: string, hour: number): void {
  openCreate(undefined, dateStr, `${String(hour).padStart(2, '0')}:00`)
}

/** 跳到某日期所在周（月视图点击日期） */
function goWeekOf(dateStr: string): void {
  const d = new Date(dateStr + 'T00:00:00')
  weekStart.value = formatDate(new Date(d.getFullYear(), d.getMonth(), d.getDate() - d.getDay() + 1))
  viewMode.value = 'week'
  fetchSchedules()
}

function monthLeading(m: number): number {
  return (new Date(year.value, m - 1, 1).getDay() + 6) % 7
}

function monthDays(m: number): number {
  return new Date(year.value, m, 0).getDate()
}

function monthDate(m: number, d: number): string {
  return `${year.value}-${String(m).padStart(2, '0')}-${String(d).padStart(2, '0')}`
}

function isTodayDate(m: number, d: number): boolean {
  return monthDate(m, d) === todayStr()
}

function dayHasSchedule(dateStr: string): boolean {
  return yearSchedules.value.some((s) => s.scheduleDate <= dateStr && dateStr <= (s.endDate || s.scheduleDate))
}
const netMinutesTs = (from: number, to: number): number => {
  const f = Math.max(from, 540)
  const t = Math.min(to, 1020)
  const span = Math.max(0, t - f)
  const brk = Math.max(0, Math.min(t, 780) - Math.max(f, 720))
  return Math.max(0, span - brk)
}
function effectiveHours(s: ScheduleItem): number {
  if (s.type === '加班') {
    let remaining = 0
    if (s.startTime && s.endTime) {
      const from = toMinutes(s.startTime)!
      const to = toMinutes(s.endTime)!
      remaining = to >= from ? to - from : to + 1440 - from
    } else if (s.hours != null) {
      remaining = Math.round(s.hours * 60)
    }
    let work = 0
    while (remaining > 0) {
      const chunk = Math.min(remaining, 240)
      work += chunk
      remaining -= chunk
      if (remaining > 0) remaining = Math.max(0, remaining - 60)
    }
    return Math.round((work / 60) * 100) / 100
  }
  if (!s.scheduleDate) return 0
  const start = new Date(s.scheduleDate + 'T00:00:00').getTime()
  const end = new Date((s.endDate || s.scheduleDate) + 'T00:00:00').getTime()
  const days = Math.round((end - start) / 86400000) + 1
  if (days === 1) {
    if (!s.startTime && !s.endTime) return 7
    const from = toMinutes(s.startTime) ?? 540
    const to = toMinutes(s.endTime) ?? 1020
    return Math.round((netMinutesTs(from, to) / 60) * 100) / 100
  }
  const first = s.startTime ? netMinutesTs(toMinutes(s.startTime)!, 1020) : 420
  const last = s.endTime ? netMinutesTs(540, toMinutes(s.endTime)!) : 420
  const middle = Math.max(0, days - 2) * 420
  return Math.round(((first + middle + last) / 60) * 100) / 100
}

/** 逻辑事件键：同标题+同起始日期+同时长的日程视为同一事件（批量发给多人时同色） */
function eventKey(s: ScheduleItem): string {
  return `${s.title || ''}|${s.scheduleDate}|${s.hours ?? ''}|${s.type || ''}`
}

/** 本周事件的取色表：按日期排序后轮换取色并避免相邻同色 → 同事件同色、近邻日程异色 */
const eventColorMap = computed(() => {
  const map = new Map<string, string>()
  const keys = [...new Set(schedules.value.map(eventKey))].sort((a, b) => {
    const da = a.split('|')[1]
    const db = b.split('|')[1]
    return da === db ? (a < b ? -1 : 1) : da < db ? -1 : 1
  })
  let prev = ''
  for (const key of keys) {
    let idx = 0
    for (let i = 0; i < key.length; i++) idx = (idx * 31 + key.charCodeAt(i)) >>> 0
    idx = idx % bandColors.length
    if (bandColors[idx] === prev) idx = (idx + 1) % bandColors.length
    map.set(key, bandColors[idx])
    prev = bandColors[idx]
  }
  return map
})

interface ScheduleBand {
  item: ScheduleItem
  left: number
  width: number
  top: number
  color: string
}

/** 日期相对本周周一的偏移天数 */
function dayOffset(date: string): number {
  const ms = new Date(date + 'T00:00:00').getTime() - new Date(weekStart.value + 'T00:00:00').getTime()
  return Math.round(ms / 86400000)
}

function memberBands(memberId: number): ScheduleBand[] {
  const weekStartStr = weekStart.value
  const weekEndStr = weekDays.value[weekDays.value.length - 1]?.date || weekStart.value
  // 裁剪到本周范围
  const clipped = schedules.value
    .filter((s) => s.userId === memberId)
    .map((s) => ({
      item: s,
      start: s.scheduleDate < weekStartStr ? weekStartStr : s.scheduleDate,
      end: (s.endDate || s.scheduleDate) > weekEndStr ? weekEndStr : (s.endDate || s.scheduleDate),
    }))
    .filter((b) => b.start <= b.end)
  clipped.sort((a, b) => (a.start === b.start ? a.item.id - b.item.id : a.start < b.start ? -1 : 1))
  // 泳道分配：找第一个不重叠的泳道，否则新开一条
  const laneEnds: string[] = []
  return clipped.map((b, idx) => {
    let lane = laneEnds.findIndex((e) => e < b.start)
    if (lane === -1) {
      laneEnds.push(b.end)
      lane = laneEnds.length - 1
    } else {
      laneEnds[lane] = b.end
    }
    const from = dayOffset(b.start)
    const to = dayOffset(b.end)
    return {
      item: b.item,
      left: (from / 7) * 100,
      width: ((to - from + 1) / 7) * 100,
      top: ROW_PADDING + lane * (BAND_HEIGHT + BAND_GAP),
      color: eventColorMap.value.get(eventKey(b.item)) || bandColors[0],
    }
  })
}

function rowHeight(memberId: number): number {
  const bands = memberBands(memberId)
  const maxTop = bands.reduce((m, b) => Math.max(m, b.top), 0)
  return Math.max(80, maxTop + BAND_HEIGHT + ROW_PADDING)
}

// ---------- 新建/编辑 ----------
const dialogVisible = ref(false)
const saving = ref(false)
const isEdit = ref(false)
const editingId = ref<number | null>(null)
/** 当前编辑的日程是否为本人参与的那条 */
const editingIsMine = ref(false)
const form = reactive<ScheduleRequest & { userIds: number[] }>({
  projectId: undefined, userIds: [], title: '', description: '',
  scheduleDate: todayStr(),
  endDate: todayStr(),
  startTime: '', endTime: '', hours: 7, type: '会议',
})
const projectOptions = ref<{ id: number; name: string }[]>([])

async function loadOptions(): Promise<void> {
  const [pData, uData, dData] = await Promise.all([
    pageProjects({ current: 1, size: 200 }),
    getUserOptions(),
    // 用免权限的 /departments/options，普通员工也能打开本页
    getDepartmentOptions(),
  ])
  projectOptions.value = pData.records
  userOptions.value = uData
  deptOptions.value = dData
}

function openCreate(userId?: number, date?: string, time?: string): void {
  isEdit.value = false
  editingId.value = null
  Object.assign(form, {
    userIds: userId ? [userId] : [userStore.userId],
    projectId: undefined, title: '', description: '',
    scheduleDate: date || todayStr(),
    endDate: date || todayStr(),
    startTime: time || '', endTime: '', hours: 7, type: '会议',
  })
  loadOptions()
  dialogVisible.value = true
}

function openEdit(row: ScheduleItem): void {
  isEdit.value = true
  editingId.value = row.id
  editingIsMine.value = row.userId === userStore.userId
  Object.assign(form, {
    userIds: [row.userId], projectId: row.projectId, title: row.title,
    description: row.description || '', scheduleDate: row.scheduleDate,
    endDate: row.endDate || row.scheduleDate,
    startTime: row.startTime || '', endTime: row.endTime || '',
    hours: row.hours, type: row.type,
  })
  loadOptions()
  dialogVisible.value = true
}

async function handleSave(): Promise<void> {
  saving.value = true
  try {
    // clearable 清空后 projectId 可能为空串，统一归一化为 undefined
    const payload = { ...form, projectId: form.projectId || undefined }
    if (isEdit.value && editingId.value) {
      // 编辑模式：单用户更新
      await updateSchedule(editingId.value, { ...payload, userId: form.userIds[0] })
      ElMessage.success('修改成功')
    } else {
      // 创建模式：一次请求传全部成员，由后端为每位成员各建一条
      // （后端本就支持 userIds 批量；若再前端循环会造成 每人 N 条 重复）
      await createSchedule({ ...payload })
      ElMessage.success(`已为 ${form.userIds.length} 位成员创建日程`)
    }
    dialogVisible.value = false
    fetchSchedules()
  } finally { saving.value = false }
}

/** 删除整场日程：连同所有参与人员的一起删除 */
async function handleDeleteEvent(): Promise<void> {
  if (!editingId.value) return
  try {
    await ElMessageBox.confirm(
      '将删除该日程，包括所有参与人员的日程，确定吗？',
      '删除日程', { type: 'warning', confirmButtonText: '全部删除', cancelButtonText: '取消' })
    await deleteSchedule(editingId.value)
    ElMessage.success('日程已删除')
    dialogVisible.value = false
    fetchSchedules()
  } catch { /* cancel */ }
}

/** 退出日程：仅移除我自己的这条，其他成员不受影响 */
async function handleExit(): Promise<void> {
  if (!editingId.value) return
  try {
    await ElMessageBox.confirm(
      '退出后你本人的这条日程将被移除，其他成员不受影响，确定退出吗？',
      '退出日程', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' })
    await exitSchedule(editingId.value)
    ElMessage.success('已退出该日程')
    dialogVisible.value = false
    fetchSchedules()
  } catch { /* cancel */ }
}

// ---------- 工时统计 ----------
const hoursSummary = ref<{ userId: number; memberName: string; totalHours: number }[]>([])

async function fetchHoursSummary(): Promise<void> {
  const end = new Date(weekStart.value + 'T00:00:00')
  end.setDate(end.getDate() + 6)
  hoursSummary.value = await getHoursSummary({
    startDate: weekStart.value,
    endDate: formatDate(end),
  })
}

onMounted(() => {
  fetchSchedules()
  loadOptions()
  if (canViewHours.value) {
    fetchHoursSummary()
  }
})
</script>

<template>
  <div class="page-container">
    <el-card shadow="never" :body-style="{ padding: '0' }">
      <!-- 顶部工具栏 -->
      <div class="toolbar">
        <div class="toolbar-left">
          <span class="company-name">会计师事务所</span>
          <el-button-group style="margin-left: 16px">
            <el-button :icon="'DArrowLeft'" size="small" @click="navStep(-7)" />
            <el-button v-if="viewMode !== 'month'" :icon="'ArrowLeft'" size="small" @click="navStep(-1)" />
          </el-button-group>
          <span class="date-range">{{ rangeLabel }}</span>
          <el-button-group>
            <el-button v-if="viewMode !== 'month'" :icon="'ArrowRight'" size="small" @click="navStep(1)" />
            <el-button :icon="'DArrowRight'" size="small" @click="navStep(7)" />
          </el-button-group>
        </div>
        <div class="toolbar-right">
          <el-radio-group v-model="viewMode" size="small">
            <el-radio-button value="week">周视图</el-radio-button>
            <el-radio-button value="day">日视图</el-radio-button>
            <el-radio-button value="month">月视图</el-radio-button>
          </el-radio-group>
          <el-button size="small" style="margin-left: 8px" @click="goToday">今天</el-button>
          <el-button v-permission="'business:schedule:add'" type="primary" size="small" @click="openCreate()">+ 添加日程</el-button>
        </div>
      </div>

      <!-- 周历网格（周视图） -->
      <div class="week-grid" v-if="viewMode === 'week'">
        <!-- 表头 -->
        <div class="grid-header sticky-col">
          <span>成员</span>
          <el-icon style="margin-left: auto"><ArrowDown /></el-icon>
        </div>
        <div v-for="d in weekDays" :key="d.date" class="grid-header" :class="{ today: d.isToday, 'grid-header-holiday': isHoliday(d.date) }">
          <div class="header-date">{{ d.label }}</div>
          <div class="header-day">
            <span v-if="holidayOf(d.date)" class="holiday-tag" :class="holidayOf(d.date)!.type">{{ holidayOf(d.date)!.type }}</span>
            {{ d.dayName }}
          </div>
          <div v-if="holidayOf(d.date)?.name" class="header-festival">{{ holidayOf(d.date)!.name }}</div>
        </div>

        <!-- 成员行 -->
        <template v-for="member in memberList" :key="member.id">
          <div class="grid-member sticky-col">
            <el-avatar :size="36" :style="{ backgroundColor: member.color }">{{ member.initial }}</el-avatar>
            <div class="member-info">
              <div class="member-name">{{ member.name }}</div>
              <div class="member-dept">{{ member.dept || '未分配部门' }}</div>
            </div>
          </div>
          <div class="row-canvas" :style="{ minHeight: rowHeight(member.id) + 'px' }">
            <div v-for="d in weekDays" :key="d.date" class="grid-cell"
              :class="{ 'grid-cell-holiday': isHoliday(d.date) }" @click="openCreate(member.id, d.date)"></div>
            <div v-for="band in memberBands(member.id)" :key="band.item.id"
              class="schedule-band"
              :style="{ left: band.left + '%', width: band.width + '%', top: band.top + 'px', backgroundColor: band.color }"
              :title="`${band.item.type}${band.item.projectName ? ' · ' + band.item.projectName : ''}${band.item.title ? ' · ' + band.item.title : ''}`"
              @click.stop="openEdit(band.item)">
              <span class="bar-text">{{ band.item.type }}{{ band.item.projectName ? ' · ' + band.item.projectName : '' }}{{ band.item.title ? ' · ' + band.item.title : '' }}</span>

            </div>
          </div>
        </template>
      </div>

      <!-- 日视图：本周 7 天 × 24 小时时间网格 -->
      <div v-else-if="viewMode === 'day'" class="day-view">
        <div class="day-grid-header">
          <div class="day-grid-corner"></div>
          <div v-for="d in weekDays" :key="d.date" class="day-head" :class="{ today: d.isToday, holiday: isHoliday(d.date) }">
            <div class="day-head-date">{{ d.label }}</div>
            <div class="day-head-week">{{ d.dayName }}</div>
            <div class="day-head-holiday" v-if="holidayOf(d.date)">
              <span class="holiday-tag" :class="holidayOf(d.date)!.type">{{ holidayOf(d.date)!.type }}</span>
              <span v-if="holidayOf(d.date)?.name" class="holiday-name">{{ holidayOf(d.date)!.name }}</span>
            </div>
          </div>
        </div>
        <div class="day-grid-body">
          <div class="day-hour-axis">
            <div v-for="h in 24" :key="'h' + h" class="day-hour-label">{{ String(h - 1).padStart(2, '0') }}:00</div>
          </div>
          <div v-for="d in weekDays" :key="'col' + d.date" class="day-col" :class="{ 'day-col-holiday': isHoliday(d.date) }">
            <div v-for="h in 24" :key="'c' + h" class="day-hour-cell" @click.stop="openCreateAt(d.date, h - 1)"></div>
            <div v-if="isHoliday(d.date)" class="day-watermark">休</div>
            <div
              v-for="ev in dayAllDay(d.date)" :key="'ad' + ev.id"
              class="day-allday"
              :style="{ background: eventColorMap.get(eventKey(ev)) || bandColors[0] }"
              :title="ev.type + (ev.projectName ? ' · ' + ev.projectName : '') + (ev.title ? ' · ' + ev.title : '')"
              @click.stop="openEdit(ev)"
            >全天</div>
            <div
              v-for="ev in dayTimed(d.date)" :key="'tm' + ev.s.id"
              class="day-event"
              :style="{ top: (ev.start / 1440) * 100 + '%', height: Math.max(9, (ev.dur / 1440) * 100) + '%', background: eventColorMap.get(eventKey(ev.s)) || bandColors[0] }"
              :title="ev.s.type + (ev.s.projectName ? ' · ' + ev.s.projectName : '') + (ev.s.title ? ' · ' + ev.s.title : '')"
              @click.stop="openEdit(ev.s)"
            >
              <span class="day-event-text">{{ ev.s.startTime }} {{ ev.s.type }}{{ ev.s.projectName ? ' · ' + ev.s.projectName : '' }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 月视图：年历 -->
      <div v-else-if="viewMode === 'month'" class="year-view">
        <div class="year-grid">
          <div v-for="m in 12" :key="m" class="month-card">
            <div class="month-title">{{ year }}年{{ m }}月</div>
            <div class="month-week-header">
              <span v-for="w in ['一', '二', '三', '四', '五', '六', '日']" :key="w">{{ w }}</span>
            </div>
            <div class="month-days">
              <div v-for="b in monthLeading(m)" :key="'b' + b" class="month-day blank"></div>
              <div
                v-for="dnum in monthDays(m)" :key="dnum"
                class="month-day"
                :class="{
                  today: isTodayDate(m, dnum),
                  hasSchedule: dayHasSchedule(monthDate(m, dnum)),
                  holiday: isHoliday(monthDate(m, dnum)),
                  makeup: isMakeupWorkday(monthDate(m, dnum)),
                }"
                @click="goWeekOf(monthDate(m, dnum))"
              >
                <span class="month-day-num">{{ dnum }}</span>
                <span v-if="holidayOf(monthDate(m, dnum))" class="month-badge" :class="holidayOf(monthDate(m, dnum))!.type">{{ holidayOf(monthDate(m, dnum))!.type }}</span>
                <span v-if="holidayOf(monthDate(m, dnum))?.name" class="month-festival">{{ holidayOf(monthDate(m, dnum))!.name!.replace('节', '') }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 工时统计（周视图） -->
      <div v-if="canViewHours && viewMode === 'week'" style="padding: 12px 16px">
        <div class="items-header">
          <span class="section-title">本周工时</span>
          <el-button size="small" @click="fetchHoursSummary">刷新</el-button>
        </div>
        <el-table :data="hoursSummary" border size="small">
          <el-table-column prop="memberName" label="成员" min-width="120" />
          <el-table-column label="工时（小时）" align="right" width="120">
            <template #default="{ row }">{{ Number(row.totalHours).toFixed(1) }}</template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑日程' : '添加新日程'" width="680px" top="5vh">
      <el-form :model="form" label-width="130px" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="类型" required>
              <el-select v-model="form.type" style="width: 100%">
                <el-option v-for="t in scheduleTypes" :key="t" :label="t" :value="t" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="关联项目（可选）">
              <el-select v-model="form.projectId" clearable filterable placeholder="不关联项目" style="width: 100%">
                <el-option v-for="p in projectOptions" :key="p.id" :label="p.name" :value="p.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="标题（可选）">
          <el-input v-model="form.title" maxlength="200" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始日期" required>
              <el-date-picker v-model="form.scheduleDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间（可选）">
              <el-select v-model="form.startTime" placeholder="--:--" filterable clearable style="width: 100%">
              <el-option v-for="t in timeOptions" :key="t" :label="t" :value="t" />
            </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时间（可选）">
              <el-select v-model="form.endTime" placeholder="--:--" filterable clearable style="width: 100%">
              <el-option v-for="t in timeOptions" :key="t" :label="t" :value="t" />
            </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="参与成员">
          <div class="member-picker">
            <!-- 已选成员标签 -->
            <div class="selected-tags" v-if="form.userIds.length">
              <el-tag v-for="uid in form.userIds" :key="uid" closable size="small"
                @close="form.userIds = form.userIds.filter((id) => id !== uid)">
                {{ userNameById(uid) }}
              </el-tag>
            </div>
            <!-- 部门筛选 + 搜索 + 成员列表 -->
            <div class="member-picker-body">
              <div style="display: flex; gap: 8px; margin-bottom: 8px">
                <el-select v-model="memberDeptFilter" placeholder="所有部门" clearable size="small" style="width: 140px">
                  <el-option v-for="d in deptOptions" :key="d.id" :label="d.deptName" :value="d.id" />
                </el-select>
                <el-input v-model="memberSearch" placeholder="搜索姓名" size="small" clearable />
              </div>
              <div class="member-list">
                <div v-for="u in availableMembers" :key="u.id" class="member-item"
                  @click="addMember(u.id)">
                  {{ u.nickname ? `${u.nickname} (${u.username})` : u.username }}
                </div>
                <div v-if="!availableMembers.length" class="member-empty">无可用成员</div>
              </div>
            </div>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button v-if="isEdit && editingIsMine" link type="warning" @click="handleExit">退出日程</el-button>
        <el-button v-if="isEdit" link type="danger" @click="handleDeleteEvent">删除日程</el-button>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid #e5e7eb;
}
.toolbar-left { display: flex; align-items: center; gap: 8px; }
.toolbar-right { display: flex; align-items: center; gap: 8px; }
.company-name { font-size: 16px; font-weight: 600; color: var(--el-text-color-primary); }
.date-range { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); margin: 0 8px; }

.week-grid {
  display: grid;
  grid-template-columns: 180px repeat(7, minmax(0, 1fr));
  border-right: 1px solid #e5e7eb;
}
.grid-header {
  padding: 10px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-regular);
  border-bottom: 1px solid var(--el-border-color-light);
  border-right: 1px solid var(--el-border-color-lighter);
}
.grid-header.today { color: var(--el-color-primary); font-weight: 600; }
.header-date { font-weight: 600; }
.header-day { font-size: 12px; color: var(--el-text-color-placeholder); }
.grid-member {
  padding: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-right: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}
.member-info { flex: 1; }
.member-name { font-size: 14px; font-weight: 500; color: var(--el-text-color-primary); }
.member-dept { font-size: 12px; color: var(--el-text-color-placeholder); }
.row-canvas {
  grid-column: 2 / 9;
  position: relative;
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
}
.grid-cell {
  border-bottom: 1px solid var(--el-border-color-lighter);
  border-right: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  transition: background 0.15s;
}
.grid-cell:hover { background: var(--el-fill-color-light); }
.schedule-band {
  position: absolute;
  height: 24px;
  padding: 0 8px;
  border-radius: 6px;
  color: #fff;
  font-size: 12px;
  cursor: pointer;
  overflow: hidden;
  display: flex;
  align-items: center;
  gap: 4px;
  z-index: 1;
  transition: transform 0.15s, box-shadow 0.15s;
}
.schedule-band:hover { transform: translateY(-1px); box-shadow: 0 2px 8px rgba(0,0,0,0.2); z-index: 2; }
.bar-text { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; flex: 1; }
.bar-sub { font-size: 11px; opacity: 0.85; flex-shrink: 0; }
.items-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.section-title { font-size: 14px; font-weight: 500; color: var(--el-text-color-primary); }

/* 节假日标记（周视图表头） */
.grid-header-holiday { background: rgba(245, 108, 108, 0.05); }
.header-day { display: flex; align-items: center; justify-content: center; gap: 4px; }
.holiday-tag { font-size: 11px; line-height: 1; padding: 1px 3px; border-radius: 3px; color: #fff; }
.holiday-tag.休 { background: #f56c6c; }
.holiday-tag.班 { background: #909399; }
.header-festival { font-size: 10px; color: #f56c6c; line-height: 1.2; }
.grid-cell-holiday { background: rgba(245, 108, 108, 0.04); }

/* 日视图（时间网格） */
.day-view { border-top: 1px solid var(--el-border-color-light); }
.day-grid-header { display: flex; position: sticky; top: 0; z-index: 3; background: var(--el-bg-color); }
.day-grid-corner { width: 56px; flex-shrink: 0; border-right: 1px solid var(--el-border-color-light); }
.day-head { flex: 1; min-width: 0; text-align: center; padding: 8px 4px 4px; border-right: 1px solid var(--el-border-color-light); }
.day-head.today .day-head-date { color: var(--el-color-primary); font-weight: 600; }
.day-head-date { font-size: 13px; font-weight: 600; color: var(--el-text-color-primary); }
.day-head-week { font-size: 12px; color: var(--el-text-color-placeholder); }
.day-head-holiday { font-size: 11px; display: flex; justify-content: center; gap: 4px; min-height: 16px; }
.day-grid-body { display: flex; max-height: 620px; overflow-y: auto; }
.day-hour-axis { width: 56px; flex-shrink: 0; }
.day-hour-label { height: 44px; font-size: 11px; color: var(--el-text-color-placeholder); text-align: center; transform: translateY(-6px); }
.day-col { flex: 1; min-width: 0; position: relative; border-right: 1px solid var(--el-border-color-lighter); }
.day-hour-cell { height: 44px; border-bottom: 1px solid var(--el-border-color-lighter); cursor: pointer; }
.day-hour-cell:hover { background: var(--el-fill-color-light); }
.day-col-holiday { background: rgba(245, 108, 108, 0.04); }
.day-watermark { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; font-size: 72px; color: rgba(245, 108, 108, 0.10); pointer-events: none; font-weight: 700; }
.day-allday { margin: 2px; padding: 2px 4px; border-radius: 4px; color: #fff; font-size: 11px; cursor: pointer; position: relative; z-index: 2; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
.day-event { position: absolute; left: 2px; right: 2px; border-radius: 4px; color: #fff; font-size: 11px; padding: 1px 4px; cursor: pointer; overflow: hidden; z-index: 2; }
.day-event-text { white-space: nowrap; overflow: hidden; text-overflow: ellipsis; display: block; }

/* 月视图（年历） */
.year-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; padding: 16px; }
.month-card { border: 1px solid var(--el-border-color-light); border-radius: 8px; padding: 10px; }
.month-title { text-align: center; font-weight: 600; color: var(--el-text-color-primary); margin-bottom: 6px; }
.month-week-header { display: grid; grid-template-columns: repeat(7, 1fr); text-align: center; font-size: 11px; color: var(--el-text-color-placeholder); margin-bottom: 2px; }
.month-days { display: grid; grid-template-columns: repeat(7, 1fr); row-gap: 2px; }
.month-day { position: relative; text-align: center; padding: 2px 0 13px; border-radius: 6px; cursor: pointer; font-size: 12px; color: var(--el-text-color-regular); }
.month-day.blank { cursor: default; }
.month-day:hover { background: var(--el-fill-color-light); }
.month-day.today .month-day-num { background: var(--el-color-primary); color: #fff; border-radius: 50%; display: inline-flex; width: 22px; height: 22px; align-items: center; justify-content: center; }
.month-day.hasSchedule { background: var(--el-color-primary-light-9); }
.month-day.holiday .month-day-num { color: #f56c6c; }
.month-badge { position: absolute; top: -2px; right: 2px; font-size: 9px; line-height: 1; padding: 1px 2px; border-radius: 3px; color: #fff; }
.month-badge.休 { background: #f56c6c; }
.month-badge.班 { background: #909399; }
.month-festival { position: absolute; left: 0; right: 0; bottom: 0; font-size: 9px; color: #f56c6c; line-height: 1.1; }
.member-picker { border: 1px solid #dcdfe6; border-radius: 6px; padding: 8px; width: 100%; }
.selected-tags { display: flex; flex-wrap: wrap; gap: 4px; margin-bottom: 8px; }
.member-picker-body { max-height: 200px; overflow-y: auto; }
.member-list { max-height: 150px; overflow-y: auto; }
.member-item { padding: 6px 10px; cursor: pointer; font-size: 13px; color: #374151; border-radius: 4px; }
.member-item:hover { background: #f0f7ff; color: #409eff; }
.member-empty { padding: 12px; text-align: center; color: #9ca3af; font-size: 13px; }
.sticky-col { position: sticky; left: 0; z-index: 2; }
</style>
