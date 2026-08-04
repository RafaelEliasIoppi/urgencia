// === SAUR - Filtro + busca client-side da lista "Minhas solicitacoes" (Portal do Solicitante) ===
// Os cards de resumo eram so decorativos (mostravam um numero, sem nenhuma
// acao) e nao havia como buscar por nome/RGCT numa lista que so cresce. Tudo
// aqui e filtro em memoria sobre as linhas/cards ja renderizados pelo
// servidor - nao faz nenhuma chamada nova ao backend.
document.addEventListener('DOMContentLoaded', function () {
    var cards = document.querySelectorAll('#cardsFiltro .stat-card-filtro');
    var busca = document.getElementById('buscaSolicitacao');
    var linhasTabela = document.querySelectorAll('#tabelaSolicitacoes tbody tr[data-categoria]');
    var cardsLista = document.querySelectorAll('.d-md-none a.card[data-categoria]');
    var vazioAviso = document.getElementById('listaVaziaFiltro');

    if (!cards.length) return;

    var filtroAtivo = 'total';

    function aplicarFiltro() {
        var termo = (busca ? busca.value : '').trim().toLowerCase();
        var visiveis = 0;

        function combina(el) {
            var categoriaOk = filtroAtivo === 'total' || el.dataset.categoria === filtroAtivo;
            var buscaOk = !termo
                || el.dataset.nome.indexOf(termo) !== -1
                || el.dataset.rgct.indexOf(termo) !== -1;
            return categoriaOk && buscaOk;
        }

        linhasTabela.forEach(function (linha) {
            var mostra = combina(linha);
            linha.classList.toggle('d-none', !mostra);
            if (mostra) visiveis++;
        });
        cardsLista.forEach(function (card) {
            var mostra = combina(card);
            card.classList.toggle('d-none', !mostra);
            if (mostra) visiveis++;
        });

        if (vazioAviso) {
            // So mostra o aviso de "nenhum resultado" quando ha solicitacoes
            // de verdade (senao duplicaria a mensagem de estado vazio real,
            // ja renderizada pelo servidor para o caso de lista sem nenhum
            // envio ainda).
            var haSolicitacoes = linhasTabela.length > 0 || cardsLista.length > 0;
            vazioAviso.classList.toggle('d-none', !haSolicitacoes || visiveis > 0);
        }
    }

    cards.forEach(function (card) {
        card.addEventListener('click', function () {
            cards.forEach(function (c) { c.classList.remove('active'); });
            card.classList.add('active');
            filtroAtivo = card.dataset.filtro;
            aplicarFiltro();
        });
    });

    if (busca) {
        busca.addEventListener('input', aplicarFiltro);
    }
});
