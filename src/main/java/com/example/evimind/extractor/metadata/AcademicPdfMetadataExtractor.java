package com.example.evimind.extractor.metadata;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AcademicPdfMetadataExtractor {

    private static final Pattern DOI_PATTERN = Pattern.compile("\\b(10\\.\\d{4,}/[\\S]+)\\b");
    private static final Pattern YEAR_PATTERN = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern AUTHOR_SEPARATOR = Pattern.compile("[,;]|\\band\\b");

    public PaperMetadata extract(String text, String fileName) {
        if (text == null || text.isBlank()) return null;

        PaperMetadata.PaperMetadataBuilder builder = PaperMetadata.builder();

        String firstPage = getFirstPage(text, 3000);

        builder.title(extractTitle(firstPage, fileName));
        builder.authors(extractAuthors(firstPage));
        builder.abstractText(extractAbstract(text));
        builder.doi(extractDoi(text));
        builder.year(extractYear(firstPage));
        builder.journal(extractJournal(firstPage));
        builder.references(extractReferences(text));

        PaperMetadata metadata = builder.build();
        if (metadata.getTitle() == null && metadata.getDoi() == null && metadata.getAuthors() == null) {
            return null;
        }
        return metadata;
    }

    private String getFirstPage(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) : text;
    }

    private String extractTitle(String firstPage, String fileName) {
        String[] lines = firstPage.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 15 && trimmed.length() < 300
                    && !trimmed.toLowerCase().contains("abstract")
                    && !trimmed.toLowerCase().contains("introduction")
                    && !trimmed.toLowerCase().contains("copyright")
                    && !trimmed.toLowerCase().contains("all rights reserved")) {
                return trimmed;
            }
        }
        if (fileName != null && fileName.toLowerCase().endsWith(".pdf")) {
            return fileName.substring(0, fileName.length() - 4).replaceAll("[_-]", " ").trim();
        }
        return null;
    }

    private List<String> extractAuthors(String firstPage) {
        List<String> authors = new ArrayList<>();
        for (String line : firstPage.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.length() > 5 && trimmed.length() < 200
                    && (trimmed.contains(",") || trimmed.contains(" and "))
                    && Character.isUpperCase(trimmed.charAt(0))) {
                for (String part : AUTHOR_SEPARATOR.split(trimmed)) {
                    String name = part.trim();
                    if (name.length() > 2 && !name.toLowerCase().contains("university")
                            && !name.toLowerCase().contains("institute")
                            && !name.toLowerCase().contains("department")
                            && !name.toLowerCase().contains("laboratory")) {
                        authors.add(name);
                    }
                }
                if (!authors.isEmpty()) break;
            }
        }
        return authors.isEmpty() ? null : authors;
    }

    private String extractAbstract(String text) {
        Pattern p = Pattern.compile("(?i)abstract[\\s\\n]+(.+?)(?:\\n\\s*\\n|introduction|1\\.\\s)",
                Pattern.DOTALL);
        Matcher m = p.matcher(text);
        if (m.find()) {
            String abs = m.group(1).trim();
            return abs.length() > 2000 ? abs.substring(0, 2000) : abs;
        }
        return null;
    }

    private String extractDoi(String text) {
        Matcher m = DOI_PATTERN.matcher(text);
        if (m.find()) {
            String doi = m.group(1);
            if (doi.endsWith(".")) doi = doi.substring(0, doi.length() - 1);
            return doi;
        }
        return null;
    }

    private Integer extractYear(String firstPage) {
        Matcher m = YEAR_PATTERN.matcher(firstPage);
        while (m.find()) {
            int year = Integer.parseInt(m.group());
            if (year >= 1950 && year <= 2030) return year;
        }
        return null;
    }

    private String extractJournal(String firstPage) {
        String[] lines = firstPage.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.matches(".*(Journal|Conference|Proceedings|Transactions|Review|Letters)\\b.*")
                    && trimmed.length() < 200) {
                return trimmed;
            }
        }
        return null;
    }

    private List<String> extractReferences(String text) {
        int refIdx = -1;
        String lower = text.toLowerCase();
        String[] markers = {"\nreferences\n", "\nreferences cited\n", "\nbibliography\n",
                "\nreference\n", "\n4. references\n"};
        for (String marker : markers) {
            refIdx = lower.lastIndexOf(marker);
            if (refIdx >= 0) break;
        }

        if (refIdx < 0) return null;

        List<String> refs = new ArrayList<>();
        String refSection = text.substring(refIdx);
        Pattern refPattern = Pattern.compile("^\\[?\\d+\\]?\\s*.+$", Pattern.MULTILINE);
        Matcher m = refPattern.matcher(refSection);
        int count = 0;
        while (m.find() && count < 50) {
            refs.add(m.group().trim());
            count++;
        }
        return refs.isEmpty() ? null : refs;
    }
}
