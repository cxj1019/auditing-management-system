import request from './request'
import type { PageResult, ProjectItem, ProjectMemberItem, ProjectRequest } from '@/types'

/** 分页筛选查询项目 */
export function pageProjects(params: {
  current: number
  size: number
  status?: number
  type?: string
  keyword?: string
  startDate?: string
  endDate?: string
  hasReport?: boolean
}): Promise<PageResult<ProjectItem>> {
  return request.get('/projects', { params })
}

/** 报告登记 */
export function updateProjectReport(id: number, data: { reportNo?: string; reportDate?: string; reportPartnerName?: string; reportRemark?: string }): Promise<void> {
  return request.put(`/projects/${id}/report`, data)
}

/** 项目下拉选项：全部非归档项目，不做部门隔离 */
export function projectOptions(): Promise<ProjectItem[]> {
  return request.get('/projects/options')
}

/** 登记项目 */
export function createProject(data: ProjectRequest): Promise<void> {
  return request.post('/projects', data)
}

/** 编辑项目基本信息 */
export function updateProject(data: ProjectRequest): Promise<void> {
  return request.put('/projects', data)
}

/** 删除项目（仅进行中且无关联合同） */
export function deleteProject(id: number): Promise<void> {
  return request.delete(`/projects/${id}`)
}

/** 状态流转：action=finish|reopen|archive */
export function changeProjectStatus(id: number, action: 'finish' | 'reopen' | 'archive'): Promise<void> {
  return request.put(`/projects/${id}/status?action=${action}`)
}

// ---------- 参与人员 ----------

/** 参与人员清单 */
export function listProjectMembers(projectId: number): Promise<ProjectMemberItem[]> {
  return request.get(`/projects/${projectId}/members`)
}

/** 添加参与人员 */
export function addProjectMember(projectId: number, memberName: string, memberRole: string): Promise<void> {
  return request.post(`/projects/${projectId}/members?memberName=${encodeURIComponent(memberName)}&memberRole=${encodeURIComponent(memberRole)}`)
}

/** 移除参与人员 */
export function removeProjectMember(projectId: number, memberId: number): Promise<void> {
  return request.delete(`/projects/${projectId}/members/${memberId}`)
}
