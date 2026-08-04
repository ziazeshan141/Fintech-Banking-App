pipeline {
    agent any

    environment {
        SCANNER_HOME = tool 'SonarQube Scanner'
        DOCKERHUB_CREDENTIALS = credentials('dockerhub-credentials')
        GITHUB_CREDENTIALS = credentials('github-credentials')
        IMAGE_NAME = "ziazeshan141/fintech-banking-app"
        IMAGE_TAG = "{BUILD_NUMBER}"
        GITOPS_REPO = 'github.com/ziazeshan141/fintech-banking-app-gitops.git'
    }

    stages {
        stage('Checkout Source Code') {
            steps {
                git scm
            }
        } 

        stage('Build Java Application') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage( 'SonarQube Code Analysis' ) {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh "${SCANNER_HOME}/bin/sonar-scanner \
                        -Dsonar.projectKey=fintech-banking-app \ 
                        -Dsonar.sources=src/main/java \ 
                        -Dsonar.java.binaries=target/classes"
                }
            }
        }

        stage( 'SonarQube Quality Gate' ) {
            steps {
                timeout(time: 5, unit: 'MINUTES') {
                    script {
                    waitForQualityGate abortPipeline: true
                    }
                }    
            }
        }

        stage('Build Docker Image') {
            steps {
                sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
            }
        }

        stage(' Trivy Vulnerability Scan') {
            steps {
                // Fail the build if any HIGH or CRITICAL vulnerabilities are found
                sh "trivy image --exit-code 1 --severity HIGH,CRITICAL ${IMAGE_NAME}:${IMAGE_TAG}"
            }
        }

        stage('Push Docker Image to Docker Hub') {
            steps {
                script {
                    // Login to Docker Hub and push the image
                    withCredentials([usernamePassword(credentialsId: 'dockerhub-credentials', usernameVariable: 'DOCKERHUB_USERNAME', passwordVariable: 'DOCKERHUB_PASSWORD')]) {
                        sh "echo $DOCKERHUB_PASSWORD | docker login -u $DOCKERHUB_USERNAME --password-stdin"
                        sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
                    }
                }
                
            }
        }

        stage('Update GitOps Repository') {
            steps {
                script {
                    sh """
                    // Clone the GitOps repository
                    git clone https://${GITHUB_CREDENTIALS_USR}:${GITHUB_CREDENTIALS_PSW}@${GITOPS_REPO} temp-gitops
                    cd temp-gitops/k8s

                    // Update the image tag in the deployment YAML file
                    sh sed -i 's|image: ${IMAGE_NAME}:.*|image: ${IMAGE_NAME}:${IMAGE_TAG}|' banking-app-deployment.yaml
                    git config user.name "Jenkins CI"
                    git config user.email "jenkins@devsecops.com"
                    git add banking-app-deployment.yaml
                    git commit -m "Update image tag to ${IMAGE_TAG}"
                    git push origin main
                    """
                }
            }
        }
    }     

}