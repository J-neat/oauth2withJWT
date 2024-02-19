package repository;

import domain.User;
import enums.SocialType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    Optional<User> findByNickName(String nickname);

    Optional<User> findByRefreshToken(String refreshToken);

    //OAuth2 로그인 구현 시 사용하는 메서드
    Optional<User> findBySocialTypeAndSocialId(SocialType socialType, String socialId);
}
