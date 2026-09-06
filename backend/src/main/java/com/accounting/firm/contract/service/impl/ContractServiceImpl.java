package com.accounting.firm.contract.service.impl;

import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.accounting.firm.common.security.DataScopeService;
import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.contract.dto.ContractRequest;
import com.accounting.firm.contract.dto.ContractVO;
import com.accounting.firm.contract.entity.Contract;
import com.accounting.firm.contract.entity.ContractNoType;
import com.accounting.firm.contract.entity.ContractStatus;
import com.accounting.firm.contract.mapper.ContractMapper;
import com.accounting.firm.contract.mapper.ContractNoTypeMapper;
import com.accounting.firm.contract.service.ContractNoGenerator;
import com.accounting.firm.contract.service.ContractService;
import com.accounting.firm.system.entity.BusinessType;
import com.accounting.firm.system.mapper.BusinessTypeMapper;
import com.accounting.firm.project.entity.Project;
import com.accounting.firm.project.entity.ProjectStatus;
import com.accounting.firm.project.mapper.ProjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 合同服务实现
 */
@Slf4j
@Service
public class ContractServiceImpl extends ServiceImpl<ContractMapper, Contract> implements ContractService {

    /** 编号唯一约束冲突后的重试次数 */
    private static final int NO_RETRY_TIMES = 1;

    private final ProjectMapper projectMapper;
    private final ClientMapper clientMapper;
    private final DataScopeService dataScopeService;
    private final ContractNoTypeMapper contractNoTypeMapper;
    private final BusinessTypeMapper businessTypeMapper;
    private final com.accounting.firm.invoice.mapper.InvoiceMapper invoiceMapper;
    private final com.accounting.firm.collection.mapper.ContractPaymentMapper paymentMapper;

    public ContractServiceImpl(ProjectMapper projectMapper, ClientMapper clientMapper,
                               DataScopeService dataScopeService, ContractNoTypeMapper contractNoTypeMapper,
                               BusinessTypeMapper businessTypeMapper,
                               com.accounting.firm.invoice.mapper.InvoiceMapper invoiceMapper,
                               com.accounting.firm.collection.mapper.ContractPaymentMapper paymentMapper) {
        this.projectMapper = projectMapper;
        this.clientMapper = clientMapper;
        this.dataScopeService = dataScopeService;
        this.contractNoTypeMapper = contractNoTypeMapper;
        this.businessTypeMapper = businessTypeMapper;
        this.invoiceMapper = invoiceMapper;
        this.paymentMapper = paymentMapper;
    }

