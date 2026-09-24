# Upgrade your running application

This release adds editing, optional due dates with overdue labels, account registration, sign-in/sign-out, and personal task lists. Passwords are BCrypt hashes. Authenticated sessions are stored in PostgreSQL so both Kubernetes replicas can recognize the same session. CSRF protection remains enabled.

## 1. Back up your current database

In WSL, inside your **existing** task-manager folder with the current Compose file:

```bash
docker compose exec -T db pg_dump -U taskmanager -d taskmanager > ../task-manager-before-v2.sql
```

Check that the command succeeds and the backup file is not empty. Keep this file private: after accounts exist, database backups contain password hashes and session data. The `-T` option makes redirection work without a pseudo-terminal.

## 2. Update the code in the same project folder

Extract the updated ZIP to a temporary folder. In your existing project, replace `src/` and `pom.xml` with the new copies, and copy `scripts/assign-legacy-tasks.sql` and the new docs. Keep your existing `.env`, `compose.yaml`, and customized Jenkins/Argo CD/Terraform settings.

Use the **same original project folder** so Docker Compose continues using the same database volume. Your app's existing port mapping should remain `127.0.0.1:8081:8080`. A fresh copy of this release defaults to that mapping too.

Do not delete the Docker volume or run `docker compose down -v`.

## 3. Rebuild the app

```bash
docker compose up -d --build app
docker compose logs -f app
```

Flyway runs migrations V2 and V3 automatically. V1 is unchanged. The migrations add user accounts, due dates, ownership, and shared sessions without deleting tasks. Once the startup message appears, open http://localhost:8081 and refresh the page with Ctrl+Shift+R.

## 4. Create your account

Click **Create an account**. Username: 3–40 lowercase letters, digits or underscores. Password: at least 10 characters, at most 72 UTF-8 bytes (BCrypt's limit). Registration signs you in through the normal login endpoint.

Pre-upgrade tasks are preserved but not assigned to any user. They will not appear in anyone's task list until you complete the next step. New tasks belong to the signed-in user automatically.

## 5. Assign your old tasks to your account

From the existing project folder, replace `your_username` with the account you just registered:

```bash
docker compose exec -T db psql -U taskmanager -d taskmanager -v owner=your_username < scripts/assign-legacy-tasks.sql
```

The result prints `UPDATE N`, the number assigned. `UPDATE 0` means the username does not exist or there are no unassigned tasks. Refresh your browser. This step only assigns tasks whose owner is empty; it does not transfer another user's tasks.

## Try the features

1. Add a task with a due date before today: it shows an **Overdue** label.
2. Click **Edit**, change the title, priority and date, then **Save changes**.
3. Clear the date and save to remove a deadline. Click **Cancel** to discard an edit.
4. Complete the task: it no longer shows as overdue.
5. Sign out and create another account. Its task list should be empty.
6. Sign back into your first account. Its tasks should still be there.

## CI/CD and deployment notes

- Maven's tests now cover authentication, CSRF, ownership isolation, editing, dates, registration and logout.
- Jenkins and the Dockerfile keep the same entry points; no new Jenkins secret is required.
- PostgreSQL-backed sessions support your existing two app replicas.
- The first app startup creates the new tables through Flyway. Do not edit V1 after it has already been applied.
- When deploying behind HTTPS, set `SESSION_COOKIE_SECURE=true` for the app. Leave it false for this HTTP localhost lab.
- This release does not include password reset, email verification, login rate limiting or account deletion. Add those before treating it as a public production service.
- For a controlled first upgrade on Kubernetes, stop old app replicas before letting Argo CD deploy this release. Otherwise the old unauthenticated version can briefly continue serving requests alongside the new version. No Kubernetes upgrade has been performed for you.

## Rollback

Keep your backup and old source ZIP. The old application can read the expanded schema, but it has no ownership protection and would list everyone's tasks. Do not roll back to it after different users have started using this version. For this private lab, a full database restore is the recovery path; it replaces newer data, so stop the app and review what will be lost first.
