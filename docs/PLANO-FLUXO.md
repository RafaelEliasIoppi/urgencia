# Plano do Fluxo — SAUR (Urgência Renal)

Mapeia o fluxo real do usuário (10 etapas da planilha Excel) para o código, o
ciclo de status e os endpoints reais de cada uma das abas do processo.

> Atualizado em 2026-08-05. Ver `CLAUDE.md` (raiz do repo) para o texto
> completo e mais recente das regras de negócio — este documento é um mapa
> de apoio, focado na correspondência etapa → controller/service/template.

## Ciclo de status

```
            cadastro                 registrar envio
    (Portal do Solicitante,      (aos 3 medicos, com
     triado e convertido)         PDF consolidado)
        │                              │
        ▼                              ▼
   SOLICITADO  ───────────────────►  ENVIADO ─────────────┐
                                       │                   │
                  medico pede info     │   maioria simples │  senao
                       ▼               │   (2/3, ou 1 se   ▼
              SOLICITA_INFORMACAO ─────┤   coordenador)
              (volta a ENVIADO quando  │        ▼          ▼
               a info e resolvida)     │    DEFERIDO   INDEFERIDO
                                       │   (final)     (final, exige
   CANCELADO (final) ◄─────────────────┘                ofício+motivo)
     a qualquer momento ate a decisao final
     (manual pelo operador OU pelo solicitante)
```

- **Em andamento (não finalizado):** `SOLICITADO`, `ENVIADO`,
  `SOLICITA_INFORMACAO`.
- **Finais:** `DEFERIDO`, `INDEFERIDO`, `CANCELADO`.
- Enum: `domain/StatusProcesso.java` — **só tem esses 6 valores.**
  `isFinalizado()`/`isEmAndamento()` e os helpers de cor/ícone de badge
  (`getBadgeIcone`, `getBootstrapBadge`).

### `EM_ANALISE` foi removido (não é mais legado, não existe mais)

Até 2026-07-29 (commit `041dc43`) existia `StatusProcesso.EM_ANALISE`, mantido
como "sinônimo legado de `ENVIADO`" para compatibilidade com processos antigos
gravados com esse valor. Confirmado pelo usuário que **não havia nenhuma linha
em produção** usando `EM_ANALISE`, e o valor foi **removido do enum por
completo** nesse commit — não é mais um caso de "aceito por compatibilidade",
é um valor que **não compila mais** se referenciado. Qualquer texto/código que
ainda cite `EM_ANALISE` está desatualizado.

### Migração de dados

Não há Flyway/Liquibase (dev = H2, prod = **Postgres rodando na própria VM
Oracle**, `localhost:5432`, banco `sgpur` — migrado do Neon em 2026-07-25
depois que o Neon estourou a cota gratuita; ver `CLAUDE.md` seção Deploy).
`ddl-auto: update` é aditivo: novas colunas/valores de enum não fazem
backfill nem atualizam `CHECK` constraints automaticamente — ver a seção
"Convenções de código" do `CLAUDE.md` para o procedimento manual exigido a
cada mudança desse tipo.

## As 5 etapas do checklist (fluxo dividido em 4 abas do wizard)

> **Atualizado em 2026-08-05: o Recebimento deixou de ser uma etapa/aba
> própria**, fundido em Envio — ver nota logo abaixo da tabela. O checklist
> (timeline vertical) tem 5 conceitos; o wizard horizontal (abas) agrupa em
> só 4, porque Ofício/Comprovante + Resposta ao solicitante já viviam juntos
> numa única aba "Finalização" desde antes.

Cada etapa é montada por `FluxoProcessoService.montarEtapas`/
`montarPassosWizard` (fonte única, usada tanto pela timeline vertical quanto
pelo wizard horizontal da tela de detalhe) e só fica **CONCLUÍDA** se a
própria condição **e** todas as anteriores também estiverem concluídas.

