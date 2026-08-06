package br.gov.saude.sgpur.web.dto;

import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.ResultadoParecer;

import java.util.ArrayList;
import java.util.List;

/**
 * Linha da "planilha" do Painel Inicial: um processo com a lista dos medicos
 * avaliadores e o status de cada parecer. Usado apenas para exibicao
 * (read-only), nao representa nenhuma regra de negocio nova.
 *
 * @param processo o processo da linha
 * @param medicos  pareceres simplificados (sempre completados ate 3 colunas)
 */
public record PainelLinha(Processo processo, List<CelulaMedico> medicos) {

    /** Numero de colunas de medico exibidas na planilha. */
    public static final int COLUNAS_MEDICO = 3;

    /**
     * Constroi a linha a partir do processo, ordenando os pareceres e
     * preenchendo com celulas vazias quando ha menos de 3 medicos definidos.
     */
    public static PainelLinha de(Processo p) {
        boolean finalizado = p.getStatus().isFinalizado();
        List<CelulaMedico> celulas = new ArrayList<>();
        for (var par : p.getPareceres()) {
            celulas.add(CelulaMedico.de(par.getMembro().getRotulo(),
                par.getResultado(), par.isImpedido(), finalizado));
        }
        while (celulas.size() < COLUNAS_MEDICO) {
            celulas.add(CelulaMedico.vazia());
        }
        return new PainelLinha(p, celulas);
    }

    /**
     * Celula de um medico na planilha: rotulo do medico + chip de situacao.
     *
     * @param medico   "INSTITUICAO - Nome" (ou null quando vazia)
     * @param texto    rotulo curto da situacao (Favoravel/Desfavoravel/etc.)
     * @param cor      classe de cor logica: success, danger, warning, info, muted
     *                 @deprecated ainda proxima demais de nomenclatura Bootstrap
     *                 (nao e um sufixo puro de classe, mas usa o mesmo
     *                 vocabulario) - use {@link #tom()} ("ok"/"danger"/
     *                 "attention"/"neutral") com o fragment {@code layout ::
     *                 tomBadge}. Mantido porque a planilha do Painel ainda le
     *                 este campo diretamente (infraestrutura apenas nesta
     *                 leva - ver "Design system - regua de tokens" no
     *                 CLAUDE.md).
     * @param icone    bootstrap-icon (sem "bi-")
     * @param definido se ha medico atribuido nessa coluna
     */
    public record CelulaMedico(String medico, String texto, String cor,
                               String icone, boolean definido) {

        /**
         * Acessor explicito de {@code cor}, so para carregar
         * {@code @Deprecated} (ver javadoc do parametro acima).
         */
        @Deprecated
        public String cor() {
            return cor;
        }

        /**
         * Tom semantico do design system, derivado de {@link #cor()}:
         * {@code "ok"} (success), {@code "danger"} (danger),
         * {@code "attention"} (warning/info) ou {@code "neutral"} (muted e
         * demais valores).
         */
        public String tom() {
            return switch (cor) {
                case "success" -> "ok";
                case "danger" -> "danger";
                case "warning", "info" -> "attention";
                default -> "neutral";
            };
        }

        static CelulaMedico vazia() {
            return new CelulaMedico(null, "Nao definido", "muted", "dash-circle", false);
        }

        static CelulaMedico de(String medico, ResultadoParecer resultado, boolean impedido,
                               boolean finalizado) {
            if (impedido) {
                return new CelulaMedico(medico, "Impedido", "muted", "slash-circle", true);
            }
            if (resultado == null) {
                // Processo ja encerrado: o parecer pendente foi dispensado pela
                // maioria (mesma logica do detalhe do processo), nao ficou "Aguardando".
                if (finalizado) {
                    return new CelulaMedico(medico, "Dispensado", "muted", "dash-circle", true);
                }
                return new CelulaMedico(medico, "Aguardando", "warning", "hourglass-split", true);
            }
            return switch (resultado) {
                case FAVORAVEL ->
                    new CelulaMedico(medico, "Favoravel", "success", "check-circle-fill", true);
                case NAO_FAVORAVEL ->
                    new CelulaMedico(medico, "Desfavoravel", "danger", "x-circle-fill", true);
                case SOLICITA_INFORMACAO ->
                    new CelulaMedico(medico, "Solicita info", "info", "question-circle-fill", true);
                case SEM_RESPOSTA ->
                    new CelulaMedico(medico, "Sem resposta", "muted", "slash-circle", true);
            };
        }
    }
}
