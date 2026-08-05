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

    // Aviso ao sair com a solicitacao preenchida e nao enviada (ver
    // aviso-sair-sem-salvar.js). Adicional ao botao manual "Salvar
    // rascunho" - o rascunho so guarda os 4 campos de texto abaixo, nunca os
    // arquivos selecionados, entao mesmo quem ja salvou um rascunho ainda
    // pode perder os documentos anexados ao fechar a aba sem enviar de
    // verdade. Por isso "Salvar rascunho" NAO desarma este aviso.
    if (typeof window.iniciarAvisoSairSemSalvar === 'function') {
        window.iniciarAvisoSairSemSalvar({
            campos: [
                document.getElementById('pacienteNome'),
                document.getElementById('pacienteRgct'),
                document.getElementById('dataSituacaoEspecial'),
                document.getElementById('justificativaClinica'),
                document.getElementById('documentos')
            ]
        });
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

    // Salvar rascunho (AJAX, sem recarregar a pagina) - ver
    // SolicitanteController#salvarRascunho. Nenhum campo e validado aqui: o
    // rascunho pode ser salvo parcialmente preenchido.
    var form = document.getElementById('formNovaSolicitacao');
    var btnSalvarRascunho = document.getElementById('btnSalvarRascunho');
    var rascunhoStatus = document.getElementById('rascunhoStatus');
    if (form && btnSalvarRascunho) {
        var rascunhoUrl = form.getAttribute('data-rascunho-url');
        var csrfMeta = document.querySelector('meta[name="_csrf"]');
        var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        var csrfToken = csrfMeta ? csrfMeta.content : null;
        var csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.content : null;

        btnSalvarRascunho.addEventListener('click', function () {
            var campoNome = document.getElementById('pacienteNome');
            var campoRgct = document.getElementById('pacienteRgct');
            var campoData = document.getElementById('dataSituacaoEspecial');
            var campoJustificativa = document.getElementById('justificativaClinica');

            var params = new URLSearchParams();
            params.set('pacienteNome', (campoNome && campoNome.value) || '');
            params.set('pacienteRgct', (campoRgct && campoRgct.value) || '');
            params.set('dataSituacaoEspecial', (campoData && campoData.value) || '');
            params.set('justificativaClinica', (campoJustificativa && campoJustificativa.value) || '');

            var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
            if (csrfHeader && csrfToken) {
                headers[csrfHeader] = csrfToken;
            }

            btnSalvarRascunho.disabled = true;
            fetch(rascunhoUrl, {
                method: 'POST',
                headers: headers,
                credentials: 'same-origin',
                body: params.toString()
            }).then(function (resp) {
                if (!resp.ok) {
                    throw new Error('Falha ao salvar rascunho');
                }
                return resp.json();
            }).then(function () {
                var agora = new Date();
                var hh = String(agora.getHours()).padStart(2, '0');
                var mm = String(agora.getMinutes()).padStart(2, '0');
                if (rascunhoStatus) {
                    rascunhoStatus.textContent = 'Rascunho salvo às ' + hh + ':' + mm;
                }
                if (typeof mostrarToast === 'function') {
                    mostrarToast('Rascunho salvo.', 'success');
                }
            }).catch(function () {
                if (typeof mostrarToast === 'function') {
                    mostrarToast('Não foi possível salvar o rascunho. Tente novamente.', 'danger');
                } else if (rascunhoStatus) {
                    rascunhoStatus.textContent = 'Falha ao salvar rascunho.';
                }
            }).finally(function () {
                btnSalvarRascunho.disabled = false;
            });
        });
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
