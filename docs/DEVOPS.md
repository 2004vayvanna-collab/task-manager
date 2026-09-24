# DevOps path

Run shell commands in WSL Ubuntu, from the project root unless stated otherwise. Keep Jenkins and SonarQube running where you already installed them. An EC2 Jenkins server cannot directly reach your laptop's private Minikube network; the GitOps design lets Argo CD pull changes from GitHub instead.

## 1. Test the application

With Java 17 and Maven 3.6.3+ installed:

```bash
mvn -B clean verify
```

Or use Maven in Docker from the project directory:

```bash
docker run --rm -v "$PWD:/app" -w /app maven:3.9.9-eclipse-temurin-17 mvn -B clean verify
```

Tests use H2 in PostgreSQL mode. The real local app uses PostgreSQL. Coverage appears at `target/site/jacoco/index.html`.

## 2. Create two GitHub repositories

Create `task-manager` for application code, and `task-manager-gitops` for deployment configuration. GitOps can be public for this lab because it contains no secrets. Set your Git name and email first if needed.

Push this project to the app repository (replace YOUR_GITHUB_USERNAME):

```bash
git init -b main
git add .
git commit -m "Build Task Manager DevOps lab"
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/task-manager.git
git push -u origin main
```

Initialize the separate GitOps repository with a copy of `k8s/`:

```bash
mkdir ../task-manager-gitops
cp -r k8s ../task-manager-gitops/
cd ../task-manager-gitops
git init -b main
git add .
git commit -m "Add Kubernetes manifests"
git remote add origin https://github.com/YOUR_GITHUB_USERNAME/task-manager-gitops.git
git push -u origin main
cd ../task-manager
```

After that, edit Kubernetes configuration in the **GitOps repository**; the copy in the app repository is the initial template. Jenkins updates only the separate GitOps repository, preventing a build-trigger loop.

## 3. Configure Jenkins and SonarQube

Create a dedicated Jenkins agent with label `devops-lab`. It needs Java 17+, Maven, Git, Python 3, Docker CLI, access to a Docker engine, and network access to GitHub, Docker Hub, Maven Central and your SonarQube server. Docker access gives the agent control over its host; use this agent only for your trusted lab code.

Jenkins plugins: Pipeline, Git, Credentials Binding, JUnit, SonarQube Scanner, and Timestamper. In **Manage Jenkins → Tools**, the Git installation name must be `Default`, as used in `Jenkinsfile`.

Add Jenkins credentials:

| ID | Type | Value |
|---|---|---|
| `dockerhub` | Username/password | Docker Hub username and access token |
| `sonarqube` | Secret text | SonarQube analysis token |
| `gitops-token` | Username/password | GitHub username and token with Contents read/write for the GitOps repository |

In **Manage Jenkins → System**, add a SonarQube installation named `sonarqube`, its actual reachable URL, and the `sonarqube` credential. In SonarQube, create project key `task-manager` and configure the quality gate you want to enforce. Add a webhook to `http://YOUR_JENKINS_ADDRESS/sonarqube-webhook/` (trailing slash required). SonarQube must be able to reach that address.

Create a Pipeline job using **Pipeline script from SCM**, Git, your app repository, branch `*/main`, script path `Jenkinsfile`. First build exposes the parameters. Set `GITOPS_REPO_URL` to your actual GitOps repository URL. `DOCKER_IMAGE` defaults to `haithem77/task-manager`; change it if needed. Create that repository in Docker Hub; use a public image for this lab, or separately configure a Kubernetes image-pull secret.

Stages:

1. Checkout app source.
2. Validate configuration.
3. Maven build, tests and JaCoCo coverage.
4. SonarQube analysis.
5. Wait for the quality gate; fail before publishing if rejected.
6. Build and push an image tagged `<build-number>-<git-commit>`.
7. Commit that exact image tag to the GitOps repository.

Jenkins success means the image and GitOps commit were published. Argo CD health confirms the actual deployment. Use a GitHub webhook for automatic builds if GitHub can reach Jenkins; otherwise enable SCM polling `H/5 * * * *` in the job. Never expose an unsecured Jenkins server merely to make a webhook work.

## 4. Terraform + local Kubernetes

Use your existing Minikube cluster. If stopped, start it first:

```bash
minikube start --driver=docker
kubectl config use-context minikube
terraform -chdir=infra/local init
terraform -chdir=infra/local plan
terraform -chdir=infra/local apply
```

Terraform creates only the `task-manager` namespace. Kubernetes workloads are managed later by Argo CD. If the namespace already exists, import it before applying:

```bash
terraform -chdir=infra/local import kubernetes_namespace_v1.lab task-manager
```

The namespace has `prevent_destroy` because deleting it would also delete its workloads and PVCs. Store Terraform state securely; commit the generated `.terraform.lock.hcl`, never state files.

Create the database secret separately from Git. This reads a password without placing it in shell history:

```bash
read -rsp 'New lab database password: ' LAB_DB_PASSWORD
printf '\n'
printf '%s' "$LAB_DB_PASSWORD" | kubectl -n task-manager create secret generic task-manager-db --from-file=password=/dev/stdin
unset LAB_DB_PASSWORD
```

Do this once per cluster. Updating the secret alone does not rotate an existing PostgreSQL user's password.

