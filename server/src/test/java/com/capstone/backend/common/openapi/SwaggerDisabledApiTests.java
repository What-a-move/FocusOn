package com.capstone.backend.common.openapi;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** docker 설정에서도 문서가 공개되지 않는지 확인한다. DB만 test 설정으로 대체한다. */
@SpringBootTest
@ActiveProfiles({"docker", "test"})
class SwaggerDisabledApiTests {
  @Autowired private WebApplicationContext context;

  @Test
  void documentationIsDisabledAndRemainsBehindDefaultDenyRule() throws Exception {
    assertFalse(context.getEnvironment().getProperty("springdoc.api-docs.enabled", Boolean.class));
    assertFalse(context.getEnvironment()
        .getProperty("springdoc.swagger-ui.enabled", Boolean.class));
    assertFalse(context.getBeanProvider(OpenAPI.class).iterator().hasNext());
    var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    for (String path : new String[]{"/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs",
        "/v3/api-docs/swagger-config", "/v3/api-docs.yaml"}) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
    }
  }
}
