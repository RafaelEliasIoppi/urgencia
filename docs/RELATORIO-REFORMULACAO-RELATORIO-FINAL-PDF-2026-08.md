# Relatório de diagnóstico e proposta de reformulação — Relatório Final (PDF)

**Data:** 2026-08-06 · **Tipo:** diagnóstico documental + proposta de direção
(**nenhuma linha de código de produção foi alterada para produzir este
documento**)

**Atualização de 2026-08-06 (mesmo dia) — Frentes 1 e 2 do R1/R3
implementadas, branch `fix/relatorio-final-pdf-conteudo-e-paleta` (PR aberto,
sem merge automático — mesma convenção do projeto para documento oficial):**
- **Frente 1 (conteúdo, achados A1/A3/A4 + parte de A5/A6):** seção "3.
  Decisão final" deixou de imprimir o status de tramitação como se fosse a
  decisão em processo não finalizado (A1); a frase da regra passou a
  explicar a exceção do Coordenador da CET-RS em vez do texto genérico
  contraditório (A3); parecer impedido ganhou rótulo próprio, tanto na
  tabela de pareceres quanto na tabela de avaliadores da capa (A4);
  `votadoPor`/`dataHoraVoto`/`origem` do parecer e `numeroOficio`/
  `dataEnvioSnt` do processo passaram a aparecer no documento (parte de
  A5/A6). **A2, A7, A8, A9, A10, A11, A12 continuam em aberto** (fora do
  escopo aprovado nesta rodada). 6 testes novos em `RelatorioServiceTest`.
- **Frente 2 (paleta, achado 6.1):** `PdfRelatorioBuilder.AZUL` trocado de
  `#0D6EFD` (Bootstrap) para `#1A4D8F` (`--rs-blue` institucional). Só essa
  constante — `CINZA`/`VERDE_ESCURO`/`VERMELHO` (também defaults do
  Bootstrap) e o Relatório Anual/Relatório do Avaliador (que replicam a
  paleta em classes próprias) **não foram tocados** nesta rodada (decisão 6
  da §10 permanece em aberto, não confirmada).
- **Não implementado nesta rodada:** R2 (acentuação), R4 (tipografia/cor
  comedida), R5 (higiene de página/divisórias), R6 (capa/rótulo parcial) e
  P11 (o retrato guardado, A8) — todas as decisões 2, 3, 4, 5, 7 e 8 da §10
  continuam pendentes de aval explícito do dono do produto.

**Escopo:** o **Relatório Final do Processo de Urgência Renal** — o PDF gerado
por `RelatorioService` + `PdfRelatorioBuilder` (`src/main/java/br/gov/saude/
sgpur/service/`), servido por `GET /processos/{id}/relatorio`
(`ProcessoAnexoController.relatorio`), gravado como anexo
`TipoAnexo.RELATORIO_FINAL` por `DecisaoFinalService.gerarDocumentos` e
incluído como `Relatorio-Final.pdf` no dossiê ZIP
(`ExportacaoProcessoService`).

**Fora de escopo, explicitamente:** o **Relatório Anual**
(`RelatorioAnualService`), o **Relatório do Avaliador**
(`RelatorioAvaliadorService`), o **Ofício de Indeferimento** (`OficioService`)
e o **material anonimizado enviado aos avaliadores**
(`SolicitacaoAvaliadorService`). Os quatro aparecem aqui **apenas como
referência de consistência** — eles compartilham timbre, texto institucional
(`PdfCabecalhoStamper.NOME_INSTITUICAO`) e, como se verá na §6.1, até as
constantes de cor, hoje triplicadas. Nenhuma fase deste plano altera esses
documentos, mas a §7.1 discute o que fazer com o código compartilhado.

---

## 1. Sumário executivo

O Relatório Final **funciona** e é tecnicamente sólido: gera PDF válido, mescla
os anexos reais, trata anexo ausente/corrompido sem quebrar, carimba cabeçalho
e numeração em todas as páginas (inclusive nas páginas de anexo escaneadas e
rotacionadas, o que é um cuidado acima da média) e limpa metadados. Nada aqui
é um resgate de código ruim.

O problema é outro, e tem duas camadas:

**Camada 1 — o documento diz coisas erradas ou incompletas.** Esta é a parte
grave, e não é estética. Verificado nos três PDFs reais gerados para este
relatório (§3):

1. Num processo ainda **não decidido**, a seção *"3. Decisão final"* imprime
   **"Resultado: ENVIADO"** em destaque de 13pt — anunciando um status de
   tramitação como se fosse a decisão. A capa do **mesmo documento** trata o
   mesmo caso corretamente ("Em andamento"). Duas respostas diferentes para a
   mesma pergunta, no mesmo PDF.
2. A frase da regra é **fixa em "2 de 3"**. Num processo deferido pela
   **exceção do coordenador CET-RS** (1 voto favorável basta —
   `ProcessoValidator.favoraveisNecessariosParaDeferir`), o documento de
   arquivo passa a registrar *"Favoráveis: 1 (regra: 2 de 3 defere o
   processo)"* ao lado de *"Resultado: DEFERIDO"* — ou seja, **o registro
   oficial documenta uma aparente violação da própria regra**, num processo
   perfeitamente regular.
3. Um parecer **impedido** por conflito de interesse (`Parecer.impedido`,
   quando o avaliador é o próprio solicitante) aparece como *"Pendente"* ou
   *"Dispensado pela maioria"* — o documento perde exatamente o fato que
   protege a lisura do julgamento.
4. **Nada da trilha de auditoria do voto entra no relatório**: `votadoPor`,
   `dataHoraVoto` e `origem = AVALIADOR_SISTEMA` existem em `Parecer` e são,
   por decisão do projeto (2026-07-27), **o que substituiu o antigo anexo
   comprobatório do parecer**. O documento que deveria carregar essa prova
   mostra só a data.
5. **`numeroOficio` (`NNNN/AAAA`) e `dataEnvioSnt` não aparecem em lugar
   nenhum** do relatório, embora sejam os dois identificadores de protocolo do
   desfecho.

**Camada 2 — a aparência é a de um relatório de painel administrativo, não a
de um documento oficial.** A causa é identificável e não é falta de capricho:
**a paleta do PDF é a paleta padrão do Bootstrap 5, não a institucional.**
`AZUL = new Color(13, 110, 253)` é `#0D6EFD`, o `$primary` do Bootstrap; o azul
da Central de Transplantes é `--rs-blue: #1A4D8F`. O mesmo vale para o verde, o
vermelho, o cinza e a borda — os cinco são defaults de framework web. Aplicados
como **cinco a sete barras sólidas de largura total por página**, produzem um
documento saturado, com contraste de **4,50:1** (branco sobre `#0D6EFD` — no
limite exato da WCAG AA), quando a cor institucional daria **8,39:1**.

Somando os dois: **um documento que sai da instituição, vai para o dossiê de
auditoria e é o registro de arquivo do processo administrativo está impresso na
cor padrão de um framework de front-end e mistura, na mesma página, texto
acentuado e não acentuado.**

Esse último ponto merece destaque porque desfaz a hipótese mais provável:
**não é limitação técnica.** A seção *"4. Andamento do processo"* já imprime
"Envio aos 3 **médicos**", "**Decisão** final", "**Ofício** n**º** 0142/2026"
corretamente — porque esses textos vêm de `EtapaFluxo`, acentuado desde
2026-08-05. Tudo o mais no documento é literal Java sem acento. **Acentuar o
resto custa zero: nenhuma fonte nova, nenhuma dependência** (§7.4).

**A proposta (§7) não troca de biblioteca e não muda a fonte.** OpenPDF
continua; Helvetica continua (decisão já tomada pelo dono do produto em
2026-08-03, ver §4.3). São seis movimentos, dos quais os dois primeiros —
corrigir o conteúdo e trocar cinco constantes de cor — respondem pela maior
parte do ganho e são de risco baixo.

