package com.porbe.app.report;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.WaitUntilState;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Convierte el HTML del informe a PNG y PDF mediante Chromium sin interfaz gráfica. */
@Service
public class PortfolioReportArtifactRenderer {

    static final int REPORT_WIDTH = 1080;
    static final int INITIAL_REPORT_HEIGHT = 2280;
    private static final List<Path> LOCAL_BROWSER_CANDIDATES = List.of(
            Path.of("/snap/bin/chromium"),
            Path.of("/usr/bin/chromium"),
            Path.of("/usr/bin/chromium-browser"),
            Path.of("/usr/bin/google-chrome"),
            Path.of("/usr/bin/google-chrome-stable"));

    private final PortfolioReportHtmlRenderer htmlRenderer;
    private final String configuredBrowserExecutable;

    public PortfolioReportArtifactRenderer(
            PortfolioReportHtmlRenderer htmlRenderer,
            @Value("${app.reports.browser-executable:}") String configuredBrowserExecutable) {
        this.htmlRenderer = htmlRenderer;
        this.configuredBrowserExecutable = configuredBrowserExecutable;
    }

    /**
     * Abre el documento procesado en una página aislada y obtiene ambos formatos del
     * mismo árbol HTML. El alto del PDF se adapta al contenido real de la plantilla.
     */
    public PortfolioReportArtifacts render(PortfolioReportData data) {
        return renderHtml(htmlRenderer.render(data));
    }

    /** Genera una vista previa con un comentario opcional sin guardarla en el historial. */
    public PortfolioReportArtifacts render(
            PortfolioReportData data,
            PortfolioReportTemplateModel.Note note) {
        return renderHtml(htmlRenderer.render(data, note));
    }

    private PortfolioReportArtifacts renderHtml(String html) {
        try (var playwright = Playwright.create();
             var browser = playwright.chromium().launch(launchOptions())) {
            var page = browser.newPage(new Browser.NewPageOptions()
                    .setViewportSize(REPORT_WIDTH, INITIAL_REPORT_HEIGHT)
                    .setDeviceScaleFactor(1));
            page.setContent(html, new Page.SetContentOptions().setWaitUntil(WaitUntilState.LOAD));
            page.evaluate("() => document.fonts.ready");

            var report = page.locator("#portfolio-report");
            report.waitFor(new Locator.WaitForOptions().setState(
                    com.microsoft.playwright.options.WaitForSelectorState.VISIBLE));
            var bounds = report.boundingBox();
            if (bounds == null || bounds.height <= 0) {
                throw new IllegalStateException("La plantilla HTML del informe no produjo contenido visible.");
            }

            var image = report.screenshot(new Locator.ScreenshotOptions());
            page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
            var pdf = page.pdf(new Page.PdfOptions()
                    .setFormat("A4")
                    .setPreferCSSPageSize(true)
                    .setPrintBackground(true)
                    .setTagged(true));
            return new PortfolioReportArtifacts(image, pdf);
        } catch (PlaywrightException exception) {
            throw new IllegalStateException(
                    "No fue posible abrir Chromium para renderizar el informe HTML.", exception);
        }
    }

    /** Usa una ruta explícita en desarrollo y el Chromium incluido por Playwright en Docker. */
    private BrowserType.LaunchOptions launchOptions() {
        var options = new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setChromiumSandbox(false);
        var executable = browserExecutable();
        if (executable != null) {
            options.setExecutablePath(executable);
        }
        return options;
    }

    private Path browserExecutable() {
        if (configuredBrowserExecutable != null && !configuredBrowserExecutable.isBlank()) {
            var configured = Path.of(configuredBrowserExecutable.trim());
            if (!Files.isExecutable(configured)) {
                throw new IllegalStateException(
                        "El ejecutable configurado para renderizar informes no existe o no se puede ejecutar: "
                                + configured);
            }
            return configured;
        }
        if (System.getenv("PLAYWRIGHT_BROWSERS_PATH") != null) {
            return null;
        }
        return LOCAL_BROWSER_CANDIDATES.stream().filter(Files::isExecutable).findFirst().orElse(null);
    }
}
