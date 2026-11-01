# Toolchain Version Matrix

| Document control | Value |
|---|---|
| Status | Locked; compatibility, build, frontend, provenance, and security gates passed |
| Verification date | 2026-11-01 |
| Verification environment | Windows 11 amd64 host; Docker 28.1.1 Linux x86_64; Eclipse Temurin 21.0.13+9; Maven 3.9.11; Node.js 24.13.0; pnpm 12.9.1 |
| Acceptance scope | Task 2 toolchain only; no production deployment or financial behavior is implied |

## Locked runtime and language versions

| Component | Exact version | Verification and upgrade constraint |
|---|---|---|
| Java | Eclipse Temurin 21.0.13+9 (Java 21 LTS) | Compiled and ran the compatibility spike. Keep Java 21 for the coordinated KIE 10.2.0 stack; re-run the spike before any patch or major upgrade. |
| Maven | 3.9.11 | Used for the spike and Maven Wrapper. Wrapper distribution SHA-256: `0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb`. No host Maven installation is required. |
| Quarkus | 3.27.4.1 | Security-fixed patch selected after Trivy identified vulnerabilities in 3.27.2. Re-run the full Kogito 10.2.0 compatibility suite for this and every subsequent patch. |
| Kogito | 10.2.0 | JDBC process persisted to PostgreSQL and was found by process/instance ID. Kogito automatic REST generation is disabled for this API-less test module. |
| Drools | 10.2.0 | Build-generated `ToolchainUnit` fired exactly once and returned exactly one `finished` query result. Use generated rule units inside Quarkus rather than runtime KieBuilder compilation. |
| DMN | 10.2.0 | DMN 1.6 model returned `COMPATIBLE` with no evaluation errors. Decision variables must be declared explicitly. |
| Jackson BOM | 2.21.7 | Security override for CVE-2026-68494, CVE-2026-89407, CVE-2026-89425, CVE-2026-91776, and CVE-2026-91777. Re-run serialization and compatibility tests on change. |
| Netty BOM | 4.1.137.Final | Security override including critical CVE-2026-75595. Re-run HTTP/process compatibility tests on change. |
| PostgreSQL JDBC | 42.7.12 | Security override for CVE-2026-42198 and CVE-2026-54291. Re-run PostgreSQL/AGE and Kogito persistence tests on change. |
| Guava | 33.7.2-jre | Security override for CVE-2026-102554. Keep dependency convergence checks enabled for all other coordinates. |
| Error Prone annotations / JSpecify | 2.50.0 / 1.0.1 | Annotation-only versions aligned with the security-patched Guava graph; no runtime behavior change is intended. |
| PostgreSQL | 16.10 (Debian 16.10-1.pgdg13+1) | `SELECT 1` passed in the pinned AGE container. Do not upgrade the PostgreSQL major independently of AGE support. |
| Apache AGE | 1.6.0 | `CREATE EXTENSION`, `LOAD 'age'`, and `extversion = 1.6.0` passed. AGE remains projection-only. |
| APISIX | 3.13.0 | `apisix version` returned 3.13.0 in the pinned image. Route/OIDC/TLS integration belongs to Task 4 and is not claimed here. |
| Testcontainers | 1.21.3 | Started the disposable PostgreSQL/AGE container used by the compatibility suite. Docker and permission to start sibling containers are required. |
| Node.js | 24.13.0 (Krypton LTS) | Host runtime present and used for frontend installation/tests. Pin the same patch in CI; evaluate patch upgrades through every frontend/security gate. |
| pnpm | 12.9.1 | Lockfile and frozen-install baseline. Set `packageManager` exactly and never generate the lockfile from modified post-install state. |
| React / React DOM | 19.3.1 | Locked for the minimal Task 2 foundation component; full browser shell belongs to Task 7. |
| Vite / plugin-react | 7.3.7 / 5.1.4 | Compatible with Node 24 and Vite 7 peer ranges. Do not use plugin-react 6 without a Vite 8 compatibility review. |
| TypeScript | 6.0.3 | Strict compiler baseline; typescript-eslint 8.72.1 supports TypeScript `<6.1.0`, so TypeScript 6.1+ requires a new compatibility spike. |
| Vitest / jsdom | 4.1.12 / 27.4.0 | Locked for component tests under Node 24. |
| Playwright | 1.64.1 | Locked for the browser-test package; no browser journey is claimed before later tasks add it. |
| ESLint / typescript-eslint | 9.39.5 / 8.72.1 | Flat-config linting with strict TypeScript and no unreviewed `any`. |
| Trivy | 0.70.0 | Local/CI high and critical dependency and secret scanning, including frontend development dependencies. No findings are suppressed by default. |
| Gitleaks | 8.30.1 | Local/CI secret scanning. No secret exception is seeded. |
| CycloneDX Maven plugin | 2.9.3 | Generates the aggregate Maven SBOM during `verify`. |
| Syft | 1.55.0 | Generates the frontend/workspace CycloneDX SBOM from the committed pnpm manifests and lockfile, excluding generated outputs; CycloneDX npm is not used because npm cannot interpret pnpm’s layout. |

## Pinned container images

Registry metadata supplied the following manifest digests. Per-architecture image digests are intentionally not substituted for the multi-platform manifest digest.

