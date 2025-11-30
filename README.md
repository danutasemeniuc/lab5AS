# Laborator 05 - Automatizarea Configurării Serverelor cu Ansible

## 📋 Cuprins

1. [Descrierea Proiectului](#descrierea-proiectului)
2. [Arhitectura Sistemului](#arhitectura-sistemului)
3. [Configurarea Jenkins Controller](#configurarea-jenkins-controller)
4. [Configurarea SSH Agent](#configurarea-ssh-agent)
5. [Crearea și Configurarea Ansible Agent](#crearea-și-configurarea-ansible-agent)
6. [Crearea Test Server](#crearea-test-server)
7. [Ansible Playbook - Descriere și Taskuri](#ansible-playbook---descriere-și-taskuri)
8. [Pipeline-urile Jenkins](#pipelineurile-jenkins)
9. [Rezultate și Testare](#rezultate-și-testare)
10. [Răspunsuri la Întrebări](#răspunsuri-la-întrebări)
11. [Probleme Întâmpinate și Soluții](#probleme-întâmpinate-și-soluții)
12. [Concluzii](#concluzii)

---

## Descrierea Proiectului

Acest laborator își propune automatizarea completă a procesului de configurare și deployment a aplicațiilor PHP prin utilizarea Jenkins, Ansible și Docker. Proiectul implementează un workflow CI/CD complet care include:

- **Build automat** al proiectelor PHP cu rulare de teste unitare
- **Configurare automată** a serverelor de test folosind Ansible playbooks
- **Deployment automat** al aplicațiilor pe servere configurate
- **Orchestrare** a întregului proces prin Jenkins pipelines

### Obiective Principale

1. Automatizarea configurării infrastructurii folosind Ansible
2. Implementarea unui sistem CI/CD complet pentru aplicații PHP
3. Separarea responsabilităților prin containere Docker specializate
4. Gestionarea configurației prin Infrastructure as Code (IaC)

### Tehnologii Utilizate

- **Jenkins** - platformă de automatizare CI/CD
- **Ansible** - tool de configuration management
- **Docker & Docker Compose** - containerizare și orchestrare
- **PHP & Composer** - limbaj de programare și dependency manager
- **Apache2** - web server
- **SSH** - protocol de comunicare securizată

---

## Arhitectura Sistemului

Sistemul este compus din patru containere Docker principale, fiecare cu rol specific:

```
┌─────────────────────────────────────────────────────────────┐
│                     Jenkins Controller                       │
│                    (Orchestrator Central)                    │
│                      Port: 8080, 50000                       │
└───────────────┬─────────────────────┬───────────────────────┘
                │                     │
        ┌───────▼────────┐   ┌───────▼────────┐
        │   SSH Agent    │   │ Ansible Agent  │
        │  (PHP Build)   │   │ (Config Mgmt)  │
        │   Port: 22     │   │   Port: 22     │
        └────────────────┘   └───────┬────────┘
                                     │
                             ┌───────▼────────┐
                             │  Test Server   │
                             │ (Apache + PHP) │
                             │ Port: 22, 80   │
                             └────────────────┘
```

### Comunicare între Componente

1. **Jenkins Controller** → **SSH Agent**: Build și testare PHP
2. **Jenkins Controller** → **Ansible Agent**: Executare playbooks
3. **Ansible Agent** → **Test Server**: Configurare și deployment
4. **Browser** → **Test Server**: Accesare aplicație (port 8081)

---

## Configurarea Jenkins Controller

### 1. Definirea Serviciului în compose.yaml

```yaml
jenkins-controller:
  image: jenkins/jenkins:lts
  container_name: jenkins-controller
  privileged: true
  user: root
  ports:
    - "8080:8080"
    - "50000:50000"
  volumes:
    - jenkins_home:/var/jenkins_home
    - /var/run/docker.sock:/var/run/docker.sock
  networks:
    - jenkins-network
```

### 2. Pornirea și Configurarea Inițială

**Comandă de pornire:**
```bash
docker compose up -d jenkins-controller
```

**Obținerea parolei inițiale:**
```bash
docker exec jenkins-controller cat /var/jenkins_home/secrets/initialAdminPassword
```

### 3. Setup Wizard Jenkins

1. Accesare interfață web: `http://localhost:8080`
2. Introducere parolă inițială
3. Instalare plugin-uri sugerate
4. Creare cont administrator:
   - Username: `admin`
   - Password: `admin123`
   - Email: `admin@localhost`

### 4. Instalarea Plugin-urilor Necesare

Plugin-uri instalate prin **Manage Jenkins → Plugins**:

- **Docker Pipeline** - suport pentru comenzi Docker în pipeline-uri
- **Docker Plugin** - integrare Jenkins cu Docker
- **GitHub Integration** - conectare la repository-uri GitHub
- **SSH Agent Plugin** - gestionare credențiale SSH
- **SSH Build Agents** - conectare la agenți prin SSH
- **Ansible Plugin** - suport pentru comenzi Ansible

### 5. Verificare Instalare

```bash
# Verificare status container
docker ps | grep jenkins-controller

# Verificare logs
docker logs jenkins-controller

# Verificare acces web
curl -I http://localhost:8080
```

---

## Configurarea SSH Agent

### 1. Dockerfile pentru SSH Agent

**Fișier: `Dockerfile.ssh_agent`**

```dockerfile
FROM jenkins/ssh-agent:latest

USER root

RUN apt-get update && apt-get install -y \
    php-cli \
    php-mbstring \
    php-xml \
    php-curl \
    git \
    curl \
    unzip \
    && rm -rf /var/lib/apt/lists/*

RUN curl -sS https://getcomposer.org/installer | php -- \
    --install-dir=/usr/local/bin --filename=composer

RUN php --version && composer --version

USER jenkins
WORKDIR /home/jenkins
```

### 2. Generarea Cheilor SSH

```bash
# Creare director pentru chei
mkdir -p keys

# Generare cheie SSH pentru SSH Agent
ssh-keygen -t ed25519 -f keys/jenkins_ssh_agent \
  -C "jenkins-ssh-agent" -N ""
```

### 3. Configurarea Variabilelor de Mediu

**Fișier: `.env`**

```env
JENKINS_SSH_AGENT_PUBKEY=ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAICXJGwC/ocTU5ep/x6EggXieoebmKEQ0FlKVlSEelyFv danuta@DanutaPC
```

### 4. Definirea Serviciului

```yaml
ssh-agent:
  build:
    context: .
    dockerfile: Dockerfile.ssh_agent
  container_name: ssh-agent
  environment:
    - JENKINS_AGENT_SSH_PUBKEY=${JENKINS_SSH_AGENT_PUBKEY}
  volumes:
    - jenkins_agent_volume:/home/jenkins/agent
  networks:
    - jenkins-network
```

### 5. Build și Pornire

```bash
docker compose build ssh-agent
docker compose up -d ssh-agent
```

### 6. Configurarea în Jenkins

**Adăugare Credentials:**

1. Navigate: **Manage Jenkins → Credentials → Global**
2. Add Credentials:
   - Kind: `SSH Username with private key`
   - ID: `jenkins-ssh-agent-key`
   - Username: `jenkins`
   - Private Key: Conținut din `keys/jenkins_ssh_agent`

**Adăugare Node:**

1. Navigate: **Manage Jenkins → Nodes → New Node**
2. Configurare:
   - Name: `ssh-agent`
   - Type: `Permanent Agent`
   - Remote root: `/home/jenkins/agent`
   - Labels: `ssh-agent php`
   - Launch method: `Launch agents via SSH`
   - Host: `ssh-agent`
   - Credentials: `jenkins-ssh-agent-key`

---

## Crearea și Configurarea Ansible Agent

### 1. Generarea Cheilor SSH

```bash
# Cheie pentru Jenkins → Ansible Agent
ssh-keygen -t ed25519 -f keys/jenkins_ansible_agent \
  -C "jenkins-ansible-agent" -N ""

# Cheie pentru Ansible Agent → Test Server
ssh-keygen -t ed25519 -f keys/ansible_to_testserver \
  -C "ansible-to-testserver" -N ""
```

### 2. Dockerfile pentru Ansible Agent

**Fișier: `Dockerfile.ansible_agent`**

```dockerfile
FROM jenkins/ssh-agent:latest

USER root

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    python3-dev \
    openssh-client \
    sshpass \
    git \
    software-properties-common \
    && rm -rf /var/lib/apt/lists/*

RUN apt-get update && \
    apt-get install -y ansible || \
    pip3 install --break-system-packages ansible

RUN mkdir -p /home/jenkins/.ssh && \
    chown -R jenkins:jenkins /home/jenkins/.ssh && \
    chmod 700 /home/jenkins/.ssh

COPY keys/ansible_to_testserver /home/jenkins/.ssh/id_ed25519
COPY keys/ansible_to_testserver.pub /home/jenkins/.ssh/id_ed25519.pub

RUN chown jenkins:jenkins /home/jenkins/.ssh/id_ed25519* && \
    chmod 600 /home/jenkins/.ssh/id_ed25519 && \
    chmod 644 /home/jenkins/.ssh/id_ed25519.pub

RUN echo "Host *\n\tStrictHostKeyChecking no\n\tUserKnownHostsFile=/dev/null" \
    > /home/jenkins/.ssh/config && \
    chown jenkins:jenkins /home/jenkins/.ssh/config && \
    chmod 600 /home/jenkins/.ssh/config

RUN ansible --version

USER jenkins
WORKDIR /home/jenkins
```

### 3. Actualizare .env

```env
JENKINS_ANSIBLE_AGENT_PUBKEY=ssh-ed25519 [cheie_generată] jenkins-ansible-agent
```

### 4. Definirea Serviciului

```yaml
ansible-agent:
  build:
    context: .
    dockerfile: Dockerfile.ansible_agent
  container_name: ansible-agent
  environment:
    - JENKINS_AGENT_SSH_PUBKEY=${JENKINS_ANSIBLE_AGENT_PUBKEY}
  volumes:
    - ansible_agent_volume:/home/jenkins/agent
    - ./ansible:/home/jenkins/ansible
  networks:
    - jenkins-network
```

### 5. Configurarea în Jenkins

Similar cu SSH Agent, dar cu:
- ID: `jenkins-ansible-agent-key`
- Labels: `ansible`
- Private Key: Din `keys/jenkins_ansible_agent`

### 6. Testare Ansible

```bash
docker exec ansible-agent ansible --version
```

---

## Crearea Test Server

### 1. Dockerfile pentru Test Server

**Fișier: `Dockerfile.test_server`**

```dockerfile
FROM ubuntu:22.04

ENV DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y \
    openssh-server \
    apache2 \
    php \
    php-cli \
    php-mbstring \
    php-xml \
    php-curl \
    libapache2-mod-php \
    sudo \
    curl \
    supervisor \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir /var/run/sshd && \
    sed -i 's/#PermitRootLogin prohibit-password/PermitRootLogin no/' /etc/ssh/sshd_config && \
    sed -i 's/#PubkeyAuthentication yes/PubkeyAuthentication yes/' /etc/ssh/sshd_config && \
    sed -i 's/#PasswordAuthentication yes/PasswordAuthentication no/' /etc/ssh/sshd_config

RUN useradd -m -s /bin/bash ansible && \
    echo "ansible ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

RUN mkdir -p /home/ansible/.ssh && \
    chmod 700 /home/ansible/.ssh && \
    chown ansible:ansible /home/ansible/.ssh

COPY keys/ansible_to_testserver.pub /home/ansible/.ssh/authorized_keys
RUN chmod 600 /home/ansible/.ssh/authorized_keys && \
    chown ansible:ansible /home/ansible/.ssh/authorized_keys

RUN a2enmod rewrite
RUN mkdir -p /var/www/html/phpapp/public && \
    chown -R www-data:www-data /var/www/html/phpapp
RUN echo '<?php phpinfo(); ?>' > /var/www/html/phpapp/public/info.php

COPY supervisord.conf /etc/supervisor/conf.d/supervisord.conf

EXPOSE 22 80

CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
```

### 2. Configurare Supervisor

**Fișier: `supervisord.conf`**

```ini
[supervisord]
nodaemon=true
logfile=/var/log/supervisor/supervisord.log
pidfile=/var/run/supervisord.pid

[program:sshd]
command=/usr/sbin/sshd -D
autostart=true
autorestart=true
stderr_logfile=/var/log/supervisor/sshd.err.log
stdout_logfile=/var/log/supervisor/sshd.out.log

[program:apache2]
command=/usr/sbin/apache2ctl -D FOREGROUND
autostart=true
autorestart=true
stderr_logfile=/var/log/supervisor/apache2.err.log
stdout_logfile=/var/log/supervisor/apache2.out.log
```

### 3. Definirea Serviciului

```yaml
test-server:
  build:
    context: .
    dockerfile: Dockerfile.test_server
  container_name: test-server
  hostname: test-server
  networks:
    - jenkins-network
  ports:
    - "2222:22"
    - "8081:80"
```

### 4. Testare Conexiune SSH

```bash
docker exec ansible-agent ssh ansible@test-server "echo 'SSH OK'"
```

---

## Ansible Playbook - Descriere și Taskuri

### 1. Structura Directoarelor Ansible

```
ansible/
├── ansible.cfg
├── hosts.ini
├── setup_test_server.yml
├── deploy_php_app.yml
└── templates/
    └── vhost.conf.j2
```

### 2. Fișierul de Inventar

**Fișier: `ansible/hosts.ini`**

```ini
[test_servers]
test-server ansible_host=test-server ansible_user=ansible ansible_ssh_private_key_file=/home/jenkins/.ssh/id_ed25519 ansible_python_interpreter=/usr/bin/python3
```

### 3. Configurația Ansible

**Fișier: `ansible/ansible.cfg`**

```ini
[defaults]
inventory = hosts.ini
host_key_checking = False
retry_files_enabled = False
gathering = smart
fact_caching = jsonfile
fact_caching_connection = /tmp/ansible_facts
fact_caching_timeout = 3600

[ssh_connection]
ssh_args = -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null
pipelining = True
```

### 4. Playbook Principal - setup_test_server.yml

**Variabile Definite:**

```yaml
vars:
  php_version: "8.1"
  app_directory: "/var/www/html/phpapp"
  apache_document_root: "{{ app_directory }}/public"
  server_name: "test-server.local"
```

**Taskuri Implementate:**

#### Task 1: Actualizare Cache APT
```yaml
- name: Update apt cache
  apt:
    update_cache: yes
    cache_valid_time: 3600
```
*Scop:* Asigurarea că lista de pachete este actualizată pentru instalări ulterioare.

#### Task 2: Instalare Apache2
```yaml
- name: Install Apache2
  apt:
    name: apache2
    state: present
```
*Scop:* Instalarea web server-ului Apache2.

#### Task 3: Instalare PHP și Extensii
```yaml
- name: Install PHP and extensions
  apt:
    name:
      - php8.1
      - php8.1-cli
      - php8.1-common
      - php8.1-mbstring
      - php8.1-xml
      - php8.1-curl
      - php8.1-mysql
      - libapache2-mod-php8.1
    state: present
```
*Scop:* Instalarea PHP cu toate extensiile necesare pentru aplicații web moderne.

#### Task 4: Activare Module Apache
```yaml
- name: Enable Apache modules
  apache2_module:
    name: "{{ item }}"
    state: present
  loop:
    - rewrite
    - php8.1
  notify: Restart Apache
```
*Scop:* Activarea modulelor Apache necesare (URL rewriting și PHP processing).

#### Task 5: Creare Directoare Aplicație
```yaml
- name: Create application directory
  file:
    path: "{{ app_directory }}"
    state: directory
    owner: www-data
    group: www-data
    mode: '0755'
```
*Scop:* Crearea structurii de directoare cu permisiuni corecte.

#### Task 6: Configurare Virtual Host
```yaml
- name: Configure Apache virtual host
  template:
    src: templates/vhost.conf.j2
    dest: /etc/apache2/sites-available/phpapp.conf
    owner: root
    group: root
    mode: '0644'
  notify: Restart Apache
```
*Scop:* Configurarea unui virtual host Apache specific pentru aplicația PHP.

#### Task 7: Activare Site
```yaml
- name: Disable default site
  command: a2dissite 000-default.conf
  notify: Restart Apache

- name: Enable PHP application site
  command: a2ensite phpapp.conf
  notify: Restart Apache
```
*Scop:* Dezactivarea site-ului default și activarea configurației aplicației.

#### Task 8: Pornire Apache
```yaml
- name: Ensure Apache is started and enabled
  service:
    name: apache2
    state: started
    enabled: yes
```
*Scop:* Asigurarea că Apache rulează și pornește automat.

### 5. Template Virtual Host

**Fișier: `ansible/templates/vhost.conf.j2`**

```apache
<VirtualHost *:80>
    ServerName {{ server_name }}
    ServerAdmin webmaster@localhost

    DocumentRoot {{ apache_document_root }}

    <Directory {{ apache_document_root }}>
        Options Indexes FollowSymLinks
        AllowOverride All
        Require all granted
    </Directory>

    ErrorLog ${APACHE_LOG_DIR}/phpapp_error.log
    CustomLog ${APACHE_LOG_DIR}/phpapp_access.log combined

    <FilesMatch \.php$>
        SetHandler application/x-httpd-php
    </FilesMatch>
</VirtualHost>
```

### 6. Handler pentru Restart Apache

```yaml
handlers:
  - name: Restart Apache
    service:
      name: apache2
      state: restarted
```

### 7. Testare Playbook

```bash
# Verificare sintaxă
ansible-playbook setup_test_server.yml --syntax-check

# Dry run
ansible-playbook setup_test_server.yml --check

# Execuție
ansible-playbook -i hosts.ini setup_test_server.yml -v
```

---

## Pipeline-urile Jenkins

### Pipeline 1: Build și Testare PHP

**Fișier: `pipelines/php_build_and_test_pipeline.groovy`**

**Descriere:** Acest pipeline automatizează procesul de build și testare a proiectului PHP.

**Stages implementate:**

1. **Clone Repository**
   - Clonează repository-ul GitHub cu proiectul PHP
   - Branch: `main`

2. **Install Dependencies**
   - Rulează `composer install`
   - Instalează toate dependințele PHP necesare

3. **Run Unit Tests**
   - Execută PHPUnit pentru rularea testelor unitare
   - Output colorat pentru lizibilitate

4. **Generate Test Report**
   - Generează raport de coverage
   - Afișează rezultatele în consolă

**Agent folosit:** `ssh-agent` (cu label `php`)

**Post-actions:**
- Curățare workspace după finalizare
- Notificări de succes/eșec

### Pipeline 2: Configurare Test Server

**Fișier: `pipelines/ansible_setup_pipeline.groovy`**

**Descriere:** Pipeline pentru configurarea automată a test server-ului folosind Ansible.

**Stages implementate:**

1. **Verify Ansible Installation**
   - Verifică versiunea Ansible instalată
   - Validează disponibilitatea comenzilor

2. **Check Connectivity**
   - Testează conexiunea SSH la test server
   - Folosește modulul `ping` din Ansible

3. **Run Ansible Playbook**
   - Execută playbook-ul `setup_test_server.yml`
   - Configurează Apache, PHP și structura aplicației

4. **Verify Configuration**
   - Verifică status Apache
   - Verifică versiunea PHP instalată

**Agent folosit:** `ansible-agent`

**Variabile de mediu:**
```groovy
ANSIBLE_CONFIG = '/home/jenkins/ansible/ansible.cfg'
ANSIBLE_INVENTORY = '/home/jenkins/ansible/hosts.ini'
PLAYBOOK_PATH = '/home/jenkins/ansible/setup_test_server.yml'
```

### Pipeline 3: Deploy Aplicație PHP

**Fișier: `pipelines/php_deploy_pipeline.groovy`**

**Descriere:** Automatizează deployment-ul aplicației PHP pe test server.

**Stages implementate:**

1. **Clone Repository on Ansible Agent**
   - Clonează codul sursă pe agentul Ansible
   - Folosește `stash` pentru persistență între stagii

2. **Deploy to Test Server**
   - Restaurează codul din stash (`unstash`)
   - Rulează playbook-ul Ansible de deployment
   - Copiază fișierele pe server
   - Instalează dependințele Composer
   - Setează permisiuni corecte

3. **Verify Deployment**
   - Verifică structura de directoare
   - Testează accesul la aplicație prin curl

**Agent folosit:** `ansible-agent`

**Flow de date:**
```
GitHub → Ansible Agent → Test Server
```

### Crearea Job-urilor în Jenkins

Pentru fiecare pipeline:

1. **Dashboard → New Item**
2. Nume descriptiv (ex: `PHP-Build-and-Test`)
3. Tip: **Pipeline**
4. Configurare:
   - Definition: `Pipeline script from SCM`
   - SCM: `Git`
   - Repository URL: URL-ul repository-ului
   - Branch: `*/main`
   - Script Path: calea către fișierul `.groovy`

---

## Rezultate și Testare

### 1. Verificarea Serviciilor

```bash
# Status toate containerele
docker compose ps

# Output așteptat:
NAME                 IMAGE                  STATUS
jenkins-controller   jenkins/jenkins:lts    Up 2 hours
ssh-agent           lab05-ssh-agent        Up 2 hours
ansible-agent       lab05-ansible-agent    Up 2 hours
test-server         lab05-test-server      Up 2 hours
```

### 2. Testare Jenkins Agents

**SSH Agent:**
```bash
docker exec ssh-agent php --version
docker exec ssh-agent composer --version
```

**Ansible Agent:**
```bash
docker exec ansible-agent ansible --version
docker exec ansible-agent ssh ansible@test-server "hostname"
```

### 3. Testare Test Server

**Verificare Apache:**
```bash
docker exec test-server ps aux | grep apache2
```

**Testare PHP:**
```bash
curl http://localhost:8081/phpapp/public/info.php
```

**Output așteptat:** Pagină PHP Info completă

### 4. Testare Playbook Ansible

```bash
docker exec ansible-agent ansible-playbook \
  -i /home/jenkins/ansible/hosts.ini \
  /home/jenkins/ansible/setup_test_server.yml
```

**Output așteptat:**
```
PLAY RECAP ***************************************
test-server    : ok=12   changed=8    unreachable=0    failed=0
```

### 5. Testare Pipeline-uri

#### Test Build Pipeline:
1. Accesare Jenkins: `http://localhost:8080`
2. Job: `PHP-Build-and-Test`
3. Click: **Build Now**
4. Verificare: Console Output → SUCCESS

#### Test Ansible Setup Pipeline:
1. Job: `Ansible-Setup-Test-Server`
2. Click: **Build Now**
3. Verificare: Apache configurat corect

#### Test Deploy Pipeline:
1. Job: `PHP-Deploy-to-Test-Server`
2. Click: **Build Now**
3. Verificare browser: `http://localhost:8081`

### 6. Screenshots Rezultate

**Jenkins Dashboard:**
- Toate job-urile cu status SUCCESS (verde)
- Build history fără eșecuri

**Test Server - Browser:**
- Aplicație PHP funcțională
- PHP Info complet vizibil

---

## Răspunsuri la Întrebări

### 1. Care sunt avantajele folosirii Ansible pentru configurarea serverelor?

**Avantaje principale:**

**a) Simplitate și Ușurință în Utilizare**
- Sintaxă YAML ușor de înțeles și scris
- Nu necesită cunoștințe avansate de programare
- Curba de învățare redusă comparativ cu alte tool-uri

**b) Agentless Architecture**
- Nu necesită instalarea de agenți pe serverele țintă
- Comunicare prin SSH standard
- Reducerea suprafeței de atac și a complexității

**c) Idempotență**
- Rularea multiplă a aceluiași playbook produce același rezultat
- Sigur de rulat repetat fără efecte adverse
- Facilitează mentenanța și actualizările

**d) Infrastructure as Code (IaC)**
- Configurația este stocată în fișiere text
- Versionare prin Git
- Review prin pull requests
- Documentație automată a infrastructurii

**e) Modularitate și Reusabilitate**
- Playbook-urile pot fi reutilizate
- Roles pentru funcționalități comune
- Variables pentru parametrizare

**f) Scalabilitate**
- Configurare simultană a sute de servere
- Inventare dinamice pentru cloud
- Paralelizare automată a task-urilor

**g) Comunitate și Ecosystem**
- Ansible Galaxy cu mii de role pre-construite
- Documentație extensivă
- Suport comunitar activ

**h) Integrare CI/CD**
- Integrare ușoară cu Jenkins, GitLab CI, etc.
- Automatizare completă deployment
- Testare infrastructură înainte de producție

### 2. Ce alte module Ansible există pentru configuration management?

**Module Esențiale pentru Package Management:**

- **apt** - Gestionare pachete pe Debian/Ubuntu
- **yum** - Gestionare pachete pe RedHat/CentOS
- **dnf** - Manager pachete modern pentru Fedora
- **package** - Module generic multi-platformă
- **pip** - Instalare pachete Python
- **npm** - Gestionare pachete Node.js
- **gem** - Instalare gem-uri Ruby

**Module pentru Fișiere și Directoare:**

- **copy** - Copiere fișiere de pe control node
- **template** - Procesare template-uri Jinja2
- **file** - Gestionare fișiere/directoare/symlinks
- **lineinfile** - Modificare linii specifice în fișiere
- **blockinfile** - Inserare blocuri de text
- **fetch** - Descărcare fișiere de pe servere remote
- **synchronize** - Sincronizare fișiere (wrapper rsync)

**Module pentru Servicii:**

- **service** - Control servicii sistem (start/stop/restart)
- **systemd** - Gestionare avansată servicii systemd
- **cron** - Configurare cron jobs
- **at** - Programare task-uri one-time

**Module pentru Useri și Permisiuni:**

- **user** - Gestionare conturi utilizatori
- **group** - Gestionare grupuri
- **authorized_key** - Configurare SSH keys
- **acl** - Setare Access Control Lists

**Module pentru Baze de Date:**

- **mysql_db** - Gestionare baze de date MySQL
- **mysql_user** - Gestionare utilizatori MySQL
- **postgresql_db** - Gestionare PostgreSQL databases
- **mongodb_user** - Administrare utilizatori MongoDB

**Module pentru Web Servers:**

- **apache2_module** - Control module Apache
- **htpasswd** - Gestionare fișiere .htpasswd
- **nginx** - Configurare Nginx (prin community)

**Module pentru Cloud:**

- **ec2** - Gestionare instanțe AWS EC2
- **s3_bucket** - Operații AWS S3
- **azure_rm_virtualmachine** - VM-uri Azure
- **gcp_compute_instance** - Instanțe Google Cloud

**Module pentru Containere:**

- **docker_container** - Gestionare containere Docker
- **docker_image** - Build și pull imagini Docker
- **docker_network** - Configurare rețele Docker
- **k8s** - Deployment Kubernetes

**Module pentru Rețea:**

- **firewalld** - Configurare firewall
- **iptables** - Gestionare reguli iptables
- **ufw** - Uncomplicated Firewall
- **nmcli** - NetworkManager configuration

**Module pentru Monitorizare și Debugging:**

- **debug** - Afișare variabile și mesaje
- **assert** - Verificare condiții
- **wait_for** - Așteptare condiții (port, fișier)
- **stat** - Obținere informații despre fișiere

### 3. Ce probleme am întâmpinat la crearea Ansible playbook și cum le-am rezolvat?

**Problemele și soluțiile sunt detaliate în secțiunea următoare.**

---

## Probleme Întâmpinate și Soluții

### Problema 1: Conflicte între Fișierele Docker Compose

**Simptom:**
```bash
WARN[0000] Found multiple config files with supported names: 
/home/danuta/lab05/compose.yaml, /home/danuta/lab05/docker-compose.yml
validating /home/danuta/lab05/compose.yaml: 
additional properties 'ssh-agent' not allowed
```

**Cauză:**
- Existența simultană a fișierelor `compose.yaml` și `docker-compose.yml` în același director
- Docker Compose încerca să combine ambele fișiere, creând conflicte de sintaxă
- Fișierul `docker-compose.yml` rămăsese din Lab04

**Soluție aplicată:**
```bash
# Ștergerea fișierului vechi
rm docker-compose.yml

# Păstrarea doar a compose.yaml
ls -la *.y*ml
# Output: compose.yaml (doar unul)

# Rebuild fără conflicte
docker compose build
```

**Lecție învățată:**
- Folosiți un singur format de nume pentru fișierele Docker Compose
- Curățați fișierele rămase din laboratoare anterioare
- Verificați întotdeauna ce fișiere de configurare există în director

---

### Problema 2: Eroare la Instalarea Ansible prin pip

**Simptom:**
```bash
ERROR [3/10] RUN pip3 install --no-cache-dir ansible
error: externally-managed-environment
× This environment is externally managed
```

**Cauză:**
- Python 3.11+ implementează PEP 668 - protecție împotriva instalării pachetelor system-wide
- Imaginea `jenkins/ssh-agent:latest` folosește o versiune nouă de Debian/Python
- pip blochează instalarea în afara virtual environments pentru a preveni conflictele

**Soluții testate:**

**Soluția 1 (adoptată):** Instalare Ansible prin apt
```dockerfile
RUN apt-get update && \
    apt-get install -y ansible || \
    pip3 install --break-system-packages ansible
```

**Soluția 2 (alternativă):** Folosire Ubuntu ca bază
```dockerfile
FROM ubuntu:22.04
# Apoi instalare Ansible prin apt
RUN apt-get install -y ansible
```

**Avantaje soluție adoptată:**
- Mai sigură decât `--break-system-packages`
- Versiune stabilă de Ansible din repository-ul oficial
- Integrare mai bună cu sistemul de pachete

**Lecție învățată:**
- Preferați instalarea prin package manager-ul sistemului (apt, yum) în loc de pip pentru tool-uri infrastructure
- Verificați versiunile Python și restricțiile din imaginile base

---

### Problema 3: systemctl Nu Funcționează în Container

**Simptom:**
```bash
docker exec test-server systemctl status apache2
System has not been booted with systemd as init system (PID 1). Can't operate.
Failed to connect to bus: Host is down
```

**Cauză:**
- Containerele Docker nu rulează systemd ca proces init (PID 1)
- Comenzile `systemctl` necesită systemd running
- Filozofia Docker: un proces principal per container, nu un init system complet

**Soluție aplicată:**

**Implementare Supervisor pentru multi-process management:**

1. **Instalare supervisor în Dockerfile:**
```dockerfile
RUN apt-get install -y supervisor
```

2. **Creare configurație supervisord.conf:**
```ini
[supervisord]
nodaemon=true

[program:sshd]
command=/usr/sbin/sshd -D
autostart=true
autorestart=true

[program:apache2]
command=/usr/sbin/apache2ctl -D FOREGROUND
autostart=true
autorestart=true
```

3. **Modificare CMD în Dockerfile:**
```dockerfile
CMD ["/usr/bin/supervisord", "-c", "/etc/supervisor/conf.d/supervisord.conf"]
```

**Verificare alternative (fără systemctl):**
```bash
# Verificare procese Apache
docker exec test-server ps aux | grep apache2

# Verificare porturi
docker exec test-server netstat -tlnp | grep :80

# Test funcționalitate
docker exec test-server curl http://localhost/
```

**Lecție învățată:**
- Containerele Docker au limitări fundamentale diferite de VM-uri
- Pentru multiple procese, folosiți supervisor, s6, sau runit
- Adaptați verificările la contextul containerizat

---

### Problema 4: Container Name Already in Use

**Simptom:**
```bash
Error response from daemon: Conflict. The container name 
"/jenkins-controller" is already in use by container "eb4cf985..."
```

**Cauză:**
- Container cu același nume rula deja din Lab04
- Docker nu permite nume duplicate de containere
- Containere oprite dar nesterase ocupă în continuare numele

**Soluție aplicată:**
```bash
# Oprire și ștergere container existent
docker stop jenkins-controller
docker rm jenkins-controller

# Curățare completă (dacă necesar)
docker compose down -v

# Restart servicii
docker compose up -d
```

**Prevenție pentru viitor:**
```bash
# Script de curățare între laboratoare
#!/bin/bash
cd ~/lab05
docker compose down -v
docker system prune -f
docker compose up -d
```

**Lecție învățată:**
- Curățați containerele între laboratoare pentru a evita conflicte
- Folosiți `docker compose down` în loc de `docker stop` manual
- Considerați namespace-uri diferite pentru laboratoare diferite

---

### Problema 5: Permission Denied pentru Chei SSH

**Simptom:**
```bash
Warning: Permanently added 'test-server' (ED25519) to the list of known hosts.
ansible@test-server: Permission denied (publickey).
```

**Cauză:**
- Permisiuni incorecte pentru cheile SSH (prea permisive)
- SSH refuză să folosească chei cu permisiuni 644 sau 777
- Cheia privată trebuie să fie accesibilă doar owner-ului

**Soluție aplicată:**

1. **În Dockerfile.ansible_agent:**
```dockerfile
RUN chmod 600 /home/jenkins/.ssh/id_ed25519 && \
    chmod 644 /home/jenkins/.ssh/id_ed25519.pub && \
    chown jenkins:jenkins /home/jenkins/.ssh/id_ed25519*
```

2. **Verificare manuală:**
```bash
# Verificare permisiuni
docker exec ansible-agent ls -la /home/jenkins/.ssh/

# Output corect:
# -rw------- jenkins jenkins id_ed25519
# -rw-r--r-- jenkins jenkins id_ed25519.pub
```

3. **Testare conexiune:**
```bash
docker exec ansible-agent ssh -vvv ansible@test-server "hostname"
```

**Lecție învățată:**
- Cheile private SSH trebuie 600 (rw-------)
- Cheile publice SSH pot fi 644 (rw-r--r--)
- Directorul .ssh trebuie 700 (rwx------)
- Verificați întotdeauna ownership-ul (jenkins:jenkins)

---

### Problema 6: Ansible Playbook Eșuează la Task-uri apt

**Simptom:**
```bash
TASK [Install Apache2] ****
fatal: [test-server]: FAILED! => 
{"msg": "Could not find aptitude. Please ensure it is installed."}
```

**Cauză:**
- Unele versiuni de Ansible necesită `aptitude` pentru modulul `apt`
- Ubuntu minimal nu include `aptitude` by default
- Ansible fallback la `apt-get` dar cu warnings

**Soluție aplicată:**

**Opțiunea 1:** Instalare aptitude în test-server
```dockerfile
RUN apt-get install -y aptitude
```

**Opțiunea 2:** Forțare apt-get în playbook
```yaml
- name: Install Apache2
  apt:
    name: apache2
    state: present
    force_apt_get: yes
```

**Opțiunea 3 (adoptată):** Update cache explicit
```yaml
- name: Update apt cache
  apt:
    update_cache: yes
    cache_valid_time: 3600

- name: Install Apache2
  apt:
    name: apache2
    state: present
```

**Lecție învățată:**
- Întotdeauna actualizați cache-ul apt înainte de instalări
- Folosiți `force_apt_get: yes` pentru consistență
- Testați playbook-urile cu `--check` înainte de rulare

---

### Problema 7: Apache Virtual Host Nu Se Aplică

**Simptom:**
- Apache rulează dar servește site-ul default
- Aplicația PHP nu este accesibilă la URL așteptat
- Virtual host configurat dar ignorat

**Cauză:**
- Site-ul default (000-default.conf) are prioritate
- Virtual host creat dar nu activat
- Apache trebuie restartat după modificări

**Soluție aplicată în Playbook:**

```yaml
- name: Disable default site
  command: a2dissite 000-default.conf
  args:
    removes: /etc/apache2/sites-enabled/000-default.conf
  notify: Restart Apache

- name: Enable PHP application site
  command: a2ensite phpapp.conf
  args:
    creates: /etc/apache2/sites-enabled/phpapp.conf
  notify: Restart Apache

handlers:
  - name: Restart Apache
    service:
      name: apache2
      state: restarted
```

**Verificare manuală:**
```bash
# Verificare site-uri active
docker exec test-server ls -la /etc/apache2/sites-enabled/

# Verificare configurație Apache
docker exec test-server apache2ctl -t

# Verificare virtual hosts încărcați
docker exec test-server apache2ctl -S
```

**Lecție învățată:**
- Folosiți handlers pentru restart-uri după modificări
- Verificați întotdeauna configurația cu `apache2ctl -t`
- Dezactivați explicit site-uri conflictuale

---

### Problema 8: Deployment Eșuează - Directory Not Empty

**Simptom:**
```bash
TASK [Copy application to final destination] ****
fatal: [test-server]: FAILED! => 
{"msg": "Destination /var/www/html/phpapp already exists"}
```

**Cauză:**
- Playbook-ul de deployment rulează de mai multe ori
- Directorul destinație există deja cu conținut
- Ansible nu suprascrie by default

**Soluție aplicată:**

```yaml
- name: Copy application to final destination
  command: rsync -av --delete {{ temp_directory }}/ {{ app_directory }}/
  args:
    warn: false

- name: Set proper permissions
  file:
    path: "{{ app_directory }}"
    owner: www-data
    group: www-data
    recurse: yes
```

**Folosire `synchronize` module (alternativă):**
```yaml
- name: Sync application files
  synchronize:
    src: "{{ temp_directory }}/"
    dest: "{{ app_directory }}/"
    delete: yes
    recursive: yes
  delegate_to: "{{ inventory_hostname }}"
```

**Lecție învățată:**
- Folosiți `rsync` cu `--delete` pentru deployment-uri idempotente
- Modulul `synchronize` este preferat pentru operații complexe
- Setați permissions după copiere, nu înainte

---

### Problema 9: Jenkins Nu Vede Ansible Agent Node

**Simptom:**
- Agent adăugat în Jenkins dar apare offline
- Eroare: "There are no agents for this label. 'ansible'"
- Pipeline-urile nu pot folosi agentul

**Cauză:**
- Configurare incompletă a node-ului în Jenkins
- Credentials greșite sau lipsă
- Host key verification failure

**Soluție aplicată:**

1. **Verificare credentials în Jenkins:**
```
Manage Jenkins → Credentials → Global
- ID: jenkins-ansible-agent-key
- Username: jenkins
- Private Key: ✓ Corespunde cu keys/jenkins_ansible_agent
```

2. **Configurare corectă Node:**
```
Name: ansible-agent
Remote root directory: /home/jenkins/agent
Labels: ansible
Host: ansible-agent (numele din Docker network!)
Credentials: jenkins-ansible-agent-key
Host Key Verification: Non verifying
```

3. **Verificare conexiune din Jenkins:**
```bash
# Din Jenkins Script Console (Manage Jenkins → Script Console)
def command = "ssh jenkins@ansible-agent hostname"
println command.execute().text
```

4. **Verificare logs agent:**
```bash
# În Jenkins UI, la node status
# Sau în containerul Jenkins:
docker logs jenkins-controller | grep ansible-agent
```

**Lecție învățată:**
- Folosiți numele containerelor din Docker network, nu IP-uri
- "Non verifying" este OK pentru development/lab
- Testați conexiunea SSH manual înainte de configurare Jenkins

---

### Problema 10: Pipeline Eșuează - Workspace Path Undefined

**Simptom:**
```bash
TASK [Copy application files from Jenkins workspace] ****
fatal: [test-server]: FAILED! => 
{"msg": "Source /undefined does not exist"}
```

**Cauză:**
- Variabila `$WORKSPACE` nu este disponibilă în contextul Ansible
- Playbook-ul încearcă să acceseze o cale inexistentă
- Sincronizare între Jenkins workspace și Ansible necesită abordare diferită

**Soluție aplicată:**

**În Pipeline:**
```groovy
stage('Deploy to Test Server') {
    steps {
        unstash 'php-app-source'
        sh '''
            cd /home/jenkins/ansible
            ansible-playbook -i hosts.ini deploy_php_app.yml \
                -e "workspace_path=${WORKSPACE}" -v
        '''
    }
}
```

**În Playbook:**
```yaml
vars:
  workspace_path: "{{ lookup('env', 'WORKSPACE') | default('/tmp') }}"
  
tasks:
  - name: Debug workspace path
    debug:
      msg: "Using workspace: {{ workspace_path }}"
```

**Alternativă cu stash/unstash:**
```groovy
// Stage 1
stash includes: '**/*', name: 'php-app-source'

// Stage 2
unstash 'php-app-source'
// Acum fișierele sunt în WORKSPACE
```

**Lecție învățată:**
- Variabilele Jenkins nu sunt automat disponibile în Ansible
- Folosiți `-e` pentru a pasa variabile explicit
- `stash/unstash` pentru transfer fișiere între stagii/agenți

---

## Concluzii

### Realizări Principale

Prin completarea acestui laborator, am reușit să:

1. **Implementez un sistem CI/CD complet** folosind Jenkins, Ansible și Docker
2. **Automatizez configurarea infrastructurii** prin Infrastructure as Code
3. **Separ responsabilitățile** în containere specializate pentru diferite taskuri
4. **Integrez multiple tehnologii** într-un workflow coerent și funcțional
5. **Rezolv probleme complexe** de networking, permisiuni și orchestrare

### Competențe Dobândite

**Tehnice:**
- Configurare avansată Docker Compose multi-container
- Scrierea și debugging playbook-uri Ansible
- Creare pipeline-uri Jenkins declarative
- Gestionare chei SSH și autentificare securizată
- Configurare web server Apache și PHP

**Conceptuale:**
- Principiile Infrastructure as Code
- Arhitectura sistemelor CI/CD
- Separarea concernurilor în microservicii
- Idempotență în configuration management
- Best practices în automatizare
  
---

### Structura Completă a Proiectului

```
lab05/
├── .env
├── compose.yaml
├── docker-entrypoint.sh
├── supervisord.conf
├── Dockerfile.ssh_agent
├── Dockerfile.ansible_agent
├── Dockerfile.test_server
├── keys/
│   ├── jenkins_ssh_agent
│   ├── jenkins_ssh_agent.pub
│   ├── jenkins_ansible_agent
│   ├── jenkins_ansible_agent.pub
│   ├── ansible_to_testserver
│   └── ansible_to_testserver.pub
├── ansible/
│   ├── ansible.cfg
│   ├── hosts.ini
│   ├── setup_test_server.yml
│   ├── deploy_php_app.yml
│   └── templates/
│       └── vhost.conf.j2
├── pipelines/
│   ├── php_build_and_test_pipeline.groovy
│   ├── ansible_setup_pipeline.groovy
│   └── php_deploy_pipeline.groovy
└── README.md
```


---

*Acest raport demonstrează implementarea completă a unui sistem CI/CD automatizat folosind cele mai bune practici din industrie.*
