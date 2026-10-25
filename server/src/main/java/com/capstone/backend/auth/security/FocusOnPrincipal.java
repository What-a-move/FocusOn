package com.capstone.backend.auth.security;

import com.capstone.backend.auth.persistence.UserDeviceEntity.DeviceType;

/** JWT와 DB의 소유·활성 검증을 마친 요청 주체. 팀원 API는 이 값을 사용한다. */
public record FocusOnPrincipal(long userId, long deviceId, long tokenFamilyId,
    DeviceType deviceType) {}
