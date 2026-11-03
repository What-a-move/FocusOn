package com.capstone.backend.auth.controller;

import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.ApiResponse;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Desktop에서 기존 계정의 활성 기기를 확인한다. */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
  private final UserDeviceRepository devices;

  /** 기기 저장소를 사용한다. */
  public DeviceController(UserDeviceRepository devices) {
    this.devices = devices;
  }

  /** 복구에 필요한 공개 식별자만 반환한다. */
  public record Device(String deviceId, String type, String name, Instant lastUsedAt,
      String linkedDesktopDeviceId) {}

  /** 기기 목록을 담는다. */
  public record DeviceList(List<Device> devices) {}

  /** 본인 기기만 나열하고 설치 식별자·토큰은 반환하지 않는다. */
  @GetMapping
  @Transactional(readOnly = true)
  public ApiResponse<DeviceList> list(@AuthenticationPrincipal FocusOnPrincipal principal) {
    if (principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    var result = devices.findByUserIdAndStatus(principal.userId(), UserDeviceEntity.Status.ACTIVE)
        .stream().map(device -> new Device(device.getId().toString(),
            device.getDeviceType() == UserDeviceEntity.DeviceType.CHROME_EXTENSION
                ? "EXTENSION" : "DESKTOP", device.getDisplayName(), device.getLastUsedAt(),
            device.getLinkedDesktopDevice() == null ? null
                : device.getLinkedDesktopDevice().getId().toString())).toList();
    return ApiResponse.of(new DeviceList(result));
  }
}
