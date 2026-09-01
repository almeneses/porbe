package com.porbe.app.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.stereotype.Service;

/** Produce PNG y PDF desde una sola imagen para mantener ambos formatos idénticos. */
@Service
public class PortfolioReportArtifactRenderer {

    private static final float PDF_WIDTH = 540f;
    private static final float PDF_HEIGHT = PDF_WIDTH * PortfolioReportImageRenderer.HEIGHT
            / PortfolioReportImageRenderer.WIDTH;

    private final PortfolioReportImageRenderer imageRenderer;

    public PortfolioReportArtifactRenderer(PortfolioReportImageRenderer imageRenderer) {
        this.imageRenderer = imageRenderer;
    }

    public PortfolioReportArtifacts render(PortfolioReportData data) {
        var image = imageRenderer.render(data);
        try (var imageOutput = new ByteArrayOutputStream();
             var pdfOutput = new ByteArrayOutputStream();
             var document = new PDDocument()) {
            ImageIO.write(image, "png", imageOutput);
            var page = new PDPage(new PDRectangle(PDF_WIDTH, PDF_HEIGHT));
            document.addPage(page);
            var pdfImage = LosslessFactory.createFromImage(document, image);
            try (var content = new PDPageContentStream(document, page)) {
                content.drawImage(pdfImage, 0, 0, PDF_WIDTH, PDF_HEIGHT);
            }
            document.save(pdfOutput);
            return new PortfolioReportArtifacts(imageOutput.toByteArray(), pdfOutput.toByteArray());
        } catch (IOException exception) {
            throw new UncheckedIOException("No fue posible renderizar el informe del portafolio.", exception);
        }
    }
}
