# Rollout: lifs-apps tracking and Goslin deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn Matomo tracking back on in lifs-apps with the new consent fragment, and deploy Goslin with its results on the shared volume using Spring Boot 4.2 property names.

**Architecture:** Two small changes in two repositories:
- lifs-apps includes `lifs/layout :: tracking` from lifs-webapps-common 2.0.1 on its pages.
- In docker-recipes-external, the Goslin service mounts the shared volume at `storage.location`, its environment uses the Boot 4.2 property names, and it runs the new image.

**Tech Stack:** Thymeleaf, Playwright (lifs-apps e2e), Docker Swarm compose.

**Spec:** `/Users/nilshoffmann/Projects/github.com/lifs-tools/goslin-webapp/docs/superpowers/specs/2026-11-26-goslin-lifs-redesign-design.md` (sections 2, 6.3, 6.7)

**Plan 3 of 3.** Task 1 needs Plan 1 released (lifs-webapps-common 2.0.1). Task 2 needs Plan 2 released (a Goslin 2.3.0 image). Task 3 needs Task 1 released.

## Global Constraints

- lifs-apps: branch `feature/matomo-tracking` from `master`. docker-recipes-external: branch `feature/goslin-2.3.0`.
- lifs-apps keeps its `ga.id` property. `PageBuilderService` already sets `page.gaId` on every page (`/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-apps/src/main/java/org/lifstools/lifsapps/webapp/services/PageBuilderService.java:54`). The pom default is empty (`/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-apps/pom.xml:79`), so the script only renders where `ga.id` is set. Production sets `"ga.id":"9"` in `docker-compose-lifs-apps-webapp.yml`.
- The Goslin compose file keeps `replicas: 2`. Results live on the shared volume, under the same container path on every replica.
- Do not copy secrets into new places. `keycloak.credentials.secret` is in the compose file today. Goslin no longer uses Keycloak, so that entry is removed with this change.
- Deploying to the swarm is Nils' step. This plan ends with a merged pull request.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Review Focus

1. **A lifs-apps visitor who already accepted cookies on Goslin** (same cookie, `path=/`, same host `apps.lifs-tools.org`) sees no banner, and tracking starts. Test `consent carries over from another app` in Task 1.
2. **The statistics page in lifs-apps** still works with tracking on; it must not count its own admin views twice. Check by hand in Task 1, Step 4.
3. **The first deploy with `stop-first` and two replicas:** results created on the old version must not break the new one. They don't, because the old version rendered results directly and stored only a TSV. Check in Task 2, Step 3 that an old `/download/<id>/OUTPUT_TSV_FILE` link from before the deploy returns 404 rather than 500.
4. **The volume's owner and permissions** must let the container user write. Check in Task 2, Step 3 by creating a result on each replica.
5. **The cleanup job runs on both replicas** against the same folder. Two deletions of the same old folder must not fail. `FileSystemUtils.deleteRecursively` ignores missing files; confirm in the logs after the first nightly run.

---

### Task 1: Matomo tracking back in lifs-apps

**Files** (all in `/Users/nilshoffmann/Projects/github.com/lifs-tools/lifs-apps`):
- Modify: `pom.xml` (`lifs.webapps.common.version` → `2.0.1`), `src/main/resources/templates/index.html`, `portal.html`, `stats.html`, `error.html` (add `<th:block th:replace="~{lifs/layout :: tracking}"></th:block>` before `</body>`)
- Modify: `e2e/scripts/start-app.sh` (add `--ga.id=9`), the test properties used by `PortalPagesIT` (add `ga.id=9`)
- Test: `e2e/tests/tracking.spec.ts`, and an assertion in `src/test/java/org/lifstools/lifsapps/webapp/PortalPagesIT.java` (failsafe)

**Interfaces:**
- Consumes: `lifs/layout :: tracking` and its DOM contract (Plan 1 Task 3: `data-testid` `consent-banner`, `consent-accept`, `consent-deny`, `consent-dnt`).

- [ ] **Step 1: Write the failing tests**

```java
// in PortalPagesIT
@Test void portalIncludesTrackingScript()   // GET / → script[data-lifs-tracking][data-site-id=9]
```
```ts
// e2e/tests/tracking.spec.ts (route https://lifs-tools.org/matomo/** to an empty 200 and record requests)
test('banner shows and accept loads matomo')            // [data-testid=consent-banner] visible; accept → one matomo.js request
test('consent carries over from another app')           // context.addCookies lifs-cookies-accepted=on, path=/ → no banner, matomo.js requested
```

