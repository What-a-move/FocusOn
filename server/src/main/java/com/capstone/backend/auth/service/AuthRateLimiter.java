package com.capstone.backend.auth.service;

import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 단일 서버용 유한 fixed-window 한도. 다중 인스턴스에서는 공유 저장소로 교체한다. */
@Component
public class AuthRateLimiter {
  private static final int MAX_KEYS = 10000;
  private final Map<String, Bucket> loginBuckets = new HashMap<>();
  private final Map<String, Bucket> authenticatedBuckets = new HashMap<>();

  /** 동일 키의 카운트와 만료 처리를 원자적으로 수행한다. */
  public synchronized void check(String key, int limit, Duration window) {
    // 익명 클라이언트가 로그인 키 공간을 채워도 기존 인증 사용자의 한도를 소진하지 못한다.
    Map<String, Bucket> buckets = key.startsWith("google:") ? loginBuckets : authenticatedBuckets;
    long now = System.currentTimeMillis();
    Bucket bucket = buckets.get(key);
    if (bucket == null || bucket.expiresAt <= now) {
      if (buckets.size() >= MAX_KEYS) {
        buckets.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (buckets.size() >= MAX_KEYS) {
          throw ApiException.tooManyRequests(60);
        }
      }
      bucket = new Bucket(now + window.toMillis());
      buckets.put(key, bucket);
    }
    if (bucket.count >= limit) {
      throw ApiException.tooManyRequests(Math.max(1, (bucket.expiresAt - now + 999) / 1000));
    }
    bucket.count++;
  }

  private static final class Bucket {
    private final long expiresAt;
    private int count;

    private Bucket(long expiresAt) {
      this.expiresAt = expiresAt;
    }
  }
}
