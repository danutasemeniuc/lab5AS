#!/bin/bash
set -e

# Pornire Apache în background
echo "Starting Apache..."
apache2ctl start

# Verificare că Apache rulează
sleep 2
if pgrep apache2 > /dev/null; then
    echo "Apache started successfully"
else
    echo "Apache failed to start"
fi

# Pornire SSH în foreground (păstrează containerul activ)
echo "Starting SSH..."
/usr/sbin/sshd -D