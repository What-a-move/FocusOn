package com.capstone.backend.auth.persistence;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/** Checks persistence defaults and active Google identity lookup. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AuthPersistenceRepositoryTests {

  @Autowired private UserRepository users;

  @Autowired private OauthIdentityRepository identities;

  @Autowired private UserDeviceRepository devices;

  @Autowired private TokenFamilyRepository families;

  @Autowired private RefreshTokenRecordRepository tokens;

  @Test
  void unlinkedGoogleIdentityIsExcludedFromLoginLookup() {
    UserEntity user = users.saveAndFlush(new UserEntity());
    OauthIdentityEntity identity = new OauthIdentityEntity();
    identity.setUser(user);
    identity.setProviderSubject("google-sub-for-login-lookup");
    identities.saveAndFlush(identity);

    assertTrue(identities.findByProviderAndProviderSubjectAndUnlinkedAtIsNull(
        OauthIdentityEntity.Provider.GOOGLE, identity.getProviderSubject()).isPresent());

    identity.setUnlinkedAt(Instant.now());
    identities.saveAndFlush(identity);

    assertTrue(identities.findByProviderAndProviderSubjectAndUnlinkedAtIsNull(
        OauthIdentityEntity.Provider.GOOGLE, identity.getProviderSubject()).isEmpty());
  }

  @Test
  void refreshTokenIssueTimeIsGeneratedWhenSaved() {
    UserEntity user = users.saveAndFlush(new UserEntity());
    UserDeviceEntity device = new UserDeviceEntity();
    device.setUser(user);
    device.setDeviceType(UserDeviceEntity.DeviceType.DESKTOP);
    device.setInstallationIdHash("a".repeat(64));
    device.setDisplayName("QA Desktop");
    device = devices.saveAndFlush(device);

    TokenFamilyEntity family = new TokenFamilyEntity();
    family.setUser(user);
    family.setDevice(device);
    family.setExpiresAt(Instant.now().plusSeconds(30L * 24 * 60 * 60));
    family = families.saveAndFlush(family);

    RefreshTokenRecordEntity token = new RefreshTokenRecordEntity();
    token.setTokenFamily(family);
    token.setTokenHash("b".repeat(64));
    token.setExpiresAt(Instant.now().plusSeconds(30L * 24 * 60 * 60));
    token = tokens.saveAndFlush(token);

    assertNotNull(token.getIssuedAt());
  }
}
