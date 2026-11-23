# Implementation status and integration checklist

This source package is a coherent learning starter, not a production-certified application.

Before team integration:
1. Align endpoint paths and DTOs with the approved API contract.
2. Verify actual Entra issuer, audience, tenant claim and user identifier claim.
3. In production, set `APP_SECURITY_DEMO_MODE=false`.
4. Create a dedicated PostgreSQL runtime role that is not a table owner and does not have BYPASSRLS; run the application with that role.
5. Review transaction boundaries: tenant context must be set and used within the same transaction/connection.
6. Complete export worker that reads queue messages, generates actual export files, uploads to Blob, and updates export job status.
7. Configure Azurite connection string with the official development account key, or use managed identity/user delegation SAS in Azure.
8. Add integration tests for cross-tenant isolation and all role/action combinations.
9. Review error responses and logging for information disclosure.
10. Run `mvn test` and team CI before merge.