**Alerta metodológico (§9):** `RelatorioServiceTest` verifica que o PDF começa
com `%PDF`, que contém as iniciais no cabeçalho e que certos textos aparecem
na extração. **Nenhuma asserção do projeto olha cor, fonte, tamanho,
espaçamento, quebra de página ou ordem visual.** A suíte pode ficar
inteiramente verde com o documento visualmente destruído. Toda fase deste plano
prescreve **gerar o PDF e olhar**.

---

## 2. O que este relatório NÃO é

| Assunto | Situação |
|---|---|
| Relatório Anual (`RelatorioAnualService`) | **Fora de escopo.** Documento diferente, outro público. |
| Relatório do Avaliador (`RelatorioAvaliadorService`) | **Fora de escopo.** |
| Ofício de Indeferimento (`OficioService`) | **Fora de escopo** como documento. Usado aqui só como *referência de qualidade já estabelecida* — é o único documento do sistema com acentuação correta e texto formal, por decisão explícita de 2026-08-04. |
| Material anonimizado aos avaliadores (`SolicitacaoAvaliadorService`) | **Fora de escopo.** Regra de imparcialidade intocada. |
| `PdfCabecalhoStamper` | **Compartilhado** com o Relatório Anual. Qualquer mudança nele afeta os dois — tratado como decisão explícita (§10, decisão 5). |
| Item 8 do `RELATORIO-OFICIO-COMPROVANTE-SNT-2026-08.md` (ofício ao SNT) | Continua **não implementado** e **não é** este documento. |

**Nada aqui muda regra de negócio.** Maioria simples, exceção do coordenador,
pausa por `SOLICITA_INFORMACAO`, travas de processo encerrado, whitelist de
`TipoAnexo` do Portal do Solicitante e imparcialidade do avaliador permanecem
exatamente como estão. As correções da §7.5/§7.6 **passam a exibir** regras que
já existem; nenhuma delas altera o cálculo de nada.

**Confidencialidade — restrição que atravessa todo o plano.** O Relatório Final
contém **nome completo do paciente**, dados clínicos e a **cópia integral de
todos os anexos**. Ele **não** é exposto ao Portal do Solicitante (cuja
whitelist é apenas `COMPROVANTE_SNT` e `OFICIO_INDEFERIMENTO`) nem ao Portal do
Avaliador. É um documento **interno / de arquivo / de dossiê**. Nenhuma fase
deste plano pode criar rota nova de download, alargar whitelist ou levar esse
PDF para perto de um portal externo.

---

## 3. Método

Não há como diagnosticar um PDF lendo o Java que o gera — o OpenPDF tem
comportamento de layout (quebra de tabela, espaçamento, orfandade de seção) que
só se confirma no resultado. Portanto:

1. Foram lidos integralmente `RelatorioService.java` (247 linhas),
   `PdfRelatorioBuilder.java` (420), `PdfCabecalhoStamper.java` (347) e
   `OficioService.java` (261), mais `RelatorioServiceTest`, `EtapaFluxo`,
   `EstadoEtapa`, `Parecer`, `ProcessoValidator`, `DecisaoFinalService` e
   `ExportacaoProcessoService`.
2. Foi escrito um **gerador temporário** (`ZzGerarPdfExemploTest`, JUnit +
   Mockito, no mesmo molde de `RelatorioServiceTest`) que monta três processos
   realistas — com 3 avaliadores nomeados, justificativas longas, observações,
   anexo PDF real em disco e anexo não-PDF — e grava os PDFs em disco.
   **O arquivo foi apagado ao final**; ele não faz parte da suíte e não está no
   repositório (`git status` limpo).
3. Os três PDFs foram **abertos e olhados página a página** (renderização via
   `pdftoppm`), não inferidos do código.

| Arquivo gerado | Processo | Páginas |
|---|---|---|
| `relatorio-final-exemplo.pdf` | **DEFERIDO**, 2 favoráveis + 1 sem voto, 1 anexo PDF + 1 anexo PNG | 5 |
| `relatorio-final-indeferido.pdf` | **INDEFERIDO**, 2 não favoráveis + 1 favorável, motivo longo, ofício `0142/2026` | 3 |
| `relatorio-final-andamento.pdf` | **ENVIADO** (nenhum parecer ainda) — o documento no seu estado mais pobre | 2 |

Ficam em
`/tmp/claude-1000/-workspaces-urgencia/a32ce583-e3fe-4952-aaf3-274a4eca4b08/scratchpad/`.
Todo achado abaixo cita em qual deles foi observado.

---

## 4. Contexto de uso — para que serve este documento

### 4.1 Quem recebe

Três caminhos, todos **internos**:

- **Anexo automático do processo** (`DecisaoFinalService.gerarDocumentos`):
  gravado como `TipoAnexo.RELATORIO_FINAL` no instante em que a decisão é
  registrada. É o registro de arquivo.
- **Download sob demanda** pelo operador (`GET /processos/{id}/relatorio`,
  botão "Relatório Final (PDF)" no card de Atalhos), **regenerado ao vivo** a
  cada clique.
- **Dossiê ZIP** (`ExportacaoProcessoService`), como `Relatorio-Final.pdf` na
  raiz — o pacote que se entrega quando alguém de fora pede "o processo
  inteiro" (auditoria, jurídico, prestação de contas).

O padrão de qualidade a perseguir, portanto, **não** é o de uma tela de
sistema: é o de uma **peça de processo administrativo** — algo que pode ser
impresso, autuado, fotocopiado e lido por alguém que nunca usou o SAUR e
precisa entender, sozinho, o que aconteceu e por quê.

### 4.2 A régua interna já existe: o Ofício

O `OficioService` estabeleceu, em 2026-08-04, o padrão do "documento formal que
sai da instituição": acentuação correta, cidade e data por extenso, numeração
própria, destinatário, fecho e assinatura configurável, sem nenhum placeholder
literal. Seu javadoc é explícito:

> *"este texto é o único do sistema que sai da instituição como documento
> formal, então usa acentuação correta"*

**O Relatório Final também sai da instituição** (vai no dossiê) e **não** segue
esse padrão. A frase acima descreve com precisão o que o Ofício é; ela deixou
de descrever com precisão o conjunto no momento em que o Relatório Final passou
a ir para o dossiê de auditoria.

Consistência que se perde hoje, medida diretamente:

| Elemento | Ofício | Relatório Final |
|---|---|---|
| Nome do órgão | `NOME_INSTITUICAO` (fonte única) | `NOME_INSTITUICAO` (fonte única) ✅ |
| Unidade | "URG**Ê**NCIA RENAL" | "URG**E**NCIA RENAL" ❌ |
| Acentuação do corpo | completa | **mista** (§6.4) ❌ |
| Cidade/data | por extenso, configurável | ausente ❌ |
| Numeração própria | `numeroOficio` impresso | **não impresso** ❌ |
| Assinatura | `app.email.assinatura` | ausente ❌ |

### 4.3 Trabalho anterior — e uma decisão que precisa ser reconfirmada

