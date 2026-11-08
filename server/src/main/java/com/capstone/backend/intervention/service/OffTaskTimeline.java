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
  private final Snapshot state;

  /** DB snapshot 또는 새 실행/context의 빈 상태로 시작한다. */
  public OffTaskTimeline(Snapshot state) {
    this.state = Objects.requireNonNull(state);
  }

  /** 실행 세대와 명시적으로 선택한 context는 외부 권한 검증 후 제공한다. */
  public static OffTaskTimeline empty(Instant runningSince, String context, Instant now) {
    return new OffTaskTimeline(new Snapshot(runningSince, context, now, 0, null,
        null, null, null, List.of()));
  }

  /** 최신 요청만 호출한다. 재분석 대기는 같은 구간을 보존하고 누적/발송만 멈춘다. */
  public OffTaskTimeline pending(long runId, Instant at) {
    if (Objects.equals(state.pendingRun(), runId)) {
      return this;
    }
    Instant now = monotonic(at);
    var spans = closeSpan(now);
    return copy(now, state.episode(), runId, state.latestRun(), state.completedAt(),
        state.openEpisode(), spans);
  }

  /** 처리 중인 최신 요청의 확정 결과만 반영한다. 늦은 완료는 무시한다. */
  public OffTaskTimeline complete(long runId, Relation relation, ContentSource source,
      Instant at) {
    if (!Objects.equals(state.pendingRun(), runId)) {
      return this;
    }
    Instant now = monotonic(at);
    Relation effective = relation == Relation.UNRELATED
        && (source == ContentSource.APP_META || source == ContentSource.TAB_META)
        ? Relation.UNCERTAIN : relation;
    if (effective != Relation.UNRELATED) {
      return copy(now, state.episode(), null, runId, now, null, closeSpan(now));
    }
    long episode = state.openEpisode() == null ? state.episode() + 1 : state.openEpisode();
    var spans = new ArrayList<>(state.spans());
    spans.add(new Span(episode, now, null));
    return copy(now, episode, null, runId, now, episode, spans);
  }

  /** 보호/미관찰/실행 또는 context 전이는 처리 중 여부와 무관하게 구간을 끊는다. */
  public OffTaskTimeline interrupt(Instant at) {
    Instant now = monotonic(at);
    return copy(now, state.episode(), null, state.latestRun(), state.completedAt(),
        null, closeSpan(now));
  }

  /** 명시적 전경 전환은 현재 구간을 끊되 지난 반복 구간은 보존한다. */
  public OffTaskTimeline switchContext(String context, Instant at) {
    var closed = interrupt(at).snapshot();
    return new OffTaskTimeline(new Snapshot(closed.runningSince(), context, closed.changedAt(),
        closed.episode(), null, null, null, null, closed.spans()));
  }

  /** 같은 서버 시각에서 반복 호출해도 구간 횟수가 늘지 않는 정책 평가다. */
  public Evaluation evaluate(String policyVersion, InterventionLevel level, Instant at,
      Instant cooldownUntil) {
    if (!"v1".equals(policyVersion) || level == null || state.pendingRun() != null
        || state.openEpisode() == null) {
      return new Evaluation(false, Duration.ZERO, 0);
    }
    Instant now = monotonic(at);
    Duration continuous = duration(state.openEpisode(), Instant.MIN, now);
    long[] thresholds = switch (level) {
      case GENTLE -> new long[] {180, 60, 3};
      case NORMAL -> new long[] {60, 30, 2};
      case ACTIVE -> new long[] {30, 20, 2};
    };
    Instant left = now.minus(WINDOW);
    long repeats = state.spans().stream().map(Span::episode).distinct()
        .filter(episode -> duration(episode, left, now)
            .compareTo(Duration.ofSeconds(thresholds[1])) > 0).count();
    boolean cooled = cooldownUntil == null || (!now.isBefore(cooldownUntil)
        && state.completedAt() != null && !state.completedAt().isBefore(cooldownUntil));
    boolean matches = continuous.compareTo(Duration.ofSeconds(thresholds[0])) > 0
        || repeats >= thresholds[2];
    return new Evaluation(cooled && matches, continuous, repeats);
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

  private OffTaskTimeline copy(Instant now, long episode, Long pending, Long latest,
      Instant completed, Long open, List<Span> spans) {
    // 과거 닫힌 구간은 10분 평가에 필요하지 않지만 열린 구간의 연속 누적은 보존한다.
    var retained = spans.stream().filter(span -> span.end() == null
        || Objects.equals(span.episode(), open) || span.end().isAfter(now.minus(WINDOW)))
        .toList();
    return new OffTaskTimeline(new Snapshot(state.runningSince(), state.context(), now,
        episode, pending, latest, completed, open, retained));
  }

  /** 같은 구간의 분석 대기 전후 확정 시간 조각을 구간 ID로 묶는다. */
  public record Span(long episode, Instant start, Instant end) {}

  /** 식별자와 서버 시각만 보존한다. request/metadata/content는 포함하지 않는다. */
  public record Snapshot(Instant runningSince, String context, Instant changedAt,
      long episode, Long pendingRun, Long latestRun, Instant completedAt, Long openEpisode,
      List<Span> spans) {
    /** snapshot은 변경 가능한 목록 참조를 보유하지 않는다. */
    public Snapshot {
      Objects.requireNonNull(runningSince);
      Objects.requireNonNull(context);
      Objects.requireNonNull(changedAt);
      spans = List.copyOf(spans);
    }
  }

  /** 확정된 시간과 서로 다른 구간 수를 후보 여부와 함께 반환한다. */
  public record Evaluation(boolean candidate, Duration continuous, long repeatedIntervals) {}
}
