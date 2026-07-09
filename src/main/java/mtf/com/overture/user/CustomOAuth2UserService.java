package mtf.com.overture.user;

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
        OauthProvider provider = OauthProvider.valueOf(registrationId.toUpperCase());

        return userRepository.findByOauthProviderAndOauthProviderId(provider, userInfo.getProviderId())
                .orElseGet(() -> createUser(provider, userInfo));
    }

    private User createUser(OauthProvider provider, OAuth2UserInfo userInfo) {
        if (userInfo.getEmail() == null || userInfo.getNickname() == null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "kakao_required_profile_missing",
                    "카카오 계정에서 이메일/닉네임 필수 동의 항목을 확인할 수 없습니다.",
                    null));
        }

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