O commit `0a425f3` (2026-08-03, *"wip: checkpoint — convite duplicado +
melhorias visuais do relatorio"*) já executou "as fases 1 e 2" de um plano que
**nunca foi escrito em `docs/`** (vivia num scratchpad de sessão, hoje
perdido). O que sobreviveu dele está nos comentários do código e é bom
trabalho: a justificativa do parecer subiu de 8pt cinza-claro para 9pt preto,
o resultado da decisão ganhou destaque (`linhaDestaque`), a margem superior foi
ajustada aos 55pt do carimbo, e a cor de "título de bloco" foi unificada (antes
a barra de seção era azul e o cabeçalho de tabela era cinza, sem regra).

Aquele commit registra três decisões do dono do produto:

> *"Decisões do usuário: manter a paleta atual, manter Helvetica, capa só por
> protótipo."*

**"Manter Helvetica" continua valendo e este relatório concorda** (§10,
decisão 2). **"Manter a paleta atual" precisa ser reconfirmado**, porque é
razoável supor que "a paleta atual" tenha sido entendida como *a paleta do
SAUR* — e ela não é: é a do Bootstrap (§6.1). A decisão 1 da §10 apresenta a
evidência para que a escolha seja feita com a informação correta.

---

## 5. Achados de **conteúdo** — o documento diz coisa errada ou incompleta

Esta é a seção que importa mais. Um documento feio que diz a verdade serve;
um documento bonito que registra "regra: 2 de 3" ao lado de um deferimento por
1 voto, não.

### A1 — "Decisão final: ENVIADO" num processo sem decisão
*Observado em `relatorio-final-andamento.pdf`, p. 2.*

`RelatorioService` (l. 186) faz `linhaDestaque(t3, "Resultado",
p.getStatus().getDescricao(), cor)` **incondicionalmente**. Num processo
`ENVIADO`, a seção intitulada **"3. Decisão final"** imprime, no maior destaque
do corpo do documento (13pt, negrito, caixa alta), a palavra **"ENVIADO"**.

O mais revelador: a **capa** do mesmíssimo PDF trata o caso corretamente —
`PdfRelatorioBuilder.adicionarCapa` (l. 252) testa `isFinalizado()` e escreve
*"Em andamento"* em itálico cinza. **A regra existe, está escrita, e não foi
aplicada na seção 3.** É o tipo de divergência que só aparece olhando o PDF
inteiro, porque cada trecho isolado parece correto.

### A2 — Um documento chamado "RELATÓRIO FINAL" pode ser emitido a qualquer momento
`GET /processos/{id}/relatorio` não tem guarda de status. Qualquer processo,
inclusive recém-criado, produz um PDF cujo título é **"RELATORIO FINAL -
PROCESSO DE URGENCIA RENAL"**.

Há precedente direto e recente no projeto: em 2026-08-04, `GET
/processos/{id}/oficio` passou a **recusar com 400 fora de `INDEFERIDO`**,
exatamente porque *"antes esta URL gerava um 'Ofício de Indeferimento' para
qualquer processo — inclusive um Deferido —, produzindo um documento que
contradiz a decisão do processo"* (javadoc do controller). O Relatório Final
tem o mesmo problema e não recebeu o mesmo tratamento.

**Não recomendo bloquear** (o operador tem uso legítimo de imprimir o andamento
parcial). Recomendo **rotular**: `RELATÓRIO PARCIAL — PROCESSO EM ANDAMENTO`
quando `!isFinalizado()`. É decisão de produto (§10, decisão 3).

### A3 — A frase da regra é fixa em "2 de 3" e pode contradizer a decisão registrada
*Observado em ambos os exemplos decididos.*

```java
"Favoraveis: " + processoService.contarFavoraveis(p) + " (regra: "
    + ProcessoService.FAVORAVEIS_PARA_DEFERIR + " de "
    + ProcessoService.AVALIADORES_POR_PROCESSO + " defere o processo)."
```

Três problemas em uma linha:

- **Ignora a exceção do coordenador.** `ProcessoValidator.
  favoraveisNecessariosParaDeferir(p)` devolve **1** quando o coordenador
  CET-RS votou favorável — e é essa função que o `decidir` usa de verdade. O
  relatório usa a constante crua. Resultado num deferimento por coordenador:
  **"Favoráveis: 1 (regra: 2 de 3 defere o processo)"** logo abaixo de
  **"Resultado: DEFERIDO"**. O documento de arquivo do processo passa a ser a
  prova aparente de que a regra foi violada. Numa auditoria, é exatamente o
  tipo de linha que gera pedido de esclarecimento.
- **Não menciona o coordenador em lugar nenhum.** `getRotulo()` é
  `"INSTITUIÇÃO - Nome"`; `MembroUrgenciaRenal.coordenador` não aparece no PDF.
  A tela de detalhe exibe o badge "Deferido pelo Coordenador da CET-RS"
  (`ProcessoService.deferidoPeloCoordenador`); o documento oficial, não.
- **É assimétrica.** Num **indeferimento**, o documento informa quantos foram
  favoráveis e explica a regra de **deferimento** — nunca cita
  `DESFAVORAVEIS_PARA_INDEFERIR`. Em `relatorio-final-indeferido.pdf` lê-se
  *"Favoráveis: 1 (regra: 2 de 3 defere o processo)"* num documento cujo
  desfecho é INDEFERIDO. O leitor precisa fazer a conta de cabeça.

### A4 — Parecer impedido some
`Parecer.impedido` marca o avaliador que é **o próprio solicitante daquele
processo** (conflito de interesse). O relatório nunca lê esse campo: um membro
impedido cai no `else` e é impresso como **"Pendente"** ou **"Dispensado pela
maioria"**.

Perde-se justamente o fato que demonstra que o conflito foi identificado e
tratado — e, pior, o documento sugere omissão do avaliador onde houve
impedimento regular.

### A5 — A prova do voto não entra no documento
`Parecer` guarda `origem` (`AVALIADOR_SISTEMA`), `dataHoraVoto` e `votadoPor`.
Desde 2026-07-27 esse registro autenticado é, textualmente, **o que substituiu
o anexo comprobatório do parecer** — o requisito de anexo foi removido porque
*"o registro autenticado (usuário + `dataHoraVoto` + IP no log de auditoria)
substitui o anexo"*.

O relatório mostra apenas `dataResposta` (uma `LocalDate`). O documento
destinado a comprovar o processo não carrega a prova que passou a valer no
lugar do comprovante. Também ficam de fora `dataEnvio` (que permitiria ler o
tempo de resposta de cada avaliador direto do documento) e `ultimoLembreteEm`.

### A6 — `numeroOficio` e `dataEnvioSnt` não aparecem
Ambos existem em `Processo` desde 2026-08-04 e são os identificadores de
protocolo do desfecho. A seção 3 imprime *"Data de emissão do ofício"* e
*"Data de envio do ofício"* mas **não o número do ofício**; e não há nenhuma
linha de "Data de envio ao SNT" — confirmado em
`relatorio-final-exemplo.pdf` p. 2, que tem `dataEnvioSnt` preenchida e não a
exibe.

### A7 — Linhas estruturalmente inaplicáveis, sempre visíveis
*Observado em `relatorio-final-exemplo.pdf` (DEFERIDO), p. 2.*

A seção 3 é uma lista fixa. Num deferimento, três das seis linhas são
permanentemente `-`: "Motivo do indeferimento", "Data de emissão do ofício",
"Data de envio do ofício". Num indeferimento, sobraria a linha do SNT (se ela
existisse, ver A6). Metade da seção mais importante do documento é ruído
previsível.

### A8 — O anexo `RELATORIO_FINAL` gravado é um retrato que envelhece
`DecisaoFinalService.gerarDocumentos` gera e anexa o relatório **no instante da
decisão**. Mas o ofício de indeferimento e o comprovante SNT são anexados
**depois** — e a resposta ao solicitante, depois ainda. Consequência estrutural:

- o anexo `RELATORIO_FINAL` guardado **nunca** contém o ofício nem o
  comprovante SNT mesclados;
- sua seção 4 registra as etapas finais como **pendentes**, para sempre;
- `GET /processos/{id}/relatorio` regenera ao vivo e mostra o estado **atual** —
  ou seja, **o anexo e o download divergem**, e ninguém é avisado disso;
- **o dossiê ZIP leva os dois**: `Relatorio-Final.pdf` na raiz (ao vivo) e
  `Anexos/NN - Relatorio final do processo - relatorio-processo-XX-YYYY.pdf`
  (o retrato antigo), porque `montarDossie` não filtra `RELATORIO_FINAL` da
  lista de anexos. Dois arquivos com o mesmo nome funcional e conteúdo
  diferente no mesmo pacote entregue a um auditor.

É o achado de maior consequência prática desta seção e **exige decisão de
produto** (§10, decisão 4), não uma correção óbvia: regerar ao final? não
anexar e sempre gerar ao vivo? datar o retrato no próprio documento?

### A9 — Os anexos mesclados não têm divisória nem correlação com o índice
*Observado em `relatorio-final-exemplo.pdf`, p. 4.*

A seção 5 lista Tipo/Arquivo/Data. Os anexos são concatenados logo depois, **sem
nenhuma folha divisória** e **sem indicação de página inicial**. Num processo
real, com 30–60 páginas de exames escaneados, é impossível saber onde termina
um anexo e começa o outro, ou em que página está o que a lista prometeu. O
único fio é o carimbo do topo, que é **idêntico em todas as páginas**.

Curiosamente, o mecanismo já existe: `adicionarPaginaAviso` produz páginas
intercaladas para anexo ausente/corrompido/não-PDF. Falta usá-lo para o caso
normal.

### A10 — `"N do Processo:"`
`PdfRelatorioBuilder` l. 202. Falta o `º`. Aparece na capa de todos os
relatórios já emitidos. Correção de um caractere.

### A11 — O fecho aparece no meio do documento
*Observado em `relatorio-final-exemplo.pdf`: o rodapé "Documento gerado
automaticamente pelo SAUR…" está na **página 3 de 5**.*

O parágrafo de encerramento é escrito no fim do **sumário**, e os anexos são
mesclados **depois**. Quem folheia vê o documento "terminar" e depois continuar
por mais duas páginas.

### A12 — Dois carimbos de emissão, em dois formatos
A capa diz *"Documento gerado pelo SAUR em 06/08/2026"* (`LocalDate`); a página
seguinte diz *"Emitido em 06/08/2026 15:12"* (`LocalDateTime`). Mesmo fato,
duas redações, duas precisões, páginas adjacentes.

---

## 6. Achados de **forma** — por que parece um painel, não um documento

### 6.1 A paleta é a do Bootstrap, não a da Central de Transplantes

O achado estrutural desta seção. `PdfRelatorioBuilder` l. 34–38:

| Constante | Valor | O que é | Institucional (`app.css`) |
|---|---|---|---|
| `AZUL` | `#0D6EFD` | `$primary` do **Bootstrap 5** | `--rs-blue: #1A4D8F` |
| `CINZA` | `#6C757D` | `$secondary`/`$gray-600` do Bootstrap | `--rs-gray-600: #475569` |
| `CINZA_BORDA` | `#DEE2E6` | `$gray-300` do Bootstrap | — |
| `VERDE_ESCURO` | `#198754` | `$success` do Bootstrap | `--rs-green: #2D8546` / `-dark: #1F6B36` |
| `VERMELHO` | `#DC3545` | `$danger` do Bootstrap | `--rs-red: #C62828` / `-dark: #8B1A1A` |

Nenhum desses cinco valores existe em `app.css`. O documento oficial da
instituição é impresso nas cores default de um framework de front-end — e,
detalhe, de um framework que o próprio SAUR **customiza** justamente para não
parecer genérico.

**Não é só estética: é contraste.** Calculado pela fórmula de luminância
relativa da WCAG 2.x (Anexo B):

| Combinação usada hoje | Contraste | AA (4,5:1) |
|---|---|---|
| branco sobre `#0D6EFD` (barra de seção 11pt, cabeçalho de tabela 9pt) | **4,50:1** | no limite exato |
| `#198754` "Favorável" 9pt sobre branco | **4,54:1** | por um triz |
| `#DC3545` "Não favorável" 9pt sobre branco | **4,53:1** | por um triz |
| `#6C757D` 8pt (rodapés, "Favoráveis: N") sobre branco | **4,69:1** | por um triz |

| Com a paleta institucional | Contraste |
|---|---|
| branco sobre `--rs-blue` `#1A4D8F` | **8,39:1** |
| `--rs-green-dark` `#1F6B36` sobre branco | **6,53:1** |
| `--rs-gray-600` `#475569` sobre branco | **7,58:1** |

Ou seja: **a troca de paleta praticamente dobra a legibilidade de cada elemento
colorido**, num documento feito para ser impresso e fotocopiado — onde 4,5:1
degrada rápido. O argumento de identidade visual é o menos forte dos dois.

**Agravante — a paleta está triplicada.** As mesmas constantes, com os mesmos
valores, aparecem copiadas em `PdfRelatorioBuilder`, `RelatorioAnualService` e
`RelatorioAvaliadorService` (e `CINZA` também em `SolicitacaoAvaliadorService`).
É exatamente o problema que `PdfCabecalhoStamper.NOME_INSTITUICAO` resolveu para
o **texto** institucional — cujo javadoc diz, textualmente, que existe *"para
evitar o que já aconteceu uma vez: um documento ficar com o nome do órgão
desatualizado enquanto os outros já tinham sido corrigidos"*. A cor tem hoje o
problema que o texto já teve.

### 6.2 Saturação: o documento é uma sequência de faixas azuis
*Observado em todas as páginas de sumário dos três exemplos.*

Cada página 2 tem **cinco barras sólidas de largura total** em `#0D6EFD`
(seções 1–5) mais **três a quatro cabeçalhos de tabela** na mesma cor cheia.
Numa página A4 de corpo 9pt, isso é muita tinta: o olho é atraído para os
rótulos organizacionais ("1. Dados da solicitação") e não para o conteúdo.

Em impressão P&B, todas essas faixas viram **cinza-escuro chapado** com texto
branco vazado — o pior caso de legibilidade em fotocópia.

### 6.3 Tipografia: corpo de 9pt e nove tamanhos sem regra

Inventário completo (Helvetica, standard-14, sem incorporação):

| Papel | Tamanho | Onde |
|---|---|---|
| Título do documento (capa) | 16 bold | `fTituloDoc` |
| Título do sumário | 15 bold azul | `fTitulo` |
| "URGENCIA RENAL" (capa) | 14 bold azul | `fUrgencia` |
| Resultado em destaque | 13 bold colorido | `linhaDestaque` |
| Nome do órgão (capa) | 13 bold | `fOrgao` |
| Secretaria (capa) | 12 | `fSubOrgao` |
| Barra de seção | 11 bold branco | `fSecao` |
| Cabeçalho carimbado | 10 | `PdfCabecalhoStamper` |
| Rótulo/valor da capa | 10 | `fRotulo`/`fValor` |
| Numeração de página | 9 | `PdfCabecalhoStamper` |
| **Corpo de todas as tabelas** | **9** | `linha`, `celula`, `cabecalho` |
| Justificativa do parecer | 9 itálico | `RelatorioService` l. 158 |
| Subtítulo / "Favoráveis: N" / fecho | 8–9 cinza | vários |

Duas leituras:

- **9pt é pequeno para o corpo de um documento administrativo impresso.** A
  convenção de peça de processo é 10–12pt. O Ofício, aliás, usa **11pt** no
  corpo — outra inconsistência com a régua interna (§4.2).
- **Nove tamanhos entre 8 e 16 sem escala declarada.** 12 e 13 aparecem uma vez
  cada, em papéis diferentes; 15 e 16 convivem como "título" em páginas
  vizinhas. Não há degrau reconhecível, e é isso que produz a sensação de
  documento montado por acréscimo.

### 6.4 Acentuação misturada dentro da mesma página
*Observado em todas as páginas 2.*

Na mesma página convivem:

- **acentuado** — seção 4: "Envio aos 3 **médicos**", "Respostas dos
  **médicos**", "**Decisão** final", "**Ofício** n**º** 0142/2026 anexado",
  "comprovante de **inserção** no SNT" (vêm de `EtapaFluxo`, acentuado desde
  2026-08-05);
- **sem acento** — todo o resto: "1. Dados da solicita**cao**",
  "Observa**coes**", "2. Pareceres dos **medicos**", "3. Deci**sao** final",
  "5. Rela**cao** de anexos", "**Favoravel**", "**Nao** favoravel",
  "Situa**cao**", "**Pagina** 1 de 5", "URG**E**NCIA RENAL",
  "N do Processo".

**A conclusão técnica importa mais que o achado:** como a seção 4 renderiza
acentos corretamente, está provado que `FontFactory` + Helvetica no encoding
padrão (WINANSI/Cp1252) **já suporta a acentuação do português**. Acentuar o
documento inteiro **não exige fonte nova, não aumenta o JAR e não muda uma
linha de dependência**.

Uma ressalva de escopo: `ResultadoParecer.getDescricao()` ("Favoravel"/"Nao
favoravel") é **deliberadamente** sem acento e alimenta cinco serviços —
`RELATORIO-UI-OPERADOR-SISTEMA-2026-08.md` §10 é explícito em não mexer nesse
enum. **A solução já foi resolvida antes no projeto**: os templates
`avaliador/lista.html` e `avaliador/votar.html` escrevem o termo acentuado como
literal num `th:switch`, sem tocar o enum. O equivalente Java é um `switch` de
três linhas no relatório (§7.4). Mesma solução, mesmo raciocínio, zero impacto
nos PDFs fora de escopo.

### 6.5 Marcadores de terminal na coluna "Status"
*Observado em `relatorio-final-andamento.pdf`, p. 2.*

A seção 4 imprime `[X]`, `[>]` e `[ ]`. É notação de checklist de terminal num
documento que pode ser autuado. Não há legenda: quem recebe o dossiê precisa
adivinhar que `[>]` é "etapa atual". Substituir por palavra ("Concluída" /
"Em andamento" / "Pendente") resolve, e de quebra torna a coluna legível em
leitor de tela quando o PDF for lido eletronicamente.

### 6.6 A capa duplica o corpo e sobra um terço de página em branco
*Observado nos três exemplos, p. 1.*

A capa apresenta: brasão, três linhas institucionais, régua, título, tabela com
Nº/Paciente/RGCT/Data da decisão/Resultado, tabela dos três avaliadores e
rodapé. **Todos** esses dados reaparecem na página seguinte, nas seções 1, 2
e 3 — a capa é um subconjunto visual do sumário, não uma peça com função
própria. E ainda assim o **último terço da página fica vazio**.

Some-se: o carimbo do topo já imprime *"Central de Transplantes do Estado do
Rio Grande do Sul - URGENCIA RENAL"* a ~1 cm do topo, e a capa repete
*"Central de Transplantes do Estado do Rio Grande do Sul"* 3 cm abaixo, junto
com um segundo brasão. **Dois brasões e dois nomes do órgão na mesma página.**

### 6.7 Barra de seção órfã no pé da página
*Reproduzido em `relatorio-final-exemplo.pdf` (p. 2→3) e em
`relatorio-final-indeferido.pdf` (p. 2→3): nos dois, a barra azul "5. Relação
de anexos" fecha a página e a tabela correspondente abre na página seguinte.*

Nenhuma seção usa `setKeepTogether(true)` nem `setSplitLate`, e `secao()`
escreve uma `PdfPTable` de uma célula sem qualquer vínculo com o conteúdo que a
sucede. Não é acaso do meu exemplo: é a configuração padrão, e a seção 5 cai
sistematicamente no pé da página em processos de tamanho típico.

### 6.8 Última página do sumário quase vazia, sem fim declarado
Em `relatorio-final-exemplo.pdf`, a p. 3 tem duas linhas de tabela e o fecho;
os outros **75%** são brancos. Em `relatorio-final-indeferido.pdf`, a p. 3 tem
**duas linhas** ("Nenhum anexo registrado." + fecho) e ~90% de vazio.

### 6.9 Sem índice, num documento que passa fácil de 40 páginas
O documento mescla **todos** os anexos PDF do processo. Não há sumário
navegável, não há marcadores (*bookmarks*, que o OpenPDF suporta via
`PdfOutline`/`addOutline`) e a "Relação de anexos" não informa página inicial
(A9). Em papel e em tela, a navegação é sequencial.

### 6.10 Outros pontos menores, verificados
- **O carimbo ocupa 55pt no topo de *todas* as páginas, inclusive da capa**,
  onde compete com o timbre próprio da capa (§6.6).
- **O cabeçalho identifica o paciente só pelas iniciais** ("Paciente A.C.B."),
  enquanto o corpo traz o nome completo. Isso é **correto e deliberado** — o
  `estampar` é compartilhado e as iniciais também alimentam o `Title` do PDF.
  Registrado aqui apenas para que ninguém "corrija" isso por engano.
- **Tabelas sem faixa zebrada e sem alinhamento de coluna por tipo de dado**
  (datas à esquerda, junto de texto). Ponto menor, mas é parte do "cara de
  dump".
- **Nenhum campo de assinatura/validação.** Documento de arquivo que não indica
  quem responde por ele (o Ofício indica).

---

## 7. Proposta de reformulação — "peça de processo administrativo"

**Princípio:** o Relatório Final é a **memória oficial** de um processo que
decidiu sobre a urgência de um paciente renal. O tom certo não é o de um
dashboard nem o de um relatório gerencial: é **sóbrio, denso de informação,
legível em papel P&B e autoexplicativo para quem nunca viu o sistema**. Cor é
recurso de orientação, não de decoração.

**Restrições auto-impostas:**
OpenPDF 1.3.34 permanece · Helvetica standard-14 permanece (§10.2) · nenhuma
dependência nova · nenhuma regra de negócio alterada · nenhuma rota nova ·
`PdfCabecalhoStamper` só se tocado sob decisão explícita · o documento continua
interno (§2).

### P1 — Paleta institucional, em fonte única (`PaletaPdf`)

Trocar os cinco valores pelos institucionais e **extrair para uma classe
compartilhada** (`service/PaletaPdf.java` ou constantes em
`PdfCabecalhoStamper`, que já é o lugar do "padrão institucional"):

```java
static final Color AZUL          = new Color(0x1A, 0x4D, 0x8F); // --rs-blue
static final Color AZUL_ESCURO   = new Color(0x0F, 0x31, 0x63); // --rs-blue-dark
static final Color CINZA_TEXTO   = new Color(0x47, 0x55, 0x69); // --rs-gray-600
static final Color CINZA_BORDA   = new Color(0xCB, 0xD5, 0xE1); // --rs-gray-300
static final Color VERDE         = new Color(0x1F, 0x6B, 0x36); // --rs-green-dark
static final Color VERMELHO      = new Color(0x8B, 0x1A, 0x1A); // --rs-red-dark
```

**Ganho colateral que não é opcional a longo prazo:** hoje mudar a cor
institucional exige editar 3 (ou 4) arquivos e ninguém é avisado se esquecer um.

> **Atenção de escopo:** o Relatório Anual e o Relatório do Avaliador estão
> **fora de escopo**. Se `PaletaPdf` for compartilhada, os três documentos mudam
> de cor juntos. Isso pode ser desejável (consistência) ou indesejável (mudança
> não revisada em documentos fora do escopo aprovado). **É a decisão 6 da §10.**
> A alternativa segura é aplicar a paleta só no `PdfRelatorioBuilder` nesta leva
> e unificar depois.

### P2 — Cor com parcimônia: régua no lugar de faixa

O maior ganho de "cara de documento" por linha alterada, e o mais barato de
fazer no OpenPDF:

| Elemento | Hoje | Proposto |
|---|---|---|
| Barra de seção | célula de largura total, **fundo azul sólido**, texto branco 11pt | **sem preenchimento**: texto azul-escuro 12pt bold em caixa alta + **filete de 1,5pt** abaixo, largura total (é o mesmo recurso já usado na régua da capa) |
| Cabeçalho de tabela | fundo azul sólido, texto branco 9pt | fundo **cinza muito claro** (`#F1F5F9`), texto **preto** bold 9pt, filete inferior azul |
| Resultado da decisão | 13pt bold colorido | **mantido** (funciona, e a caixa alta carrega a informação em P&B) |
| Favorável / Não favorável | verde/vermelho 9pt | **mantido**, nas variantes `-dark` |

Resultado: a página deixa de ter 5 faixas chapadas e passa a ter 5 títulos
tipográficos com régua — que é como um documento oficial marca seção. Fotocópia
melhora muito.

### P3 — Escala tipográfica declarada

Cinco degraus, com regra explícita, em vez de nove tamanhos ad hoc:

| Papel | Proposto | Hoje |
|---|---|---|
| Título do documento (capa) | **18** bold | 16 |
| Título de seção | **12** bold caixa alta | 11 branco |
| Corpo de tabela / rótulos | **10** | 9 |
| Apoio (justificativa, notas, fecho) | **9** | 8–9 |
| Numeração/carimbo | **9** / 10 | 9 / 10 |

Subir o corpo de 9 para 10pt é o item de maior impacto de leitura em papel.
**Verificar** o efeito nas tabelas de 3 colunas (§9): 10pt aumenta a quebra de
linha em "Equipe solicitante" e em nomes de arquivo longos — é preciso **olhar o
PDF**, não presumir.

### P4 — Acentuação completa, sem tocar em `ResultadoParecer`

Todos os literais Java do relatório passam a ser acentuados: títulos de seção,
rótulos, "Página X de Y", "URGÊNCIA RENAL", "Nº do Processo", "Não", "Situação",
"Relação de anexos", "Observações", "Emissão".

`ResultadoParecer.getDescricao()` **não muda** (proibição documentada). Em vez
disso, um tradutor local de exibição:

```java
private static String rotulo(ResultadoParecer r) {
    return switch (r) {
        case FAVORAVEL -> "Favorável";
        case NAO_FAVORAVEL -> "Não favorável";
        case SOLICITA_INFORMACAO -> "Solicita informação";
        case SEM_RESPOSTA -> "Sem resposta";
    };
}
```

Mesmo padrão do `th:switch` de `avaliador/lista.html`. **Efeito zero** nos
demais PDFs e exportações.

> `TipoAnexo.getDescricao()` (usado na seção 5) tem a mesma característica.
> Verificar quem mais o consome antes de decidir entre acentuar o enum ou
> aplicar o mesmo tradutor local. Na dúvida, **tradutor local** — a regra do
> projeto é não alterar enum que alimenta documento.

### P5 — Conteúdo correto e completo (endereça A1, A4, A5, A6, A7)

- **Seção 3 condicional.** Se `!isFinalizado()`, a seção deixa de se chamar
  "Decisão final" e vira **"3. Situação atual"**, com "Em andamento" (o mesmo
  tratamento que a capa **já faz**) — e as linhas de decisão desaparecem. Se
  DEFERIDO: some o bloco de ofício, entra **"Data de envio ao SNT"**. Se
  INDEFERIDO: entra **"Ofício nº"** junto das datas, some o SNT.
- **Parecer impedido** ganha rótulo próprio: *"Impedido — conflito de
  interesse (solicitante do processo)"*.
- **Coluna de auditoria do voto** na tabela de pareceres: `dataHoraVoto`,
  `votadoPor` e a origem por extenso (*"Portal do Avaliador (voto
  autenticado)"*). É a prova que substituiu o anexo — precisa estar no
  documento de prova.
- **`dataEnvio` do parecer** exibida ao lado da resposta: o prazo de cada
  avaliador passa a ser legível direto no documento.

### P6 — A frase da regra passa a dizer a verdade (endereça A3)

Substituir a linha fixa por um parágrafo derivado do estado real, usando as
funções que a decisão de fato usa:

- caso normal deferido: *"Favoráveis: 2 de 3 — maioria simples atingida (2 de 3
  deferem)."*
- caso indeferido: *"Não favoráveis: 2 de 3 — maioria simples atingida (2 de 3
  indeferem)."*
