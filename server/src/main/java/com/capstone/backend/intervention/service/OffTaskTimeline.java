package com.capstone.backend.intervention.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.ContentSource;
import com.capstone.backend.analysis.dto.AnalysisDtos.Relation;
import com.capstone.backend.user.persistence.UserFocusSettingsEntity.InterventionLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 서버 확정 시간만 세며 처리 중과 확정 UNCERTAIN을 분리하는 순수 정책 상태다. */
public final class OffTaskTimeline {
  private static final Duration WINDOW = Duration.ofMinutes(10);
  /** 판단 보류·분석 장애가 딴짓 구간을 끊지 않고 멈추게 하는 정책 버전이다(#296). */
  public static final String POLICY_V2 = "v2";
  /** 휴식 제안: 이탈 시작 사이 최대 간격과 최소 반복 횟수(설계서 8.1). */
  private static final Duration BREAK_GAP = Duration.ofMinutes(5);
  private static final int BREAK_REPEATS = 3;
  private final Snapshot state;

  /** DB snapshot 또는 새 실행/context의 빈 상태로 시작한다. */
  public OffTaskTimeline(Snapshot state) {
    this.state = Objects.requireNonNull(state);
  }

  /** 실행 세대와 명시적으로 선택한 context는 외부 권한 검증 후 제공한다. */
  public static OffTaskTimeline empty(Instant runningSince, String context, Instant now) {
    return new OffTaskTimeline(new Snapshot(runningSince, context, now, 0, null,
        null, null, null, null, List.of(), null));
  }

  /** 최신 요청만 호출한다. 재분석 대기는 같은 구간을 보존하고 누적/발송만 멈춘다. */
  public OffTaskTimeline pending(long runId, Instant at) {
    if (Objects.equals(state.pendingRun(), runId)) {
      return this;
    }
    Instant now = monotonic(at);
    var spans = closeSpan(now);
    return copy(now, state.episode(), runId, state.latestRun(), state.completedAt(),
        state.latestReceivedAt(), state.openEpisode(), spans);
  }

  /** 처리 중인 최신 요청의 확정 결과만 반영한다. 늦은 완료는 무시한다. */
  public OffTaskTimeline complete(long runId, Relation relation, ContentSource source,
      Instant at) {
    return complete(runId, relation, source, at, at);
  }

  /** 완료 시각과 실제 서버 접수 시각을 분리해 cooldown 이후 새 관측을 검증한다(v1 규칙). */
  public OffTaskTimeline complete(long runId, Relation relation, ContentSource source,
      Instant receivedAt, Instant at) {
    return complete(runId, relation, source, receivedAt, at, "v1");
  }

  /**
   * 세션 정책 버전에 맞춰 확정 결과를 반영한다. v2에서는 판단 보류(분석 장애·제목만 본 무관 판정
   * 포함)가 열린 딴짓 구간을 닫지 않고 멈춘다. 그 시간은 쌓지도 빼지도 않고, 다음 무관 판정이
   * 같은 구간을 이어 센다. 관련 판정과 보호 상태는 지금처럼 구간을 끊는다.
   */
  public OffTaskTimeline complete(long runId, Relation relation, ContentSource source,
      Instant receivedAt, Instant at, String policyVersion) {
    if (!Objects.equals(state.pendingRun(), runId)) {
      return this;
    }
    Instant now = monotonic(at);
    Relation effective = relation == Relation.UNRELATED
        && (source == ContentSource.APP_META || source == ContentSource.TAB_META)
        ? Relation.UNCERTAIN : relation;
    if (effective != Relation.UNRELATED) {
      Long paused = POLICY_V2.equals(policyVersion) && effective == Relation.UNCERTAIN
          ? state.openEpisode() : null;
      return copy(now, state.episode(), null, runId, now, receivedAt, paused, closeSpan(now));
    }
    long episode = state.openEpisode() == null ? state.episode() + 1 : state.openEpisode();
    var spans = new ArrayList<>(state.spans());
    spans.add(new Span(episode, now, null));
    return copy(now, episode, null, runId, now, receivedAt, episode, spans, runId);
  }

  /** 보호/미관찰/실행 또는 context 전이는 처리 중 여부와 무관하게 구간을 끊는다. */
  public OffTaskTimeline interrupt(Instant at) {
    Instant now = monotonic(at);
    return copy(now, state.episode(), null, state.latestRun(), state.completedAt(),
        state.latestReceivedAt(), null, closeSpan(now));
  }

