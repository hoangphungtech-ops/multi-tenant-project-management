# Security Notes

- Entra ID access token must be validated by Spring Security Resource Server.
- Configure exact issuer and audience; do not accept arbitrary JWTs.
- Demo mode (`APP_SECURITY_DEMO_MODE=true`) is for local learning only and must be false in shared/staging/production environments.
- Never trust tenant IDs or role claims supplied by a client without checking membership in the database.
- RLS is defense in depth. Use a non-owner runtime DB role without BYPASSRLS.
- Use transaction-scoped tenant context so HikariCP cannot leak one request's tenant to another.
- Never log bearer tokens, secrets, SAS query strings or sensitive payloads.
