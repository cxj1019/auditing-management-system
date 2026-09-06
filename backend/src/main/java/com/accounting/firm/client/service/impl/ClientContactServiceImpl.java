package com.accounting.firm.client.service.impl;

import com.accounting.firm.client.dto.ClientContactRequest;
import com.accounting.firm.client.entity.ClientContact;
import com.accounting.firm.client.entity.Client;
import com.accounting.firm.client.mapper.ClientContactMapper;
import com.accounting.firm.client.mapper.ClientMapper;
import com.accounting.firm.client.service.ClientContactService;
import com.accounting.firm.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 客户联系人服务实现
 */
@Service
@RequiredArgsConstructor
public class ClientContactServiceImpl extends ServiceImpl<ClientContactMapper, ClientContact>
        implements ClientContactService {

    private final ClientMapper clientMapper;

    @Override
    public List<ClientContact> listByClientId(Long clientId) {
        requireClient(clientId);
        return lambdaQuery()
                .eq(ClientContact::getClientId, clientId)
                .orderByAsc(ClientContact::getId)
                .list();
    }

    @Override
    public ClientContact addContact(Long clientId, ClientContactRequest request) {
        requireClient(clientId);
        ClientContact contact = new ClientContact();
        copyFields(request, contact);
        contact.setClientId(clientId);
        save(contact);
        return contact;
    }

    @Override
    public ClientContact updateContact(Long contactId, ClientContactRequest request) {
        ClientContact contact = requireContact(contactId);
        copyFields(request, contact);
        updateById(contact);
        return contact;
    }

    @Override
    public void deleteContact(Long clientId, Long contactId) {
        ClientContact contact = requireContact(contactId);
        if (!contact.getClientId().equals(clientId)) {
            throw new BusinessException("联系人不属于该客户");
        }
        removeById(contactId);
    }

    private void copyFields(ClientContactRequest request, ClientContact contact) {
        contact.setContactName(request.getContactName());
        contact.setPosition(request.getPosition());
        contact.setPhone(request.getPhone());
        contact.setEmail(request.getEmail());
        contact.setRemark(request.getRemark());
    }

    private void requireClient(Long clientId) {
        if (clientId == null || clientMapper.selectById(clientId) == null) {
            throw new BusinessException("客户不存在");
        }
    }

    private ClientContact requireContact(Long contactId) {
        ClientContact contact = getById(contactId);
        if (contact == null) {
            throw new BusinessException("联系人不存在");
        }
        return contact;
    }
}
