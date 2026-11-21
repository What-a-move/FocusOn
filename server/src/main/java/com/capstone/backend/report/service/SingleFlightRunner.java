package com.capstone.backend.report.service;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 오래 걸리는 주기 작업을 전용 스레드 하나에서 돌린다. 앞선 실행이 끝나지 않았으면 이번 요청은
 * 건너뛴다. Spring 기본 스케줄러 스레드(세션 정리·연결 코드 정리와 공유)를 붙잡지 않기 위해서다.
 * TaskScheduler bean으로 만들지 않는 이유: bean이 생기면 Boot 기본 스케줄러가 빠지고 다른
 * 주기 작업까지 이 스레드로 몰린다.
 */
final class SingleFlightRunner implements AutoCloseable {
  private final ExecutorService executor;
  private final AtomicBoolean running = new AtomicBoolean();

  /** 이름이 붙은 daemon 스레드 하나를 쓰는 실행기를 만든다. */
  SingleFlightRunner(String threadName) {
    this.executor = Executors.newSingleThreadExecutor(task -> {
      var thread = new Thread(task, threadName);
      thread.setDaemon(true);
      return thread;
    });
  }

  /** 실행 중이 아니면 작업을 넘기고 true, 이미 실행 중이면 넘기지 않고 false를 반환한다. */
  boolean submit(Runnable task) {
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    try {
      executor.execute(() -> {
        try {
          task.run();
        } finally {
          running.set(false);
        }
      });
      return true;
    } catch (RuntimeException rejected) {
      running.set(false);
      throw rejected;
    }
  }

  /** 새 작업을 받지 않고 진행 중인 작업을 잠시 기다린다. 남은 작업은 lease 만료로 재점유된다. */
  void shutdown(Duration grace) {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(grace.toMillis(), TimeUnit.MILLISECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException exception) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  /** 종료 단계 제한(30초) 안에 끝나도록 25초까지만 기다린다. */
  @Override
  public void close() {
    shutdown(Duration.ofSeconds(25));
  }
}
