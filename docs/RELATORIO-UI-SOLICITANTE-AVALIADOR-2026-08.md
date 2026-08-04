# Relatório de UI/UX — Portal do Solicitante e experiência dos Avaliadores

**Data:** 2026-08-03
**Escopo:** (1) Portal do Solicitante (`/solicitante/**`), (2) Portal do Avaliador
(`/avaliador/**`) e (3) card "Respostas dos Avaliadores" na tela de detalhe do
processo (visão do operador).
**Natureza:** documento de diagnóstico + plano de execução. **Nenhum código foi
alterado.** As fases abaixo foram escritas para serem executadas por um agente
Sonnet, uma por vez, sem ambiguidade.

**Restrições inegociáveis assumidas por todo o documento:**
- Bootstrap 5.3.8 + `static/css/app.css` (variáveis `--rs-*`). **Nenhuma
  biblioteca nova**, nenhum Tailwind, nenhum framework de componentes.
- JS sempre em `static/js/*.js`, nunca inline. Feedback via `mostrarToast()`.
- Thymeleaf: no máximo 2 níveis de ternário em atributo; nunca `th:if` +
  `th:unless` no mesmo elemento; `/*[[...]]*/` exige `th:inline="javascript"`.
- **Imparcialidade:** avaliador vê **somente iniciais**. Nenhuma melhoria pode
  expor nome do paciente, equipe solicitante, co-avaliadores ou votos alheios ao
  avaliador.
- Voto só entra pelo Portal do Avaliador, é irreversível, e a decisão é por
  maioria simples 2/3 (com a exceção do coordenador CET-RS).

---

## 0. Leitura geral

O sistema **não está mal desenhado**. Há evidência clara de cuidado anterior:
partição de atrasados no painel do avaliador, modal de confirmação do voto com
checkbox de ciência e fallback para `confirm()`, versões mobile em cards para as
listas, `prefers-reduced-motion` no CSS, paleta validada para daltonismo, timeline
compartilhada entre solicitante e processo, testes que varrem template atrás de
ícone inexistente e de botão só-ícone sem rótulo acessível.

O problema hoje **não é feiúra — é hierarquia e carga cognitiva**. Nas três áreas
analisadas repete-se o mesmo padrão: a informação que o usuário precisa para agir
está correta e presente, mas empilhada em blocos de igual peso visual, atrás de
rolagem, ou repetida em três formatos diferentes na mesma tela. Para dois públicos
que usam o sistema **sob pressão e sem familiaridade com TI** (equipe de hospital
pedindo urgência renal; médico avaliador que entra 1x por processo, muitas vezes
pelo celular), isso se traduz em ansiedade: "eu preciso fazer alguma coisa? o quê?
já mandei? vai demorar quanto?".

O eixo do plano é, portanto: **uma tela, uma pergunta respondida, uma ação óbvia.**

---

## 1. Diagnóstico — Portal do Solicitante

Arquivos: `src/main/resources/templates/solicitante/lista.html` (201 linhas),
`nova.html` (82), `detalhe.html` (383), `indisponivel.html` (31);
`src/main/resources/static/js/solicitante-nova.js`;
`src/main/java/br/gov/saude/sgpur/web/SolicitanteController.java`.

### 1.1 Pontos fortes a preservar
- **Banner de "Ação necessária"** (`lista.html:26-35`) — informação crítica
  duplicada de forma impossível de ignorar. Padrão correto, manter e replicar.
- **Cards empilhados em mobile** (`lista.html:158-195`) no lugar de tabela
  espremida, com o card inteiro clicável.
- **Timeline do pedido** (`detalhe.html:40-151`) reaproveitando o componente
  `.timeline` do design system — dá ao solicitante a noção de "onde meu pedido
  está" sem jargão interno.
- **Chat com poll AJAX + notificação sonora/toast** — resolve a dúvida "alguém
  leu o que eu mandei?" sem o usuário precisar recarregar.
- **`podeCancelar` como fonte única** (servidor decide se o botão aparece) —
  nunca oferece uma ação que o serviço vai recusar.
- **Previsão de prazo baseada em histórico** (`detalhe.html:120-125`), com a
  ressalva honesta "não é um prazo formal".

### 1.2 Problemas concretos

**A) `detalhe.html` — a mesma informação dita três vezes, com regras duplicadas**
A tela responde "qual o status?" em três lugares independentes: o badge do
cabeçalho (`detalhe.html:11-28`), o passo final da timeline (`:65-148`) e **oito
blocos `alert` condicionais empilhados** (`:153-274`). A condição
`status == 'CONVERTIDA' and processoGerado != null and ...status.isFinalizado()`
aparece literalmente ~10 vezes ao longo do arquivo, cada vez reescrita à mão.
Consequências: (i) para o usuário, um pedido deferido gera badge "Aprovada" +
timeline "Deferido" + alerta verde grande dizendo a mesma coisa em três tons
diferentes; (ii) para manutenção, qualquer regra nova precisa ser replicada em
três lugares, e já existe assimetria de vocabulário entre eles
("Aprovada" no badge × "Deferido" na timeline × "Pedido aprovado!" no alerta),
o que confunde: são a mesma coisa, mas o usuário não sabe disso.

**B) `detalhe.html:231-248` — a ação mais urgente da tela está no meio da página**
Quando um avaliador pede informação complementar, o pedido **pausa o processo
inteiro**. O formulário para o solicitante responder está dentro de um `alert`
amarelo posicionado depois da timeline, no meio de outros sete alertas. Em celular
isso fica bem abaixo da dobra. A tela deveria abrir com um cartão de ação
inconfundível no topo.

**C) `detalhe.html:338-358` — ação destrutiva com o mesmo peso de "Voltar"**
"Cancelar processo" encerra uma análise em curso com 3 médicos e **não tem como
desfazer**. Hoje é um `btn-outline-danger` do mesmo tamanho de "Voltar", lado a
lado, protegido apenas por um modal genérico (`data-confirm-msg`) com um texto
longo. Compare com o cuidado dado ao voto do avaliador (modal dedicado + checkbox
de ciência + botão que só habilita depois). A assimetria de proteção não se
justifica: as duas ações são igualmente irreversíveis.

