# Relatório de UI/UX — Área do Operador e camada de sistema

**Data:** 2026-08-04 · **Analista:** auditoria técnica (Opus 5)
**Escopo:** as 19 telas do operador/administrador + a camada transversal
(design system, acessibilidade, responsividade, privacidade, estados de erro).
**Complementar a:** `RELATORIO-UI-SOLICITANTE-AVALIADOR-2026-08.md`, que cobriu
os dois Portais externos (Solicitante e Avaliador) e cujas Fases 1–10 já foram
implementadas.

> **Nenhum código foi alterado para produzir este relatório.** Todo achado
> abaixo foi verificado por leitura do código real e, quando envolve número,
> por medição reproduzível — os comandos estão no Anexo A. Onde uma suspeita
> não se confirmou, ela foi descartada e não aparece aqui.

---

## 0. Status de execução

**As correções deste relatório foram aprovadas pelo usuário em 2026-08-04 e
serão implementadas em sessão futura.** Nada foi implementado ainda — este
documento é, até segunda ordem, diagnóstico e backlog, não registro do que já
existe no código.

Marcar cada fase abaixo à medida que for concluída (detalhe de cada uma no §9):

| Fase | Conteúdo | Esforço | Risco | Status |
|:---:|---|:---:|:---:|:---:|
| **A** | Higiene de alto retorno — travas de upload, contraste, breakpoint, ordem de colunas, favicon, autocomplete, microcopy | baixo | ~nulo | ⬜ pendente |
| **B** | Acessibilidade estrutural — `<main>`, rótulos, validação, ARIA das abas | médio | baixo | ⬜ pendente |
| **C** | Consistência do design system — cartões, paginação, estado vazio, script do login | baixo/médio | baixo (visual) | ⬜ pendente |
| **D** | Acentuação e microcopy do operador (67 + 27 ocorrências) | médio | baixo ⚠ E2E | ⬜ pendente |
| **E** | Robustez de médio prazo — paginar Arquivo, página de erro, fonte local, filtros de auditoria, impressão | médio | baixo | ⬜ pendente |

**Ordem:** A → B → C → D → E, **um PR por fase**, mesclado assim que suíte +
E2E passarem (decisão do usuário, 2026-08-04). As fases são independentes; A é
a de maior relação ganho/esforço.

