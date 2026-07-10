package mtf.com.overture.user;

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

        return userRepository.findByOauthProviderAndOauthProviderId(provider, userInfo.getProviderId())
                .orElseGet(() -> createOrGetUser(provider, userInfo));
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
        if (userInfo.getNickname() == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "oauth_required_profile_missing",
                    "OAuth 계정에서 닉네임 필수 동의 항목을 확인할 수 없습니다.",
                    null));
        }

        // provider별로 이메일 제공 여부가 다름 - 카카오는 비즈 앱 전환 전까지 항상 null (KOE205로 스코프 요청 자체가 거부됨)
        User user = User.builder()
                .email(userInfo.getEmail())
                .nickname(userInfo.getNickname())
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
