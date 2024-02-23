package jwt.filter;

import domain.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jwt.Jwtservice.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.authority.mapping.NullAuthoritiesMapper;
import org.springframework.web.filter.OncePerRequestFilter;
import repository.UserRepository;

import java.io.IOException;

@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationProcessingFilter extends OncePerRequestFilter {

    private static final String NO_CHECK_URL = "/login";// "/login"으로 들어오는 요청은 필터 검사 X

    private final JwtService jwtService;
    private final UserRepository userRepository;

    private GrantedAuthoritiesMapper authoritiesMapper = new NullAuthoritiesMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException{
        if (request.getRequestURI().equals(NO_CHECK_URL)){
            filterChain.doFilter(request, response); // "/login" 요청이 들어오면, 다음 필터 호출
            return;// return으로 이후 현재 필터 진행 막기 (안해주면 아래로 내려가서 계속 필터 진행시킴)
        }
        // 사용자 요청 헤더에서 RefreshToken 추출
        // -> RefreshToken이 없거나 유효하지 않다면(DB에 저장된 RefreshToken과 다르다면) null을 반환
        // 사용자의 요청 헤더에 RefreshToken이 있는 경우는, AccessToken이 만료되어 요청한 경우밖에 없다.
        // 따라서, 위의 경우를 제외하면 추출한 refreshToken은 모두 null
        String refreshToken = jwtService.extractRefreshToken(request)
                .filter(jwtService::isTokenValid)
                .orElse(null);

        //RefreshToken 이 요청 헤드에 존재한다면, AccessToken이 만료되어서 재요청 보낸 것이기 떄문에 DB에서 확인후 일치하면
        //AccessToken을 재발급해주기.
        if(refreshToken != null){
            checkRefreshTokenAndIssueAccessToken(response, refreshToken);
            return;
        }

        // RefreshToken이 없거나 유효하지 않다면, AccessToken을 검사하고 인증을 처리하는 로직 수행
        // AccessToken이 없거나 유효하지 않다면, 인증 객체가 담기지 않은 상태로 다음 필터로 넘어가기 때문에 403 에러 발생
        // AccessToken이 유효하다면, 인증 객체가 담긴 상태로 다음 필터로 넘어가기 때문에 인증 성공
        if (refreshToken == null){
            checkAccessTokenAndAuthentication(request, response, filterChain);
        }

        public void checkRefreshTokenAndIssueAccessToken(HttpServletResponse response, String refreshToken){
            userRepository.findByRefreshToken(refreshToken)
                    .isPresent(user -> {
                        String reIssuedRefreshToken = reIssueRefreshToken(user);
                        jwtService.sendAccessAndRefreshToken(response, jwtService.createAccessToken(user.getEmail()), reIssuedRefreshToken);
                    });
        }

        private String reIssueRefreshToken(User user){
            String reIssuedRefreshToken = jwtService.createRefreshToken();
            user.updateRefreshToken(reIssuedRefreshToken);
            userRepository.saveAndFlush(user);
            return reIssuedRefreshToken;
        }
    }
}
