FROM jenkins/ssh-agent

# Instalează PHP-CLI pentru rularea testelor
RUN apt-get update && apt-get install -y php-cli