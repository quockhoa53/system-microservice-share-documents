package com.system_share_documents.AppCommonService.service.impl;

import com.system_share_documents.AppCommonService.config.properties.VaultProperties;
import com.system_share_documents.AppCommonService.service.VaultTransitService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.vault.core.VaultTemplate;

import java.util.Map;

@Service
@Slf4j
public class VaultTransitServiceImpl implements VaultTransitService {

    @Autowired
    private VaultTemplate vaultTemplate;

    @Autowired
    private VaultProperties vaultProperties;

    @Override
    public String encrypt(String plaintextBase64) {
        try {
            Map<String, Object> body = Map.of("plaintext", plaintextBase64);
            var res = vaultTemplate.write("transit/encrypt/" +  vaultProperties.getTransitKey(), body);
            return (String) res.getData().get("ciphertext");
        } catch (Exception ex) {
            log.error("Vault encrypt failed", ex);
            throw ex;
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        try {
            Map<String, Object> body = Map.of("ciphertext", ciphertext);
            var res = vaultTemplate.write("transit/decrypt/" + vaultProperties.getTransitKey(), body);
            return (String) res.getData().get("plaintext");
        } catch (Exception ex) {
            log.error("Vault decrypt failed", ex);
            throw ex;
        }
    }
}
