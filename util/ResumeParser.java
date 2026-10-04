package com.careerintelligence.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Extracts plain text from an uploaded resume file (.txt, .docx or .pdf)
 * using only classes built into the JDK - no Apache POI / PDFBox / other
 * third-party document library dependency is added, consistent with how
 * ai.AIServiceImpl avoids extra dependencies for its LLM HTTP calls.
 *
 * <p><b>.txt</b> - read directly as UTF-8.
 *
 * <p><b>.docx</b> - a .docx file is a ZIP archive; {@code word/document.xml}
 * inside it holds the document body as WordprocessingML. This reads that
 * entry with {@link java.util.zip.ZipFile} (JDK built-in) and strips the
 * XML markup with a small regex-based converter that preserves paragraph
 * breaks and tabs.
 *
 * <p><b>.pdf</b> - a best-effort, dependency-free extractor: it scans the
 * raw PDF bytes for {@code stream ... endstream} objects using linear marker
 * searches (rather than a large backtracking regex), inflates any that
 * are {@code /FlateDecode} compressed with {@link java.util.zip.Inflater}
 * (also JDK built-in - PDF's Flate filter is plain zlib/deflate), and then
 * pulls the literal text out of the content-stream {@code Tj} / {@code TJ}
 * show-text operators. This works well for standard, programmatically
 * generated PDFs (exported from Word, Google Docs, LaTeX, etc.) but - like
 * any non-OCR extractor - cannot read scanned/image-only PDFs or PDFs that
 * remap character codes through a custom/embedded font encoding. Skill
 * extraction degrades gracefully in that case (fewer/no skills found); it
 * never crashes the calling flow.
 */
public final class ResumeParser {

    private static final Pattern PDF_SHOW_TEXT = Pattern.compile("\\(((?:\\\\.|[^()\\\\])*)\\)\\s*Tj");
    private static final Pattern PDF_SHOW_ARRAY = Pattern.compile("\\[((?:[^\\[\\]])*)\\]\\s*TJ");
    private static final Pattern PDF_ARRAY_TOKEN =
            Pattern.compile("\\(((?:\\\\.|[^()\\\\])*)\\)|(-?\\d+\\.?\\d*)");
    /** TJ-array numeric offsets more negative than this (1/1000 em units) are treated as an implicit word gap. */
    private static final double WORD_GAP_THRESHOLD = -100.0;
    private static final Pattern XML_TAG = Pattern.compile("<[^>]+>");

    private ResumeParser() {
    }

    public static class UnsupportedResumeFormatException extends IOException {
        public UnsupportedResumeFormatException(String message) {
            super(message);
        }
    }

    /** Extracts and returns the plain text content of a resume file based on its extension. */
    public static String extractText(Path filePath) throws IOException {
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".txt")) {
            return extractTxt(filePath);
        } else if (name.endsWith(".docx")) {
            return extractDocx(filePath);
        } else if (name.endsWith(".pdf")) {
            return extractPdf(filePath);
        }
        throw new UnsupportedResumeFormatException(
                "Unsupported resume file type '" + name + "'. Supported formats: .pdf, .docx, .txt");
    }

    // -----------------------------------------------------------------
    // .txt
    // -----------------------------------------------------------------

    private static String extractTxt(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    // -----------------------------------------------------------------
    // .docx
    // -----------------------------------------------------------------

    private static String extractDocx(Path path) throws IOException {
        try (ZipFile zip = new ZipFile(path.toFile())) {
            ZipEntry entry = zip.getEntry("word/document.xml");
            if (entry == null) {
                throw new IOException("'" + path.getFileName() + "' does not look like a valid .docx file "
                        + "(missing word/document.xml).");
            }
            try (InputStream is = zip.getInputStream(entry)) {
                String xml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                return stripDocxXml(xml);
            }
        }
    }

    private static String stripDocxXml(String xml) {
        String withBreaks = xml
                .replace("</w:p>", "\n")
                .replace("<w:tab/>", "\t")
                .replace("<w:tab />", "\t")
                .replace("<w:br/>", "\n")
                .replace("<w:br />", "\n");
        String noTags = XML_TAG.matcher(withBreaks).replaceAll("");
        return unescapeXml(noTags);
    }

    private static String unescapeXml(String s) {
        return s.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&apos;", "'");
    }

    // -----------------------------------------------------------------
    // .pdf (best-effort, no external library)
    // -----------------------------------------------------------------

    private static String extractPdf(Path path) throws IOException {
        byte[] data = Files.readAllBytes(path);
        // ISO-8859-1 is a lossless 1-byte-per-char round trip for arbitrary binary data,
        // which lets us use regex over the raw PDF bytes (structure is ASCII) while still
        // being able to convert matched stream bodies back to the exact original bytes.
        String raw = new String(data, StandardCharsets.ISO_8859_1);

        StringBuilder text = new StringBuilder();
        int streamsScanned = 0;
        int cursor = 0;
        while (cursor < raw.length()) {
            int streamMarker = raw.indexOf("stream", cursor);
            if (streamMarker < 0) {
                break;
            }

            // Locate the PDF dictionary immediately before this stream without
            // applying a large backtracking regex to the entire binary PDF.
            int dictEnd = streamMarker;
            while (dictEnd > 0 && Character.isWhitespace(raw.charAt(dictEnd - 1))) {
                dictEnd--;
            }
            int dictStart = raw.lastIndexOf("<<", dictEnd);
            int closeDict = raw.lastIndexOf(">>", dictEnd);
            if (dictStart < 0 || closeDict < dictStart) {
                cursor = streamMarker + 6;
                continue;
            }
            String dict = raw.substring(dictStart, closeDict + 2);

            int bodyStart = streamMarker + 6;
            if (bodyStart < raw.length() && raw.charAt(bodyStart) == '\r') bodyStart++;
            if (bodyStart < raw.length() && raw.charAt(bodyStart) == '\n') bodyStart++;
            int endMarker = raw.indexOf("endstream", bodyStart);
            if (endMarker < 0) {
                break;
            }

            streamsScanned++;
            String streamBody = raw.substring(bodyStart, endMarker);
            byte[] streamBytes = streamBody.getBytes(StandardCharsets.ISO_8859_1);

            byte[] decoded;
            if (dict.contains("/FlateDecode")) {
                decoded = inflate(streamBytes);
                if (decoded == null) {
                    cursor = endMarker + 9;
                    continue;
                }
            } else if (dict.contains("/Image") || dict.contains("/XObject") && !dict.contains("/Font")) {
                cursor = endMarker + 9;
                continue;
            } else {
                decoded = streamBytes;
            }

            String content = new String(decoded, StandardCharsets.ISO_8859_1);
            if (content.contains("Tj") || content.contains("TJ")) {
                appendShowTextOperators(content, text);
            }
            cursor = endMarker + 9;
        }

        String result = collapseWhitespace(text.toString());
        if (result.isBlank()) {
            throw new IOException("Could not extract any text from '" + path.getFileName() + "' ("
                    + streamsScanned + " stream object(s) scanned). The PDF may be a scanned/image-only "
                    + "document (which needs OCR, not supported here) or use a non-standard encoding. "
                    + "Try uploading a .docx or .txt version of the resume instead.");
        }
        return result;
    }

    private static void appendShowTextOperators(String content, StringBuilder out) {
        Matcher tj = PDF_SHOW_TEXT.matcher(content);
        Matcher tja = PDF_SHOW_ARRAY.matcher(content);
        // Walk both operator kinds in document order by scanning linearly.
        int pos = 0;
        while (pos < content.length()) {
            int tjStart = findNext(tj, content, pos);
            int tjaStart = findNext(tja, content, pos);
            if (tjStart == -1 && tjaStart == -1) {
                break;
            }
            if (tjaStart == -1 || (tjStart != -1 && tjStart <= tjaStart)) {
                tj.region(tjStart, content.length());
                if (tj.lookingAt()) {
                    out.append(unescapePdfString(tj.group(1))).append(' ');
                    pos = tj.end();
                } else {
                    pos = tjStart + 1;
                }
            } else {
                tja.region(tjaStart, content.length());
                if (tja.lookingAt()) {
                    Matcher inner = PDF_ARRAY_TOKEN.matcher(tja.group(1));
                    while (inner.find()) {
                        if (inner.group(1) != null) {
                            out.append(unescapePdfString(inner.group(1)));
                        } else if (inner.group(2) != null) {
                            try {
                                if (Double.parseDouble(inner.group(2)) <= WORD_GAP_THRESHOLD) {
                                    out.append(' ');
                                }
                            } catch (NumberFormatException ignored) {
                                // not a usable number - ignore
                            }
                        }
                    }
                    out.append(' ');
                    pos = tja.end();
                } else {
                    pos = tjaStart + 1;
                }
            }
        }
        out.append('\n');
    }

    private static int findNext(Matcher m, String content, int from) {
        m.region(from, content.length());
        return m.find() ? m.start() : -1;
    }

    private static String unescapePdfString(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n' -> { sb.append('\n'); i++; }
                    case 'r' -> { sb.append('\r'); i++; }
                    case 't' -> { sb.append('\t'); i++; }
                    case '(' -> { sb.append('('); i++; }
                    case ')' -> { sb.append(')'); i++; }
                    case '\\' -> { sb.append('\\'); i++; }
                    default -> {
                        if (Character.isDigit(next)) {
                            // octal escape \ddd (up to 3 digits)
                            int end = i + 1;
                            int digits = 0;
                            while (end < s.length() && digits < 3 && Character.isDigit(s.charAt(end))) {
                                end++;
                                digits++;
                            }
                            try {
                                int code = Integer.parseInt(s.substring(i + 1, end), 8);
                                sb.append((char) code);
                            } catch (NumberFormatException ignored) {
                                // fall through - drop the escape
                            }
                            i = end - 1;
                        } else {
                            sb.append(next);
                            i++;
                        }
                    }
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static byte[] inflate(byte[] compressed) {
        Inflater inflater = new Inflater();
        inflater.setInput(compressed);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, compressed.length * 3));
        byte[] buffer = new byte[4096];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        break; // truncated/partial stream - return what we have so far
                    }
                }
                out.write(buffer, 0, count);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            // Return whatever was successfully inflated before the corruption, if anything.
            return out.size() > 0 ? out.toByteArray() : null;
        } finally {
            inflater.end();
        }
    }

    private static String collapseWhitespace(String s) {
        return s.replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }
}
