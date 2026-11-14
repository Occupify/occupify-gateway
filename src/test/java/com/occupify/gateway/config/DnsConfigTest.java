package com.occupify.gateway.config;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.test.context.ActiveProfiles;
import reactor.netty.http.client.HttpClient;

@SpringBootTest
@ActiveProfiles("test")
class DnsConfigTest {

    @Autowired
    private HttpClientCustomizer dnsCacheHttpClientCustomizer;

    @Test
    void shouldRegisterDnsCacheHttpClientCustomizer() {
        assertNotNull(dnsCacheHttpClientCustomizer, "DnsCacheHttpClientCustomizer bean must be registered");
    }

    @Test
    void shouldCustomizeHttpClientWithDisabledDnsCache() {
        HttpClient client = HttpClient.create();
        HttpClient customized = dnsCacheHttpClientCustomizer.customize(client);
        assertNotNull(customized, "Customized HttpClient must not be null");
    }
}
