package com.capstone.backend.report.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** 오래 걸리는 작업이 호출 스레드(공유 스케줄러)를 붙잡지 않고, 겹친 실행은 건너뛰는지 본다. */
class SingleFlightRunnerTests {
  @Test
  void longTaskRunsOffCallerThreadAndOverlappingSubmitIsSkipped() throws Exception {
    var runner = new SingleFlightRunner("summary-test");
    var release = new CountDownLatch(1);
    var started = new CountDownLatch(1);
    var thread = new AtomicReference<String>();
    try {
      long before = System.nanoTime();
      assertTrue(runner.submit(() -> {
        thread.set(Thread.currentThread().getName());
        started.countDown();
        awaitQuietly(release);
      }));
      // 작업이 막혀 있어도 호출자는 바로 돌아온다.
      assertTrue(Duration.ofNanos(System.nanoTime() - before).toMillis() < 1000);
      assertTrue(started.await(5, TimeUnit.SECONDS));
      assertTrue(thread.get().equals("summary-test"));
      assertFalse(runner.submit(() -> {}));
      release.countDown();
      var next = new CountDownLatch(1);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean accepted = false;
      while (!accepted && System.nanoTime() < deadline) {
        accepted = runner.submit(next::countDown);
      }
      assertTrue(accepted);
      assertTrue(next.await(5, TimeUnit.SECONDS));
    } finally {
      release.countDown();
      runner.shutdown(Duration.ofSeconds(5));
    }
  }

  @Test
  void failedTaskDoesNotBlockLaterRuns() throws Exception {
    var runner = new SingleFlightRunner("summary-test-fail");
    try {
      assertTrue(runner.submit(() -> {
        throw new IllegalStateException("boom");
      }));
      var next = new CountDownLatch(1);
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      boolean accepted = false;
      while (!accepted && System.nanoTime() < deadline) {
        accepted = runner.submit(next::countDown);
      }
      assertTrue(accepted);
      assertTrue(next.await(5, TimeUnit.SECONDS));
    } finally {
      runner.shutdown(Duration.ofSeconds(5));
    }
  }

  private static void awaitQuietly(CountDownLatch latch) {
    try {
      latch.await(10, TimeUnit.SECONDS);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
