package com.capstone.backend.auth.service;

import com.capstone.backend.common.api.ApiException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 단일 서버용 유한 fixed-window 한도. 다중 인스턴스에서는 공유 저장소로 교체한다. */
@Component
public class AuthRateLimiter {
  private static final int MAX_KEYS = 10000;
  private static final int MAX_PAIRING_INSTALLATIONS_PER_IP = 32;
  private final Map<String, Bucket> loginBuckets = new HashMap<>();
  private final Map<String, Bucket> refreshBuckets = new HashMap<>();
  private final Map<String, Bucket> pairingBuckets = new HashMap<>();
  private final Map<String, Map<String, Bucket>> pairingInstallationBuckets = new HashMap<>();
  private final Map<String, Bucket> authenticatedBuckets = new HashMap<>();
  private final LongSupplier milliseconds;

  /** 운영 요청 제한은 서버 시계를 사용한다. */
  @Autowired
  public AuthRateLimiter() {
    this(System::currentTimeMillis);
  }

  /** 만료 경계를 기다리지 않고 검증할 수 있도록 테스트 시계를 받는다. */
  AuthRateLimiter(LongSupplier milliseconds) {
    this.milliseconds = milliseconds;
  }

  /**
   * 설치 ID 변경으로 키 공간을 채우지 못하도록 IP를 먼저 검사한다.
   * 공유 NAT를 고려한 IP 보호 상한 300회/10분과 기존 설치별 5회/10분을 함께 적용한다.
   */
  public synchronized void checkGoogleLogin(String remoteAddress, String installationId) {
    check("google:ip:" + TokenSecrets.hash(remoteAddress), 300, Duration.ofMinutes(10));
    check("google:" + TokenSecrets.hash(remoteAddress + "\n" + installationId),
        5, Duration.ofMinutes(10));
  }

  /** 미인증 refresh 요청은 별도 공개 공간에서 IP별로 제한한다. */
  public void checkRefreshIp(String remoteAddress) {
    check("refresh:ip:" + TokenSecrets.hash(remoteAddress), 300, Duration.ofMinutes(10));
  }

  /** JSON 검증 전 익명 pairing IP 공간을 제한한다. */
  public void checkPairingIp(String remoteAddress) {
    check("pairing:ip:" + TokenSecrets.hash(remoteAddress), 300, Duration.ofMinutes(10));
  }

  /** 공개 요청은 인증 사용자 키 공간을 소모하지 않는다. */
  public void checkPairingInstallation(String remoteAddress, String installationId) {
    synchronized (pairingInstallationBuckets) {
      checkPairingInstallationLocked(remoteAddress, installationId);
    }
  }

  private void checkPairingInstallationLocked(String remoteAddress, String installationId) {
    String ip = TokenSecrets.hash(remoteAddress);
    String installation = TokenSecrets.hash(installationId);
    long now = milliseconds.getAsLong();
    var group = pairingInstallationBuckets.get(ip);
    if (group == null) {
      if (pairingInstallationBuckets.size() >= MAX_KEYS) {
        pairingInstallationBuckets.values().forEach(bucketMap ->
            bucketMap.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now));
        pairingInstallationBuckets.values().removeIf(Map::isEmpty);
        if (pairingInstallationBuckets.size() >= MAX_KEYS) {
          throw ApiException.tooManyRequests(60);
        }
      }
      group = new HashMap<>();
      pairingInstallationBuckets.put(ip, group);
    }
    Bucket bucket = group.get(installation);
    if (bucket == null || bucket.expiresAt <= now) {
      if (group.size() >= MAX_PAIRING_INSTALLATIONS_PER_IP) {
        group.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (group.size() >= MAX_PAIRING_INSTALLATIONS_PER_IP) {
          long earliest = group.values().stream().mapToLong(value -> value.expiresAt).min()
              .orElse(now + 1000);
          throw ApiException.tooManyRequests(Math.max(1, (earliest - now + 999) / 1000));
        }
      }
      bucket = new Bucket(now + Duration.ofMinutes(10).toMillis());
      group.put(installation, bucket);
    }
    if (bucket.count >= 10) {
      throw ApiException.tooManyRequests(Math.max(1, (bucket.expiresAt - now + 999) / 1000));
    }
    bucket.count++;
  }

  /** 동일 키의 카운트와 만료 처리를 원자적으로 수행한다. */
  public synchronized void check(String key, int limit, Duration window) {
    // 익명 클라이언트가 로그인 키 공간을 채워도 기존 인증 사용자의 한도를 소진하지 못한다.
    Map<String, Bucket> buckets = authenticatedBuckets;
    if (key.startsWith("google:")) {
      buckets = loginBuckets;
    } else if (key.startsWith("refresh:")) {
      buckets = refreshBuckets;
    } else if (key.startsWith("pairing:")) {
      buckets = pairingBuckets;
    }
    long now = milliseconds.getAsLong();
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
