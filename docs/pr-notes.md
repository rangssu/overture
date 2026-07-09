# PR 설명 노트

> PR 올릴 때 리뷰어(시니어)에게 언급해야 할 결정 사항들을 기능(날짜)별로 계속 누적 기록.

---

## 2026-07-07 — 사용자 인증(OAuth2/JWT)

- **`mtf.com.overture.core.security` 패키지 신설**: JWT 발급/검증, 인증 필터, 인증 예외를 여기 둔 이유는 횡단 관심사라서다 — 나중에 B가 만들 booking/payment/ticket API도 같은 필터를 타게 됨. `SecurityConfig` 자체는 처음엔 이 패키지에 뒀다가 최종 리뷰에서 "core가 user를 참조하는 순환 의존"으로 지적받아 `mtf.com.overture.config`(합성 루트) 패키지로 옮겼다는 것까지 설명하면 리뷰 맥락 이해가 쉬움.
- **Gradle 멀티모듈 미적용**: 아직 `module-user`/`module-core` 같은 실제 Gradle 서브프로젝트로는 안 쪼갰고 패키지로만 구분했다는 점. 다만 나중에 멀티모듈로 전환할 때 폴더만 옮기면 되도록 패키지 경계를 미리 맞춰뒀다는 점을 공유.
- **단일 세션 정책 채택**: 설계 문서엔 "다중 세션 제어 필요"라고만 되어 있고 구체 방식이 없었어서, 이번에 "새 로그인 시 이전 Refresh Token을 덮어써서 무효화"하는 단일 세션 정책으로 직접 정했다는 점을 명시.
- **Refresh Token을 쿠키가 아닌 Body로 받는 이유**: 프론트엔드가 아직 정해지지 않아 쿠키 도메인/CORS 정책을 결정할 수 없는 상태라는 전제를 설명하고, 프론트엔드 확정 시 쿠키 방식으로 전환할 여지가 있음을 언급.
- **Kakao만 우선 구현, Google/Naver는 후속 PR 예정**: 이번 PR에서 스코프를 의도적으로 좁혔다는 점을 밝혀서, 리뷰어가 "왜 Google/Naver가 없냐"고 묻지 않도록 미리 설명.
- **JWT에 `type`(access/refresh) 클레임 추가**: 처음엔 두 토큰이 role 클레임 유무로만 구분돼서, Refresh Token을 Authorization 헤더에 넣으면 Access Token처럼 통과되는 문제가 있었음. 최종 리뷰에서 발견해서 `type` 클레임 + `JwtAuthenticationFilter`/`AuthService.refresh` 양쪽에서 타입 검증을 추가로 걸었다는 점을 리뷰어에게 짚어줄 것 — 인증 인프라의 핵심 변경이라 꼭 언급.
- **JWT 시크릿 기본값 관련 안전장치**: `application.yaml`의 로컬 개발용 기본 시크릿이 그대로 배포되면 위험하다는 지적을 받아, `application-prod.yaml`에 기본값 없는 `JWT_SECRET`을 넣어 `--spring.profiles.active=prod`로 띄우면 환경변수 미설정 시 기동이 실패하도록 만들었음. **실제 배포 환경에서는 반드시 `prod` 프로파일 + `JWT_SECRET` 환경변수를 설정해야 한다는 것을 시니어에게도 공유 필요.**
- **Kakao 개발자 콘솔 등록 + 브라우저 로그인 확인은 아직 미완료**: 실제 Kakao client-id/secret과 브라우저가 필요한 수동 확인 단계라 이번 구현에서는 못 했음. 머지 전 또는 머지 후 별도로 직접 확인 필요.
- **PR**: https://github.com/rangssu/overture/pull/2 (리뷰어 siwol025 지정 완료)

---

## 2026-07-09 — B 코드 리뷰 반영 (PR #2 후속, 8건)

B와 오프라인으로 진행한 코드 리뷰에서 나온 지적 8건을 순서대로 반영. 커밋: `ab79dc1`.

