package com.system_share_documents.AppCommonService.config.vault;

import com.system_share_documents.AppCommonService.config.properties.VaultProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;

@Configuration
public class VaultConfig {

    @Bean
    public VaultTemplate vaultTemplate(VaultProperties vaultProperties) {
        VaultEndpoint endpoint = VaultEndpoint.create(vaultProperties.getHost(), vaultProperties.getPort());
        endpoint.setScheme(vaultProperties.getScheme());
        TokenAuthentication tokenAuth = new TokenAuthentication(vaultProperties.getToken());
        return new VaultTemplate(endpoint, tokenAuth);
    }
}
