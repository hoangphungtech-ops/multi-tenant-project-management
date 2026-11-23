package com.project.backendapi.workspace;

import com.project.backendapi.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/tenants/{tenantId}/workspaces")
public class WorkspaceController {
    private final WorkspaceService service;
    private final CurrentUser currentUser;
    public WorkspaceController(WorkspaceService service, CurrentUser currentUser) {
        this.service=service; this.currentUser=currentUser;
    }
    private String user() { return currentUser.userKey(SecurityContextHolder.getContext().getAuthentication()); }

    @PostMapping
    public ResponseEntity<WorkspaceResponse> create(@PathVariable UUID tenantId, @Valid @RequestBody WorkspaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(tenantId,user(),request));
    }
    @GetMapping
    public List<WorkspaceResponse> list(@PathVariable UUID tenantId) { return service.list(tenantId,user()); }
    @GetMapping("/{id}")
    public WorkspaceResponse get(@PathVariable UUID tenantId,@PathVariable UUID id) { return service.get(tenantId,id,user()); }
    @PutMapping("/{id}")
    public WorkspaceResponse update(@PathVariable UUID tenantId,@PathVariable UUID id,@Valid @RequestBody WorkspaceRequest request) {
        return service.update(tenantId,id,user(),request);
    }
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID tenantId,@PathVariable UUID id) {
        service.delete(tenantId,id,user()); return ResponseEntity.noContent().build();
    }
}
