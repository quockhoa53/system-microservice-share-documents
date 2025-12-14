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
                            // Font size tự động dựa trên kích thước trang
                            float baseFontSize = Math.min(w, h) / 25f;
                            float fontSize = Math.max(24f, Math.min(48f, baseFontSize));
                            cs.setFont(PDType1Font.HELVETICA_BOLD_OBLIQUE, fontSize);

                            // Màu xám nhẹ với độ trong suốt tốt hơn (lighter gray)
                            cs.setNonStrokingColor(200, 200, 200);

                            float angle = (float) Math.toRadians(45);

                            // Khoảng cách giữa các watermark tự động dựa trên kích thước trang
                            float stepX = Math.max(250f, w * 0.4f);
                            float stepY = Math.max(200f, h * 0.35f);

                            // Điều chỉnh vị trí bắt đầu để watermark được căn giữa tốt hơn
                            float startX = -w * 0.2f;
                            float startY = -h * 0.2f;

                            for (float x = startX; x < w * 1.5f; x += stepX) {
                                for (float y = startY; y < h * 1.5f; y += stepY) {
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

                // Cải thiện chất lượng rendering
                g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                // Độ trong suốt vừa phải - không quá đậm, không quá nhạt
                AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f);
                g2d.setComposite(alphaChannel);

                // Màu xám nhẹ hơn
                g2d.setColor(new Color(180, 180, 180));

                // Font size tự động dựa trên kích thước ảnh
                int fontSize = Math.max(28, Math.min(72, Math.max(width, height) / 20));
                Font font = new Font("Arial", Font.BOLD, fontSize);
                g2d.setFont(font);

                FontMetrics fm = g2d.getFontMetrics();
                int textWidth = fm.stringWidth(text);
                int textHeight = fm.getHeight();

                // Góc xoay watermark
                double angle = Math.toRadians(-35);

                // Khoảng cách giữa các watermark
                int stepX = (int) (textWidth * 2.2);
                int stepY = (int) (textHeight * 3.5);

                // Điều chỉnh vị trí bắt đầu để watermark được phân bố đều
                int offsetX = (int) (-width * 0.1);
                int offsetY = (int) (-height * 0.1);

                // Vẽ watermark với rotation riêng cho mỗi vị trí
                for (int x = offsetX; x < width * 1.3; x += stepX) {
                    for (int y = offsetY; y < height * 1.3; y += stepY) {
                        // Lưu transform hiện tại
                        java.awt.geom.AffineTransform originalTransform = g2d.getTransform();

                        // Tính toán tâm của watermark text
                        double centerX = x + textWidth / 2.0;
                        double centerY = y + textHeight / 2.0;

                        // Xoay quanh tâm của text
                        g2d.rotate(angle, centerX, centerY);
                        g2d.drawString(text, x, y);

                        // Khôi phục transform
                        g2d.setTransform(originalTransform);
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
