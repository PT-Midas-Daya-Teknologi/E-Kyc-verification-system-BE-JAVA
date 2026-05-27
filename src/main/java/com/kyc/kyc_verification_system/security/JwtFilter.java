package com.kyc.kyc_verification_system.security;

import com.kyc.kyc_verification_system.util.JwtUtil;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {

        this.jwtUtil = jwtUtil;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request) {

        String path =
                request.getServletPath();

        return path.equals("/kyc/initiate");
    }

    @Override
    protected void doFilterInternal(

            HttpServletRequest request,

            HttpServletResponse response,

            FilterChain filterChain

    ) throws ServletException, IOException {

        String authHeader =
                request.getHeader("Authorization");

        String token = null;

        String sessionId = null;

        if (authHeader != null
                && authHeader.startsWith("Bearer ")) {

            token =
                    authHeader.substring(7);

            try {

                sessionId =
                        jwtUtil.extractSessionId(token);

            } catch (Exception e) {

                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

                response.getWriter()
                        .write("Invalid JWT Token");

                return;
            }
        }

        if (sessionId != null
                && SecurityContextHolder
                .getContext()
                .getAuthentication() == null) {

            if (jwtUtil.validateToken(token)) {

                UsernamePasswordAuthenticationToken authToken =

                        new UsernamePasswordAuthenticationToken(

                                sessionId,

                                null,

                                Collections.emptyList()
                        );

                authToken.setDetails(

                        new WebAuthenticationDetailsSource()
                                .buildDetails(request)
                );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authToken);

                request.setAttribute(
                        "sessionId",
                        sessionId
                );
            }
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}