Check that a default StorageClass exists: `kubectl get storageclass`. Standard Minikube installations provide one. The database StatefulSet requests a 1 GiB PVC.

### Optional: test Kubernetes before Jenkins

```bash
docker build -t haithem77/task-manager:local .
minikube image load haithem77/task-manager:local
kubectl apply -k k8s
kubectl -n task-manager rollout status statefulset/postgres --timeout=180s
kubectl -n task-manager rollout status deployment/task-manager --timeout=300s
kubectl -n task-manager port-forward service/task-manager 8082:80
```

Open http://localhost:8082. The `local` image is only available on this Minikube cluster; do not use that tag on AWS. Stop port forwarding with Ctrl+C.

## 5. Argo CD

Use your existing Argo CD installation in namespace `argocd`. If you create a fresh cluster, install Argo CD using its official getting-started instructions and a pinned release: https://argo-cd.readthedocs.io/en/stable/getting_started/

In `argocd/application.yaml`, replace `YOUR_GITHUB_USERNAME` with the owner of your GitOps repository. For a private GitOps repo, first connect it in Argo CD using repository credentials. Run a successful Jenkins build to publish a real image and update the GitOps tag, then:

```bash
kubectl apply -f argocd/application.yaml
kubectl -n argocd get application task-manager
kubectl -n task-manager get pods,pvc
kubectl -n task-manager port-forward service/task-manager 8082:80
```

Argo CD reads `k8s/` on the GitOps repository's `main` branch. Automatic sync and self-healing are enabled. Automatic pruning is disabled for this beginner database lab; deletions need deliberate review.

**Practice:** change the heading in `src/main/resources/static/index.html`, commit and push the app repository. Watch Jenkins produce a new image, the GitOps commit change its tag, and Argo CD roll out the app. Refresh the browser after the rollout.

**Rollback:** revert the deployment commit in the GitOps repository and push. Argo CD restores that image version. Reverting an image does not undo database migrations; keep future schema changes backward-compatible.

## 6. Optional AWS deployment with Terraform

This module creates billable AWS resources: one `t3.medium` EC2 instance, a 30 GiB disk, a public IPv4 address, and a VPC. It installs a single-node K3s cluster. Jenkins and SonarQube stay on your existing host; do not add them to this small Kubernetes VM. No AWS resources have been created for you.

Prerequisites: configured AWS CLI credentials, Terraform, an SSH public/private key pair, your current public IPv4 address, and an explicit supported K3s release from https://github.com/k3s-io/k3s/releases . The module requires a pinned version instead of choosing one silently.

```bash
cp infra/aws/terraform.tfvars.example infra/aws/terraform.tfvars
# Edit region, admin_cidr, ssh_public_key, and k3s_version.
terraform -chdir=infra/aws init
terraform -chdir=infra/aws plan
# Review resources and current AWS pricing before the next command.
terraform -chdir=infra/aws apply
terraform -chdir=infra/aws output -raw public_ip
```

SSH to `ubuntu@PUBLIC_IP` using the matching private key. Check bootstrap:

```bash
sudo cloud-init status --wait
sudo kubectl get nodes
```

On that VM, create the namespace and database secret (step 4, using `sudo kubectl`), install Argo CD, and apply your configured `argocd/application.yaml`. K3s includes local-path storage for the database PVC. The same GitOps repository and published Docker Hub image can be used; this creates an independent database, not a copy of your local data.

Only SSH from `admin_cidr` is allowed inbound. To view the app, on the VM run:

```bash
sudo kubectl -n task-manager port-forward service/task-manager 8080:80
```

Keep it running. From a second WSL terminal on your laptop:

```bash
ssh -N -L 8082:127.0.0.1:8080 ubuntu@PUBLIC_IP
```

Open http://localhost:8082. Add `-i /path/to/key` to SSH if your private key is not in its default location. This release has user accounts. A public release still needs TLS, login rate limiting, account recovery, backups, and an ingress plan. See UPGRADE.md for the first migration from the unauthenticated version.

When finished with the disposable cloud lab, export any data you need, then run `terraform -chdir=infra/aws destroy`. Destroying the VM removes its disk and database. A changed public IP at home also requires updating `admin_cidr` and applying it again.

## Troubleshooting

| Symptom | Check |
|---|---|
| Docker command not found in WSL | Docker Desktop WSL integration and running engine |
| App fails to connect to database | App logs, secret/password, PostgreSQL readiness |
| ImagePullBackOff | Correct image tag, published image, repository visibility |
| PVC Pending | A working default StorageClass exists |
| Quality gate waits until timeout | SonarQube webhook URL and reachability to Jenkins |
| Jenkins cannot push GitOps | Token repository permissions and branch protection |
| Argo CD OutOfSync | Repository URL, branch, path, repository credentials |
| Rollout fails | `kubectl -n task-manager describe pod POD_NAME` and pod logs |

## Official references

- Spring Boot requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- SonarQube pipeline gate: https://docs.sonarsource.com/sonarqube-server/analyzing-source-code/ci-integration/jenkins-integration/pipeline-pause
- Argo CD CI automation: https://argo-cd.readthedocs.io/en/stable/user-guide/ci_automation/
- K3s quick start: https://docs.k3s.io/quick-start
- AWS Terraform provider: https://registry.terraform.io/providers/hashicorp/aws/latest/docs
