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

# LE o sgpur.env, NUNCA faz "source" dele. O arquivo e um EnvironmentFile de
# systemd, nao um script shell: o systemd trata cada linha como KEY=valor
# literal, entao valores com espaco (a senha de app do Gmail vem em 4 grupos
# separados por espaco) sao perfeitamente validos ali - mas quebram no shell.
# Com ". ${ENV_FILE}", o bash lia SGPUR_MAIL_PASS=abcd e tentava EXECUTAR os
# grupos seguintes como comandos ("qlnr: command not found" em producao,
# 2026-08-05). Alem disso, sourcing executaria qualquer coisa que estivesse no
# arquivo - risco desnecessario num script que roda como o dono da credencial.
ler_env() {
    local valor
    valor=$(sed -n "s/^$1=//p" "${ENV_FILE}" | tail -1)
    valor="${valor%$'\r'}"          # tolera arquivo salvo com CRLF
    valor="${valor%\"}"; valor="${valor#\"}"   # tira aspas opcionais
    valor="${valor%\'}"; valor="${valor#\'}"
    printf '%s' "${valor}"
}

SGPUR_MAIL_HOST=$(ler_env SGPUR_MAIL_HOST)
SGPUR_MAIL_PORT=$(ler_env SGPUR_MAIL_PORT)
SGPUR_MAIL_USER=$(ler_env SGPUR_MAIL_USER)
SGPUR_MAIL_PASS=$(ler_env SGPUR_MAIL_PASS)
SGPUR_MAIL_FROM=$(ler_env SGPUR_MAIL_FROM)
SGPUR_BACKUP_ALERTA_EMAIL=$(ler_env SGPUR_BACKUP_ALERTA_EMAIL)
export SGPUR_MAIL_HOST SGPUR_MAIL_PORT SGPUR_MAIL_USER SGPUR_MAIL_PASS SGPUR_MAIL_FROM

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
