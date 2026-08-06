package br.gov.saude.sgpur.service.dto;

/**
 * Representa uma etapa do fluxo do processo para exibicao em tempo real.
 *
 * @param chave    identidade estavel da etapa, para casamento por codigo (nunca
 *                 por texto) - ver javadoc de {@link Chave}
 * @param titulo   nome da etapa (texto de EXIBICAO apenas, pode ser acentuado
 *                 livremente desde 2026-08-05; ver relatorio de clareza,
 *                 item 4.13b)
 * @param icone    classe do bootstrap-icon (sem o "bi-")
 * @param estado   {@link EstadoEtapa#CONCLUIDA}, {@link EstadoEtapa#ATUAL} ou
 *                 {@link EstadoEtapa#BLOQUEADA} - vocabulario compartilhado
 *                 com {@link PassoWizard} desde 2026-08-05 (item D3 do
 *                 relatorio de redesign; ver javadoc de {@link EstadoEtapa}).
 *                 Chamado de {@code PENDENTE} nesta classe ate entao.
 * @param detalhe  mensagem do que falta ou do que ja foi feito
 */
public record EtapaFluxo(Chave chave, String titulo, String icone, EstadoEtapa estado, String detalhe) {

    /**
     * Identidade estavel de cada etapa do fluxo, para casar por CODIGO em vez
     * de comparar a string de {@code titulo}.
     *
     * <p>Ate 2026-08-05, {@code FluxoProcessoService.montarPassosWizard} e
     * ~15 asserções de {@code FluxoProcessoServiceTest} comparavam
     * {@code titulo.equals("Decisao final")} etc. - o titulo funcionava como
     * chave de identidade por acidente. Isso travava a acentuacao do wizard:
     * acentuar "Decisao final" -> "Decisão final" quebraria esse casamento
     * por string SILENCIOSAMENTE (todo passo apareceria "nao concluido"), sem
     * nenhum erro de compilacao para avisar. Este enum separa as duas coisas:
     * {@code chave} e a identidade (nunca muda, nunca acentuada), {@code
     * titulo} e so o texto exibido (livre para acentuar, sem risco).</p>
     */
    public enum Chave {
        ENVIO, RESPOSTAS, INFO_COMPLEMENTAR, DECISAO,
        OFICIO, COMPROVANTE_SNT, RESPOSTA_SOLICITANTE
    }

    public boolean isConcluida() {
        return estado == EstadoEtapa.CONCLUIDA;
    }

    public boolean isAtual() {
        return estado == EstadoEtapa.ATUAL;
    }
}
