/**
 * Atalho de teclado "/" para focar o campo de busca da tela atual, quando
 * ela existir (padrao GitHub/Gmail). Escopo minimo de proposito - ver
 * docs/RELATORIO-UI-INTERACAO-AVANCADA-2026-08.md, item 5.1: NAO e uma
 * "command palette" (sem navegacao entre telas, sem busca cross-entidade).
 *
 * Restricoes:
 * - Nunca intercepta a tecla enquanto o foco ja esta em input/textarea/
 *   select ou em elemento contenteditable (o operador digita motivo de
 *   indeferimento, corpo de e-mail etc. - "/" faz parte do texto normal).
 * - So age se existir um campo marcado com [data-busca-atalho] na pagina
 *   (o campo de busca das listas que tem essa funcionalidade). Sem campo,
 *   a tecla nao faz nada - nunca sequestra "/" de telas sem busca.
 * - Nenhum atalho para voto ou acao destrutiva (fora de escopo por design,
 *   ver relatorio).
 */
(function () {
    function estaDigitando(elemento) {
        if (!elemento) return false;
        var tag = elemento.tagName ? elemento.tagName.toUpperCase() : '';
        return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || elemento.isContentEditable;
    }

    document.addEventListener('keydown', function (evento) {
        if (evento.key !== '/') return;
        if (evento.ctrlKey || evento.metaKey || evento.altKey) return;
        if (estaDigitando(document.activeElement)) return;

        var campo = document.querySelector('[data-busca-atalho]');
        if (!campo) return;

        evento.preventDefault();
        campo.focus();
        if (typeof campo.select === 'function') campo.select();
    });
})();