**D) `nova.html` — formulário que produz devoluções**
- `nova.html:53-56`: "Justificativa clínica" é um `textarea` sem nenhuma
  orientação sobre o que a equipe de Urgência Renal espera encontrar, sem
  contador de caracteres e sem exemplo. Cada solicitação incompleta vira uma
  devolução ou um pedido de informação complementar — retrabalho para os dois
  lados e dias perdidos num processo de **urgência**. Este é, disparado, o item
  de maior retorno do relatório inteiro.
- `nova.html:60-67`: upload múltiplo sem limite de tamanho informado, sem barra
  de progresso e **sem como remover um arquivo escolhido por engano** (o input
  `multiple` substitui a seleção inteira a cada clique). Em conexão de hospital,
  um upload grande que falha no fim, sem feedback, é a pior experiência possível.
- `nova.html:46-48`: rótulo "Data de solicitação da urgência renal" é ambíguo
  (é a data de hoje? a data em que a equipe indicou?) e o campo não tem `max`,
  aceitando data futura no navegador.
- `nova.html:38-40`: "RGCT / SNT" com placeholder `123456789-12345` e nenhuma
  explicação de onde encontrar o número.
- `nova.html:69-74`: "Cancelar" e "Enviar solicitação" adjacentes, mesmo tamanho;
  "Cancelar" descarta todo o preenchimento **sem confirmar**.
- O formulário **não usa `data-lock-submit`** (o projeto já tem
  `static/js/lock-submit.js` e o usa em `processos/detalhe.html`). Com upload
  lento, um duplo clique cria duas solicitações do mesmo paciente.

**E) `lista.html` — cores que mentem e números que não fazem nada**
- `lista.html:73-82`: o card "Decididas" usa `stat-card-deferido` (verde) mesmo
  agregando pedidos **indeferidos**. Verde para um pedido negado é enganoso.
- `lista.html:83-93`: "Devolvidas" usa `stat-card-indeferido` (vermelho de
  indeferimento). Devolvida ≠ indeferida — são conceitos diferentes do domínio
  pintados com a mesma cor.
- Os 5 cards são decorativos: não filtram a lista. Um solicitante com 20 pedidos
  não tem como ver "só os em análise", nem buscar por nome/RGCT.
- `lista.html:40-93`: cada card repete **seis** declarações `style="..."` inline,
  cinco vezes seguidas, duplicando o que as classes `.stat-card-*` do
  `app.css:710-716` já definem via variáveis. Dívida pura.
- A tabela desktop não mostra o **número do processo** (`NN/AAAA`) depois da
  conversão — justamente a chave que o solicitante usa para falar com a equipe
  por telefone/e-mail. Só aparece dentro do detalhe.

**F) Documentos anexados sem contexto** (`detalhe.html:293-303`)
Lista os nomes de arquivo crus, sem tamanho, sem data de envio, sem ícone por
tipo; e o ícone de download (`:298`) fica **fora** do `<a>`, portanto não é
clicável — alvo de toque reduzido em celular.

---

## 2. Diagnóstico — Portal do Avaliador

Arquivos: `src/main/resources/templates/avaliador/lista.html` (374 linhas),
`votar.html` (215); `static/js/avaliador-votar.js`,
`avaliador-pdf-fullscreen.js`; `web/AvaliadorController.java`.

### 2.1 Pontos fortes a preservar
- **`votar.html` split-pane** (`:20-77`): PDF à esquerda, contexto + voto à
  direita em `sticky-lg-top`. Em desktop é excelente — o médico lê e vota sem
  rolar entre as duas coisas.
- **Modal de confirmação do voto** (`votar.html:169-208` + `avaliador-votar.js`):
  repete a escolha feita, exige checkbox de ciência, desabilita contra duplo
  clique e **cai para `confirm()` nativo** se o Bootstrap falhar. É o padrão de
  referência do projeto para ação irreversível — replicar, não mexer.
- **Partição atrasados × demais no servidor** (`AvaliadorController:181-190`),
  com aviso pré-atentivo (cor **+ ícone**, não só cor) e a justificativa do bug
  que ela evita documentada no template (`lista.html:108-116`).
- **Aviso explícito sobre iniciais** (`lista.html:97-102`): explica *por que* o
  paciente aparece anonimizado. Reduz a suspeita de "o sistema está com defeito".
- **DTOs projetados** (`ParecerPendenteView`/`ProcessoVotoView`): a entidade
  `Processo` nunca chega ao template. Qualquer melhoria proposta deve continuar
  passando por esses records.

### 2.2 Problemas concretos

**A) `lista.html` — hierarquia invertida: 7 blocos antes da primeira ação**
Ordem atual da página: 4 cards de estatística pessoal (`:19-80`) → alerta "você
tem N processos aguardando" (`:83-91`) → alerta de estado vazio/sucesso (`:92-95`)
→ alerta explicativo sobre iniciais (`:97-102`) → título "Pendentes de voto"
(`:104`) → só então a lista com o botão "Ver e votar".
O avaliador entra no portal por **um motivo só**: votar. Estatísticas pessoais
("Avaliados por mim", "Atribuídos a mim", distribuição dos meus votos) são
interesse secundário e ocupam a área nobre. Além disso o card "Pendentes"
(`:20-28`) e o alerta (`:83-91`) dizem exatamente o mesmo número duas vezes.
Em celular, isso é uma tela inteira de rolagem antes de qualquer coisa acionável.

