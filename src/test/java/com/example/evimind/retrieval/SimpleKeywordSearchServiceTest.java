package com.example.evimind.retrieval;

import com.example.evimind.mapper.DocumentChunkMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SimpleKeywordSearchServiceTest {

    @Test
    void shouldSkipDatabaseScanWhenQueryHasNoSearchableTerms() {
        DocumentChunkMapper documentChunkMapper = mock(DocumentChunkMapper.class);
        SimpleKeywordSearchService searchService = new SimpleKeywordSearchService();
        ReflectionTestUtils.setField(searchService, "documentChunkMapper", documentChunkMapper);

        assertTrue(searchService.search(" , ! a ", 1L, 10).isEmpty());

        verifyNoInteractions(documentChunkMapper);
    }
}
