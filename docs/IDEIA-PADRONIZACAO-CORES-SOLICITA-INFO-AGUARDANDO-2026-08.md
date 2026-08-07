# Padronização de cores — "Solicita informação" = amarelo, "Aguardando" = azul

**Status: IMPLEMENTADA em 2026-08-06.**

Pedido do dono do produto em 2026-08-06: *"solicita informação deve ser cor
amarela e o aguardando deveria ser de cor azul, pra padronizar tudo."*

## O que foi feito

Todos os pontos levantados na tabela abaixo (mais alguns achados numa
varredura própria por `grep -rn "SOLICITA_INFORMACAO|Aguardando"` em todo
`src/main/resources/templates` e `src/main/java`) foram corrigidos para a
regra: **"Solicita informação" (a pausa do processo, aguardando resposta do
solicitante) = amarelo (`bg-warning`/`--rs-gold`)**; **"Aguardando" (algo
pendente do lado interno do sistema, ex.: médico ainda não votou, pedido
ainda não triado) = azul (`bg-primary`/`--rs-blue`)**.

Seguida a recomendação do documento (Opção A): foi criado um **5º tom
semântico** `"aguardando"` (distinto de `"attention"`), com token
`--saur-state-aguardando` (`app.css`) e caso próprio no fragment
`layout :: tomBadge`. `StatusProcesso.getTom()`/`PainelLinha.CelulaMedico
.tom()`/`SituacaoPedidoView.tom()` foram atualizados para emitir
`"aguardando"` onde antes emitiam `"attention"`/`"neutral"` incorretamente
(esses métodos `tom()` continuam sem consumidor direto em nenhum template
nesta leva — mesma situação de infraestrutura já documentada no CLAUDE.md
para o design system —, mas ficaram corretos e coerentes com os campos
`cor`/`classeCor`/`bootstrapBadge` que os templates de fato leem).

Pontos corrigidos:
- `StatusProcesso.getBootstrapBadge()`: `SOLICITA_INFORMACAO` de `bg-info`
  para `bg-warning text-dark` (agora concorda com `getTom()`, que já dizia
  `"attention"`/amarelo).
- `StatusSolicitacaoOnline.getBootstrapBadge()`: `ENVIADA` ("Enviada,
  aguardando triagem") de `bg-warning` para `bg-primary` — usado nos badges
  de `/processos/solicitacoes-online`, `solicitante/lista.html` e
  `solicitacoes-online-detalhe.html`.
- `PainelLinha.CelulaMedico`: "Aguardando" (médico não votou) de
  `cor="warning"` para `cor="primary"`; "Solicita info" (voto do médico) de
  `cor="info"` para `cor="warning"`. `dashboard.html` (planilha do Painel,
  `th:switch="${c.cor}"`) ganhou o caso `'primary'` → `text-bg-primary`.
- `dashboard.html`: `.legend-dot-aguardando` de `var(--rs-gold)` para
  `var(--rs-blue)`.
- `SolicitanteController.montarSituacaoPedido`: "Aguardando triagem" e
  "Aguardando análise" de `warning`/`info` para `primary` (azul);
  "Informação necessária" (a pausa ativa, aguardando upload do solicitante)
  **manteve** `warning` (já estava correto/amarelo).
- `processos/detalhe.html`: badge do voto `SOLICITA_INFORMACAO` (`bg-primary`
  → `bg-warning text-dark`); card "Aguardando informação complementar do
  solicitante" (a pausa em si, `border-info`/`bg-info` → `border-warning`/
  `bg-warning`); badge dinâmico `statusSubrotulo` ("Aguardando parecer (x/y)"
  — pendência interna de votos dos avaliadores, não a pausa — `bg-warning`
  → `bg-primary`).
- `avaliador/lista.html` (2 ocorrências, tabela e cards mobile): badge do
  voto `SOLICITA_INFORMACAO` de `bg-primary` para `bg-warning text-dark`.

Não alterado (fora do escopo da regra, cores diferentes por motivo próprio,
não confundir com "Aguardando"/"Solicita informação"): badges de status
finais (Deferido=verde, Indeferido=vermelho, Cancelado=cinza/preto), o card
"Devolvidas" do Portal do Solicitante (âmbar, é "você precisa agir de novo",
diferente de "aguardando"), a legenda `legend-dot-sem-medico`, e os badges
`bg-warning`/`bg-info` usados para avisos genéricos sem relação com os dois
conceitos (ex.: "não-PDF", "sem e-mail cadastrado", contador "avaliados" de
`membros/lista.html`).

Nenhuma regra de negócio mudou — só cor/classe CSS. Suíte completa validada
(810 testes, 0 falhas, JDK 21) após as mudanças.

## Estado atual (levantado por grep, para não perder o contexto)

Hoje as duas cores estão **inconsistentes entre si e ao contrário** do que
foi pedido:

