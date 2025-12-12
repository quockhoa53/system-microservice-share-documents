package com.system_share_documents.WatermarkWorkerService.service.impl;

import com.system_share_documents.WatermarkWorkerService.exception.AppException;
import com.system_share_documents.WatermarkWorkerService.exception.errorcode.BusinessError;
import com.system_share_documents.WatermarkWorkerService.service.WaterMarkService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.isImage;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.isPdf;

@Service
public class WaterMarkServiceImpl implements WaterMarkService {
    private static final Logger log = LoggerFactory.getLogger(WaterMarkServiceImpl.class);

    @Override
    public byte[] addWatermark(byte[] input, String text) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(input)) {

            // PDF
            if (isPdf(input)) {
                try (PDDocument document = PDDocument.load(inputStream);
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                    for (PDPage page : document.getPages()) {
                        var mediaBox = page.getMediaBox();
                        float w = mediaBox.getWidth();
                        float h = mediaBox.getHeight();

                        try (PDPageContentStream cs = new PDPageContentStream(
                                document, page,
                                PDPageContentStream.AppendMode.APPEND,
                                true, true
                        )) {
                            cs.setFont(PDType1Font.HELVETICA_BOLD_OBLIQUE, 40);
                            cs.setNonStrokingColor(180, 180, 180); // màu xám nhẹ

                            float angle = (float) Math.toRadians(45);

                            // khoảng cách giữa các watermark
                            float stepX = 300;
                            float stepY = 250;

                            for (float x = -w; x < w * 2; x += stepX) {
                                for (float y = -h; y < h * 2; y += stepY) {

                                    cs.beginText();
                                    cs.setTextMatrix(
                                            (float) Math.cos(angle), (float) Math.sin(angle),
                                            (float) -Math.sin(angle), (float) Math.cos(angle),
                                            x, y
                                    );
                                    cs.showText(text);
                                    cs.endText();
                                }
                            }
                        }
                    }

                    document.save(outputStream);
                    return outputStream.toByteArray();
                }
            }

            // IMAGE
            if (isImage(input)) {
                BufferedImage image = ImageIO.read(inputStream);
                int width = image.getWidth();
                int height = image.getHeight();

                Graphics2D g2d = image.createGraphics();

                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Đậm vừa phải
                AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f);
                g2d.setComposite(alphaChannel);

                g2d.setColor(Color.GRAY);

                int fontSize = Math.max(32, width / 18);
                Font font = new Font("Arial", Font.BOLD, fontSize);
                g2d.setFont(font);

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getHeight();

                double angle = Math.toRadians(-35);
                g2d.rotate(angle, width / 2.0, height / 2.0);

                int stepX = (int) (textWidth * 1.8);
                int stepY = (int) (textHeight * 4);

                for (int x = -width; x < width * 2; x += stepX) {
                    for (int y = -height; y < height * 2; y += stepY) {
                        g2d.drawString(text, x, y);
                    }
                }

                g2d.dispose();
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    String format = image.getColorModel().hasAlpha() ? "png" : "jpg";
                    ImageIO.write(image, format, outputStream);
                    return outputStream.toByteArray();
                }
            }

            // fallback
            return input;

        } catch (Exception e) {
            log.error("Watermark processing failed. Text: {}. Error: {}", text, e.getMessage());
            throw new AppException(BusinessError.FAILED_WATERMARK);
        }
    }
}
