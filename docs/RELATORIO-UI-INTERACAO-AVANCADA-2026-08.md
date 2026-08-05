# Relatório de UI/UX — Interação avançada (os 4 perfis)

**Data:** 2026-08-04 · **Analista:** auditoria técnica (Opus 5)
**Escopo:** a qualidade da **interação** do sistema como um todo — dados vivos,
teclado, instalabilidade, notificação fora da aba, busca global, autosave,
riqueza do painel, micro-interações e acessibilidade avançada — para ADMIN,
OPERADOR, AVALIADOR e SOLICITANTE.
**Terceiro da série.** Complementar a:
- `RELATORIO-UI-SOLICITANTE-AVALIADOR-2026-08.md` (Portais externos, Fases 1–10, PRs #2–#6)
- `RELATORIO-UI-OPERADOR-SISTEMA-2026-08.md` (área do operador + camada de sistema, Fases A–E, PRs #8–#12)

> **Documento de diagnóstico. Nenhum código foi alterado para produzi-lo.**
> Todo achado foi verificado por leitura do código real, com arquivo:linha.
> Onde a decisão depende de produto (volume de uso, apetite de risco), o texto
> diz explicitamente **"decisão do usuário"** em vez de decidir sozinho.

---

## 1. Sumário executivo

Os dois relatórios anteriores cobriram **higiene**: contraste, rótulos,
landmarks, acentuação, consistência de componentes. Esse trabalho está feito e
feito bem. O que sobra agora é uma questão diferente, e mais difícil de ver:
**o sistema é estático**.

Fora do chat, **nada no SAUR se atualiza sozinho**. O contador de pendências do
avaliador, o status do processo, a fila de triagem, os cartões do Painel — todos
são fotografias tiradas no instante do `render` e congeladas até alguém apertar
F5. Um avaliador pode ter o portal aberto na tela o dia inteiro enquanto três
processos novos chegam para ele, e o sino da barra continuará marcando o número
de quando ele fez login.

A ironia é que **a infraestrutura para resolver isso já existe e está em
produção** — só foi aplicada ao chat. O `layout.html` tem dois blocos de
*polling* de 20 s (`:177-229`) alimentados por dois endpoints JSON de contagem
(`/processos/solicitacoes-online/nao-lidas-count`, `/solicitante/nao-lidas-count`),
com comparação contra `sessionStorage` para não notificar no primeiro ciclo. É
um padrão maduro, testado em produção, e **está sendo usado para avisar sobre
conversa, não sobre trabalho**.

**Os cinco itens que eu faria primeiro:**

| # | Achado | Por que importa |
|---|--------|-----------------|
| 1 | Os dois *polls* globais **nunca pausam** e rodam a cada 20 s para sempre | Além de bateria/rede, eles **renovam a sessão indefinidamente**, anulando na prática o `timeout: 30m` que foi configurado de propósito (`application-prod.yml:23`). Numa estação compartilhada de hospital, uma aba aberta é uma sessão que nunca expira. |
| 2 | O contador de pendências do avaliador **nunca se atualiza** | É o único número que diz ao médico que há trabalho. Hoje só muda com recarga manual da página. O padrão de *poll* para corrigir isso já existe pronto ao lado. |
| 3 | `mostrarToast` está **definido duas vezes, com comportamentos diferentes** | `layout.html:396` e `processo-detalhe.js:8`. Na tela mais usada do operador vale uma versão; em todas as outras, a outra. Divergência silenciosa numa função de feedback. |
| 4 | **Nenhum aviso ao sair com formulário preenchido** (`beforeunload`: 0 ocorrências) | O solicitante que escreve a justificativa clínica e fecha a aba perde tudo, sem nenhum aviso. Idem o operador que edita o corpo de um e-mail (`detalhe.html:193`). Mais barato e mais abrangente que autosave. |
| 5 | O sistema tem **zero atalhos de teclado** e busca em **apenas 3 das 11 telas de lista** | Operador e admin navegam 100% por menu e mouse. `ProcessoRepository.buscar` já resolve a consulta no banco — falta só a porta de entrada. |

Dois itens da lista original de exploração eu **desaconselho explicitamente**:
**WebSocket** (§8.1) e **notificações push do navegador** (§8.2). O raciocínio
está na §8, com os números que sustentam a recusa.

---

## 2. Método e limites

**Examinado:** os 28 templates Thymeleaf (5.129 linhas), os 14 arquivos
JavaScript (1.257 linhas), o `app.css` (1.098 linhas), o `SecurityConfig`, o
`GlobalModelAdvice`, o `AvaliadorController`, o `HomeController`, os
`application*.yml` e as consultas de `ProcessoRepository` envolvidas.

**Como:** leitura integral dos arquivos de interação (`layout.html`,
`chat-solicitacao.js`, `processo-detalhe.js`, `solicitante-nova.js`,
`lock-submit.js`, `confirmar-acao.js`) e verificação por varredura para as
afirmações quantitativas (atalhos de teclado, `beforeunload`, `setInterval`,
`localStorage`, `@media`, `font-size` em px). Comandos no Anexo A.

**Fora de escopo (deliberado):**
- Regra de negócio, decisão médica, fluxo de aprovação, imparcialidade do
  avaliador. Nada aqui altera o que o sistema decide — só como ele conversa.
- Os achados de higiene dos dois relatórios anteriores, já executados.
- Desempenho de backend além do que a interação proposta provoca.

**Limite honesto:** não executei o sistema em navegador nem medi latência real.
As afirmações sobre comportamento de navegador (instalabilidade de PWA, Web
Push no iOS) dependem de política de fornecedor que muda com o tempo — estão
marcadas como tal e devem ser reconferidas antes de virar implementação.

---

## 3. O que já está bom (não mexer)

1. **O padrão de *polling* do chat é bem construído.** `chat-solicitacao.js`
   compara IDs entre ciclos para só notificar mensagem realmente nova
   (`:150-161`), calcula uma assinatura do estado para **não reescrever o DOM
   quando nada mudou** (`:123-148`, preservando *scroll* e seleção de texto), e
   **pausa com `visibilitychange`** (`:227-230`). Isso é qualidade acima da
   média; é o modelo a replicar, não a substituir.

2. **`lock-submit.js` documenta o incidente que o originou** e explica por que
   o `setTimeout(0)` é necessário (o botão desabilitado sairia do *entry list*
   do formulário). Código com memória institucional.

3. **A `AudioContext` compartilhada e destravada no primeiro gesto**
   (`layout.html:352-366`) resolve corretamente um problema real de política de
   autoplay, e o comentário registra a limitação que sobra.

4. **Tipografia toda em `rem`.** Zero `font-size` em `px` no `app.css` e zero
   nos templates — o ajuste de tamanho de fonte do navegador funciona
   nativamente em todo o sistema. Isso torna desnecessário um controle próprio
   de tamanho de fonte (§7.3).

5. **`prefers-reduced-motion` respeitado** (`app.css:1031-1048`), incluindo o
   desligamento da animação infinita do passo atual.

6. **O único "gráfico" do sistema é CSS puro** — a barra empilhada de
   `avaliador/lista.html:393-402`, montada com `flex` e as variáveis `--rs-*`.
   É a prova de que dá para enriquecer o Painel (§6) sem introduzir biblioteca
   de gráfico.

7. **Estado de erro tem saída por perfil.** `GlobalModelAdvice.inicioDoPerfil`
   (`:138-156`) resolveu o beco sem saída dos perfis externos. É o tipo de
   detalhe que costuma faltar.

---

## 4. Achados — Severidade ALTA

### 4.1 Os *polls* globais nunca pausam — e mantêm a sessão viva para sempre

**O que acontece.** `layout.html` registra dois `setInterval(poll, 20000)`
(`:203` e `:227`), um para ADMIN/OPERADOR e outro para SOLICITANTE, incluídos
dentro do fragmento `navbar` — ou seja, **em praticamente toda tela do
sistema**. Diferente do `chat-solicitacao.js`, eles:

- **não têm `visibilitychange`** — continuam disparando com a aba em segundo
  plano, minimizada, ou o celular no bolso;
- **não têm limite de tempo** — não param depois de N ciclos sem interação;
- **não têm recuo em caso de falha** — o `.catch` (`:200`, `:224`) engole o erro
  e o intervalo segue no mesmo ritmo mesmo com o servidor fora do ar.

**A consequência não é só bateria.** `application-prod.yml:23-24` configura
`server.servlet.session.timeout: 30m`, e o `CLAUDE.md` registra esse timeout
como uma decisão **deliberada** da vistoria de segurança de 2026-07-28. Cada
requisição do *poll* toca a `HttpSession` e reinicia o relógio de inatividade.
Portanto: **enquanto qualquer aba do SAUR estiver aberta, a sessão nunca
expira** — para ADMIN, OPERADOR e SOLICITANTE. Numa estação compartilhada de
hospital ou de secretaria, o timeout de 30 minutos existe exatamente para o
cenário "o servidor saiu para o almoço e deixou a tela aberta", e o *poll* o
neutraliza.

Não é uma falha de autenticação (ninguém entra sem senha), e não é exclusividade
deste projeto — é o efeito colateral clássico de introduzir *polling* num sistema
com timeout de sessão. Mas é uma **regressão silenciosa de uma decisão de
segurança explícita**, causada por uma feature de UI, e por isso entra aqui.

**Correção (duas partes, independentes):**
- (a) **Pausar com `visibilitychange`**, exatamente como `chat-solicitacao.js`
  já faz. Três linhas por bloco. Resolve bateria e reduz muito o problema de
  sessão (aba em segundo plano deixa de renovar).
- (b) **Decisão do usuário:** aceitar que uma aba em primeiro plano mantenha a
  sessão viva (é defensável — há alguém na frente da tela), ou tornar os
  endpoints de contagem "não renovadores" de sessão. A segunda opção é mais
  invasiva (exige um filtro que não toque no *last accessed time*, o que o
  servlet container não expõe de forma limpa) e eu **não a recomendaria** sem
  um pedido explícito. A opção honesta e barata é (a) + registrar no `CLAUDE.md`
  que o timeout só vale com a aba fora de foco.

**Esforço: muito baixo (a). Risco: nulo.**

---

### 4.2 O contador de pendências do avaliador é uma fotografia

**O que acontece.** `GlobalModelAdvice.pendentesAvaliador()` (`:76-89`) calcula
a contagem no `render` de cada página e o `layout.html:131-133` a pinta no sino
da barra. Não há nenhum mecanismo de atualização: **o número só muda quando o
médico navega**. Um avaliador que deixa `/avaliador` aberto e recebe três
processos novos não vê nada acontecer.

O contraste é o ponto: o mesmo `layout.html`, 40 linhas acima, tem dois *polls*
de 20 s **atualizando o badge de mensagens não lidas do operador ao vivo**
(`:194-198`). O sistema aprendeu a manter um número vivo — e aplicou isso à
conversa, não ao trabalho.

**Há um pré-requisito técnico que não pode ser ignorado.** A contagem hoje é
cara: `AvaliadorController.pendentesDoMembro` (`:549-563`) carrega as **entidades
`Parecer`** e filtra em Java navegando `par.getProcesso().getStatus()`, que é
LAZY — é um N+1 por *render*, hoje tolerável porque acontece uma vez por página.
Transformar isso num *poll* de 20 s multiplicaria o N+1 por ciclo, numa VM com
956 MB de RAM compartilhada com outros três serviços (`CLAUDE.md`, vistoria de
2026-08-03). **A correção começa por uma consulta de contagem no banco**
(`select count(...) from Parecer p where p.membro.id = :id and p.resultado is
null and p.dataEnvio is not null and p.processo.status = ENVIADO`), com o mesmo
critério de `pendenteAtivoParaVoto` — e aí sim o *poll*.

**Ganho real:** o médico avaliador é o usuário mais esporádico do sistema (entra
uma vez por processo, muitas vezes pelo celular). Ele é justamente quem mais se
beneficia de "apareceu trabalho novo" sem depender de checar o e-mail.

**Correção.** (1) consulta de contagem dedicada; (2) endpoint
`GET /avaliador/pendentes-count` devolvendo `{"total": N}`, no mesmo formato dos
dois já existentes; (3) terceiro bloco de *poll* no `layout.html`, escopado com
`sec:authorize="hasRole('AVALIADOR')"`, reaproveitando o padrão de
`sessionStorage` (primeiro ciclo só define a base, nunca notifica sozinho).
**Esforço: baixo. Risco: baixo.**

**Imparcialidade:** o endpoint devolve **um número e nada mais** — mesmo
contrato já documentado no javadoc de `GlobalModelAdvice` (`:26-29`). Nenhuma
iniciai, nenhum número de processo, nenhuma informação sobre votos alheios.
Isso é inegociável e deve constar do javadoc do endpoint novo.

---

### 4.3 `mostrarToast` existe em duas versões divergentes

**O que acontece.** A função de feedback padrão do sistema está implementada
**duas vezes**:

| Onde | Carregada em | Diferenças |
|---|---|---|
| `layout.html:396-424` (fragmento `notificacaoSonora`) | toda tela com `layout :: navbar` (25 telas) | remove o toast de uma vez; botão fechar via `innerHTML = '&times;'`, **sem `aria-label`** |
| `processo-detalhe.js:8-41` (`window.mostrarToast`) | `processos/detalhe.html` | *fade-out* de 300 ms antes de remover; botão fechar com `aria-label="Fechar"` e `textContent` |

Em `processos/detalhe.html` **as duas carregam** (`:1242` inclui o script depois
da navbar) e a segunda sobrescreve a primeira. Resultado: a tela mais usada do
operador tem uma versão do toast — com *fade* e com rótulo acessível no botão de
fechar — e **as outras 24 telas têm outra**, sem *fade* e com o botão de fechar
sem nome acessível.

Isso não quebra nada hoje. Mas é uma função de feedback do design system com
duas fontes da verdade, e a divergência já produziu uma regressão de
acessibilidade (o `aria-label` que existe numa e não na outra) sem ninguém
perceber.

**Correção.** Extrair para `static/js/toast.js` (fonte única, na versão melhor —
a de `processo-detalhe.js`), incluir junto do fragmento de navbar, e remover as
duas cópias. **Esforço: baixo. Risco: baixo**, com uma ressalva: `mostrarToast`
é chamada de `chat-solicitacao.js`, `solicitante-nova.js`, `processo-detalhe.js`
e dos dois *polls* inline do layout — a ordem de carregamento precisa garantir
que o arquivo novo venha antes de todos.

---

### 4.4 Nenhum aviso ao sair com trabalho não salvo

**O que acontece.** Varredura em todo o `static/js` e em todos os templates:
**zero ocorrências de `beforeunload`** e **zero de `localStorage`**. Dois
cenários concretos, ambos com perda silenciosa:

1. **Portal do Solicitante, nova solicitação.** O formulário tem justificativa
   clínica, dados do paciente e anexos. Existe um botão **manual** "Salvar
   rascunho" (`solicitante-nova.js:50-97`, endpoint `salvarRascunho`), mas nada
   avisa quem fecha a aba ou clica em "Cancelar" sem tê-lo apertado. E, como o
   relatório anterior já registrou, **o rascunho não salva os arquivos** — só os
   quatro campos de texto.
2. **Tela de detalhe do processo, e-mails prontos.** O operador edita o corpo do
   e-mail num `<textarea>` (`processos/detalhe.html:193-194`) que só é lido no
   momento do envio AJAX. Navegar para outra aba do wizard, clicar em qualquer
   link, ou apertar "voltar" descarta o texto editado sem nenhum aviso.

**Por que isto vem antes de autosave.** Autosave (§5.3) resolve o caso 1 e não
resolve o caso 2 (não há onde persistir o rascunho de um e-mail). Um
`beforeunload` guardado por "o formulário está sujo" resolve os dois, custa ~20
linhas, e não introduz nenhuma escrita nova no banco.

**Cuidado de implementação:** o `beforeunload` precisa ser **desarmado no
submit**, senão o próprio envio do formulário dispara o aviso — erro clássico. E
o navegador ignora a mensagem customizada há anos: o texto exibido é o genérico
dele, não adianta caprichar na frase.

**Esforço: baixo. Risco: baixo.**

---

## 5. Achados — Severidade MÉDIA

### 5.1 Zero atalhos de teclado em todo o sistema

Varredura: **nenhum** `keydown`/`keyup` em todo o `static/js` fora do
desbloqueio de áudio (`layout.html:361`), e **nenhum** `accesskey` em nenhum
template. Toda navegação é mouse + menu.

**Onde há ganho real** (e onde não há):

| Candidato | Avaliação |
|---|---|
| `/` ou `Ctrl+K` para focar a busca | **Sim.** Ganho direto para o operador, que trabalha na lista de processos o dia inteiro. |
| `?` abrindo um cartão com os atalhos | **Sim, se houver atalhos.** Sem isso, atalho invisível é atalho inexistente. |
| Navegação entre pendentes do avaliador (`j`/`k`, `n` = próximo) | **Talvez.** Já existe "próximo pendente" (`avaliador/lista.html:12-19`) e "Processo X de N" (`votar.html:20-22`). Um atalho economiza um clique — ganho pequeno para um usuário que entra 1× por processo. **Decisão do usuário.** |
| **Atalho que seleciona ou registra voto** | **Não. Recusado.** O voto é irreversível e já é protegido por modal + *checkbox* de ciência. Uma tecla que seleciona "Não favorável" por engano é exatamente o pior erro possível do sistema. Isto fica registrado como **descartado por design**, não como pendência. |
| Atalhos de ação destrutiva em geral (excluir, cancelar, reabrir) | **Não.** Mesma lógica. |

**Restrições de implementação:** nunca capturar tecla enquanto o foco está em
`input`/`textarea`/`select` ou em elemento `contenteditable` (o operador digita
motivo de indeferimento e corpo de e-mail); nunca sequestrar combinação já usada
pelo navegador; e o JS vai em `static/js/`, não inline.

**Esforço: baixo. Risco: baixo.** **Decisão do usuário:** a equipe é pequena e
usa o sistema em ritmo de dias, não de segundos — atalho de teclado é um ganho
de conforto, não de produtividade mensurável. Vale perguntar antes de investir.

---

### 5.2 Busca existe em 3 telas de 11 — e não há busca global

**Estado verificado.** Formulário de busca (`method="get"`) existe em três
templates apenas: `processos/lista.html`, `arquivo/lista.html` e
`auditoria/lista.html`. **Não existe busca** em Membros, Usuários, Controle de
Urgências e Solicitações online. Para achar um médico avaliador, o operador
navega ao menu, abre a lista inteira e procura com os olhos (ou `Ctrl+F` do
navegador, que só acha o que está na página atual — e Membros não é paginada).

**A peça mais cara já existe.** `ProcessoRepository.buscar` (`:189-203`) resolve
a busca **no banco**, por nome do paciente, número e equipe solicitante, com
paginação. Um *command palette* (`Ctrl+K`) precisaria de: um endpoint JSON que
projete `{numero, paciente, status, url}` a partir dessa consulta, um `<dialog>`
ou modal Bootstrap, e ~80 linhas de JS.

**Riscos que a implementação precisa respeitar:**
1. **Só ADMIN/OPERADOR.** O resultado carrega **nome completo de paciente**. A
   *palette* não pode existir para AVALIADOR (quebraria a imparcialidade) nem
   para SOLICITANTE (veria pedidos de terceiros). Escopo por `sec:authorize` no
   template **e** por `requestMatchers` no `SecurityConfig` — não só um dos dois.
2. **Não registrar o termo buscado em auditoria com nome de paciente.** O
   projeto já tem duas correções desse exato padrão (`PROCESSO_CADASTRADO` em
   2026-07-28 e a exportação de dossiê em 2026-08-03, ambas passadas a usar
   `Iniciais.de`). Uma busca digitada é literalmente o nome do paciente. A
   recomendação é **não auditar** esse endpoint, ou auditar só a contagem de
   resultados.
3. **Debounce e limite.** Sem `debounce` (~250 ms) e `LIMIT` pequeno (10), cada
   tecla vira uma consulta com `like '%...%'` — que não usa índice.

**Esforço: médio. Risco: médio-baixo**, dominado pelos itens 1 e 2 acima.
Ganho: alto para o operador, nulo para os outros três perfis.

---

### 5.3 Autosave: sim, mas o menor dos problemas

Já existe o botão manual "Salvar rascunho" com endpoint pronto
(`solicitante-nova.js:50-97`). Transformá-lo em autosave é **debounce de ~2 s no
`input` dos quatro campos, reusando exatamente a mesma chamada** — talvez 15
linhas.

**Minha avaliação:** faça, mas **depois** do `beforeunload` (§4.4), e sem
ilusões. Três ressalvas honestas:

1. **Não cobre os anexos.** A limitação é do navegador (um `<input type="file">`
   não pode ser repopulado), já documentada no relatório anterior. Autosave que
   salva o texto e não os arquivos pode até **piorar** a percepção: o usuário
   volta, vê tudo preenchido, e envia sem os documentos. Se for feito, o aviso
   "reanexe os documentos" precisa ficar mais visível, não menos.
2. **Escrita no banco a cada 2 s de digitação.** Com o volume atual (6
   solicitações em produção) é irrelevante; é bom lembrar que a conta cresce com
   usuários, não com processos.
3. **Não há autosave possível para o e-mail do operador** — não existe entidade
   para guardá-lo, e criar uma seria inventar estado novo para um ganho pequeno.
   **Não recomendo.**

**Decisão do usuário:** autosave automático versus deixar o botão manual e só
adicionar o aviso de saída. Eu ficaria com a segunda opção se fosse escolher
uma só.

---

### 5.4 Nenhum feedback entre o clique e a próxima página

O sistema é POST + *redirect* + página inteira nova — decisão correta e que os
dois relatórios anteriores recomendam preservar. Mas isso significa que entre o
clique e o desenho da página nova **há um intervalo em que nada acontece na
tela**. Hoje isso só é coberto onde há `data-lock-submit` (formulários) ou
`chamarComEspera` (botões AJAX de `processo-detalhe.js:95-116`). **Links de
navegação não têm nada**: abrir um processo, paginar, trocar de tela.

Numa rede de hospital, alguns segundos sem retorno visual é o que produz o
segundo clique — o mesmo mecanismo do incidente de 03/08 documentado no
`lock-submit.js`.

**Correção proposta: uma barra de progresso fina no topo**, disparada no evento
`click` de links internos e no `submit` de formulários, removida no `pageshow`
(cobrindo BFcache, padrão que o projeto já usa). ~30 linhas de JS + ~15 de CSS,
sem biblioteca, respeitando `prefers-reduced-motion`.

**O que eu NÃO faria: *skeleton loading*.** *Skeleton* só faz sentido quando o
conteúdo é montado no cliente — aqui o HTML chega pronto do servidor, e o
"branco" é tempo de rede, não de renderização. Fazer *skeleton* exigiria
transformar a navegação em AJAX, que é exatamente o que o relatório anterior
desaconselha (§10.5 do relatório do operador). Barra de progresso resolve a
mesma dor sem mexer na arquitetura.

**Esforço: baixo. Risco: baixo.**

---

## 6. Painel: de fotografia para tendência

`dashboard.html` (276 linhas) é hoje **8 cartões de número + 1 tabela**. Não há
nenhuma noção de tempo: quantos processos entraram este mês contra o anterior, se
o tempo médio de decisão está subindo ou caindo, se a fila de triagem cresceu na
semana.

**Os dados já estão carregados.** `HomeController.dashboard` (`:50`) traz todos
os processos do ano com pareceres (`findByAnoComPareceres`, com *fetch join*) e
percorre a lista uma vez para os contadores (`:60-84`). Agregar por mês a partir
de `dataCadastro`/`dataDecisao` **no mesmo laço** custa praticamente nada — sem
consulta nova, sem serviço novo.

**Três acréscimos de valor real, em ordem:**

1. **Barra de 12 meses "processos por mês"** — a mesma técnica CSS de
   `avaliador/lista.html:393-402` (divs com `flex`, variáveis `--rs-*`). Diz
   sazonalidade, que é a pergunta que um relatório anual responde hoje só em PDF.
2. **Tendência do tempo médio de decisão** — o `TempoRespostaService` já calcula
   a média geral e o prazo-meta (`HomeController:114-119`); falta a série por
   mês. Um indicador de "melhorando / piorando" com seta é mais útil que o
   número absoluto isolado.
3. **Idade da fila** — quantos dias o processo em andamento mais antigo está
   parado. É a informação que dispara ação, e hoje o operador só a obtém
   abrindo processo por processo.

**O que NÃO fazer: introduzir Chart.js ou qualquer biblioteca de gráfico.** O
projeto decidiu, e documentou, não ter framework de front. Um gráfico de 12
barras é CSS; um *sparkline* é um `<svg>` de uma linha com `polyline`. Adicionar
uma dependência de ~200 KB para isso contradiz a decisão registrada no
`CLAUDE.md` e cria superfície de manutenção (versão, CVE, WebJar).

**Decisão do usuário — e essa é importante:** produção tem **4 processos** e 6
solicitações (vistoria de 2026-08-03). Um gráfico de tendência sobre 4 pontos
mostra ruído, não tendência, e pode dar ao Painel uma aparência de "sistema com
dado errado". Minha recomendação é **implementar preparado, exibir
condicionalmente** — o gráfico só aparece quando houver, digamos, ≥ 3 meses com
movimento. Mas o corte é decisão do usuário, não minha.

**Esforço: médio. Risco: baixo** (nenhuma escrita, nenhuma regra tocada).

---

## 7. Acessibilidade avançada (além da estrutural já feita)

O trabalho estrutural (landmarks, rótulos, ARIA de abas, botões só-ícone) foi
concluído nas Fases B/9 e está protegido por testes de arquivo
(`AcessibilidadeEstruturaTest`, `AcessibilidadeBotaoIconeTest`,
`IconesBootstrapTest`). O que sobra:

**7.1 Navegação por teclado nunca foi verificada de ponta a ponta.** Os testes
existentes leem arquivo (garantem que o `<main id="conteudo">` existe), e os
`@WebMvcTest` verificam status e model. **Nenhum deles prova que dá para votar
usando só o teclado** — que é o fluxo mais crítico do sistema, feito por um
usuário esporádico, às vezes com o mouse ocupado. O projeto já tem Playwright
(`e2e.ps1`); um teste que percorra `Tab`/`Enter`/`Espaço` do login até o voto
confirmado é a única forma automatizada de garantir isso, e roda no *profile*
`e2e` já existente, sem pesar na suíte rápida.
**Esforço: médio. Risco: nulo** (é teste, não código de produção).

**7.2 Não há suporte a `prefers-contrast` nem a `forced-colors`.** As sete
`@media` do `app.css` (`:811, 857, 872, 931, 978, 1031, 1057`) cobrem
*breakpoints*, movimento reduzido e impressão — nenhuma cobre contraste. Um
usuário com "aumentar contraste" ligado no sistema operacional, ou no modo de
alto contraste do Windows, recebe exatamente a mesma paleta. Como todos os
*badges* institucionais já passam em AA (medição do relatório anterior), o ganho
é menor do que parece; o bloco `@media (prefers-contrast: more)` valeria para
reforçar bordas e o anel de foco, ~20 linhas.
**Esforço: baixo. Risco: baixo. Prioridade: baixa.**

**7.3 Controle próprio de tamanho de fonte: NÃO fazer.** Verifiquei: **não há um
único `font-size` em `px`** no `app.css` nem nos templates — tudo em `rem`. Isso
significa que o ajuste de tamanho de texto do navegador e do sistema operacional
**já funciona** em todo o SAUR. Um controle de "A- / A+" próprio seria uma
segunda fonte da verdade, precisaria persistir preferência por usuário e
duplicaria um recurso nativo que já está correto. **Registrado como descartado.**

**7.4 O anel de foco é parcial.** `app.css` define `:focus-visible` para dois
componentes (`.wizard-step:366-367`, `.btn-apagar-msg-chat:1015`) e
`.form-control:focus` (`:604`). O restante depende do padrão do Bootstrap, que é
aceitável — mas um `:focus-visible` global com o azul institucional daria
consistência e é uma regra só.

---

## 8. O que eu **não** recomendo fazer

Esta seção é tão importante quanto as anteriores. Três dos itens explorados são,
na minha avaliação, ruins para este sistema.

### 8.1 WebSocket — **não**

Custo real: dependência nova (`spring-boot-starter-websocket` + STOMP ou
SockJS), estado por conexão no servidor, configuração de `upgrade` no nginx,
lógica de reconexão no cliente, e um novo eixo de teste que a suíte atual não
cobre.

Contra o quê: um fluxo cujo desfecho é medido em **dias** (o próprio
`application.yml:118-126` justifica um intervalo de varredura de 15 minutos
dizendo que isso "mantém o atraso máximo muito abaixo de qualquer janela
clinicamente relevante"), com **4 processos e 8 usuários em produção**, numa VM
de **956 MB de RAM compartilhada com outros três serviços**.

*Polling* de 20 s já entrega, para este domínio, a mesma percepção de "ao vivo"
com uma fração do custo e zero dependência nova. **A resposta certa é usar
melhor o polling que já existe (§4.1, §4.2), não trocá-lo.** WebSocket só se
justificaria se surgisse um caso de colaboração simultânea real — que não
existe: cada processo é tocado por um operador de cada vez.

### 8.2 Notificações push do navegador (Web Push) — **não**

Custo real: chaves VAPID (dependência criptográfica nova), entidade de
*subscription* por usuário × dispositivo com tratamento de expiração e `410
Gone`, endpoint de registro, **service worker obrigatório**, e o pedido de
permissão do navegador — que, uma vez negado, é praticamente irrecuperável sem
o usuário ir às configurações.

E há um limite que decide a questão: **no iOS, Web Push só funciona se o usuário
tiver instalado o site como PWA na tela inicial** (Safari 16.4+). Ou seja, para
o médico de iPhone — parcela relevante do público — o recurso não funciona a
menos que ele execute um passo manual que ninguém vai executar.

**Contra isso, o canal já existe e é o certo:** registrar o envio dispara
automaticamente `EmailTemplateService.emailConviteAvaliador` para cada avaliador
pendente (`RegistroEnvioService.enviarConvitesAvaliadores`), e há lembrete
manual (`POST /processos/{id}/lembrete-avaliador`). E-mail é o canal que médico
lê. Push seria um segundo canal, mais frágil, para a mesma mensagem.

**Recomendação: descartar.** Se o objetivo real é "o avaliador saber sem abrir o
sistema", o investimento certo é melhorar o texto e a cadência do e-mail que já
existe, não abrir uma frente nova.

### 8.3 PWA / service worker — **não agora; e nunca com cache de HTML**

O sistema já tem `favicon.svg` + `apple-touch-icon` (`layout.html:14-15`), e
**não tem `manifest.json`** (`static/` contém apenas `brasao.png`,
`favicon.svg`, `css/`, `fonts/`, `js/`).

O que um PWA entregaria aqui: um ícone na tela inicial do celular e a barra de
endereço escondida. **Não** entregaria uso offline — cada tela depende de sessão
autenticada e de dado do servidor.

**O risco que precisa ser dito com todas as letras:** um service worker mal
desenhado guarda no dispositivo respostas HTML de telas autenticadas. Numa
delas está o **nome completo do paciente** (tela do operador, do solicitante),
e em `/avaliador/*/pdf/*` está o **PDF clínico** — que ficaria em cache no
disco do celular do médico, fora do controle da sessão, sobrevivendo ao logout.
Isso transformaria uma melhoria de UI num problema de proteção de dado de saúde.

**Se um dia for feito, as regras não negociáveis são:**
1. Cache **exclusivamente** de `/css/**`, `/js/**`, `/fonts/**`, `/webjars/**` e
   `/favicon.svg` — todos já servidos com hash de conteúdo no nome
   (`spring.web.resources.chain.strategy.content`, `application.yml`), o que
   torna a invalidação automática.
2. **Nenhuma resposta HTML em cache. Jamais.** Qualquer `fetch` que não bata na
   lista acima passa direto para a rede, sem tocar no cache.
3. **Nenhum *navigation fallback* offline.** Uma "casca" offline de sistema
   autenticado leva o usuário a uma tela logada sem sessão — pior que a página
   de erro do navegador.
4. `/avaliador/*/pdf/*` explicitamente na lista de exclusão, com comentário
   dizendo por quê.

A CSP de produção não é obstáculo: `default-src 'self'` (`SecurityConfig:76`)
cobre `worker-src` e `manifest-src` por herança.

**Minha recomendação:** um `manifest.json` **sozinho**, sem service worker, é
barato (um arquivo, dois `<link>`) e já dá ícone e nome decentes ao "Adicionar à
Tela de Início" do iOS. Isso captura a maior parte do ganho com **zero** risco de
cache. O service worker fica como **decisão do usuário**, e minha opinião é que
não vale — a menos que apareça uma demanda concreta de "quero abrir como app".

> ⚠ **Verificar antes de implementar:** o critério do Chrome para oferecer o
> prompt de instalação (historicamente manifest + service worker com handler de
> `fetch`) muda com o tempo. A afirmação acima sobre iOS e sobre o prompt do
> Chrome deve ser reconferida na documentação vigente antes de virar tarefa.

### 8.4 Feedback tátil (`navigator.vibrate`) — marginal

Zero usos hoje. Funciona em Android/Chrome, é **ignorado no iOS** (todos os
Safari), e depende de o dispositivo não estar no silencioso. Como **único**
feedback seria uma falha de acessibilidade; como reforço do toast que já existe,
é um detalhe de duas linhas com ganho quase nulo. **Não é prioridade.** Se
entrar, entra de carona na fase de micro-interações, nunca como item próprio.

### 8.5 Reiterando o que os relatórios anteriores já descartaram

Continuam válidos e **não devem ser reabertos**: não trocar Bootstrap nem
introduzir framework de front; não transformar a navegação em AJAX; não
adicionar tema escuro; não acentuar `ResultadoParecer.descricao` (alimenta PDF
oficial); não mostrar ao avaliador quantos votos o processo já tem.

---

## 9. Plano de execução sugerido

Fases independentes entre si, ordenadas por **ganho ÷ risco**. Cada fase = 1 PR.

> **Instrução geral ao executor:** ao fim de cada fase, `.\test.ps1` (JDK 21,
> ~747 testes hoje) com 0 falhas. Fases marcadas ⚠ mexem em algo coberto por
> Page Object do E2E ou por teste de arquivo — nessas, rodar também
> `.\e2e.ps1 -Headless`, lembrando da falha pré-existente de SMTP local
> documentada no `CLAUDE.md` (não é regressão). Nunca introduzir ícone `bi-*`
> inexistente (`IconesBootstrapTest`), botão só-ícone sem rótulo
> (`AcessibilidadeBotaoIconeTest`) ou tela sem `<main id="conteudo">`
> (`AcessibilidadeEstruturaTest`). JS novo **sempre** em `static/js/`.

### FASE I — Higiene da interação (risco ~nulo)
- `visibilitychange` nos dois *polls* globais do `layout.html` (§4.1a)
- `mostrarToast` como fonte única em `static/js/toast.js` (§4.3)
- `beforeunload` guardado por "formulário sujo" em `solicitante/nova.html` e nos
  `<textarea>` de e-mail de `processos/detalhe.html` (§4.4)
- Remover `fonts.googleapis.com`/`fonts.gstatic.com` da `CSP_PROD`
  (`SecurityConfig:78-79`) — resíduo da auto-hospedagem da fonte feita na Fase E
  (`layout.html:16-21`); a CSP está mais frouxa do que precisa (§Anexo B)
- `:focus-visible` global com o azul institucional (§7.4)

> Tudo isso é atributo, função movida de lugar ou regra de CSS. É a maior
> relação ganho/esforço do relatório.

### FASE II — Contador do avaliador ao vivo ⚠
- Consulta de contagem dedicada em `ParecerRepository` (mesmo critério de
  `pendenteAtivoParaVoto`), eliminando o N+1 antes de multiplicá-lo (§4.2)
- `GET /avaliador/pendentes-count` → `{"total": N}` — **só o número**
- Terceiro bloco de *poll* no `layout.html`, escopado a `ROLE_AVALIADOR`, no
  mesmo padrão de `sessionStorage` dos dois existentes
- Badge do sino atualizado ao vivo; toast/som só quando o número **sobe**

⚠ `GlobalModelAdviceTest` cobre o comportamento atual do contador — se a
consulta mudar, o teste precisa ser revisto no mesmo *commit*.

### FASE III — Feedback de navegação
- Barra de progresso no topo em `click` de link interno e `submit`, removida no
  `pageshow` (§5.4), respeitando `prefers-reduced-motion`

### FASE IV — Painel vivo
- Agregação mensal no laço já existente de `HomeController.dashboard` (§6)
- Barra de 12 meses em CSS puro; tendência do tempo médio; idade da fila
- **Exibição condicional** ao volume mínimo — corte é decisão do usuário
- **Sem biblioteca de gráfico**

### FASE V — Busca global (`Ctrl+K`) ⚠
- Endpoint JSON sobre `ProcessoRepository.buscar`, `debounce` 250 ms, `LIMIT 10`
- **Só ADMIN/OPERADOR**, garantido no template **e** no `SecurityConfig` (§5.2)
- **Sem auditoria do termo digitado** (é nome de paciente)
- Atalhos mínimos junto: `/` foca a busca da tela, `?` abre a lista de atalhos,
  `Esc` fecha (§5.1). **Nenhum atalho para voto ou ação destrutiva.**

### FASE VI — Acessibilidade avançada
- Teste E2E de navegação 100% por teclado no fluxo de voto (§7.1)
- `@media (prefers-contrast: more)` (§7.2)

### FASE VII — `manifest.json` (opcional, decisão do usuário)
- Manifesto **sem** service worker: nome, ícones (reusar `favicon.svg`), cor de
  tema, `display: standalone` (§8.3)
- **Nada de service worker sem decisão explícita**, e se houver, com as 4 regras
  da §8.3 escritas como comentário no próprio arquivo

---

## 10. Critério de aceite

| Fase | Como verificar |
|---|---|
| I | `grep -c visibilitychange layout.html` ≥ 2; **uma única** definição de `mostrarToast` no repositório; fechar a aba com a justificativa clínica preenchida gera aviso do navegador; `curl -I` em produção não mostra mais `fonts.g*` na CSP |
| II | Com `/avaliador` aberto, um processo enviado por outro operador faz o sino subir em ≤ 20 s **sem recarregar**; o endpoint devolve **apenas** `{"total":N}` (verificar o corpo cru — nenhum outro campo) |
| III | Clicar em "Abrir" num processo mostra a barra de progresso; ela some ao chegar; some também no "voltar" (BFcache) |
| IV | Painel exibe a série mensal com dado real; com menos de N meses de movimento, o bloco não aparece; `grep -ri "chart\.js\|chartjs" src/` vazio |
| V | `Ctrl+K` abre e busca; `/avaliador` e `/solicitante` **não** têm a palette (verificar como cada perfil); `/auditoria` não registra nenhum termo digitado |
| VI | O teste E2E de teclado passa; `@media (prefers-contrast: more)` presente |
| VII | `manifest.json` servido com 200 para usuário anônimo (liberar em `SecurityConfig`, como foi preciso para `/fonts/**` na Fase E); **nenhum** `serviceWorker.register` no repositório |

Em todas: **suíte completa verde** e, nas marcadas ⚠, `.\e2e.ps1 -Headless` sem
regressão nova.

---

## Anexo A — Comandos de verificação

```bash
# Polls sem pausa: blocos com setInterval que nao registram visibilitychange
grep -rn "setInterval" src/main/resources/

# Atalhos de teclado (esperado hoje: so o desbloqueio de audio do layout)
grep -rn "keydown\|keyup\|accesskey" src/main/resources/

# Aviso de saida / persistencia local (esperado hoje: 0 e 0)
grep -rn "beforeunload" src/main/resources/ | wc -l
grep -rn "localStorage" src/main/resources/ | wc -l

# Definicoes duplicadas de mostrarToast (esperado ao fim da Fase I: 1)
grep -rn "function mostrarToast\|mostrarToast = function" src/main/resources/

# Telas com busca (esperado hoje: 3)
grep -ln 'method="get"' src/main/resources/templates/**/*.html

# Media queries do app.css (contraste ausente hoje)
grep -n "@media" src/main/resources/static/css/app.css

# Tipografia: nenhum font-size em px (esperado: vazio nos dois)
grep -n "font-size: *[0-9]*px" src/main/resources/static/css/app.css
grep -ro "font-size:[.0-9]*px" src/main/resources/templates

# Biblioteca de grafico entrando por engano (esperado: vazio)
grep -ri "chart\.js\|chartjs\|d3\.js" src/ pom.xml
```

---

## Anexo B — Achados menores confirmados de passagem

Não justificam fase própria; entram de carona onde couber.

**B.1 · A CSP de produção ainda libera o Google Fonts.**
`SecurityConfig:78-79` mantém `style-src ... https://fonts.googleapis.com` e
`font-src 'self' https://fonts.gstatic.com`, mas a fonte Inter foi
auto-hospedada em 2026-08-04 (`layout.html:16-21`, arquivos em
`static/fonts/inter-{400,600,700}.woff2`). Nenhum efeito funcional — a política
está simplesmente mais permissiva do que o sistema precisa, o que contradiz a
motivação (privacidade/LGPD) que levou a auto-hospedar. Duas strings a remover.

**B.2 · `script-src 'unsafe-inline'` é consequência do JS inline do layout.**
`SecurityConfig:80` precisa de `'unsafe-inline'` porque o `layout.html` tem três
blocos `<script>` embutidos (`:170-229`, `:345-425`) — o que também contraria a
convenção do próprio `CLAUDE.md` ("JS específico fica em `static/js/*.js`, nunca
inline"). Mover esses blocos para arquivo permitiria endurecer a CSP. **Atenção:**
eles usam `th:inline="javascript"` para injetar URLs (`@{...}`), então a migração
exige passar as URLs por atributo `data-*` num elemento do HTML — não é
recortar e colar. Fica registrado como caminho conhecido, **não** como tarefa
desta rodada.

**B.3 · Os *polls* globais não têm recuo em caso de falha.** Com o servidor
fora do ar, os dois blocos continuam disparando a cada 20 s indefinidamente
(`.catch` vazio, `:200` e `:224`). Um recuo exponencial simples (dobrar o
intervalo até um teto de ~5 min, restaurando no primeiro sucesso) evita que
25 abas abertas martelem um servidor que está justamente tentando voltar.
Cabe na Fase I.

**B.4 · O `data-confirm-msg` só é ligado no `DOMContentLoaded`**
(`confirmar-acao.js:98`). Nenhum conteúdo do sistema é injetado dinamicamente
hoje, exceto o chat (que tem tratamento próprio, `chat-solicitacao.js:205-225`),
então **não é um bug atual**. Mas qualquer conteúdo futuro renderizado por JS
com um botão de ação destrutiva nasceria **sem confirmação**. Se a Fase V
(*palette*) ou a Fase IV introduzirem HTML dinâmico com ações, isso precisa
virar delegação de evento no `document`.

---

*Relatório produzido por inspeção de código em 2026-08-04. Nenhum arquivo de
código foi alterado. Cada achado foi confirmado individualmente no código
citado; suspeitas que não se confirmaram na verificação foram descartadas e não
constam do documento. As afirmações sobre comportamento de navegador (§8.2,
§8.3) dependem de política de fornecedor e estão marcadas para reconferência.*
