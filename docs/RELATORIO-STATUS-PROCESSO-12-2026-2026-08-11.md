# Revisão dos status exibidos — motivada pelo processo 12/2026 (2026-08-11)

**Status deste documento: DIAGNÓSTICO + PLANO. Nada foi implementado.**
Nenhuma linha de código de produção foi alterada nesta sessão.

**Pedido que originou a revisão** (dono do produto, olhando `/processos/17`
em produção): *"verifique o status do processo 12/2026, corrija os status
para que fiquem de acordo com a situação atual dos processos"*.

---

## ⚠ LEIA PRIMEIRO — a resposta curta

**O status GRAVADO do processo 12/2026 está CORRETO.** `SOLICITA_INFORMACAO`
é exatamente o que a regra manda para um processo em que um avaliador votou
"Solicita informação" e ninguém retomou a análise. O mesmo vale para os
outros 10 processos de produção: **nenhum registro do banco contradiz os
pareceres** (conferência processo a processo na §2).

**O que está errado é a EXIBIÇÃO.** Especificamente: quando a pausa acontece
**antes** de a maioria se formar — que é o caso do 12/2026 —, o sistema
esconde a pausa de todas as telas de acompanhamento e anuncia, no lugar dela,
uma pendência que **não destrava nada**. O Painel e a lista de processos
mostram, na mesma linha:

> | Situação | O que falta |
> |---|---|
> | 🟡 `Solicita informacao` | *Respostas dos médicos* — "Faltam 1 de 3 pareceres" |

As duas células se contradizem, e a segunda é a que o operador usa para
priorizar o trabalho. Ele vai cobrar o 3º médico — mas mesmo que esse médico
vote, **o processo continua travado**, porque quem destrava é o solicitante
enviar a informação e o operador clicar em "retomar análise".

**Nenhuma correção proposta neste relatório altera o cálculo da votação.**
Maioria simples 2-de-3, exceção do coordenador, bloqueio da pausa, contagem
de pareceres e `ProcessoService.decidir` **não se tocam** — mesma regra de
ouro do `RELATORIO-VISTORIA-BRECHAS-DECISAO-2026-08-10.md`. Tudo aqui é
apresentação: qual etapa a timeline pinta como "atual", qual rótulo vai para
a coluna "o que falta", e acentuação de texto visível.

---

## 1. Metodologia

- **Dados reais de produção** (leitura via SSH na VM Oracle, somente
  `SELECT`): os 3 pareceres do processo 17, o panorama dos 11 processos, os
  anexos obrigatórios e as flags de e-mail. Nada foi presumido.
- **Reprodução executável, não leitura de código**: os dados exatos do
  12/2026 foram montados num teste temporário sobre `FluxoProcessoService` /
  `ProcessoService` / `ProcessoValidator` **reais** (só os repositórios
  mockados), imprimindo o que cada tela receberia — timeline, wizard,
  gating, badge, pendência e placar. O mesmo foi feito para os **cenários
  vizinhos** (pausa depois da maioria; pausa com coordenador favorável;
  11/2026 real; processo recém-criado), para separar "está sempre errado"
  de "está errado só neste caso". As saídas estão citadas literalmente
  abaixo. O teste foi **descartado** ao fim da investigação (era instrumento
  de diagnóstico, não cobertura) — a §5 propõe testes de verdade.
- **Leitura das superfícies de exibição**: `processos/detalhe.html`,
  `dashboard.html`, `processos/lista.html`, `arquivo/lista.html`,
  `solicitante/detalhe.html`, `solicitante/lista.html`,
  `avaliador/lista.html`, mais `FluxoProcessoService`, `ProcessoValidator`,
  `StatusProcesso`, `StatusSolicitacaoOnline`, `RegraDecisao`,
  `SituacaoPedidoView`, `SituacaoListaView`, `PainelLinha.CelulaMedico`.

---

## 2. Conferência dado × regra — os 11 processos

Verdicto: **11 de 11 coerentes**. Nenhum status gravado contradiz os pareceres.