    @Override
    public List<com.accounting.firm.contract.dto.ContractOptionVO> options() {
        List<Contract> contracts = lambdaQuery()
                .ne(Contract::getStatus, ContractStatus.DRAFT.getCode())
                .orderByDesc(Contract::getCreateTime)
                .list();
        if (contracts.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = contracts.stream()
                .map(Contract::getProjectId).filter(Objects::nonNull).distinct().toList();
        Map<Long, Project> projectMap = projectIds.isEmpty()
                ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, Function.identity()));
        // 开票/收款关联场景屏蔽已归档项目的合同，并按项目归属部门隔离
        var scope = dataScopeService.currentScope();
        contracts = contracts.stream().filter(c -> {
            Project p = projectMap.get(c.getProjectId());
            if (p == null) {
                return false;
            }
            if (p.getStatus() != null && p.getStatus() == com.accounting.firm.project.entity.ProjectStatus.ARCHIVED.getCode()) {
                return false;
            }
            return switch (scope.type()) {
                case DEPT -> scope.deptId().equals(p.getDeptId());
                case SELF -> scope.username().equals(c.getCreateBy());
                default -> true;
            };
        }).toList();
        if (contracts.isEmpty()) {
            return List.of();
        }
        List<Long> clientIds = projectMap.values().stream()
                .map(Project::getClientId).filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        Map<Long, Client> clientMap = clientIds.isEmpty()
                ? Map.of()
                : clientMapper.selectBatchIds(clientIds).stream()
                        .collect(Collectors.toMap(Client::getId, Function.identity()));
        // 业务类型 → 开票要素（合同未填业务类型时回退项目的业务类型）
        List<String> bizTypes = new java.util.ArrayList<>();
        for (Contract c : contracts) {
            Project p0 = projectMap.get(c.getProjectId());
            String effective = StringUtils.hasText(c.getBizType())
                    ? c.getBizType()
                    : (p0 != null ? p0.getBizType() : null);
            c.setBizType(effective);
            if (StringUtils.hasText(effective)) {
                bizTypes.add(effective);
            }
        }
        Map<String, BusinessType> bizMap = bizTypes.isEmpty()
                ? Map.of()
                : businessTypeMapper.selectList(new LambdaQueryWrapper<BusinessType>()
                        .in(BusinessType::getBizType, bizTypes)).stream()
                        .collect(Collectors.toMap(BusinessType::getBizType, Function.identity(), (a, b) -> a));
        return contracts.stream().map(c -> {
            var vo = new com.accounting.firm.contract.dto.ContractOptionVO();
            vo.setId(c.getId());
            vo.setContractNo(c.getContractNo());
            vo.setName(c.getName());
            vo.setContractType(c.getContractType());
            vo.setAmount(c.getAmount());
            vo.setBizType(c.getBizType());
            BusinessType biz = bizMap.get(c.getBizType());
            if (biz != null) {
                vo.setInvoiceItem(biz.getInvoiceItem());
                vo.setTaxCode(biz.getTaxCode());
                vo.setTaxClass(biz.getTaxClass());
            }
            Project project = projectMap.get(c.getProjectId());
            if (project != null) {
                vo.setProjectName(project.getName());
                vo.setClientId(project.getClientId());
                Client client = clientMap.get(project.getClientId());
                if (client != null) {
                    vo.setClientName(client.getClientName());
                    vo.setInvoiceTitle(client.getInvoiceTitle());
                    vo.setInvoiceTaxNo(client.getInvoiceTaxNo());
                    vo.setInvoiceBankName(client.getInvoiceBankName());
                    vo.setInvoiceBankAccount(client.getInvoiceBankAccount());
                    vo.setInvoiceAddress(client.getInvoiceAddress());
                    vo.setInvoicePhone(client.getInvoicePhone());
                }
            }
            return vo;
        }).toList();
    }

    @Override
    public PageResult<ContractVO> pageContracts(long current, long size, String name,
                                                String clientName, String ownerName, Integer status) {
        LambdaQueryWrapper<Contract> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(name), Contract::getName, name)
                .eq(StringUtils.hasText(ownerName), Contract::getKeeperName, ownerName)
                .eq(status != null, Contract::getStatus, status);
        // 客户名称来自所属项目：EXISTS 子查询关联客户表匹配
        if (StringUtils.hasText(clientName)) {
            wrapper.apply("EXISTS (SELECT 1 FROM project p JOIN client c ON c.id = p.client_id"
                            + " WHERE p.id = contract.project_id AND c.client_name LIKE {0})",
                    "%" + clientName + "%");
        }
        // 合同按项目归属部门隔离：admin 全部；本部门成员看本部门项目的合同；无部门用户仅看自己创建
        var scope = dataScopeService.currentScope();
        switch (scope.type()) {
            case DEPT -> wrapper.inSql(Contract::getProjectId, scope.projectDeptInSql());
            case SELF -> wrapper.eq(Contract::getCreateBy, scope.username());
            default -> { }
        }
        wrapper.orderByDesc(Contract::getCreateTime);
        Page<Contract> page = page(new Page<>(current, size), wrapper);
        List<ContractVO> records = toVOs(page.getRecords());
        return new PageResult<>(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 实体转 VO 并批量填充项目与客户信息（避免 N+1 查询） */
    private List<ContractVO> toVOs(List<Contract> contracts) {
        List<Long> projectIds = contracts.stream()
                .map(Contract::getProjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Project> projectMap = projectIds.isEmpty()
                ? Map.of()
                : projectMapper.selectBatchIds(projectIds).stream()
                        .collect(Collectors.toMap(Project::getId, Function.identity()));
        // 客户名称按项目的 client_id 从客户表带出（clientName 为项目实体上的联表字段，原始查询不含）
        List<Long> clientIds = projectMap.values().stream()
                .map(Project::getClientId)
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .distinct()
                .toList();
        Map<Long, String> clientNames = clientIds.isEmpty()
                ? Map.of()
                : clientMapper.selectBatchIds(clientIds).stream()
                        .collect(Collectors.toMap(Client::getId, Client::getClientName));
        return contracts.stream().map(c -> {
            ContractVO vo = ContractVO.from(c);
            Project project = projectMap.get(c.getProjectId());
            if (project != null) {
                vo.setProjectNo(project.getProjectNo());
                vo.setProjectName(project.getName());
                // 客户随项目带出
                vo.setClientName(clientNames.get(project.getClientId()));
            }
            return vo;
        }).toList();
    }

    @Override
    public void createContract(ContractRequest request) {
        validateDates(request);
        requireValidProject(request.getProjectId());
        for (int attempt = 0; attempt <= NO_RETRY_TIMES; attempt++) {
            String contractNo = generateContractNo(request);
            Contract contract = new Contract();
            copyBasicFields(request, contract);
            deriveFxAmount(contract);
            contract.setProjectId(request.getProjectId());
            contract.setContractNo(contractNo);
            contract.setStatus(ContractStatus.DRAFT.getCode());
            try {
                save(contract);
                return;
            } catch (DuplicateKeyException e) {
                // 并发下编号冲突：重试一次，仍失败则由数据库约束兜底报错
                if (attempt == NO_RETRY_TIMES) {
                    throw new BusinessException("合同编号生成冲突，请重试");
                }
                log.warn("合同编号冲突，重试生成: no={}", contractNo);
            }
        }
    }

    @Override
    public void updateContract(ContractRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("合同 ID 不能为空");
        }
        Contract contract = getById(request.getId());
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        validateDates(request);
        // 仅更新基本信息字段；编号、状态与所属项目不可通过编辑修改
        copyBasicFields(request, contract);
        deriveFxAmount(contract);
        updateById(contract);
    }

    @Override
    public void changeStatus(Long id, Integer targetStatus) {
        if (id == null || targetStatus == null) {
            throw new BusinessException("参数不完整");
        }
        Contract contract = getById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        ContractStatus current = ContractStatus.of(contract.getStatus());
        ContractStatus target = ContractStatus.of(targetStatus);
        current.transitionTo(target);
        contract.setStatus(target.getCode());
        updateById(contract);
    }

    @Override
    public void deleteContract(Long id) {
        Contract contract = getById(id);
        if (contract == null) {
            throw new BusinessException("合同不存在");
        }
        // 草稿与已终止的合同可删除（已终止的合同无存续意义）
        if (contract.getStatus() != ContractStatus.DRAFT.getCode()
                && contract.getStatus() != ContractStatus.TERMINATED.getCode()) {
            throw new BusinessException("仅草稿或已终止状态的合同可删除");
        }
        // 有发票或收款关联时不允许删除，避免产生孤儿数据
        Long invoiceCount = invoiceMapper.selectCount(
                new LambdaQueryWrapper<com.accounting.firm.invoice.entity.Invoice>()
                        .eq(com.accounting.firm.invoice.entity.Invoice::getContractId, id));
        if (invoiceCount > 0) {
            throw new BusinessException("合同已关联发票，不可删除");
        }
        Long paymentCount = paymentMapper.selectCount(
                new LambdaQueryWrapper<com.accounting.firm.collection.entity.ContractPayment>()
                        .eq(com.accounting.firm.collection.entity.ContractPayment::getContractId, id));
        if (paymentCount > 0) {
            throw new BusinessException("合同已存在收款记录，不可删除");
        }
        removeById(id);
    }

    /** 校验项目存在且处于进行中状态 */
    private void requireValidProject(Long projectId) {
        if (projectId == null) {
            throw new BusinessException("所属项目不能为空");
        }
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException("所属项目不存在");
        }
        if (project.getStatus() != ProjectStatus.IN_PROGRESS.getCode()) {
            throw new BusinessException("仅进行中的项目可登记合同");
        }
    }

    /**
     * 生成合同字号，解析优先级：
     * ① 合同业务类型 → 字典字号类型（审/验/咨/代/商）→ 字号前缀，按「前缀(签约年份)第{4 位流水}号」编号；
     * ② 未填业务类型时回退按合同类型匹配字号；
     * ③ 均未配置的（评估/其他等）沿用 HT 日期流水规则。
     */
    private String generateContractNo(ContractRequest request) {
        // ① 业务类型字典的字号类型优先
        if (StringUtils.hasText(request.getBizType())) {
            BusinessType bizType = businessTypeMapper.selectOne(new LambdaQueryWrapper<BusinessType>()
                    .eq(BusinessType::getBizType, request.getBizType())
                    .last("LIMIT 1"));
            if (bizType != null && StringUtils.hasText(bizType.getNoChar())) {
                ContractNoType noType = contractNoTypeMapper.selectOne(new LambdaQueryWrapper<ContractNoType>()
                        .eq(ContractNoType::getTypeChar, bizType.getNoChar())
                        .last("LIMIT 1"));
                if (noType != null) {
                    return generateStructuredNo(noType.getPrefix(), request.getSignDate().getYear());
                }
            }
        }
        // ② 按合同类型匹配字号
        ContractNoType noType = contractNoTypeMapper.selectOne(new LambdaQueryWrapper<ContractNoType>()
                .eq(ContractNoType::getContractType, request.getContractType()));
        if (noType != null) {
            return generateStructuredNo(noType.getPrefix(), request.getSignDate().getYear());
        }
        // ③ 兜底
        return generateLegacyNo();
    }

    /** 按前缀+年份取同类最大字号后递增 */
    private String generateStructuredNo(String prefix, int year) {
        String head = prefix + "(" + year + ")第";
        Contract maxContract = lambdaQuery()
                .likeRight(Contract::getContractNo, head)
                .orderByDesc(Contract::getContractNo)
                .last("LIMIT 1")
                .one();
        return ContractNoGenerator.nextStructured(prefix, year,
                maxContract == null ? null : maxContract.getContractNo());
    }

    /** 兜底编号：HT + 当日日期 + 当日流水，取当日最大编号后递增 */
    private String generateLegacyNo() {
        LocalDate today = LocalDate.now();
        String prefix = "HT" + "%1$tY%1$tm%1$td".formatted(today);
        Contract maxContract = lambdaQuery()
                .likeRight(Contract::getContractNo, prefix)
                .orderByDesc(Contract::getContractNo)
                .last("LIMIT 1")
                .one();
        return ContractNoGenerator.next(today, maxContract == null ? null : maxContract.getContractNo());
    }

    /** 外币折算：非人民币且未填金额时，按 外币金额 ÷ 100 × 中行牌价 折算 */
    private void deriveFxAmount(Contract contract) {
        boolean isCny = contract.getCurrency() == null || "人民币".equals(contract.getCurrency());
        if (contract.getAmount() != null || isCny
                || contract.getForeignAmount() == null || contract.getExchangeRate() == null) {
            return;
        }
        contract.setAmount(contract.getForeignAmount()
                .multiply(contract.getExchangeRate())
                .divide(java.math.BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP));
    }

    /** 服务期限校验：都填写时开始不得晚于结束（允许不约定期间） */
    private void validateDates(ContractRequest request) {
        if (request.getServiceStart() != null && request.getServiceEnd() != null
                && request.getServiceStart().isAfter(request.getServiceEnd())) {
            throw new BusinessException("服务期限开始日期不能晚于结束日期");
        }
    }

    /** 复制基本信息字段（不含编号、状态、所属项目与客户） */
    private void copyBasicFields(ContractRequest request, Contract contract) {
        contract.setName(request.getName());
        contract.setContractType(request.getContractType());
        contract.setBizType(request.getBizType());
        contract.setAmount(request.getAmount());
        contract.setCurrency(StringUtils.hasText(request.getCurrency()) ? request.getCurrency() : "人民币");
        contract.setForeignAmount(request.getForeignAmount());
        contract.setExchangeRate(request.getExchangeRate());
        contract.setRatePublishTime(request.getRatePublishTime());
        contract.setSignDate(request.getSignDate());
        contract.setServiceStart(request.getServiceStart());
        contract.setServiceEnd(request.getServiceEnd());
        contract.setKeeperName(request.getKeeperName());
        contract.setRemark(request.getRemark());
    }
}