**B) `lista.html:167` e `:265` — ação principal no canto morto**
Em desktop, "Ver e votar" é a **última coluna à direita** de uma tabela de 6
colunas. `docs/ESTUDO-UI-COMPORTAMENTAL.md` (princípio #1, padrão de leitura F)
já registra exatamente essa crítica para outra tela. Nas versões mobile
(`:194-208`, `:290-305`) o botão já está corretamente em destaque — a
inconsistência é só no desktop.

**C) `lista.html:317-368` — histórico sem versão mobile**
Toda a tela adota o padrão "tabela ≥768px / cards <768px" (duas vezes), mas o
"Histórico das minhas avaliações" é uma tabela única sem par `d-md-none`. Em
celular vira scroll horizontal, quebrando o padrão da própria página.

**D) `lista.html:63-76` — legenda que só existe como tooltip**
Os três totais do mini-gráfico são identificados por `title="Favoravel"`,
`title="Nao favoravel"`, `title="Solicita informacao"`. **`title` não existe em
touch** — no celular o médico vê três ícones e três números sem rótulo. O
`aria-label` do gráfico (`:56`) está correto; falta o rótulo visível.

**E) `lista.html:148` — o prazo-meta é invisível**
"Atrasados (acima do prazo-meta)" nunca diz **qual é** o prazo. O valor
(`prazoDias`) já está no model (`AvaliadorController:225`) e só aparece dentro
de um `title`. O avaliador não tem como saber que a meta são 7 dias.

**F) `votar.html` em celular — o voto fica soterrado pelo PDF**
Abaixo de 992px as colunas empilham: PDF (60vh, `app.css:845-850`) primeiro,
formulário de voto depois. Não há âncora, botão flutuante nem link "ir para o
voto". O médico que já leu o material no celular precisa rolar o bloco inteiro
para votar.

**G) `votar.html:114-128` — alvo de toque pequeno para a ação mais importante**
Os três resultados são `form-check` padrão: o alvo real de toque é o círculo do
rádio (~16px) mais a linha de texto. WCAG 2.5.5 recomenda 44×44px. Para uma
decisão **irreversível**, tocar por engano em "Não favorável" no lugar de
"Favorável" é o pior erro possível do sistema — e hoje só o modal de confirmação
segura isso.

**H) `votar.html:115` — "Solicita informação" sem dizer o que faz**
As três opções aparecem como rótulos secos. "Solicita informação" **pausa o
processo inteiro** (bloqueia a decisão até o solicitante responder) e reabre o
parecer depois. O avaliador que escolhe isso achando que é "tenho uma dúvida
menor" está tomando uma decisão de fluxo sem saber. Cada opção precisa de uma
frase explicando a consequência.

**I) `votar.html:106-110` vs `:183-190` — aviso de irreversibilidade duplicado**
O mesmo texto ("registrado com sua identidade, data/hora e IP; não pode ser
desfeito") aparece em destaque no formulário **e** no modal. Repetir um aviso
grave duas vezes o enfraquece: o usuário aprende a pular o bloco amarelo. O lugar
onde ele importa é o modal, no instante da confirmação.

**J) `votar.html:130-137` — justificativa opcional para voto negativo**
A justificativa é o insumo que o operador usa para redigir o **ofício de
indeferimento** e o pedido de informação complementar. Hoje é opcional para os
três resultados. Tornar obrigatória para `NAO_FAVORAVEL` e `SOLICITA_INFORMACAO`
é uma **decisão de produto** (muda regra de negócio, não é ajuste de UI) —
registrada aqui na Fase 9 para o usuário aprovar, **não implementar por conta**.

**K) Fluxo em lote inexistente**
Após votar, o médico volta para a lista. Não há "processo 1 de 3" nem "próximo
pendente". Quem tem 3 pareceres atrasados faz três voltas completas pela lista.

**L) Não recomendado (registro explícito)**
Mostrar ao avaliador quantos votos o processo já tem, ou que o voto dele é o
desempate, **quebraria a imparcialidade** que é a razão de existir da
anonimização. Fica registrado como ideia **descartada por regra de domínio**,
para que ninguém a proponha de novo achando que é uma melhoria de UX.

---

## 3. Diagnóstico — card "Respostas dos Avaliadores" (operador)

Arquivo: `src/main/resources/templates/processos/detalhe.html:578-692`;
CSS `.tabela-pareceres` em `app.css:176-245` e `:679-708`.

### 3.1 Pontos fortes a preservar
- O texto explicativo (`:593-600`) diz de onde vem o parecer e qual é a regra de
  maioria — bom onboarding embutido.
- "Sem e-mail cadastrado" (`:647-651`) explica **por que** o botão de lembrete
  não aparece, em vez de sumir silenciosamente. Padrão excelente.
- "Dispensado pela maioria" (`:616-620`) para pareceres pendentes em processo já
  decidido — evita a leitura errada de "faltou gente votar".
- Justificativa do avaliador exibida ao operador com aviso de que é material
  interno (`:654-665`).

### 3.2 Problemas concretos

**A) A informação mais importante do card é a menos visível**
A coluna "Parecer" (`:621-623`) renderiza `par.resultado.descricao` como texto
`small` cinza. No **mesmo sistema**, o histórico do avaliador
(`avaliador/lista.html:340-349`) mostra o mesmo dado com badge colorido + ícone
(verde/vermelho/azul). O operador — que olha esse card dezenas de vezes por dia —
tem a versão pior.

**B) Não existe placar**
O header mostra só `${favoraveis} + ' favoravel(is)'` (`:589`). Para saber se
falta 1 voto para fechar maioria, o operador lê a tabela linha a linha. O card
deveria abrir com um placar de três posições (favorável / não favorável /
pendente) e uma frase do tipo "faltam N votos para a maioria" ou "maioria já
formada".

**C) O operador decide "lembrar" sem a informação que motiva o lembrete**
Não há "há quantos dias este avaliador está com o processo" nem marcação de fora
do prazo-meta — **dado que já existe** (`TempoRespostaService`,
`app.avaliador.prazo-dias`, já usado no painel do avaliador e em `/membros`).
Também não há registro de quando o último lembrete foi enviado, então nada impede
o operador de disparar e-mail para o mesmo médico três vezes no mesmo dia.

