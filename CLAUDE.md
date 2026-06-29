# CLAUDE.md — Guia do projeto SGPUR

Sistema de Gestão de Processos de Urgência Renal (SGPUR). Substitui a planilha
Excel da equipe de Urgência Renal da Secretaria de Saúde.

> **Regra de ouro:** para tarefas deste sistema, use o agente **`urgencia-renal`**
> (`.claude/agents/urgencia-renal.md`) — ele concentra a arquitetura e as regras.

## Stack
Java 21 · Spring Boot 3.3 (web, data-jpa, thymeleaf, security, validation) ·
PostgreSQL/Neon (prod) e H2 (dev) · Thymeleaf + Bootstrap · OpenPDF · Maven.
Pacote base `br.gov.saude.sgpur`.

## Toolchain (Windows desta máquina)
- JDK 21: `C:\Users\rafae\Tools\jdk-21.0.11+10` (NÃO usar o Java 17 do sistema).
- Maven: `C:\Users\rafae\Tools\apache-maven-3.9.6`.

## Como rodar / testar
```powershell
.\start.ps1            # dev (H2) — sandbox de teste
.\start.ps1 prod       # prod (Neon) — usa application-local.yml (gitignored)
```
- App em http://localhost:8080 · login inicial `admin` / `admin123`.
- Testes: `.\test.ps1` (ou `mvn test`) — **39 testes**, sempre com **JDK 21**.
  Build: `mvn -DskipTests package` (gera o JAR).
