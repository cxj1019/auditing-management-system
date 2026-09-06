package com.accounting.firm.client.service;

import com.accounting.firm.client.dto.ClientContactRequest;
import com.accounting.firm.client.entity.ClientContact;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 客户联系人服务
 */
public interface ClientContactService extends IService<ClientContact> {

    /** 按客户查询联系人清单 */
    List<ClientContact> listByClientId(Long clientId);

    /** 新增联系人 */
    ClientContact addContact(Long clientId, ClientContactRequest request);

    /** 编辑联系人 */
    ClientContact updateContact(Long contactId, ClientContactRequest request);

    /** 删除联系人（校验归属） */
    void deleteContact(Long clientId, Long contactId);
}