| # | Etapa | Endpoint(s) reais | Service | Template |
|---|---|---|---|---|
| 1 | Envio | `POST /processos/{id}/documento-clinico` (`ProcessoDecisaoController.anexarDocumentoClinico`) · `POST /processos/{id}/documento-clinico/{anexoId}/confirmar-anonimizacao` (`ProcessoDetalheController.confirmarAnonimizacao`, trava de anonimização) · `POST /processos/{id}/registrar-envio` (`ProcessoDecisaoController.registrarEnvio`) | `RegistroEnvioService.registrar` + `enviarConvitesAvaliadores` / `SolicitacaoAvaliadorService.consolidar` + `carimbarCabecalho` / `ProcessoValidator.validarRegistroEnvio` | `detalhe.html#pane-envio` |
| 2 | Respostas | `POST /avaliador/{processoId}/votar` (`AvaliadorController.registrarVoto`, único caminho de voto) · `POST /processos/{id}/lembrete-avaliador` / `.../lembrete-pendentes` (lembrete manual, não registra parecer) · `POST /processos/{id}/retomar-analise` (`ProcessoDecisaoController.retomarAnalise`, sai da pausa "Solicita informação") | `ProcessoService.atualizarStatusPorPareceres` + `tentarDecisaoAutomatica` + `retomarAposInformacao` | `detalhe.html#pane-respostas` (acompanha) · `avaliador/votar.html` (vota) |
| 3 | Decisão | `POST /processos/{id}/decidir` (`ProcessoDecisaoController.decidir`) | `ProcessoService.decidir` + `ProcessoValidator` (contagem de votos/pausa/motivo) + `DecisaoFinalService.gerarDocumentos` | `detalhe.html#pane-decisao` |
| 4 | Ofício/Comprovante | `POST /processos/{id}/oficio-upload` · `POST /processos/{id}/comprovante-snt` — em `ProcessoAnexoController` | `AnexoStorageService` + `OficioService` | `detalhe.html#pane-finalizacao` |
| 5 | Resposta ao solicitante | `POST /processos/{id}/finalizar` (`ProcessoDecisaoController.finalizar`) — ação única, dispara o e-mail automaticamente | `ProcessoService.finalizarResposta` (envia e-mail com o anexo obrigatório + marca `emailEnviadoSolicitante=true`) | `detalhe.html#pane-finalizacao` |

Notas importantes sobre a tabela acima (histórico ↔ estado atual):

- **Recebimento fundido em Envio (2026-08-05).** Não existia mais nenhuma
  ação real nessa aba desde 2026-07-27 (sempre `true` incondicionalmente,
  sem upload nem endpoint próprio) — só uma etiqueta sempre-verde antes do
  Envio. Removida como etapa/aba própria; o link "Ver solicitação original"
  que vivia lá migrou para dentro da aba Envio (agora o passo 1). Ver
  `FluxoProcessoService`/CLAUDE.md.

- **Recebimento é sempre automático desde 2026-07-27** e não existe mais
  cadastro manual "do zero": `GET/POST /processos` (`novo`/`salvar` em
  `ProcessoDetalheController`) **exigem** `origemSolicitacaoOnlineId` — todo
  `Processo` nasce de uma `SolicitacaoOnline` convertida pelo Portal do
  Solicitante. O antigo endpoint `POST /{id}/recebimento`
  (`ProcessoDetalheController.registrarRecebimento`, upload da solicitação
  original + geração automática da capa) **foi removido** — não existe
  processo real que ainda precise dele. Os valores de enum que essa etapa
  usava (`TipoAnexo.SOLICITACAO_RECEBIDA`, `TipoAnexo.CAPA_PROCESSO`) também
  **foram removidos do enum por completo** (commit `041dc43`,
  2026-07-29) — hoje o `TipoAnexo` nem tem mais esses valores. Sem nenhuma
  ação real sobrando, o Recebimento deixou de ser uma etapa/aba própria em
  2026-08-05 (ver nota acima da tabela) — sempre foi só uma etiqueta
  sempre-verde antes do Envio.
- **Trava de anonimização (Passo 1/Envio, não documentada em versões
  anteriores deste arquivo):** documentos que chegam pelo Portal do Solicitante entram
  como `TipoAnexo.DOCUMENTO_PORTAL_NAO_ANONIMIZADO` (staging) e **nunca**
  entram no PDF consolidado nem satisfazem `ProcessoValidator.
  validarRegistroEnvio` enquanto o operador não confirmar explicitamente,
  via `POST /{id}/documento-clinico/{anexoId}/confirmar-anonimizacao`, que
  "Este documento foi anonimizado" (nome do paciente removido do corpo) —
  aí sim o anexo é promovido para `TipoAnexo.DOCUMENTO_CLINICO_AVALIADOR` e
  passa a contar para o envio. É o único caminho de promoção; o operador
  também pode simplesmente subir um arquivo já anonimizado direto por
  `POST /{id}/documento-clinico`, que entra direto como
  `DOCUMENTO_CLINICO_AVALIADOR`. Ação auditada (`ANONIMIZACAO_CONFIRMADA`,
  com quem confirmou e qual anexo).
- **O comprovante de envio aos avaliadores deixou de ser exigido em
  2026-07-27** (os avaliadores hoje votam autenticados no Portal, que nunca
  dependeu desse anexo). O valor de enum correspondente,
  `TipoAnexo.EMAIL_ENVIADO_AVALIADORES`, **foi removido por completo do enum**
  (commit `041dc43`) — não sobrou nem para leitura.
- **Parecer só entra pelo Portal do Avaliador (desde 2026-07-27).** Os
  endpoints que existiam para o operador lançar/editar parecer manualmente
  (`POST /processos/{id}/resposta-avaliador` e
  `POST /processos/{id}/pareceres`, em `ProcessoDecisaoController`) **foram
  removidos**, junto com `OrigemParecer.OPERADOR_EMAIL` e
  `TipoAnexo.RESPOSTA_AVALIADOR` (removidos do enum por completo no commit
  `041dc43`). Hoje `OrigemParecer` só tem o valor `AVALIADOR_SISTEMA`.