**Ao retomar, ler antes:** o **Anexo B** (ordem de serviço: lista de arquivos
por tarefa e as decisões de produto já tomadas), §10 ("o que **não** recomendo
fazer") e §11 (critérios objetivos de aceite).

**Decisões de produto já tomadas — não reperguntar:** passo bloqueado do wizard
deve **bloquear de verdade**; entrega em **um PR por fase, mesclado por mim**;
**as 5 fases estão aprovadas integralmente**. Detalhe no Anexo B.

**Armadilha conhecida da Fase D:** o teste E2E localiza botões por texto exato.
Qualquer rótulo de botão acentuado exige atualizar `PlaywrightTestBase` e
`e2e/pages/*Page.java` no mesmo commit — foi o que aconteceu na Fase 8 do
relatório anterior.

---

## 1. Sumário executivo

O SAUR tem um design system **maduro e bem construído** — mais maduro do que a
maioria dos sistemas públicos desta escala. A paleta institucional passa nos
testes de contraste, os componentes de status são padronizados, há suporte a
`prefers-reduced-motion`, e as decisões difíceis (voto irreversível, duplo
clique em operação lenta) estão protegidas por mecanismos deliberados e bem
documentados.

O problema não é a qualidade do que existe. É a **distribuição desigual**: o
trabalho de UI dos últimos meses foi todo aplicado aos dois Portais externos, e
a área onde a equipe de Urgência Renal passa o dia inteiro — o Painel, a lista
de processos, o Arquivo, Membros, Usuários, Auditoria e a tela de detalhe do
processo — **nunca passou por uma revisão equivalente**.

O sintoma mais claro disso: durante a auditoria encontrei uma classe CSS
(`.stat-card-portal`) que foi criada justamente para eliminar um `style=`
repetido — e ela foi aplicada ao Portal do Solicitante, enquanto o **Painel,
onde esse padrão nasceu, continua repetindo o mesmo trecho inline 8 vezes**. A
melhoria foi feita na cópia, não no original.

**Os cinco achados que eu resolveria primeiro:**

| # | Achado | Por que importa |
|---|--------|-----------------|
| 1 | O atalho "Pular para o conteúdo" está **quebrado em 20 das 25 telas** | É o primeiro elemento de teclado de toda página. Hoje aponta para um endereço que não existe fora dos Portais. |
| 2 | **7 dos 8 formulários de upload não têm trava de duplo envio** | O mecanismo existe e foi escrito por causa de um incidente real de produção (03/08). Não foi aplicado justamente onde a espera é maior. |
| 3 | Texto de etapa pendente com contraste **2.56:1** (mínimo exigido: 4.5:1) | Aparece no cartão "Progresso" de todo processo. É o pior contraste do sistema, e está na informação mais consultada da tela. |
| 4 | **60 rótulos de formulário sem associação** com seu campo | Clicar no rótulo não foca o campo; leitor de tela anuncia campo sem nome. Atinge todos os formulários do operador. |
| 5 | O **Arquivo carrega todos os processos encerrados sem paginação** | É a única tela que só cresce. Hoje com 4 processos é imperceptível; é uma dívida com data marcada. |

Nenhum desses exige redesenho. Os cinco somados são, na estimativa, **menos
esforço que a Fase 6 do relatório anterior** — e três deles são aplicação de
mecanismos que o próprio sistema já tem prontos.

---

## 2. Método e escopo

**O que foi examinado:** os 28 templates Thymeleaf (4.876 linhas), o
`app.css` (984 linhas), os 13 arquivos JavaScript (1.159 linhas), e os
controllers correspondentes quando o comportamento da tela dependia deles.

**Como:** leitura integral dos arquivos de maior superfície
(`layout.html`, `app.css`, `dashboard.html`, `processos/detalhe.html`),
leitura completa das telas menores, e medição automatizada para os achados
quantitativos (contraste, rótulos órfãos, landmarks, acentuação, travas de
formulário). Contraste calculado pela fórmula WCAG 2.1 de luminância relativa,
considerando a composição real de opacidade sobre o fundo.

**Fora de escopo (deliberado):**
- Os dois Portais externos — já cobertos pelo relatório anterior, com Fases
  1–10 implementadas. Só apareço neles quando o achado é da camada comum.
- Regra de negócio, segurança de backend, modelagem — não é um relatório de
  arquitetura.
- Teste com usuário real. Este é um relatório de inspeção; ele aponta onde
  provavelmente dói, não substitui ver a equipe usando o sistema.

**Limite honesto desta análise:** não executei o sistema em navegador nem medi
tempo de carregamento real. Os achados de responsividade vêm da leitura das
classes de grade e das regras de mídia, que é confiável para o tipo de
problema apontado (ordem de colunas, sobreposição de *breakpoint*), mas não
substitui abrir a tela num tablet de verdade.

---

## 3. Diagnóstico por dimensão

| Dimensão | Nota | Leitura |
|---|:---:|---|
| Paleta e tokens de cor | **A** | Todos os 4 pares de *badge* institucional passam em AA. Variáveis bem nomeadas, sem hex solto na maior parte do CSS. |
| Componentes de status | **A** | `status`/`statusRotulo`/`statusNa` são um acerto: nunca cor sozinha, sempre ícone + texto. Documentados no próprio layout. |
| Proteção de ação irreversível | **A−** | Voto do avaliador com modal + confirmação explícita; trava de duplo envio nas 3 operações críticas. Falta estender a uploads. |
| Movimento e preferências | **A** | `prefers-reduced-motion` respeitado, incluindo a animação infinita do passo atual. Raro de ver. |
| Consistência entre telas | **C** | 4 padrões diferentes de "cartão de número"; 2 padrões de paginação; 2 padrões de estado vazio. |
| Acessibilidade estrutural | **D** | Landmarks e rótulos ausentes na maior parte da área do operador; ARIA das abas apontando para o vazio. |
| Estados de erro e validação | **D** | Zero `aria-invalid`, zero destaque visual no campo com erro, página de erro genérica para todos os casos. |
| Responsividade | **C+** | Boa base, mas ordem de colunas errada na faixa de tablet e sobreposição de *breakpoint* em 768px. |
| Privacidade / dependência externa | **C** | Fonte carregada do Google em toda página de um sistema de saúde estadual. |
| Consistência de idioma | **C−** | Acentuação corrigida só nos Portais; 18 arquivos do operador ainda sem acento. |

---

## 4. O que já está bom (não mexer)

Um relatório que só lista defeitos leva a equipe a "consertar" coisas que estão
certas. Estes pontos devem ser **preservados** em qualquer refatoração:

1. **Os tokens de cor passam em contraste.** Medi os quatro *badges*
   institucionais: `badge-rs-blue` 7.08:1, `badge-rs-gold` 5.93:1,
   `badge-rs-green` 5.82:1, `badge-rs-red` 4.78:1 — todos acima de 4.5:1. O
   comentário no `app.css` explicando por que `--rs-chart-blue` existe separado
   do `--rs-blue` (faixa de luminosidade para gráfico categórico) mostra que a
   paleta foi pensada, não escolhida por gosto.

2. **O padrão de status é exemplar.** Ícone + cor + **texto visível sempre**,
   com três variantes semanticamente distintas (concluído / pendente / não se
   aplica). Isso é o que impede o sistema de comunicar estado só por cor — a
   falha de acessibilidade mais comum em painéis administrativos.

3. **A trava de duplo envio (`lock-submit.js`).** O comentário documenta o
   incidente real que a originou (operador clicou duas vezes em "Registrar
   envio", os 3 avaliadores receberam convite duplicado) e explica por que o
   `setTimeout(0)` é necessário. É código com memória institucional. O problema
   é só que ela não foi aplicada em todo lugar (§5.2).

4. **A confirmação do voto do avaliador.** Modal que repete a escolha, exige
   *checkbox* explícito, desabilita o botão no clique, e cai para `confirm()`
   nativo se o Bootstrap falhar — com um `console.error` avisando. É defesa em
   profundidade numa ação irreversível, feita corretamente.

5. **`prefers-reduced-motion`.** Desliga a animação infinita do passo atual e
   neutraliza as entradas com *fade*. Poucos sistemas fazem isso.

6. **Botões só-ícone estão rotulados:** 13 de 14 têm `aria-label`. O trabalho
   da Fase 9 do relatório anterior funcionou — o problema é que parou nos
   Portais.

---

## 5. Achados — Severidade ALTA

### 5.1 O atalho de teclado "Pular para o conteúdo" está quebrado em 20 das 25 telas

**O que acontece.** O `layout.html` coloca, como primeiro elemento focável de
toda página, um atalho `<a href="#conteudo">Pular para o conteúdo</a>`. Ele
serve para quem navega por teclado não precisar tabular pelos ~15 itens do menu
em toda tela. O alvo `id="conteudo"` existe em **5 telas** (os Portais, feitos
na Fase 9). Nas outras **20**, o atalho aponta para um endereço inexistente:
o foco não vai a lugar nenhum.

Pior: **18 dessas 20 telas não têm sequer um elemento `<main>`**. Sem landmark,
um leitor de tela não consegue oferecer "ir para o conteúdo principal" nem como
alternativa.

| Situação | Telas |
|---|---|
| `<main id="conteudo">` correto | 5 (`avaliador/lista`, `avaliador/votar`, `solicitante/lista`, `solicitante/detalhe`, `solicitante/nova`) |
| Tem `<main>`, falta o `id` | 2 (`dashboard`, `error`) |
| Não tem `<main>` nenhum | **18** (todas as demais do operador + `solicitante/indisponivel`) |

**Impacto.** Acessibilidade por teclado degradada em toda a área operacional.
Para um sistema de secretaria estadual de saúde, isso também é exposição em
auditoria de acessibilidade digital (eMAG / WCAG 2.1 AA).

**Correção.** Trocar o `<div class="container...">` de cada tela por
`<main id="conteudo" class="container...">`. É substituição de tag, sem efeito
visual. **Esforço: baixo. Risco: praticamente nulo.**

---

### 5.2 Sete dos oito formulários de upload não têm trava de duplo envio

**O que acontece.** O sistema tem um mecanismo pronto (`lock-submit.js`) que
desabilita o botão e mostra um *spinner* com mensagem durante operações lentas.
Ele está aplicado em 4 formulários: registrar envio, registrar decisão, enviar
resposta ao solicitante, e nova solicitação do Portal.

Os formulários de **upload de arquivo** — que são, por definição, os mais
lentos do sistema (um PDF clínico de até 25 MB por uma conexão de hospital) —
não têm nenhum deles, exceto o do Portal:

| Formulário de upload | Trava? |
|---|:---:|
| `solicitante/nova` (nova solicitação) | ✅ |
| Documento clínico do avaliador (`detalhe:495`) | ❌ |
| Informação complementar (`detalhe:589`) | ❌ |
| Ofício de indeferimento (`detalhe:929`) | ❌ |
| Comprovante SNT (`detalhe:989`) | ❌ |
| Anexo genérico (`detalhe:1104`) | ❌ |
| Voto do avaliador (`avaliador/votar:124`) | ❌ (protegido por outro caminho — ver abaixo) |
| Informação complementar do solicitante (`solicitante/detalhe:73`) | ❌ |

**Impacto.** Durante o upload a tela fica visualmente parada: o botão continua
clicável, sem *spinner*, sem mensagem. É exatamente o cenário que produziu o
incidente de 03/08 documentado no próprio `lock-submit.js` — só que agora numa
operação mais lenta ainda. Um segundo clique pode gerar anexo duplicado.

**Observação sobre o voto do avaliador.** Ele tem proteção própria (modal +
`checkbox` + desabilitar o botão), então não está desprotegido contra duplo
envio. Mas há um detalhe fino: como o `avaliador-votar.js` chama
`form.submit()` diretamente, o evento `submit` não dispara — o que significa
que **adicionar `data-lock-submit` nesse formulário não teria efeito**. Ali a
correção é outra: mostrar o *spinner* no próprio botão do modal antes de
submeter, porque hoje, entre o clique em "Confirmar" e a página navegar, a tela
fica sem nenhum retorno visual na ação mais importante do sistema.

**Correção.** Adicionar `data-lock-submit="Enviando arquivo..."` nos 6
formulários de upload comuns (um atributo cada), e um `spinner` no botão de
confirmação do voto. **Esforço: muito baixo. Risco: nulo.**

---

### 5.3 Contraste insuficiente em texto de uso diário

Medição pela fórmula WCAG 2.1, considerando composição real de opacidade:

| Elemento | Onde aparece | Contraste | AA (4.5:1) |
|---|---|:---:|:---:|
| **Título de etapa pendente** (`--rs-gray-400` sobre branco) | Cartão "Progresso" de **todo** processo | **2.56:1** | ❌ **falha larga** |
| Cabeçalho da tabela de pareceres (`gray-500` sobre `gray-100`) | Card "Respostas dos Avaliadores" | 4.34:1 | ❌ falha por pouco |
| Subtítulo do login (branco 50% sobre azul) | Tela de entrada | 3.35:1 | ❌ falha |
| Rodapé de créditos (`gray-400` a 70% sobre `gray-800`) | Todas as telas | 3.55:1 | ❌ falha |
| Nome do sistema no menu (branco 85%) | Todas as telas | 6.56:1 | ✅ |
| Descrição de etapa (`gray-500` sobre branco) | Cartão "Progresso" | 4.76:1 | ✅ |
| Rótulo de etapa bloqueada (`gray-600`) | Passo a passo | 7.58:1 | ✅ |

**O caso mais grave é o primeiro.** `.timeline-item.pendente .timeline-title`
usa `--rs-gray-400` sobre branco: **2.56:1**, quase metade do mínimo. Isso é o
texto que diz *o que ainda falta fazer no processo* — a informação mais
consultada da tela. E há uma ironia reveladora: o rótulo de passo **bloqueado**
já foi corrigido para `gray-600` (7.58:1), com comentário no CSS explicando
que "o rótulo usa uma cor fixa mais escura em vez de depender da opacidade
herdada, que deixava o texto com contraste baixo demais". **Alguém já
identificou exatamente este problema e o corrigiu num lugar, mas não no outro.**

**Correção.** Trocar `--rs-gray-400` por `--rs-gray-600` no título de etapa
pendente (mesma correção já validada no passo bloqueado); escurecer o cabeçalho
da tabela de pareceres para `gray-600`; trocar `text-white-50` por
`text-white-75` ou equivalente no login. **Esforço: baixo (4 linhas de CSS).
Risco: baixo** — muda tom, não estrutura.

*Nota técnica:* `.wizard-step .wizard-label` também usa `gray-400`, mas esse
valor nunca aparece — todo passo é sempre concluído, atual ou bloqueado, e as
três variantes sobrescrevem a cor. É código morto, não um defeito visível.

---

### 5.4 Sessenta rótulos de formulário sem associação com o campo

**O que acontece.** Dos 78 `<label>` do sistema, apenas 18 têm `for=`. Os
outros 60 **não têm `for=` e também não envolvem o campo** — ou seja, não há
nenhuma ligação entre o rótulo e o controle.

| Tela | Rótulos órfãos |
|---|:---:|
| `processos/detalhe` | 14 |
| `processos/form` | 8 |
| `processos/editar` | 7 |
| `usuarios/form` | 7 |
| `controle-urgencias/form` | 6 |
| `avaliador/votar`, `membros/form`, `usuarios/minha-senha` | 3 cada |
| `login`, `processos/lista`, `solicitante/nova` | 2 cada |
| demais | 1 cada |

**Impacto.** Duas consequências concretas: (a) clicar no rótulo não foca o
campo — comportamento que todo usuário espera e que aumenta a área de clique,
importante em tela sensível ao toque; (b) leitor de tela anuncia "campo de
edição" sem dizer qual, tornando os formulários do operador inutilizáveis por
esse caminho.

**O padrão certo já está no repositório:** `solicitante/nova.html` usa
`th:for` corretamente (5 dos 7 rótulos), e é o arquivo com melhor índice. Basta
replicar.

**Correção.** Adicionar `id` ao campo e `for` ao rótulo. Em campos com
`th:field`, o Thymeleaf já gera o `id` automaticamente — basta o `th:for`.
**Esforço: médio pelo volume (60 pares). Risco: nulo.**

---

### 5.5 As abas da tela de detalhe têm ARIA apontando para o vazio

**O que acontece.** Os 5 painéis do passo a passo declaram
`aria-labelledby="tab-recebimento"`, `"tab-envio"`, `"tab-respostas"`,
`"tab-decisao"`, `"tab-finalizacao"`. **Nenhum desses cinco `id` existe** em
lugar nenhum do arquivo — os elementos de aba (`<a class="wizard-step">`) são
gerados em laço e não recebem `id`.

Além disso, no arquivo inteiro (1.188 linhas) há **uma única** ocorrência de
`aria-selected` ou `aria-controls` — e ela não é do passo a passo. Ou seja: o
componente se declara `role="tablist"`/`role="tab"`/`role="tabpanel"` mas não
fornece nenhuma das propriedades que dão sentido a esses papéis.

**Efeito colateral relacionado.** Os passos marcados como bloqueados recebem
apenas `cursor: not-allowed` no CSS. Não há `preventDefault`, nem
`aria-disabled`, nem `tabindex="-1"` — verifiquei o `processo-detalhe.js` e não
há nada interceptando o clique. **Um passo que parece desabilitado abre
normalmente ao ser clicado.** Não é falha de segurança (os formulários de
dentro são escondidos pelo servidor), mas é o tipo de inconsistência que faz o
operador desconfiar do que a tela está dizendo.

**Correção.** Dar `id` a cada aba (`th:id="'tab-' + ${passo.paneId}"`),
adicionar `aria-selected` e `aria-controls`, e decidir explicitamente o
comportamento do passo bloqueado — ou bloquear de verdade (`aria-disabled` +
`preventDefault`), ou parar de aparentar bloqueio. **Esforço: baixo.
Risco: baixo** (a navegação continua sendo a do Bootstrap).

---

## 6. Achados — Severidade MÉDIA

### 6.1 Quatro padrões diferentes para a mesma coisa: "cartão de número"

O sistema exibe contadores em quatro dialetos visuais distintos:

| Tela | Padrão | Clicável? |
|---|---|:---:|
| Painel | `.stat-card` + **`style=` inline repetido 8×** | sim |
| Portal do Solicitante | `.stat-card` + `.stat-card-portal` (classe) | sim (filtra) |
| Controle de Urgências | `card border-*` + `badge fs-5` | **não** |
| Membros | círculo de 44px com `style=` inline | não |

O ponto mais concreto: **`.stat-card-portal` foi criada no `app.css`
exatamente para eliminar esse `style=` repetido** — o comentário no arquivo diz
"extraído para evitar duplicar o mesmo `style=...` em cada card". Ela foi
aplicada ao Portal do Solicitante e **não** ao Painel, que continua com as 8
repetições. São 52 `style=` inline só no `dashboard.html`, de 128 no sistema.

Consequência funcional, não só estética: nos cartões do Controle de Urgências e
de Membros o usuário **não pode clicar para filtrar**, embora nos outros dois
lugares o mesmo elemento visual seja clicável. O sistema ensina uma expectativa
e a quebra.

**Correção.** Aplicar `.stat-card-portal` no Painel (remove as 8 repetições) e
padronizar Controle de Urgências e Membros no mesmo componente, tornando-os
clicáveis onde houver filtro correspondente. **Esforço: baixo/médio.
Risco: baixo, mas visual — pede conferência humana.**

---

### 6.2 Ordem das colunas errada na faixa de tablet

Na tela de detalhe, a coluna lateral é `col-lg-3 order-2 order-md-1` e a área
de trabalho é `col-lg-9 order-1 order-md-2`. O detalhe: `order-md-1` vale a
partir de 768px, mas `col-lg-3` só divide a tela a partir de 992px.

Resultado, na faixa **768px–991px** (iPad em retrato, o formato mais provável
de uso à beira de uma mesa de trabalho): as colunas ainda estão empilhadas em
largura total, mas a lateral foi promovida para **primeiro**. O operador abre um
processo e precisa rolar por *Progresso → Atalhos → E-mails prontos → Conversa
com o solicitante → Dados* **antes de chegar ao passo 1**.

**Correção.** Trocar `order-md-*` por `order-lg-*`, alinhando a ordem ao mesmo
ponto de quebra da grade. **Esforço: 2 atributos. Risco: baixo.**

### 6.3 Sobreposição de *breakpoint* em exatamente 768px

O `app.css` tem `@media (min-width: 768px)` (linhas 751 e 797) e
`@media (max-width: 768px)` (linha 808). **Em exatamente 768px as duas
regras valem ao mesmo tempo** — e 768px é justamente a largura CSS de um iPad
em retrato.

O próprio arquivo já usa a convenção correta em outro ponto
(`max-width: 991.98px`, linha 867), o que mostra que o padrão é conhecido.

**Correção.** `max-width: 767.98px`. **Esforço: um caractere. Risco: nulo.**

---

### 6.4 Erros de validação não são identificáveis

Contagem em todos os 28 templates:

| Recurso | Ocorrências |
|---|:---:|
| `th:errors` (mensagem de erro) | 19 |
| `is-invalid` (destaque visual no campo) | **0** |
| `invalid-feedback` | **0** |
| `aria-invalid` | **0** |
| `aria-describedby` | **0** |

Um erro de validação hoje aparece como uma linha de texto vermelho pequeno
abaixo do campo. O campo em si **não muda de aparência**, não é marcado como
inválido para tecnologia assistiva, e a mensagem não está ligada a ele. Não há
resumo de erros no topo do formulário.

**Caso específico e mais sensível — o formulário de nova solicitação.** Ele não
usa `BindingResult`: o `SolicitanteController.criar` captura a exceção do
serviço e devolve **uma única mensagem genérica no topo**, sem indicar o campo.
E há um agravante que vale destacar para decisão de produto: os textos digitados
são preservados no retorno, mas **os arquivos anexados não são** — um
`<input type="file">` não pode ser repopulado por limitação do navegador. Como
nada na tela avisa isso, o usuário externo pode reenviar acreditando que os
documentos clínicos continuam anexados. O mesmo vale para o rascunho, que salva
os 4 campos de texto e não os arquivos.

**Correção.** (a) Aplicar `is-invalid` + `invalid-feedback` + `aria-invalid` +
`aria-describedby` no padrão do Bootstrap; (b) no retorno com erro do formulário
de nova solicitação, exibir aviso explícito *"reanexe os documentos clínicos —
por segurança, o navegador não mantém arquivos selecionados"*.
**Esforço: médio. Risco: baixo.** O item (b) sozinho é de esforço muito baixo e
resolve o risco real.

---

### 6.5 O Arquivo carrega tudo, sem paginação

`ArquivoController.listar` executa `findByStatusIn(...)` sem paginação,
carrega **todos** os processos encerrados na memória e filtra a busca em Java.
O comentário no código assume "o conjunto já pequeno dos encerrados".

Hoje isso é verdade (4 processos em produção). Mas o Arquivo é, por definição,
a **única tela do sistema que só cresce** — nada nunca sai dela. Enquanto isso,
`/processos`, que é limitada pelo trabalho ativo, **é paginada em 15**. O
esforço de paginação foi para a tela que não precisa.

Num horizonte de 3–5 anos a algumas centenas de processos por ano, isso vira uma
página com milhares de linhas e uma busca que percorre tudo a cada tecla.

**Correção.** Paginar o Arquivo com o mesmo componente já usado em
`/processos`, e mover o filtro de busca para o banco. **Esforço: baixo.
Risco: baixo.** Não é urgente hoje; é barato agora e caro depois.

---

### 6.6 A página de erro é um beco sem saída para metade dos perfis

Há uma única página para todos os erros. Um 404, um 403 e um 500 mostram a mesma
frase — "Algo deu errado" — mais o número do status. O usuário não recebe
orientação distinta entre "esse endereço não existe", "você não tem permissão" e
"houve uma falha do sistema".

Além disso, o botão de saída é **"Voltar ao painel" apontando para `/`** — e no
`SecurityConfig`, `/` exige `ADMIN` ou `OPERADOR`. Um **avaliador** ou um
**solicitante** que caia em qualquer erro recebe um único botão que o leva a um
403, que renderiza a mesma página de erro, com o mesmo botão. **Dois dos quatro
perfis do sistema não têm saída da tela de erro.**

**Correção.** Mensagem por faixa de status (404 / 403 / 5xx) e destino do botão
conforme o perfil autenticado (`/avaliador`, `/solicitante`, `/`).
**Esforço: baixo. Risco: baixo.**

---

### 6.7 Acentuação: a correção parou nos Portais

A Fase 8 do relatório anterior corrigiu a acentuação dos 6 templates dos
Portais. A medição confirma que **eles estão limpos**. O resto do sistema não:
**67 ocorrências em texto visível, em 18 arquivos**, mais 27 em atributos que o
usuário lê (`title`, `placeholder`, mensagens de confirmação).

| Tela | Ocorrências em texto visível |
|---|:---:|
| `processos/detalhe` | 19 |
| `dashboard` | 9 |
| `layout` (menu, rodapé, status) | 5 |
| `processos/form`, `relatorios/anual`, `relatorios/avaliador` | 4 cada |
| demais 12 arquivos | 1–3 cada |

O caso mais visível é o **menu de navegação**, presente em toda tela:
"Urgencias", "Relatorio anual", "Solicitacoes online", "Usuarios". E os
fragmentos de status compartilhados — "Concluido" / "Pendente" — aparecem em
praticamente todas as telas do operador.

A tela de login ilustra a inconsistência dentro de um mesmo arquivo: o subtítulo
está corretamente acentuado ("Sistema de avaliação de urgência renal"), e três
linhas abaixo a mensagem de erro diz "Usuario ou senha invalidos".

**Ressalva importante, herdada do relatório anterior:** `ResultadoParecer.descricao`
("Favoravel"/"Nao favoravel") foi **deliberadamente mantido sem acento** porque
alimenta PDFs oficiais e auditoria. Essa decisão continua válida — a correção é
só nos templates, com o mesmo padrão de `th:switch` já usado nos Portais.

**Esforço: médio pelo volume. Risco: baixo, com uma armadilha conhecida** — o
teste E2E localiza botões por texto exato. Qualquer texto de botão alterado
exige atualizar `PlaywrightTestBase`/`pages/*Page.java` no mesmo *commit*, como
foi feito na Fase 8.

---

## 7. Achados — Severidade BAIXA

**7.1 Não existe favicon.** `src/main/resources/static/` tem apenas
`brasao.png`, `css/` e `js/`. O `SecurityConfig` libera `/favicon.ico`, mas o
arquivo não existe — toda página gera um 404 silencioso e o navegador mostra o
ícone genérico. Como a VM hospeda 4 sistemas e o operador provavelmente mantém
abas fixadas, um ícone próprio tem valor prático. O logo gota+cruz já existe
como SVG embutido no `layout.html`.

**7.2 Não há folha de estilo para impressão.** Zero `@media print`. Este é um
fluxo de trabalho com ofício, protocolo e papel; imprimir a tela de um processo
hoje sai com menu, passo a passo, conversa, botões e rodapé. Os documentos
oficiais são gerados como PDF (isso está bem resolvido), então a prioridade é
baixa — mas uma regra de impressão de ~20 linhas resolveria.

**7.3 Dois padrões de paginação.** Auditoria mostra "1 / 12" (com total);
Processos mostra apenas "1" (sem total). O usuário de Processos não sabe quantas
páginas existem.

**7.4 Dois padrões de estado vazio.** Painel, Processos e Arquivo usam ícone
grande + frase. Controle de Urgências, Membros e Auditoria usam só a frase.

**7.5 JavaScript embutido no `login.html`.** O `CLAUDE.md` estabelece:
"JavaScript específico fica em `static/js/*.js`, **nunca inline** nos
templates". O `login.html` tem um bloco `<script>` de 12 linhas (mostrar/ocultar
senha). É a única violação dessa regra que encontrei — e o botão também não
atualiza `aria-pressed` ao alternar.

**7.6 Falta `autocomplete` no login.** Sem `autocomplete="username"` e
`autocomplete="current-password"`, gerenciadores de senha e o preenchimento
automático do navegador funcionam de forma degradada. Dois atributos.

**7.7 Microcopy desatualizada no Relatório anual.** O estado vazio diz
*"Ainda não há processos cadastrados. **Cadastre um processo** para gerar o
relatório anual."* — mas o cadastro manual de processo **foi removido em
2026-07-27**; todo processo nasce agora de uma solicitação do Portal. A tela
orienta a fazer algo que não existe mais.

**7.8 A coluna "Designados / Avaliados / Fav." de Membros depende de tooltip.**
Três números em três *badges* de cores diferentes separados por barras; o
significado está só no `title` do cabeçalho — que não aparece em toque e não é
lido de forma confiável. Para leitor de tela, a célula é "0 / 0 / 0".

**7.9 Auditoria não tem nenhum filtro.** Nem por usuário, nem por ação, nem por
período. É a tela usada exatamente quando se está investigando um incidente
específico, e a única navegação disponível é paginar do mais recente para trás.

---

## 8. Privacidade e dependência externa

O `layout.html` carrega a fonte Inter de `fonts.googleapis.com` e
`fonts.gstatic.com` em **toda** página. Três consequências:

1. **Privacidade.** Cada carregamento de página envia o IP do usuário a um
   terceiro fora do país. Num sistema de secretaria estadual de saúde, operado
   por servidores públicos e acessado por equipes de hospitais, isso é matéria
   de LGPD — não porque haja dado de paciente na requisição (não há), mas porque
   é transferência internacional de dado pessoal (IP) sem finalidade necessária.
   Tribunais europeus já trataram o caso equivalente sob o GDPR.
2. **Disponibilidade.** É uma folha de estilo *render-blocking*: se o Google
   estiver lento ou bloqueado pela rede do hospital, a página atrasa a primeira
   pintura.
3. **Custo zero para remover.** Inter em dois pesos `woff2` são ~100 KB
   auto-hospedados, servidos pelo mesmo nginx que já serve o resto, com o
   *cache* por hash de conteúdo que o projeto já tem configurado.

**Recomendação: auto-hospedar a fonte.** Resolve os três pontos de uma vez.
**Esforço: baixo. Risco: baixo** (fallback `system-ui` já está declarado).

---

## 9. Plano de execução sugerido

As fases são independentes entre si e podem ser feitas em qualquer ordem — a
sequência abaixo é por relação esforço/risco/ganho.

### FASE A — Higiene de alto retorno (risco ~nulo)
- `data-lock-submit` nos 6 formulários de upload (§5.2)
- `max-width: 767.98px` (§6.3)
- `order-lg-*` na tela de detalhe (§6.2)
- Contraste: `gray-400` → `gray-600` na etapa pendente e no cabeçalho da tabela (§5.3)
- Favicon (§7.1) · `autocomplete` no login (§7.6) · microcopy do Relatório anual (§7.7)
- Aviso de "reanexe os documentos" no erro da nova solicitação (§6.4b)

> Tudo isso é atributo, uma linha de CSS ou uma frase. Cabe num único PR
> pequeno e é a maior relação ganho/esforço do relatório.

### FASE B — Acessibilidade estrutural
- `<main id="conteudo">` nas 20 telas (§5.1)
- `for`/`id` nos 60 rótulos (§5.4)
- `is-invalid` + `aria-invalid` + `aria-describedby` na validação (§6.4)
- ARIA das abas do detalhe + decisão sobre passo bloqueado (§5.5)

### FASE C — Consistência do design system
- `.stat-card-portal` no Painel; padronizar Controle de Urgências e Membros (§6.1)
- Unificar paginação (§7.3) e estado vazio (§7.4)
- Mover o script do login para `static/js/` (§7.5)
- Coluna de métricas de Membros com rótulo real (§7.8)

### FASE D — Acentuação e microcopy do operador
- 67 ocorrências em 18 arquivos + 27 em atributos (§6.7)
- ⚠ Atualizar os *page objects* do E2E no mesmo *commit*

### FASE E — Robustez de médio prazo
- Paginar o Arquivo (§6.5)
- Página de erro por status e por perfil (§6.6)
- Auto-hospedar a fonte (§8)
- Filtros na Auditoria (§7.9)
- Folha de impressão (§7.2)

---

## 10. O que eu **não** recomendo fazer

Um relatório desta profundidade tende a virar licença para reescrever coisas.
Estes caminhos parecem tentadores e não valem o risco:

1. **Não quebrar o `processos/detalhe.html` em fragmentos agora.** 1.188 linhas
   incomodam, mas é a tela mais coberta por testes e pelo E2E, e a que concentra
   as regras de negócio mais delicadas. Fragmentar é um PR dedicado, com o E2E
   como rede — não um efeito colateral de uma correção de acessibilidade.

2. **Não trocar o Bootstrap nem introduzir framework de front.** O sistema é
   Thymeleaf server-side com JavaScript pontual, e isso está funcionando. O
   `CLAUDE.md` já registra um experimento abandonado nessa direção.

3. **Não "corrigir" `ResultadoParecer.descricao`.** A ausência de acento ali é
   deliberada e documentada: alimenta PDFs oficiais e auditoria. Acentuar o enum
   muda documento oficial.

4. **Não adicionar tema escuro.** Zero sinal de demanda; dobraria a superfície
   de manutenção de uma paleta que hoje está correta.

5. **Não transformar tudo em AJAX.** O padrão POST + *redirect* é adequado e
   previsível aqui. O chat já é AJAX porque precisava ser.

---

## 11. Como verificar que ficou pronto

Critérios objetivos, verificáveis pelos mesmos comandos do Anexo A:

| Fase | Critério de aceite |
|---|---|
| A | 8/8 formulários de upload com trava; nenhum contraste medido abaixo de 4.5:1 nos itens de §5.3 |
| B | 25/25 telas com `<main id="conteudo">`; 0 rótulos órfãos; 0 referências ARIA sem alvo |
| C | 0 `style=` inline em `dashboard.html`; um único padrão de cartão, paginação e estado vazio |
| D | 0 ocorrências da lista de §6.7 em texto visível; suíte + E2E verdes |
| E | Arquivo paginado; página de erro com destino correto para os 4 perfis |

Em todas: **suíte completa verde** (735 testes hoje) e `.\e2e.ps1 -Headless`
sem regressão nova — lembrando a falha pré-existente de SMTP local já
documentada no `CLAUDE.md`.

---

## Anexo A — Comandos de verificação

```bash
# Telas sem <main id="conteudo"> (esperado ao fim da Fase B: nenhuma)
for f in $(find src/main/resources/templates -name "*.html"); do
  grep -q "layout :: navbar" "$f" && ! grep -q 'id="conteudo"' "$f" && echo "$f"
done

# Rótulos órfãos: sem for= e sem envolver o campo
python3 - <<'PY'
import re, glob
p=re.compile(r'<label\b(?![^>]*\bfor\s*=)[^>]*>(.*?)</label>', re.S)
n=sum(1 for f in glob.glob('src/main/resources/templates/**/*.html', recursive=True)
        for c in p.findall(re.sub(r'<!--.*?-->','',open(f,encoding='utf-8').read(),flags=re.S))
        if not re.search(r'<(input|select|textarea)\b', c))
print('rotulos orfaos:', n)
PY

# Formulários de upload sem trava de duplo envio
grep -rn 'enctype="multipart/form-data"' src/main/resources/templates \
  | grep -v 'data-lock-submit'

# Referências ARIA sem alvo (aria-labelledby apontando para id inexistente)
grep -o 'aria-labelledby="[^"]*"' src/main/resources/templates/processos/detalhe.html \
  | sed 's/.*"\(.*\)"/\1/' | sort -u \
  | while read id; do grep -q "id=\"$id\"" src/main/resources/templates/processos/detalhe.html \
      || echo "alvo inexistente: $id"; done

# style= inline por template
grep -ro 'style="' src/main/resources/templates | cut -d: -f1 | sort | uniq -c | sort -rn
```

O cálculo de contraste usado na §5.3 segue a fórmula WCAG 2.1 de luminância
relativa, com composição prévia das camadas de opacidade sobre a cor de fundo
real — não sobre branco, o que produziria números otimistas demais nos casos do
login e do rodapé.

---

---

## Anexo B — Ordem de serviço (para executar sem refazer o diagnóstico)

Lista de arquivos por tarefa, levantada em 2026-08-04. **Reconferir com os
comandos do Anexo A antes de começar** — se o código mudou, a lista muda.

### Decisões de produto já tomadas (não reperguntar)

| Questão | Decisão do usuário (2026-08-04) |
|---|---|
| Passo bloqueado do wizard (§5.5) | **Bloquear de verdade**: clique e teclado ignorados (`preventDefault`, `aria-disabled="true"`, fora da ordem de Tab). O operador não navega para etapas onde não pode agir. |
| Entrega | **Um PR por fase (A–E)**, mesclado por mim assim que suíte + E2E passarem. Permite reverter uma fase isolada se algo ficar estranho na tela. |
| Escopo | **Todas as 5 fases aprovadas integralmente.** Executar em sequência A → B → C → D → E. |

### FASE A — Higiene (PR 1)

**A1 · Contraste** (`app.css`): `.timeline-item.pendente .timeline-title`
`gray-400`→`gray-600`; `.tabela-pareceres thead th` `gray-500`→`gray-600`;
`login.html` `text-white-50`→tom mais claro (2 ocorrências).

**A2 · Trava de upload** — adicionar `data-lock-submit="Enviando arquivo..."`:

| Arquivo | Linha |
|---|---|
| `processos/detalhe.html` | 495, 589, 929, 989, 1104 |
| `solicitante/detalhe.html` | 73 |

⚠ `avaliador/votar.html:124` **não** entra aqui: `avaliador-votar.js` chama
`form.submit()`, que não dispara o evento `submit`. Ali a correção é spinner no
botão `#btnConfirmarVotoFinal` antes do `form.submit()`.

**A3 · Breakpoint** (`app.css:808`): `max-width: 768px` → `767.98px`.

**A4 · Ordem de colunas** (`processos/detalhe.html:84,260`): `order-md-1`/
`order-md-2` → `order-lg-1`/`order-lg-2`.

**A5 · Miudezas**: favicon em `static/` (reusar o SVG gota+cruz do
`layout.html`); `autocomplete="username"`/`"current-password"` em `login.html`;
microcopy de `relatorios/anual.html:28` (não existe mais cadastro manual de
processo); aviso "reanexe os documentos clínicos" no retorno com erro de
`solicitante/nova.html`.

### FASE B — Acessibilidade (PR 2)

**B1 · `<main id="conteudo">` em 20 arquivos** — trocar o `<div class="container...">`
externo por `<main id="conteudo" class="container...">` (`dashboard` e `error`
já têm `<main>`, falta só o `id`):

`arquivo/lista` · `auditoria/lista` · `controle-urgencias/form` ·
`controle-urgencias/lista` · `dashboard` · `error` · `membros/form` ·
`membros/lista` · `processos/detalhe` · `processos/editar` · `processos/form` ·
`processos/lista` · `processos/solicitacoes-online-detalhe` ·
`processos/solicitacoes-online-lista` · `relatorios/anual` ·
`relatorios/avaliador` · `solicitante/indisponivel` · `usuarios/form` ·
`usuarios/lista` · `usuarios/minha-senha`

**B2 · 60 rótulos órfãos** (`for`/`id`; com `th:field` basta `th:for`, padrão já
usado em `solicitante/nova.html`):

| Arquivo | Qtd |
|---|:---:|
| `processos/detalhe` | 14 |
| `processos/form` | 8 |
| `processos/editar` · `usuarios/form` | 7 cada |
| `controle-urgencias/form` | 6 |
| `avaliador/votar` · `membros/form` · `usuarios/minha-senha` | 3 cada |
| `login` · `processos/lista` · `solicitante/nova` | 2 cada |
| `arquivo/lista` · `processos/solicitacoes-online-detalhe` · `usuarios/esqueci-senha` | 1 cada |

**B3 · Validação**: `is-invalid` + `invalid-feedback` + `aria-invalid` +
`aria-describedby` nos 5 forms com `th:errors` (`membros/form`,
`processos/form`, `processos/editar`, `usuarios/form`,
`controle-urgencias/form`).

**B4 · ARIA das abas** (`processos/detalhe.html:289-306`): `th:id="'tab-' +
${passo.paneId}"`, `aria-selected`, `aria-controls` — e **bloquear de verdade**
o passo bloqueado, conforme a decisão registrada acima.

### FASE C — Design system (PR 3)

`.stat-card-portal` no `dashboard.html` (remove os 8 `style=` repetidos, dos 52
do arquivo) · mesmo componente em `controle-urgencias/lista` e `membros/lista`,
clicáveis onde houver filtro · paginação unificada no padrão "N / total" de
`auditoria` · estado vazio unificado com ícone · mover o `<script>` inline de
`login.html` para `static/js/` (regra do CLAUDE.md) · rótulo real na coluna
"Designados / Avaliados / Fav." de `membros/lista`.

### FASE D — Acentuação (PR 4)

18 arquivos, 67 ocorrências em texto visível + atributos. Maiores:
`processos/detalhe` (19) · `dashboard` (9) · `layout` (5 — menu e fragments de
status, aparecem em toda tela) · `processos/form`, `relatorios/anual`,
`relatorios/avaliador` (4 cada) · demais 1–3.

⚠ **Não tocar em `ResultadoParecer.descricao`** (alimenta PDF oficial — §10).
⚠ **Atualizar `e2e/pages/*Page.java` no mesmo commit** se algum texto de botão
mudar: o E2E localiza por texto exato.

### FASE E — Robustez (PR 5)

Paginar `/arquivo` (`ArquivoController` + filtro no banco) · página de erro por
faixa de status e destino por perfil (hoje é beco sem saída para AVALIADOR e
SOLICITANTE) · auto-hospedar a fonte Inter · filtros na Auditoria · `@media print`.

### Checagem obrigatória em cada PR

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
mvn -o test                 # 735 testes hoje, exigir 0 falhas
```
Mais o E2E antes de mesclar as fases que tocam texto de botão (D) ou estrutura
de formulário (B). Falha conhecida e **pré-existente** do E2E local:
`FluxoCompletoProcessoIT` passo 5, por falta de SMTP configurado na máquina —
documentada no `CLAUDE.md`, não é regressão.

---

*Relatório produzido por inspeção de código em 2026-08-04. Nenhum arquivo de
código foi alterado. Os achados de §5 e §6 foram confirmados individualmente;
suspeitas que não se confirmaram na verificação foram descartadas e não constam
do documento.*
