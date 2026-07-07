package mtf.com.overture.user;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        User user = resolveUser(oAuth2User.getAttributes());
        return new CustomOAuth2User(user.getId(), user.getRole(), oAuth2User.getAttributes());
    }

    User resolveUser(Map<String, Object> kakaoAttributes) {
        String providerId = String.valueOf(kakaoAttributes.get("id"));

        return userRepository.findByOauthProviderAndOauthProviderId(OauthProvider.KAKAO, providerId)
                .orElseGet(() -> createUser(providerId, kakaoAttributes));
    }

    @SuppressWarnings("unchecked")
    private User createUser(String providerId, Map<String, Object> kakaoAttributes) {
        Map<String, Object> kakaoAccount = (Map<String, Object>) kakaoAttributes.get("kakao_account");
        Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

        User user = User.builder()
                .email((String) kakaoAccount.get("email"))
                .nickname((String) profile.get("nickname"))
                .profileImageUrl((String) profile.get("profile_image_url"))
                .oauthProvider(OauthProvider.KAKAO)
                .oauthProviderId(providerId)
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }
}
