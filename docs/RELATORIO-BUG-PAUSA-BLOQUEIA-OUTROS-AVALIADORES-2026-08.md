# Bug real e grave: "Solicita informação" de UM avaliador trava o voto dos OUTROS DOIS

**Status: CORRIGIDO em 2026-08-06.** A correção seguiu exatamente a direção
recomendada na seção 5 abaixo: `StatusProcesso.aceitaVotoAvaliador()` (novo
método, `true` para `ENVIADO` e `SOLICITA_INFORMACAO`) substituiu a checagem
`status != StatusProcesso.ENVIADO` em `AvaliadorController.
resolverParecerPendente` e em `pendenteAtivoParaVoto`; a query de contagem
`ParecerRepository.
countByMembroIdAndResultadoIsNullAndDataEnvioIsNotNullAndProcessoStatus`
virou `...ProcessoStatusIn` (recebe uma `Collection<StatusProcesso>`), com
`GlobalModelAdvice.pendentesAvaliador()` passando `List.of(ENVIADO,
SOLICITA_INFORMACAO)`. `ProcessoValidator.validarPausaDecisao`/
`tentarDecisaoAutomatica` (a trava da DECISÃO) não foram tocados — continuam
bloqueando só a decisão, como já era correto. O avaliador que causou a pausa
continua impedido de votar de novo (checagem `parecer.getResultado() !=
null`, inalterada, roda antes da checagem de status). Teste de regressão
dedicado: `AvaliadorVotoDuranteSolicitaInformacaoIntegrationTest`
(`@SpringBootTest`, H2 real, sem mock de serviço) — confirma que os outros
dois avaliadores abrem `GET /avaliador/{id}` (200) e votam com sucesso via
`POST /avaliador/{id}/votar` enquanto o processo está em
`SOLICITA_INFORMACAO`, que o processo continua na lista `/avaliador` deles, e
que o avaliador que pediu informação continua bloqueado (403) de votar de
novo. Suíte completa validada: **815 testes, 0 falhas** (JDK 21).

O texto abaixo (seções 1-6) é o relatório de diagnóstico original, mantido
como registro da investigação — reflete o estado ANTES da correção.

Reportado pelo dono do produto em 2026-08-06: *"QUANDO UM MEMBRO SOLICITA
INFORMAÇAO, OS OUTROS MEMBROS NAO CONSEGUEM MAIS VOTAR. ISSO NAO ERA PARA SER
ASSIM"*. Confirmado lendo o código (não reproduzido em ambiente rodando nesta
sessão, mas a cadeia de causa é direta e inequívoca — ver "Como confirmar" no
fim).

## 1. Resumo do bug

Um processo vai a **3 médicos avaliadores**. Se **qualquer um dos três** vota
`SOLICITA_INFORMACAO`, o `Processo` inteiro muda de status para
`SOLICITA_INFORMACAO` — e esse único campo (`Processo.status`) é o mesmo
usado para decidir se **qualquer avaliador, de qualquer processo, pode
votar**. Resultado: os outros 2 médicos, que **não pediram informação
nenhuma** e podem estar prontos para votar, ficam **impedidos de votar** até
o operador concluir manualmente todo o ciclo de "pedir informação ao
solicitante → receber a resposta → clicar em retomar análise" — processo que
pode levar dias.

