// === SAUR - Confirmacao reforcada do cancelamento de processo em analise (Portal do Solicitante) ===
// Mesmo padrao de avaliador-votar.js: uma acao irreversivel (cancelar um
// processo que ja esta sendo analisado por 3 medicos) ganha um modal dedicado
// com checkbox de ciencia, em vez do modal generico de confirmar-acao.js
// (usado so para o caso mais simples: pedido ainda ENVIADA, nunca virou
// processo). Se o Bootstrap ou algum elemento do modal faltar, cai para
// confirm() nativo como rede de seguranca - o cancelamento nunca deve seguir
// sem nenhuma confirmacao.
(function () {
    var form = document.getElementById('formCancelarProcesso');
    if (!form) return;

    var botaoAbrir = form.querySelector('button[data-bs-toggle="modal"]');
    var modalEl = document.getElementById('modalConfirmarCancelamentoProcesso');
    var checkbox = document.getElementById('checkConfirmaCancelamentoProcesso');
    var botaoConfirmar = document.getElementById('btnConfirmarCancelamentoProcessoFinal');

    if (typeof bootstrap === 'undefined' || !modalEl || !checkbox || !botaoConfirmar || !botaoAbrir) {
        console.error('SAUR: modal de confirmacao do cancelamento indisponivel (Bootstrap ou elemento do DOM ausente); usando confirm() nativo como rede de seguranca.');
        if (botaoAbrir) {
            botaoAbrir.addEventListener('click', function () {
                if (confirm('Cancelar este processo em analise? Esta acao nao pode ser desfeita.')) {
                    form.submit();
                }
            });
        }
        return;
    }

    var modal = new bootstrap.Modal(modalEl);

    checkbox.addEventListener('change', function () {
        botaoConfirmar.disabled = !checkbox.checked;
    });

    botaoConfirmar.addEventListener('click', function () {
        botaoConfirmar.disabled = true;
        modal.hide();
        form.submit();
    });

    modalEl.addEventListener('hidden.bs.modal', function () {
        // Fechado sem confirmar (Cancelar/X/Esc): reseta pra proxima tentativa.
        checkbox.checked = false;
        botaoConfirmar.disabled = true;
    });
})();
