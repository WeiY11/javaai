package com.example.evimind.auth;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.evimind.identity.GroupContext;
import com.example.evimind.identity.GroupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final TokenProvider tokenProvider;
  private final GroupService groupService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    try {
      String token = extractToken(request);
      if (token != null && tokenProvider.validateToken(token)) {
        Long userId = tokenProvider.getUserIdFromToken(token);
        String username = tokenProvider.getUsernameFromToken(token);
        String systemRole = tokenProvider.getSystemRoleFromToken(token);
        Long groupId = groupService.getOrCreateDefaultGroupId(userId, username);
        GroupContext.set(userId, groupId, systemRole);

        List<SimpleGrantedAuthority> authorities =
            List.of(new SimpleGrantedAuthority("ROLE_" + systemRole));
        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(username, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
      filterChain.doFilter(request, response);
    } finally {
      GroupContext.clear();
    }
  }

  private String extractToken(HttpServletRequest request) {
    String bearer = request.getHeader("Authorization");
    if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
      return bearer.substring(7);
    }
    return null;
  }
}