A regra de negócio documentada (`CLAUDE.md`, seção "Solicita informação
(PAUSA)") diz que a pausa bloqueia **a Decisão**:

> "Isso pausa o fluxo: a Decisão fica bloqueada — `ProcessoService.decidir`
> lança erro ao tentar Deferir/Indeferir (...)"

Não diz — e não deveria implicar — que a pausa bloqueia o **voto** dos outros
dois médicos. Bloquear a decisão final enquanto falta uma resposta faz
sentido (não dá pra decidir com um voto "aberto"). Bloquear os outros dois
médicos de exercer o voto deles, que é independente, não faz sentido nenhum
e não está em nenhum lugar do texto de regras de negócio como algo
pretendido.

## 2. Causa raiz — código exato

### 2.1 O gatilho: qualquer voto "Solicita informação" muda o status do PROCESSO inteiro

`ProcessoService.atualizarStatusPorPareceres`
([ProcessoService.java:191-200](../src/main/java/br/gov/saude/sgpur/service/ProcessoService.java#L191-L200)):

```java
public Processo atualizarStatusPorPareceres(Long id) {
    Processo p = buscar(id);
    if (p.getStatus().isFinalizado()) {
        throw new IllegalStateException(ProcessoValidator.MSG_ENCERRADO);
    }
    boolean pediuInfo = p.getPareceres().stream()
        .anyMatch(par -> par.getResultado() == ResultadoParecer.SOLICITA_INFORMACAO);
    p.setStatus(pediuInfo ? StatusProcesso.SOLICITA_INFORMACAO : StatusProcesso.ENVIADO);
    return processoRepository.save(p);
}
```

`anyMatch` — basta **um** dos três pareceres ter `SOLICITA_INFORMACAO` para o
`Processo` inteiro virar `SOLICITA_INFORMACAO`, independente do estado dos
outros dois (`resultado == null`, ainda não votaram).

### 2.2 A trava: o portal exige `status == ENVIADO` para QUALQUER voto, de QUALQUER médico

`AvaliadorController.resolverParecerPendente`
([AvaliadorController.java:614-637](../src/main/java/br/gov/saude/sgpur/web/AvaliadorController.java#L614-L637)),
chamado tanto por `GET /avaliador/{processoId}` (abrir o formulário de voto)
quanto por `POST /avaliador/{processoId}/votar` (registrar o voto):

```java
StatusProcesso status = parecer.getProcesso().getStatus();
if (status != StatusProcesso.ENVIADO) {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN,
        "Este processo nao esta disponivel para avaliacao (status: "
            + status.getDescricao() + ").");
}
```

Isso roda para **qualquer** `Parecer` do processo, não só o que pediu
informação. Assim que o status vira `SOLICITA_INFORMACAO`, os outros dois
médicos recebem **403 Forbidden** ao tentar abrir a tela de voto, mesmo tendo
`parecer.resultado == null` (nunca votaram).

### 2.3 O mesmo critério também os faz "sumir" da lista de pendências, sem nenhum aviso

`pendenteAtivoParaVoto`
([AvaliadorController.java:561-563](../src/main/java/br/gov/saude/sgpur/web/AvaliadorController.java#L561-L563)):

```java
private static boolean pendenteAtivoParaVoto(Parecer par) {
    StatusProcesso s = par.getProcesso().getStatus();
    return s == StatusProcesso.ENVIADO;
}
```

Usado em **3 lugares**, todos afetados:
- `lista()` — painel do avaliador (`GET /avaliador`): o processo some da
  lista de pendentes do médico, sem nenhuma indicação de que está pausado
  (não aparece em lugar nenhum — simplesmente deixa de existir na tela dele).
- `votar()` — cálculo de "processo X de N pendentes" (mesma filtragem).
- A query de contagem do badge da navbar,
  `ParecerRepository.countByMembroIdAndResultadoIsNullAndDataEnvioIsNotNullAndProcessoStatus`
  ([ParecerRepository.java:44-57](../src/main/java/br/gov/saude/sgpur/repository/ParecerRepository.java#L44-L57)),
  reaproveita **o mesmo critério** documentado explicitamente no próprio
  javadoc: *"processo em status ENVIADO"*. O badge de pendências do médico
  para de contar esse processo.

**Ou seja: para os outros dois avaliadores, o processo não aparece como
"pausado" ou "aguardando" em lugar nenhum — ele simplesmente desaparece.**
Se um deles já tinha o link direto salvo/aberto e clica em votar, cai num
403 técnico sem nenhuma explicação de que é temporário.

### 2.4 Confirmação de que a intenção original era só travar a DECISÃO, não o voto

`ProcessoValidator.validarPausaDecisao` (citado no javadoc de
`registrarEnvio`, [ProcessoService.java:150-161](../src/main/java/br/gov/saude/sgpur/service/ProcessoService.java#L150-L161))
e `ProcessoService.tentarDecisaoAutomatica`
([ProcessoService.java:213-226](../src/main/java/br/gov/saude/sgpur/service/ProcessoService.java#L213-L226))
**já tratam a pausa corretamente, no nível certo**: bloqueiam só a
**decisão** (`decidir`/decisão automática), não o voto:

```java
if (p.getStatus() == StatusProcesso.SOLICITA_INFORMACAO && !temVotoCoordenadorFavoravel(p)) {
    return p; // decisao automatica so nao roda, nao impede votos de acontecerem
}
```

Isso mostra que o desenho da "pausa" sempre foi para a etapa de decisão — o
bloqueio de voto em `AvaliadorController` é um efeito colateral não
intencional de reusar `status == ENVIADO` como proxy de "processo aceita
interação do avaliador", sem diferenciar "aceita voto NOVO" de "aceita
decisão".

## 3. Linha do tempo real de um caso afetado

1. Processo enviado aos 3 médicos (A, B, C). Status: `ENVIADO`.
2. Médico A vota `SOLICITA_INFORMACAO` (com justificativa, obrigatória desde
   2026-08-03). Status do processo vira `SOLICITA_INFORMACAO`.
3. Médico B, que já tinha decidido seu voto e ia votar `Favorável` naquele
   mesmo dia, abre o link do e-mail de convite (ou o painel `/avaliador`) e:
   - não vê mais o processo na lista de pendências, **ou**
   - se tinha a URL direta salva, recebe **403 Forbidden**.
4. Médico C, na mesma situação de B.
5. O processo só volta a aceitar voto de B e C depois que: o solicitante
   responde pelo Portal → o operador registra o recebimento → o operador
   clica em "Retomar análise" (`POST /processos/{id}/retomar-analise`,
   `ProcessoService.retomarAposInformacao`), que só então bota o status de
   volta em `ENVIADO`.
6. Esse intervalo (passo 2 → passo 5) pode ser de **dias**, dependendo de
   quanto tempo o solicitante demora a responder — e durante todo esse
   tempo, dois médicos com voto pronto ficam impedidos de registrá-lo.

## 4. Por que isso é grave

- **Atraso real no processo de urgência renal** — o próprio propósito do
  sistema é agilizar decisões urgentes; travar 2 votos prontos por causa de
  um terceiro pendente contraria isso diretamente.
- **Silencioso**: não há mensagem "aguardando resposta do solicitante,
  volte depois" — o processo some da lista do médico ou estoura um 403 cru.
  Do ponto de vista de B e C, parece que o sistema simplesmente "esqueceu"
  do processo, ou quebrou.
- **Nenhum teste automatizado cobre esse cenário** — buscar por
  "SOLICITA_INFORMACAO" nos testes de `AvaliadorController`/
  `AvaliadorControllerTest` não encontra nenhum caso que registre um voto de
  um segundo avaliador **enquanto** o processo já está pausado por um
  primeiro voto de `SOLICITA_INFORMACAO` (confirmar ao corrigir).

## 5. Direção de correção recomendada (NÃO implementada)

O ajuste mais direto, coerente com o resto do sistema (que já distingue
"pausa bloqueia decisão" de "pausa bloqueia voto"):

- Em `AvaliadorController.resolverParecerPendente`
  ([AvaliadorController.java:629-634](../src/main/java/br/gov/saude/sgpur/web/AvaliadorController.java#L629-L634)),
  trocar `status != StatusProcesso.ENVIADO` por algo como
  `status != StatusProcesso.ENVIADO && status != StatusProcesso.SOLICITA_INFORMACAO`
  (ou um método `StatusProcesso.aceitaVotoAvaliador()` para não espalhar a
  regra em `||`/`&&` soltos).
- Isso é seguro porque `parecer.getResultado() != null` (checado **antes**,
  linhas 624-627) já bloqueia sozinho o avaliador que causou a pausa — ele
  não pode votar de novo até `retomarAposInformacao` limpar o resultado dele
  especificamente. Só quem tem `resultado == null` (os outros dois) passaria
  a conseguir votar durante a pausa.
- `pendenteAtivoParaVoto`
  ([AvaliadorController.java:561-563](../src/main/java/br/gov/saude/sgpur/web/AvaliadorController.java#L561-L563))
  precisa da mesma correção (senão o processo continua sumindo da lista/
  badge mesmo liberando o voto direto por URL — inconsistência pior que o
  bug atual).
- A query `ParecerRepository.countByMembroIdAndResultadoIsNullAndDataEnvioIsNotNullAndProcessoStatus`
  ([ParecerRepository.java:56-57](../src/main/java/br/gov/saude/sgpur/repository/ParecerRepository.java#L56-L57))
  usa um único `StatusProcesso` como parâmetro — passaria a precisar aceitar
  os dois status (`IN (...)`) ou virar duas chamadas somadas; ajustar
  `GlobalModelAdvice.pendentesAvaliador()`, que é quem chama.
- **Não mexe** em `tentarDecisaoAutomatica`/`ProcessoValidator.validarPausaDecisao`
  — essa parte já está correta e deve continuar bloqueando a decisão
  enquanto o pedido de informação não for resolvido, mesmo que os outros
  dois já tenham votado nesse meio-tempo.
- Vale revisar `avaliador/lista.html`/`avaliador/votar.html` para dar
  alguma pista visual quando o processo estiver pausado por outro colega
  (hoje nem essa mensagem existe) — ou, ao corrigir o gate, o problema
  desaparece por completo (o processo simplesmente continua votável
  normalmente, sem precisar explicar pausa nenhuma pros outros dois).
- **Teste de regressão necessário** (padrão do projeto: rota que grava algo
  irreversível exige `@SpringBootTest` sem mock do serviço, ver CLAUDE.md):
  processo com 3 avaliadores, um vota `SOLICITA_INFORMACAO`, confirma que os
  outros dois **conseguem** abrir `GET /avaliador/{id}` e votar com sucesso
  via `POST /avaliador/{id}/votar` enquanto o processo está em
  `SOLICITA_INFORMACAO`; e que o avaliador que pediu a informação continua
  bloqueado de votar de novo até `retomarAposInformacao`.

## 6. Como confirmar em ambiente rodando (para quem for corrigir)

1. Processo com 3 avaliadores, enviado.
2. Avaliador A vota `SOLICITA_INFORMACAO` (com justificativa).
3. Como avaliador B: `GET /avaliador` → processo não aparece na lista.
   `GET /avaliador/{processoId}` direto → `403 Forbidden`,
   "Este processo nao esta disponivel para avaliacao (status: ...)".
4. Confirma que `parecer` de B continua com `resultado == null` no banco —
   ele não votou, só foi impedido de votar.
