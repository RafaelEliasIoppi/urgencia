# Protocolo de teste manual em produção — SAUR

Roteiro para o usuário executar manualmente contra
**https://urgenciarenal.duckdns.org/** (produção real, dados de saúde/LGPD).
Cobre as regras de negócio documentadas em `CLAUDE.md` na ordem em que
aparecem no fluxo. Marque cada caixa `[ ]` conforme for testando.

> Atualizado em 2026-07-31 para refletir o fluxo real do sistema hoje: **todo
> processo nasce obrigatoriamente de uma solicitação do Portal do
> Solicitante** (não existe mais cadastro manual "do zero"), e **parecer só
> entra pelo Portal do Avaliador** (não existe mais lançamento manual pelo
> operador). Ver `CLAUDE.md` para o texto completo das regras.

## 0. Antes de começar — leia isto

- **Não há modo teste de e-mail em produção.** `app.mail.override-recipient`
  só existe em dev (`application.yml`) — em prod
  (`application-prod.yml`) é explicitamente vazio, então **todo e-mail
  disparado neste teste vai para o destinatário real calculado pelo
  sistema** (avaliadores/solicitantes de verdade), sem redirecionamento.
- **Use dados de paciente claramente fictícios**, nunca um paciente real:
  - Nome: `TESTE QA APAGAR` (ou similar, óbvio de identificar depois)
  - RGCT: `000000-0` (ou qualquer número que não exista de verdade)
  - Equipe solicitante: pode usar uma equipe real (só afeta o campo
    "solicitante", não recebe e-mail automaticamente neste fluxo, exceto se
    você testar "Solicita informação" ou "Resposta ao solicitante" — nesses
    casos, veja o próximo ponto).
