package com.zju.offercatcher.infrastructure.file;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 简历文件解析服务。
 * 使用 Apache Tika 自动检测文档格式，提取纯文本内容。
 * 支持 PDF、DOCX、TXT 等格式。
 * <p>
 * 参考 interview-guide: DocumentParseService
 */
@Service
public class ResumeParseService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParseService.class);
    private static final int MAX_TEXT_LENGTH = 5 * 1024 * 1024;

    public String parse(MultipartFile file) {
        String fileName = file.getOriginalFilename();
        log.info("Parsing resume: {}", fileName);

        if (file.isEmpty() || file.getSize() == 0) {
            log.warn("Empty file: {}", fileName);
            return "";
        }

        try (InputStream in = file.getInputStream()) {
            return doParse(in);
        } catch (IOException | TikaException | SAXException e) {
            log.error("Failed to parse resume: {}", e.getMessage(), e);
            throw new RuntimeException("简历解析失败：" + e.getMessage(), e);
        }
    }

    private String doParse(InputStream inputStream) throws IOException, TikaException, SAXException {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(MAX_TEXT_LENGTH);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);
        context.set(EmbeddedDocumentExtractor.class, new EmbeddedDocumentExtractor() {
            public boolean shouldParseEmbedded(Metadata metadata) {
                return false;
            }

            public void parseEmbedded(InputStream stream, org.xml.sax.ContentHandler handler,
                                      Metadata metadata, boolean outputHtml) {
            }
        });

        PDFParserConfig pdfConfig = new PDFParserConfig();
        pdfConfig.setExtractInlineImages(false);
        pdfConfig.setSortByPosition(true);
        context.set(PDFParserConfig.class, pdfConfig);

        parser.parse(inputStream, handler, metadata, context);

        String text = handler.toString();
        String cleaned = cleanText(text);
        log.info("Resume parsed: {} chars", cleaned.length());
        return cleaned;
    }

    private String cleanText(String text) {
        return text.replaceAll("\\r\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("[ \\t]{3,}", "  ")
                .strip();
    }
}
