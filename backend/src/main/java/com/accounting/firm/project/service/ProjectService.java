package com.accounting.firm.project.service;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.project.dto.ProjectOptionVO;
import com.accounting.firm.project.dto.ProjectRequest;
import com.accounting.firm.project.entity.Project;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 项目服务
 */
public interface ProjectService extends IService<Project> {

    /** 分页筛选查询项目（含日期范围） */
    PageResult<Project> pageProjects(long current, long size,
                                     Integer status, String type, String keyword,
                                     java.time.LocalDate startDate, java.time.LocalDate endDate);

    /** 项目下拉选项：全部非归档项目（不做部门隔离，供报销等成本归集场景关联） */
    List<ProjectOptionVO> listOptions();

    /** 登记项目（自动编号，初始状态进行中） */
    void createProject(ProjectRequest request);

    /** 编辑项目基本信息（编号与状态不可修改；归档项目不可编辑） */
    void updateProject(ProjectRequest request);

    /** 删除项目（仅进行中且无关联合同） */
    void deleteProject(Long id);

    /**
     * 状态流转
     *
     * @param id     项目 ID
     * @param action 动作：finish-完成 reopen-重开 archive-归档
     */
    void changeStatus(Long id, String action);
}
