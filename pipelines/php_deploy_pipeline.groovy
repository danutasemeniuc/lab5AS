pipeline {
    agent none
    
    environment {
        PHP_PROJECT_REPO = 'https://github.com/YOUR_USERNAME/YOUR_PHP_PROJECT.git'
        PHP_PROJECT_BRANCH = 'main'
    }
    
    stages {
        stage('Clone Repository on Ansible Agent') {
            agent {
                label 'ansible-agent'
            }
            steps {
                echo 'Cloning PHP project repository...'
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: "*/${PHP_PROJECT_BRANCH}"]],
                    userRemoteConfigs: [[url: "${PHP_PROJECT_REPO}"]]
                ])
                
                stash includes: '**/*', name: 'php-app-source'
            }
        }
        
        stage('Deploy to Test Server') {
            agent {
                label 'ansible-agent'
            }
            steps {
                unstash 'php-app-source'
                
                echo 'Deploying PHP application using Ansible...'
                sh '''
                    cd /home/jenkins/ansible
                    ansible-playbook -i hosts.ini deploy_php_app.yml \
                        -e "workspace_path=${WORKSPACE}" -v
                '''
            }
        }
        
        stage('Verify Deployment') {
            agent {
                label 'ansible-agent'
            }
            steps {
                echo 'Verifying deployment...'
                sh '''
                    cd /home/jenkins/ansible
                    ansible test_servers -m shell \
                        -a "ls -la /var/www/html/phpapp" -b
                    ansible test_servers -m shell \
                        -a "curl -s http://localhost/info.php | head -n 5" -b
                '''
            }
        }
    }
    
    post {
        success {
            echo 'Deployment completed successfully!'
        }
        failure {
            echo 'Deployment failed!'
        }
    }
}