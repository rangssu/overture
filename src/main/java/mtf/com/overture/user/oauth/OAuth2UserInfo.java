package mtf.com.overture.user.oauth;

public interface OAuth2UserInfo {
    String getProviderId();
    String getEmail();
    String getNickname();
    String getProfileImageUrl();
}
