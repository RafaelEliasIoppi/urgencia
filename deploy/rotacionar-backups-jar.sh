#!/bin/bash
# Mantem apenas os 3 backups mais recentes de /opt/sgpur/sgpur.jar.bak-*
# (cada deploy manual/CI gera um novo backup do jar antes de substituir o ativo;
# sem rotacao eles acumulam ~70MB cada indefinidamente).
set -euo pipefail

KEEP=3
cd /opt/sgpur

ls -t sgpur.jar.bak* 2>/dev/null | tail -n +$((KEEP+1)) | xargs -r rm -v --
