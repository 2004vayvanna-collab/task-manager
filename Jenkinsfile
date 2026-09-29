pipeline {
  agent any
  options { timestamps(); disableConcurrentBuilds(); timeout(time: 30, unit: 'MINUTES'); skipDefaultCheckout(true) }
  parameters {
    string(name: 'DOCKER_IMAGE', defaultValue: 'haithem77/task-manager', description: 'Your Docker Hub repository')
    string(name: 'GITOPS_REPO_URL', defaultValue: 'https://github.com/YOUR_GITHUB_USERNAME/task-manager-gitops.git', description: 'Separate GitOps repository, HTTPS URL')
  }
  stages {
    stage('Checkout') {
      steps {
        checkout scm
        script { env.IMAGE_TAG = "${env.BUILD_NUMBER}-${sh(script: 'git rev-parse --short=12 HEAD', returnStdout: true).trim()}" }
      }
    }
    stage('Validate configuration') {
      steps {
        sh '''
          test "$GITOPS_REPO_URL" != 'https://github.com/2004vayvanna-collab/task-manager-gitops.git'
          python3 -c 'import os,re; assert re.fullmatch(r"[a-z0-9][a-z0-9._/-]*",os.environ["DOCKER_IMAGE"]), "Invalid image name"'
        '''
      }
    }
    stage('Build and test') {
      steps { sh 'mvn -B clean verify' }
      post { always { junit 'target/surefire-reports/*.xml' } }
    }
    stage('SonarQube analysis') {
      steps {
        withSonarQubeEnv('sonarqube') {
          sh 'mvn -B sonar:sonar -Dsonar.token="$SONAR_AUTH_TOKEN"'
        }
      }
    }
    stage('Quality gate') {
      steps { timeout(time: 10, unit: 'MINUTES') { waitForQualityGate abortPipeline: true } }
    }
    stage('Build and push image') {
      steps {
        withCredentials([usernamePassword(credentialsId: 'dockerhub', usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_TOKEN')]) {
          sh '''
            set +x
            export DOCKER_CONFIG="$(mktemp -d)"
            trap 'rm -rf "$DOCKER_CONFIG"' EXIT
            printf '%s' "$DOCKER_TOKEN" | docker login --username "$DOCKER_USER" --password-stdin
            docker build -t "$DOCKER_IMAGE:$IMAGE_TAG" .
            docker push "$DOCKER_IMAGE:$IMAGE_TAG"
          '''
        }
      }
    }
    stage('Update GitOps repository') {
      steps {
        withCredentials([gitUsernamePassword(credentialsId: 'gitops-token', gitToolName: 'Default')]) {
          dir('gitops-work') {
            deleteDir()
            sh '''
              git clone --branch main --single-branch "$GITOPS_REPO_URL" .
              python3 ../scripts/update_image.py k8s/kustomization.yaml "$DOCKER_IMAGE" "$IMAGE_TAG"
              git config user.name 'Jenkins DevOps Lab'
              git config user.email 'jenkins@example.invalid'
              git add k8s/kustomization.yaml
              git commit -m "Deploy task-manager $IMAGE_TAG"
              git push origin HEAD:main
            '''
          }
        }
      }
    }
  }
  post { success { echo 'Image published and GitOps updated. Check Argo CD for deployment health.' } }
}
