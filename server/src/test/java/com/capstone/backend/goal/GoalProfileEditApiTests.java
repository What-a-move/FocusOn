package com.capstone.backend.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.goal.service.GoalAiClient;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 목표 범위(goalProfile) 수정과 다시 만들기를 검증한다(#314, Desktop 범위 편집 요청). */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class GoalProfileEditApiTests {
  private static final String EDITED = "{\"mainTopic\":\"리액트\",\"purpose\":\"PRACTICE\","
      + "\"coreTopics\":[\"훅\"],\"supportingTopics\":[\"자바스크립트\"],"
      + "\"expectedActivities\":[\"예제 따라 하기\"]}";
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private GoalRepository goals;
  @Autowired private LearningSessionRepository sessions;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private GoalAiClient ai;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "profile-edit-" + UUID.randomUUID());
  }

  @Test
  void patchReplacesWholeProfileAndKeepsText() throws Exception {
    var owner = desktop();
    aiReturns("처음 주제");
    String id = create(owner, "리액트 공부");
    patch(owner, id, "{\"goalProfile\":" + EDITED + "}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalText").value("리액트 공부"))
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("리액트"))
        .andExpect(jsonPath("$.data.goalProfile.coreTopics[0]").value("훅"))
        .andExpect(jsonPath("$.data.goalProfile.expectedActivities.length()").value(1));
    assertEquals("리액트", stored(id).path("mainTopic").asString());

    // 같은 문구를 다시 보내도 고친 범위는 지워지지 않는다.
    patch(owner, id, "{\"goalText\":\"리액트 공부\"}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("리액트"));
    // 문구와 범위를 함께 보내면 둘 다 바뀐다.
    patch(owner, id, "{\"goalText\":\"뷰 공부\",\"goalProfile\":"
        + EDITED.replace("리액트", "뷰") + "}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalText").value("뷰 공부"))
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("뷰"));
  }

  @Test
  void invalidProfileIsRejectedWithoutChange() throws Exception {
    var owner = desktop();
    aiReturns("처음 주제");
    String id = create(owner, "리액트 공부");
    for (String bad : new String[] {
        EDITED.replace("[\"훅\"]", "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]"),
        EDITED.replace("\"훅\"", "\"" + "가".repeat(101) + "\""),
        EDITED.replace("PRACTICE", "UNKNOWN"),
        EDITED.replace("[\"훅\"]", "[\"훅\",\"훅\"]"), "{}", "[]"}) {
      patch(owner, id, "{\"goalProfile\":" + bad + "}").andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    assertEquals("처음 주제", stored(id).path("mainTopic").asString());
  }

  @Test
  void regenerateReplacesProfileAndFailureKeepsOldOne() throws Exception {
    var owner = desktop();
    aiReturns("처음 주제");
    String id = create(owner, "리액트 공부");
    aiReturns("새 주제");
    regenerate(owner, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalId").value(id))
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("새 주제"));
    assertEquals("새 주제", stored(id).path("mainTopic").asString());

    when(ai.profile(eq("리액트 공부"), any())).thenReturn(json.readTree("{\"goalProfile\":{}}"));
    regenerate(owner, id).andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("GOAL_ANALYSIS_UNAVAILABLE"));
    doThrow(failure(GoalAiClient.Failure.TIMEOUT)).when(ai).profile(eq("리액트 공부"), any());
    regenerate(owner, id).andExpect(status().isGatewayTimeout())
        .andExpect(jsonPath("$.code").value("GOAL_ANALYSIS_TIMEOUT"));
    assertEquals("새 주제", stored(id).path("mainTopic").asString());

    // AI 범위가 없던 할 일도 다시 만들 수 있다.
    doThrow(failure(GoalAiClient.Failure.UNAVAILABLE)).when(ai).profile(eq("범위 없는 할 일"),
        any());
    String empty = create(owner, "범위 없는 할 일");
    assertEquals(null, jdbc.queryForObject("SELECT profile_json FROM goals WHERE id = ?",
        String.class, Long.valueOf(empty)));
    org.mockito.Mockito.reset(ai);
    aiReturns("이제 생긴 주제");
    when(ai.profile(eq("범위 없는 할 일"), any())).thenReturn(json.readTree(
        "{\"goalProfile\":" + EDITED.replace("리액트", "이제 생긴 주제") + "}"));
    regenerate(owner, empty).andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("이제 생긴 주제"));
  }

  @Test
  void activeSessionBlocksProfileEditAndRegenerateAndOthersGet404() throws Exception {
    var owner = desktop();
    aiReturns("처음 주제");
    String id = create(owner, "리액트 공부");
    var user = users.findById(Long.valueOf(owner.user().userId())).orElseThrow();
    var device = devices.findById(Long.valueOf(owner.device().deviceId())).orElseThrow();
    Instant now = Instant.now();
    sessions.saveAndFlush(LearningSessionEntity.create(user,
        goals.findById(Long.valueOf(id)).orElseThrow(), device, 1800000, "Asia/Seoul",
        now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), "{}", "v1", now));
    patch(owner, id, "{\"goalProfile\":" + EDITED + "}").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOAL_UPDATE_BLOCKED"));
    regenerate(owner, id).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOAL_UPDATE_BLOCKED"));
    patch(owner, id, "{\"todoStatus\":\"DONE\"}").andExpect(status().isOk());
    assertEquals("처음 주제", stored(id).path("mainTopic").asString());

    var other = desktop();
    regenerate(other, id).andExpect(status().isNotFound());
    patch(other, id, "{\"goalProfile\":" + EDITED + "}").andExpect(status().isNotFound());
    mvc.perform(post("/api/v1/goals/" + id + "/profile")).andExpect(status().isUnauthorized());
  }

  private static GoalAiClient.GoalAiException failure(GoalAiClient.Failure kind) throws Exception {
    var constructor = GoalAiClient.GoalAiException.class
        .getDeclaredConstructor(GoalAiClient.Failure.class, boolean.class);
    constructor.setAccessible(true);
    return constructor.newInstance(kind, false);
  }

  private void aiReturns(String topic) {
    when(ai.profile(any(), any())).thenReturn(json.readTree("{\"goalProfile\":"
        + EDITED.replace("\"리액트\"", "\"" + topic + "\"") + "}"));
  }

  private JsonNode stored(String id) {
    return json.readTree(jdbc.queryForObject("SELECT profile_json FROM goals WHERE id = ?",
        String.class, Long.valueOf(id)));
  }

  private String create(LoginResponse owner, String text) throws Exception {
    String response = mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"" + text + "\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("data").path("goalId").asString();
  }

  private ResultActions patch(LoginResponse owner, String id, String body) throws Exception {
    return mvc.perform(MockMvcRequestBuilders.patch("/api/v1/goals/" + id)
        .header("Authorization", token(owner)).contentType(MediaType.APPLICATION_JSON)
        .content(body));
  }

  private ResultActions regenerate(LoginResponse owner, String id) throws Exception {
    return mvc.perform(post("/api/v1/goals/" + id + "/profile")
        .header("Authorization", token(owner)));
  }

  private String token(LoginResponse owner) {
    return "Bearer " + owner.accessToken();
  }

  private LoginResponse desktop() {
    String id = "profile-edit-" + UUID.randomUUID();
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), id,
        "http://127.0.0.1:49152", new DesktopDevice(id, "QA", "MACOS")));
  }
}
