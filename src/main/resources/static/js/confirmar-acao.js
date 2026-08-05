// === SAUR - Confirmacao de acoes destrutivas via modal (nao confirm() nativo) ===
// Qualquer <form> ou <a> com o atributo data-confirm-msg="..." passa por um
// modal Bootstrap generico em vez do confirm() bloqueavel do navegador,
// mantendo o padrao visual consistente com o resto do sistema. Se o modal ou
// o Bootstrap nao estiverem disponiveis por algum motivo, cai para confirm()
// nativo como rede de seguranca - a acao nunca deve seguir sem nenhuma
// confirmacao.
//
// window.confirmarAcao(mensagem) tambem fica exposta globalmente, retornando
// uma Promise<boolean>, para telas que precisam decidir programaticamente
// (ex.: reverter um <select> se o usuario nao confirmar) em vez de um
// form/link simples.
//
// ATENCAO (bug real corrigido em 2026-08-05): num <form data-lock-submit
// data-confirm-msg>, o listener de 'submit' de lock-submit.js roda no
// PARSE do documento (mais cedo), e este script so registra o dele no
// DOMContentLoaded (mais tarde) - ambos direto no MESMO <form>, que e o
// alvo do evento. Na fase "target" do DOM, a ordem de execucao de
// listeners no proprio elemento-alvo e SEMPRE a ordem de registro,
// independente da flag de captura de cada um (capture=true num listener
// registrado no proprio alvo NAO da prioridade sobre outro listener
// bubble no mesmo alvo - so importa entre elementos diferentes na
// cadeia). Resultado: no clique, o handler de lock-submit sempre disparava
// primeiro (desabilitando o botao/trocando pro spinner via setTimeout),
// so DEPOIS o de confirmar-acao rodava e abria o modal - o botao ficava
// "processando" com o modal ainda aberto, e cancelar o modal nunca
// reabilitava nada.
//
// Correcao: o listener de submit para <form data-confirm-msg> e registrado
// aqui por DELEGACAO NO document, em fase de captura real
// (addEventListener(..., true) num ANCESTRAL, nao no proprio form) - isso
// SIM roda antes de qualquer listener no alvo, porque a fase de captura
// (do document ate o pai do alvo) sempre termina antes da fase "target"
// comecar. stopImmediatePropagation() ali barra o evento de chegar ao
// <form> (o lock-submit.js nunca chega a rodar via submit nativo). Depois
// que o modal confirma, chamamos window.travarBotoesSubmit(...) na mao
// (exposta por lock-submit.js) logo antes do el.submit() - que de
// qualquer forma NAO redispara o evento 'submit' (comportamento do DOM),
// entao e o unico jeito de a trava visual valer no caminho confirmado.
(function () {
    var modal = null;
    var mensagemEl = null;
    var btnConfirmar = null;
    var resolverPendente = null;

    function inicializar() {
        var modalEl = document.getElementById('modalConfirmarAcao');
        mensagemEl = document.getElementById('modalConfirmarAcaoMensagem');
        btnConfirmar = document.getElementById('btnConfirmarAcaoFinal');
        modal = (typeof bootstrap !== 'undefined' && modalEl)
            ? new bootstrap.Modal(modalEl) : null;

        if (btnConfirmar && modal) {
            btnConfirmar.addEventListener('click', function () {
                modal.hide();
                if (resolverPendente) {
                    var resolver = resolverPendente;
                    resolverPendente = null;
                    resolver(true);
                }
            });
        }
        if (modalEl) {
            modalEl.addEventListener('hidden.bs.modal', function () {
                // Fechado sem clicar em Confirmar (Cancelar/X/Esc): nega.
                if (resolverPendente) {
                    var resolver = resolverPendente;
                    resolverPendente = null;
                    resolver(false);
                }
            });
        }

        function tratarConfirmacao(el, ev) {
            // So relevante para <a>/click(): re-disparar o evento (linha
            // abaixo, el.click()) passa de novo por este listener, e essa
            // flag evita reabrir o modal na segunda vez. Para <form>,
            // el.submit() NAO redispara 'submit' (comportamento do DOM),
            // entao este guard nunca chega a importar nesse caminho - mas
            // mantido igual nos dois branches por simetria/robustez.
            if (el.dataset.confirmed === 'true') {
                el.dataset.confirmed = '';
                return; // ja confirmado pelo modal, deixa a acao prosseguir
            }

            var mensagem = el.dataset.confirmMsg;

            if (!modal || !mensagemEl || !btnConfirmar) {
                if (!window.confirm(mensagem)) {
                    ev.preventDefault();
                }
                return;
            }

            ev.preventDefault();
            window.confirmarAcao(mensagem).then(function (confirmado) {
                if (!confirmado) return;
                el.dataset.confirmed = 'true';
                // Avisa quem estiver ouvindo (ver aviso-sair-sem-salvar.js)
                // que o usuario ja confirmou explicitamente que quer sair/
                // prosseguir - evita um SEGUNDO aviso (o alerta nativo do
                // navegador) logo em seguida, quando a navegacao/submit
                // real acontecer.
                document.dispatchEvent(new CustomEvent('saur:acao-confirmada'));
                if (el.tagName === 'FORM') {
                    // So agora, com a confirmacao do usuario em maos, disparamos
                    // a trava visual (spinner/desabilitar) - nunca antes, senao
                    // o botao fica "processando" com o modal ainda aberto (o
                    // bug original). el.submit() nao redispara 'submit', entao
                    // e so aqui que o lock-submit.js tem chance de agir.
                    if (typeof window.travarBotoesSubmit === 'function') {
                        window.travarBotoesSubmit(el, el.dataset.lockSubmit);
                    }
                    el.submit();
                } else {
                    el.click();
                }
            });
        }

        // Forms: delegado no document, em fase de CAPTURA de verdade (o
        // document e ancestral do form, nao o proprio alvo) - roda ANTES de
        // qualquer listener registrado direto no <form> (ex.: lock-submit.js),
        // que so escuta na fase "target"/bubble. stopImmediatePropagation()
        // impede o evento nativo de chegar ao form quando ha confirmacao
        // pendente (a real submissao so acontece via el.submit() explicito
        // acima, apos o usuario confirmar).
        document.addEventListener('submit', function (ev) {
            var form = ev.target;
            if (!form || !form.matches || !form.matches('[data-confirm-msg]')) return;
            ev.stopImmediatePropagation();
            tratarConfirmacao(form, ev);
        }, true);

        // Links: sem conflito de ordem com lock-submit.js (que so escuta
        // 'submit'), mantidos com listener direto no elemento como antes.
        document.querySelectorAll('a[data-confirm-msg]').forEach(function (el) {
            el.addEventListener('click', function (ev) {
                tratarConfirmacao(el, ev);
            });
        });
    }

    // API programatica: window.confirmarAcao('mensagem').then(function (ok) {...})
    window.confirmarAcao = function (mensagem) {
        if (!modal || !mensagemEl) {
            return Promise.resolve(window.confirm(mensagem));
        }
        return new Promise(function (resolve) {
            resolverPendente = resolve;
            mensagemEl.textContent = mensagem;
            modal.show();
        });
    };

    document.addEventListener('DOMContentLoaded', inicializar);
})();
