package com.capstone.backend.auth.service;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 서버 시각과 만료 정리 작업을 연결 기능 내부에서 공유한다. */
@Configuration
@EnableScheduling
public class PairingConfiguration {
  @Bean
  Clock pairingClock() {
    return Clock.systemUTC();
  }
}
