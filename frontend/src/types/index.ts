/** 统一响应结构 */
export interface ApiResult<T = unknown> {
  code: number
  message: string
  data: T
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  size: number
}

/** 菜单类型：0-目录 1-菜单 2-按钮 */
export type MenuType = 0 | 1 | 2

/** 菜单 */
export interface MenuItem {
  id: number
  parentId: number
  name: string
  path?: string
  component?: string
  perm?: string
  icon?: string
  type: MenuType
  sort: number
  visible: number
  children: MenuItem[]
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 / 当前用户信息 */
export interface LoginResponse {
  token?: string
  userId: number
  username: string
  nickname?: string
  menus: MenuItem[]
  permissions: string[]
  roles?: string[]
  deptId?: number
}

/** 用户 */
export interface UserItem {
  id: number
  email: string
  nickname?: string
  phone?: string
  deptId?: number
  deptName?: string
  status: number
  createTime?: string
  roleIds?: number[]
  roleNames?: string[]
}

/** 用户创建/编辑请求 */
export interface UserRequest {
  id?: number
  email: string
  password?: string
  nickname?: string
  phone?: string
  deptId?: number
  status?: number
  roleIds?: number[]
}

/** 角色 */
export interface RoleItem {
  id: number
  roleCode: string
  roleName: string
  description?: string
  status: number
  createTime?: string
}

/** 角色创建/编辑请求 */
export interface RoleRequest {
  id?: number
  roleCode: string
  roleName: string
  description?: string
  status?: number
  menuIds?: number[]
}

/** 合同状态：0-草稿 1-执行中 2-已完成 3-已终止 */
export type ContractStatus = 0 | 1 | 2 | 3

/** 在册人员选项 */
export interface UserOption {
  id: number
  nickname: string
  username: string
  email?: string
  deptId?: number
  deptName?: string
}

/** 部门 */
export interface DepartmentItem {
  id: number
  deptName: string
  sort: number
  createTime?: string
}

/** 合同附件 */
export interface ContractAttachmentItem {
  id: number
  contractId: number
  fileName: string
  storedName: string
  fileSize: number
  contentType?: string
  createBy?: string
  createTime?: string
}

/** 项目状态：0-进行中 1-已完成 2-已归档 */
export type ProjectStatus = 0 | 1 | 2

/** 项目（顶层业务维度） */
export interface ProjectItem {
  id: number
  projectNo: string
  name: string
  type: string
  bizNature?: string
  bizType?: string
  clientId: number
  clientName?: string
  deptId: number
  deptName?: string
  partnerName?: string
  managerName: string
  siteLeaderName: string
  startDate: string
  endDate: string
  status: ProjectStatus
  remark?: string
  createTime?: string
}

/** 项目登记/编辑请求 */
export interface ProjectRequest {
  id?: number
  name: string
  type: string
  bizNature?: string
  bizType?: string
  clientId: number
  deptId: number
  partnerName: string
  managerName: string
  siteLeaderName: string
  startDate?: string
  endDate?: string
  remark?: string
}

/** 项目参与人员 */
export interface ProjectMemberItem {
  id: number
  projectId: number
  memberName: string
  memberRole: string
  sort?: number
  createTime?: string
}

/** 客户 */
export interface ClientItem {
  id: number
  clientNo: string
  clientName: string
  clientType: string
  creditCode?: string
  registeredCapital?: string
  registeredAddress?: string
  legalRepresentative?: string
  businessScope?: string
  contactPerson?: string
  contactPhone?: string
  invoiceTitle?: string
  invoiceTaxNo?: string
  invoiceBankName?: string
  invoiceBankAccount?: string
  invoiceAddress?: string
  invoicePhone?: string
  remark?: string
  createTime?: string
}

/** 客户创建/编辑请求 */
export interface ClientRequest {
  clientName: string
  clientType: string
  creditCode?: string
  registeredCapital?: string
  registeredAddress?: string
  legalRepresentative?: string
  businessScope?: string
  contactPerson?: string
  contactPhone?: string
  invoiceTitle?: string
  invoiceTaxNo?: string
  invoiceBankName?: string
  invoiceBankAccount?: string
  invoiceAddress?: string
  invoicePhone?: string
  remark?: string
}

/** 客户联系人 */
export interface ClientContactItem {
  id: number
  clientId: number
  contactName: string
  position?: string
  phone?: string
  email?: string
  remark?: string
}

/** 客户联系人创建/编辑请求 */
export interface ClientContactRequest {
  contactName: string
  position?: string
  phone?: string
  email?: string
  remark?: string
}

/** 日程 */
export interface ScheduleItem {
  id: number
  projectId?: number
  projectName?: string
  userId: number
  creatorName?: string
  title: string
  description?: string
  scheduleDate: string
  endDate?: string
  startTime?: string
  endTime?: string
  hours: number
  type: string
  eventId?: string
  createBy?: string
  createTime?: string
}

/** 日程创建/编辑请求 */
export interface ScheduleRequest {
  projectId?: number
  userId?: number
  title: string
  description?: string
  scheduleDate: string
  endDate?: string
  startTime?: string
  endTime?: string
  hours: number
  type: string
}

/** 合同 */
export interface ContractItem {
  id: number
  projectId: number
  projectNo?: string
  projectName?: string
  contractNo: string
  name?: string
  /** 客户名称（来自所属项目，只读） */
  clientName?: string
  contractType: string
  bizType?: string
  amount: number
  taxRate?: number
  amountExTax?: number
  taxAmount?: number
  currency?: string
  foreignAmount?: number
  exchangeRate?: number
  signDate: string
  serviceStart?: string
  serviceEnd?: string
  keeperName: string
  status: ContractStatus
  remark?: string
  createTime?: string
  ratePublishTime?: string
}

/** 合同创建/编辑请求 */
export interface ContractRequest {
  id?: number
  projectId: number
  name?: string
  contractType: string
  bizType?: string
  amount: number
  taxRate?: number
  amountExTax?: number
  taxAmount?: number
  signDate?: string
  serviceStart?: string
  serviceEnd?: string
  keeperName: string
  remark?: string
  currency?: string
  foreignAmount?: number
  exchangeRate?: number
  ratePublishTime?: string
}

/** 收款记录 */
export interface PaymentItem {
  id: number
  contractId: number
  invoiceId?: number
  invoiceNo?: string
  projectNo?: string
  projectName?: string
  contractNo: string
  contractName: string
  clientName: string
  amount: number
  paymentDate: string
  paymentMethod: string
  payerName?: string
  remark?: string
  createTime?: string
}

/** 收款登记/编辑请求 */
export interface PaymentRequest {
  /** 核销收款：已开票发票 ID */
  invoiceId?: number
  /** 预收收款：合同 ID（暂不挂发票，开票后可核销） */
  contractId?: number
  amount: number
  paymentDate: string
  paymentMethod: string
  payerName?: string
  remark?: string
}

/** 合同收款汇总 */
export interface CollectionSummaryItem {
  contractId: number
  contractNo: string
  contractName: string
  clientName: string
  contractAmount: number
  totalCollected: number
  outstanding: number
  progressPercent: number
}

/** 发票状态：0-待开票 1-已开票 2-已作废 */
export type InvoiceStatus = 0 | 1 | 2

/** 发票 */
export interface InvoiceItem {
  id: number
  invoiceNo: string
  contractId: number
  contractNo: string
  contractName: string
  projectNo?: string
  projectName?: string
  clientId: number
  clientName?: string
  type: string
  amount: number
  taxRate?: number
  amountExTax?: number
  taxAmount?: number
  currency?: string
  foreignAmount?: number
  exchangeRate?: number
  ratePublishTime?: string
  invoiceItem?: string
  taxCode?: string
  taxClass?: string
  invoiceDate?: string
  status: InvoiceStatus
  isRecharge?: boolean
  remark?: string
  collectedAmount: number
  createTime?: string
}

/** 发票登记/编辑请求 */
export interface InvoiceRequest {
  id?: number
  isRecharge?: boolean
  contractId: number
  invoiceNo: string
  type: string
  amount: number
  taxRate?: number
  amountExTax?: number
  taxAmount?: number
  currency?: string
  foreignAmount?: number
  exchangeRate?: number
  ratePublishTime?: string
  invoiceItem?: string
  taxCode?: string
  taxClass?: string
  invoiceDate?: string
  remark?: string
}

/** 已开票发票下拉选项（供收款核销） */
export interface InvoiceOptionItem {
  id: number
  invoiceNo: string
  contractId: number
  contractNo: string
  contractName: string
  clientName?: string
  amount: number
  collectedAmount: number
}

/** 发票核销汇总 */
export interface InvoiceSummaryItem {
  invoiceId: number
  invoiceNo: string
  type: string
  contractNo: string
  contractName: string
  clientName?: string
  invoiceAmount: number
  collectedAmount: number
  outstanding: number
  progressPercent: number
}

/** 发票附件 */
export interface InvoiceAttachmentItem {
  id: number
  invoiceId: number
  attachmentType: string
  fileName: string
  fileSize: number
  contentType?: string
  createTime?: string
}

/** 非草稿合同下拉选项（供发票登记，带出客户开票信息） */
export interface ContractOptionItem {
  id: number
  contractNo: string
  name: string
  contractType: string
  amount: number
  projectName?: string
  clientId?: number
  clientName?: string
  clientType?: string
  bizType?: string
  invoiceItem?: string
  taxCode?: string
  taxClass?: string
  invoiceTitle?: string
  invoiceTaxNo?: string
  invoiceBankName?: string
  invoiceBankAccount?: string
  invoiceAddress?: string
  invoicePhone?: string
}

/** 业务类型字典（项目性质/项目类型/业务类型/字号/开票要素） */
export interface BusinessTypeItem {
  id: number
  bizNature: string
  projectType: string
  bizType: string
  bizDesc?: string
  noChar?: string
  feeFreq?: string
  taxCode?: string
  taxClass?: string
  invoiceItem?: string
  sort?: number
}

/** 报销状态：0-草稿 1-待审批 2-已批准 3-已驳回 4-待终审 */
export type ReimbursementStatus = 0 | 1 | 2 | 3 | 4

/** 费用明细行（请求） */
export interface ReimbursementItemData {
  /** 明细行 ID（草稿保存后由后端返回） */
  id?: number
  category: string
  amount: number
  expenseDate: string
  description?: string
  invoiceNumber?: string
  isVatInvoice?: boolean
  /** 发票类型：none-不涉及 vat_general-增值税普通发票 vat_special-增值税专用发票 */
  invoiceType?: string
  /** 税率（%，专票必填） */
  taxRate?: number
  /** 税额（元，可手填） */
  taxAmount?: number
  /** 税额是否手动覆盖（仅前端记忆，不提交） */
  taxAmountManual?: boolean
  projectId?: number
  billable?: boolean
}

/** 应收账龄行 */
export interface InvoiceAgingItem {
  invoiceId: number
  invoiceNo?: string
  clientName?: string
  contractNo?: string
  projectName?: string
  invoiceDate?: string
  agingDays: number
  bucket: string
  amount: number
  collectedAmount: number
  outstanding: number
}

/** 垫付台账行（按项目） */
export interface RechargeLedgerItem {
  projectId: number
  projectNo?: string
  projectName?: string
  clientName?: string
  rechargeTotal: number
  invoicedTotal: number
  collectedTotal: number
  pendingInvoice: number
  pendingCollect: number
  status: string
}

/** 报销费用类别 */
export interface ExpenseCategoryItem {
  id: number
  name: string
  sort: number
  status: number
}

/** 报销费用类别创建/编辑请求 */
export interface ExpenseCategoryRequest {
  name: string
  sort?: number
  status?: number
}

/** 报销单 */
export interface ReimbursementItem {
  id: number
  reimbursementNo: string
  applicantId?: number
  applicantUsername: string
  applicantName?: string
  projectId?: number
  title: string
  totalAmount: number
  status: ReimbursementStatus
  primaryApproverName?: string
  isInvoiceReceived: boolean
  isPaid: boolean
  approverUsername?: string
  approverName?: string
  approveTime?: string
  approveComment?: string
  createTime?: string
}

/** 报销草稿创建/编辑请求 */
export interface ReimbursementRequest {
  projectId?: number
  title: string
  items: ReimbursementItemData[]
}

/** 报销附件 */
export interface ReimbursementAttachmentItem {
  id: number
  reimbursementId: number
  /** 关联费用明细行 ID（NULL 表示挂单整体） */
  itemId?: number
  fileName: string
  storedName: string
  fileSize: number
  contentType?: string
  createBy?: string
  createTime?: string
}

/** 报销费用明细导出行 */
export interface ReimbursementExportItem {
  reimbursementNo: string
  applicantName?: string
  projectName?: string
  title: string
  itemCategory: string
  itemAmount: number
  itemExpenseDate: string
  itemDescription?: string
  invoiceNumber?: string
  isVatInvoice?: boolean
  invoiceType?: string
  taxRate?: number
  taxAmount?: number
  statusLabel: string
  approverName?: string
}

/** 报销审批请求 */
export interface ApproveRequest {
  action: 'approve' | 'reject'
  comment: string
}

/** 函证状态：0-未发出 1-已发出 2-已回函 3-已作废 */
export type ConfirmationStatus = 0 | 1 | 2 | 3

/** 函证 */
export interface ConfirmationItem {
  id: number
  confirmationNo: string
  type: string
  confirmationMethod?: string
  targetUnit: string
  summary: string
  projectId?: number
  projectName?: string
  status: ConfirmationStatus
  sentDate?: string
  sendTrackingNo?: string
  confirmedDate?: string
  replyTrackingNo?: string
  hasReply?: boolean
  replyMatched?: boolean
  discrepancyReason?: string
  overdue?: boolean
  createTime?: string
}

/** 函证登记/编辑请求（编号人工填写） */
export interface ConfirmationRequest {
  id?: number
  confirmationNo: string
  type: string
  confirmationMethod?: string
  targetUnit: string
  summary: string
  projectId?: number
  sendTrackingNo?: string
  replyTrackingNo?: string
  replyMatched?: boolean
  discrepancyReason?: string
}

/** 函证附件 */
export interface ConfirmationAttachmentItem {
  id: number
  confirmationId: number
  attachmentType: string
  fileName: string
  storedName: string
  fileSize: number
  contentType?: string
  createBy?: string
  createTime?: string
}

/** 项目利润 */
export interface ProjectProfitItem {
  projectId: number
  projectNo: string
  projectName: string
  clientName: string
  contractAmount: number
  totalCollected: number
  directCost: number
  expenseCost: number
  laborCost: number
  grossProfit: number
  marginPercent: number | null
}

/** 经营概览 */
export interface CostOverview {
  totalIncome: number
  totalCost: number
  grossProfit: number
  collectionRate: number | null
}

/** 人工成本 */
export interface LaborCostItem {
  id: number
  projectId: number
  personName: string
  costMonth: string
  amount: number
  remark?: string
  createTime?: string
}

/** 人工成本登记/编辑请求 */
export interface LaborCostRequest {
  projectId: number
  personName: string
  costMonth: string
  amount: number
  remark?: string
}

/** 站内通知 */
export interface NotificationItem {
  id: number
  userId: number
  type: string
  title: string
  content?: string
  relatedPath?: string
  relatedId: number
  isRead: number
  dedupDate?: string
  createTime?: string
}

/** 审计日志 */
export interface AuditLogItem {
  id: number
  username?: string
  operation: string
  result: string
  errorMsg?: string
  costMs?: number
  ip?: string
  createTime?: string
}

/** 工作台聚合 */
export interface DashboardSummary {
  todo: {
    pendingReimbursement: number
    pendingInvoice: number
    overdueReceivable: number
    overdueConfirmation: number
    expiringContract: number
  }
  weekHours: number
  todaySchedules: {
    id: number
    title?: string
    type?: string
    startTime?: string
    endTime?: string
    hours?: number
    projectName?: string
  }[]
  receivable: {
    invoicedAmount: number
    collectedAmount: number
    outstanding: number
  }
  topProjects: {
    projectNo: string
    projectName: string
    contractAmount?: number
    totalCollected?: number
    progressPercent?: number
  }[]
}

/** 中国银行外汇牌价行 */
export interface ExchangeRateRow {
  currencyName: string
  spotBuy: string
  cashBuy: string
  spotSell: string
  cashSell: string
  bocRate: string
  publishTime: string
  pair?: string
  date?: string
}

/** 项目工时汇总行 */
export interface ProjectHoursItem {
  projectId: number
  projectNo: string
  projectName: string
  clientName?: string
  memberName?: string
  totalHours: number
}
