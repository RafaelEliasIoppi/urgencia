// === SAUR - Aviso ao sair com dados nao salvos (beforeunload) ===
// Utilitario minimo e reutilizavel: liga o alerta nativo do navegador
// ("as alteracoes que voce fez podem nao ser salvas" - o texto exato e
// definido pelo proprio navegador ha anos, uma mensagem customizada no
// evento e ignorada por todos os navegadores modernos) quando o usuario
// tenta fechar a aba, recarregar ou navegar para fora com algum campo
// "sujo" (editado desde o carregamento da pagina).
//
// NUNCA dispara:
//  - no submit normal de um <form> ancestral de um campo sujo (o proprio
//    envio do formulario nao pode acionar o aviso - seria um bug pior que o
//    problema original);
//  - depois que o usuario ja confirmou uma navegacao por um modal de
//    confirmacao proprio (data-confirm-msg, ver confirmar-acao.js), que
//    dispara o evento 'saur:acao-confirmada' no document logo antes de
//    seguir com a acao - sem isso o usuario veria DOIS avisos em sequencia
//    (o modal customizado e depois o alerta nativo do navegador).
//
// Uso: var aviso = window.iniciarAvisoSairSemSalvar({campos: [elemento, ...]});
// Cada elemento produz "sujo" ao primeiro evento 'input'/'change'. Para
// campos que sao editados programaticamente (ex.: um botao de IA que
// reescreve o texto), dispare voce mesmo um evento 'input' no elemento -
// ninguem aqui precisa saber disso.
//
// aviso.limpar(elemento) marca um campo especifico como nao mais sujo (ex.:
// depois que o conteudo dele foi enviado com sucesso por AJAX, sem que a
// pagina tenha navegado) - util quando ha varios campos independentes na
// mesma tela (ex.: varios textareas de e-mail pronto) e so um deles foi
// efetivamente enviado.
(function () {
    function iniciarAvisoSairSemSalvar(config) {
        var sujos = new Set();
        var desarmadoGlobal = false;

        function marcarSujo(campo) {
            return function () { sujos.add(campo); };
        }

        function registrarCampo(campo) {
            if (!campo) return;
            campo.addEventListener('input', marcarSujo(campo));
            campo.addEventListener('change', marcarSujo(campo));
            var form = campo.closest ? campo.closest('form') : null;
            if (form) {
                // Envio normal do formulario: nao e "sair sem salvar", e o
                // proprio salvamento - desarma so este campo, sem afetar
                // outros campos independentes que continuem sujos.
                form.addEventListener('submit', function () { sujos.delete(campo); });
            }
        }

        (config && config.campos || []).forEach(registrarCampo);

        document.addEventListener('saur:acao-confirmada', function () {
            desarmadoGlobal = true;
        });

        window.addEventListener('beforeunload', function (ev) {
            if (desarmadoGlobal || sujos.size === 0) {
                return undefined;
            }
            ev.preventDefault();
            ev.returnValue = '';
            return '';
        });

        return {
            registrar: registrarCampo,
            limpar: function (campo) { sujos.delete(campo); },
            estaSujo: function () { return sujos.size > 0; }
        };
    }

    window.iniciarAvisoSairSemSalvar = iniciarAvisoSairSemSalvar;
})();
