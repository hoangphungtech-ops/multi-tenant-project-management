# HikariCP and Scaling

HikariCP reuses database connections. Tune `maximum-pool-size`, `minimum-idle`, `connection-timeout`, `idle-timeout`, and `max-lifetime` using load tests and database connection limits.

Connection budget:
`maximum pool size per instance × maximum application instances + operational reserve <= PostgreSQL max_connections`.

Scale HTTP API horizontally behind a load balancer. Keep API stateless. Scale export workers independently from the queue backlog. Monitor DB CPU, active connections, query latency, lock waits, queue age and error rates.