| Nº | Status | Fav | Desfav | Sem voto | Regra que explica |
|---|---|---|---|---|---|
| 12/2026 (id 17) | `SOLICITA_INFORMACAO` | 1 | 0 | 1 | 1 parecer `SOLICITA_INFORMACAO` ativo → pausa. ✅ |
| 02, 04, 05, 06, 08, 09/2026 | `DEFERIDO` | 2 | 0 | 1 | Maioria simples 2-de-3; 3º parecer dispensado. ✅ |
| 03/2026 | `INDEFERIDO` | 1 | 2 | 0 | Maioria simples 2-de-3. ✅ |
| 07, 10/2026 | `INDEFERIDO` | 0 | 2 | 1 | Maioria simples 2-de-3. ✅ |
| 11/2026 | `DEFERIDO` | 1 | 0 | 2 | Exceção do coordenador (`era_coordenador_no_voto = true`). ✅ |

Anexos obrigatórios e e-mail final também batem: todo `DEFERIDO` tem
`COMPROVANTE_SNT`, todo `INDEFERIDO` tem `OFICIO_INDEFERIMENTO`, todos os
finalizados têm `email_enviado_solicitante = true`, e o único não finalizado
(17) tem `false` — correto, ainda não chegou nessa etapa.

**Conclusão:** não há nada a "corrigir no banco". Não existe UPDATE a rodar
em produção. Isso descarta a hipótese mais cara e mais arriscada logo de
saída.

---

## 3. Achados

### Achado 1 — A pausa some do sistema quando acontece ANTES da maioria ⚠ PRINCIPAL

**Onde:** `FluxoProcessoService.montarEtapas` (cascata `anterioresConcluidas`)
→ `pendenciaAberta` → `dashboard.html`, `processos/lista.html`, card
"Progresso" de `processos/detalhe.html`.

**Saída real do 12/2026:**

```
[CONCLUIDA] Envio aos 3 médicos      -> Enviado aos 3 medicos.
[ATUAL]     Respostas dos médicos    -> Faltam 1 de 3 pareceres. Favoraveis ate agora: 1.
[BLOQUEADA] Informação complementar  -> Aguardando informacao complementar do solicitante...
[BLOQUEADA] Decisão final            -> Aguardando informacao complementar do solicitante.
[BLOQUEADA] Resposta ao solicitante  -> Falta marcar o e-mail como enviado.
pendenciaAberta : Respostas dos médicos | Faltam 1 de 3 pareceres. Favoraveis ate agora: 1.
```

**Por que acontece.** A etapa "Respostas dos médicos" só fica `CONCLUIDA`
quando há maioria **ou** todos os 3 votaram. No 12/2026 há 1 favorável, 1
pedido de informação e 1 pendente → nenhuma das duas condições. Ela fica
`ATUAL`, e a cascata `anterioresConcluidas = false` derruba tudo que vem
depois para `BLOQUEADA` — **inclusive a própria etapa da pausa**, que é
inserida logo em seguida. Como `pendenciaAberta` devolve a **primeira** etapa
`ATUAL`, a pendência global do processo vira "Respostas dos médicos".

**A assimetria é o que prova que é bug, não desenho.** No cenário em que a
pausa chega **depois** da maioria, o mesmo código acerta:

```
### 2 FAVORÁVEIS + 1 SOLICITA_INFORMAÇÃO (pausa após maioria)
[CONCLUIDA] Respostas dos médicos
[ATUAL]     Informação complementar      <-- correto
pendencia : Informação complementar      <-- correto
```

Ou seja: o mesmo processo, na mesma pausa, é descrito de duas formas
opostas dependendo apenas de **quantos colegas já tinham votado quando o
pedido de informação chegou**. Isso não é uma escolha de produto — é um
efeito colateral da ordem em que as etapas são montadas.

**Impacto real.** Três telas erram ao mesmo tempo para o 12/2026:

1. **Painel** — coluna "o que falta": *Respostas dos médicos*.
2. **`/processos`** — coluna "Pendência": *Respostas dos médicos*, ao lado do
   badge amarelo `Solicita informacao`. Contradição literal, lado a lado.
