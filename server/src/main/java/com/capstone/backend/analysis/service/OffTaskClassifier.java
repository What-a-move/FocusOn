package com.capstone.backend.analysis.service;

import com.capstone.backend.analysis.dto.AnalysisDtos.OffTaskCategory;
import com.capstone.backend.analysis.dto.AnalysisDtos.Request;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * UNRELATED 판정의 딴짓 종류를 고정 도메인·앱 목록으로 정한다(리포트 설계 B). AI를 부르지 않고
 * host·앱 이름은 분류에만 쓰며 저장하지 않는다. 목록에 없으면 OTHER다.
 */
public final class OffTaskClassifier {
  private static final Map<String, OffTaskCategory> HOSTS = new HashMap<>();
  private static final Map<String, OffTaskCategory> APPS = new HashMap<>();

  static {
    hosts(OffTaskCategory.VIDEO, "youtube.com", "youtu.be", "netflix.com", "twitch.tv",
        "tiktok.com", "vimeo.com", "tving.com", "wavve.com", "watcha.com", "laftel.net",
        "coupangplay.com", "disneyplus.com", "chzzk.naver.com", "tv.naver.com",
        "afreecatv.com", "sooplive.co.kr", "tv.kakao.com");
    hosts(OffTaskCategory.SNS, "instagram.com", "facebook.com", "x.com", "twitter.com",
        "threads.net", "threads.com", "pinterest.com", "band.us", "tumblr.com", "bsky.app");
    hosts(OffTaskCategory.COMMUNITY, "reddit.com", "dcinside.com", "fmkorea.com",
        "ruliweb.com", "theqoo.net", "clien.net", "inven.co.kr", "ppomppu.co.kr",
        "instiz.net", "everytime.kr", "cafe.naver.com", "cafe.daum.net", "mlbpark.donga.com",
        "9gag.com", "arca.live", "bobaedream.co.kr");
    hosts(OffTaskCategory.SHOPPING, "coupang.com", "11st.co.kr", "gmarket.co.kr",
        "auction.co.kr", "musinsa.com", "amazon.com", "aliexpress.com", "temu.com",
        "shopping.naver.com", "smartstore.naver.com", "ssg.com", "29cm.co.kr", "kurly.com",
        "ebay.com", "zigzag.kr", "oliveyoung.co.kr");
    hosts(OffTaskCategory.GAME, "steampowered.com", "op.gg", "leagueoflegends.com",
        "roblox.com", "nexon.com", "battle.net", "epicgames.com", "chess.com", "poki.com",
        "minecraft.net", "playstation.com", "nintendo.com");
    hosts(OffTaskCategory.MESSENGER, "discord.com", "slack.com", "web.whatsapp.com",
        "messenger.com", "web.telegram.org", "teams.microsoft.com", "teams.live.com",
        "line.me");
    hosts(OffTaskCategory.NEWS, "news.naver.com", "n.news.naver.com", "news.daum.net",
        "v.daum.net", "yna.co.kr", "chosun.com", "joongang.co.kr", "donga.com", "hani.co.kr",
        "khan.co.kr", "mk.co.kr", "hankyung.com", "cnn.com", "bbc.com", "bbc.co.uk",
        "nytimes.com", "news.google.com");
    apps(OffTaskCategory.VIDEO, "netflix", "tv", "iina", "vlc", "disney+", "twitch");
    apps(OffTaskCategory.SNS, "instagram", "threads");
    apps(OffTaskCategory.GAME, "steam", "league of legends", "battle.net", "minecraft",
        "epic games launcher", "roblox");
    apps(OffTaskCategory.MESSENGER, "discord", "slack", "kakaotalk", "카카오톡",
        "microsoft teams", "teams", "telegram", "whatsapp", "line", "messages", "메시지",
        "messenger");
    apps(OffTaskCategory.NEWS, "news", "뉴스");
  }

  private OffTaskClassifier() {}

  /** 요청의 정규화 host를 먼저, 없으면 앱 이름으로 분류한다. */
  public static OffTaskCategory classify(Request request) {
    String host = AnalysisPrivacy.host(request);
    if (!host.isEmpty()) {
      return byHost(host);
    }
    String app = request.metadata() == null ? null : request.metadata().appName();
    return app == null ? OffTaskCategory.OTHER
        : APPS.getOrDefault(app.strip().toLowerCase(Locale.ROOT), OffTaskCategory.OTHER);
  }

  /** 가장 구체적인 상위 도메인부터 찾는다(예: news.naver.com 먼저, 그다음 naver.com). */
  static OffTaskCategory byHost(String host) {
    String candidate = host.startsWith("www.") ? host.substring(4) : host;
    while (true) {
      var category = HOSTS.get(candidate);
      if (category != null) {
        return category;
      }
      int dot = candidate.indexOf('.');
      if (dot < 0 || candidate.indexOf('.', dot + 1) < 0) {
        return OffTaskCategory.OTHER;
      }
      candidate = candidate.substring(dot + 1);
    }
  }

  private static void hosts(OffTaskCategory category, String... values) {
    List.of(values).forEach(value -> HOSTS.put(value, category));
  }

  private static void apps(OffTaskCategory category, String... values) {
    List.of(values).forEach(value -> APPS.put(value, category));
  }
}
