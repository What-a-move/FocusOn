package com.capstone.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** Checks that the Spring application context can start. */
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

  @Test
  void contextLoads() {}

}
