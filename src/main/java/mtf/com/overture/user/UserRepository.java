package mtf.com.overture.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByOauthProviderAndOauthProviderId(OauthProvider oauthProvider, String oauthProviderId);

    @Query("select u.role from User u where u.id = :id and u.status = mtf.com.overture.user.UserStatus.ACTIVE")
    Optional<Role> findRoleById(@Param("id") Long id);

    boolean existsByNicknameAndIdNot(String nickname, Long id);
}