  /** 명시적 전경 전환은 현재 구간을 끊되 지난 반복 구간은 보존한다. */
  public OffTaskTimeline switchContext(String context, Instant at) {
    var closed = interrupt(at).snapshot();
    return new OffTaskTimeline(new Snapshot(closed.runningSince(), context, closed.changedAt(),
        closed.episode(), null, null, null, null, null, closed.spans(), null));
  }

  /** 같은 서버 시각에서 반복 호출해도 구간 횟수가 늘지 않는 정책 평가다. */
  public Evaluation evaluate(String policyVersion, InterventionLevel level, Instant at,
      Instant cooldownUntil) {
    boolean known = "v1".equals(policyVersion) || POLICY_V2.equals(policyVersion);
    if (!known || level == null || state.pendingRun() != null
        || state.openEpisode() == null) {
      return new Evaluation(false, Duration.ZERO, 0);
    }
    Instant now = monotonic(at);
    Duration continuous = duration(state.openEpisode(), Instant.MIN, now);
    long[] thresholds = thresholds(level);
    Instant left = now.minus(WINDOW);
    long repeats = state.spans().stream().map(Span::episode).distinct()
        .filter(episode -> duration(episode, left, now)
            .compareTo(Duration.ofSeconds(thresholds[1])) > 0).count();
    boolean matches = continuous.compareTo(Duration.ofSeconds(thresholds[0])) > 0
        || repeats >= thresholds[2];
    // 판단 보류로 멈춘 동안은 지금 화면이 딴짓인지 모르므로 시간은 보고하되 알림 후보는 아니다.
    return new Evaluation(cooled(now, cooldownUntil) && matches && !onHold(), continuous,
        repeats);
  }

  /**
   * 휴식 제안 근거를 계산한다(설계서 「학습 세션 · 타이머 · 휴식 제안」 8.1, #301). 강도별 반복
   * 기준을 넘긴 딴짓 구간을 이탈 1회로 보고, 지금 이탈까지 시작 시각이 서로 5분 이내로 이어진 이탈이
   * 3회 이상이며 알림 간격이 지났을 때만 근거를 돌려준다. 판단 보류·보호 화면 시간은 구간
   * 시간에 들어가지 않으므로 근거로 쓰이지 않는다.
   */
  public java.util.Optional<BreakEvidence> breakEvidence(String policyVersion,
      InterventionLevel level, Instant at, Instant cooldownUntil) {
    boolean known = "v1".equals(policyVersion) || POLICY_V2.equals(policyVersion);
    // 판단 보류로 멈춘 동안(onHold)에는 지금 딴짓 중인지 모르므로 휴식 제안도 만들지 않는다.
    if (!known || level == null || state.pendingRun() != null || state.openEpisode() == null
        || onHold()) {
      return java.util.Optional.empty();
    }
    Instant now = monotonic(at);
    if (!cooled(now, cooldownUntil)) {
      return java.util.Optional.empty();
    }
    Duration repeat = Duration.ofSeconds(thresholds(level)[1]);
    // 사슬은 지금 이탈에서 끝나야 한다. 지금 이탈이 아직 짧으면 몇 분 전에 끝난 이탈들만으로
    // 새 화면을 본 직후 바로 제안하게 되므로, 지금 이탈이 반복 기준을 넘길 때까지 기다린다.
    if (duration(state.openEpisode(), Instant.MIN, now).compareTo(repeat) <= 0) {
      return java.util.Optional.empty();
    }
    List<Instant> starts = state.spans().stream().map(Span::episode).distinct()
        .filter(episode -> duration(episode, Instant.MIN, now).compareTo(repeat) > 0)
        .map(episode -> state.spans().stream().filter(span -> span.episode() == episode)
            .map(Span::start).min(Instant::compareTo).orElseThrow())
        .sorted().toList();
    if (starts.isEmpty()) {
      return java.util.Optional.empty();
    }
    int chain = 1;
    for (int index = starts.size() - 1; index > 0; index--) {
      if (Duration.between(starts.get(index - 1), starts.get(index)).compareTo(BREAK_GAP) > 0) {
        break;
      }
      chain++;
    }
    if (chain < BREAK_REPEATS) {
      return java.util.Optional.empty();
    }
    Instant first = starts.get(starts.size() - chain);
    long minutes = Math.max(1, (Duration.between(first, now).toSeconds() + 59) / 60);
    return java.util.Optional.of(new BreakEvidence(chain, (int) minutes));
  }

