package br.gov.saude.sgpur.e2e.pages;

import br.gov.saude.sgpur.e2e.Legenda;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FilePayload;

/**
 * Page Object da tela de detalhe do processo (wizard de 4 passos: Envio,
 * Respostas, Decisao, Finalizacao). Cada metodo corresponde a uma acao que
 * um operador humano realizaria clicando na tela - nao chama nenhum
 * endpoint diretamente.
 *
 * <p><b>O Recebimento nao tem passo/aba propria desde 2026-08-05</b> - era
 * sempre automatico e concluido (todo processo nasce de uma
 * SolicitacaoOnline convertida pelo operador), sem nenhuma acao manual, e
 * foi fundido na aba Envio (agora o passo 1). Ver
 * {@code FluxoProcessoService}/CLAUDE.md.
 */
public class ProcessoDetalhePage {

    private final Page page;

    public ProcessoDetalhePage(Page page) {
        this.page = page;
    }

    private void narrar(String texto) {
        Legenda.mostrar(page, texto);
    }

    private void clicarPasso(String paneId) {
        page.locator(".wizard-step[href='#" + paneId + "']").click();
    }

    // ===== Passo 1: Envio (inclui o Recebimento automatico) =====

    public ProcessoDetalhePage passo1_anexarDocumentoClinico(FilePayload documentoClinico) {
        narrar("Passo 1/4 - Envio: anexando o documento clinico anonimizado...");
        clicarPasso("pane-envio");
        // Seletor ANCORADO NO FIM da URL (action$=): desde 2026-08-06 a area de
        // revisao da trava de anonimizacao tambem tem um <form> de upload cuja
        // action contem "documento-clinico"
        // (.../documento-clinico/{anexoId}/substituir) - um action*= aqui passaria
        // a casar os dois e o modo estrito do Playwright falharia. Este e o
        // formulario generico de documento clinico, cuja action TERMINA em
        // /documento-clinico.
        page.locator("#pane-envio form[action$='/documento-clinico'] input[name=arquivo]").setInputFiles(documentoClinico);
        page.locator("#pane-envio button:has-text('Anexar documento clínico')").click();
        page.waitForLoadState();
        return this;
    }

    /**
     * "Registrar envio" ganhou confirmacao por modal na Fase 4 do relatorio
     * de clareza (2026-08-05, item 4.3): dispara e-mail real e irreversivel
     * aos avaliadores, mesma protecao ja usada em
     * {@link #passo4_confirmarRespostaAoSolicitante()}. O texto do botao nao
     * mudou - so o clique precisa passar pelo modal antes de seguir.
     */
    public ProcessoDetalhePage passo1_registrarEnvio() {
        narrar("Passo 1/4 - Envio: registrando o envio aos 3 avaliadores...");
        clicarPasso("pane-envio");
        page.locator("#pane-envio button:has-text('Registrar envio')").click();
        page.locator("#btnConfirmarAcaoFinal").click();
        page.waitForLoadState();
        return this;
    }

    // ===== Passo 2: Respostas (registradas pelos proprios avaliadores no Portal
    //       do Avaliador - ver AvaliadorPage - nao ha mais acao do operador aqui) =====

    // ===== Passo 3: Decisao =====
    //
    // O sistema decide sozinho (ProcessoService.tentarDecisaoAutomatica,
    // chamado por AvaliadorController logo apos cada voto) assim que a
    // maioria simples se forma (2 favoraveis defere, 2 desfavoraveis
    // indefere; ou o coordenador CET-RS vota favoravel sozinho). O oficio de
    // indeferimento NAO sai daqui: e sempre anexado pelo operador no passo 4
    // (2026-08-04). O formulario manual
    // abaixo (`#decisaoSelect`) SO aparece quando o processo AINDA NAO esta
    // finalizado (th:unless="${processo.status.finalizado}" no template) -
    // usado para Cancelado (nunca automatico) ou para redecidir apos uma
    // reabertura pelo ADMIN. No cenario feliz de 2/3 votos formando maioria,
    // nunca chame este metodo - o processo ja chega finalizado sozinho.
    public ProcessoDetalhePage passo3_decidir(String decisao) {
        return passo3_decidir(decisao, null);
    }

    public ProcessoDetalhePage passo3_decidir(String decisao, String motivoIndeferimento) {
        narrar("Passo 3/4 - Decisao: registrando a decisao final (" + decisao + ")...");
        clicarPasso("pane-decisao");
        page.locator("#decisaoSelect").selectOption(decisao);
        if (motivoIndeferimento != null) {
            page.locator("#motivoIndeferimentoInput").fill(motivoIndeferimento);
        }
        page.locator("#decisao button:has-text('Registrar decisão')").click();
        page.locator("#btnConfirmarAcaoFinal").click();
        page.waitForLoadState();
        return this;
    }