3. **Detalhe, card "Progresso"** — a etapa da pausa aparece **cinza**, como
   se fosse coisa do futuro, enquanto o card amarelo "Aguardando informação
   complementar" (aba Respostas, logo ao lado) grita que é agora. O dono do
   produto chegou a este relatório justamente olhando esse card amarelo e
   estranhando o resto da tela.

**A pendência anunciada é ativamente enganosa**, não apenas incompleta: se o
3º médico votar favorável, formam-se 2 favoráveis, e mesmo assim
`ProcessoValidator.validarPausaDecisao` continua bloqueando a decisão até
`retomarAposInformacao`. O sistema está pedindo ao operador uma ação que
**não desbloqueia o processo**, e escondendo a única que desbloqueia.

**Correção proposta (mínima, cirúrgica).** Em `montarEtapas`, quando o
processo está em pausa, a etapa `INFO_COMPLEMENTAR` deve ser sempre `ATUAL`
— nunca `BLOQUEADA` —, porque é literalmente a situação presente do
processo. Concretamente: passar `anterioresConcluidas = true` **apenas para
essa etapa**, mantendo intacto o `anterioresConcluidas = false` que ela já
propaga para Decisão/Finalização (a pausa continua travando o que vem
depois). Complementarmente, quando a pausa estiver ativa e a etapa Respostas
não estiver concluída, o detalhe dela deve dizer que o processo está pausado
em vez de só "Faltam N pareceres" (sugestão: *"Processo PAUSADO aguardando
informação complementar. Faltam N de 3 pareceres — mas o voto pendente não
libera a decisão sozinho."*).

Com isso, `pendenciaAberta` passa a devolver "Informação complementar" nos
dois cenários, sem nenhuma mudança em `pendenciaAberta` em si, no Painel, na
lista ou nos templates — a correção é num único método.

**Risco: BAIXO.** Não toca contagem de votos, `sugerirDecisao`,
`validarPausaDecisao`, `calcularGating` (a aba Decisão continua bloqueada
pelo mesmo `aguardandoInfo` de sempre) nem em nenhum status gravado. Muda só
o `EstadoEtapa` de uma etapa e o texto de outra. **Não é "seguro para
aplicar direto"** mesmo assim: mexe no serviço que alimenta timeline, wizard,
Painel e lista ao mesmo tempo, e `FluxoProcessoServiceTest` tem asserções
sobre a cascata que precisam ser lidas com cuidado — merece PR com revisão.

---

### Achado 2 — O placar do card "Respostas dos Avaliadores" não menciona a pausa

**Onde:** `ProcessoDetalheController.detalhe`, variável `fraseMaioria`
(linhas ~452-462) → `processos/detalhe.html:772`.

Para o 12/2026, o placar exibe: **"Faltam 1 voto"**. Nada sobre a pausa.

**Por quê.** A ressalva da pausa só existe no ramo `sugestao.isPresent()`:

```java
if (sugestao.isPresent() && pausaBloqueiaDecisao) { "Maioria formada, mas BLOQUEADA..." }
else if (sugestao.isPresent())                    { "Maioria ja formada" }
else if (pendentesVoto == 0)                      { "Todos os votos recebidos" }
else                                              { "Faltam N voto(s)" }   // <-- 12/2026 cai aqui
```

Ou seja, a correção do achado A do
`RELATORIO-BUG-DOIS-VOTOS-DEFEREM-DURANTE-PAUSA-2026-08.md` cobriu só o caso
"pausa **com** maioria" — exatamente o mesmo ponto cego do Achado 1, na
mesma tela, por outro caminho.

**Correção proposta:** mover a checagem de `pausaBloqueiaDecisao` para fora
dos ramos de maioria, prefixando a frase (ex.: *"PAUSADO (aguardando
informação complementar) — faltam 1 voto"*). Uma linha de controller, sem
tocar em regra.

**Risco: BAIXÍSSIMO** — string de apresentação, já calculada, já no model.

---

### Achado 3 — "Maioria já formada" onde quem decidiu foi o coordenador sozinho (recaída)

**Onde:** mesma `fraseMaioria`.

Quando o coordenador CET-RS vota favorável, `sugerirDecisao` retorna
`DEFERIDO` com **um único voto** — e o placar anuncia *"Maioria já
formada"*. Confirmado na reprodução, tanto no cenário pausado quanto no
**11/2026 real** (produção: 1 favorável do coordenador, 2 sem voto,
`DEFERIDO`), onde o card exibe "Maioria já formada" para um processo que
nunca teve maioria nenhuma.

Isto é uma **recaída literal do Achado 3** do
`RELATORIO-VISTORIA-BRECHAS-DECISAO-2026-08-10.md`, que corrigiu exatamente
esse texto na timeline (`FluxoProcessoService` hoje diz corretamente *"Voto
favoravel do Coordenador da CET-RS ... isoladamente, sem precisar da
maioria"*) — mas não no placar do card, que ficou de fora daquela varredura.
Resultado: **a mesma tela** afirma as duas coisas, a 40 pixels de distância.

**Correção proposta:** ramo próprio para `temVotoCoordenadorFavoravel`,
reusando o vocabulário já existente (`RegraDecisao`, já no model como
`regraDecisao`) em vez de inventar texto novo.

**Risco: BAIXÍSSIMO** — apresentação.

---

### Achado 4 — Badge de status sem acento: "Solicita informacao"

**Onde:** `StatusProcesso.getDescricao()` consumido cru em 4 telas do
operador — `processos/detalhe.html:22`, `processos/lista.html:100`,
`arquivo/lista.html:69`, `dashboard.html:223`.

O badge principal do processo 12/2026 — o elemento de maior destaque da
tela — lê literalmente **"Solicita informacao"**, sem cedilha nem til. Idem
"Nao definido" e "Favoravel"/"Desfavoravel" na grade de médicos do Painel
(`PainelLinha.CelulaMedico`).

**Não é esquecimento** — é consequência de uma decisão deliberada e correta:
`StatusProcesso.descricao` (como `ResultadoParecer.descricao`) alimenta o
Relatório Final, o dossiê exportado e a auditoria, e o CLAUDE.md registra
que **não se muda o enum** por causa do raio de impacto em documento oficial.
A saída já usada no projeto para isso é um **tradutor local**: já existe
`PdfRelatorioBuilder.descricaoStatus(StatusProcesso)` com os literais
acentuados, mas é `static` de pacote e só o PDF o consome.

**Correção proposta:** expor a versão acentuada para os templates sem tocar
no enum. Duas opções, a decidir:
- **(a)** um fragment `layout :: statusProcessoBadge(status)` com `th:switch`
  e literais acentuados — mesmo padrão já usado em `avaliador/lista.html`
  para `ResultadoParecer`. **Recomendada:** centraliza os 4 pontos num só,
  e o badge já é sempre a mesma composição (classe + ícone + texto).
- **(b)** um método novo `getDescricaoExibicao()` no enum, deixando
  `getDescricao()` intocado para os PDFs. Mais simples de escrever, mas
  espalha duas descrições pelo domínio e convida ao uso errado.

**Risco: BAIXO**, com uma ressalva concreta: **o E2E localiza elementos por
texto exato**, e a Fase 8 de acentuação já quebrou 3 botões desse jeito (ver
CLAUDE.md). Antes de mesclar, conferir `ProcessoDetalhePage` e as asserções
de `containsString` da suíte.

---

### Achado 5 — Textos de etapa/tooltip sem acento (visíveis em 4 telas)

**Onde:** os literais de `EtapaFluxo.detalhe` em `FluxoProcessoService`
(*"Enviado aos 3 medicos"*, *"Favoraveis ate agora"*, *"retome a analise
para liberar a decisao"*, *"Sugestao automatica"*, *"Falta marcar o e-mail
como enviado"*), os tooltips do wizard (*"Aguardando informacao complementar
do solicitante antes de decidir."*) e `fraseMaioria`
(*"...BLOQUEADA: aguardando informacao complementar"*).

Os **títulos** foram acentuados na Fase 7 do relatório de clareza ("Envio aos
3 médicos", "Decisão final") — os **detalhes** ficaram de fora. Hoje a
timeline mostra título acentuado e, uma linha abaixo, descrição sem acento.
Esses textos aparecem no card "Progresso", no `title` das células de
pendência do Painel e da lista, e nos tooltips do wizard.

**Correção proposta:** acentuar os literais Java desses três pontos. **Não
tocar em `StatusProcesso.descricao` nem em `ResultadoParecer.descricao`** —
onde eles forem interpolados dentro dessas frases, usar o tradutor do
Achado 4.

**Risco: BAIXO** — mesma ressalva do E2E por texto exato.

---

### Achado 6 — `StatusProcesso.getTom()` diverge do badge para `ENVIADO` (latente)

`getBootstrapBadge()` devolve `bg-primary` (azul) para `ENVIADO`, mas
`getTom()` devolve `"neutral"` (cinza). A padronização de 2026-08-06 fixou
**"Aguardando" = azul**, e todos os outros pontos do sistema já seguem
(`SituacaoListaView`, `SituacaoPedidoView`, `CelulaMedico` usam
`"aguardando"`); `StatusProcesso.getTom()` não foi atualizado junto, porque
**nenhum template o consome hoje** (confirmado por grep: só aparece num
comentário de `layout.html`).

**Impacto hoje: ZERO.** O risco é futuro e silencioso: quem migrar essas 4
telas para `layout :: tomBadge` — que é a direção declarada do design
system — muda a cor de `ENVIADO` de azul para cinza sem perceber, desfazendo
a padronização.

**Correção proposta:** trocar `ENVIADO -> "neutral"` por `"aguardando"` no
`getTom()`, e reavaliar `SOLICITADO` na mesma passada.

**Risco: BAIXÍSSIMO, com verificação obrigatória** — é só seguro *porque*
o método está sem consumidor. Reconfirmar o grep antes de aplicar.

---

### Achado 7 — Detecção da pausa por `status`, enquanto a decisão usa `status OU fato` (defensivo)

`FluxoProcessoService.montarEtapas`/`calcularGating` e
`ProcessoDetalheController` detectam a pausa **só** por
`status == SOLICITA_INFORMACAO`. Já `ProcessoValidator.validarPausaDecisao`
— quem de fato recusa a decisão — usa `status **OU**
temPedidoInformacaoAtivo(processo)`, justamente porque o status é um campo
derivado que já dessincronizou antes (achados C e D do relatório de dois
votos).

**Hoje não há divergência:** os dois caminhos que causavam isso foram
corrigidos (`reabrir` recalcula via `atualizarStatusPorPareceres`;
`registrarEnvio` não tira mais o processo da pausa). Verificado no código,
não presumido.

**Se voltar a divergir**, o sintoma é feio: a tela libera a aba Decisão e o
botão, e o POST recusa com "aguardando informação complementar" — o operador
vê um botão que não funciona, sem entender por quê.

**Correção proposta:** as telas passarem a usar o mesmo predicado da regra
(`temPedidoInformacaoAtivo` em OU com o status). É alinhamento com a fonte
de verdade já existente, não regra nova.

**Risco: BAIXO**, mas é o único achado que muda o **gating** de uma aba —
não juntar com os cosméticos; PR próprio.

---

## 4. Plano de correção proposto

Ordem por valor/risco. **Nada aqui deve ser implementado sem o seu aval.**

| # | Fase | Achados | Arquivos | Risco | Aplicar direto? |
|---|---|---|---|---|---|
| 1 | Pausa vira a etapa atual | 1 | `FluxoProcessoService` | BAIXO | **Não** — PR com revisão |
| 2 | Placar do card de Respostas | 2, 3 | `ProcessoDetalheController` | BAIXÍSSIMO | Pode ir junto com a F1 |
| 3 | Acentuação do badge de status | 4 | `layout.html` + 4 templates | BAIXO | **Não** — conferir E2E |
| 4 | Acentuação de etapas/tooltips | 5 | `FluxoProcessoService`, `ProcessoDetalheController` | BAIXO | **Não** — conferir E2E |
| 5 | `getTom()` de `ENVIADO` | 6 | `StatusProcesso` | BAIXÍSSIMO | Só após reconfirmar o grep |
| 6 | Pausa por fato, não só status | 7 | `FluxoProcessoService`, `ProcessoDetalheController` | BAIXO | **Não** — PR próprio |

**Sugestão de recorte:** F1+F2 num PR (é o que responde ao pedido do dono do
produto e o que de fato muda a operação do dia a dia), F3+F4 num segundo
(acentuação, risco concentrado no E2E), F5+F6 num terceiro (higiene técnica,
sem urgência).

**Nenhuma fase requer migração, backfill ou qualquer escrita em produção.**
Os dados estão corretos; assim que o jar novo subir, o 12/2026 passa a ser
descrito certo, sem nenhum `UPDATE`.

---

## 5. Testes que faltam (e por que a suíte não pegou nada disso)

As ~900 asserções da suíte passam com todos os achados acima presentes. Não
é descuido: `FluxoProcessoServiceTest` cobre a cascata de estados nos
cenários **com maioria** (que é onde a pausa aparece certa), e nenhum teste
existente monta o cenário **1 favorável + 1 solicita-informação + 1
pendente** — a forma exata do 12/2026.

Testes a criar junto com as correções:

1. `FluxoProcessoServiceTest` — pausa **antes** da maioria: a etapa
   `INFO_COMPLEMENTAR` é `ATUAL` e `pendenciaAberta` devolve `Chave
   .INFO_COMPLEMENTAR` (hoje devolveria `RESPOSTAS`). **Este é o teste que
   trava a recaída do Achado 1** — escrevê-lo *antes* da correção e vê-lo
   falhar.
2. O par simétrico (pausa **depois** da maioria) já existe; manter os dois
   lado a lado, com nome explícito, para a assimetria nunca mais voltar
   despercebida.
3. `ProcessoDetalheControllerTest` — `fraseMaioria` menciona a pausa sem
   maioria (Achado 2) e não diz "maioria" em decisão por coordenador
   (Achado 3).
4. Um teste de acentuação sobre os literais de `EtapaFluxo.detalhe`, no
   mesmo espírito de `DesignSystemFontSizeInlineTest` (guarda barata contra
   regressão de texto).

---

## 6. O que NÃO fazer

- **Não rodar nenhum UPDATE em produção.** Os 11 status estão corretos
  (§2). Qualquer "correção de dado" aqui só pode piorar.
- **Não mexer em `ProcessoValidator`, `sugerirDecisao`, `validarPausaDecisao`,
  `decidir`, `tentarDecisaoAutomatica` nem nas constantes de maioria.**
  Nenhum achado deste relatório tem causa nelas.
- **Não acentuar `StatusProcesso.descricao` nem `ResultadoParecer.descricao`.**
  Decisão deliberada e ainda válida: alimentam PDF oficial, dossiê e
  auditoria. Usar tradutor local (Achado 4).
- **Não "resolver" o Achado 1 tirando a etapa Respostas da frente.** A
  etapa Respostas continua legitimamente pendente (o 3º médico realmente não
  votou); o que muda é qual das duas o sistema apresenta como **a ação
  atual**.
- **Não juntar o Achado 7 com os cosméticos** — é o único que altera gating
  de aba.

---

## 7. Resposta direta ao dono do produto

O processo 12/2026 **está no status certo**. Ele está pausado porque a Dra.
Marcia Abichequer votou "Solicita informação" às 20:37 de 11/08, depois de a
Dra. Ana Lúcia já ter votado favorável às 15:49; o terceiro avaliador ainda
não votou. Nessa situação o sistema, por regra, congela a decisão até que a
equipe solicitante mande a informação pedida e o operador clique em "retomar
análise" — e é exatamente isso que o card amarelo da tela está dizendo.

O que está errado é o **resto da tela não concordar com esse card**: o
Painel e a lista de processos dizem que o que falta são "Respostas dos
médicos", e a barra de progresso pinta a informação complementar como uma
etapa futura, cinza. Isso induz a cobrar o 3º médico — o que não resolve
nada, porque mesmo com o voto dele o processo segue travado até a informação
chegar.

O plano acima corrige a exibição, sem tocar em nenhuma regra de votação e
sem alterar um único registro do banco.
