<script setup lang="ts">
/** 使用帮助正文：桌面/手机两端共用，按角色分卡片 */
</script>

<template>
  <div class="help-content">
    <div class="hc-tip">
      手机浏览器打开系统地址会自动切换手机版。本页可用浏览器打印（Ctrl+P）输出一页纸。
    </div>

    <section class="hc-card">
      <h2>📄 员工（人人都要会）</h2>
      <ol>
        <li>
          <b>报报销</b>：【费用报销】→【登记报销】→ 填标题、选项目 → 加明细行（金额/费用日期/类别，增值税专票勾选后自动算税额）
          → 上传发票照片（手机打开可直接调摄像头拍照）→ 保存草稿 → <b>提交</b>
        </li>
        <li><b>查进度</b>：【我的报销单】看状态：草稿 → 待审批 → 已批准 → 已付款</li>
        <li><b>改错</b>：草稿可直接编辑；已提交可【撤回】；删错的单在【回收站】恢复</li>
      </ol>
      <p class="hc-qa">发票号写错？草稿里直接改。已批准的单不能撤回（已进财务流程），问题找财务。</p>
    </section>

    <section class="hc-card">
      <h2>✅ 经理</h2>
      <ol>
        <li><b>批报销</b>：报销单状态筛"待审批"（或看右上角通知）→【批准/驳回】；超过 5000 元的单批准后自动转合伙人/管理员终审</li>
        <li><b>批付款</b>：【对公付款】→ 待审批的单 →【批准/驳回】→ 批准后可【标记已付款】</li>
        <li><b>日程工时</b>：【日程管理】给成员排日程（参与人会收到邮件提醒）→ 周视图拖动查看 → 月末管理员锁月前确认工时</li>
      </ol>
      <p class="hc-qa">系统不允许审批自己提交的单。日程月份被锁定后增删改都要找管理员解锁。</p>
    </section>

    <section class="hc-card">
      <h2>👔 合伙人</h2>
      <ol>
        <li>审批本部门报销与付款；合伙人之间<b>互看互审</b>（跨部门单据仅限合伙人层面互通）</li>
        <li><b>对公付款审批</b>：待审批列表直接批准/驳回</li>
        <li><b>项目进度</b>：【项目管理】→ 进入项目 → 工作台一屏看合同、回款、成本、函证</li>
      </ol>
    </section>

    <section class="hc-card">
      <h2>🏦 财务 / 行政</h2>
      <ol>
        <li><b>报销财务环节</b>：已批准的单 →【收发票】→【标记付款】</li>
        <li>
          <b>扫函证（智能导入）</b>：扫描仪把<b>一份函证扫成一个 PDF</b>（通常 3 页）
          →【函证管理】→【智能批量导入】→ 上传 PDF → AI 自动识别"被函证单位"并分组
          → 核对分组、指定项目 →【确认归档】（自动按页拆分挂到各项目）
        </li>
        <li><b>发票与收款</b>：合同下登记发票并【开票】；回款在发票上登记核销</li>
      </ol>
      <p class="hc-qa">
        AI 没识别出的页会归入"未识别单位"，在确认界面手动指定项目即可，数据不会丢。
        高峰期识别失败，等一两分钟重试；单份 3 页以内最稳。
      </p>
    </section>

    <section class="hc-card">
      <h2>❓ 常见问题</h2>
      <ul>
        <li><b>忘记密码</b>：找管理员重置；连续输错 5 次锁定 10 分钟</li>
        <li><b>手机怎么用</b>：浏览器直接打开系统地址，自动切手机版；底部标签栏切换功能</li>
        <li><b>收不到邮件提醒</b>：邮件提醒发给"账号名即邮箱"的同事；没配到的找管理员在系统设置里补</li>
        <li><b>打印本页</b>：Ctrl+P（浏览器打印），桌面端右上角帮助页内也有打印按钮</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
.hc-tip {
  background: #eff6ff;
  border: 1px solid #bfdbfe;
  color: #1d4ed8;
  border-radius: 8px;
  padding: 8px 12px;
  font-size: 13px;
  margin-bottom: 12px;
}
.hc-card {
  border: 1px solid #e5e7eb;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 12px;
  break-inside: avoid;
}
.hc-card h2 {
  margin: 0 0 8px;
  font-size: 16px;
}
.hc-card ol,
.hc-card ul {
  margin: 0;
  padding-left: 20px;
  font-size: 13.5px;
  line-height: 1.9;
  color: #374151;
}
.hc-qa {
  margin: 8px 0 0;
  font-size: 12.5px;
  color: #92400e;
  background: #fffbeb;
  border-radius: 6px;
  padding: 6px 10px;
}
@media print {
  .hc-tip {
    display: none;
  }
  .hc-card {
    border-color: #9ca3af;
    page-break-inside: avoid;
  }
}
</style>
