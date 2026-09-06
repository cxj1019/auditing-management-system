package com.accounting.firm.client.service;

import com.accounting.firm.client.dto.ClientRequest;
import com.accounting.firm.client.entity.Client;
import com.accounting.firm.common.api.PageResult;
import com.baomidou.mybatisplus.extension.service.IService;

public interface ClientService extends IService<Client> {

    /** 客户分页（客户全员可见，不再按部门隔离） */
    PageResult<Client> pageClients(long current, long size, String keyword, String clientType);

    /** 客户对账单：按客户归集合约/发票/回款 */
    com.accounting.firm.client.dto.ClientStatementVO statement(Long clientId);

    Long createClient(ClientRequest request);

    void updateClient(ClientRequest request);

    void deleteClient(Long id);
}
