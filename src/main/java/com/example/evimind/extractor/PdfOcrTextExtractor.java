package com.example.evimind.extractor;

import java.io.IOException;
import java.nio.file.Path;

public interface PdfOcrTextExtractor {

  ExtractionResult extract(Path filePath, int maxSize) throws IOException;
}
