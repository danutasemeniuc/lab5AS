pipeline {
    agent {
        label 'ssh-agent'
    }
    
    environment {
        PHP_PROJECT_REPO = 'https://github.com/YOUR_USERNAME/YOUR_PHP_PROJECT.git'
        PHP_PROJECT_BRANCH = 'main'
    }
    
    stages {
        stage('Clone Repository') {
            steps {
                echo 'Cloning PHP project repository...'
                git branch: "${PHP_PROJECT_BRANCH}", 
                    url: "${PHP_PROJECT_REPO}"
            }
        }
        
        stage('Install Dependencies') {
            steps {
                echo 'Installing Composer dependencies...'
                sh '''
                    composer install --no-interaction --prefer-dist --optimize-autoloader
                '''
            }
        }
        
        stage('Run Unit Tests') {
            steps {
                echo 'Running PHPUnit tests...'
                sh '''
                    vendor/bin/phpunit --testdox --colors=always
                '''
            }
        }
        
        stage('Generate Test Report') {
            steps {
                echo 'Generating test coverage report...'
                sh '''
                    vendor/bin/phpunit --coverage-text --colors=never > test-report.txt
                    cat test-report.txt
                '''
            }
        }
    }
    
    post {
        always {
            echo 'Cleaning up workspace...'
            cleanWs()
        }
        success {
            echo 'Build and tests completed successfully!'
        }
        failure {
            echo 'Build or tests failed!'
        }
    }
}