# Verification — account and due-date update

## Passed

- Maven Java compilation, 11 automated tests (zero failures/errors), executable JAR packaging and JaCoCo report generation.
- Task creation, editing, priority change, completion, removal of a due date and deletion.
- Invalid task input and weak-password rejection; duplicate username rejection and BCrypt password storage.
- Unauthenticated access rejection and CSRF enforcement for writes.
- Cross-account task isolation: another user cannot list, edit or delete someone else's task.
- Registration, session-based login, persisted-session access and logout invalidation.
- Flyway V1-to-V3 upgrade with an existing task: the task survives and remains unassigned.
- Live HTTP check using actual cookies and CSRF tokens: registration, login, token refresh, task editing, deadline removal, logout and subsequent access rejection.
- Frontend JavaScript syntax check.

## Limits

Tests and live HTTP checks use H2 in PostgreSQL compatibility mode. Real PostgreSQL migration and Docker builds must still be verified in your local environment. Browser visual/interaction testing was unavailable here. Jenkins/SonarQube, Kubernetes/Argo CD, and Terraform/AWS were not executed. No changes were made to your running laptop application or external accounts.

Read UPGRADE.md before rebuilding your local app. Keep your database volume and back up the database first.
