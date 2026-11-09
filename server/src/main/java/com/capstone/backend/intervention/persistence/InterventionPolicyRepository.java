package com.capstone.backend.intervention.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** 세션 소유 잠금 안에서만 접근하는 정책 상태다. */
public interface InterventionPolicyRepository extends
    JpaRepository<InterventionPolicyState, Long> {}
