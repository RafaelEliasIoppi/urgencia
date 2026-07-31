# SAUR — Sistema de Gestão de Processos de Urgência Renal

[![CI](https://github.com/RafaelEliasIoppi/urgencia/actions/workflows/ci.yml/badge.svg)](https://github.com/RafaelEliasIoppi/urgencia/actions/workflows/ci.yml)

Sistema web que substitui a planilha Excel utilizada pela equipe de **Urgência
Renal** da Secretaria de Saúde, informatizando todo o fluxo de um processo —
do recebimento da solicitação até o deferimento ou indeferimento — de forma
segura, auditável e com **Relatório Final em PDF**.

## Funcionalidades

- **Portal do Solicitante** (`/solicitante`): a equipe solicitante envia o
  pedido online; todo `Processo` nasce de uma dessas solicitações, triada e
  convertida pelo operador (não há mais cadastro manual "do zero").
- **Envio a exatamente 3 médicos** avaliadores, com PDF único anonimizado
  (só iniciais do paciente) e convite automático ao Portal do Avaliador.
- **Portal do Avaliador** (`/avaliador`): único caminho para registrar um
  parecer — o médico se autentica e vota diretamente no sistema
  (não-repúdio com auditoria de IP). Não existe mais lançamento manual de
  parecer pelo operador.
- **Regra de decisão:** 2 de 3 favoráveis = Deferido; 2 de 3 desfavoráveis =
  Indeferido. **Coordenador CET-RS defere sozinho** com voto favorável.
- **Decisão automática** por maioria simples — dispara no evento (voto do
  avaliador) e também numa varredura periódica de segurança, além de poder
  ser feita manualmente pelo operador.
- **Fluxo em 6 etapas:** Recebimento (automático) → Envio → Respostas →
  Decisão → Ofício/Comprovante → Resposta ao solicitante (checklist visual).
- **Textos de e-mail prontos** (copiar/colar) e envio automático de e-mail
  via SMTP em pontos-chave do fluxo (convite ao avaliador, resposta final).
- **Anexos** de documentos em cada etapa, com trava de anonimização para
  documentos vindos do Portal do Solicitante (o operador precisa confirmar
  explicitamente que removeu o nome do paciente antes de liberá-los aos
  avaliadores).
- **Relatório Final em PDF** (documento oficial para arquivamento e auditoria).
- **Gestão de membros** da Urgência Renal e **de usuários** (login via banco,
  perfis Administrador/Operador/Avaliador/Solicitante).
- **Assistência por IA (Gemini):** resumo de anexos PDF e sugestão de motivo
  de indeferimento (opcional, desligado por padrão em produção).
- **Indicador de tempo de resposta** dos avaliadores (média em dias, fora do
  prazo configurável).

## Stack

- Java 21, Spring Boot 3.5 (Web, Data JPA, Thymeleaf, Security, Validation)
- **PostgreSQL** em produção (desde 2026-07-25 rodando na própria VM Oracle,
  `localhost:5432`; usou Neon até essa data — ver "Configuração" abaixo) ·
  H2 em desenvolvimento
- Thymeleaf + Bootstrap 5 · OpenPDF (relatórios)
- Maven (artifactId `saur`, gera `target/saur-0.0.1-SNAPSHOT.jar`)

## Como rodar

Requisitos: **JDK 21** e **Maven**.

### Desenvolvimento (H2, sem banco externo)
```powershell
.\start.ps1
```
Acesse **http://localhost:3000** (a porta mudou de 8080 para 3000) — login
inicial **admin / Admin123!**, criado automaticamente na primeira subida (só
quando a tabela `usuario` está vazia). Console do H2 em `/h2-console`.

### Produção (Postgres)
As credenciais ficam em `application-local.yml` (não versionado) ou no
`deploy/sgpur.env` via variáveis de ambiente:
```powershell
.\start.ps1 prod
```
Produção real roda com **Postgres na própria VM** (`localhost:5432`, banco
`sgpur`) — não mais no Neon. Se preferir usar um Postgres externo (ex.: Neon)
em vez do Postgres local, basta trocar a `SPRING_DATASOURCE_URL` pela
connection string do provedor (ver `deploy/sgpur.env.example`).

### Testes
```powershell
.\test.ps1
```
Sempre com JDK 21. Não há empacotamento desktop — o projeto é só web desde
2026-07-03 (rode via `start.ps1` e acesse pelo navegador).

### E2E (Playwright)
```powershell
.\e2e.ps1
```
Sobe o SAUR real (H2, porta aleatória) e um Chromium de verdade, clicando na
tela pelo fluxo completo (login → solicitação online → triagem/conversão →
Recebimento → Envio → pareceres pelo Portal do Avaliador → Decisão →
Finalização). `-Headless` roda sem janela; profile Maven `e2e`, separado dos
testes rápidos (`mvn verify -Pe2e`).

## Modo teste de e-mail

Em dev, `app.mail.override-recipient` (default `rafaelioppi@gmail.com`)
redireciona **todo** e-mail enviado para esse endereço, com prefixo
`[TESTE - para: ...]` no assunto. Em prod fica vazio (envio real) — ver
`docs/PROTOCOLO-TESTE-PRODUCAO.md` antes de testar qualquer fluxo direto em
produção.

## Configuração

| Variável | Padrão | Descrição |
|---|---|---|
| `SGPUR_ADMIN_USER` | `admin` | login do administrador inicial |
| `SGPUR_ADMIN_PASSWORD` | `Admin123!` (dev) ou **obrigatória em prod** | senha do administrador inicial |
| `SGPUR_BASE_URL` | `http://localhost:3000` | URL base para links nos e-mails (portal avaliador) |
| `SGPUR_MAIL_HOST` | `smtp.gmail.com` | servidor SMTP |
| `SGPUR_MAIL_PORT` | `587` | porta SMTP |
| `SGPUR_MAIL_USER` | — | usuário SMTP |
| `SGPUR_MAIL_PASS` | — | senha SMTP (app password) |
| `SGPUR_MAIL_FROM` | — | remetente dos e-mails |
| `SPRING_DATASOURCE_URL` | — | JDBC URL do PostgreSQL (produção: Postgres local da VM, `jdbc:postgresql://localhost:5432/sgpur`; pode apontar para um Postgres externo, ex. Neon, se preferir) |
| `SPRING_DATASOURCE_USERNAME` | — | usuário do banco |
| `SPRING_DATASOURCE_PASSWORD` | — | senha do banco |
| `SGPUR_SOLICITANTE_HABILITADO` | `true` (dev) / `false` (prod) | liga/desliga o Portal do Solicitante — **necessário para cadastrar qualquer processo**, já que todo processo nasce de uma solicitação convertida |
| `SGPUR_GEMINI_ENABLED` | `true` (dev) / `false` (prod) | liga a assistência por IA (Gemini) — desligada por padrão em produção por proteção de dados |
| `app.anexos.dir` | `./data/anexos` | diretório de armazenamento dos anexos |
| `app.avaliador.prazo-dias` | `7` | prazo-meta em dias para resposta dos avaliadores |
| `app.gemini.api-key` | — | chave da API Google Gemini (IA) |

> Em produção, defina **`SGPUR_ADMIN_PASSWORD`** e as credenciais de banco/SMTP
> antes do primeiro deploy. `AdminBootstrap` só cria o admin se a tabela
> `usuario` estiver vazia. Nunca versione segredos.

## Estrutura

```
domain/      entidades JPA (Processo, MembroUrgenciaRenal, Parecer, Anexo,
             Usuario, ControleUrgencia, SolicitacaoOnline, MensagemSolicitacao)
             e enums (StatusProcesso, ResultadoParecer, TipoAnexo, Perfil,
             OrigemParecer, StatusSolicitacaoOnline)
repository/  repositórios Spring Data
service/     regras de negócio (ProcessoService, FluxoProcessoService,
             ProcessoValidator, EmailTemplateService, EmailSenderService,
             RelatorioService, AnexoStorageService, DecisaoFinalService,
             TempoRespostaService, RelatorioAnualService,
             RelatorioAvaliadorService, GeminiService,
             SolicitacaoAvaliadorService, RegistroEnvioService, OficioService,
             UsuarioService, ControleUrgenciaService, ConflitoEquipeMatcher,
             SolicitacaoOnlineService, MensagemSolicitacaoService, dto/)
web/         controllers MVC — vários controllers, não um monolítico
             (ProcessoListaController, ProcessoDetalheController,
             ProcessoDecisaoController, ProcessoAnexoController,
             AvaliadorController, SolicitanteController,
             SolicitacaoOnlineTriagemController, UsuarioController,
             AuditoriaController, MembroController, ControleUrgenciaController,
             RelatorioController, dto/)
bootstrap/   inicialização de boot (AdminBootstrap, MembroDevSeed,
             SchemaMigration, EnumCheckConstraintValidator)
config/      configuração Spring de verdade (SecurityConfig,
             AgendamentoConfig, EmailProperties)
templates/   páginas Thymeleaf · static/ CSS (Bootstrap + app.css, sem Tailwind)
```

## Regras de negócio (resumo)

- Cada processo vai para **3 médicos**; **2 favoráveis = Deferido**;
  **2 desfavoráveis = Indeferido** (exige ofício + motivo).
- **Coordenador CET-RS defere sozinho** com voto favorável (não para indeferir
  — e fica vedado indeferir manualmente enquanto ele já votou favorável).
- Status: `Solicitado` → `Enviado` → `Deferido` / `Indeferido` /
  `Solicita informação` (pausa) / `Cancelado`. Finais: Deferido/Indeferido/
  Cancelado. (O antigo sinônimo legado `EM_ANALISE` foi removido do enum em
  2026-07-29 — não existe mais no código.)
- **Todo processo nasce de uma solicitação do Portal do Solicitante**,
  triada e convertida pelo operador — não há mais cadastro manual "do zero".
- **Parecer só entra pelo Portal do Avaliador** — o operador não lança/edita
  mais resultado de parecer manualmente.
- Indeferimento **exige** motivo + ofício + data de emissão + envio ao solicitante.
- Deferido **exige** comprovante de inserção no SNT antes de a resposta ao
  solicitante poder ser finalizada.
- Processo encerrado (final) **trava edição** das etapas 1-4; papelada pós-decisão
  (ofício, SNT, confirmar resposta) continua liberada. Só ADMIN reabre.
- Numeração `NN/AAAA`: **manual em 2026**, **automática a partir de 2027**.
- Avaliadores veem só **iniciais** do paciente (imparcialidade); comunicação
  com o solicitante usa **nome completo**.
- "Membros da Urgência Renal" (nunca "Câmara Técnica").
- **Não há mais empacotamento desktop** (só web desde jul/2026).

Ver `CLAUDE.md` (raiz do repo) e `docs/PLANO-FLUXO.md` para o detalhamento
completo de cada regra e a correspondência etapa → endpoint/serviço.

---
Documento oficial gerado pelo sistema: **Relatório Final do Processo de
Urgência Renal**.
