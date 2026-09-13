import io
p = r'frontend/src/views/business/schedule/index.vue'
s = io.open(p, encoding='utf-8').read()

# ---- imports ----
old = """import { listSchedules, createSchedule, updateSchedule, deleteSchedule, exitSchedule, getHoursSummary, listScheduleResources } from '@/api/schedule'"""
new = """import { listSchedules, createSchedule, updateSchedule, deleteSchedule, exitSchedule, getHoursSummary, listScheduleResources, getHoursMatrix, confirmHours, listLocks, lockMonth, unlockMonth } from '@/api/schedule'"""
assert old in s
s = s.replace(old, new)

# ---- state ----
old = """// ---------- 工时统计 ----------
const hoursSummary = ref<{ userId: number; memberName: string; totalHours: number }[]>([])

async function fetchHoursSummary(): Promise<void> {
  const end = new Date(weekStart.value + 'T00:00:00')
  end.setDate(end.getDate() + 6)
  hoursSummary.value = await getHoursSummary({
    startDate: weekStart.value,
    endDate: formatDate(end),
  })
}"""
new = """// ---------- 工时统计 ----------
const hoursSummary = ref<{ userId: number; memberName: string; totalHours: number; overtimeHours: number; stdHours: number; utilization: number; confirmedCount: number }[]>([])
const hoursTab = ref('summary')
const matrixRows = ref<{ userId: number; userName: string; projectId: number | null; projectName: string; hours: number }[]>([])
const matrixProjects = ref<string[]>([])
const matrixLoading = ref(false)
const confirming = ref(false)
const lockedMonths = ref<string[]>([])
const lockInput = ref('')
const isAdmin = computed(() => userStore.hasRole('admin'))

function weekRange(): { start: string; end: string } {
  const end = new Date(weekStart.value + 'T00:00:00')
  end.setDate(end.getDate() + 6)
  return { start: weekStart.value, end: formatDate(end) }
}

async function fetchHoursSummary(): Promise<void> {
  const range = weekRange()
  hoursSummary.value = await getHoursSummary({ startDate: range.start, endDate: range.end })
}

async function fetchMatrix(): Promise<void> {
  matrixLoading.value = true
  try {
    const month = weekStart.value.slice(0, 7)
    const first = month + '-01'
    const lastDate = new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)), 0)
    const range = { start: first, end: formatDate(lastDate) }
    matrixRows.value = await getHoursMatrix(range)
    const names: string[] = []
    matrixRows.value.forEach((r) => {
      if (!names.includes(r.projectName)) names.push(r.projectName)
    })
    // 列排序：项目在前、未关联项目在最后
    names.sort((a, b) => (a === '未关联项目' ? 1 : b === '未关联项目' ? -1 : a.localeCompare(b, 'zh')))
    matrixProjects.value = names
  } finally {
    matrixLoading.value = false
  }
}

function matrixCell(userName: string, projectName: string): number | null {
  const row = matrixRows.value.find((r) => r.userName === userName && r.projectName === projectName)
  return row ? row.hours : null
}

function matrixUsers(): string[] {
  const names: string[] = []
  matrixRows.value.forEach((r) => {
    if (!names.includes(r.userName)) names.push(r.userName)
  })
  return names
}

function matrixTotal(projectName: string): number {
  return Math.round(matrixRows.value.filter((r) => r.projectName === projectName)
    .reduce((s, r) => s + r.hours, 0) * 10) / 10
}

async function handleConfirm(userId?: number): Promise<void> {
  const range = weekRange()
  confirming.value = true
  try {
    const n = await confirmHours({ startDate: range.start, endDate: range.end, userId })
    ElMessage.success(`已确认 ${n} 条工时`)
    fetchHoursSummary()
  } finally {
    confirming.value = false
  }
}

async function fetchLocks(): Promise<void> {
  if (isAdmin.value) lockedMonths.value = await listLocks()
}

async function handleLock(): Promise<void> {
  if (!lockInput.value) return
  await lockMonth(lockInput.value)
  ElMessage.success(`${lockInput.value} 已锁定`)
  lockInput.value = ''
  fetchLocks()
}

async function handleUnlock(month: string): Promise<void> {
  await ElMessageBox.confirm(`解锁 ${month} 后该月日程可再次修改，确定解锁？`, '解锁确认', { type: 'warning' })
  await unlockMonth(month)
  ElMessage.success(`${month} 已解锁`)
  fetchLocks()
}

function onHoursTab(tab: string): void {
  hoursTab.value = tab
  if (tab === 'matrix') fetchMatrix()
  if (tab === 'lock') fetchLocks()
}"""
assert old in s
s = s.replace(old, new)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('script ok')
