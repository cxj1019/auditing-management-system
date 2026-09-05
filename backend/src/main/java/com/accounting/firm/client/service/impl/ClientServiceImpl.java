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

import java.time.LocalDate;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientServiceImpl extends ServiceImpl<ClientMapper, Client> implements ClientService {

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
