package mtf.com.overture.user;

import mtf.com.overture.core.security.AuthErrorCode;
import mtf.com.overture.core.security.AuthException;
import mtf.com.overture.user.dto.UserResponse;
import mtf.com.overture.user.dto.UserUpdateRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        return UserResponse.from(findActiveUser(userId));
    }

    @Transactional
    public UserResponse updateMe(Long userId, UserUpdateRequest request) {
        User user = findActiveUser(userId);

        if (request.nickname() == null && request.profileImageUrl() == null) {
            throw new UserException(UserErrorCode.NO_FIELDS_TO_UPDATE);
        }

        if (request.nickname() != null) {
            if (userRepository.existsByNicknameAndIdNot(request.nickname(), userId)) {
                throw new UserException(UserErrorCode.NICKNAME_DUPLICATE);
            }
            user.updateNickname(request.nickname());
        }
        if (request.profileImageUrl() != null) {
            user.updateProfileImageUrl(request.profileImageUrl());
        }

        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // existsByNicknameAndIdNot 사전 조회 이후에도 동시에 같은 닉네임으로 PATCH가 들어오는 레이스가 있을 수 있다 -
            // 이 경우 flush 시점에 DB unique 제약 위반으로 걸러진다.
            throw new UserException(UserErrorCode.NICKNAME_DUPLICATE);
        }

        return UserResponse.from(user);
    }

    private User findActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE)
                .orElseThrow(() -> new AuthException(AuthErrorCode.INVALID_ACCESS_TOKEN));
    }
}
