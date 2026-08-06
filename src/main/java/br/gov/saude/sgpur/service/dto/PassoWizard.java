package br.gov.saude.sgpur.service.dto;

/**
 * Representa um dos 4 passos fixos do wizard horizontal na tela de detalhe
 * do processo (Envio, Respostas, Decisão, Finalização - o Recebimento foi
 * fundido em Envio em 2026-08-05, ver CLAUDE.md). Agrupa as
 * {@link EtapaFluxo} correspondentes para que o wizard e a timeline vertical
 * (card "Progresso") sempre concordem sobre o que esta concluido, atual ou
 * bloqueado.
 *
 * @param numero  posicao do passo (1 a 4)
 * @param titulo  rotulo curto exibido no wizard
 * @param paneId  id do elemento (tab-pane) associado no template
 * @param estado  {@link EstadoEtapa#CONCLUIDA}, {@link EstadoEtapa#ATUAL} ou
 *                {@link EstadoEtapa#BLOQUEADA} - vocabulario compartilhado
 *                com {@link EtapaFluxo} desde 2026-08-05 (item D3 do
 *                relatorio de redesign; ver javadoc de {@link EstadoEtapa}).
 * @param tooltip texto exibido no title/tooltip do passo
 */
public record PassoWizard(int numero, String titulo, String paneId, EstadoEtapa estado, String tooltip) {
}