- **Desktop:** `.\release.ps1` faz tudo (pull + `.exe` + `SGPUR-Setup.exe` +
  **reinstala** em `C:\Program Files\SGPUR`). Use ao mexer em telas/CSS — só
  `package-desktop.ps1` não atualiza a versão instalada (causa do bug "CSS
  antigo": o atalho abria a versão velha em Program Files).

## Regras de negócio (não violar)
- Cada processo vai para **exatamente 3 médicos**. Decisão por **maioria
  simples (2 de 3)**: **≥2 favoráveis = Deferido**; **≥2 desfavoráveis =
  Indeferido** (exige **ofício + motivo**). As duas regras são **impostas** no
  serviço e no controller (`decidir` rejeita Deferido sem 2 favoráveis e
  Indeferido sem 2 desfavoráveis).
- **Toda resposta de médico recebida** (parecer com `resultado` preenchido)
  **precisa ter o anexo comprobatório** (`TipoAnexo.RESPOSTA_AVALIADOR`
  vinculado ao parecer) **antes de Deferir/Indeferir**. Imposto no serviço e no
  controller (`pareceresRecebidosSemAnexo`) e refletido na etapa "Respostas dos
  médicos" do fluxo. Como a decisão exige ≥2 pareceres, isso garante ≥2 anexos.
- **Deferido exige anexar o comprovante de inserção da urgência renal no SNT**
  (`TipoAnexo.COMPROVANTE_SNT`) e enviá-lo junto na resposta ao solicitante; a
  etapa "Comprovante SNT" bloqueia a conclusão até o anexo existir (simétrico
  ao ofício no indeferimento). O comprovante é gerado fora do sistema.
- Status (ciclo expandido, reflete a planilha): `Solicitado` → `Enviado` →
  { `Deferido` / `Indeferido` / `Solicita informação` } (+ `Cancelado`).
  Finais: Deferido/Indeferido/Cancelado. `Em análise` é mantido como sinônimo
  legado de `Enviado` (registros antigos continuam válidos). Ver
  `docs/PLANO-FLUXO.md`.
- **Solicita informação (PAUSA):** quando um avaliador vota
  `ResultadoParecer.SOLICITA_INFORMACAO`, o processo entra em
  `StatusProcesso.SOLICITA_INFORMACAO` (via
  `ProcessoService.atualizarStatusPorPareceres`, chamado em `salvarPareceres`).
  Isso **pausa o fluxo**: a Decisão fica **bloqueada** — `ProcessoService.decidir`
  lança erro ao tentar Deferir/Indeferir, o controller devolve flash de erro e a
  aba **4. Decisão** fica travada (`liberadoDecisao=false`). O checklist
  (`FluxoProcessoService`) insere a etapa **"Informacao complementar"** com o
  aviso *"Aguardando informacao complementar do solicitante"*. O sistema gera o
  e-mail pronto *"Pedido de informacao complementar ao solicitante"*
  (`EmailTemplateService.emailSolicitaInfo`) endereçado à **equipe solicitante**,
  com nº do processo + **nome completo** do paciente (e-mail ao solicitante leva
  o nome completo; só o material dos avaliadores usa iniciais). Na
  aba **3. Respostas** o operador tem dois botões: **registrar o reenvio** ao
  solicitante (`POST /processos/{id}/solicitar-info`, anexa cópia do e-mail em
  `TipoAnexo.INFO_COMPLEMENTAR`, mantém a pausa) e **registrar o recebimento +
  retomar a análise** (`POST /processos/{id}/retomar-analise` →
  `ProcessoService.retomarAposInformacao`): o processo **volta para `Enviado`**,
  os pareceres marcados como *Solicita informação* são **reabertos** (resultado
  limpo) para o voto definitivo, e o fluxo de Respostas/Decisão é liberado.
- **Fluxo em 6 passos** (checklist `FluxoProcessoService` + abas na tela):
  **1 Recebimento · 2 Envio · 3 Respostas · 4 Decisão · 5 Ofício/Comprovante ·
  6 Resposta ao solicitante**.
- **Passo 1 (Recebimento):** exige a **cópia da solicitação original**
  (`SOLICITACAO_RECEBIDA`, manual) **+** a **capa do processo**
  (`CAPA_PROCESSO`, **gerada pelo sistema** com dados do solicitante e os 3
  médicos — reaproveita a capa do Relatório Final via
  `RelatorioService.gerarCapaProcesso`). Endpoint
  `POST /processos/{id}/recebimento`. A etapa bloqueia até os dois existirem.
- **Passo 2 (Envio):** ao registrar o envio o sistema gera a **cópia anonimizada
  para as equipes** (`SOLICITACAO_AVALIADOR`, só iniciais), nome oficial
  `Processo CET-RS NN-AAAA - Paciente X.X.X.pdf`
  (`SolicitacaoAvaliadorService.nomeArquivoOficial`). Esse anexo é um **PDF único
  consolidado** = **folha-rosto** (gerada pelo sistema, só iniciais —
  `SolicitacaoAvaliadorService.gerar`) **+** os **documentos clínicos
  anonimizados** anexados ao processo (`DOCUMENTO_CLINICO_AVALIADOR`, só os que
  forem PDF), unidos por `SolicitacaoAvaliadorService.consolidar`. A **solicitação
  original** (`SOLICITACAO_RECEBIDA`) **NUNCA** entra nesse PDF — contém o nome
  completo do paciente, e os avaliadores julgam sem saber quem é o paciente
  (imparcialidade). Documentos clínicos não-PDF são ignorados do merge
  com **aviso não-bloqueante** (flash `aviso`). Os documentos clínicos são
  anexados na própria aba Envio (`POST /processos/{id}/documento-clinico`).
  **Aviso (não bloqueia)** se algum médico for da mesma equipe/instituição do
  solicitante.
- Numeração `NN/AAAA`: **manual em 2026**, **automática a partir de 2027**.
- Fluxo por e-mail com anexos por etapa. **Identificação do paciente:** o
  e-mail/material aos **médicos avaliadores oculta o nome** do paciente (só
  iniciais), para preservar a **imparcialidade do julgamento** — os avaliadores
  decidem sem saber quem é o paciente (convenção da equipe de Urgência Renal,
  **não** é LGPD). Já os e-mails/documentos dirigidos à **equipe solicitante**
  (pedido de informação complementar, resposta de Deferido/Indeferido) levam o
  **nome completo** do paciente. Decisão manual com **sugestão automática** por
  maioria simples (2/3 favoráveis → Deferido; 2/3 desfavoráveis → Indeferido).
- "Membros da Urgência Renal" (nunca "Câmara Técnica").

## Convenções de código
- Entidades JPA em `domain/` com getters/setters simples (sem Lombok).
- Serviços em `service/`, controllers em `web/`, repos em `repository/`.
- Templates Thymeleaf usam os fragments de `templates/layout.html`.
- Não commitar segredos: `application-local.yml`, `deploy/sgpur.env` e `/dist/`
  estão no `.gitignore`.

## Deploy
Artefatos em `deploy/` (systemd, nginx, env de exemplo, guia). Host alvo:
**Oracle Always Free (São Paulo)** — ver `deploy/README-deploy.md`.
A **Vercel não hospeda o app Java** (só serve de banco).
