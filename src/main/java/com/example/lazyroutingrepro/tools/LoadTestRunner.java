package com.example.lazyroutingrepro.tools;

import com.example.lazyroutingrepro.probe.ProbeResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public final class LoadTestRunner {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private LoadTestRunner() {
  }

  public static void main(String[] args) {
    Config config = Config.fromArgs(args);

    HttpClient client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(config.timeoutSeconds()))
        .build();

    ConcurrentHashMap<String, AtomicInteger> readonlyCounts = new ConcurrentHashMap<>();
    ConcurrentHashMap<String, AtomicInteger> writeCounts = new ConcurrentHashMap<>();
    ConcurrentLinkedQueue<String> errors = new ConcurrentLinkedQueue<>();
    AtomicInteger completed = new AtomicInteger();
    Semaphore semaphore = new Semaphore(config.concurrency());
    List<CompletableFuture<Void>> futures = new ArrayList<>(config.totalRequests());

    try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
      for (int i = 1; i <= config.totalRequests(); i++) {
        int requestIndex = i;
        acquirePermit(semaphore, requestIndex);

        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
          try {
            executeRequest(
                client,
                config,
                requestIndex,
                readonlyCounts,
                writeCounts,
                errors
            );
          }
          finally {
            int done = completed.incrementAndGet();
            if (done % config.progressEvery() == 0 || done == config.totalRequests()) {
              System.out.printf("Progress: %d/%d%n", done, config.totalRequests());
            }
            semaphore.release();
          }
        }, executor);

        futures.add(future);
      }

      CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
    }

    printSummary("Readonly routing summary", readonlyCounts);
    printSummary("Write routing summary", writeCounts);

    System.out.println();
    System.out.println("Errors");
    if (errors.isEmpty()) {
      System.out.println("  none");
    }
    else {
      errors.forEach(error -> System.out.println("  " + error));
    }

    int readonlyWriterHits = readonlyCounts.entrySet().stream()
        .filter(entry -> entry.getKey().contains("|WRITER|"))
        .mapToInt(entry -> entry.getValue().get())
        .sum();

    System.out.println();
    System.out.printf("Readonly requests routed to WRITER: %d%n", readonlyWriterHits);
    if (readonlyWriterHits > 0) {
      System.out.println("BUG REPRODUCED: readonly traffic reached the writer datasource.");
    }
    else {
      System.out.println("No readonly traffic reached writer in this run.");
    }
  }

  private static void executeRequest(
      HttpClient client,
      Config config,
      int requestIndex,
      ConcurrentHashMap<String, AtomicInteger> readonlyCounts,
      ConcurrentHashMap<String, AtomicInteger> writeCounts,
      ConcurrentLinkedQueue<String> errors
  ) {
    boolean writeRequest = requestIndex % config.writeEvery() == 0;
    String path = writeRequest
        ? "/api/probe/write?note=" + encode("req-" + requestIndex)
        : "/api/probe/readonly";

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(config.baseUrl() + path))
        .timeout(Duration.ofSeconds(config.timeoutSeconds()));

    HttpRequest request = writeRequest
        ? builder.POST(HttpRequest.BodyPublishers.noBody()).build()
        : builder.GET().build();

    try {
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        errors.add((writeRequest ? "WRITE " : "READ ") + requestIndex
            + " failed: HTTP " + response.statusCode() + " " + response.body());
        return;
      }

      ProbeResponse payload = OBJECT_MAPPER.readValue(response.body(), ProbeResponse.class);
      String key = payload.marker() + "|" + payload.nodeRole() + "|" + payload.currentDatabase();
      increment(writeRequest ? writeCounts : readonlyCounts, key);
    }
    catch (IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      errors.add((writeRequest ? "WRITE " : "READ ") + requestIndex + " exception: " + ex.getMessage());
    }
  }

  private static void acquirePermit(Semaphore semaphore, int requestIndex) {
    try {
      semaphore.acquire();
    }
    catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while scheduling request " + requestIndex, ex);
    }
  }

  private static void increment(ConcurrentHashMap<String, AtomicInteger> counts, String key) {
    counts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
  }

  private static void printSummary(String title, ConcurrentHashMap<String, AtomicInteger> counts) {
    System.out.println();
    System.out.println(title);
    if (counts.isEmpty()) {
      System.out.println("  none");
      return;
    }

    counts.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> System.out.printf("  %s -> %d%n", entry.getKey(), entry.getValue().get()));
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private record Config(
      String baseUrl,
      int totalRequests,
      int concurrency,
      int writeEvery,
      int timeoutSeconds,
      int progressEvery
  ) {

    private Config {
      Objects.requireNonNull(baseUrl, "baseUrl");
      if (totalRequests <= 0) {
        throw new IllegalArgumentException("totalRequests must be > 0");
      }
      if (concurrency <= 0) {
        throw new IllegalArgumentException("concurrency must be > 0");
      }
      if (writeEvery <= 0) {
        throw new IllegalArgumentException("writeEvery must be > 0");
      }
      if (timeoutSeconds <= 0) {
        throw new IllegalArgumentException("timeoutSeconds must be > 0");
      }
      if (progressEvery <= 0) {
        throw new IllegalArgumentException("progressEvery must be > 0");
      }
    }

    static Config fromArgs(String[] args) {
      String baseUrl = "http://localhost:8088";
      int totalRequests = 200;
      int concurrency = 20;
      int writeEvery = 10;
      int timeoutSeconds = 30;
      int progressEvery = 25;

      for (String arg : args) {
        if (arg.startsWith("--base-url=")) {
          baseUrl = arg.substring("--base-url=".length());
        }
        else if (arg.startsWith("--total-requests=")) {
          totalRequests = Integer.parseInt(arg.substring("--total-requests=".length()));
        }
        else if (arg.startsWith("--concurrency=")) {
          concurrency = Integer.parseInt(arg.substring("--concurrency=".length()));
        }
        else if (arg.startsWith("--write-every=")) {
          writeEvery = Integer.parseInt(arg.substring("--write-every=".length()));
        }
        else if (arg.startsWith("--timeout-seconds=")) {
          timeoutSeconds = Integer.parseInt(arg.substring("--timeout-seconds=".length()));
        }
        else if (arg.startsWith("--progress-every=")) {
          progressEvery = Integer.parseInt(arg.substring("--progress-every=".length()));
        }
      }

      return new Config(baseUrl, totalRequests, concurrency, writeEvery, timeoutSeconds, progressEvery);
    }
  }
}