package com.porbe.app.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Verifica el contrato Java-Node sin abrir Chromium ni conectarse a WhatsApp. */
class WhatsAppWebClientTest {

    private MockRestServiceServer server;
    private WhatsAppWebClient client;

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder()
                .baseUrl("http://whatsapp-web:3001")
                .defaultHeader("X-Porbe-Internal-Token", "token-prueba");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new WhatsAppWebClient(builder.build());
    }

    @Test
    void readsQrStatusFromTheInternalService() {
        server.expect(requestTo("http://whatsapp-web:3001/api/status"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Porbe-Internal-Token", "token-prueba"))
                .andRespond(withSuccess("""
                        {"state":"QR_REQUIRED","ready":false,"qrDataUrl":"data:image/png;base64,abc",
                         "accountLabel":null,"message":"Escanea el código QR.","updatedAt":"2026-09-02T12:00:00Z"}
                        """, MediaType.APPLICATION_JSON));

        var status = client.status();

        assertThat(status.state()).isEqualTo("QR_REQUIRED");
        assertThat(status.qrDataUrl()).startsWith("data:image/png;base64,");
        server.verify();
    }

    @Test
    void sendsThePngAsBase64() {
        server.expect(requestTo("http://whatsapp-web:3001/api/messages/report"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Porbe-Internal-Token", "token-prueba"))
                .andExpect(content().json("""
                        {"to":"573001234567","caption":"Informe de prueba","filename":"informe.png",
                         "mediaBase64":"iVBORw=="}
                        """))
                .andRespond(withSuccess("""
                        {"status":"SENT","messageId":"message-1","sentAt":"2026-09-02T12:01:00Z"}
                        """, MediaType.APPLICATION_JSON));

        var response = client.send(
                "573001234567",
                "Informe de prueba",
                "informe.png",
                "\u0089PNG".getBytes(StandardCharsets.ISO_8859_1));

        assertThat(response.status()).isEqualTo("SENT");
        assertThat(response.messageId()).isEqualTo("message-1");
        server.verify();
    }
}
