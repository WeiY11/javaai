package com.example.evimind.retrieval;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import java.util.Collection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.service.DocumentPermissionService;

@ExtendWith(MockitoExtension.class)
class HybridSearchServiceTest {

  @Mock private PgVectorSearchService pgVectorSearchService;
  @Mock private ElasticsearchSearchService elasticsearchSearchService;
  @Mock private RrfFusionService rrfFusionService;
  @Mock private QueryRewriteService queryRewriteService;
  @Mock private DocumentPermissionService documentPermissionService;

  private ExecutorService retrievalExecutor;
  private HybridSearchService hybridSearchService;

  @BeforeEach
  void setUp() {
    GroupContext.set(11L, 1L, "USER");
    lenient()
        .when(
            documentPermissionService.findReadableDocumentIds(
                eq(1L), anyCollection(), eq(11L)))
        .thenAnswer(invocation -> Set.copyOf(invocation.<Collection<Long>>getArgument(1)));
    retrievalExecutor = Executors.newCachedThreadPool();
    hybridSearchService =
        new HybridSearchService(
            pgVectorSearchService,
            elasticsearchSearchService,
            rrfFusionService,
            null,
            queryRewriteService,
            documentPermissionService,
            retrievalExecutor);
  }

  @AfterEach
  void tearDown() {
    GroupContext.clear();
    retrievalExecutor.shutdownNow();
  }

