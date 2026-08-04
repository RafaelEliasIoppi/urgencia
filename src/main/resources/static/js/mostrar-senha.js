// === SAUR - Botao de mostrar/ocultar senha ===
// Estava inline em login.html ate 2026-08-04, contrariando a convencao do
// projeto ("JavaScript especifico fica em static/js/*.js, NUNCA inline nos
// templates" - CLAUDE.md). Era a unica violacao dessa regra no sistema.
//
// Generico de proposito: qualquer botao com data-alvo-senha="<id do input>"
// passa a alternar aquele campo, entao a tela de login e o formulario de
// usuario usam o mesmo codigo em vez de duas copias.
(function () {
    document.querySelectorAll('[data-alvo-senha]').forEach(function (botao) {
        var input = document.getElementById(botao.dataset.alvoSenha);
        if (!input) return;

        // aria-pressed: sem isso o leitor de tela anuncia so "botao", sem
        // dizer se a senha esta visivel ou oculta no momento.
        botao.setAttribute('aria-pressed', 'false');

        botao.addEventListener('click', function () {
            var mostrando = input.type === 'text';
            input.type = mostrando ? 'password' : 'text';
            botao.setAttribute('aria-pressed', String(!mostrando));
            var icone = botao.querySelector('i');
            if (icone) icone.className = mostrando ? 'bi bi-eye' : 'bi bi-eye-slash';
            botao.setAttribute('aria-label', mostrando ? 'Mostrar senha' : 'Ocultar senha');
        });
    });
})();
