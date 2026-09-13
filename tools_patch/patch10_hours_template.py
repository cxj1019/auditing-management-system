import io
p = r'frontend/src/views/business/schedule/index.vue'
s = io.open(p, encoding='utf-8').read()

old = """      <!-- 工时统计（周视图） -->
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
      </div>"""
new = """      <!-- 工时统计（周视图） -->
      <div v-if="canViewHours && viewMode === 'week'" style="padding: 12px 16px">
        <el-tabs v-model="hoursTab" @tab-change="onHoursTab">
          <el-tab-pane label="按人汇总" name="summary">
            <div class="items-header">
              <span class="section-title">本周工时（{{ weekStart }} 起）</span>
              <div>
                <el-button size="small" :loading="confirming" @click="handleConfirm()">全部确认</el-button>
                <el-button size="small" @click="fetchHoursSummary">刷新</el-button>
              </div>
            </div>
            <el-table :data="hoursSummary" border size="small">
              <el-table-column prop="memberName" label="成员" min-width="100" />
              <el-table-column label="工时（h）" align="right" width="90">
                <template #default="{ row }">{{ Number(row.totalHours).toFixed(1) }}</template>
              </el-table-column>
              <el-table-column label="加班（h）" align="right" width="90">
                <template #default="{ row }">{{ Number(row.overtimeHours).toFixed(1) }}</template>
              </el-table-column>
              <el-table-column label="标准（h）" align="right" width="90">
                <template #default="{ row }">{{ Number(row.stdHours).toFixed(0) }}</template>
              </el-table-column>
              <el-table-column label="利用率" align="right" width="90">
                <template #default="{ row }">
                  <span :style="{ color: Number(row.utilization) > 120 ? '#f56c6c' : Number(row.utilization) < 60 ? '#e6a23c' : '#67c23a' }">{{ Number(row.utilization).toFixed(0) }}%</span>
                </template>
              </el-table-column>
              <el-table-column label="已确认" align="center" width="80">
                <template #default="{ row }">
                  <el-tag v-if="row.confirmedCount > 0" type="success" size="small">已确认</el-tag>
                  <el-tag v-else type="info" size="small">未确认</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="90">
                <template #default="{ row }">
                  <el-button link type="primary" size="small" :loading="confirming" @click="handleConfirm(row.userId)">确认</el-button>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="人×项目矩阵（本月）" name="matrix">
            <div class="items-header">
              <span class="section-title">本月工时矩阵</span>
              <el-button size="small" :loading="matrixLoading" @click="fetchMatrix">刷新</el-button>
            </div>
            <el-table v-if="matrixProjects.length" :data="matrixUsers().map((u) => ({ user: u }))" border size="small" max-height="360">
              <el-table-column prop="user" label="成员" min-width="100" fixed="left" />
              <el-table-column v-for="pj in matrixProjects" :key="pj" :label="pj" min-width="110" align="right">
                <template #default="{ row }">
                  {{ matrixCell(row.user, pj) != null ? matrixCell(row.user, pj)!.toFixed(1) : '' }}
                </template>
              </el-table-column>
              <el-table-column label="合计" align="right" width="90" fixed="right">
                <template #default="{ row }">
                  {{ Math.round(matrixProjects.reduce((s, pj) => s + (matrixCell(row.user, pj) || 0), 0) * 10) / 10 }}
                </template>
              </el-table-column>
            </el-table>
            <div v-if="matrixProjects.length" style="margin-top: 4px; color: #9ca3af; font-size: 12px; text-align: right">
              项目合计：{{ matrixProjects.map((pj) => pj + ' ' + matrixTotal(pj).toFixed(1) + 'h').join('　') }}
            </div>
            <el-empty v-else description="本月暂无项目工时" :image-size="60" />
          </el-tab-pane>

          <el-tab-pane v-if="isAdmin" label="月度锁定" name="lock">
            <div class="items-header">
              <span class="section-title">月度工时锁定（锁定后该月日程不可增删改）</span>
            </div>
            <div style="display: flex; gap: 8px; margin-bottom: 10px">
              <el-date-picker v-model="lockInput" type="month" value-format="YYYY-MM" placeholder="选择月份" style="width: 160px" />
              <el-button type="primary" size="small" @click="handleLock">锁定该月</el-button>
            </div>
            <div>
              <el-tag
                v-for="m in lockedMonths"
                :key="m"
                closable
                style="margin: 0 8px 8px 0"
                @close="handleUnlock(m)"
              >{{ m }} 已锁定</el-tag>
              <span v-if="!lockedMonths.length" style="color: #9ca3af; font-size: 13px">暂无锁定月份</span>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>"""
assert old in s
s = s.replace(old, new)
io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('template ok')
