package com.project.backendapi.workspace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceRequest(
    @NotBlank @Size(max=160) String name,
    @Size(max=4000) String description
) {}
