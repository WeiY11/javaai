package com.example.evimind.extractor;

import java.io.IOException;
import java.nio.file.Path;

public interface FileContentExtractor {

  boolean supports(String fileName);

  ExtractionResult extract(Path filePath, int maxSize) throws IOException;
}
