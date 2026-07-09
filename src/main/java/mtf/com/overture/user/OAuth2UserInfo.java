package mtf.com.overture.user;

public interface OAuth2UserInfo {
    String getProviderId();
    String getEmail();
    String getNickname();
    String getProfileImageUrl();
}