| Purpose | Image reference | Registry platforms |
|---|---|---|
| PostgreSQL 16.10 + AGE 1.6.0 | `apache/age:release_PG16_1.6.0@sha256:16aa423d20a31aed36a3313244bf7aa00731325862f20ed584510e381f2feaed` | linux/amd64 and linux/arm64 metadata present; linux/amd64 executed |
| APISIX 3.13.0 | `apache/apisix:3.13.0-debian@sha256:c5c7a55ebb5c07abc210dbb963a37f41030e12c91d23bacedbaa168fec633bd7` | linux/amd64 and linux/arm64 metadata present; linux/amd64 executed |
| Maven 3.9.11 + Temurin 21 build image | `maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237` | amd64, arm64/v8, ppc64le, s390x, riscv64 metadata present; Windows-host JDK used for executed tests |

## Compatibility evidence

The clean pre-lock spike used Java 21.0.13+9, Maven 3.9.11, Quarkus 3.27.2, Kogito/Drools/DMN 10.2.0, Testcontainers 1.21.3, and the pinned AGE image. Its final result was:

```text
Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The five assertions are PostgreSQL connectivity, AGE extension loading/version, one terminating DRL derivation, one exact DMN result, and a durable Kogito process instance found in `process_instances`. The same five-test suite also passed from the locked reactor after the Quarkus 3.27.4.1, Jackson 2.21.7, Netty 4.1.137.Final, PostgreSQL JDBC 42.7.12, and Guava 33.7.2 security updates. Frontend typecheck/lint/test/build, frozen installation, provenance checks, and Trivy/Gitleaks scans passed after those updates.

## Dependency convergence exceptions

The Kogito/Quarkus dependency graph contains nine third-party version conflicts. Forcing the highest version would change the tested runtime, so Enforcer excludes only these coordinates from `dependencyConvergence` and `requireUpperBoundDeps`: `net.minidev:json-smart`, `io.smallrye.common:smallrye-common-annotation`, `org.flywaydb:flyway-core`, `com.google.j2objc:j2objc-annotations`, `org.glassfish.jaxb:jaxb-runtime`, `com.google.guava:failureaccess`, `io.smallrye.reactive:mutiny`, `net.bytebuddy:byte-buddy`, and `com.fasterxml.jackson.core:jackson-core` (the latter only for `requireUpperBoundDeps`).

These are narrow compatibility exceptions, not dynamic-version or snapshot permissions. Maven resolves its normal nearest-wins graph, every other dependency remains subject to both rules, and the real compatibility suite must pass after every related upgrade. Any new conflict requires an explicit reviewed entry rather than a blanket exclusion.

## Prerequisites and supported setup

- Java 21 is required on `PATH` or through `JAVA_HOME`; Maven Wrapper supplies Maven. The verified local fallback and CI selection are Temurin 21.0.13+9. Maven uses a fixed `project.build.outputTimestamp`; byte-identical Quarkus application artifacts are not claimed.
- Node.js 24.13.0 and pnpm 12.9.1 are required for the frozen frontend workflow. Syft scans committed source manifests, excluding generated outputs and scratch directories; Java dependencies are recorded separately in the aggregate Maven SBOM. `verify-provenance` normalizes SBOM serial numbers and omits unstable `build-meta` external references; dependency versions, artifact checksums, test results, and vulnerability findings remain intact.
- Docker with Linux-container support is required for the real AGE and persistence tests. Tests must fail, never skip, when Docker is unavailable.
- Network access is required for initial Maven/npm dependency downloads, container images, and vulnerability databases. Subsequent builds must use frozen lockfiles and pinned digests.
- The verified execution platform is Windows 11 amd64 with Linux amd64 containers. CI uses Ubuntu amd64. Other registry-listed architectures are supported candidates, not executed claims.

## Security exception register

No dependency vulnerability exception is approved. Only the two PRD example UUIDs at lines 659 and 724 are excluded by exact fingerprint in `.gitleaksignore`; they are synthetic API examples, not credentials. Linked worktrees and local agent scratch are outside current-checkout scans, not approved findings. No other secret or vulnerability finding is suppressed.

## Upgrade rules

1. Treat Kogito, Drools/DMN, and Quarkus as one compatibility unit. The [Apache KIE 10.2.0 release](https://kie.apache.org/blog/kie_10_2_0_release/) documents Quarkus 3.27.2 and Java 21 support.
2. Run a clean compatibility spike before editing the locked POM or package manifests. Reject any failing combination before scaffolding changes.
3. Pin new container manifests by registry digest and re-run PostgreSQL/AGE/Kogito and APISIX checks on every supported release platform before claiming support.
4. Keep Maven dynamic versions and all `SNAPSHOT` dependencies prohibited. Enforcer and clean dependency-tree comparisons are mandatory.
5. Re-run frozen installation, all builds/tests, Trivy/Gitleaks, SBOM/provenance generation, and both clean checkouts for every version change.
6. High or critical security findings block the upgrade unless an authorized exception records owner, rationale, remediation date, and residual risk. No exception is currently approved.

## Sources

- [Apache KIE 10.2.0 release and platform upgrades](https://kie.apache.org/blog/kie_10_2_0_release/)
- [Kogito 10.2.x documentation, including JDBC persistence](https://kie.apache.org/docs/10.2.x/kogito/)
- [Apache AGE Docker Hub release metadata](https://hub.docker.com/v2/repositories/apache/age/tags/release_PG16_1.6.0)
- [APISIX 3.13.0 image metadata](https://hub.docker.com/v2/repositories/apache/apisix/tags/3.13.0-debian)
- [Maven 3.9.11/Temurin 21 image metadata](https://hub.docker.com/v2/repositories/library/maven/tags/3.9.11-eclipse-temurin-21)
- [Node.js release schedule](https://nodejs.org/en/about/previous-releases)
