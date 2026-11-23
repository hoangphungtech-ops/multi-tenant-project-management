package com.project.backendapi.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationFilter implements Filter {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9._-]{1,80}");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String supplied = req.getHeader("X-Request-Id");
        String requestId = supplied != null && SAFE.matcher(supplied).matches()
                ? supplied : UUID.randomUUID().toString();
        MDC.put("request_id", requestId);
        res.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("request_id");
        }
    }
}