- **`decidir` hoje exige apenas os votos da maioria simples**, sem checagem
  de anexo nenhuma (`pareceresRecebidosSemAnexo` não existe mais em
  `ProcessoValidator`/`ProcessoService` — fazia sentido só quando conviviam
  voto por operador/e-mail e voto autenticado; hoje só existe o voto
  autenticado, que já é a prova de não-repúdio).
- **Passo 5 (Resposta ao solicitante) é uma ação única** desde que `ProcessoService.finalizarResposta`
  foi criado como fonte única da regra: um clique em "Finalizar" dispara o
  e-mail (com o anexo obrigatório, comprovante SNT ou ofício, já embutido) e
  marca `Processo.emailEnviadoSolicitante = true`. O upload manual de
  `TipoAnexo.COMPROVANTE_ENVIO_SOLICITANTE` (print do e-mail) continua
  disponível (`POST /{id}/comprovante-envio-solicitante`) mas **não é mais
  exigido** para a etapa fechar — só o envio automático conta.

## Regra de decisão (inalterada desde a criação do sistema)

- Exatamente 3 médicos (`AVALIADORES_POR_PROCESSO = 3`).
- 2 favoráveis = DEFERIDO (`FAVORAVEIS_PARA_DEFERIR = 2`); 2 desfavoráveis =
  INDEFERIDO (`DESFAVORAVEIS_PARA_INDEFERIR = 2`).
- **Exceção do coordenador CET-RS:** se o `MembroUrgenciaRenal.coordenador`
  votar Favorável, Deferido imediato com esse único voto
  (`ProcessoValidator.temVotoCoordenadorFavoravel`/
  `favoraveisNecessariosParaDeferir`). Indeferir continua exigindo sempre
  ≥2 desfavoráveis — o coordenador não pesa mais para indeferir, e inclusive
  fica **vedado** indeferir se ele já votou favorável (mesmo com 2
  desfavoráveis registrados) — ver `ProcessoValidator.validarContagemVotos`.
- Decisão **manual** (operador clica em Deferir/Indeferir/Cancelar) com
  **sugestão automática** (`ProcessoValidator.sugerirDecisao`) — a decisão
  automática de fato (sem clique do operador) também roda em dois gatilhos:
  no evento (voto do avaliador, `ProcessoService.tentarDecisaoAutomatica`) e
  numa varredura periódica de segurança (`DecisaoAutomaticaScheduler`,
  configurável via `app.decisao-automatica.varredura.*`).
- INDEFERIDO exige motivo + ofício (gerado em `decidir` via
  `DecisaoFinalService`).
- **DEFERIDO exige** o **comprovante de inserção da urgência renal no SNT**
  (`TipoAnexo.COMPROVANTE_SNT`) antes de concluir a comunicação ao
  solicitante (`ProcessoValidator.validarRespostaSolicitante`, checado tanto
  no upload da etapa 5 quanto na finalização automática da etapa 6). O
  comprovante é gerado fora do sistema (operador insere a urgência no SNT e
  salva o comprovante) e anexado ao processo.

## Painel (dashboard)

- `web/HomeController` monta a planilha (`PainelLinha`) com os 3 médicos e o
  status de cada parecer; card "Em andamento" soma todos os status não-finais.
- `templates/dashboard.html` usa os badges com as cores por status via
  helpers do enum (`getBootstrapBadge`).
- `lista.html` e `detalhe.html` usam `status.bootstrapBadge`.
- **O dashboard é 100% Bootstrap + `app.css`** (migrado do Tailwind no
  commit `3bfba9b`, 2026-07-09). Não existe mais nenhum arquivo CSS gerado
  por Tailwind no projeto (`static/css/tailwind-dashboard.css` foi apagado em
  2026-07-29, junto com o `node_modules`/experimento bun/typescript nunca
  usado — ver "Organização do repositório" no `CLAUDE.md`). **Não recriar**
  esse arquivo nem reintroduzir Tailwind — o design system vive inteiramente
  em `app.css` com variáveis `--rs-*`.

## Pontos de atenção

- A transição para `SOLICITA_INFORMACAO`/`ENVIADO` é recalculada a cada voto
  (`atualizarStatusPorPareceres`); nunca rebaixa um processo já finalizado
  (lança `IllegalStateException` se tentarem chamar isso sobre um processo
  finalizado).
- Processo ENCERRADO (`DEFERIDO`/`INDEFERIDO`/`CANCELADO`) trava as etapas
  1-3 (Envio, Respostas, Decisão) e o upload genérico/exclusão de
  anexos/lembretes (`ProcessoValidator.edicaoBloqueada`); as etapas 4-5
  continuam liberadas (papelada pós-decisão). Só ADMIN reabre
  (`POST /processos/{id}/reabrir`).
