package com.porbe.app.portfolio;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.porbe.app.PorbeApplication;
import com.porbe.app.importer.ImportBatchRepository;
import com.porbe.app.market.MarketInstrumentRepository;
import com.porbe.app.market.MarketPriceDailyRepository;
import com.porbe.app.operation.OperationAuditRepository;
import com.porbe.app.operation.PortfolioOperationRepository;
import com.porbe.app.report.PortfolioReportRepository;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** Verifica aislamiento entre portafolios y administración de íconos de ticker. */
@SpringBootTest(classes = PorbeApplication.class)
@AutoConfigureMockMvc
class PortfolioManagementIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PortfolioReportRepository reportRepository;
    @Autowired private OperationAuditRepository auditRepository;
    @Autowired private PortfolioOperationRepository operationRepository;
    @Autowired private ImportBatchRepository importBatchRepository;
    @Autowired private MarketPriceDailyRepository priceRepository;
    @Autowired private MarketInstrumentRepository instrumentRepository;
    @Autowired private PortfolioRepository portfolioRepository;

    @BeforeEach
    void cleanDatabase() {
        reportRepository.deleteAll();
        auditRepository.deleteAll();
        operationRepository.deleteAll();
        importBatchRepository.deleteAll();
        priceRepository.deleteAll();
        instrumentRepository.deleteAll();
        portfolioRepository.deleteAll();
    }

    @Test
    void createsRenamesAndKeepsOperationsIsolatedByPortfolio() throws Exception {
        var colombiaId = createPortfolio("Acciones Colombia");
        var usaId = createPortfolio("Acciones USA");

        mockMvc.perform(post("/api/operations")
                        .queryParam("portfolioId", String.valueOf(usaId))
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-08-28","type":"depósito","ticker":null,"name":null,
                                 "quantity":null,"unitPrice":null,"commission":0,"totalAmount":500,"notes":"Aporte"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/portfolio/summary")
                        .queryParam("portfolioId", String.valueOf(colombiaId))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationCount").value(0))
                .andExpect(jsonPath("$.portfolioValue").value(0));
        mockMvc.perform(get("/api/portfolio/summary")
                        .queryParam("portfolioId", String.valueOf(usaId))
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operationCount").value(1))
                .andExpect(jsonPath("$.portfolioValue").value(500));

        mockMvc.perform(put("/api/portfolios/{id}", usaId)
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Crecimiento USA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Crecimiento USA"));
    }

    @Test
    void uploadsServesAndRemovesATickerIcon() throws Exception {
        var icon = new MockMultipartFile("file", "ecopetrol.png", "image/png", png());
        mockMvc.perform(multipart("/api/market-data/ECOPETROL.CL/icon")
                        .file(icon)
                        .with(request -> { request.setMethod("PUT"); return request; })
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasIcon").value(true));

        mockMvc.perform(get("/api/market-data/ECOPETROL.CL/icon")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));
    }

    private long createPortfolio(String name) throws Exception {
        var response = mockMvc.perform(post("/api/portfolios")
                        .with(user("admin").roles("ADMIN"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private byte[] png() throws Exception {
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(45, 137, 108));
        graphics.fillRect(0, 0, 32, 32);
        graphics.dispose();
        try (var output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