- [ ] **Step 2: Run them to see them fail**

Run: `./mvnw -B verify -Dit.test=PortalPagesIT` and then `cd e2e && npm test -- tracking.spec.ts` (with Keycloak up, as the lifs-apps README says)
Expected: FAIL.

- [ ] **Step 3: Implement** as listed under Files.

- [ ] **Step 4: Run the tests and check by hand**

Run: `./mvnw -B verify && cd e2e && npm test`
Expected: everything PASSES. By hand: the statistics page still renders its charts with the consent banner present.

- [ ] **Step 5: Commit and open a pull request**

```bash
git add pom.xml src e2e/tests/tracking.spec.ts
git commit -m "Turn Matomo tracking back on with the consent fragment from lifs-webapps-common 2.0.1"
```

### Task 2: Goslin 2.3.0 on the shared volume

**Files** (in `/Users/nilshoffmann/Projects/github.com/lifs-tools/docker-recipes-external`):
- Modify: `external/applications/docker-compose-goslin.yml`

- [ ] **Step 1: Ask Nils for the shared volume.** Ask whether it is a host path present on every manager node, or a named volume with a driver, and for its exact name or path. Ask which user ID owns it. Do not guess. Every later step depends on the answer.

- [ ] **Step 2: Change the compose file**
  - `image: docker.lifs-tools.org/lifs/lifs-goslin-webapp:2.3.0`
  - Add a volume to the service, mounted at `/data/goslin`. Declare it in the top-level `volumes:` following the `driver_opts` pattern of `docker-compose-artifactory.yml`, with the device from Step 1.
  - In `SPRING_APPLICATION_JSON`:
    - add `"storage.location":"/data/goslin/upload-dir"`,
    - `server.tomcat.internal-proxies`, `protocol-header`, `remote-ip-header` → `server.tomcat.remoteip.internal-proxies`, `remoteip.protocol-header`, `remoteip.remote-ip-header` (as `docker-compose-lifs-apps-webapp.yml` already has),
    - `server.forward-headers-strategy` → `"server.request.header.forwarded.strategy":"web-server"`,
    - `server.error.include-stacktrace` → `"spring.web.error.include-stacktrace":"never"`,
    - `server.error.include-exception` → `"spring.web.error.include-exception":"false"` (production should not show stack traces),
    - remove `keycloak.credentials.secret`.
  - Keep `replicas: 2` and the update settings.

- [ ] **Step 3: Validate and check after the deploy**

Run: `docker compose -f external/applications/docker-compose-goslin.yml config -q`
Expected: no output (valid).

After Nils deploys:
- Create a result and open its `/result/<id>` link ten times in private windows. Every load shows it, whichever replica answers.
- `docker service ps` shows both replicas running.
- An old `/download/<id>/OUTPUT_TSV_FILE` link returns 404.

- [ ] **Step 4: Commit and open a pull request**

```bash
git add external/applications/docker-compose-goslin.yml
git commit -m "Goslin 2.3.0: results on the shared volume, Spring Boot 4.2 property names"
```

### Task 3: lifs-apps image in production

**Files:** `external/applications/docker-compose-lifs-apps-webapp.yml` (in docker-recipes-external)

- [ ] **Step 1: Ask Nils first.** Production still runs `lifs-apps-webapp:1.1.44`. Moving to the release that contains Task 1 also deploys the whole 2.x line (OIDC login, Spring Boot 4.2), not only tracking. Ask whether 2.x is cleared for production. If it is not, stop here; tracking in lifs-apps goes live with whatever deploys 2.x.
- [ ] **Step 2:** If cleared, set `image:` to the lifs-apps release that contains Task 1. Move any `server.error.*` and `server.forward-headers-strategy` entries to the Boot 4.2 names as in Task 2, and keep `"ga.id":"9"`. Validate with `docker compose -f external/applications/docker-compose-lifs-apps-webapp.yml config -q`.
- [ ] **Step 3: Commit and open a pull request**

```bash
git add external/applications/docker-compose-lifs-apps-webapp.yml
git commit -m "lifs-apps 2.x with Matomo tracking"
```
