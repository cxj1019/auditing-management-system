package com.accounting.firm.client.service.impl;

import com.accounting.firm.client.dto.ClientRequest;
import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.client.service.ClientService;
import com.accounting.firm.common.api.PageResult;
import com.accounting.firm.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements ClientService {

    private final com.accounting.firm.contract.mapper.ContractMapper contractMapper;
    private final com.accounting.firm.project.mapper.ProjectMapper projectMapper;
    private final com.accounting.firm.invoice.mapper.InvoiceMapper invoiceMapper;
    private final com.accounting.firm.collection.mapper.ContractPaymentMapper paymentMapper;

    @Override
    public PageResult<Client> pageClients(long current, long size, String keyword,
                                          String clientType) {
        LambdaQueryWrapper<Client> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StringUtils.hasText(clientType), Client::getClientType, clientType);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Client::getClientNo, keyword)
                    .or().like(Client::getClientName, keyword));
        }
        // 客户不再归属部门，全员可见；部门隔离由项目维度承担
        wrapper.orderByDesc(Client::getCreateTime);
        Page<Client> page = page(new Page<>(current, size), wrapper);
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public Long createClient(ClientRequest request) {
        Long count = lambdaQuery().eq(Client::getClientName, request.getClientName()).count();
        if (count > 0) {
            throw new BusinessException("客户名称已存在");
        }
        Client client = new Client();
        copyFields(request, client);
        client.setClientNo(generateNo());
        save(client);
        return client.getId();
    }

    @Override
    public void updateClient(ClientRequest request) {
        if (request.getId() == null) {
            throw new BusinessException("客户 ID 不能为空");
        }
        Client client = getById(request.getId());
        if (client == null) {
            throw new BusinessException("客户不存在");
        }
        copyFields(request, client);
        updateById(client);
    }

    @Override
    public void deleteClient(Long id) {
        if (getById(id) == null) {
            throw new BusinessException("客户不存在");
        }
        removeById(id);
    }

    @Override
    public com.accounting.firm.client.dto.ClientStatementVO statement(Long clientId) {
        Client client = getById(clientId);
        if (client == null) {
            throw new BusinessException("客户不存在");
        }
        var vo = new com.accounting.firm.client.dto.ClientStatementVO();
        vo.setClientNo(client.getClientNo());
        vo.setClientName(client.getClientName());
        vo.setClientType(client.getClientType());

        // 客户的合同：合同挂项目，项目挂客户
        List<com.accounting.firm.contract.entity.Contract> contracts = contractMapper.selectList(
                new LambdaQueryWrapper<com.accounting.firm.contract.entity.Contract>()
                        .inSql(com.accounting.firm.contract.entity.Contract::getProjectId,
                                "SELECT id FROM project WHERE client_id = " + clientId)
                        .orderByDesc(com.accounting.firm.contract.entity.Contract::getCreateTime));
        Map<Long, com.accounting.firm.project.entity.Project> projectMap = projectMapper.selectList(
                        new LambdaQueryWrapper<com.accounting.firm.project.entity.Project>()
                                .eq(com.accounting.firm.project.entity.Project::getClientId, clientId))
                .stream().collect(java.util.stream.Collectors.toMap(
                        com.accounting.firm.project.entity.Project::getId, java.util.function.Function.identity()));

        var contractRows = new java.util.ArrayList<com.accounting.firm.client.dto.ClientStatementVO.ContractRow>();
        BigDecimal contractTotal = BigDecimal.ZERO;
        for (var c : contracts) {
            var row = new com.accounting.firm.client.dto.ClientStatementVO.ContractRow();
            row.setContractNo(c.getContractNo());
            row.setName(c.getName());
            var project = projectMap.get(c.getProjectId());
            row.setProjectName(project == null ? null : project.getName());
            row.setAmount(c.getAmount());
            row.setStatusLabel(switch (c.getStatus() == null ? -1 : c.getStatus()) {
                case 0 -> "草稿";
                case 1 -> "执行中";
                case 2 -> "已完成";
                case 3 -> "已终止";
                default -> "未知";
            });
            contractRows.add(row);
            contractTotal = contractTotal.add(c.getAmount() == null ? BigDecimal.ZERO : c.getAmount());
        }
        vo.setContracts(contractRows);
        vo.setContractTotal(contractTotal);

        // 发票（含税），不含已作废
        List<com.accounting.firm.invoice.entity.Invoice> invoices = invoiceMapper.selectList(
                new LambdaQueryWrapper<com.accounting.firm.invoice.entity.Invoice>()
                        .eq(com.accounting.firm.invoice.entity.Invoice::getClientId, clientId)
                        .ne(com.accounting.firm.invoice.entity.Invoice::getStatus, 2)
                        .orderByDesc(com.accounting.firm.invoice.entity.Invoice::getCreateTime));
        Map<Long, String> contractNoMap = contracts.stream().collect(java.util.stream.Collectors.toMap(
                com.accounting.firm.contract.entity.Contract::getId,
                com.accounting.firm.contract.entity.Contract::getContractNo));
        var invoiceRows = new java.util.ArrayList<com.accounting.firm.client.dto.ClientStatementVO.InvoiceRow>();
        BigDecimal issuedTotal = BigDecimal.ZERO;
        for (var inv : invoices) {
            var row = new com.accounting.firm.client.dto.ClientStatementVO.InvoiceRow();
            row.setInvoiceNo(inv.getInvoiceNo() == null ? "(待补号)" : inv.getInvoiceNo());
            row.setContractNo(contractNoMap.get(inv.getContractId()));
            row.setInvoiceDate(inv.getInvoiceDate());
            row.setAmount(inv.getAmount());
            row.setAmountExTax(inv.getAmountExTax());
            row.setTaxAmount(inv.getTaxAmount());
            row.setStatusLabel(inv.getStatus() == 1 ? "已开票" : "待开票");
            invoiceRows.add(row);
            if (inv.getStatus() == 1) {
                issuedTotal = issuedTotal.add(inv.getAmount() == null ? BigDecimal.ZERO : inv.getAmount());
            }
        }
        vo.setInvoices(invoiceRows);
        vo.setInvoiceIssuedTotal(issuedTotal);

        // 回款
        List<Long> contractIds = contracts.stream().map(
                com.accounting.firm.contract.entity.Contract::getId).toList();
        var paymentRows = new java.util.ArrayList<com.accounting.firm.client.dto.ClientStatementVO.PaymentRow>();
        BigDecimal collectedTotal = BigDecimal.ZERO;
        if (!contractIds.isEmpty()) {
            List<com.accounting.firm.collection.entity.ContractPayment> payments = paymentMapper.selectList(
                    new LambdaQueryWrapper<com.accounting.firm.collection.entity.ContractPayment>()
                            .in(com.accounting.firm.collection.entity.ContractPayment::getContractId, contractIds)
                            .orderByDesc(com.accounting.firm.collection.entity.ContractPayment::getPaymentDate));
            for (var pay : payments) {
                var row = new com.accounting.firm.client.dto.ClientStatementVO.PaymentRow();
                row.setPaymentDate(pay.getPaymentDate());
                row.setContractNo(contractNoMap.get(pay.getContractId()));
                row.setAmount(pay.getAmount());
                row.setPaymentMethod(pay.getPaymentMethod());
                row.setCreateTime(pay.getCreateTime());
                paymentRows.add(row);
                collectedTotal = collectedTotal.add(pay.getAmount() == null ? BigDecimal.ZERO : pay.getAmount());
            }
        }
        vo.setPayments(paymentRows);
        vo.setCollectedTotal(collectedTotal);
        vo.setOutstanding(issuedTotal.subtract(collectedTotal));
        return vo;
    }

    private String generateNo() {
        LocalDate today = LocalDate.now();
        String prefix = "KH" + "%1$tY%1$tm%1$td".formatted(today);
        Client max = lambdaQuery()
                .likeRight(Client::getClientNo, prefix)
                .orderByDesc(Client::getClientNo)
                .last("LIMIT 1")
                .one();
        String no = max == null ? null : max.getClientNo();
        int seq = 1;
        if (no != null && no.startsWith(prefix) && no.length() == prefix.length() + 4) {
            seq = Integer.parseInt(no.substring(prefix.length())) + 1;
        }
        return prefix + ("%0" + 4 + "d").formatted(seq);
    }

    private void copyFields(ClientRequest request, Client client) {
        client.setClientName(request.getClientName());
        client.setClientType(request.getClientType());
        client.setCreditCode(request.getCreditCode());
        client.setRegisteredCapital(request.getRegisteredCapital());
        client.setRegisteredAddress(request.getRegisteredAddress());
        client.setLegalRepresentative(request.getLegalRepresentative());
        client.setBusinessScope(request.getBusinessScope());
        client.setContactPerson(request.getContactPerson());
        client.setContactPhone(request.getContactPhone());
        client.setInvoiceTitle(request.getInvoiceTitle());
        client.setInvoiceTaxNo(request.getInvoiceTaxNo());
        client.setInvoiceBankName(request.getInvoiceBankName());
        client.setInvoiceBankAccount(request.getInvoiceBankAccount());
        client.setInvoiceAddress(request.getInvoiceAddress());
        client.setInvoicePhone(request.getInvoicePhone());
        client.setRemark(request.getRemark());
    }
}