  @Test
  void shouldNotWaitForSlowKeywordBackendWhenSemanticResultsAreAvailable() {
    ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 50L);
    List<SearchResult> semanticResults =
        List.of(new SearchResult("chunk_1", 1L, 1L, "semantic", 0, 0.90, "pgvector"));
    List<SearchResult> fusedResults =
        List.of(new SearchResult("chunk_1", 1L, 1L, "semantic", 0, 1.0, "rrf_fused"));

    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search(eq("query"), eq(1L), eq(30))).thenReturn(semanticResults);
    when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(
            invocation -> {
              Thread.sleep(500);
              return List.of();
            });
    when(rrfFusionService.fuse(eq(semanticResults), eq(List.of()), eq(10)))
        .thenReturn(fusedResults);

    long started = System.nanoTime();
    List<SearchResult> results = hybridSearchService.search("query", 1L, 10);
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

    assertEquals(fusedResults, results);
    assertTrue(
        elapsedMillis < 250,
        "search should return after timeout instead of waiting for the slow backend");
  }

  @Test
  void shouldShareOneTimeoutBudgetAcrossBothBackends() {
    ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 500L);
    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(
            invocation -> {
              Thread.sleep(2000);
              return List.of();
            });
    when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(
            invocation -> {
              Thread.sleep(2000);
              return List.of();
            });

    long started = System.nanoTime();
    List<SearchResult> results = hybridSearchService.search("query", 1L, 10);
    long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

    assertTrue(results.isEmpty());
    assertTrue(
        elapsedMillis < 800,
        "parallel backends must share one timeout budget, elapsed=" + elapsedMillis + "ms");
  }

  @Test
  void shouldInterruptBlockedBackendsAfterTimeout() throws InterruptedException {
    ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 100L);
    CountDownLatch bothStarted = new CountDownLatch(2);
    CountDownLatch interrupted = new CountDownLatch(2);

    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(invocation -> blockUntilInterrupted(bothStarted, interrupted));
    when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(invocation -> blockUntilInterrupted(bothStarted, interrupted));

    assertTrue(hybridSearchService.search("query", 1L, 10).isEmpty());

    assertEquals(0L, bothStarted.getCount(), "both backend tasks must start");
    assertTrue(
        interrupted.await(300, TimeUnit.MILLISECONDS),
        "timed-out backend tasks must release retrieval threads promptly");
  }

  @Test
  void shouldPropagateCallerInterruptionWithoutRunningFallback() throws InterruptedException {
    SimpleKeywordSearchService fallbackSearchService = mock(SimpleKeywordSearchService.class);
    HybridSearchService serviceWithFallback =
        new HybridSearchService(
            pgVectorSearchService,
            elasticsearchSearchService,
            rrfFusionService,
            fallbackSearchService,
            queryRewriteService,
            documentPermissionService,
            retrievalExecutor);
    ReflectionTestUtils.setField(serviceWithFallback, "backendTimeoutMillis", 2000L);
    CountDownLatch bothStarted = new CountDownLatch(2);
    CountDownLatch backendsInterrupted = new CountDownLatch(2);
    CountDownLatch callerFinished = new CountDownLatch(1);
    AtomicBoolean callerInterruptPreserved = new AtomicBoolean();

    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(invocation -> blockUntilInterrupted(bothStarted, backendsInterrupted));
    when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(invocation -> blockUntilInterrupted(bothStarted, backendsInterrupted));

    Thread caller =
        new Thread(
            () -> {
              serviceWithFallback.search("query", 1L, 10);
              callerInterruptPreserved.set(Thread.currentThread().isInterrupted());
              callerFinished.countDown();
            },
            "hybrid-search-caller-test");
    caller.start();
    try {
      assertTrue(bothStarted.await(1, TimeUnit.SECONDS), "both backend tasks must start");
      caller.interrupt();

      assertTrue(
          callerFinished.await(500, TimeUnit.MILLISECONDS),
          "an interrupted caller must not wait for the backend deadline");
      assertTrue(callerInterruptPreserved.get(), "caller interrupt status must be restored");
      assertTrue(
          backendsInterrupted.await(300, TimeUnit.MILLISECONDS),
          "caller interruption must cancel both backend tasks");
      verifyNoInteractions(fallbackSearchService);
    } finally {
      caller.interrupt();
      caller.join(1000);
    }
  }

  @Test
  void shouldCancelSubmittedBackendAndFallbackWhenExecutorRejects() throws InterruptedException {
    ExecutorService firstTaskExecutor = Executors.newSingleThreadExecutor();
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch firstInterrupted = new CountDownLatch(1);
    AtomicInteger submissions = new AtomicInteger();
    Executor rejectingSecondSubmission =
        command -> {
          if (submissions.getAndIncrement() > 0) {
            throw new RejectedExecutionException("retrieval executor saturated");
          }
          firstTaskExecutor.execute(command);
          try {
            if (!firstStarted.await(1, TimeUnit.SECONDS)) {
              throw new RejectedExecutionException("first backend task did not start");
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("test caller interrupted", e);
          }
        };
    SimpleKeywordSearchService fallbackSearchService = mock(SimpleKeywordSearchService.class);
    HybridSearchService serviceWithRejectingExecutor =
        new HybridSearchService(
            pgVectorSearchService,
            elasticsearchSearchService,
            rrfFusionService,
            fallbackSearchService,
            queryRewriteService,
            documentPermissionService,
            rejectingSecondSubmission);

    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search(eq("query"), eq(1L), eq(30)))
        .thenAnswer(invocation -> blockUntilInterrupted(firstStarted, firstInterrupted));
    when(fallbackSearchService.search(eq("query"), eq(1L), eq(30))).thenReturn(List.of());

    try {
      assertTrue(serviceWithRejectingExecutor.search("query", 1L, 10).isEmpty());

      assertTrue(
          firstInterrupted.await(300, TimeUnit.MILLISECONDS),
          "a partial submission must be cancelled when the paired task is rejected");
      verifyNoInteractions(elasticsearchSearchService);
      verify(fallbackSearchService).search("query", 1L, 30);
    } finally {
      firstTaskExecutor.shutdownNow();
      assertTrue(firstTaskExecutor.awaitTermination(1, TimeUnit.SECONDS));
    }
  }

  @Test
  void shouldAskBackendsForExpandedCandidateWindow() {
    ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 1000L);
    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search(eq("query"), eq(1L), eq(15))).thenReturn(List.of());
    when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(15))).thenReturn(List.of());

    hybridSearchService.search("query", 1L, 5);

    verify(pgVectorSearchService).search("query", 1L, 15);
    verify(elasticsearchSearchService).search("query", 1L, 15);
  }

  @Test
  void shouldClampInvalidAndOversizedTopKAtSearchBoundary() {
    ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 1000L);
    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search(eq("query"), eq(1L), eq(50))).thenReturn(List.of());
    when(elasticsearchSearchService.search(eq("query"), eq(1L), eq(50))).thenReturn(List.of());

    hybridSearchService.search("query", 1L, 500);

    verify(pgVectorSearchService).search("query", 1L, 50);
    verify(elasticsearchSearchService).search("query", 1L, 50);
  }

  @Test
  void shouldUseRewrittenQueryWhenConversationHistoryProvided() {
    ReflectionTestUtils.setField(hybridSearchService, "backendTimeoutMillis", 1000L);
    String history = "user: 什么是机器学习\nassistant: 机器学习是...";

    when(queryRewriteService.rewrite(eq("它的优点"), eq(history))).thenReturn("机器学习的优点");
    when(pgVectorSearchService.search(eq("机器学习的优点"), eq(1L), eq(30))).thenReturn(List.of());
    when(elasticsearchSearchService.search(eq("机器学习的优点"), eq(1L), eq(30))).thenReturn(List.of());

    hybridSearchService.search("它的优点", 1L, 10, history);

    // 验证后端搜索引擎收到的是改写后的查询，而非原始查询
    verify(pgVectorSearchService).search("机器学习的优点", 1L, 30);
    verify(elasticsearchSearchService).search("机器学习的优点", 1L, 30);
    // 验证原始查询未被直接使用
    verify(pgVectorSearchService, never()).search("它的优点", 1L, 30);
  }

  @Test
  void shouldExcludeRestrictedDocumentsBeforeFusion() {
    SearchResult accessible =
        new SearchResult("chunk-accessible", 1L, 1L, "accessible", 0, 0.9, "pgvector");
    SearchResult restricted =
        new SearchResult("chunk-restricted", 2L, 1L, "restricted", 0, 0.99, "pgvector");
    List<SearchResult> backendResults = List.of(accessible, restricted);

    when(queryRewriteService.rewrite(eq("query"), isNull())).thenReturn("query");
    when(pgVectorSearchService.search("query", 1L, 30)).thenReturn(backendResults);
    when(elasticsearchSearchService.search("query", 1L, 30)).thenReturn(backendResults);
    when(documentPermissionService.findReadableDocumentIds(1L, Set.of(1L, 2L), 11L))
        .thenReturn(Set.of(1L));
    when(rrfFusionService.fuse(eq(List.of(accessible)), eq(List.of(accessible)), eq(10)))
        .thenReturn(List.of(accessible));

    assertEquals(List.of(accessible), hybridSearchService.search("query", 1L, 10));

    verify(documentPermissionService)
        .findReadableDocumentIds(1L, Set.of(1L, 2L), 11L);
  }

  private static List<SearchResult> blockUntilInterrupted(
      CountDownLatch bothStarted, CountDownLatch interrupted) throws InterruptedException {
    bothStarted.countDown();
    if (!bothStarted.await(1, TimeUnit.SECONDS)) {
      throw new IllegalStateException("both backend tasks did not start");
    }
    try {
      Thread.sleep(5000);
    } catch (InterruptedException e) {
      interrupted.countDown();
      Thread.currentThread().interrupt();
    }
    return List.of();
  }
}
