pipeline {
    agent any
    
    environment {
        COMPOSE_PROJECT = 'spring-backend'
        DOCKER_NETWORK = 'app-network'
    }
    
    stages {
        stage('Checkout') {
            steps {
                echo 'Checking out code from GitHub...'
                checkout scm
            }
        }
        
        stage('Create Network') {
            steps {
                script {
                    echo 'Creating Docker network if not exists...'
                    sh """
                        docker network create ${DOCKER_NETWORK} || true
                    """
                }
            }
        }
        
        stage('Stop Old Containers') {
            steps {
                script {
                    echo 'Stopping old containers...'
                    sh """
                        docker compose down || true
                    """
                }
            }
        }
        
        stage('Build and Deploy') {
            steps {
                script {
                    echo 'Building and deploying with docker-compose...'
                    sh """
                        docker compose up -d --build
                    """
                }
            }
        }
        
        stage('Wait for Health Check') {
            steps {
                script {
                    echo 'Waiting for services to be healthy...'
                    sh """
                        timeout 120 sh -c 'until docker inspect --format="{{.State.Health.Status}}" mysql-db | grep -q healthy; do sleep 2; done' || true
                        sleep 10
                    """
                }
            }
        }
        
        stage('Verify Deployment') {
            steps {
                script {
                    echo 'Verifying deployment...'
                    sh """
                        docker compose ps
                        docker logs spring-backend --tail=50
                    """
                }
            }
        }
        
        stage('Clean Up') {
            steps {
                script {
                    echo 'Cleaning up unused Docker resources...'
                    sh """
                        docker image prune -f
                        docker volume prune -f
                    """
                }
            }
        }
    }
    
    post {
        success {
            echo 'Spring Boot deployment successful!'
        }
        failure {
            echo 'Spring Boot deployment failed!'
            sh """
                docker compose logs || true
            """
        }
    }
}