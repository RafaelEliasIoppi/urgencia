# Plano de Refatoração — SGPUR Desktop + textos padronizados + ofício automático

## Contexto e objetivo
O SGPUR **não precisa ser cliente-servidor** (roda em 1 máquina, 1 usuário).
Vamos empacotá-lo como **app desktop standalone com banco local (H2 em arquivo)**,
o que **elimina a hospedagem** (sem Neon/Oracle/cartão/deploy). Preserva 100% do
código Spring Boot atual — a UI continua via navegador local.

Além do desktop, refinar regras de negócio:
- Texto padrão à Câmara da Urgência Renal identificado por **nº do processo +
  INICIAIS do paciente** (LGPD).
- **Todas as comunicações padronizadas** (médicos e solicitante).
- **Ofício de indeferimento gerado automaticamente** (documento formal).

---

## Parte A — Empacotamento Desktop

### A1. Banco H2 em pasta de dados do usuário (persistente)
- Novo perfil **`desktop`** com:
  `jdbc:h2:file:${user.home}/.sgpur/db/sgpur;AUTO_SERVER=TRUE`
- Anexos em `${user.home}/.sgpur/anexos` (via `app.anexos.dir`).
- Assim os dados persistem independente de onde o app foi instalado, e o
  backup é só copiar a pasta `~/.sgpur`.

### A2. Abrir o navegador automaticamente ao iniciar
- `ApplicationRunner` (ativo só no perfil `desktop`) que chama
  `java.awt.Desktop.browse("http://localhost:8080")` após o start.
- Proteções: checar `Desktop.isDesktopSupported()` e não-headless; logar e
  seguir caso falhe (o usuário abre o navegador manualmente).

### A3. Empacotar com `jpackage` (vem no JDK 21)
- Build do fat jar: `mvn -DskipTests package`.
- `jpackage --type app-image` (sem dependência externa) gerando uma pasta com
  `SGPUR.exe` + **JRE embutida** (o usuário NÃO precisa ter Java instalado).
- Script `package-desktop.ps1` automatizando: limpa, builda, roda jpackage,
  zipa o resultado em `dist/SGPUR-desktop.zip`.
- Ícone `.ico` (gero um simples) e nome "SGPUR".
- Opcional futuro: `--type msi` (exige WiX Toolset) para instalador com atalho
  no Menu Iniciar. Começo com app-image (mais simples e portátil).

### A4. Perfil padrão do executável
- O `SGPUR.exe` sobe com `--spring.profiles.active=desktop` (via
  `--java-options "-Dspring.profiles.active=desktop"` no jpackage).
- Admin inicial continua semeado (admin / senha definível).

---

## Parte B — Regras de negócio

### B1. Iniciais do paciente
- Util `Iniciais.de(nomeCompleto)` → ex.: "Mariana da Rosa Martins" → **"M.R.M."**
  (ignora conectivos: da, de, do, dos, das, e). Em maiúsculas, com pontos.
- Usar no `EmailTemplateService` (e-mail aos médicos) como identificador:
  "Processo 07/2026 — Paciente M.R.M.".
- (Nome completo do paciente continua oculto para os médicos — LGPD.)

### B2. Comunicações padronizadas
- Manter/− revisar os modelos do `EmailTemplateService`: envio aos médicos,
  resposta ao solicitante (deferido/indeferido). Acrescentar, se quiser,
  "confirmação de recebimento".

### B3. Ofício de indeferimento automático
- Novo `OficioService` gera **PDF formal** (modelo de ofício) com: cabeçalho
  (Secretaria de Saúde — placeholder), nº do ofício, data, referência ao
  processo, **nome do paciente** (documento oficial ao solicitante), **motivo**
  do indeferimento e fecho/assinatura.
- Geração **automática ao indeferir** (igual ao Relatório Final): cria o PDF e
  anexa como `TipoAnexo.OFICIO_INDEFERIMENTO` (substitui anterior).
- Botão/endpoint `GET /processos/{id}/oficio` para baixar.
- `FluxoProcessoService`: a etapa "Ofício" passa a concluir quando o ofício
  (anexo automático) existe + motivo preenchido.

---

## Ordem de execução (incremental, com commit a cada passo)
1. **Iniciais** (util + teste) e ajuste do e-mail aos médicos.
2. **OficioService** + auto-geração no indeferimento + endpoint/botão + ajuste do fluxo.
3. **Perfil `desktop`** (H2 em ~/.sgpur) + auto-open browser.
4. **Script jpackage** + ícone + gerar app-image e zipar.
5. **Docs** (README desktop / como instalar e fazer backup) + memória/agente.

## Verificação
- `mvn test` continua verde (novos testes: iniciais; ofício gerado).
- Rodar perfil desktop: navegador abre sozinho; dados em `~/.sgpur`.
- Indeferir um processo → **ofício PDF** gerado e anexado; baixar e conferir.
- E-mail aos médicos mostra **iniciais**, sem nome completo.
- `jpackage` gera `SGPUR.exe`; abrir e validar o fluxo ponta a ponta.

## Decisões em aberto (confirmar antes de codar)
1. Formato das iniciais: **"M.R.M."** (com pontos, ignorando da/de) — ok?
2. Ofício: usa **nome completo** do paciente (documento oficial ao solicitante) — ok?
   Cabeçalho/assinatura: usar placeholders "Secretaria de Saúde" e
   "Responsável / Cargo" para você editar depois?
3. Empacotamento: começar por **app-image** (pasta + .exe, sem instalador) — ok?

## Observações
- Por não ser cliente-servidor, **cada PC tem seu próprio banco** (dados não são
  compartilhados entre máquinas). Se um dia precisar compartilhar, volta um
  banco central (Neon) — o código já suporta (perfil `prod`).
- Mantemos o perfil `prod` (Neon) e os artefatos de deploy web como alternativa.
