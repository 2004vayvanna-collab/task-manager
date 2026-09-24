# Task Manager — accounts, editing and due dates

**Already running the earlier version? Start with [docs/UPGRADE.md](docs/UPGRADE.md) to preserve your existing database and assign your old tasks.**

A Spring Boot 3.5 / Java 17 application with PostgreSQL, a browser UI, and a complete CI/CD learning path. Add tasks, choose a priority, mark them complete, filter the list, and delete tasks. Register or sign in to your own task list. Use localhost or an SSH tunnel for this learning lab.

## Start here — Windows + WSL Ubuntu

Start Docker Desktop and enable its WSL integration for Ubuntu. Extract this project, open the **Ubuntu terminal**, and enter the `task-manager` folder. Use these commands there (not in Jenkins or the browser):

```bash
cp .env.example .env
nano .env
# Set DB_PASSWORD to your own local database password, save, then:
docker compose up --build -d
docker compose logs -f app
```

When the log says `Started TaskManagerApplication`, open **http://localhost:8081** in your Windows browser. Press Ctrl+C to leave the logs; the containers keep running. The first build downloads dependencies and images.

The app uses host port 8081 so it can coexist with Jenkins on port 8080.

Create an account in the browser first.

**First practical task:** add “Learn Docker volumes”, choose High priority, and mark it complete. Run `docker compose restart` and refresh the page. Your task should still be there.

Check questions:
1. Which container stores the task data?
2. Why does restarting the application keep the tasks?
3. What is the difference between the Docker image and a running container?

```bash
docker compose ps          # App and healthy database
docker compose logs app    # Diagnose startup errors
docker compose down        # Stop; retain your database volume
```

Do not use `down -v` unless you intend to erase the lab database. Changing `.env` later does not change the password inside an already-initialized PostgreSQL volume.

## Learning stages

| Stage | What you build | Proof it works |
|---|---|---|
| 1. Application + Docker | Local app and persistent PostgreSQL | Create a task; restart; task remains |
| 2. Tests | API lifecycle, validation, and health checks | `mvn clean verify` passes |
| 3. Jenkins + SonarQube | Tests, coverage report, analysis and quality gate | Pipeline stops on a failed quality gate |
| 4. Kubernetes + Terraform | Namespace, database volume, app replicas, probes | Both app pods become Ready |
| 5. Argo CD | Git controls the app image version | A GitOps commit triggers a rollout |
| 6. AWS + Terraform | Optional private-access K3s lab | Reach the app through an SSH tunnel |

Follow [docs/DEVOPS.md](docs/DEVOPS.md) after stage 1 works. Do not try to install every tool at once.

## Project map

- `src/main/java/`: REST API and database model.
- `src/main/resources/static/`: UI, with no frontend build step.
- `src/main/resources/db/migration/`: Flyway schema migrations.
- `src/test/`: automated API tests (H2 in PostgreSQL compatibility mode).
- `Dockerfile`, `compose.yaml`: local app and PostgreSQL.
- `Jenkinsfile`: CI pipeline, quality gate, image push, GitOps commit.
- `k8s/`: database StatefulSet, persistent storage, app Deployment and Service.
- `argocd/`: application pointing to your separate GitOps repository.
- `infra/local/`: Terraform namespace exercise for existing Minikube.
- `infra/aws/`: optional Terraform AWS VM, network, and K3s bootstrap.

## API

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/tasks` | List newest tasks first |
| POST | `/api/tasks` | Create: `{"title":"Learn Docker","priority":"HIGH","dueDate":"2026-10-01"}` |
| PUT | `/api/tasks/{id}` | Update: title, priority, completed; optional dueDate |
| DELETE | `/api/tasks/{id}` | Delete a task |
| GET | `/actuator/health/readiness` | App and database readiness |

Priorities: `LOW`, `MEDIUM`, `HIGH`. Titles: 1–120 characters, nonblank. The UI renders task text as text, not HTML.

## Verification and limits

See `docs/VERIFICATION.md` for the exact checks performed. Docker/Kubernetes, Jenkins/SonarQube, Argo CD and AWS require your local services or accounts; those deployments have not been executed for you. PostgreSQL is persistent within its Docker volume or Kubernetes PVC; this is a single-database learning lab, not a backed-up, highly available production service.

Task API requests require authentication. Mutation requests also require a CSRF token from `/api/auth/csrf`; the browser UI handles this automatically. Dates use YYYY-MM-DD; omit or send null to remove a deadline. The overdue label uses the browser's local date.
