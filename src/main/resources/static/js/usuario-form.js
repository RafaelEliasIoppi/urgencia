// === SAUR - Campos condicionais por perfil (usuarios/form.html) ===
// Estava inline no template ate 2026-08-05 (relatorio de clareza, item 5.5),
// contrariando a convencao do projeto ("JavaScript especifico fica em
// static/js/*.js, NUNCA inline nos templates" - CLAUDE.md).
//
// Mostra/oculta o campo "Membro vinculado" (obrigatorio so para AVALIADOR) e
// o campo "Equipe solicitante" (obrigatorio so para SOLICITANTE) conforme o
// perfil selecionado, e ajusta o required de cada um junto - sem isso o
// navegador bloquearia o envio pedindo um campo escondido, ou deixaria de
// exigir um campo visivel.
(function () {
    var perfilSelect = document.getElementById('perfilSelect');
    var membroBox = document.getElementById('membroBox');
    var membroSelect = document.getElementById('membroSelect');
    var equipeSolicitanteBox = document.getElementById('equipeSolicitanteBox');
    var equipeSolicitanteInput = document.getElementById('equipeSolicitanteInput');
    if (!perfilSelect) return;

    function atualizarMembro() {
        var ehAvaliador = perfilSelect.value === 'AVALIADOR';
        membroBox.style.display = ehAvaliador ? '' : 'none';
        membroSelect.required = ehAvaliador;

        var ehSolicitante = perfilSelect.value === 'SOLICITANTE';
        equipeSolicitanteBox.style.display = ehSolicitante ? '' : 'none';
        equipeSolicitanteInput.required = ehSolicitante;
    }

    perfilSelect.addEventListener('change', atualizarMembro);
    atualizarMembro();
})();
