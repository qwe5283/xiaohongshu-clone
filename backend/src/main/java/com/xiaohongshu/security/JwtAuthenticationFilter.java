package com.xiaohongshu.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaohongshu.common.result.Result;
import com.xiaohongshu.common.result.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT认证过滤器
 *
 * 路径匹配使用 getServletPath()（不含 context-path），与 SecurityConfig 保持一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 不需要认证的路径（白名单，相对于 servlet path，不含 context-path）
     */
    private static final String[] WHITE_LIST = {
            "/user/login",
            "/user/register",
            "/post/list",
            "/post/user/*",
            "/comment/post/*",
            "/comment/replies/*",
            "/follow/following/*",
            "/follow/followers/*",
            "/follow/count/*",
            "/upload/**",
            "/doc.html",
            "/swagger-ui/**",
            "/v3/api-docs/**"
    };

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 使用 servletPath（不含 context-path），与 SecurityConfig 一致
        String requestPath = request.getServletPath();

        // 1. 获取Token
        String token = request.getHeader(jwtUtil.getHeader());

        // 2. 始终尝试解析Token并设置认证
        if (StringUtils.hasText(token)) {
            try {
                if (jwtUtil.validateToken(token)) {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    String username = jwtUtil.getUsernameFromToken(token);

                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                    authentication.setDetails(username);

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception e) {
                log.warn("Token解析失败（非阻断）：{}", e.getMessage());
            }
        }

        // 3. 检查是否需要强制认证
        boolean isWhiteListed = isWhiteListed(request.getMethod(), requestPath);

        // 4. 非白名单路径 + 未认证 → 返回401
        if (!isWhiteListed && SecurityContextHolder.getContext().getAuthentication() == null) {
            writeError(response, ResultCode.USER_NOT_LOGIN);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isWhiteListed(String method, String requestPath) {
        if ("GET".equalsIgnoreCase(method)) {
            if (requestPath.startsWith("/user/") && !"/user/me".equals(requestPath)) {
                return true;
            }
            if (requestPath.startsWith("/post/") && !"/post/my".equals(requestPath)) {
                return true;
            }
        }
        for (String path : WHITE_LIST) {
            if (pathMatcher.match(path, requestPath)) {
                return true;
            }
        }
        return false;
    }

    private void writeError(HttpServletResponse response, ResultCode resultCode) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        Result<?> result = Result.error(resultCode);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