- **caso coordenador**: *"Deferido pelo voto do Coordenador da CET-RS (Dr. X),
  que defere isoladamente, conforme regra vigente. Favoráveis: 1."*
- em andamento: *"Pareceres recebidos: N de 3."*

E marcar o coordenador na tabela de avaliadores (*"Nome — Coordenador
CET-RS"*), já que a tela de detalhe o faz e o documento não.

**Nenhum cálculo novo:** `deferidoPeloCoordenador`, `contarFavoraveis`,
`contarNaoFavoraveis` e `favoraveisNecessariosParaDeferir` já existem em
`ProcessoService`/`ProcessoValidator` e já são injetados.

### P7 — Capa enxuta e sem duplicação (endereça 6.6, A12)

A capa deixa de repetir as seções 1–3 e passa a ser **folha de rosto**: brasão,
identificação institucional, título do documento, número do processo, paciente,
resultado e **uma única** data de emissão. A tabela de avaliadores sai da capa
(ela é a seção 2, completa, na página seguinte).

**Alternativa mais radical, e legítima:** **eliminar a capa** e promover o
cabeçalho da página 2 a abertura do documento. O carimbo institucional já
aparece em todas as páginas (§6.10); o Ofício, que é o documento formal de
referência do projeto, **não tem capa**. Isso economiza uma página por
relatório e elimina de vez a duplicação. **É a decisão 7 da §10.**

### P8 — Divisória por anexo e índice com página (endereça A9)

Duas ambições, com custos bem diferentes — proponho a barata agora:

- **(a) Folha divisória por anexo** *(barato, recomendado)*: antes de cada anexo
  mesclado, uma página com "ANEXO N de M", o tipo, o nome do arquivo e a data.
  Reaproveita `adicionarPaginaAviso`, que já existe e já é usada para os casos
  de exceção. Custo: uma página por anexo.
- **(b) Índice com página inicial** *(mais caro, opcional)*: exige **duas
  passagens** — gerar o sumário, contar suas páginas, ler o número de páginas de
  cada anexo, e **regerar** o sumário já com os números. É viável no OpenPDF e
  não é exótico, mas dobra a geração e adiciona uma classe de bug (índice
  divergente) que hoje não existe. **Só implementar se (a) não bastar.**
- **(c) Marcadores/bookmarks** (`PdfCopy` + `PdfOutline`): navegação em leitor
  de PDF sem alterar uma linha visual. Barato e de bom retorno para um dossiê de
  40+ páginas. Vale considerar junto de (a).

### P9 — Higiene de página (endereça 6.7, 6.8, A11)

- `setKeepTogether(true)` no bloco *barra de seção + primeira linha da tabela*,
  para nenhuma seção nascer órfã no pé da página.
- O parágrafo de encerramento sai do fim do sumário e passa a ser a **última
  linha da última página do documento** (após o merge), ou vira uma **página de
  encerramento** própria — coerente com P8(a).
- Um único carimbo de emissão, num formato só (A12).

### P10 — Rótulo honesto quando o processo não terminou (endereça A2)

Título dinâmico: `RELATÓRIO FINAL` quando `isFinalizado()`, `RELATÓRIO PARCIAL —
PROCESSO EM ANDAMENTO` caso contrário. Nenhum bloqueio de rota. **Decisão 3.**

### P11 — O que fazer com o retrato guardado (endereça A8)

Quatro caminhos, todos com custo — **decisão 4 da §10**, não implementar por
conta própria:

| Opção | Efeito | Custo/risco |
|---|---|---|
| (a) **Regerar** o `RELATORIO_FINAL` ao concluir a etapa 6 | anexo sempre completo | mais um ponto de escrita pós-decisão; a etapa 6 hoje envia e-mail e não mexe em anexo |
| (b) **Não anexar** na decisão; só gerar ao vivo | some a divergência | perde-se o retrato imutável do momento da decisão — que pode ser exatamente o que se quer preservar |
| (c) **Datar o retrato** no próprio documento ("Emitido na decisão em …; pode não incluir documentos posteriores") | honesto, barato | a divergência continua existindo, apenas deixa de enganar |
| (d) **Filtrar `RELATORIO_FINAL`** da lista de anexos do dossiê ZIP | acaba a duplicata no pacote entregue | some o retrato do dossiê (pode ser desejado ou não) |

Minha recomendação: **(c) + (d)** — baratas, sem efeito colateral em fluxo, e
resolvem o sintoma que chega ao auditor. (a) é a solução "certa" e pode vir
depois.

### P12 — O que **não** proponho: trocar de biblioteca

Para registro, porque a pergunta é inevitável ao ver o código de baixo nível:

- **iText 7/8** — licença AGPL ou comercial. **Descartado** para órgão público
  sem contrato.
- **openhtmltopdf/Flying Saucer (HTML+CSS → PDF)** — permitiria escrever o
  documento como template e reaproveitar tokens do `app.css`. Sedutor, e
  **recomendo contra**: exigiria reescrever os quatro serviços de PDF, **não
  resolve o merge de anexos** (continuaria precisando de PDFBox/OpenPDF), traria
  dependência nova numa aplicação de 1 GB de RAM compartilhada com outras três
  (ver CLAUDE.md, vistoria de 2026-08-03), e o carimbo página a página
  — a parte mais delicada e mais bem-feita do código atual, incluindo o
  tratamento de páginas rotatadas — teria de ser refeito.

**Tudo que este relatório propõe cabe no OpenPDF, sem dependência nova.** O que
o OpenPDF realmente não dá de graça e por isso não foi proposto: fluxo de texto
multi-coluna, tipografia avançada (kerning fino, ligaduras), e layout responsivo
a conteúdo. Nada disso é necessário aqui.

---

## 8. Plano faseado

Ordenado por **(correção × risco)**: primeiro o que o documento **diz**, depois
como ele **parece**. Cada fase é um commit/PR próprio.

> **Regra obrigatória para todas as fases:** rodar `.\test.ps1` (JDK 21) **e
> gerar um PDF de verdade e olhá-lo** — nos três estados (DEFERIDO com anexos,
> INDEFERIDO, EM ANDAMENTO). A §9 explica por que a suíte não substitui isso.
> O gerador temporário usado neste relatório (`ZzGerarPdfExemploTest`) é fácil
> de recriar a partir de `RelatorioServiceTest`; **não commitar** o gerador.

### R1 — Correções de conteúdo, sem tocar em nada visual · risco baixo · **maior valor**
A1 (seção 3 condicional), A3/P6 (frase da regra + coordenador), A4 (impedido),
A6 (`numeroOficio`, `dataEnvioSnt`), A7 (linhas inaplicáveis), A10 (`Nº`),
A12 (um só carimbo de emissão).

Só `RelatorioService` (e uma linha de `PdfRelatorioBuilder` para o `Nº`).
Nenhuma mudança de cor, fonte ou layout. **Testes novos obrigatórios**, por
extração de texto: um processo deferido-por-coordenador **não** pode conter a
string "2 de 3 defere"; um deferido **não** pode conter "Motivo do
indeferimento"; um não decidido **não** pode conter "Decisão final: ENVIADO";
um impedido **deve** conter "Impedido".

### R2 — Acentuação completa · risco baixo
P4. Literais + tradutores locais de `ResultadoParecer` e (se confirmado)
`TipoAnexo`. **Não tocar nos enums.**

⚠ Conferir antes: `ProcessoExportacaoIntegrationTest` verifica strings do
relatório de movimentação dentro do ZIP, e `RelatorioServiceTest` faz
`contains(...)` sobre o texto extraído. Grep em `src/test/java` por qualquer
literal que vá ganhar acento.

### R3 — Paleta institucional · risco baixo, **decisões 1 e 6 primeiro**
P1. Cinco constantes. Se a decisão 6 for "só o Relatório Final por ora",
mantém-se em `PdfRelatorioBuilder` e a unificação fica para depois.

Validação: gerar e **olhar** — é uma mudança 100% invisível para a suíte.

### R4 — Tipografia e cor comedida · risco médio · **maior mudança visual**
P2 + P3. É a fase que muda a aparência de verdade: régua no lugar da faixa,
cabeçalho de tabela claro, corpo a 10pt.

Riscos concretos a olhar no PDF gerado: com 10pt, a tabela `{3, 7}` de rótulos
pode quebrar "Data de solicitação da urgência renal" de forma feia, e a tabela
de anexos `{4.5, 3.5, 1.5}` pode passar a quebrar nomes de arquivo. **Reajustar
proporções olhando o resultado**, não no papel.

Executar **sozinha**, em PR próprio.

### R5 — Higiene de página e divisórias de anexo · risco médio
P9 + P8(a) (+ P8(c) bookmarks, se aprovado). Mexe em `mergeComAnexos` e no fim
do sumário. Testar especificamente: processo **sem** anexo, com **um** anexo,
com anexo **não-PDF**, e com anexo **ausente do disco** (os quatro caminhos já
cobertos por `RelatorioServiceTest`, que **não pode** quebrar).

### R6 — Capa e rótulo parcial · risco médio · **decisões 3 e 7 primeiro**
P7 + P10. Depende de escolha do dono do produto entre enxugar ou eliminar a
capa.

### Fora de fase (decisão 4)
P11 — o retrato guardado (A8). Toca `DecisaoFinalService` e/ou
`ExportacaoProcessoService`, ou seja, **fluxo**, não documento. Se aprovado,
merece PR próprio com teste de integração real (não `@MockitoBean` do serviço),
seguindo a convenção do projeto para escrita irreversível.

### Ordem sugerida
**Bloco 1 (verdade do documento):** R1 → R2.
**Bloco 2 (identidade):** R3 → R4, com revisão visual entre as duas.
**Bloco 3 (navegação):** R5.
**Bloco 4 (estrutura):** R6.
**À parte:** P11.

---

## 9. Riscos de teste — por que a suíte não protege quase nada aqui

`RelatorioServiceTest` tem 5 testes. Eles verificam: que o PDF começa com
`%PDF`; que o cabeçalho traz as **iniciais** e não o nome completo; que anexo
ausente vira página de aviso; que anexo real é mesclado; que anexo não-PDF vira
página informativa. `PdfCabecalhoStamperTest` verifica `/Info` e XMP.

**Nenhuma asserção, em nenhum teste do projeto, olha cor, fonte, tamanho,
espaçamento, quebra de página, ordem visual ou orfandade de seção.** Todos os
achados da §6 convivem hoje com a suíte verde — e o achado A1 ("Decisão final:
ENVIADO") convive com ela sem nem ser de layout: é conteúdo, e passou porque
nenhum teste gera um relatório de processo **não** decidido.

Consequências práticas:

1. **Todo PR deste plano precisa anexar/gerar PDF e alguém precisa olhar.**
2. **R1 deve criar os testes que faltam** — os de conteúdo são perfeitamente
   testáveis por `PdfTextExtractor`, que o projeto já usa. Boa parte dos achados
   da §5 vira asserção negativa de uma linha.
3. Testes que podem acusar quebra ao longo do plano:

| Teste | O que trava | Fases sensíveis |
|---|---|---|
| `RelatorioServiceTest` | `contains(...)` sobre texto extraído; os 4 caminhos de anexo | R2, R5 |
| `PdfCabecalhoStamperTest` | `/Info`, XMP, ausência do nome do paciente | só se `PdfCabecalhoStamper` for tocado (decisão 5) |
| `ProcessoExportacaoIntegrationTest` | strings do relatório de movimentação no ZIP | R2 |
| `OficioServiceTest` | — | nenhuma (fora de escopo, mas **conferir** se R3 compartilhar `PaletaPdf`) |
| `RelatorioAnualServiceTest` / `RelatorioAvaliadorServiceTest` | — | **R3, se a paleta for unificada** (decisão 6) |
| E2E `FluxoCompletoProcessoIT` | localiza o botão **"Relatório Final (PDF)"** por texto exato | qualquer fase que renomeie o botão — **não renomear** |

4. **Regressão silenciosa a vigiar em R4:** subir o corpo para 10pt pode fazer
   um relatório que hoje tem 3 páginas de sumário passar a ter 4. Nada quebra;
   simplesmente muda. Olhar.

---

## 10. Decisões que exigem aval explícito do dono do produto

Nenhuma linha de código deve ser escrita antes destas respostas. As decisões
1–4 são as que mudam o resultado de verdade.

### Decisão 1 — Paleta: manter o azul do Bootstrap ou adotar o institucional
*(a mais importante da parte visual; §6.1)*

O documento é impresso hoje em `#0D6EFD`, `$primary` do Bootstrap. O azul da
Central de Transplantes é `#1A4D8F`. Em 2026-08-03 houve uma decisão registrada
de *"manter a paleta atual"*, possivelmente sob o entendimento de que "a paleta
atual" fosse a institucional.

- **Opção A — adotar a paleta institucional.** Identidade correta e contraste
  quase o dobro (4,50:1 → 8,39:1) num documento feito para papel.
  **Recomendada.**
- **Opção B — manter como está.** Custo zero; o documento continua na cor de um
  framework web e no limite da WCAG AA.

### Decisão 2 — Fonte: Helvetica ou incorporar uma TTF
- **Opção A — manter Helvetica** (uma das 14 fontes padrão do PDF, **não
  incorporada**): zero bytes no JAR, renderiza em qualquer leitor, acentuação
  pt-BR **já funciona** (§6.4). **Recomendada, e é o que já foi decidido em
  2026-08-03.**
- **Opção B — incorporar uma TTF** (ex.: a própria Inter do sistema): tipografia
  mais próxima da identidade da tela. **Custos concretos que não podem ser
  presumidos:** a Inter do projeto está em **`.woff2`, formato que o OpenPDF não
  incorpora** — seria preciso versionar os `.ttf` (~300 KB por peso, e são
  necessários ao menos regular + bold), a licença (Inter é **SIL OFL 1.1**,
  permissiva, mas exige manter o aviso de licença junto do arquivo) precisa ser
  verificada e registrada, e o JAR cresce. **Não recomendada** — o ganho é o
  menor de todo o plano e o custo é o único que mexe em empacotamento.

### Decisão 3 — Rotular "RELATÓRIO PARCIAL" quando o processo não terminou (A2, P10)
- **Opção A — rotular** (título dinâmico, sem bloquear nada). **Recomendada.**
- **Opção B — bloquear a rota** fora de status final, como se fez com o Ofício.
  Mais rígida; tira do operador a impressão do andamento parcial.
- **Opção C — não mexer.**

### Decisão 4 — O retrato guardado na decisão (A8, P11)
Hoje o anexo `RELATORIO_FINAL` nunca contém o ofício nem o comprovante SNT, e o
**dossiê ZIP entrega dois relatórios diferentes**. Quatro caminhos na tabela da
P11. **Recomendação: (c) datar o retrato + (d) filtrar a duplicata do ZIP.**
Isso é fluxo, não documento — **não implementar junto com as fases visuais**.

### Decisão 5 — Tocar ou não o `PdfCabecalhoStamper`
Ele é **compartilhado com o Relatório Anual** (fora de escopo). Acentuar
"URGÊNCIA"/"Página" e trocar a cor da régua muda os dois documentos de uma vez.
- **Opção A — tocar** (aceitar que o Relatório Anual mude junto; é consistência,
  e a mudança é pequena e do mesmo sinal). **Recomendada, com revisão visual do
  Relatório Anual no mesmo PR.**
- **Opção B — não tocar** nesta leva: o Relatório Final fica acentuado no corpo
  e com "Pagina X de Y" sem acento no rodapé. Inconsistência visível.

### Decisão 6 — `PaletaPdf` compartilhada ou cor só no Relatório Final
Consequência de escopo da decisão 1.
- **Opção A — classe compartilhada**: elimina a triplicação, mas **muda a cor
  do Relatório Anual e do Relatório do Avaliador**, ambos fora do escopo
  aprovado.
- **Opção B — só `PdfRelatorioBuilder` agora**, unificar numa leva futura
  dedicada. **Recomendada** — mantém o escopo honesto; a triplicação fica
  registrada aqui como dívida conhecida.

### Decisão 7 — Capa: enxugar ou eliminar (§6.6, P7)
- **Opção A — enxugar** (folha de rosto sem duplicar as seções 1–3).
  **Recomendada** para preservar a formalidade de peça autuada.
- **Opção B — eliminar** a capa: economiza uma página por relatório, elimina a
  duplicação de brasão/nome do órgão e alinha com o Ofício, que não tem capa.
- **Opção C — manter** como está.

### Decisão 8 — Divisórias e navegação (P8)
- **(a) folha divisória por anexo** — recomendada; custo: +1 página por anexo.
- **(b) índice com página inicial** — exige geração em duas passagens; só se (a)
  não bastar.
- **(c) bookmarks/marcadores** — barato e útil num dossiê de 40+ páginas; sem
  efeito visual nenhum. Vale aprovar junto de (a).

---

## Anexo A — como reproduzir o diagnóstico

```bash
cd /workspaces/urgencia
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# §6.1 - a paleta do PDF nao existe no CSS institucional, e esta triplicada
grep -n "new Color(" src/main/java/br/gov/saude/sgpur/service/*.java
grep -n "rs-blue:\|rs-green:\|rs-red:\|rs-gray-600" src/main/resources/static/css/app.css

# A3 - a regra real do coordenador que o relatorio ignora
grep -n "favoraveisNecessariosParaDeferir\|deferidoPeloCoordenador" \
     src/main/java/br/gov/saude/sgpur/service/ProcessoValidator.java

# A4/A5 - campos de Parecer que o relatorio nao le
grep -n "impedido\|votadoPor\|dataHoraVoto\|origem" \
     src/main/java/br/gov/saude/sgpur/domain/Parecer.java

# A8 - o dossie leva o relatorio ao vivo E o anexo antigo (sem filtro)
grep -n "RELATORIO_FINAL\|relatorioService.gerar" \
     src/main/java/br/gov/saude/sgpur/service/ExportacaoProcessoService.java \
     src/main/java/br/gov/saude/sgpur/service/DecisaoFinalService.java

# A10 - o "N" sem o grau
grep -n '"N do Processo:"' src/main/java/br/gov/saude/sgpur/service/PdfRelatorioBuilder.java
```

**Para gerar os PDFs de exemplo:** copiar `RelatorioServiceTest` para um
`ZzGerarPdfExemploTest`, montar os três processos descritos na §3, gravar os
bytes com `Files.write(...)` e rodar
`mvn -o test -Dtest=ZzGerarPdfExemploTest`. **Apagar o arquivo depois** — ele é
instrumento de inspeção, não teste. Para olhar: `pdftoppm -png -r 110`, ou abrir
o PDF direto.

---

## Anexo B — contrastes (calculados, WCAG 2.x)

Luminância relativa pela fórmula da WCAG 2.x sobre os hex reais. **AA exige
4,5:1 para texto normal.** Reconferir com ferramenta antes do merge.

| Texto | Sobre | Contraste | AA |
|---|---|---|---|
| branco | `#0D6EFD` (`AZUL` atual) | **4,50:1** | no limite exato |
| branco | `#1A4D8F` (`--rs-blue`) | **8,39:1** | ✅ folgado |
| `#198754` (`VERDE_ESCURO` atual) | branco | **4,54:1** | ✅ por um triz |
| `#1F6B36` (`--rs-green-dark`) | branco | **6,53:1** | ✅ |
| `#DC3545` (`VERMELHO` atual) | branco | **4,53:1** | ✅ por um triz |
| `#6C757D` (`CINZA` atual, usado a 8–9pt) | branco | **4,69:1** | ✅ por um triz |
| `#475569` (`--rs-gray-600`) | branco | **7,58:1** | ✅ |

Observação relevante para papel: o mínimo da WCAG foi pensado para tela. Num
documento que será **impresso e fotocopiado**, valores no limite (4,5–4,7:1) em
corpo de 8–9pt degradam rápido — o que reforça, de forma independente da
identidade visual, a Opção A da decisão 1 **e** a subida do corpo para 10pt
(P3).

---

## 11. Fechamento

O Relatório Final é **tecnicamente bem-feito e documentalmente incompleto**. O
código que o gera é cuidadoso onde é difícil (merge, carimbo em página
rotacionada, metadados, tolerância a anexo corrompido) e desatento onde é
fácil: a seção da decisão não sabe que pode não haver decisão, a frase da regra
não conhece a exceção do coordenador que o próprio sistema aplica, o parecer
impedido desaparece, e a prova do voto — que substituiu formalmente o anexo
comprobatório — não é impressa.

A camada visual tem uma causa única e barata de corrigir: **o documento herdou
a paleta default do Bootstrap e nunca recebeu a da instituição**, e essa herança
é também o que o deixa no limite do contraste mínimo num documento destinado ao
papel.

O caminho mais curto entre o estado atual e um documento do qual a Central de
Transplantes possa se orgulhar são as fases **R1** (verdade), **R2**
(acentuação) e **R3** (paleta) — as três de risco baixo, nenhuma tocando regra
de negócio, fluxo, rota ou os outros três PDFs do sistema.

**Próximo passo:** abrir os três PDFs de exemplo citados na §3, responder as
oito decisões da §10 — em especial a nº 1 (paleta) e a nº 4 (o retrato
guardado) — e só então autorizar a fase R1.
