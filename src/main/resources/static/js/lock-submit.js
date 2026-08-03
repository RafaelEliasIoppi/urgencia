// === SAUR - Trava generica de duplo-envio em forms classicos (POST + redirect) ===
// Operacoes lentas e irreversiveis (registrar envio, decidir, finalizar) sao
// <form method="post"> classicos - sem nenhum feedback visual durante os
// segundos que o servidor leva pra responder (funde PDFs, carimba cabecalho,
// manda e-mail...), o que convida o duplo clique. Bug real de producao
// (2026-08-03): o operador clicou duas vezes em "Registrar envio" e os 3
// avaliadores receberam o convite ao Portal do Avaliador 2x - nao havia
// nenhuma trava nem no front nem no servidor.
//
// Marque o <form> com o atributo data-lock-submit (o texto, se preenchido,
// vira a mensagem ao lado do spinner - ex.: data-lock-submit="Enviando..."; se
// vazio, so desabilita sem trocar o texto). Este script desabilita todo
// <button type="submit"> (ou <button> sem "type", que o HTML trata como
// submit por padrao) de dentro do form assim que o envio dispara.
//
// ATENCAO (motivo do setTimeout abaixo): desabilitar o botao SINCRONAMENTE
// dentro do handler do evento 'submit' e arriscado em alguns navegadores -
// controles disabled ficam de fora do entry list construido para o envio, e
// se o botao clicado carregasse um name/value usado pelo servidor (nao e o
// caso dos 3 forms de hoje, mas pode vir a ser em algum novo), desabilitar
// cedo demais apagaria esse par antes do POST sair. Adiar para o proximo
// "tick" (setTimeout 0) garante que o navegador ja capturou o clique/name-value
// e iniciou a submissao antes de qualquer <button> ser desabilitado - mesma
// tecnica recomendada para este problema classico de formularios HTML.
//
// E o equivalente, para forms classicos (POST + redirect, pagina inteira
// troca), do chamarComEspera() de processo-detalhe.js (usado nos botoes
// AJAX/fetch) - mesma linguagem de "desabilitar + trocar texto por spinner
// com mensagem de espera", so que sem fetch: o proprio POST nativo do
// navegador e quem segue em frente. Nao precisa reabilitar o botao depois:
// a resposta troca a pagina inteira (redirect), entao o HTML novo ja vem
// limpo do servidor.
(function () {
    document.querySelectorAll('form[data-lock-submit]').forEach(function (form) {
        form.addEventListener('submit', function () {
            var mensagem = form.dataset.lockSubmit;
            var botoes = form.querySelectorAll('button[type="submit"], button:not([type])');
            setTimeout(function () {
                botoes.forEach(function (btn) {
                    btn.disabled = true;
                    if (mensagem) {
                        btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> ' + mensagem;
                    }
                });
            }, 0);
        });
    });
})();
