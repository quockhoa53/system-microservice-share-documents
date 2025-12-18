package com.system_share_documents.WatermarkWorkerService.service.impl;

import com.system_share_documents.WatermarkWorkerService.exception.AppException;
import com.system_share_documents.WatermarkWorkerService.exception.errorcode.BusinessError;
import com.system_share_documents.WatermarkWorkerService.service.WaterMarkService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.isImage;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.isPdf;
import static com.system_share_documents.WatermarkWorkerService.utils.TypeFileUtils.detectImageFormat;

@Service
public class WaterMarkServiceImpl implements WaterMarkService {
    private static final Logger log = LoggerFactory.getLogger(WaterMarkServiceImpl.class);
    private static final long MAX_OFFICE_FILE_SIZE = 100 * 1024 * 1024;

    @Override
    public byte[] addWatermark(byte[] input, String text) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(input)) {
            if (isPdf(input)) {
                log.info("Type file is PDF");
                return addPdfWatermark(inputStream, text);
            }

            if (isImage(input)) {
                log.info("Type file is image");
                return addImageWatermark(input, text);
            }

            if (isTextFile(input)) {
                log.info("Type file is text");
                return addTextFileWatermark(input, text);
            }

            if (isOfficeFile(input)) {
                log.info("Type file is office");
                return addOfficeFileWatermark(input, text);
            }

            if (isArchiveFile(input)) {
                log.debug("Archive file detected, keeping original");
                return input;
            }

            log.warn("Unknown file type, keeping original without watermark");
            return input;

        } catch (Exception e) {
            log.error("Watermark processing failed. Error: {}", e.getMessage(), e);
            throw new AppException(BusinessError.FAILED_WATERMARK);
        }
    }

    private byte[] addPdfWatermark(ByteArrayInputStream inputStream, String text) {
        try (PDDocument document = PDDocument.load(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            for (PDPage page : document.getPages()) {
                var mediaBox = page.getMediaBox();
                float w = mediaBox.getWidth();
                float h = mediaBox.getHeight();

                try (PDPageContentStream cs = new PDPageContentStream(
                        document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                    float baseFontSize = Math.min(w, h) / 25f;
                    float fontSize = Math.max(24f, Math.min(48f, baseFontSize));
                    cs.setFont(PDType1Font.HELVETICA_BOLD_OBLIQUE, fontSize);
                    cs.setNonStrokingColor(200, 200, 200);

                    float angle = (float) Math.toRadians(45);
                    float stepX = Math.max(250f, w * 0.4f);
                    float stepY = Math.max(200f, h * 0.35f);
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
        } catch (Exception e) {
            log.error("Failed to watermark PDF. Error: {}", e.getMessage(), e);
            throw new AppException(BusinessError.FAILED_WATERMARK, "PDF watermarking failed: " + e.getMessage());
        }
    }

    private byte[] addImageWatermark(byte[] input, String text) {
        String detectedFormat = detectImageFormat(input);
        BufferedImage image = null;
        String outputFormat = "png";

        try {
            ByteArrayInputStream imageStream = new ByteArrayInputStream(input);
            image = ImageIO.read(imageStream);

            if (image == null) {
                throw new Exception("Cannot read image file");
            }

            if (detectedFormat != null) {
                switch (detectedFormat.toLowerCase()) {
                    case "jpg":
                    case "jpeg":
                        outputFormat = "jpg";
                        if (image.getColorModel().hasAlpha()) {
                            BufferedImage rgbImage = new BufferedImage(
                                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
                            Graphics2D g = rgbImage.createGraphics();
                            g.setColor(Color.WHITE);
                            g.fillRect(0, 0, rgbImage.getWidth(), rgbImage.getHeight());
                            g.drawImage(image, 0, 0, null);
                            g.dispose();
                            image = rgbImage;
                        }
                        break;
                    case "png":
                        outputFormat = "png";
                        break;
                    case "gif":
                    case "tiff":
                    case "tif":
                    case "webp":
                    case "ico":
                        outputFormat = "png";
                        break;
                    case "bmp":
                        outputFormat = image.getColorModel().hasAlpha() ? "png" : "bmp";
                        break;
                    default:
                        outputFormat = image.getColorModel().hasAlpha() ? "png" : "jpg";
                }
            } else {
                outputFormat = image.getColorModel().hasAlpha() ? "png" : "jpg";
            }
        } catch (Exception e) {
            log.warn("Failed to read image, trying fallback. Error: {}", e.getMessage());
            try {
                ByteArrayInputStream fallbackStream = new ByteArrayInputStream(input);
                image = ImageIO.read(fallbackStream);
                if (image == null) {
                    throw new Exception("Cannot read image with any method");
                }
                outputFormat = "png";
            } catch (Exception e2) {
                log.error("Failed to read image file. Error: {}", e2.getMessage());
                throw new AppException(BusinessError.FAILED_WATERMARK, "Cannot read image file: " + e2.getMessage());
            }
        }

        int width = image.getWidth();
        int height = image.getHeight();

        Graphics2D g2d = image.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        AlphaComposite alphaChannel = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.15f);
        g2d.setComposite(alphaChannel);
        g2d.setColor(new Color(180, 180, 180));

        int fontSize = Math.max(28, Math.min(72, Math.max(width, height) / 20));
        Font font = new Font("Arial", Font.BOLD, fontSize);
        g2d.setFont(font);

        FontMetrics fm = g2d.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();

        double angle = Math.toRadians(-35);
        int stepX = (int) (textWidth * 2.2);
        int stepY = (int) (textHeight * 3.5);
        int offsetX = (int) (-width * 0.1);
        int offsetY = (int) (-height * 0.1);

        for (int x = offsetX; x < width * 1.3; x += stepX) {
            for (int y = offsetY; y < height * 1.3; y += stepY) {
                java.awt.geom.AffineTransform originalTransform = g2d.getTransform();
                double centerX = x + textWidth / 2.0;
                double centerY = y + textHeight / 2.0;
                g2d.rotate(angle, centerX, centerY);
                g2d.drawString(text, x, y);
                g2d.setTransform(originalTransform);
            }
        }

        g2d.dispose();

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            String[] writerFormats = ImageIO.getWriterFormatNames();
            boolean formatSupported = false;
            for (String format : writerFormats) {
                if (format.equalsIgnoreCase(outputFormat)) {
                    formatSupported = true;
                    break;
                }
            }

            if (!formatSupported) {
                log.warn("Format {} not supported, converting to PNG", outputFormat);
                outputFormat = "png";
            }

            ImageIO.write(image, outputFormat, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to write watermarked image. Error: {}", e.getMessage());
            throw new AppException(BusinessError.FAILED_WATERMARK, "Cannot write watermarked image: " + e.getMessage());
        }
    }

    private boolean isTextFile(byte[] data) {
        if (data == null || data.length == 0) {
            return false;
        }

        int checkLength = Math.min(512, data.length);
        int textBytes = 0;
        int nullBytes = 0;

        for (int i = 0; i < checkLength; i++) {
            byte b = data[i];
            if (b == 0) {
                nullBytes++;
                if (nullBytes > 5) {
                    return false;
                }
            }
            if ((b >= 0x20 && b <= 0x7E) || b == 0x09 || b == 0x0A || b == 0x0D) {
                textBytes++;
            }
        }

        return nullBytes == 0 && textBytes > (checkLength * 0.95);
    }

    private byte[] addTextFileWatermark(byte[] input, String watermarkText) {
        try {
            String content = new String(input, StandardCharsets.UTF_8);
            String copyrightHeader = generateCopyrightHeader(watermarkText);

            if (content.startsWith("/*") || content.startsWith("<!--") || content.startsWith("#")) {
                int firstNewline = content.indexOf('\n');
                if (firstNewline > 0) {
                    String header = content.substring(0, firstNewline);
                    String rest = content.substring(firstNewline);
                    content = header + "\n" + copyrightHeader + rest;
                } else {
                    content = copyrightHeader + "\n" + content;
                }
            } else {
                content = copyrightHeader + "\n" + content;
            }

            return content.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to add text watermark with UTF-8, trying ISO-8859-1. Error: {}", e.getMessage());
            try {
                String content = new String(input, StandardCharsets.ISO_8859_1);
                String copyrightHeader = generateCopyrightHeader(watermarkText);
                content = copyrightHeader + "\n" + content;
                return content.getBytes(StandardCharsets.ISO_8859_1);
            } catch (Exception e2) {
                log.error("Failed to add text watermark. Error: {}", e2.getMessage());
                return input;
            }
        }
    }

    private String generateCopyrightHeader(String watermarkText) {
        String[] parts = watermarkText.split("\\|");
        String userId = parts.length > 0 ? parts[0].trim() : "Unknown";
        String timestamp = parts.length > 1 ? parts[1].trim() : Instant.now().toString();

        try {
            Instant instant = Instant.parse(timestamp);
            timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .format(instant.atZone(java.time.ZoneId.systemDefault()));
        } catch (Exception e) {
            // Keep original timestamp
        }

        return String.format(
                "/*\n" +
                        " * Copyright (c) %s\n" +
                        " * Owner: %s\n" +
                        " * This file is protected by copyright. Unauthorized copying or distribution is prohibited.\n" +
                        " * Watermarked on: %s\n" +
                        " */",
                java.time.Year.now(), userId, timestamp
        );
    }

    private boolean isOfficeFile(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }

        byte[] zipMagic = {0x50, 0x4B, 0x03, 0x04};
        if (startsWith(data, zipMagic)) {
            int checkLength = Math.min(5000, data.length);
            String header = new String(data, 0, checkLength, StandardCharsets.UTF_8);

            if (header.contains("word/") || header.contains("xl/") || header.contains("ppt/")) {
                return true;
            }

            return tryOpenAsOfficeFile(data);
        }

        byte[] docMagic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
        return startsWith(data, docMagic);
    }

    private boolean tryOpenAsOfficeFile(byte[] data) {
        OPCPackage opcPackage = null;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            opcPackage = OPCPackage.open(bis);

            try (XWPFDocument doc = new XWPFDocument(opcPackage)) {
                return true;
            } catch (Exception e) {
                opcPackage.close();
                opcPackage = null;

                ByteArrayInputStream bis2 = new ByteArrayInputStream(data);
                opcPackage = OPCPackage.open(bis2);
                try (XSSFWorkbook wb = new XSSFWorkbook(opcPackage)) {
                    return true;
                } catch (Exception e2) {
                    opcPackage.close();
                    opcPackage = null;

                    ByteArrayInputStream bis3 = new ByteArrayInputStream(data);
                    try (XMLSlideShow ppt = new XMLSlideShow(bis3)) {
                        return true;
                    } catch (Exception e3) {
                        return false;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            if (opcPackage != null) {
                try {
                    opcPackage.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private boolean isArchiveFile(byte[] data) {
        if (data == null || data.length < 4) {
            return false;
        }

        byte[] zipMagic = {0x50, 0x4B, 0x03, 0x04};
        if (startsWith(data, zipMagic)) {
            int checkLength = Math.min(5000, data.length);
            String header = new String(data, 0, checkLength, StandardCharsets.UTF_8);
            if (!header.contains("word/") && !header.contains("xl/") && !header.contains("ppt/")) {
                if (!tryOpenAsOfficeFile(data)) {
                    return true;
                }
            }
            return false;
        }

        byte[] rarMagic = {0x52, 0x61, 0x72, 0x21};
        if (startsWith(data, rarMagic)) {
            return true;
        }

        byte[] gzipMagic = {(byte) 0x1F, (byte) 0x8B};
        return startsWith(data, gzipMagic);
    }

    private boolean startsWith(byte[] data, byte[] pattern) {
        if (data == null || pattern == null || data.length < pattern.length) {
            return false;
        }
        for (int i = 0; i < pattern.length; i++) {
            if (data[i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private byte[] addOfficeFileWatermark(byte[] input, String watermarkText) throws Exception {
        if (input == null || input.length < 4) {
            throw new IllegalArgumentException("Invalid Office file data");
        }

        if (input.length > MAX_OFFICE_FILE_SIZE) {
            log.warn("Office file size ({}) exceeds maximum ({}), keeping original", input.length, MAX_OFFICE_FILE_SIZE);
            return input;
        }

        String officeType = detectOfficeFileType(input);
        long startTime = System.currentTimeMillis();

        try {
            byte[] result;
            switch (officeType) {
                case "docx":
                    result = addWordWatermark(input, watermarkText);
                    break;
                case "xlsx":
                    result = addExcelWatermark(input, watermarkText);
                    break;
                case "pptx":
                    result = addPowerPointWatermark(input, watermarkText);
                    break;
                case "doc":
                case "xls":
                case "ppt":
                    log.warn("Legacy Office format ({}) not supported, keeping original", officeType);
                    return input;
                default:
                    log.warn("Unknown Office file type: {}, keeping original", officeType);
                    return input;
            }

            long duration = System.currentTimeMillis() - startTime;
            log.debug("Office file ({}) watermarked in {}ms, size: {} bytes", officeType, duration, input.length);
            return result;
        } catch (OutOfMemoryError e) {
            log.error("OutOfMemoryError while watermarking Office file (size: {})", input.length);
            throw new Exception("File too large to watermark: " + e.getMessage(), e);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Failed to watermark Office file ({}) after {}ms. Error: {}", officeType, duration, e.getMessage());
            throw e;
        }
    }

    private String detectOfficeFileType(byte[] data) {
        if (data.length < 30) {
            return "unknown";
        }

        byte[] zipMagic = {0x50, 0x4B, 0x03, 0x04};
        if (startsWith(data, zipMagic)) {
            int checkLength = Math.min(5000, data.length);
            String header = new String(data, 0, checkLength, StandardCharsets.UTF_8);

            if (header.contains("word/")) {
                return "docx";
            } else if (header.contains("xl/")) {
                return "xlsx";
            } else if (header.contains("ppt/")) {
                return "pptx";
            }

            return detectOfficeFileTypeByOpening(data);
        }

        byte[] docMagic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
        if (startsWith(data, docMagic)) {
            return "legacy";
        }

        return "unknown";
    }

    private String detectOfficeFileTypeByOpening(byte[] data) {
        OPCPackage opcPackage = null;
        try {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            opcPackage = OPCPackage.open(bis);

            try (XWPFDocument doc = new XWPFDocument(opcPackage)) {
                return "docx";
            } catch (Exception e) {
                opcPackage.close();
                opcPackage = null;

                ByteArrayInputStream bis2 = new ByteArrayInputStream(data);
                opcPackage = OPCPackage.open(bis2);
                try (XSSFWorkbook wb = new XSSFWorkbook(opcPackage)) {
                    return "xlsx";
                } catch (Exception e2) {
                    opcPackage.close();
                    opcPackage = null;

                    ByteArrayInputStream bis3 = new ByteArrayInputStream(data);
                    try (XMLSlideShow ppt = new XMLSlideShow(bis3)) {
                        return "pptx";
                    } catch (Exception e3) {
                        return "unknown";
                    }
                }
            }
        } catch (Exception e) {
            return "unknown";
        } finally {
            if (opcPackage != null) {
                try {
                    opcPackage.close();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }

    private byte[] addWordWatermark(byte[] input, String watermarkText) throws Exception {
        OPCPackage opcPackage = null;
        XWPFDocument document = null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(input);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            opcPackage = OPCPackage.open(bis);
            document = new XWPFDocument(opcPackage);

            String[] parts = watermarkText.split("\\|");
            String userId = parts.length > 0 ? parts[0].trim() : "Unknown";
            String timestamp = parts.length > 1 ? parts[1].trim() : Instant.now().toString();

            try {
                Instant instant = Instant.parse(timestamp);
                timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(instant.atZone(java.time.ZoneId.systemDefault()));
            } catch (Exception e) {
                // Keep original timestamp
            }

            String watermark = String.format("%s | %s", userId, timestamp);

            for (XWPFHeader header : document.getHeaderList()) {
                addWordHeaderWatermark(header, watermark);
            }

            for (XWPFFooter footer : document.getFooterList()) {
                addWordFooterWatermark(footer, watermark);
            }

            if (document.getHeaderList().isEmpty() && document.getFooterList().isEmpty()) {
                XWPFHeader header = document.createHeader(HeaderFooterType.DEFAULT);
                addWordHeaderWatermark(header, watermark);
            }

            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to watermark Word document. Error: {}", e.getMessage(), e);
            throw new Exception("Word watermarking failed: " + e.getMessage(), e);
        } finally {
            if (document != null) {
                try {
                    document.close();
                } catch (Exception e) {
                    log.warn("Error closing Word document: {}", e.getMessage());
                }
            }
            if (opcPackage != null) {
                try {
                    opcPackage.close();
                } catch (Exception e) {
                    log.warn("Error closing OPCPackage: {}", e.getMessage());
                }
            }
        }
    }

    private void addWordHeaderWatermark(XWPFHeader header, String watermarkText) {
        XWPFParagraph paragraph = header.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setText(watermarkText);
        run.setColor("808080");
        run.setFontSize(10);
        run.setFontFamily("Arial");
        run.setItalic(true);
    }

    private void addWordFooterWatermark(XWPFFooter footer, String watermarkText) {
        XWPFParagraph paragraph = footer.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setText(watermarkText);
        run.setColor("808080");
        run.setFontSize(10);
        run.setFontFamily("Arial");
        run.setItalic(true);
    }

    private byte[] addExcelWatermark(byte[] input, String watermarkText) throws Exception {
        OPCPackage opcPackage = null;
        XSSFWorkbook workbook = null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(input);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            opcPackage = OPCPackage.open(bis);
            workbook = new XSSFWorkbook(opcPackage);

            String[] parts = watermarkText.split("\\|");
            String userId = parts.length > 0 ? parts[0].trim() : "Unknown";
            String timestamp = parts.length > 1 ? parts[1].trim() : Instant.now().toString();

            try {
                Instant instant = Instant.parse(timestamp);
                timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(instant.atZone(java.time.ZoneId.systemDefault()));
            } catch (Exception e) {
                // Keep original timestamp
            }

            String watermark = String.format("%s | %s", userId, timestamp);

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet != null) {
                    addExcelSheetWatermark(sheet, watermark);
                }
            }

            workbook.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to watermark Excel workbook. Error: {}", e.getMessage(), e);
            throw new Exception("Excel watermarking failed: " + e.getMessage(), e);
        } finally {
            if (workbook != null) {
                try {
                    workbook.close();
                } catch (Exception e) {
                    log.warn("Error closing Excel workbook: {}", e.getMessage());
                }
            }
            if (opcPackage != null) {
                try {
                    opcPackage.close();
                } catch (Exception e) {
                    log.warn("Error closing OPCPackage: {}", e.getMessage());
                }
            }
        }
    }

    private void addExcelSheetWatermark(Sheet sheet, String watermarkText) {
        Header header = sheet.getHeader();
        Footer footer = sheet.getFooter();
        header.setCenter("&C" + watermarkText);
        footer.setCenter("&C" + watermarkText);
    }

    private byte[] addPowerPointWatermark(byte[] input, String watermarkText) throws Exception {
        XMLSlideShow slideShow = null;
        try (ByteArrayInputStream bis = new ByteArrayInputStream(input);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

            slideShow = new XMLSlideShow(bis);

            String[] parts = watermarkText.split("\\|");
            String userId = parts.length > 0 ? parts[0].trim() : "Unknown";
            String timestamp = parts.length > 1 ? parts[1].trim() : Instant.now().toString();

            try {
                Instant instant = Instant.parse(timestamp);
                timestamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .format(instant.atZone(java.time.ZoneId.systemDefault()));
            } catch (Exception e) {
                // Keep original timestamp
            }

            String watermark = String.format("%s | %s", userId, timestamp);

            for (XSLFSlide slide : slideShow.getSlides()) {
                addPowerPointSlideWatermark(slide, watermark);
            }

            slideShow.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("Failed to watermark PowerPoint presentation. Error: {}", e.getMessage(), e);
            throw new Exception("PowerPoint watermarking failed: " + e.getMessage(), e);
        } finally {
            if (slideShow != null) {
                try {
                    slideShow.close();
                } catch (Exception e) {
                    log.warn("Error closing PowerPoint presentation: {}", e.getMessage());
                }
            }
        }
    }

    private void addPowerPointSlideWatermark(XSLFSlide slide, String watermarkText) {
        try {
            java.awt.Dimension pageSize = slide.getSlideShow().getPageSize();
            double width = pageSize.getWidth();
            double height = pageSize.getHeight();

            XSLFTextBox textBox = slide.createTextBox();
            textBox.setAnchor(new java.awt.geom.Rectangle2D.Double(
                    width * 0.1, height * 0.1, width * 0.8, height * 0.8));

            XSLFTextParagraph paragraph = textBox.addNewTextParagraph();
            XSLFTextRun run = paragraph.addNewTextRun();
            run.setText(watermarkText);
            run.setFontFamily("Arial");
            run.setFontSize(24.0);
            run.setFontColor(new java.awt.Color(128, 128, 128));
            run.setItalic(true);

            paragraph.setTextAlign(TextParagraph.TextAlign.CENTER);
            paragraph.setSpaceAfter(0.0);
            paragraph.setSpaceBefore(0.0);
            textBox.setRotation(45.0);

            XSLFSimpleShape shape = (XSLFSimpleShape) textBox;
            shape.setFillColor(null);
        } catch (Exception e) {
            log.warn("Failed to add watermark to PowerPoint slide. Error: {}", e.getMessage());
        }
    }
}
