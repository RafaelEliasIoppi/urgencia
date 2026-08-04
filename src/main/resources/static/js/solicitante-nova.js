// Feedback do formulario de Nova solicitacao (Portal do Solicitante):
// contador de caracteres da justificativa clinica e lista de arquivos
// selecionados com opcao de remover um por um. Nao valida tipo/tamanho real
// dos arquivos (isso e feito no servidor) - so ajuda o solicitante a ver e
// corrigir o que escolheu antes de enviar.
document.addEventListener('DOMContentLoaded', function () {
    // Bloqueia data futura no campo "Data em que a urgencia foi identificada".
    // Calculado em JS (nao via Thymeleaf/SpringEL no atributo "max") porque
    // T(java.time.LocalDate) e instanciacao de objeto sao bloqueados nesse
    // contexto de expressao pelo Thymeleaf (TemplateProcessingException:
    // "Instantiation of new objects and access to static classes or
    // parameters is forbidden in this context").
    var campoData = document.getElementById('dataSituacaoEspecial');
    if (campoData) {
        var hoje = new Date();
        var iso = hoje.getFullYear() + '-'
            + String(hoje.getMonth() + 1).padStart(2, '0') + '-'
            + String(hoje.getDate()).padStart(2, '0');
        campoData.max = iso;
    }

    var LIMITE_ATENCAO = 80; // abaixo disso, sinal visual de "provavelmente incompleto"

    var textarea = document.getElementById('justificativaClinica');
    var contador = document.getElementById('justificativaContador');
    if (textarea && contador) {
        var atualizarContador = function () {
            var tamanho = textarea.value.length;
            contador.textContent = tamanho + ' caractere' + (tamanho === 1 ? '' : 's');
            contador.classList.toggle('text-warning', tamanho > 0 && tamanho < LIMITE_ATENCAO);
            contador.classList.toggle('fw-semibold', tamanho > 0 && tamanho < LIMITE_ATENCAO);
        };
        textarea.addEventListener('input', atualizarContador);
        atualizarContador();
    }

    var input = document.getElementById('documentos');
    var lista = document.getElementById('documentosSelecionados');
    if (!input || !lista) {
        return;
    }

    function formatarTamanho(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    // O input file nao permite remover um arquivo individualmente da selecao
    // nativa - reescrevemos input.files com um DataTransfer contendo so os
    // arquivos que sobraram, e disparamos 'change' para re-renderizar a lista.
    function removerArquivo(indice) {
        var restantes = Array.prototype.slice.call(input.files || []);
        restantes.splice(indice, 1);
        var dt = new DataTransfer();
        restantes.forEach(function (arquivo) { dt.items.add(arquivo); });
        input.files = dt.files;
        renderizar();
    }

    function renderizar() {
        var arquivos = Array.prototype.slice.call(input.files || []);
        lista.replaceChildren();
        arquivos.forEach(function (arquivo, indice) {
            var item = document.createElement('li');
            item.className = 'list-group-item d-flex justify-content-between align-items-center py-1 px-2 small';

            var nomeSpan = document.createElement('span');
            nomeSpan.className = 'text-truncate';
            nomeSpan.style.minWidth = '0';
            nomeSpan.textContent = arquivo.name + ' (' + formatarTamanho(arquivo.size) + ')';

            var btnRemover = document.createElement('button');
            btnRemover.type = 'button';
            btnRemover.className = 'btn btn-sm btn-outline-danger py-0 px-2 ms-2 flex-shrink-0';
            btnRemover.title = 'Remover ' + arquivo.name;
            btnRemover.innerHTML = '<i class="bi bi-x-lg"></i>';
            btnRemover.addEventListener('click', function () { removerArquivo(indice); });

            item.appendChild(nomeSpan);
            item.appendChild(btnRemover);
            lista.appendChild(item);
        });
    }

    input.addEventListener('change', renderizar);
});
