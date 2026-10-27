package com.capstone.backend.user.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 사용자 기본 집중 설정의 저장소다. */
public interface UserFocusSettingsRepository extends JpaRepository<UserFocusSettingsEntity, Long> {}
