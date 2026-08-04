package br.gov.saude.sgpur.web.dto;

/**
 * View pronta para o "cartao de situacao" unico do Portal do Solicitante
 * ({@code /solicitante/{id}}, Fase 6 do relatorio de UI de 2026-08). Antes
 * disso a tela tinha 8 blocos {@code <alert>} condicionais reescrevendo a
 * mesma decisao de status em formatos/vocabularios diferentes (ex.:
 * "Aprovada"/"Deferido"/"Pedido aprovado!" na mesma pagina). Agora toda a
 * decisao e feita UMA VEZ em {@code SolicitanteController.montarSituacaoPedido}
 * e o template so consome este record - nunca recalcula a regra sozinho.
 *
 * @param rotulo texto curto pro badge do topo (ex.: "Deferido").
 * @param classeCor sufixo Bootstrap (success/danger/warning/info/primary/secondary),
 *                   usado no alerta do cartao e no badge do {@code <h1>}.
 * @param icone bootstrap-icon sem o prefixo {@code bi-}.
 * @param titulo frase curta de resultado, cabecalho do cartao (ex.:
 *               "Deferido - Urgencia renal reconhecida").
 * @param mensagem paragrafo principal explicando o que aconteceu / o que fazer.
 * @param detalhe texto secundario opcional (motivo do indeferimento/devolucao,
 *                 ou a mensagem oficial enviada a equipe) - {@code null} se nao houver.
 * @param precisaAcao quando {@code true}, o cartao inclui o formulario de
 *                     upload de informacao complementar, no topo da pagina.
 * @param mostrarNovaSolicitacao quando {@code true}, o cartao oferece o link
 *                                para enviar uma nova solicitacao (devolvida
 *                                ou processo excluido).
 * @param anexoParaBaixar anexo final (comprovante SNT / oficio de indeferimento)
 *                         pronto para download, ou {@code null} se ainda nao existir.
 * @param numeroProcesso numero {@code NN/AAAA} do processo gerado, ou
 *                         {@code null} se ainda nao convertido.
 */
public record SituacaoPedidoView(
    String rotulo,
    String classeCor,
    String icone,
    String titulo,
    String mensagem,
    String detalhe,
    boolean precisaAcao,
    boolean mostrarNovaSolicitacao,
    AnexoDownload anexoParaBaixar,
    String numeroProcesso
) {

    /** Anexo final pronto pra baixar: id do {@code Anexo} do processo + rotulo do botao. */
    public record AnexoDownload(Long id, String rotulo) {
    }
}
