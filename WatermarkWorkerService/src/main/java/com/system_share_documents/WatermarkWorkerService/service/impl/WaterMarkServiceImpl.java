package com.system_share_documents.WatermarkWorkerService.service.impl;

import com.system_share_documents.WatermarkWorkerService.service.WaterMarkService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.isImage;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.isPdf;

@Service
public class WaterMarkServiceImpl implements WaterMarkService {

    @Override
    public byte[] addWatermark(byte[] input, String text) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(input)) {

            // 🧩 Case 1: PDF watermark
            if (isPdf(input)) {
                try (PDDocument document = PDDocument.load(inputStream);
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                    for (PDPage page : document.getPages()) {
                        var mediaBox = page.getMediaBox();
                        float pageWidth = mediaBox.getWidth();
                        float pageHeight = mediaBox.getHeight();

                        try (PDPageContentStream contentStream = new PDPageContentStream(
                                document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {

                            contentStream.beginText();
                            contentStream.setFont(PDType1Font.HELVETICA_OBLIQUE, 36); // mỏng hơn
                            contentStream.setNonStrokingColor(150, 150, 150); // nhạt hơn

                            // 🔄 Rotate around page center
                            float angle = (float) Math.toRadians(45);
                            float x = pageWidth / 4;
                            float y = pageHeight / 2;
                            contentStream.setTextMatrix(
                                    (float) Math.cos(angle), (float) Math.sin(angle),
                                    (float) -Math.sin(angle), (float) Math.cos(angle),
                                    x, y
                            );
                            contentStream.showText(text);
                            contentStream.endText();
                        }
                    }

                    document.save(outputStream);
                    return outputStream.toByteArray();
                }
            }

            // 🖼️ Case 2: Image watermark
            if (isImage(input)) {
                BufferedImage image = ImageIO.read(inputStream);
                int width = image.getWidth();
                int height = image.getHeight();

                Graphics2D g2d = image.createGraphics();
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Độ trong suốt watermark
                AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f);
                g2d.setComposite(alphaChannel);
                g2d.setColor(Color.GRAY);

                // Font tự scale theo chiều rộng ảnh
                int fontSize = Math.max(24, width / 15);
                g2d.setFont(new Font("Arial", Font.BOLD, fontSize));

                FontRenderContext frc = g2d.getFontRenderContext();
                double textWidth = g2d.getFont().getStringBounds(text, frc).getWidth();

                int centerX = (width - (int) textWidth) / 2;
                int centerY = height / 2;

                g2d.rotate(Math.toRadians(-30), width / 2.0, height / 2.0);
                g2d.drawString(text, centerX, centerY);
                g2d.dispose();

                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    String format = image.getColorModel().hasAlpha() ? "png" : "jpg";
                    ImageIO.write(image, format, outputStream);
                    return outputStream.toByteArray();
                }
            }

            // 🔙 Default fallback: return original
            return input;

        } catch (Exception e) {
            e.printStackTrace();
            return input;
        }
    }
}
