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
- Testes: `mvn test` (19 testes). Build: `mvn -DskipTests package` (gera o JAR).
- Sempre compile/rode com o **JDK 21**.

## Regras de negócio (não violar)
- Cada processo vai para **exatamente 3 médicos**; **2 favoráveis = Deferido**,
  senão **Indeferido** (exige **ofício + motivo**).
- Status: `Em análise` → `Deferido` / `Indeferido` / `Cancelado`.
- Numeração `NN/AAAA`: **manual em 2026**, **automática a partir de 2027**.
- Fluxo por e-mail com anexos por etapa; e-mail aos médicos **oculta dados do
  paciente** (LGPD). Decisão manual com **sugestão automática** (regra 2/3).
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
