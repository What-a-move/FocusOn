package com.capstone.backend.goal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.capstone.backend.auth.dto.AuthDtos.DesktopDevice;
import com.capstone.backend.auth.dto.AuthDtos.GoogleExchangeRequest;
import com.capstone.backend.auth.dto.AuthDtos.LoginResponse;
import com.capstone.backend.auth.service.GoogleOauthClient;
import com.capstone.backend.auth.service.LoginService;
import com.capstone.backend.common.api.RequestIdFilter;
import com.capstone.backend.goal.persistence.GoalEntity;
import com.capstone.backend.goal.persistence.GoalEntity.ConfirmationSource;
import com.capstone.backend.goal.service.GoalAiClient;
import com.capstone.backend.goal.service.GoalProfile;
import com.capstone.backend.session.persistence.SessionGoalEntity;
import java.time.Instant;
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

/**
 * 목표 다듬기 응답에는 세분화가 나오지 않고, 확정 목표에 저장되고, 세션에 고정되는지 본다. 여기서는
 * 세분화 AI(profile)가 응답하지 않으므로(목) 직접 입력·문구 수정 목표의 세분화는 비어 있다(#314).
 */
@SpringBootTest
@ActiveProfiles({"local", "test"})
class GoalProfileTests {
  private static final String PROFILE = "{\"originalText\":\"리액트 공부\","
      + "\"interpretedGoal\":\"리액트 공부\",\"mainTopic\":\"React\","
      + "\"purpose\":\"CONCEPT_LEARNING\",\"coreTopics\":[\"컴포넌트\",\"훅\"],"
      + "\"supportingTopics\":[\"JavaScript\"],"
      + "\"expectedActivities\":[\"공식 문서 읽기\",\"오류 검색\"],\"profileSource\":\"AI_ASSISTED\"}";
  @Autowired private WebApplicationContext context;
  @Autowired private LoginService login;
  @Autowired private ObjectMapper json;
  @Autowired private JdbcTemplate jdbc;
  @MockitoBean private GoogleOauthClient google;
  @MockitoBean private GoalAiClient ai;
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity())
        .addFilters(new RequestIdFilter()).build();
    when(google.exchange(any())).thenAnswer(invocation -> "profile-" + UUID.randomUUID());
  }

  @Test
  void clearGoalProfileIsStoredAndOldProfileDropsOnTextChange() throws Exception {
    var owner = desktop();
    aiReturns(PROFILE);
    String sessionId = clarify(owner)
        .andExpect(jsonPath("$.data.clarityStatus").value("CLEAR"))
        .andExpect(jsonPath("$.data.goalProfileDraft").doesNotExist())
        .andExpect(jsonPath("$.data.profile").doesNotExist())
        .andReturn().getResponse().getContentAsString();
    String goalId = create(owner, ConfirmationSource.AI_CONFIRMED,
        json.readTree(sessionId).path("data").path("clarificationSessionId").asText());
    JsonNode stored = json.readTree(profileJson(goalId));
    assertEquals("React", stored.path("mainTopic").asText());
    assertEquals("CONCEPT_LEARNING", stored.path("purpose").asText());
    assertEquals(2, stored.path("coreTopics").size());
    assertEquals("오류 검색", stored.path("expectedActivities").get(1).asText());
    assertTrue(stored.path("originalText").isMissingNode());
    mvc.perform(patch("/api/v1/goals/" + goalId)
            .header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content("{\"goalText\":\"뷰 공부\"}"))
        .andExpect(status().isOk());
    assertNull(profileJson(goalId));
  }

  @Test
  void invalidDraftAndFailedExpansionStoreNoProfileButGoalIsCreated() throws Exception {
    var owner = desktop();
    aiReturns(PROFILE.replace("\"CONCEPT_LEARNING\"", "\"UNKNOWN_PURPOSE\""));
    String body = clarify(owner).andReturn().getResponse().getContentAsString();
    String goalId = create(owner, ConfirmationSource.AI_CONFIRMED,
        json.readTree(body).path("data").path("clarificationSessionId").asText());
    assertNull(profileJson(goalId));
    assertNull(profileJson(create(owner, ConfirmationSource.MANUAL_CONFIRMED, null)));
  }

  @Test
  void sessionFreezesProfileAtStart() {
    var profile = GoalProfile.fromDraft(json.readTree(PROFILE));
    var goal = GoalEntity.create(null, "리액트 공부", ConfirmationSource.AI_CONFIRMED,
        Instant.now());
    goal.attachProfile(json.writeValueAsString(profile));
    var snapshot = SessionGoalEntity.snapshot(10L, 1, goal);
    goal.updateText("뷰 공부", Instant.now());
    assertEquals(json.writeValueAsString(profile), snapshot.getGoalProfileSnapshotJson());
    assertEquals("리액트 공부", snapshot.getGoalTextSnapshot());
    // 세션 하나에 담을 수 있는 목표는 최대 3개다.
    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
        () -> SessionGoalEntity.snapshot(10L, 4, goal));
  }

  @Test
  void draftValidationRejectsOverflowDuplicatesAndBlankItems() {
    assertNull(GoalProfile.fromDraft(json.readTree(PROFILE.replace("[\"JavaScript\"]",
        "[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\"]"))));
    assertNull(GoalProfile.fromDraft(json.readTree(PROFILE.replace("[\"컴포넌트\",\"훅\"]",
        "[\"훅\",\"훅\"]"))));
    assertNull(GoalProfile.fromDraft(json.readTree(PROFILE.replace("\"JavaScript\"", "\" \""))));
    assertNull(GoalProfile.fromDraft(json.readTree("null")));
    var empty = GoalProfile.fromDraft(json.readTree("{\"mainTopic\":\"React\","
        + "\"purpose\":\"OTHER\"}"));
    assertEquals(0, empty.coreTopics().size());
  }

  private void aiReturns(String profile) {
    when(ai.clarify(any())).thenReturn(json.readTree("{\"data\":{\"clarityStatus\":\"CLEAR\","
        + "\"interpretedGoal\":\"리액트 공부\",\"recommendedGoals\":[],\"question\":null,"
        + "\"goalProfileDraft\":" + profile + "}}"));
  }

  private org.springframework.test.web.servlet.ResultActions clarify(LoginResponse owner)
      throws Exception {
    return mvc.perform(post("/api/v1/goals/clarify")
            .header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"goalText\":\"리액트 공부\",\"conversationTurn\":0,"
                + "\"previousAnswers\":[]}"))
        .andExpect(status().isOk());
  }

  private String create(LoginResponse owner, ConfirmationSource source, String sessionId)
      throws Exception {
    String body = "{\"goalText\":\"리액트 공부\",\"confirmationSource\":\"" + source + "\""
        + (sessionId == null ? "" : ",\"clarificationSessionId\":\"" + sessionId + "\"") + "}";
    String response = mvc.perform(post("/api/v1/goals")
            .header("Authorization", "Bearer " + owner.accessToken())
            .contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    return json.readTree(response).path("data").path("goalId").asText();
  }

  private String profileJson(String goalId) {
    return jdbc.queryForObject("SELECT profile_json FROM goals WHERE id = ?", String.class,
        Long.valueOf(goalId));
  }

  private LoginResponse desktop() {
    String id = "profile-" + UUID.randomUUID();
    return login.exchange(new GoogleExchangeRequest("code", "a".repeat(43), id,
        "http://127.0.0.1:49152", new DesktopDevice(id, "QA", "MACOS")));
  }
}