| Onde | Rótulo | Cor hoje | Mecanismo |
|---|---|---|---|
| `StatusProcesso.getBootstrapBadge()` ([StatusProcesso.java:74](../src/main/java/br/gov/saude/sgpur/domain/StatusProcesso.java#L74)) | "Solicita informacao" (status do processo) | `bg-info` (**azul/ciano**) | classe Bootstrap direta (deprecated) |
| `StatusProcesso.getTom()` ([StatusProcesso.java:94](../src/main/java/br/gov/saude/sgpur/domain/StatusProcesso.java#L94)) | idem | tom `"attention"` → `bg-warning` (**amarelo**) via `layout :: tomBadge` | vocabulário semântico (não usado em todos os templates ainda, ver "Design system" no CLAUDE.md) |
| `PainelLinha.CelulaMedico` ([PainelLinha.java:98](../src/main/java/br/gov/saude/sgpur/web/dto/PainelLinha.java#L98)) | "Aguardando" (médico ainda não votou) | `cor = "warning"` (**amarelo**) | campo `cor` direto, usado no Painel |
| `PainelLinha.CelulaMedico` ([PainelLinha.java:106](../src/main/java/br/gov/saude/sgpur/web/dto/PainelLinha.java#L106)) | "Solicita info" (voto deste médico) | `cor = "info"` (**azul**) | idem |

Ou seja: **já existem duas fontes divergentes para a mesma cor** (o badge
direto do status diz azul, o `tom()` semântico do mesmo status diz amarelo),
e a tabela do Painel já faz hoje o **oposto exato** do que foi pedido
(Aguardando=amarelo, Solicita info=azul). Padronizar exige alinhar todos os
pontos abaixo na mesma direção, não só trocar uma cor isolada.

## O que a ideia pede

- **"Solicita informação"** (a pausa do processo aguardando resposta do
  solicitante) → **amarelo** em todo lugar.
- **"Aguardando"** (algo pendente do lado interno do sistema — ex.: médico
  ainda não votou) → **azul** em todo lugar.

## Tensão com o design system já documentado (ver CLAUDE.md, "Design system — régua de tokens")

O projeto já decidiu deliberadamente um vocabulário semântico de **só 4
tons**: `"ok"|"danger"|"attention"|"neutral"` (`StatusProcesso.getTom()`,
`SituacaoPedidoView.tom()`, `PainelLinha.CelulaMedico.tom()`,
`EtapaFluxo.tom()`) — hoje tanto "Solicita informação" quanto "Aguardando"
caem no MESMO tom (`"attention"`, que os métodos `tom()` mapeiam a partir de
`"warning"` OU `"info"` indistintamente — ver
[PainelLinha.java:78](../src/main/java/br/gov/saude/sgpur/web/dto/PainelLinha.java#L78) e
[SituacaoPedidoView.java:76](../src/main/java/br/gov/saude/sgpur/web/dto/SituacaoPedidoView.java#L76)).

Isso significa que atender o pedido do jeito mais simples (dar cores
diferentes às duas coisas) **não cabe no vocabulário de 4 tons como está
hoje** — exige uma decisão de design system, não só uma troca de classe
CSS:

- **Opção A** — criar um 5º tom semântico (ex. `"aguardando"` ou
  `"pendente-interno"`, distinto de `"attention"`), com seu próprio token
  `--saur-state-*` e entrada no fragment `layout :: tomBadge`. Mantém a
  filosofia "tom em vez de classe Bootstrap" do resto do sistema.
- **Opção B** — tratar como caso especial fora do vocabulário de tom (cor
  Bootstrap direta só nesses dois lugares), mais rápido mas quebra a
  convenção que o resto do sistema já vem seguindo.

Recomendação para quem for implementar: **Opção A**, para não reabrir a
inconsistência "classe Bootstrap direta vs. tom semântico" que já existe
hoje nesses dois pontos (ver tabela acima).

## Escopo a revisar quando for implementar

Não levantado exaustivamente nesta sessão (só a ideia foi registrada, a
pedido do usuário) — ao implementar, conferir pelo menos:
- `StatusProcesso.getBootstrapBadge()`/`getTom()` (status do processo,
  usado no badge de `/processos`, `/arquivo`, detalhe do processo).
- `PainelLinha.CelulaMedico` (tabela do Painel, por médico).
- Qualquer badge de `StatusSolicitacaoOnline` (Portal do Solicitante/
  triagem) que também use as palavras "Aguardando"/"Solicita informação" —
  não conferido nesta sessão.
- `EtapaFluxo`/`SituacaoPedidoView` (timeline do processo e do solicitante)
  se algum passo usar essas duas palavras.
- Consistência entre o campo `cor` legado (usado direto em templates) e o
  `tom()` semântico — os dois precisam concordar depois da mudança, ao
  contrário do estado atual (ver tabela).

Ver também [[project_bug_pausa_bloqueia_avaliadores]] (mesmo status
`SOLICITA_INFORMACAO`, achado na mesma sessão, bug funcional diferente desta
ideia de cor — não confundir os dois ao implementar).
