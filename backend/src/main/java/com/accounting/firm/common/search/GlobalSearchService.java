package com.accounting.firm.common.search;

import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.accounting.firm.reimbursement.entity.Reimbursement;
import com.accounting.firm.reimbursement.mapper.ReimbursementMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局搜索：客户/项目/合同/报销单（各按数据权限过滤，每类最多 5 条）
 */
@Service
@RequiredArgsConstructor
public class GlobalSearchService {

    private final ClientMapper clientMapper;
    private final ProjectMapper projectMapper;
    private final ContractMapper contractMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final DataScopeService dataScopeService;

    public List<Map<String, Object>> search(String keyword) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (keyword == null || keyword.isBlank()) {
            return result;
        }
        String kw = keyword.trim();

        for (Client c : clientMapper.selectList(new LambdaQueryWrapper<Client>()
                .like(Client::getClientName, kw).or().like(Client::getClientNo, kw)
                .last("LIMIT 5"))) {
            result.add(row("client", "客户", c.getId(), c.getClientNo(), c.getClientName(), "/business/client"));
        }

        var projectScope = dataScopeService.currentScope();
        var pw = new LambdaQueryWrapper<Project>()
                .and(w -> w.like(Project::getProjectNo, kw).or().like(Project::getName, kw))
                .last("LIMIT 5");
        if (projectScope.type() == DataScopeService.ScopeType.DEPT) {
            pw.eq(Project::getDeptId, projectScope.deptId());
        } else if (projectScope.type() == DataScopeService.ScopeType.SELF) {
            pw.eq(Project::getCreateBy, projectScope.username());
        }
        for (Project p : projectMapper.selectList(pw)) {
            result.add(row("project", "项目", p.getId(), p.getProjectNo(), p.getName(), "/business/project"));
        }

        for (Contract ct : contractMapper.selectList(new LambdaQueryWrapper<Contract>()
                .like(Contract::getContractNo, kw).or().like(Contract::getName, kw)
                .last("LIMIT 5"))) {
            result.add(row("contract", "合同", ct.getId(), ct.getContractNo(),
                    ct.getName() == null ? "" : ct.getName(), "/business/contract"));
        }

        var reimbScope = dataScopeService.currentScope();
        var rw = new LambdaQueryWrapper<Reimbursement>()
                .and(w -> w.like(Reimbursement::getReimbursementNo, kw).or().like(Reimbursement::getTitle, kw))
                .last("LIMIT 5");
        if (reimbScope.type() == DataScopeService.ScopeType.DEPT) {
            rw.inSql(Reimbursement::getProjectId, reimbScope.projectDeptInSql());
        } else if (reimbScope.type() == DataScopeService.ScopeType.SELF) {
            rw.eq(Reimbursement::getApplicantId, reimbScope.userId());
        }
        for (Reimbursement r : reimbursementMapper.selectList(rw)) {
            result.add(row("reimbursement", "报销单", r.getId(), r.getReimbursementNo(), r.getTitle(), "/business/reimbursement"));
        }
        return result;
    }

    private Map<String, Object> row(String type, String typeLabel, Long id, String no, String name, String path) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", type);
        row.put("typeLabel", typeLabel);
        row.put("id", id);
        row.put("no", no);
        row.put("name", name);
        row.put("path", path);
        return row;
    }
}