1. **`CustomOAuth2UserService`의 `@SuppressWarnings("unchecked")` + NPE 위험**: `Map` 캐스팅으로 `kakao_account`/`profile`을 직접 꺼내던 방식이라, 카카오 선택 동의 미동의 시 해당 키 자체가 응답에서 빠지면서 NPE 나던 문제. `KakaoUserResponse`(record) + `KakaoUserInfo`(null-safe 파싱) + `OAuth2UserInfo` 인터페이스로 교체. 이메일/닉네임이 끝내 null이면 `OAuth2AuthenticationException`으로 로그인 자체를 거부(단일 동의 실패로 DB NOT NULL 제약 위반이 나지 않도록). **카카오 개발자 콘솔에서 nickname/email을 필수 동의로 설정하는 건 별도 수동 작업으로 남아있음.**
2. **`JwtProvider`의 `type` 클레임만 상수화되고 `role` 클레임은 문자열 리터럴로 남아있던 불일치**: `ROLE_CLAIM` 상수로 통일 (순수 리팩터, 동작 변화 없음).
3. **OAuth 로그인 성공 후 리다이렉트 URL에 accessToken/refreshToken을 쿼리파라미터로 노출**: 브라우저 히스토리·서버 access 로그·Referer 헤더로 토큰이 유출될 수 있는 구조. 이번엔 이 이슈 자체는 논의만 하고 아직 미해결 상태로 남겨둠 — **다음에 1회용 교환 코드(exchange code) 패턴으로 해결 예정** (프론트엔드 도메인이 아직 안 정해져 쿠키 방식은 보류).
4. **Refresh Token Rotation + Reuse Detection 도입**: 기존엔 `/refresh` 호출 시 refresh token을 재발급하지 않아, 탈취되면 만료(7일)까지 계속 악용 가능했음. 이제 매 refresh마다 새 refresh token 발급 + 이전 토큰 폐기. 구현 중 Redis `GET`→`SET` 사이 원자성이 없어서 동시 요청 레이스로 정상 세션이 강제 로그아웃되는 버그를 TDD로 재현 후 Lua 스크립트로 원자적 처리 + grace-window(10초, 직전 폐기 토큰까지는 재사용 허용)로 해결. 두 세대 이상 지난 토큰 재사용만 탈취 의심으로 세션 전체 무효화.
5. **`refresh()`에서 access token role이 `"USER"`로 하드코딩**: ORGANIZER/ADMIN 유저가 refresh하면 권한이 조용히 강등되는 버그였음. `UserRepository.findRoleById()`(projection 쿼리)로 실제 role을 조회하도록 수정.
6. **후속 코드 리뷰(자체 실시, 8건)**: 위 변경들 리뷰하면서 추가로 발견된 것들 — (a) `refresh:` Redis 키/TTL이 `AuthService`와 `OAuth2SuccessHandler`에 중복 하드코딩 → `RefreshTokenStore`로 통합, (b) `OAuth2UserInfo` 인터페이스가 있는데 실제 분기 없이 `KakaoUserInfo` 하드코딩 → `OAuth2UserInfoFactory` + `registrationId` 기반 분기로 실제 동작하게 함, (c) `RefreshResult`와 `RefreshResponse`가 필드까지 완전 동일한 record 중복 → `RefreshResult` 제거하고 서비스가 `RefreshResponse` 직접 반환, (d) `KakaoUserInfo`가 Spring이 관리하는 `ObjectMapper` 빈 대신 자체 인스턴스 생성 → 주입 방식으로 변경, (e) `CustomOAuth2UserService.createUser`에서 던지는 `OAuth2Error`의 구체적 에러 코드가 `OAuth2FailureHandler`에서 무시되고 항상 `error=oauth_failed`로만 리다이렉트되던 것 → 실제 에러 코드 전달하도록 수정.
7. **Spring Boot 4(Jackson 3) 관련 발견**: `ObjectMapper` 패키지가 `com.fasterxml.jackson.databind` → `tools.jackson.databind`로 바뀜 (`@JsonProperty` 등 애너테이션은 `com.fasterxml.jackson.annotation`에 그대로 유지). Jackson 관련 코드 작성 시 다들 헷갈릴 수 있어서 공유.
8. **PR**: https://github.com/rangssu/overture/pull/2 (동일 PR에 반영, origin에 push 완료)
