package br.gov.saude.sgpur.service.dto;

/**
 * Vocabulario UNICO de estado de uma etapa/passo do fluxo do processo,
 * compartilhado entre a timeline vertical ({@link EtapaFluxo}, card
 * "Progresso") e o wizard horizontal ({@link PassoWizard}) na tela de
 * detalhe do processo (item D3 do relatorio de redesign, 2026-08-05).
 *
 * <p><b>Antes desta normalizacao</b> existiam DOIS enums Java diferentes
 * para representar exatamente o mesmo conceito - etapa ja concluida, etapa
 * atual (proxima acao do operador) ou etapa bloqueada porque uma etapa
 * anterior ainda nao foi concluida: {@code EtapaFluxo.Estado} (valores
 * {@code CONCLUIDA/ATUAL/PENDENTE}) e {@code PassoWizard.Estado} (valores
 * {@code CONCLUIDA/ATUAL/BLOQUEADA}). O terceiro valor tinha nomes
 * diferentes ({@code PENDENTE} vs {@code BLOQUEADA}) para a MESMA
 * semantica (basta comparar {@code FluxoProcessoService.montar} com
 * {@code FluxoProcessoService.adicionarPasso}: a condicao
 * "{@code anterioresConcluidas} == false" e identica nos dois). A
 * sincronia entre timeline e wizard dependia so de disciplina humana ao
 * editar os dois enums em paralelo, sem nenhuma garantia estrutural.</p>
 *
 * <p>Este enum e agora a UNICA fonte de estado tanto para {@link EtapaFluxo}
 * quanto para {@link PassoWizard} - o valor {@code BLOQUEADA} venceu por ja
 * ser o nome mais preciso (uma etapa "pendente" da timeline nao esta so
 * "esperando a vez", ela esta especificamente bloqueada por uma etapa
 * anterior nao concluida).</p>
 *
 * <p>O template ({@code processos/detalhe.html}) continua livre para
 * traduzir este MESMO estado em classes CSS DIFERENTES por widget - o
 * wizard usa {@code .concluida/.atual/.bloqueada}, a timeline usa
 * {@code .concluida/.atual/.pendente}. A unificacao e da FONTE DE DADO
 * (este enum), nao da aparencia visual dos dois widgets, que continuam
 * propositalmente distintos (ver {@link #cssClasse(String)}).</p>
 */
public enum EstadoEtapa {
    CONCLUIDA, ATUAL, BLOQUEADA;

    /**
     * Ponto UNICO de traducao deste enum para a classe CSS de um widget da
     * tela de detalhe do processo. Existia antes como dois blocos de
     * ternario aninhado quase identicos, um dentro do {@code th:each} do
     * wizard e outro dentro do {@code th:each} da timeline
     * ({@code processos/detalhe.html}) - cada um podia divergir do outro ao
     * ser editado isoladamente. Centralizado aqui (metodo Java, testavel),
     * em vez de um {@code th:fragment} Thymeleaf: fragments servem para
     * INSERIR marcacao, nao para calcular um valor simples dentro de um
     * atributo como {@code th:classappend} - forcar isso violaria a
     * restricao de nao reestruturar a marcacao existente da tela mais
     * usada do sistema so para acomodar o mecanismo de traducao.
     *
     * @param contexto {@code "wizard"} ou {@code "timeline"} (qualquer outro
     *                 valor cai no vocabulario da timeline, por seguranca)
     */
    public String cssClasse(String contexto) {
        return switch (this) {
            case CONCLUIDA -> "concluida";
            case ATUAL -> "atual";
            case BLOQUEADA -> "wizard".equals(contexto) ? "bloqueada" : "pendente";
        };
    }
}
