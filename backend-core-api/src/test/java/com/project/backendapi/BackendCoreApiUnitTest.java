package com.project.backendapi;

import com.project.backendapi.export.ExportJob;
import com.project.backendapi.filter.CorrelationFilter;
import com.project.backendapi.filter.DemoAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BackendCoreApiUnitTest {

    @Test
    void exportJobStoresTenantAndRequester() {
        UUID tenantId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        ExportJob job = new ExportJob(tenantId, requesterId);

        assertEquals(tenantId, job.getTenantId());
        assertEquals(requesterId, job.getRequestedBy());
        assertEquals("PENDING", job.getStatus());
        assertNotNull(job.getCreatedAt());
    }

    @Test
    void correlationFilterPropagatesSafeRequestId() throws Exception {
        CorrelationFilter filter = new CorrelationFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Request-Id", "req-team-test-01");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertEquals("req-team-test-01", res.getHeader("X-Request-Id"));
    }

    @Test
    void demoFilterRejectsMalformedTenantHeader() throws Exception {
        SecurityContextHolder.clearContext();
        DemoAuthenticationFilter filter = new DemoAuthenticationFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Demo-User", "demo-owner");
        req.addHeader("X-Demo-Tenant", "not-a-uuid");
        MockHttpServletResponse res = new MockHttpServletResponse();

        filter.doFilter(req, res, new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
