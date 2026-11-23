package com.capstone.backend.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.util.Map;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 모든 목표 세분화, 응답의 goalProfile, 수정 시 재확장·덮어쓰기를 검증한다(#314). */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class GoalProfileExpandTests {
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private UserRepository users;
  @Autowired private UserDeviceRepository devices;
  @Autowired private GoalRepository goals;
  @Autowired private LearningSessionRepository sessions;
  @Autowired private com.capstone.backend.goal.persistence.GoalClarificationRepository
      clarifications;
  @Autowired private org.springframework.transaction.support.TransactionTemplate transactions;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private GoalAiClient ai;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "expand-" + UUID.randomUUID());
    // 세분화 AI는 받은 문구를 그대로 주제로 돌려준다.
    when(ai.profile(any())).thenAnswer(invocation -> {
      Map<?, ?> body = invocation.getArgument(0);
      return draft(body.get("goalText").toString());
    });
  }

  @Test
  void manualGoalIsExpandedAndReturned() throws Exception {
    var owner = desktop();
    JsonNode created = create(owner, "리액트 훅 공부");
    assertEquals("리액트 훅 공부", created.path("goalProfile").path("mainTopic").asString());
    assertEquals("CONCEPT_LEARNING", created.path("goalProfile").path("purpose").asString());
    String id = created.path("goalId").asString();
    assertEquals("리액트 훅 공부", json.readTree(profileJson(id)).path("mainTopic").asString());
    mvc.perform(get("/api/v1/goals/" + id).header("Authorization", token(owner)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalProfile.coreTopics[0]").value("핵심"));
  }

  @Test
  void failedExpansionStillCreatesGoalWithNullProfile() throws Exception {
    doThrow(new IllegalStateException("down")).when(ai).profile(any());
    var owner = desktop();
    JsonNode created = create(owner, "알고리즘 문제 풀기");
    assertEquals(true, created.path("goalProfile").isNull());
    assertNull(profileJson(created.path("goalId").asString()));
  }

  @Test
  void textChangeReexpandsAndSubmittedProfileWins() throws Exception {
    var owner = desktop();
    String id = create(owner, "리액트 공부").path("goalId").asString();
    patch(owner, id, "{\"goalText\":\"뷰 공부\"}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("뷰 공부"));
    assertEquals("뷰 공부", json.readTree(profileJson(id)).path("mainTopic").asString());

    String submitted = "{\"mainTopic\":\"직접 정한 주제\",\"purpose\":\"PRACTICE\","
        + "\"coreTopics\":[\"연습\"],\"supportingTopics\":[],\"expectedActivities\":[]}";
    patch(owner, id, "{\"goalText\":\"스벨트 공부\",\"goalProfile\":" + submitted + "}")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalText").value("스벨트 공부"))
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("직접 정한 주제"));
    verify(ai, never()).profile(argThat(body -> "스벨트 공부".equals(body.get("goalText"))));
    assertEquals("직접 정한 주제", json.readTree(profileJson(id)).path("mainTopic").asString());

    patch(owner, id, "{\"goalProfile\":{\"mainTopic\":\"x\",\"purpose\":\"NOPE\"}}")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    assertEquals("직접 정한 주제", json.readTree(profileJson(id)).path("mainTopic").asString());
  }

  @Test
  void staleExpansionIsDiscardedWhenTextChangedMeanwhile() throws Exception {
    var owner = desktop();
    String id = create(owner, "처음 문구").path("goalId").asString();
    doAnswer(invocation -> {
      // 세분화를 기다리는 사이 다른 요청이 문구를 바꾼 상황이다.
      jdbc.update("UPDATE goals SET goal_text = '나중 문구', profile_json = NULL WHERE id = ?",
          Long.valueOf(id));
      return draft("바뀌기 전 문구");
    }).when(ai).profile(any());
    patch(owner, id, "{\"goalText\":\"바뀌기 전 문구\"}").andExpect(status().isOk())
        .andExpect(jsonPath("$.data.goalText").value("나중 문구"));
    assertNull(profileJson(id));
  }

  @Test
  void activeSessionBlocksProfileChangeAndAiConfirmedSkipsExpansion() throws Exception {
    var owner = desktop();
    String id = create(owner, "세션 목표").path("goalId").asString();
    var user = users.findById(Long.valueOf(owner.user().userId())).orElseThrow();
    var device = devices.findById(Long.valueOf(owner.device().deviceId())).orElseThrow();
    Instant now = Instant.now();
    sessions.saveAndFlush(LearningSessionEntity.create(user,
        goals.findById(Long.valueOf(id)).orElseThrow(), device, 1800000, "Asia/Seoul",
        now.atZone(ZoneId.of("Asia/Seoul")).toLocalDate(), "{}", "v1", now));
    patch(owner, id, "{\"goalProfile\":{\"mainTopic\":\"새 주제\",\"purpose\":\"OTHER\"}}")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("GOAL_UPDATE_BLOCKED"));

    when(ai.clarify(any())).thenReturn(json.readTree("{\"data\":{\"clarityStatus\":\"CLEAR\","
        + "\"interpretedGoal\":\"리액트 공부\",\"recommendedGoals\":[],\"question\":null,"
        + "\"goalProfileDraft\":{\"mainTopic\":\"React\",\"purpose\":\"CONCEPT_LEARNING\"}}}"));
    var other = desktop();
    String clarify = mvc.perform(post("/api/v1/goals/clarify").header("Authorization",
            token(other)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"리액트 공부\",\"conversationTurn\":0,"
                + "\"previousAnswers\":[]}"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String sessionId = json.readTree(clarify).path("data").path("clarificationSessionId")
        .asString();
    mvc.perform(post("/api/v1/goals").header("Authorization", token(other))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"리액트 공부\",\"confirmationSource\":\"AI_CONFIRMED\","
                + "\"clarificationSessionId\":\"" + sessionId + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("React"));
    verify(ai, never()).profile(argThat(body -> "리액트 공부".equals(body.get("goalText"))));
  }

  /** CLEAR와 함께 받은 후보 중 하나를 AI 확인 목표로 저장하면 그 문구로 세분화한다(#315). */
  @Test
  void candidateFromClearResponseCanBeSavedAndIsExpanded() throws Exception {
    when(ai.clarify(any())).thenReturn(json.readTree("{\"data\":{"
        + "\"clarityStatus\":\"NEEDS_SELECTION\",\"interpretedGoal\":null,"
        + "\"question\":{\"text\":\"하나를 고르세요\"},\"recommendedGoals\":["
        + "{\"title\":\"리액트 훅 공부\"},{\"title\":\"알고리즘 풀기\"}]}}"));
    var owner = desktop();
    String clarify = mvc.perform(post("/api/v1/goals/clarify").header("Authorization",
            token(owner)).contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"리액트랑 알고리즘\",\"conversationTurn\":0,"
                + "\"previousAnswers\":[]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clarityStatus").value("CLEAR"))
        .andExpect(jsonPath("$.data.goalCandidates[1]").value("알고리즘 풀기"))
        .andExpect(jsonPath("$.data.question").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    String sessionId = json.readTree(clarify).path("data").path("clarificationSessionId")
        .asString();
    mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"딴 목표\",\"confirmationSource\":\"AI_CONFIRMED\","
                + "\"clarificationSessionId\":\"" + sessionId + "\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"알고리즘 풀기\",\"confirmationSource\":\"AI_CONFIRMED\","
                + "\"clarificationSessionId\":\"" + sessionId + "\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.confirmationSource").value("AI_CONFIRMED"))
        .andExpect(jsonPath("$.data.goalProfile.mainTopic").value("알고리즘 풀기"));
  }

  /** 폐기 전에 저장된 NEEDS_SELECTION 상태: 다시 받으면 질문 없는 CLEAR이고 후보만 저장된다. */
  @Test
  void legacySelectionStateReplaysAsClearAndAllowsOnlyCandidates() throws Exception {
    when(ai.clarify(any())).thenReturn(json.readTree("{\"data\":{"
        + "\"clarityStatus\":\"NEEDS_SELECTION\",\"interpretedGoal\":null,"
        + "\"recommendedGoals\":[{\"title\":\"리액트 훅 공부\"},{\"title\":\"알고리즘 풀기\"}]}}"));
    var owner = desktop();
    String request = "{\"goalText\":\"리액트랑 알고리즘\",\"conversationTurn\":0,"
        + "\"previousAnswers\":[]}";
    String first = mvc.perform(post("/api/v1/goals/clarify").header("Authorization",
            token(owner)).contentType(MediaType.APPLICATION_JSON).content(request))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    String sessionId = json.readTree(first).path("data").path("clarificationSessionId")
        .asString();
    // 폐기 전 형식으로 저장된 상태를 흉내 낸다(상태 이름과 고르기 질문).
    transactions.executeWithoutResult(status -> {
      var stored = clarifications.findById(Long.valueOf(sessionId)).orElseThrow();
      var state = (tools.jackson.databind.node.ObjectNode) json.readTree(stored.getStateJson());
      var response = (tools.jackson.databind.node.ObjectNode) state.path("response");
      response.put("clarityStatus", "NEEDS_SELECTION");
      response.put("question", "하나를 고르세요");
      // 같은 대화 ID로 같은 요청을 다시 보내면 저장된 응답을 그대로 돌려주는 경로를 탄다.
      ((tools.jackson.databind.node.ObjectNode) state.path("request"))
          .put("clarificationSessionId", sessionId);
      stored.complete(json.writeValueAsString(state), 0, Instant.now());
    });
    String request2 = request.replace("\"conversationTurn\":0", "\"conversationTurn\":0,"
        + "\"clarificationSessionId\":\"" + sessionId + "\"");
    mvc.perform(post("/api/v1/goals/clarify").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON).content(request2))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.clarityStatus").value("CLEAR"))
        .andExpect(jsonPath("$.data.question").doesNotExist())
        .andExpect(jsonPath("$.data.goalCandidates[0]").value("리액트 훅 공부"));
    mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"null\",\"confirmationSource\":\"AI_CONFIRMED\","
                + "\"clarificationSessionId\":\"" + sessionId + "\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"리액트 훅 공부\",\"confirmationSource\":\"AI_CONFIRMED\","
                + "\"clarificationSessionId\":\"" + sessionId + "\"}"))
        .andExpect(status().isCreated());
  }

  private JsonNode draft(String topic) {
    return json.readTree("{\"data\":{\"goalProfile\":{\"mainTopic\":\"" + topic + "\","
        + "\"purpose\":\"CONCEPT_LEARNING\",\"coreTopics\":[\"핵심\"],"
        + "\"supportingTopics\":[],\"expectedActivities\":[\"문서 읽기\"]}}}");
  }

  private JsonNode create(LoginResponse owner, String text) throws Exception {
    String response = mvc.perform(post("/api/v1/goals").header("Authorization", token(owner))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"" + text + "\",\"confirmationSource\":\"MANUAL_CONFIRMED\"}"))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("data");
  }

  private org.springframework.test.web.servlet.ResultActions patch(LoginResponse owner,
      String id, String body) throws Exception {
    return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .patch("/api/v1/goals/" + id).header("Authorization", token(owner))
        .contentType(MediaType.APPLICATION_JSON).content(body));
  }

  private String profileJson(String goalId) {
    return jdbc.queryForObject("SELECT profile_json FROM goals WHERE id = ?", String.class,
        Long.valueOf(goalId));
  }

  private String token(LoginResponse owner) {
    return "Bearer " + owner.accessToken();
  }

  private LoginResponse desktop() {
    String id = "expand-" + UUID.randomUUID();
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), id,
        "http://127.0.0.1:49152", new DesktopDevice(id, "QA", "MACOS")));
  }
}
