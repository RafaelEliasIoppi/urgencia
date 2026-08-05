#!/bin/bash
# Envia por e-mail um alerta de falha do backup. Chamado por backup-db.sh.
#
# POR QUE ESTE SCRIPT EXISTE SEPARADO, E RODA COMO OUTRO USUARIO:
# o backup roda como 'postgres', que NAO tem (e nao deve ter) acesso a
# /opt/sgpur/sgpur.env - o arquivo com a senha SMTP institucional, modo 600 e
# dono 'sgpur'. A alternativa obvia seria copiar essa senha para um arquivo que
# o postgres leia, mas isso espalharia o segredo por mais um lugar, com mais um
# dono e mais um conjunto de permissoes para manter certo. Em vez disso, este
# script e executado COMO 'sgpur' (via a regra pontual de
# deploy/cron/sudoers-sgpur-backup-alerta), entao a credencial continua
# existindo em UM unico arquivo, com o mesmo dono e as mesmas permissoes de
# sempre - o postgres ganha o direito de disparar o alerta, nunca de ler a
# senha.
#
# Uso:  notificar-falha-backup.sh "assunto" < corpo-no-stdin
#
# Destinatario: SGPUR_BACKUP_ALERTA_EMAIL, se definido no sgpur.env; senao
# SGPUR_MAIL_FROM (a propria conta institucional).
set -euo pipefail

ENV_FILE="/opt/sgpur/sgpur.env"
ASSUNTO="${1:-Falha no backup do SAUR}"
CORPO=$(cat)

if [ ! -r "${ENV_FILE}" ]; then
    echo "notificar-falha-backup: ${ENV_FILE} nao legivel (rodando como $(id -un))." >&2
    exit 1
fi

# shellcheck disable=SC1090
set -a; . "${ENV_FILE}"; set +a

DESTINO="${SGPUR_BACKUP_ALERTA_EMAIL:-${SGPUR_MAIL_FROM:-}}"
if [ -z "${DESTINO}" ] || [ -z "${SGPUR_MAIL_USER:-}" ] || [ -z "${SGPUR_MAIL_PASS:-}" ]; then
    echo "notificar-falha-backup: configuracao de SMTP incompleta - alerta nao enviado." >&2
    exit 1
fi

ASSUNTO="${ASSUNTO}" CORPO="${CORPO}" DESTINO="${DESTINO}" python3 - <<'PY'
import os, smtplib, ssl
from email.message import EmailMessage

msg = EmailMessage()
msg["Subject"] = os.environ["ASSUNTO"]
msg["From"] = os.environ.get("SGPUR_MAIL_FROM") or os.environ["SGPUR_MAIL_USER"]
msg["To"] = os.environ["DESTINO"]
msg.set_content(os.environ["CORPO"])

host = os.environ.get("SGPUR_MAIL_HOST", "smtp.gmail.com")
porta = int(os.environ.get("SGPUR_MAIL_PORT", "587"))
with smtplib.SMTP(host, porta, timeout=30) as s:
    s.starttls(context=ssl.create_default_context())
    s.login(os.environ["SGPUR_MAIL_USER"], os.environ["SGPUR_MAIL_PASS"])
    s.send_message(msg)
print("alerta enviado para", os.environ["DESTINO"])
PY
