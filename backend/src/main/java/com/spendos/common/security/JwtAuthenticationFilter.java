package com.spendos.common.security;

import com.spendos.auth.repository.RevokedTokenRepository;
import com.spendos.auth.repository.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying a valid, unrevoked access token whose user is still active and
 * whose issue time is after the user's last credential change. The principal is the user's UUID.
 * Registered only inside the Spring Security chain (see SecurityConfig).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final RevokedTokenRepository revokedTokenRepository;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider, RevokedTokenRepository revokedTokenRepository,
                                   UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.revokedTokenRepository = revokedTokenRepository;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = bearerToken(request);
        if (token != null) {
            try {
                Claims claims = tokenProvider.parseToken(token);
                UUID userId = tokenProvider.getUserId(claims);
                boolean valid = tokenProvider.isAccessToken(claims)
                        && !revokedTokenRepository.existsById(tokenProvider.getTokenId(claims))
                        && userRepository.findById(userId)
                                .map(user -> user.acceptsTokenIssuedAt(tokenProvider.getIssuedAt(claims)))
                                .orElse(false);
                if (valid) {
                    var authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (RuntimeException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    public static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && header.length() > 7) {
            return header.substring(7).trim();
        }
        return null;
    }
}