  private static long[] thresholds(InterventionLevel level) {
    return switch (level) {
      case GENTLE -> new long[] {180, 60, 3};
      case NORMAL -> new long[] {60, 30, 2};
      case ACTIVE -> new long[] {30, 20, 2};
    };
  }

  private boolean cooled(Instant now, Instant cooldownUntil) {
    return cooldownUntil == null || (!now.isBefore(cooldownUntil)
        && state.latestReceivedAt() != null && !state.latestReceivedAt().isBefore(cooldownUntil));
  }

  /**
   * 열린 딴짓 구간은 있지만 지금 흐르는 시간 조각이 없는 상태다. v2에서 판단 보류로 멈췄거나
   * 재분석을 기다리는 중이다. 지금 화면이 딴짓인지 모르므로 새 알림을 만들지 않는다.
   */
  public boolean onHold() {
    return state.openEpisode() != null
        && state.spans().stream().noneMatch(span -> span.end() == null);
  }

  /** 원문 없는 영속화용 snapshot이다. */
  public Snapshot snapshot() {
    return state;
  }

  private Instant monotonic(Instant now) {
    return now.isBefore(state.changedAt()) ? state.changedAt() : now;
  }

  private Duration duration(long episode, Instant from, Instant to) {
    Duration total = Duration.ZERO;
    for (var span : state.spans()) {
      if (span.episode() == episode) {
        Instant start = span.start().isBefore(from) ? from : span.start();
        Instant end = span.end() == null || span.end().isAfter(to) ? to : span.end();
        if (end.isAfter(start)) {
          total = total.plus(Duration.between(start, end));
        }
      }
    }
    return total;
  }

  private List<Span> closeSpan(Instant now) {
    return state.spans().stream().map(span -> span.end() == null
        ? new Span(span.episode(), span.start(), now) : span).toList();
  }

  /** 열린 구간이 이어지면 그 구간의 마지막 무관 판정 id를 유지하고, 끝나면 지운다. */
  private OffTaskTimeline copy(Instant now, long episode, Long pending, Long latest,
      Instant completed, Instant received, Long open, List<Span> spans) {
    return copy(now, episode, pending, latest, completed, received, open, spans,
        open == null ? null : state.offTaskRun());
  }

  private OffTaskTimeline copy(Instant now, long episode, Long pending, Long latest,
      Instant completed, Instant received, Long open, List<Span> spans, Long offTaskRun) {
    // 과거 닫힌 구간은 10분 평가에 필요하지 않지만 열린 구간의 연속 누적은 보존한다.
    var retained = spans.stream().filter(span -> span.end() == null
        || Objects.equals(span.episode(), open) || span.end().isAfter(now.minus(WINDOW)))
        .toList();
    return new OffTaskTimeline(new Snapshot(state.runningSince(), state.context(), now,
        episode, pending, latest, completed, received, open, retained, offTaskRun));
  }

  /** 같은 구간의 분석 대기 전후 확정 시간 조각을 구간 ID로 묶는다. */
  public record Span(long episode, Instant start, Instant end) {}

  /**
   * 식별자와 서버 시각만 보존한다. request/metadata/content는 포함하지 않는다.
   * offTaskRun은 열린 딴짓 구간을 마지막으로 이어 준 무관 판정 id다. 판단 보류로 멈춘 동안에도
   * 유지돼 알림의 근거 판정이 된다. 이전에 저장된 snapshot에는 없으므로 null일 수 있다.
   */
  public record Snapshot(Instant runningSince, String context, Instant changedAt,
      long episode, Long pendingRun, Long latestRun, Instant completedAt, Instant latestReceivedAt,
      Long openEpisode,
      List<Span> spans, Long offTaskRun) {
    /** snapshot은 변경 가능한 목록 참조를 보유하지 않는다. */
    public Snapshot {
      Objects.requireNonNull(runningSince);
      Objects.requireNonNull(context);
      Objects.requireNonNull(changedAt);
      spans = List.copyOf(spans);
    }
  }

  /** 휴식 제안 근거: 이어진 이탈 횟수와 첫 이탈부터 지금까지의 분(올림). 민감값은 없다. */
  public record BreakEvidence(int repeatCount, int windowMinutes) {}

  /** 확정된 시간과 서로 다른 구간 수를 후보 여부와 함께 반환한다. */
  public record Evaluation(boolean candidate, Duration continuous, long repeatedIntervals) {}
}
