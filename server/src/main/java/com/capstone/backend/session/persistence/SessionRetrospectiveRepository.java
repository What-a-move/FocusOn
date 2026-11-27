package com.capstone.backend.session.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 세션 회고를 세션 ID로 읽고 저장한다. */
public interface SessionRetrospectiveRepository
    extends JpaRepository<SessionRetrospectiveEntity, Long> {}
