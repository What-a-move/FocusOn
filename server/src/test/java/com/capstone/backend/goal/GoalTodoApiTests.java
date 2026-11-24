package com.capstone.backend.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.persistence.UserEntity.OnboardingStatus;
import com.capstone.backend.auth.persistence.UserRepository;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.goal.persistence.GoalRepository;
import com.capstone.backend.session.persistence.LearningSessionEntity;
import com.capstone.backend.session.persistence.LearningSessionRepository;
import com.capstone.backend.user.service.OnboardingReadinessService;
import com.capstone.backend.user.service.OnboardingReadinessService.Readiness;
import com.capstone.backend.user.service.OnboardingReadinessService.Steps;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 할 일 목록 조회와 할 일 상태 변경을 검증한다(#313). */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class GoalTodoApiTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private GoalRepository goals;
  @Autowired private LearningSessionRepository sessions;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private OnboardingReadinessService onboarding;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "goal-todo-" + UUID.randomUUID());
    when(onboarding.get(any(), isNull())).thenReturn(new Readiness(OnboardingStatus.COMPLETED,
        "COMPLETED", new Steps(true, true, true, true, true)));
  }

  @Test
  void repeatedGoalCreationReturnsTheSameGoal() throws Exception {
    LoginResponse owner = desktop("goal-replay");
    String key = UUID.randomUUID().toString();
    String body = "{\"goalText\":\"테스트 로그 분석\","
        + "\"confirmationSource\":\"MANUAL_CONFIRMED\"}";
    var first = mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn();
    var replay = mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn();
    JsonNode firstGoal = json.readTree(first.getResponse().getContentAsString()).path("data");
    JsonNode replayedGoal = json.readTree(replay.getResponse().getContentAsString()).path("data");
    assertEquals(firstGoal.path("goalId"), replayedGoal.path("goalId"));
    mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
            .content(body.replace("테스트 로그 분석", "서버 테스트 실행")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    assertEquals(1, list(owner, "").path("items").size());
  }

  @Test
  void listsOwnOpenAndDoneGoalsNewestFirstWithCursor() throws Exception {
    LoginResponse owner = desktop("todo-owner");
    LoginResponse other = desktop("todo-other");
    final String first = createGoal(owner, "첫 할 일");
    String second = createGoal(owner, "둘째 할 일");
    final String third = createGoal(owner, "셋째 할 일");
    String deleted = createGoal(owner, "지운 할 일");
    createGoal(other, "남의 할 일");
    mvc.perform(delete("/api/v1/goals/" + deleted).header("Authorization", token(owner)))
        .andExpect(status().isNoContent());
    mvc.perform(patch("/api/v1/goals/" + second).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"todoStatus\":\"DONE\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.todoStatus").value("DONE"))
        .andExpect(jsonPath("$.data.goalText").value("둘째 할 일"));

    JsonNode page = list(owner, "?limit=2");
    assertEquals(2, page.path("items").size());
    assertEquals(third, page.path("items").path(0).path("goalId").asString());
    assertEquals("OPEN", page.path("items").path(0).path("todoStatus").asString());
    assertEquals(second, page.path("items").path(1).path("goalId").asString());
    JsonNode rest = list(owner, "?limit=2&cursor=" + page.path("nextCursor").asString());
    assertEquals(1, rest.path("items").size());
    assertEquals(first, rest.path("items").path(0).path("goalId").asString());
    assertEquals(true, rest.path("nextCursor").isNull());

    JsonNode open = list(owner, "?todoStatus=OPEN");
    assertEquals(2, open.path("items").size());
    JsonNode done = list(owner, "?todoStatus=DONE");
    assertEquals(1, done.path("items").size());
    assertEquals(second, done.path("items").path(0).path("goalId").asString());

    for (String bad : new String[] {"?limit=0", "?limit=51", "?cursor=abc", "?cursor=0",
        "?todoStatus=FINISHED"}) {
      mvc.perform(get("/api/v1/goals" + bad).header("Authorization", token(owner)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    mvc.perform(get("/api/v1/goals")).andExpect(status().isUnauthorized());
  }

  @Test
  void activeSessionAllowsOnlyTodoStatusChange() throws Exception {
    LoginResponse owner = desktop("todo-session");
    String id = createGoal(owner, "세션 할 일");
    var user = users.findById(Long.valueOf(owner.user().userId())).orElseThrow();
    var device = devices.findById(Long.valueOf(owner.device().deviceId())).orElseThrow();
    var goal = goals.findById(Long.valueOf(id)).orElseThrow();
    Instant now = Instant.now();
    sessions.saveAndFlush(LearningSessionEntity.create(user, goal, device, 1800000, "Asia/Seoul",
        now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), "{}", "v1", now));
    String path = "/api/v1/goals/" + id;
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"todoStatus\":\"DONE\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.data.todoStatus").value("DONE"));
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"바꾼 할 일\",\"todoStatus\":\"OPEN\"}"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOAL_UPDATE_BLOCKED"));
    assertEquals("DONE", goals.findById(Long.valueOf(id)).orElseThrow().getTodoStatus().name());
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isConflict());
    mvc.perform(patch(path).header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content("{\"todoStatus\":\"LATER\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  private JsonNode list(LoginResponse owner, String query) throws Exception {
    String body = mvc.perform(get("/api/v1/goals" + query).header("Authorization", token(owner)))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    return json.readTree(body).path("data");
  }

  private String createGoal(LoginResponse owner, String text) throws Exception {
    String response = mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"" + text + "\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.todoStatus").value("OPEN"))
        .andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("data").path("goalId").asString();
  }

  private String token(LoginResponse owner) {
    return "Bearer " + owner.accessToken();
  }

  private LoginResponse desktop(String installationId) {
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), installationId,
        "http://127.0.0.1:49152", new DesktopDevice(installationId, "QA", "MACOS")));
  }
}
