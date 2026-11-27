package com.capstone.backend.analysis.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.capstone.backend.analysis.dto.AnalysisDtos.OffTaskCategory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OffTaskClassifierTests {
  @ParameterizedTest
  @CsvSource({"www.youtube.com,VIDEO", "youtu.be,VIDEO", "chzzk.naver.com,VIDEO",
      "news.naver.com,NEWS", "n.news.naver.com,NEWS", "cafe.naver.com,COMMUNITY",
      "shopping.naver.com,SHOPPING", "naver.com,OTHER", "www.instagram.com,SNS",
      "gall.dcinside.com,COMMUNITY", "store.steampowered.com,GAME", "discord.com,MESSENGER",
      "coupang.com,SHOPPING", "notyoutube.com,OTHER", "com,OTHER"})
  void classifiesMostSpecificHostFirst(String host, OffTaskCategory expected) {
    assertEquals(expected, OffTaskClassifier.byHost(host));
  }
}