**D) `detalhe.html:657` — `colspan="6"` numa tabela de 4 colunas**
O cabeçalho tem 4 `<th>` (`:604-609`); a linha da justificativa usa
`colspan="6"`. Bug de HTML real. No mesmo espírito, `app.css:215-231` e
`:812-829` ainda dimensionam colunas `nth-child(5)` e `nth-child(6)` que não
existem mais — resíduo do layout antigo de 6 colunas.

**E) Coluna "Ação" acumulando legado do modelo híbrido**
`:629-633` exibe "Voto no portal" como diferenciador — mas **hoje todo voto é do
portal** (`OrigemParecer` só tem `AVALIADOR_SISTEMA` desde o commit `041dc43`).
A coluna mistura três estados textuais diferentes (`Voto no portal`, `—`,
`Sem e-mail cadastrado`) com um botão. Antes de simplificar, o executor **deve
conferir no controller** o que ainda alimenta `pareceresPortal` — a limpeza é
segura, mas não deve ser feita às cegas.

**F) Perde-se a hora do voto**
`:626` mostra só `dataResposta` formatada como `dd/MM/yyyy`, enquanto o portal
grava `dataHoraVoto` com hora. Em processo de urgência, a hora importa.

---

## 4. Problemas transversais

1. **Acentuação ausente nos dois portais.** `layout.html` usa acentos corretos
   ("Sistema de avaliação de urgência renal"), mas praticamente todo o texto de
   `solicitante/*` e `avaliador/*` está sem acento ("Minhas solicitacoes",
   "Justificativa clinica", "Nao favoravel"). São as telas vistas por **público
   externo** — equipes de hospitais e médicos — num sistema de Secretaria de
   Saúde. Custo de credibilidade alto, risco técnico baixo, mas com armadilha de
   testes (ver §6).
2. **Alvos de toque.** `btn-sm` em ações primárias de mobile (avaliador
   `lista.html:204-207`, solicitante `detalhe.html:181-190`) e o ícone de
   download fora do link (`solicitante/detalhe.html:298`).
3. **Estilos inline espalhados** — `style="max-width: 980px"`
   (`solicitante/lista.html:8`, `avaliador/lista.html:8`),
   `style="max-height:250px;overflow-y:auto;"` (`solicitante/detalhe.html:324`),
   os 5 blocos de stat-card. Deveriam virar classes utilitárias no `app.css`.
4. **Sem landmarks/skip-link.** Nenhum template usa `<main>`; não há link "pular
   para o conteúdo"; o bloco de flash não é `aria-live`.
5. **Sem feedback de carregamento em upload** em nenhum dos dois portais.

---

## 5. Plano de execução em fases

Ordenado por **impacto ÷ risco**. Cada fase é independente e pode virar um commit
(ou PR) próprio. Nenhuma fase depende de fase posterior.

> **Instrução geral para o executor (Sonnet):** ao final de **cada** fase, rodar
> `.\test.ps1` (JDK 21). Fases marcadas com ⚠ mexem em algo coberto por teste ou
> por Page Object do E2E — nessas, além da suíte, rodar `.\e2e.ps1 -Headless`.
> Nunca introduzir ícone `bi-*` sem conferir que existe (o `IconesBootstrapTest`
> quebra), nem botão só-ícone sem `title`/`aria-label` (`AcessibilidadeBotaoIconeTest`).

---

### FASE 1 — Higiene e correções factuais (risco ~zero, ganho imediato)

**Objetivo:** corrigir erros objetivos e cores que mentem, sem mexer em estrutura.

| # | O quê | Onde | Por quê |
|---|---|---|---|
|1.1| Trocar `colspan="6"` por `colspan="4"` | `processos/detalhe.html:657` | A tabela tem 4 colunas; HTML inválido |
|1.2| Remover as regras de `.tabela-pareceres` para `nth-child(5)` e `nth-child(6)` | `app.css:215-231`, `:812-829` | Colunas não existem mais; CSS morto que atrapalha o próximo ajuste |
|1.3| Card "Decididas" deixa de usar `stat-card-deferido`; usar `stat-card-total` (neutro) | `solicitante/lista.html:73-82` | Agrupa deferidos **e** indeferidos; verde é enganoso |
|1.4| Card "Devolvidas" deixa de usar `stat-card-indeferido`; usar `stat-card-andamento` (âmbar = precisa de ação) | `solicitante/lista.html:83-93` | Devolvida ≠ indeferida; âmbar comunica "requer ação sua", que é o caso |
|1.5| Mover o `<i class="bi bi-download">` para **dentro** do `<a>` | `solicitante/detalhe.html:295-299` | Ícone fora do link não é clicável; alvo de toque maior |
|1.6| Substituir os 5 blocos de `style="..."` inline por uma classe `.stat-card-portal` no `app.css` que aplique `background/border/border-radius/box-shadow` a partir das variáveis `--stat-*` já existentes | `solicitante/lista.html:38-94`, `app.css` (nova regra perto de `:608-716`) | 30 declarações inline duplicadas; impede ajuste global |
|1.7| Adicionar rótulo textual visível (`Fav.` / `Não fav.` / `Info`) ao lado de cada número do mini-gráfico, mantendo os `title` | `avaliador/lista.html:63-76` | `title` não existe em touch |
|1.8| Exibir o prazo-meta em texto no cabeçalho do bloco "Atrasados": "Atrasados (acima do prazo-meta de N dias)", usando `${prazoDias}` já no model | `avaliador/lista.html:117-120` | O avaliador não sabe qual é a meta |
|1.9| Mostrar `dataHoraVoto` (com hora) quando existir, caindo para `dataResposta` quando não — mesmo padrão de `avaliador/lista.html:352-356` | `processos/detalhe.html:625-626` | Perde-se a hora do voto num processo de urgência |

**Testes:** baixo risco. Rodar `.\test.ps1`.

---

### FASE 2 — Voto no celular: a tela mais crítica do sistema ⚠

