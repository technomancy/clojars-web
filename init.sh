#!/bin/bash

apt-get update
apt-get install -y \
    git-core openjdk-6-jdk tmux sqlite3 subversion nginx balance cronolog \
    emacs23 tree unzip rlwrap tmux curl

# Leiningen
if [ ! -r /usr/local/bin/lein ]; then
    wget -O /usr/local/bin/lein https://github.com/technomancy/leiningen/raw/stable/bin/lein
    chmod +x /usr/local/bin/lein
fi

# Clojars user
grep clojars /etc/passwd
if [ $? -eq 1 ]; then
    adduser --disabled-password --gecos "" clojars
    echo "Match User clojars
PasswordAuthentication no" >> /etc/ssh/sshd_config
    cd /home/clojars
    mkdir -p data repo .ssh /var/log/clojars
    chown -R clojars /var/log/clojars
fi

# Clojars Web app
if [ ! -r /home/clojars/prod ]; then
    git clone git://github.com/ato/clojars-web /home/clojars/prod
    cd /home/clojars/prod

    touch data/authorized_keys
    ln -s /home/clojars/data/authorized_keys /home/clojars/.ssh/authorized_keys
    ln -s /home/clojars/data data
    sqlite3 /home/clojars/data/db < clojars.sql
    chown -R clojars /home/clojars

    sudo -u clojars lein uberjar
fi

# Nailgun
if [ ! -r /home/clojars/nailgun ]; then
    svn co https://nailgun.svn.sourceforge.net/svnroot/nailgun/trunk/nailgun \
        /home/clojars/nailgun
    cd /home/clojars/nailgun
    sudo -u clojars make
    cp ng /usr/local/bin
fi

# TODO: Nexus indexer, crontab

# Init scripts
cp /home/clojars/prod/config/clojars.conf /etc/init
cp /home/clojars/prod/config/clojars-scp-balance.conf /etc/init
cp /home/clojars/prod/config/nginx-clojars /etc/nginx/sites-available/clojars

if [ -r /etc/nginx/sites-enabled/default ]; then
    ln -s /etc/nginx/sites-available/clojars /etc/nginx/sites-enabled/clojars
    rm /etc/nginx/sites-enabled/default
fi

/etc/init.d/nginx restart

stop clojars 2> /dev/null
stop clojars-scp-balance clojars 2> /dev/null

start clojars
start clojars-scp-balance
