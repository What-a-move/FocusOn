package com.capstone.backend.auth.controller;

import com.capstone.backend.auth.persistence.UserDeviceEntity;
import com.capstone.backend.auth.persistence.UserDeviceRepository;
import com.capstone.backend.auth.security.FocusOnPrincipal;
import com.capstone.backend.auth.service.TokenLifecycleService;
import com.capstone.backend.common.api.ApiErrorCode;
import com.capstone.backend.common.api.ApiException;
import com.capstone.backend.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Desktop에서 기존 계정의 활성 기기를 확인한다. */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {
  private final UserDeviceRepository devices;
  private final TokenLifecycleService lifecycle;

  /** 기기 저장소와 로그인 폐기 서비스를 사용한다. */
  public DeviceController(UserDeviceRepository devices, TokenLifecycleService lifecycle) {
    this.devices = devices;
    this.lifecycle = lifecycle;
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

  /** 본인 소유 활성 기기를 폐기한다. Desktop이면 연결된 Extension도 함께 폐기한다. */
  @DeleteMapping("/{deviceId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "기기 해제", description = "본인 소유 ACTIVE 기기만 폐기합니다. 없거나 남의 기기, "
      + "이미 해제된 기기는 404 DEVICE_NOT_FOUND입니다. Desktop을 해제하면 연결된 Extension도 함께 "
      + "해제합니다.")
  public void revoke(@AuthenticationPrincipal FocusOnPrincipal principal,
      @PathVariable String deviceId) {
    // ID 형식보다 호출자 종류를 먼저 확인해 Extension에는 항상 401을 준다.
    if (principal.deviceType() != UserDeviceEntity.DeviceType.DESKTOP) {
      throw new ApiException(ApiErrorCode.AUTH_RELOGIN_REQUIRED);
    }
    lifecycle.revokeDevice(principal, parseDeviceId(deviceId));
  }

  private static long parseDeviceId(String value) {
    try {
      long id = Long.parseLong(value);
      if (id <= 0) {
        throw new NumberFormatException();
      }
      return id;
    } catch (NumberFormatException exception) {
      throw new ApiException(ApiErrorCode.DEVICE_NOT_FOUND);
    }
  }
}
