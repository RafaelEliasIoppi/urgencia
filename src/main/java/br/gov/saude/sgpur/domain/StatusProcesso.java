package br.gov.saude.sgpur.domain;

/**
 * Situacao administrativa do Processo de Urgencia Renal, refletindo o fluxo
 * real (planilha) em 10 etapas:
 *
 *   SOLICITADO -> ENVIADO -> { DEFERIDO, INDEFERIDO, SOLICITA_INFORMACAO }
 *   (+ CANCELADO a qualquer momento).
 *
 * O processo nasce SOLICITADO (e-mail recebido, registro criado com os 3
 * medicos), passa a ENVIADO quando a solicitacao e enviada aos avaliadores e
 * termina em uma decisao. SOLICITA_INFORMACAO e um estado intermediario (um
 * medico pediu mais dados) que ainda nao e final.
 */
public enum StatusProcesso {
    SOLICITADO("Solicitado"),
    ENVIADO("Enviado"),
    SOLICITA_INFORMACAO("Solicita informacao"),
    DEFERIDO("Deferido"),
    INDEFERIDO("Indeferido"),
    CANCELADO("Cancelado");

    private final String descricao;

    StatusProcesso(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }

    /**
     * Estados finais (encerram o processo): DEFERIDO, INDEFERIDO e CANCELADO.
     * SOLICITADO, ENVIADO e SOLICITA_INFORMACAO ainda estao em andamento.
     */
    public boolean isFinalizado() {
        return this == DEFERIDO || this == INDEFERIDO || this == CANCELADO;
    }

    /** Indica se o processo ainda esta em andamento (nao finalizado). */
    public boolean isEmAndamento() {
        return !isFinalizado();
    }

    /**
     * Indica se o processo aceita voto NOVO de avaliador (abrir a tela de
     * voto e registrar um parecer). Inclui {@code ENVIADO} (fluxo normal) e
     * {@code SOLICITA_INFORMACAO} (pausa causada por UM avaliador pedir
     * informacao complementar) - a pausa bloqueia a DECISAO
     * ({@code ProcessoValidator.validarPausaDecisao}/
     * {@code tentarDecisaoAutomatica}), nao o voto dos outros dois medicos,
     * que e independente. O avaliador que causou a pausa continua bloqueado
     * de votar de novo por outra checagem, em {@code AvaliadorController}
     * ({@code parecer.getResultado() != null}), nao por este metodo.
     */
    public boolean aceitaVotoAvaliador() {
        return this == ENVIADO || this == SOLICITA_INFORMACAO;
    }

    /** Bootstrap-icon (sem o prefixo "bi-") usado no badge do status. */
    public String getBadgeIcone() {
        return switch (this) {
            case SOLICITADO -> "inbox-fill";
            case ENVIADO -> "send-fill";
            case SOLICITA_INFORMACAO -> "question-circle-fill";
            case DEFERIDO -> "check-circle-fill";
            case INDEFERIDO -> "x-circle-fill";
            case CANCELADO -> "slash-circle-fill";
        };
    }

    /**
     * Classe de cor do Bootstrap (badge bg-*) para as telas que usam Bootstrap.
     *
     * @deprecated acopla o dominio a uma classe CSS de um framework especifico
     * - use {@link #getTom()} (vocabulario semantico "ok"/"danger"/
     * "attention"/"neutral") e traduza para Bootstrap no template, via o
     * fragment {@code layout :: tomBadge(tom, texto, icone)}. Mantido por
     * enquanto porque templates existentes ainda leem este metodo diretamente
     * e nao foram migrados nesta leva (infraestrutura apenas - ver secao
     * "Design system - regua de tokens" no CLAUDE.md).
     */
    @Deprecated
    public String getBootstrapBadge() {
        return switch (this) {
            case SOLICITADO -> "bg-secondary";
            case ENVIADO -> "bg-primary";
            case SOLICITA_INFORMACAO -> "bg-warning text-dark";
            case DEFERIDO -> "bg-success";
            case INDEFERIDO -> "bg-danger";
            case CANCELADO -> "bg-dark";
        };
    }

    /**
     * Tom semantico do design system, independente de framework CSS:
     * {@code "ok"} (deferido), {@code "danger"} (indeferido),
     * {@code "attention"} (aguardando algo de fora do sistema - solicitacao
     * de informacao complementar) ou {@code "neutral"} (em andamento sem
     * alarme, ou encerrado sem decisao de merito - solicitado/enviado/
     * cancelado). Ver fragment {@code layout :: tomBadge} para a traducao
     * deste vocabulario para classes Bootstrap.
     */
    public String getTom() {
        return switch (this) {
            case SOLICITADO -> "neutral";
            // Padronizacao de cores 2026-08-06 ("Aguardando" = azul): o badge
            // Bootstrap (getBootstrapBadge()) ja usa bg-primary/azul para
            // ENVIADO - getTom() estava divergente ("neutral"/cinza) porque
            // nenhum template consumia este metodo ainda (ver
            // RELATORIO-STATUS-PROCESSO-12-2026-2026-08-11.md, Achado 6).
            // Corrigido para nao virar uma recaida silenciosa quando algum
            // template futuro migrar para o fragment layout :: tomBadge.
            case ENVIADO -> "aguardando";
            case SOLICITA_INFORMACAO -> "attention";
            case DEFERIDO -> "ok";
            case INDEFERIDO -> "danger";
            case CANCELADO -> "neutral";
        };
    }
}