- **Antes de decidir quem serão os "3 médicos avaliadores", o "e-mail do
  solicitante" e a conta de teste do Portal do Solicitante**, escolha:
  - **(A) Recomendado:** cadastre um membro temporário em `/membros` com um
    e-mail que você mesmo controla (ex.: um alias seu), marque-o como
    coordenador se quiser testar aquele caminho, e use esse membro nos 3
    slots de avaliador do processo de teste. Cadastre também um usuário
    `AVALIADOR` de teste vinculado a esse membro (`/usuarios`) e um usuário
    `SOLICITANTE` de teste (`/usuarios`, perfil Solicitante, com "equipe
    solicitante" e e-mail que você controla) para submeter o pedido pelo
    Portal do Solicitante. Assim nenhum e-mail de teste cai na caixa de
    ninguém de verdade.
  - **(B)** Use avaliadores/e-mail de solicitante reais, mas avise antes por
    fora do sistema que vão receber um e-mail de teste (o assunto **não**
    tem prefixo `[TESTE]` em produção, diferente do dev).
- **Limpeza ao final:** o processo de teste fica no banco de produção real.
  Ao terminar, **exclua o processo** (ADMIN, botão de exclusão) ou pelo
  menos deixe claro no nome do paciente que é lixo de teste, para não
  confundir estatísticas/relatórios reais (Relatório Anual, tempo de
  resposta).
- **PDFs de anexo prontos:** use os arquivos em `teste-pdfs/` (gerados por
  `teste-pdfs/gerar.ps1`). A tabela abaixo mostra qual usar em cada etapa.
  **Atenção:** essa pasta também contém arquivos de um fluxo antigo que **não
  existe mais** (`solicitacao-recebida.pdf`, `email-enviado-avaliadores.pdf`,
  `resposta-avaliador-1/2/3.pdf`) — os tipos de anexo que eles representavam
  (`SOLICITACAO_RECEBIDA`, `CAPA_PROCESSO`, `EMAIL_ENVIADO_AVALIADORES`,
  `RESPOSTA_AVALIADOR`) foram **removidos por completo do enum `TipoAnexo`**
  no commit `041dc43` (2026-07-29) — **não use esses 4 arquivos**, eles não
  correspondem a nada no sistema atual e o próprio `gerar.ps1` ainda os gera
  por não ter sido atualizado junto (fora do escopo desta vistoria de
  documentação — sinalizar para quem cuida do script). Se precisar de mais
  cópias dos arquivos válidos (ex.: testar upload de vários documentos
  clínicos), rode `.\teste-pdfs\gerar.ps1` de novo ou duplique os arquivos.

| Arquivo | Tipo de anexo (`TipoAnexo`) | Usado no passo |
|---|---|---|
| `documento-clinico-1.pdf`, `documento-clinico-2.pdf` | `DOCUMENTO_CLINICO_AVALIADOR` | 4. Envio |
| `info-complementar.pdf` | `INFO_COMPLEMENTAR` | Fluxo "Solicita informação" (opcional) |
| `oficio-indeferimento.pdf` | `OFICIO_INDEFERIMENTO` | 7. Ofício (só se Indeferido) |
| `comprovante-snt.pdf` | `COMPROVANTE_SNT` | 7. Comprovante SNT (só se Deferido) |
| `comprovante-envio.pdf` | `COMPROVANTE_ENVIO_SOLICITANTE` (opcional, ver seção 8) | 8. Resposta ao solicitante |

---

## 1. Login e perfis

- [ ] Login como `admin` (a senha atual é a que você definiu — não está
      documentada aqui de propósito).
- [ ] Confirme que o menu do ADMIN mostra todas as áreas: Processos,
      Controle de Urgências, Membros, Usuários, Auditoria, Relatórios.
- [ ] Em `/usuarios/minha-senha`, teste trocar a própria senha e depois
      volte pra senha que você quer manter (ou deixe a nova, sua escolha).
- [ ] Crie (ou confirme que já existem) usuários de teste dos **3 perfis
      operacionais**: `OPERADOR`, `AVALIADOR` (vinculado a um `MembroUrgenciaRenal`)
      e `SOLICITANTE` (com "equipe solicitante" preenchida). Faça logout e
      login como cada um, confirmando:
  - [ ] OPERADOR **não** vê `/usuarios` nem `/auditoria` no menu.
  - [ ] AVALIADOR só vê `/avaliador`, nada de área operacional.
  - [ ] SOLICITANTE só vê `/solicitante`, nada de área operacional.
  - [ ] Tentar acessar `/usuarios`, `/auditoria` ou `/processos` direto pela
        URL como OPERADOR/AVALIADOR/SOLICITANTE (fora do que cada um pode)
        deve dar 403.

## 2. Solicitação online + triagem (nasce todo processo)

**Não existe mais `/processos/novo` "do zero".** `GET/POST /processos`
(`ProcessoDetalheController.novo`/`salvar`) **exigem**
`origemSolicitacaoOnlineId` — sem isso, redirecionam para a fila de triagem
com um erro. Desde 2026-07-27, **todo** `Processo` nasce de uma
`SolicitacaoOnline` enviada pelo Portal do Solicitante e depois convertida
pelo operador. O teste começa por aí:

- [ ] Login como o `SOLICITANTE` de teste. Em `/solicitante/nova`, preencha
      paciente `TESTE QA APAGAR`, RGCT `000000-0`, data da situação especial
      e a justificativa clínica. Opcionalmente anexe
      `documento-clinico-1.pdf` como documento clínico já neste formulário
      (isso testa o caminho "documento veio do Portal", ver próxima nota).
      Envie.
- [ ] Confirme a mensagem "Solicitação enviada. Aguarde a triagem..." e que
      a solicitação aparece em `/solicitante` com status `Enviada`.
- [ ] Login como ADMIN ou OPERADOR. Em `/processos/solicitacoes-online`,
      confirme que a solicitação de teste aparece na fila.
- [ ] Abra o detalhe da solicitação. Clique em "Converter" — isso leva a
      `/processos/novo?origemSolicitacaoOnlineId=...`, com os dados do
      solicitante **pré-preenchidos** (paciente, RGCT, e-mail, equipe,
      justificativa).
- [ ] Complete o formulário: escolha **3 avaliadores** (o membro de teste da
      seção 0, repetido ou com 3 membros de teste). Se for testar a exceção
      do coordenador, um dos 3 deve ter `coordenador = true` marcado em
      `/membros`. Salve.
- [ ] Confirme que o processo nasce com status `Solicitado`, que a
      solicitação de origem virou `Convertida`, e que a tela de detalhe do
      processo mostra o link "Ver solicitação original".
- [ ] **(Opcional) Teste "Devolver":** em outra solicitação de teste, use o
      botão "Devolver" na fila de triagem em vez de converter, e confirme
      que ela volta para o solicitante pedindo correção.

## 3. Passo 1 — Recebimento (sempre automático, nada a testar aqui)

- [ ] Confirme apenas que a etapa "Recebimento" já aparece **verde/concluída**
      na timeline assim que o processo é criado, sem nenhum upload manual —
      desde 2026-07-27 esta etapa não depende de nenhum anexo (o antigo
      upload da "solicitação recebida" + geração automática da capa do
      processo foram removidos junto com o cadastro manual).
- [ ] Confirme que a aba "Envio" já está liberada desde o início.

## 4. Passo 2 — Envio

Se você anexou um documento clínico **pelo Portal do Solicitante** (seção 2),
ele chega ao processo como `DOCUMENTO_PORTAL_NAO_ANONIMIZADO` — um tipo de
"staging" que **nunca** entra no PDF enviado aos avaliadores até o operador
confirmar explicitamente a anonimização. Teste os dois caminhos:

- [ ] **Se usou o documento do Portal:** na aba Envio, confirme que ele
      aparece numa lista separada ("pendente de anonimização"). Clique em
      "Confirmar anonimização" (checkbox "Confirmo que este documento foi
      anonimizado" + botão). Confirme que ele passa a contar como documento
      clínico válido e que a ação fica registrada em auditoria
      (`ANONIMIZACAO_CONFIRMADA`).
- [ ] **Documento direto (mais simples para teste):** anexe
      `documento-clinico-1.pdf` e `documento-clinico-2.pdf` via
      "Anexar documento clínico" — esses entram direto como
      `DOCUMENTO_CLINICO_AVALIADOR`, sem passar pela trava de anonimização.
- [ ] **Teste o bloqueio:** tente "Registrar envio" com **zero** documentos
      clínicos válidos (nem confirmados, nem diretos) — deve dar erro e não
      efetivar. Se só houver documento pendente de anonimização, a mensagem
      deve orientar a confirmar a anonimização (não apenas "anexe um
      documento").
- [ ] Registre o envio. Confirme:
  - [ ] Processo muda para `Enviado`.
  - [ ] O PDF único anonimizado (`Processo CET-RS NN-2026 - Paciente T.Q.A.pdf`
        ou similar, com iniciais) é gerado, mesclando os documentos clínicos
        válidos com o cabeçalho carimbado.
  - [ ] O nome do arquivo gerado usa **iniciais**, nunca o nome completo.
  - [ ] Um convite automático ao Portal do Avaliador é disparado para cada
        um dos 3 avaliadores com parecer pendente (flash de sucesso cita
        quantos convites saíram; se algum avaliador de teste não tiver
        e-mail cadastrado, aparece um aviso nomeando quem ficou de fora).
- [ ] **Teste o aviso de conflito de equipe** (não bloqueia): se algum dos
      3 avaliadores de teste for da mesma instituição da equipe
      solicitante escolhida, deve aparecer um aviso não-bloqueante na tela.
- [ ] **Não há mais** requisito de anexar "comprovante de envio aos
      avaliadores" — esse anexo (`EMAIL_ENVIADO_AVALIADORES`) foi removido
      do sistema em 2026-07-27/29; não procure esse formulário, ele não
      existe mais.

## 5. Passo 3 — Respostas dos avaliadores

**Parecer só entra pelo Portal do Avaliador — não existe mais lançamento
manual pelo operador.** Os antigos endpoints/forms que permitiam ao operador
digitar o resultado (com upload de comprovante) foram removidos por completo
em 2026-07-27/29, junto com `OrigemParecer.OPERADOR_EMAIL` e
`TipoAnexo.RESPOSTA_AVALIADOR`.

### 5a. Via Portal do Avaliador (login autenticado) — único caminho

- [ ] Faça logout, login como o `AVALIADOR` de teste vinculado a um dos 3
      membros do processo.
- [ ] Em `/avaliador`, confirme que o processo aparece na lista **só com
      iniciais** do paciente, sem nome completo, sem nome da equipe
      solicitante, sem ver quem são os outros avaliadores.
- [ ] Vote (Favorável/Desfavorável/Solicita informação) direto pelo portal —
      sem precisar anexar nada (o voto autenticado substitui qualquer
      anexo comprobatório).
- [ ] Confirme em `/auditoria` (como ADMIN) que o voto ficou registrado com
      IP e `origem = AVALIADOR_SISTEMA` (auditoria `PARECER_VOTADO`).
- [ ] Repita o login/voto para os outros 2 avaliadores de teste (usando
      contas diferentes ou reaproveitando a mesma, se o membro de teste for
      o único vinculado — nesse caso, vote nos outros 2 processos-slot por
      fora do fluxo real ou monte 3 usuários avaliadores de teste distintos
      se quiser cobrir os 3 votos de fato).
- [ ] Confirme que, assim que 2 dos 3 votos formam maioria (2 favoráveis ou
      2 desfavoráveis), a decisão automática dispara sozinha **sem** nenhum
      clique do operador (ver seção 6) — ou, se preferir testar a decisão
      manual, pare em 2 votos e confira que a aba Decisão já libera.

### 5b. Fluxo "Solicita informação" (opcional, mas recomendado testar 1x)

- [ ] Em vez de Favorável/Desfavorável, um dos avaliadores vota
      **Solicita informação** direto no Portal do Avaliador.
- [ ] Confirme que o processo entra em status `Solicita informação` e a
      aba "Decisão" fica **bloqueada** (tentar Deferir/Indeferir deve dar
      erro) — **exceto** se o coordenador já tiver votado Favorável nesse
      meio tempo, caso em que Deferir continua liberado mesmo pausado
      (regra do coordenador tem prioridade sobre a pausa; Indeferir
      continua bloqueado).
- [ ] Confirme que o e-mail pronto "Pedido de informação complementar"
      aparece com o **nome completo** do paciente (diferente do material
      dos avaliadores).
- [ ] Pelo lado do solicitante (login como o `SOLICITANTE` de teste, em
      `/solicitante/{id}`), envie a informação complementar pelo formulário
      próprio — vira o anexo `INFO_COMPLEMENTAR` visível ao operador. (Como
      alternativa mais rápida de testar, o operador também pode anexar
      `info-complementar.pdf` direto ao registrar o recebimento, na aba
      Respostas.)
- [ ] Como operador, use "Retomar análise". Confirme que o processo volta
      para `Enviado` e o parecer que estava "Solicita informação" foi
      reaberto para voto definitivo (vote de novo pelo Portal do Avaliador
      para fechar a maioria).

## 6. Passo 4 — Decisão

A decisão automática dispara sozinha assim que a maioria se forma (no
momento do voto, e também numa varredura periódica de segurança) — mas o
operador também pode decidir manualmente pela tela. Teste os 3 caminhos
possíveis (pode ser em 3 processos de teste diferentes):

- [ ] **Maioria simples Deferido** (2 de 3 favoráveis): confirme a decisão
      automática (ou decida manualmente), e confirme bloqueio se tentar
      decidir manualmente com só 1 favorável.
- [ ] **Indeferido** (2 de 3 desfavoráveis): confirme que decidir Indeferido
      **exige** motivo preenchido.
- [ ] **Exceção do coordenador**: com o membro marcado `coordenador = true`
      votando Favorável sozinho (sem esperar os outros 2), confirme que o
      processo já defere imediatamente com esse único voto, e que o
      detalhe mostra o badge "Deferido pelo Coordenador da CET-RS".
      Confirme também que esse mesmo coordenador **não** tem poder especial
      para Indeferir sozinho (ainda exige 2 desfavoráveis), e que o sistema
      **veda** indeferir manualmente se o coordenador já votou favorável
      (mesmo com 2 desfavoráveis registrados).

## 7. Passo 5 — Ofício / Comprovante SNT

- [ ] Se Indeferido: anexe `oficio-indeferimento.pdf`. Confirme que a etapa
      só fecha depois disso (mais a data de emissão do ofício).
- [ ] Se Deferido: anexe `comprovante-snt.pdf`. Confirme que a etapa só
      fecha depois disso.

## 8. Passo 6 — Resposta ao solicitante

Esta etapa hoje é uma **ação única**: o botão "Finalizar" dispara o e-mail
automaticamente (template Deferido/Indeferido + o anexo obrigatório já
embutido) e marca a resposta como enviada — não é mais "gerar o e-mail
pronto e confirmar" em dois passos manuais.

- [ ] Confirme que o botão "Finalizar" só fica disponível depois do anexo
      obrigatório (ofício ou comprovante SNT, conforme a decisão) existir.
- [ ] Clique em "Finalizar". Confirme que o e-mail foi enviado (destinatário
      = e-mail do solicitante de teste) com o **nome completo** do paciente
      (não iniciais) e o anexo correto embutido.
- [ ] Confirme que o processo fica marcado com a resposta enviada e que
      tentar "Finalizar" de novo dá erro ("Resposta já foi enviada").
- [ ] **Opcional:** anexe também `comprovante-envio.pdf` como
      `COMPROVANTE_ENVIO_SOLICITANTE` (print do e-mail) — esse upload
      continua disponível, mas **não é mais exigido** para a etapa fechar.

## 9. Processo encerrado — trava de edição

- [ ] Com o processo já em status final, tente editar dados básicos,
      re-anexar documento clínico, ou redecidir — tudo deve ser **rejeitado**
      com a mensagem de processo encerrado.
- [ ] Confirme que **ainda é possível**: anexar ofício/comprovante SNT (se
      faltou), reenviar a resposta ao solicitante (se ainda não enviada),
      fazer downloads/relatórios.
- [ ] Como ADMIN, use "Reabrir processo" e confirme que volta para
      `Enviado` e a edição é liberada de novo.

## 10. Cancelamento

O cancelamento pode ser feito por dois lados — teste os dois se possível:

- [ ] **Pelo operador:** em outro processo de teste, decida `Cancelado`
      (mesmo endpoint de decisão) e confirme que também é um status final
      (mesma trava de edição do item 9).
- [ ] **Pelo solicitante:** crie outra solicitação/processo de teste e, como
      `SOLICITANTE`, use "Cancelar pedido" em `/solicitante/{id}` **antes**
      da decisão final (funciona tanto com a solicitação ainda `Enviada`
      quanto já `Convertida` num processo ainda não decidido — depois de
      Deferido/Indeferido não cancela mais). Confirme que os avaliadores
      pendentes recebem um e-mail de aviso do cancelamento (só iniciais) e
      que o Portal do Solicitante mostra o status como "Cancelada" (não
      "Reprovada").

## 11. Relatórios e indicadores

- [ ] `/processos/{id}/relatorio` (Relatório Final de um processo) — confirme
      o PDF gerado.
- [ ] `/relatorios/anual` — gere o relatório anual, confirme que o processo
      de teste aparece (e lembre de filtrar/desconsiderar depois de
      limpar).
- [ ] `/relatorios/avaliador` — confirme que aparece o indicador por
      avaliador.
- [ ] Painel (`/`) — confirme o card "Tempo de resposta" (média geral +
      contagem fora do prazo) refletindo os pareceres de teste.
- [ ] `/membros` — confirme a coluna de tempo de resposta por avaliador.

## 12. Controle de Urgências (módulo separado)

- [ ] `/controle-urgencias` — cadastre um registro de teste, edite,
      confirme responsividade da lista (`table-responsive`, botões de ação
      não estourando em mobile).

## 13. Portal do Solicitante — estado real em produção (AMBÍGUO, ver nota)

O Portal do Solicitante é controlado pela flag `app.solicitante.habilitado`
(env var `SGPUR_SOLICITANTE_HABILITADO`). O código tem defaults diferentes
por perfil:

- `application.yml` (dev): `${SGPUR_SOLICITANTE_HABILITADO:true}` — **ligado
  por padrão em dev.**
- `application-prod.yml`: `${SGPUR_SOLICITANTE_HABILITADO:false}` — **default
  do perfil prod é desligado**, comentado no próprio arquivo como "módulo
  experimental... até o time decidir usar".
- `deploy/sgpur.env.example` (o modelo do arquivo de env real da VM) **não
  define `SGPUR_SOLICITANTE_HABILITADO` em lugar nenhum.**

**Não foi possível confirmar neste levantamento qual é o valor efetivo em
produção hoje**, porque o `sgpur.env` real da VM está fora do repositório
(gitignored, só existe no servidor) — não dá para inspecioná-lo por aqui.

Há, porém, um indício forte de que está **ligado** (`true`) na VM: desde
2026-07-27 **todo** `Processo` só pode ser criado a partir de uma
`SolicitacaoOnline` convertida (`ProcessoDetalheController.novo`/`salvar`
retornam erro sem `origemSolicitacaoOnlineId`, e o próprio endpoint fica
condicionado ao módulo — se estivesse desligado, seria **impossível cadastrar
qualquer processo novo em produção**, o que contradiz o sistema estar em uso
normal desde então). Mas isso é inferência, não confirmação direta.

**Antes de testar esta seção, confirme o estado real rodando:**
```
GET https://urgenciarenal.duckdns.org/solicitante
```
como usuário deslogado ou com perfil que não seja SOLICITANTE:
- Se o módulo estiver **desligado**, a rota mostra uma **tela de aviso**
  (`solicitante/indisponivel.html`, via `SolicitanteIndisponivelController`)
  — **não é mais um 404 cru** (isso mudou depois que um usuário SOLICITANTE
  cadastrado ficava sem nenhum lugar navegável ao logar com o módulo
  desligado; corrigido em 2026-07-26). Se você vir essa tela de aviso em vez
  do formulário de nova solicitação, o módulo está desligado e as seções 2,
  5b (parte do solicitante) e 10 (cancelamento pelo solicitante) deste
  protocolo não se aplicam até alguém ligar a flag na VM.
- Se aparecer o formulário/login do Portal do Solicitante normalmente, o
  módulo está ligado — pode seguir o protocolo como descrito.

## 14. Responsividade (mobile)

- [ ] Abra o site no celular (ou DevTools em modo responsivo, ~375-390px):
      navbar, detalhe do processo, listas — confirme que nada corta ou
      estoura horizontalmente.

## 15. Limpeza final

- [ ] Exclua (ADMIN) todos os processos de teste criados (`TESTE QA
      APAGAR`), ou documente claramente que ainda existem para não confundir
      relatórios reais.
- [ ] Remova/inative os membros e usuários de teste criados na seção 0/1,
      se não forem reaproveitáveis.
- [ ] Confirme em `/auditoria` que as ações de teste ficaram registradas
      (é esperado — não precisa limpar o log de auditoria).
