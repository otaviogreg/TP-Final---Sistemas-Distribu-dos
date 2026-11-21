package br.edu.cefet.tp.auth.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InstanceHeaderFilter extends OncePerRequestFilter {

    private final String instanceId;

    public InstanceHeaderFilter(Environment environment) {
        this.instanceId = environment.getProperty(
                "HOSTNAME",
                environment.getProperty("spring.application.name", "auth-service")
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        response.setHeader("X-Application-Instance", instanceId);
        filterChain.doFilter(request, response);
    }
}