**Objetivo:** tornar o voto seguro e confortável no celular, sem tocar em
nenhuma regra.

| # | O quê | Onde | Por quê |
|---|---|---|---|
|2.1| Transformar cada opção de resultado em um **cartão selecionável grande** (mínimo 44px de altura, largura total, borda que muda de cor quando `:checked` via `:has()` ou classe no label), mantendo `<input type="radio">` **visível e com o mesmo `th:id="'resultado_' + r.name()"`** | `avaliador/votar.html:114-128` + regra nova em `app.css` | Alvo de toque atual ~16px para a ação irreversível do sistema |
|2.2| Adicionar sob cada opção uma frase de consequência: Favorável → "Concordo com o reconhecimento da urgência"; Não favorável → "Não reconheço a urgência para este caso"; Solicita informação → "**Pausa o processo** até o solicitante enviar mais dados" | mesmo bloco | O avaliador escolhe "Solicita informação" sem saber que pausa o fluxo |
|2.3| Adicionar botão âncora "Ir para o voto ↓" visível **apenas abaixo de 992px**, no topo do card de material, apontando para o card do formulário | `avaliador/votar.html:25-33` (topo da col-lg-7) | Em celular o formulário fica soterrado sob 60vh de PDF |
|2.4| Reduzir o alerta de irreversibilidade do formulário a uma linha discreta (`small text-muted` com ícone de cadeado), mantendo o alerta forte **apenas no modal** | `avaliador/votar.html:106-110` (o modal `:183-190` não muda) | Aviso grave repetido duas vezes perde força |
|2.5| Trocar o rótulo "Parecer *" por "Qual é o seu parecer? *" | `avaliador/votar.html:115` | Pergunta direta > rótulo abstrato para usuário esporádico |

**⚠ Risco de teste (obrigatório ler antes de codar):**
`src/test/java/br/gov/saude/sgpur/e2e/pages/AvaliadorPage.java:61` faz
`page.locator("#resultado_" + resultado).check()` e `:65` clica no botão pelo
nome acessível **"Registrar meu voto"**. Portanto:
- **NÃO** usar o padrão `btn-check` do Bootstrap (que esconde o input com
  `clip`/`position:absolute`) — o Playwright falha ao `.check()` um elemento não
  acionável. Manter o rádio real visível dentro do cartão.
- **NÃO** renomear o botão "Registrar meu voto".
- Manter os ids `resultado_FAVORAVEL`, `resultado_NAO_FAVORAVEL`,
  `resultado_SOLICITA_INFORMACAO`, o `name="resultado"`, o
  `textarea[name=justificativa]`, `#modalConfirmarVoto`, `#checkConfirmaVoto` e
  `#btnConfirmarVotoFinal`.
Rodar `.\test.ps1` **e** `.\e2e.ps1 -Headless`.

---

### FASE 3 — Painel do avaliador: colocar a ação na frente

**Objetivo:** o avaliador abre o portal e a primeira coisa na tela é o que ele
tem para votar.

