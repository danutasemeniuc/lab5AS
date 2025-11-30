pipeline {
    agent {
        label 'ansible-agent'
    }
    
    environment {
        ANSIBLE_CONFIG = '/home/jenkins/ansible/ansible.cfg'
        ANSIBLE_INVENTORY = '/home/jenkins/ansible/hosts.ini'
        PLAYBOOK_PATH = '/home/jenkins/ansible/setup_test_server.yml'
    }
    
    stages {
        stage('Verify Ansible Installation') {
            steps {
                echo 'Verifying Ansible installation...'
                sh '''
                    ansible --version
                    ansible-playbook --version
                '''
            }
        }
        
        stage('Check Connectivity') {
            steps {
                echo 'Checking connectivity to test server...'
                sh '''
                    cd /home/jenkins/ansible
                    ansible test_servers -m ping
                '''
            }
        }
        
        stage('Run Ansible Playbook') {
            steps {
                echo 'Executing Ansible playbook to configure test server...'
                sh '''
                    cd /home/jenkins/ansible
                    ansible-playbook -i ${ANSIBLE_INVENTORY} ${PLAYBOOK_PATH} -v
                '''
            }
        }
        
        stage('Verify Configuration') {
            steps {
                echo 'Verifying server configuration...'
                sh '''
                    cd /home/jenkins/ansible
                    ansible test_servers -m shell -a "systemctl status apache2" -b
                    ansible test_servers -m shell -a "php -v" -b
                '''
            }
        }
    }
    
    post {
        success {
            echo 'Test server configured successfully!'
        }
        failure {
            echo 'Failed to configure test server!'
        }
    }
}