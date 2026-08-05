#!/bin/bash
# Backup diario do banco sgpur (PostgreSQL local na VM) + anexos.
# Roda como usuario 'postgres' via /etc/cron.d/sgpur-backup (autenticacao peer,
# sem senha em texto claro), com a saida em /var/log/sgpur-backup.log.
#
# TRAVA DE EXECUCAO UNICA (flock, 2026-08-05): existiam DUAS entradas de cron
# para este script (crontab do postgres + /etc/cron.d/sgpur-backup), as duas as
# 03:00. Os dois processos calculavam quase o mesmo TIMESTAMP, disputavam o
# mesmo arquivo .tmp, um vencia e o outro abortava com "mv: cannot stat" - erro
# diario no log que mascarava qualquer falha de verdade, alem de dois pg_dump
# simultaneos numa VM de 1 GB de RAM. A entrada duplicada foi removida; o flock
# garante que nem um cron novo, nem uma execucao manual sobreposta, repitam o
# problema.
set -euo pipefail

# Re-executa a si mesmo sob flock. -n = nao espera: se ja ha um backup rodando,
# sai avisando em vez de enfileirar mais um pg_dump.
LOCK="/tmp/sgpur-backup.lock"
if [ "${SGPUR_BACKUP_LOCKED:-}" != "1" ]; then
    export SGPUR_BACKUP_LOCKED=1
    # -E 99: codigo de saida proprio para "nao consegui o lock", que separa
    # esse caso (benigno) de uma falha real do backup, que tambem sairia 1.
    # Sem "exec": com exec o processo e substituido pelo flock e o tratamento
    # abaixo nunca rodaria - a execucao ignorada saia com 1 e SEM mensagem
    # nenhuma, que e justamente o silencio que este script tenta eliminar.
    # "|| rc=$?" e obrigatorio: com "set -e" ativo, um comando que retorna != 0
    # fora de um contexto condicional derruba o script na hora - o rc=$? da
    # linha seguinte nunca chegava a ser avaliado, e a execucao ignorada saia
    # com 99 e sem a mensagem, repetindo o silencio que se queria eliminar.
    rc=0
    flock -n -E 99 "${LOCK}" "$0" "$@" || rc=$?
    if [ "${rc}" -eq 99 ]; then
        echo "[$(date +%F\ %T)] AVISO: outro backup ja esta em execucao - esta execucao foi ignorada."
        exit 0
    fi
    exit "${rc}"
fi

DB_NAME="sgpur"
BACKUP_DIR="/opt/sgpur/backups"
RETENCAO_DIAS=14
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
ARQUIVO="${BACKUP_DIR}/sgpur-${TIMESTAMP}.sql.gz"
NOME_ARQUIVO="sgpur-${TIMESTAMP}.sql.gz"
REMOTO="gdrive:sgpur-backups"
# Marcador do ultimo backup OFFSITE confirmado. E o que permite descobrir que a
# copia remota parou, mesmo que o backup local continue funcionando.
MARCADOR="${BACKUP_DIR}/.ultimo-offsite-ok"

log() { echo "[$(date +%F\ %T)] $*"; }

mkdir -p "${BACKUP_DIR}"
cd "${BACKUP_DIR}"   # cwd previsivel: evita erro de permissao quando chamado de outro diretorio

pg_dump -U postgres -d "${DB_NAME}" | gzip > "${ARQUIVO}.tmp"
mv "${ARQUIVO}.tmp" "${ARQUIVO}"
chmod 600 "${ARQUIVO}"

# Dump vazio/truncado e falha, nao sucesso: sem esta checagem um pg_dump que
# morresse no meio deixaria um .gz minusculo e o script diria "concluido".
TAMANHO=$(stat -c%s "${ARQUIVO}")
if [ "${TAMANHO}" -lt 1000 ]; then
    log "ERRO: dump gerado tem apenas ${TAMANHO} bytes - suspeito de falha do pg_dump. Arquivo mantido para inspecao."
    exit 1
fi

# Remove backups mais antigos que RETENCAO_DIAS
find "${BACKUP_DIR}" -name 'sgpur-*.sql.gz' -type f -mtime +${RETENCAO_DIAS} -delete

# ---------- Copia offsite (Google Drive) ----------
# O rclone pode falhar sem derrubar o backup LOCAL, e e exatamente por isso que
# a falha precisa ser gritada: perder a VM sem copia remota e o cenario
# catastrofico. O client_id compartilhado do rclone sera desativado durante
# 2026 - quando isso acontecer, e AQUI que vai aparecer.
OFFSITE_OK=0
if rclone copy "${ARQUIVO}" "${REMOTO}/"; then
    # Confirma que o arquivo REALMENTE chegou: "rclone copy" pode retornar 0 e
    # ainda assim nada estar la (credencial expirada em alguns modos, quota).
    if rclone lsf "${REMOTO}/" | grep -qx "${NOME_ARQUIVO}"; then
        OFFSITE_OK=1
        date +%F > "${MARCADOR}"
        log "Copia offsite confirmada no Drive: ${NOME_ARQUIVO}"
    else
        log "ERRO: rclone terminou sem erro mas ${NOME_ARQUIVO} NAO esta em ${REMOTO}. Backup offsite FALHOU."
    fi
else
    log "ERRO: falha ao enviar ${NOME_ARQUIVO} para ${REMOTO}. Backup offsite FALHOU."
fi

# Backup dos anexos (so faz sentido se a conexao com o Drive esta boa)
ANEXOS_DIR="/opt/sgpur/data/anexos"
if [ "${OFFSITE_OK}" = "1" ] && [ -d "${ANEXOS_DIR}" ]; then
    if rclone sync "${ANEXOS_DIR}" "${REMOTO}/anexos/" \
            --backup-dir "${REMOTO}/anexos-archive/${TIMESTAMP}/"; then
        log "Anexos sincronizados: ${ANEXOS_DIR}"
    else
        log "ERRO: falha ao sincronizar os anexos para o Drive."
        OFFSITE_OK=0
    fi
fi

# ---------- Alerta de copia offsite parada ----------
# Se a ultima copia confirmada tem mais de 3 dias, algo esta quebrado ha tempo
# suficiente para merecer destaque no log (e nao passar batido em meio ao ruido).
if [ -f "${MARCADOR}" ]; then
    DIAS=$(( ( $(date +%s) - $(date -d "$(cat "${MARCADOR}")" +%s) ) / 86400 ))
    if [ "${DIAS}" -gt 3 ]; then
        log "ALERTA: ultimo backup offsite confirmado foi ha ${DIAS} dias ($(cat "${MARCADOR}")). Verifique o rclone."
    fi
fi

log "Backup local concluido: ${ARQUIVO} ($((TAMANHO / 1024)) KB) | offsite=${OFFSITE_OK}"
[ "${OFFSITE_OK}" = "1" ]