| # | O quê | Onde | Por quê |
|---|---|---|---|
|3.1| Reordenar a página para: (1) saudação + chamada única de pendências, (2) lista "Atrasados", (3) lista "Demais pendentes", (4) histórico, (5) **por último** os cards de estatística pessoal | `avaliador/lista.html` (mover blocos `:19-80` para depois de `:368`) | 7 blocos antes da primeira ação; hierarquia invertida |
|3.2| Fundir o card "Pendentes (a votar)" (`:20-28`) com o alerta "Você tem N processos aguardando" (`:83-91`) num **único** bloco de destaque, contendo o número e um botão "Começar a votar" que rola até a primeira pendência | `avaliador/lista.html:19-95` | Mesmo número dito duas vezes em dois formatos |
|3.3| Rebaixar o alerta explicativo sobre iniciais (`:97-102`) para um `small text-muted` logo abaixo do título da lista | `avaliador/lista.html:97-102` | Informação importante, mas não merece um `alert` cheio acima da ação |
|3.4| Em desktop, mover a coluna de ação ("Ver e votar") das tabelas para a **primeira** coluna, ou promover o número do processo a link clicável para a tela de voto (mantendo o botão à direita) | `avaliador/lista.html:135-171` e `:233-269` | Ação principal fora da zona de leitura F (ver `docs/ESTUDO-UI-COMPORTAMENTAL.md` #1) |
|3.5| Criar versão mobile em cards para "Histórico das minhas avaliações", seguindo exatamente o padrão `d-none d-md-block` / `d-md-none` já usado nas duas listas de pendentes | `avaliador/lista.html:317-368` | Única tabela da tela sem versão mobile |

**Testes:** `AvaliadorControllerTest` verifica model attributes, não a ordem do
HTML — risco baixo, mas rodar `.\test.ps1`. Nenhum atributo novo de model é
necessário nesta fase.

---

### FASE 4 — Card "Respostas dos Avaliadores": placar de verdade ⚠

**Objetivo:** o operador bate o olho e sabe em quantos votos está e o que fazer.

| # | O quê | Onde | Por quê |
|---|---|---|---|
|4.1| Substituir o texto cru do resultado por **badge com ícone e cor**, reutilizando exatamente o mapeamento de `avaliador/lista.html:340-349` (verde/polegar-cima, vermelho/polegar-baixo, azul/interrogação) | `processos/detalhe.html:621-623` | Dado mais importante do card é o menos visível; inconsistente com o resto do sistema |
|4.2| Substituir o badge solitário `${favoraveis} favoravel(is)` por um **placar de 3 posições** (favoráveis / não favoráveis / pendentes) + uma frase de estado ("maioria formada" ou "faltam N votos"), calculada **no controller** e passada pronta ao template | `processos/detalhe.html:589` + `ProcessoDetalheController` | Hoje é preciso ler a tabela linha a linha para saber se falta 1 voto |
|4.3| Adicionar coluna (ou linha secundária) "Aguardando há N dias", com destaque vermelho + ícone quando acima do prazo-meta — reutilizando `app.avaliador.prazo-dias` e o mesmo cálculo de `AvaliadorController:169-172` | `processos/detalhe.html:611-652` + controller | O operador decide enviar lembrete sem o dado que justifica o lembrete |
|4.4| Investigar no controller o que ainda alimenta `pareceresPortal`; **se** (e somente se) ele for sempre verdadeiro para todo parecer votado, remover o texto "Voto no portal" e simplificar a coluna Ação para conter apenas o botão de lembrete ou o motivo do bloqueio | `processos/detalhe.html:627-651` | Vestígio do modelo híbrido encerrado em 2026-07-27 |
|4.5| No botão "Lembrar pendentes", incluir a contagem ("Lembrar 2 pendentes") e desabilitá-lo quando não houver pendentes | `processos/detalhe.html:583-588` | Evita disparo redundante de e-mail a médico |

**⚠ Risco:** 4.2 e 4.3 adicionam atributos ao model —
`ProcessoDetalheControllerTest` e `ProcessoDetalheSemTransacaoIntegrationTest`
podem precisar de ajuste. `ProcessoDetalhePage.java` (E2E) navega essa tela.
Rodar `.\test.ps1` e `.\e2e.ps1 -Headless`.
**Não alterar** nenhuma regra de decisão: o placar é apenas apresentação do que
`ProcessoValidator`/`ProcessoService` já calculam — se precisar de um número novo,
derivá-lo dos pareceres, nunca reimplementar a regra de maioria no template.

---

### FASE 5 — Nova solicitação: reduzir devolução na origem ⚠

**Objetivo:** cada solicitação chega completa da primeira vez.

| # | O quê | Onde | Por quê |
|---|---|---|---|
|5.1| Adicionar acima do `textarea` de justificativa um bloco "O que a equipe precisa saber" com 4-5 marcadores (ex.: quadro clínico atual, exames que sustentam a urgência, tempo em diálise/intercorrências, tratamentos já tentados, motivo da urgência **agora**) — **o conteúdo exato deve ser confirmado com o usuário/equipe da Urgência Renal antes de publicar** | `solicitante/nova.html:52-56` | Maior fonte de devolução e de pedido de informação complementar |
|5.2| Adicionar contador de caracteres ao vivo no `textarea` (JS em `solicitante-nova.js`), com sinal visual discreto quando o texto for muito curto | `nova.html:54` + `solicitante-nova.js` | Texto de 2 linhas quase sempre volta como incompleto |
|5.3| Adicionar `data-lock-submit="Enviando solicitação..."` ao formulário (o projeto já tem `lock-submit.js`) | `nova.html:12` e `:71-73` | Duplo clique com upload lento cria duas solicitações do mesmo paciente |
|5.4| Evoluir `solicitante-nova.js` para listar cada arquivo selecionado como uma linha com nome + tamanho formatado + botão "remover" (usando um `DataTransfer` para reescrever `input.files`) e exibir o limite de tamanho aceito | `nova.html:59-67` + `solicitante-nova.js` | Hoje não há como remover um arquivo escolhido por engano nem saber o limite |
|5.5| Adicionar `max` = data de hoje no campo de data e melhorar o rótulo/`form-text` explicando de que data se trata | `nova.html:45-49` | Aceita data futura; rótulo ambíguo |
|5.6| Adicionar `form-text` sob RGCT/SNT explicando o que é e onde encontrar | `nova.html:37-40` | Público leigo no jargão do sistema |
|5.7| Adicionar `data-confirm-msg` ao botão "Cancelar" ("Descartar o preenchimento?") e separá-lo visualmente do "Enviar" (`me-auto`/link discreto à esquerda, botão primário à direita) | `nova.html:69-74` | Descarta o formulário inteiro sem confirmar, adjacente ao envio |

**⚠ Risco alto de teste (ler antes de codar):**
- `SolicitacaoOnlineCamposIntegrationTest` e o utilitário
  `src/test/java/br/gov/saude/sgpur/support/CamposDeFormulario.java` derivam a
  lista de campos **do próprio HTML** via regex `th:field="*{...}"` e
  `name="..."`. **Adicionar um `name="..."` novo neste formulário pode quebrar
  esses testes.** Se for necessário um input auxiliar de UI, ele **não deve ter
  atributo `name`** (usar `id`/`data-*`).
- `PortalSolicitantePage.java:38-48` (E2E) usa
  `input[name=pacienteNome]`, `input[name=pacienteRgct]`,
  `input[name=dataSituacaoEspecial]`, `textarea[name=justificativaClinica]` e o
  botão pelo nome **"Enviar solicitacao"** (sem acento). **Não renomear o botão
  nesta fase** — isso é tratado na Fase 8.
Rodar `.\test.ps1` **e** `.\e2e.ps1 -Headless`.

---

### FASE 6 — Detalhe do solicitante: uma resposta, não oito ⚠⚠

**Objetivo:** a tela responde imediatamente "em que pé está meu pedido e o que
eu preciso fazer", em um bloco só.

| # | O quê | Onde | Por quê |
|---|---|---|---|
|6.1| Criar no `SolicitanteController` um record de view (ex.: `SituacaoPedidoView`) com campos prontos: `rotulo`, `classeCor`, `icone`, `titulo`, `mensagem`, `precisaAcao` (boolean), `anexoParaBaixar` (id + rótulo, ou null), `numeroProcesso`. Toda a decisão de status passa a ser feita em Java, uma vez | `SolicitanteController.detalhe` (~`:199-247`) | Hoje a regra de status está reescrita ~10 vezes no template |
|6.2| Substituir os 8 blocos `alert` por **um único** "cartão de situação" alimentado por esse record, posicionado **logo abaixo do título**, acima da timeline | `solicitante/detalhe.html:153-274` | Empilhamento de alertas + informação repetida em 3 formatos |
|6.3| Quando `precisaAcao` for verdadeiro (informação complementar pedida), esse cartão contém o formulário de upload — no topo da página, não no meio | `solicitante/detalhe.html:231-248` | Ação mais urgente do fluxo fica abaixo da dobra no celular |
|6.4| Unificar o vocabulário nas três exibições: escolher **um** par de termos (sugestão: "Deferido"/"Indeferido", que é o vocabulário oficial do ofício) e usá-lo no badge, na timeline e no cartão | `solicitante/detalhe.html:11-28`, `:83-99`, cartão novo | "Aprovada" × "Deferido" × "Pedido aprovado!" na mesma tela |
|6.5| Manter a timeline como **resumo de progresso**, tirando dela o texto longo de resultado (que passa a viver só no cartão) | `solicitante/detalhe.html:101-147` | Timeline vira leitura rápida em vez de terceiro parágrafo |
|6.6| Promover o botão de download (comprovante SNT / ofício) a ação primária do cartão, e não a um link pequeno dentro de um alerta | `solicitante/detalhe.html:181-190`, `:214-223` | É o que o solicitante veio buscar quando o processo está decidido |
|6.7| Dar ao "Cancelar processo" a mesma proteção do voto: modal dedicado com checkbox de ciência quando o pedido **já virou processo em análise** (reaproveitar o padrão de `avaliador-votar.js` num JS próprio) — manter o modal simples quando ainda for `ENVIADA` | `solicitante/detalhe.html:346-357` | Ação irreversível com proteção menor do que a de ações equivalentes |
|6.8| Exibir número do processo (`NN/AAAA`) ao lado do nome do paciente no `<h1>` quando já convertido | `solicitante/detalhe.html:10` | É a chave que a equipe usa ao telefone |

**⚠⚠ Fase de maior risco do plano.** Mexe em controller e em toda a árvore
condicional da tela. `SolicitanteControllerTest`,
`SolicitanteControllerSemTransacaoIntegrationTest`,
`SolicitacaoOnlineDetalheIntegrationTest`,
`SolicitanteCancelamentoTransacaoIntegrationTest` e
`SolicitanteInformacaoComplementarIntegrationTest` cobrem essa rota.
**Regras que não podem mudar:** `podeCancelar` continua sendo a fonte única (o
template nunca recalcula a condição); o endpoint de download continua com a
whitelist de `TipoAnexo` (só `COMPROVANTE_SNT` e `OFICIO_INDEFERIMENTO`) e a
checagem de posse; o chat e seus 3 endpoints AJAX não são tocados; manter
`chatAtivoNestaTela=true` no model (senão o poll global duplica a notificação).
Executar esta fase **sozinha**, num commit só dela. Rodar `.\test.ps1` e
`.\e2e.ps1 -Headless`.

---

### FASE 7 — Lista do solicitante: encontrar e filtrar

| # | O quê | Onde | Por quê |
|---|---|---|---|
|7.1| Tornar os cards de resumo **clicáveis**, filtrando a lista por status (filtro no servidor via query param, ou filtro client-side por `data-status` nas linhas/cards — preferir client-side para não mexer no controller) | `solicitante/lista.html:38-94` | Números decorativos; sem forma de ver "só os em análise" |
|7.2| Adicionar campo de busca por nome do paciente / RGCT que filtra a lista ao digitar (JS novo em `static/js/solicitante-lista.js`) | `solicitante/lista.html` (acima da tabela) | Lista cresce indefinidamente, sem busca |
|7.3| Exibir o número do processo na tabela desktop e no card mobile quando a solicitação já foi convertida | `solicitante/lista.html:110-143` e `:165-194` | Chave de referência só existe dentro do detalhe |
|7.4| Adicionar chevron (`bi-chevron-right`) no card mobile indicando que é clicável | `solicitante/lista.html:165-194` | Card inteiro é link, mas nada sinaliza isso |
|7.5| Enriquecer a lista de documentos anexados com ícone por extensão e data de envio | `solicitante/detalhe.html:293-303` | Nomes de arquivo crus, sem contexto |

**Testes:** `SolicitanteControllerTest` (model attributes). Se 7.1 for feito
client-side, o controller não muda. Rodar `.\test.ps1`.

---

### FASE 8 — Acentuação, microcopy e tom ⚠

**Objetivo:** o texto dos dois portais externos ficar à altura de um sistema de
Secretaria de Saúde.

| # | O quê | Onde | Por quê |
|---|---|---|---|
|8.1| Corrigir a acentuação de **todo** texto visível em `templates/solicitante/*.html` e `templates/avaliador/*.html` (títulos, rótulos, botões, alertas, `title`, `aria-label`, placeholders) | os 6 arquivos dos dois portais | Telas de público externo escritas sem acento; `layout.html` já usa acentos, então o suporte a UTF-8 está provado |
|8.2| Revisar o microcopy para tom de serviço público direto: preferir 2ª pessoa ("Você não precisa fazer nada agora") e frases curtas; eliminar jargão interno ("triagem", "convertida em processo") ou explicá-lo entre parênteses na primeira ocorrência | mesmos arquivos | Público leigo em jargão administrativo |
|8.3| Padronizar os rótulos de status entre lista e detalhe do solicitante (mesmo vocabulário decidido em 6.4) | `solicitante/lista.html`, `detalhe.html` | Termos diferentes para o mesmo estado em telas vizinhas |

**⚠ Risco de teste (crítico nesta fase):** os Page Objects do E2E localizam
botões **pelo texto sem acento**:
`PortalSolicitantePage.java:48` → `"Enviar solicitacao"`;
`AvaliadorPage.java:65` → `"Registrar meu voto"` (esse não tem acento, mas
confira todos). Antes de mudar qualquer rótulo de botão, **grepar
`src/test/java` pela string exata** e atualizar o Page Object no mesmo commit.
Verificar também asserções `containsString(...)` nos testes MockMvc dos dois
portais. Rodar `.\test.ps1` **e** `.\e2e.ps1 -Headless`.

---

### FASE 9 — Acessibilidade estrutural e design system

| # | O quê | Onde | Por quê |
|---|---|---|---|
|9.1| Envolver o conteúdo em `<main id="conteudo">` e adicionar skip-link "Pular para o conteúdo" no fragment `navbar` | `layout.html` + os templates dos dois portais | Nenhum landmark hoje; navegação por leitor de tela é sequencial |
|9.2| Tornar o bloco de flash um `aria-live="polite"` | `layout.html` (fragment `flash`) | Mensagem de sucesso/erro não é anunciada |
|9.3| Criar utilitárias no `app.css` (`.container-portal` para o `max-width: 980px`, `.chat-box` para o `max-height:250px;overflow-y:auto`) e substituir os `style` inline | `solicitante/lista.html:8`, `avaliador/lista.html:8`, `solicitante/detalhe.html:324` | Inline espalhado; impede ajuste responsivo global |
|9.4| Revisar alvos de toque: promover a `btn` (não `btn-sm`) as ações primárias das versões mobile | `avaliador/lista.html:204-207`, `:300-303`; `solicitante/detalhe.html` | WCAG 2.5.5 (44×44px) |
|9.5| Conferir contraste dos badges âmbar sobre fundo claro usando `--rs-gold-dark` (que existe exatamente para isso) em vez de `text-dark` genérico | `solicitante/lista.html:124`, `:181`; `avaliador/lista.html` | O design system já tem a variável AA; os templates não a usam |

**Testes:** `AcessibilidadeBotaoIconeTest` e `IconesBootstrapTest` varrem os
templates — favoráveis a esta fase, mas rodar `.\test.ps1`.

---

### FASE 10 — Fluxo em lote do avaliador (melhoria de produtividade)

| # | O quê | Onde | Por quê |
|---|---|---|---|
|10.1| Exibir "Processo X de N pendentes" no cabeçalho da tela de voto | `avaliador/votar.html:10-18` + `AvaliadorController.votar` | Dá noção de progresso a quem tem vários pendentes |
|10.2| Após registrar o voto, se ainda houver pendências, o flash de sucesso oferecer "Ir para o próximo pendente" | `AvaliadorController.registrarVoto` + template de destino | Hoje o médico volta à lista e recomeça a busca a cada voto |

**Cuidado:** `X de N` e "próximo pendente" só podem usar dados **do próprio
membro logado** (pendências dele), nunca informação sobre o processo em si.
Rodar `.\test.ps1`.

---

### FASE 11 — Decisões de produto (NÃO implementar sem aval do usuário)

Itens que melhoram a experiência mas **mudam regra de negócio ou política**.
Devem ser apresentados ao dono do produto e só então virar fase de execução:

1. **Justificativa obrigatória para voto negativo.** Tornar a justificativa
   obrigatória quando o resultado for `NAO_FAVORAVEL` ou `SOLICITA_INFORMACAO`
   (`avaliador/votar.html:130-137` + validação no serviço). Benefício: o operador
   passa a ter sempre o insumo do ofício de indeferimento e do pedido de
   informação. Custo: fricção adicional no voto; exige validação server-side, não
   só `required` no HTML.
2. **Registro do último lembrete enviado por avaliador**, exibido no card de
   Respostas. Exige campo novo (ou consulta ao log de auditoria) — decisão de
   modelagem, e `ddl-auto: update` implica cuidado de backfill em produção.
3. **Rascunho de solicitação** (salvar preenchimento incompleto no Portal do
   Solicitante). Exige um estado novo em `SolicitacaoOnline` — deve ser avaliado
   contra a diretriz de não afrouxar invariantes da entidade de staging.
4. **Texto-guia da justificativa clínica (item 5.1)**: o conteúdo dos marcadores
   precisa ser validado pela equipe de Urgência Renal antes de ir ao ar — é
   orientação clínica, não texto de interface.

---

## 6. Resumo dos riscos de teste por arquivo tocado

| Arquivo tocado | Testes/artefatos que podem quebrar |
|---|---|
| `templates/solicitante/nova.html` | `CamposDeFormulario` + `SolicitacaoOnlineCamposIntegrationTest` (regex sobre `th:field`/`name`); `e2e/pages/PortalSolicitantePage.java:38-48` |
| `templates/solicitante/detalhe.html` | `SolicitanteControllerTest`, `SolicitanteControllerSemTransacaoIntegrationTest`, `SolicitacaoOnlineDetalheIntegrationTest`, `SolicitanteCancelamentoTransacaoIntegrationTest`, `SolicitanteInformacaoComplementarIntegrationTest` |
| `templates/avaliador/votar.html` | `e2e/pages/AvaliadorPage.java` (ids `#resultado_*`, `#checkConfirmaVoto`, `#btnConfirmarVotoFinal`, `textarea[name=justificativa]`, botão "Registrar meu voto"); `AvaliadorControllerTest` |
| `templates/avaliador/lista.html` | `AvaliadorControllerTest` (model attributes) |
| `templates/processos/detalhe.html` | `ProcessoDetalheControllerTest`, `ProcessoDetalheSemTransacaoIntegrationTest`, `e2e/pages/ProcessoDetalhePage.java` |
| Qualquer template (novo ícone `bi-*`) | `IconesBootstrapTest` |
| Qualquer botão/link só com ícone | `AcessibilidadeBotaoIconeTest` |
| `static/css/app.css` (renomear arquivo não se aplica) | `RecursosEstaticosCacheTest` |

---

## 7. Ordem sugerida de execução

**Bloco 1 (imediato, baixo risco):** Fases 1 → 3 → 4.
**Bloco 2 (maior retorno percebido):** Fases 2 → 5.
**Bloco 3 (reestruturação):** Fase 6, sozinha, com PR próprio.
**Bloco 4 (polimento):** Fases 7 → 8 → 9 → 10.
**Bloco 5:** Fase 11, só depois de conversa com o dono do produto.

**Total: 10 fases executáveis + 1 fase de decisões de produto.**