    // ===== Passo 4: Finalizacao =====

    /**
     * Anexa o Oficio de Indeferimento (obrigatorio so quando INDEFERIDO).
     * Desde 2026-08-04 o oficio NAO e mais gerado pelo sistema: o operador
     * baixa um rascunho editavel, ajusta no Word e anexa o documento final -
     * o anexo e o unico oficio valido do processo.
     */
    public ProcessoDetalhePage passo4_anexarOficioIndeferimento(FilePayload oficio) {
        narrar("Passo 4/4 - Finalizacao: anexando o oficio de indeferimento assinado...");
        clicarPasso("pane-finalizacao");
        page.locator("#finalizacao form[action*='oficio-upload'] input[name=arquivo]").setInputFiles(oficio);
        page.locator("#finalizacao form[action*='oficio-upload'] button").click();
        page.waitForLoadState();
        return this;
    }

    /** Anexa/substitui o comprovante SNT (obrigatorio so quando DEFERIDO - nao e gerado pelo sistema). */
    public ProcessoDetalhePage passo4_anexarComprovanteSnt(FilePayload comprovanteSnt) {
        narrar("Passo 4/4 - Finalizacao: anexando o comprovante de insercao no SNT...");
        clicarPasso("pane-finalizacao");
        page.locator("#finalizacao form[action*='comprovante-snt'] input[name=arquivo]").setInputFiles(comprovanteSnt);
        page.locator("#finalizacao form[action*='comprovante-snt'] button").click();
        page.waitForLoadState();
        return this;
    }

    /**
     * Clica no botao unico "Enviar Resposta ao Solicitante"
     * (POST /processos/{id}/finalizar): dispara o e-mail com o template
     * certo (deferido/indeferido) + o anexo correspondente, tudo automatico
     * - nao ha mais checkbox de confirmacao nem upload manual de
     * "comprovante de envio" nesta etapa. So habilitado quando o documento
     * obrigatorio da decisao (Oficio de Indeferimento ou Comprovante SNT) ja
     * estiver anexado. O form tem data-confirm-msg (acao irreversivel), entao
     * o clique abre o modal generico de confirmacao (ver
     * static/js/confirmar-acao.js) - precisa confirmar antes do submit real.
     */
    public ProcessoDetalhePage passo4_confirmarRespostaAoSolicitante() {
        narrar("Passo 4/4 - Finalizacao: enviando a resposta final ao solicitante...");
        clicarPasso("pane-finalizacao");
        page.locator("#finalizacao form[action*='/finalizar'] button").click();
        page.locator("#btnConfirmarAcaoFinal").click();
        page.waitForLoadState();
        return this;
    }

    // ===== Asserts / leitura de estado =====

    /** true se o passo (1 a 4) esta marcado como concluido (classe .concluida) na barra do wizard. */
    public boolean passoConcluido(int numero) {
        return page.locator(".wizard-step:nth-child(" + numero + ")")
            .getAttribute("class").contains("concluida");
    }

    public String statusAtual() {
        return page.locator("[data-testid=status-processo]").count() > 0
            ? page.locator("[data-testid=status-processo]").innerText()
            : page.locator("h1").innerText();
    }

    /**
     * Clica de verdade no botao "Relatorio Final (PDF)" (target=_blank) e
     * espera a nova aba abrir, para quem esta acompanhando em modo headed
     * VER o PDF sendo renderizado na tela pelo visualizador nativo do
     * Chrome - e o comportamento real de um operador clicando no botao.
     *
     * <p>So funciona de forma visivel em modo HEADED: o Chromium headless
     * nao tem visualizador de PDF embutido e trata a mesma resposta como
     * DOWNLOAD em vez de navegar para uma pagina - {@code waitForLoadState()}
     * nunca dispara o evento "load" nesse caso e trava ate o timeout (60s).
     * Por isso so esperamos o load state quando {@code headed}; em headless
     * basta confirmar que a aba abriu (a URL ja fica disponivel antes do
     * load terminar).
     */
    public Page abrirRelatorioFinal(boolean headed) {
        narrar("Abrindo o Relatorio Final (PDF) gerado pelo sistema...");
        Page abaRelatorio = page.context().waitForPage(() ->
            page.locator("a.btn:has-text('Relatório Final (PDF)')").click());
        if (headed) {
            abaRelatorio.waitForLoadState();
        }
        return abaRelatorio;
    }

    public Page raw() {
        return page;
    }
}
