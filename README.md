# Lazy Routing Repro

Minimal Spring Boot sample for testing `LazyConnectionDataSourceProxy` with:

- master datasource
- slave datasource
- `@Transactional(readOnly = true)` routing
- `datasource-micrometer-spring-boot`

This sample intentionally uses two separate PostgreSQL containers instead of physical replication.
That keeps the repro simple and makes wrong routing immediately obvious:

- writer DB returns `marker=MASTER`
- reader DB returns `marker=SLAVE`

For this specific bug, that is enough and easier to reason about than a full replica setup.

## Versions

- Spring Boot `3.3.0`
- Java `21`
- `datasource-micrometer-spring-boot` `1.0.0`

## Start PostgreSQL

```powershell
docker compose -f C:\Users\mcetkovic\IdeaProjects\Projects\lazy-routing-repro\docker-compose.yaml up -d
```

## Run the app

From the project root:

```powershell
.\gradlew bootRun
```

The app starts on `http://localhost:8088`.


## Java load test

A standalone Java runner is available via Gradle.

Default run:

```powershell
.\gradlew runLoadTest
```

Custom run:

```powershell
.\gradlew runLoadTest --args="--base-url=http://localhost:8088 --total-requests=500 --concurrency=30 --write-every=10 --timeout-seconds=30"
```

What it does:

- fires many concurrent requests against the running app
- sends mostly `/readonly` requests
- sends every Nth request to `/write`
- prints routing summaries for readonly and write calls
- prints `Readonly requests routed to WRITER: X`

How to read the result:

- good: readonly traffic stays on `SLAVE|READER|slavedb`
- bad: readonly traffic appears on `MASTER|WRITER|masterdb`

The old PowerShell version is still available at `scripts\load-test.ps1` if you want it.


## Compare with datasource-micrometer disabled  or removing the dependency

```powershell
.\gradlew bootRun --args="--jdbc.datasource-proxy.enabled=false"
```