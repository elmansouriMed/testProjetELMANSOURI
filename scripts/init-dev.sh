#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
if [ -f .env ]; then echo '.env existe déjà ; aucun remplacement effectué.' >&2; exit 1; fi
umask 077
mkdir -p .secrets
db_password=$(openssl rand -hex 24)
mq_password=$(openssl rand -hex 24)
license=''
if [ "${1:-}" = '--accept-mq-license' ]; then license=accept; fi
printf '%s' "$mq_password" > .secrets/mq-app-password.txt
printf 'DB_PASSWORD=%s\nMQ_PASSWORD=%s\nMQ_LICENSE=%s\nMQ_CONSUMERS=4\nDB_POOL_SIZE=16\n' "$db_password" "$mq_password" "$license" > .env
echo 'Configuration locale créée ; identifiants exclus de Git.'
if [ -z "$license" ]; then echo 'Après lecture de la licence IBM, renseignez MQ_LICENSE=accept dans .env.'; fi
