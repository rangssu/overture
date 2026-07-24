package mtf.com.overture.user.service;
import mtf.com.overture.user.entity.User;
import mtf.com.overture.user.oauth.OAuth2UserInfoFactory;
import mtf.com.overture.user.oauth.OAuth2UserInfo;
import mtf.com.overture.user.repository.UserRepository;
import mtf.com.overture.user.enums.UserStatus;
import mtf.com.overture.user.enums.OauthProvider;
import mtf.com.overture.user.oauth.CustomOAuth2User;
import mtf.com.overture.user.enums.Role;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public CustomOAuth2UserService(UserRepository userRepository, ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        User user = resolveUser(registrationId, oAuth2User.getAttributes());
        return new CustomOAuth2User(user.getId(), user.getRole(), oAuth2User.getAttributes());
    }

    User resolveUser(String registrationId, Map<String, Object> attributes) {
        OAuth2UserInfo userInfo = OAuth2UserInfoFactory.create(registrationId, attributes, objectMapper);
        OauthProvider provider = toOauthProvider(registrationId);

        User user = userRepository.findByOauthProviderAndOauthProviderId(provider, userInfo.getProviderId())
                .orElseGet(() -> createOrGetUser(provider, userInfo));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "inactive_user", "탈퇴하거나 정지된 계정입니다.", null));
        }

        return user;
    }

    private OauthProvider toOauthProvider(String registrationId) {
        try {
            return OauthProvider.valueOf(registrationId.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "unsupported_provider", "지원하지 않는 OAuth2 provider입니다: " + registrationId, null));
        }
    }

    private User createOrGetUser(OauthProvider provider, OAuth2UserInfo userInfo) {
        try {
            return createUser(provider, userInfo);
        } catch (DataIntegrityViolationException e) {
            // 동시 첫 로그인 레이스: 진 쪽은 unique 제약 위반 대신 이긴 쪽이 만든 row를 그대로 사용한다.
            return userRepository.findByOauthProviderAndOauthProviderId(provider, userInfo.getProviderId())
                    .orElseThrow(() -> e);
        }
    }

    private User createUser(OauthProvider provider, OAuth2UserInfo userInfo) {
        // provider별로 이메일 제공 여부가 다름 - 카카오는 비즈 앱 전환 전까지 항상 null (KOE205로 스코프 요청 자체가 거부됨)
        // 닉네임은 카카오 동의 항목에서 가져오지 않는다 - 여러 사용자가 같은 카카오 닉네임을 가질 수 있어
        // 앱 내에서 사용자가 PATCH /api/v1/users/me로 직접 고유한 닉네임을 설정하게 한다.
        User user = User.builder()
                .email(userInfo.getEmail())
                .nickname(null)
                .profileImageUrl(userInfo.getProfileImageUrl())
                .oauthProvider(provider)
                .oauthProviderId(userInfo.getProviderId())
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }
}
