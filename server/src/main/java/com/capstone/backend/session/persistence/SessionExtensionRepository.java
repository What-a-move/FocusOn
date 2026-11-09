package com.capstone.backend.session.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 확정된 연장 이력을 저장한다. */
public interface SessionExtensionRepository extends JpaRepository<SessionExtensionEntity, Long> {}
