package com.tevs.server.config;

import com.tevs.server.service.NodeStateManager;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class BootstrapFilter implements Filter {

    private final NodeStateManager nodeStateManager;

    public BootstrapFilter(NodeStateManager nodeStateManager) {
        this.nodeStateManager = nodeStateManager;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse,
                         FilterChain chain) throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // Only block /api/status* during bootstrap
        // Allow health check, sync/all, and non-API paths
        if (nodeStateManager.isBootstrapping()
                && path.startsWith("/api/status")
                && !path.equals("/api/health")
                && !path.equals("/api/sync/all")) {

            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Node bootstrapping, try again later\"}");
            return;
        }

        chain.doFilter(servletRequest, servletResponse);
    }
}
