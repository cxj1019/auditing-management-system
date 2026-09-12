<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

/** 手机端"更多"：全部功能入口 + 账号操作（修改密码/退出登录/回桌面版） */
const router = useRouter()
const userStore = useUserStore()

const groups = [
  {
    title: '报销相关',
    items: [
      { label: '我要报销（拍照上传）', icon: 'Camera', path: '/m/reimburse' },
      { label: '我的报销单', icon: 'Tickets', path: '/m/bills' },
      { label: '报销审批', icon: 'Bell', path: '/m/approval' },
    ],
  },
  {
    title: '业务查询（电脑版页面，手机可用）',
    items: [
      { label: '项目管理', icon: 'Notebook', path: '/business/project' },
      { label: '客户管理', icon: 'User', path: '/business/client' },
      { label: '合同管理', icon: 'Document', path: '/business/contract' },
      { label: '发票管理', icon: 'Postcard', path: '/business/invoice' },
      { label: '收款管理', icon: 'Money', path: '/business/collection' },
      { label: '函证管理', icon: 'Memo', path: '/business/confirmation' },
      { label: '日程周板（滑动）', icon: 'Calendar', path: '/m/schedule' },
      { label: '汇率牌价', icon: 'Coin', path: '/business/fx' },
      { label: '成本分析', icon: 'DataAnalysis', path: '/business/cost' },
    ],
  },
]

// ---------- 修改密码 ----------
const pwdVisible = ref(false)
const pwdSaving = ref(false)
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

function openPasswordDialog(): void {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdVisible.value = true
}

async function handleChangePassword(): Promise<void> {
  if (!pwdForm.oldPassword || !pwdForm.newPassword) {
    ElMessage.warning('请填写原密码和新密码')
    return
  }
  if (pwdForm.newPassword.length < 6) {
    ElMessage.warning('新密码长度至少 6 位')
    return
  }
  if (pwdForm.newPassword !== pwdForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  pwdSaving.value = true
  try {
    await changePassword({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    pwdVisible.value = false
    ElMessage.success('密码修改成功，请重新登录')
    await userStore.logout()
    router.push('/login')
  } finally {
    pwdSaving.value = false
  }
}

async function handleLogout(): Promise<void> {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning' })
    await userStore.logout()
    router.push('/login')
    ElMessage.success('已退出登录')
  } catch { /* 取消 */ }
}
</script>

<template>
  <div class="mm-page">
    <!-- 账号卡片 -->
    <div class="mm-user-card">
      <el-avatar :size="44" class="mm-avatar">{{ userStore.nickname.charAt(0) }}</el-avatar>
      <div class="mm-user-info">
        <div class="mm-user-name">{{ userStore.nickname }}</div>
        <div class="mm-user-account">{{ userStore.username }}</div>
      </div>
    </div>

    <template v-for="group in groups" :key="group.title">
      <div class="mm-group-title">{{ group.title }}</div>
      <div class="mm-group">
        <div v-for="item in group.items" :key="item.path" class="mm-item" @click="router.push(item.path)">
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span class="mm-item-label">{{ item.label }}</span>
          <el-icon class="mm-arrow"><ArrowRight /></el-icon>
        </div>
      </div>
    </template>

    <div class="mm-group-title">账号</div>
    <div class="mm-group">
      <div class="mm-item" @click="openPasswordDialog">
        <el-icon :size="18"><Lock /></el-icon>
        <span class="mm-item-label">修改密码</span>
        <el-icon class="mm-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="mm-item" @click="router.push('/dashboard?desktop=1')">
        <el-icon :size="18"><Monitor /></el-icon>
        <span class="mm-item-label">回到电脑版首页</span>
        <el-icon class="mm-arrow"><ArrowRight /></el-icon>
      </div>
      <div class="mm-item mm-logout" @click="handleLogout">
        <el-icon :size="18"><SwitchButton /></el-icon>
        <span class="mm-item-label">退出登录</span>
      </div>
    </div>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="pwdVisible" title="修改密码" :width="320">
      <el-form label-width="76px">
        <el-form-item label="原密码" required>
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="原密码" />
        </el-form-item>
        <el-form-item label="新密码" required>
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="确认新密码" required>
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSaving" @click="handleChangePassword">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.mm-page { max-width: 640px; margin: 0 auto; padding: 14px 12px; }
.mm-user-card { background: linear-gradient(135deg, #2563eb, #1d4ed8); border-radius: 12px; padding: 16px; display: flex; align-items: center; gap: 12px; color: #fff; }
.mm-avatar { background: rgba(255, 255, 255, 0.25); color: #fff; font-size: 18px; }
.mm-user-name { font-size: 16px; font-weight: 600; }
.mm-user-account { font-size: 12px; opacity: 0.85; margin-top: 3px; }
.mm-group-title { font-size: 13px; color: #9ca3af; margin: 16px 0 6px; }
.mm-group { background: #fff; border: 1px solid #e5e7eb; border-radius: 10px; overflow: hidden; }
.mm-item { display: flex; align-items: center; gap: 10px; padding: 13px 14px; border-bottom: 1px solid #f3f4f6; font-size: 14px; color: #374151; cursor: pointer; }
.mm-item:last-child { border-bottom: none; }
.mm-item:active { background: #f9fafb; }
.mm-item-label { flex: 1; }
.mm-arrow { color: #d1d5db; }
.mm-logout { color: #ef4444; justify-content: center; }
</style>
