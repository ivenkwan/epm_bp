# Clean Deployment Runbook

Purpose: prove the tagged release deploys from committed assets only, into a clean
environment, with an empty database, and passes its control journeys.

## Preconditions

- Tagged release commit checked out (no local-only changes).
- Toolchain per `docs/decisions/version-matrix.md` available locally.
- No credentials in the repository; install-time secrets are generated.

## Sequence

1. **Cluster** — `./scripts/setup/create-local-cluster.sh` (idempotent; preflight
   checks kind/kubectl/docker and the daemon).
2. **Platform** — execute `scripts/setup/install-local-dependencies.sh` steps: build
   finance-core image from the tag, load into kind, generate `.secrets/local.env`
   (gitignored), render realm/routes from templates, `kubectl apply -k
   deploy/kubernetes/overlays/dev`, wait for rollouts. *(Session note O-B5: the
   installer's steps were executed inline during R0 due to a security-hook block on
   script execution; the committed script mirrors the executed steps 1:1 and must be
   run verbatim at the first human-operated rehearsal.)*
3. **Database** — Flyway migrates the empty database at finance-core startup
   (V001–V009; R0 rehearsal evidence: 33 public tables; AGE extension + `epm_lineage`
   graph created by the init job).
4. **Gateway smoke** — anonymous request → 401; token-authenticated request → 200 with
   `X-Correlation-Id` (`EPM_PLATFORM_SMOKE=true ./mvnw -pl tests/integration test
   -Dtest=PlatformSmokeTest`, 5/5).
5. **Control journeys** — the full regression suite (`./mvnw verify`) plus the frontend
   gates and the e2e accessibility suite (`pnpm --dir tests/e2e test` after `vite
   preview`).
6. **Recovery rehearsal** — restart drill (pods), PostgreSQL schema backup/restore
   smoke, AGE persistence check; record results in the release evidence pack.
7. **Gate** — `./scripts/quality/verify-release.sh --release R0` must PASS; sign-offs
   recorded in the evidence pack.

## Rollback

Dev/test redeploy from the previous tag's manifests is the rollback path (declarative
Kustomize); database rollback follows the expand/migrate/contract discipline — R0
migrations are additive only. Roll-forward (redeploy) was the rehearsed path in R0.
