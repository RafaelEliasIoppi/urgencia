// === SAUR - Portal do Avaliador: confirmacao explicita do voto ===
// O voto e definitivo (nao ha edicao posterior). Em vez do confirm() nativo
// do navegador - facil de clicar sem ler -, mostra um modal que repete a
// escolha feita, exige um checkbox explicito de ciencia, e so entao envia
// o formulario de verdade. Se o Bootstrap ou algum elemento do modal nao
// estiver disponivel por qualquer motivo, cai para o confirm() nativo como
// rede de seguranca - o voto (irreversivel) nunca deve poder ser enviado
// sem NENHUMA confirmacao.
(function () {
    var form = document.getElementById('formVotoAvaliador');
    if (!form) return;

    // === Justificativa obrigatoria para NAO_FAVORAVEL/SOLICITA_INFORMACAO ===
    // So UX: alterna o atributo "required" do textarea e o texto de ajuda
    // conforme o resultado marcado. A regra de verdade e imposta no servidor
    // (AvaliadorController.registrarVoto) - este bloco nunca e a unica
    // defesa contra um voto negativo sem justificativa.
    var camposObrigam = ['NAO_FAVORAVEL', 'SOLICITA_INFORMACAO'];
    var campoJustificativa = document.getElementById('campoJustificativa');
    var labelJustificativa = document.getElementById('labelJustificativa');
    var ajudaJustificativa = document.getElementById('ajudaJustificativa');
    var radiosResultado = form.querySelectorAll('input[name="resultado"]');

    function atualizarObrigatoriedadeJustificativa() {
        if (!campoJustificativa || !labelJustificativa || !ajudaJustificativa) return;
        var radioMarcado = form.querySelector('input[name="resultado"]:checked');
        var obrigatoria = !!radioMarcado && camposObrigam.indexOf(radioMarcado.value) !== -1;
        campoJustificativa.required = obrigatoria;
        if (obrigatoria) {
            labelJustificativa.textContent = 'Justificativa / observações *';
            ajudaJustificativa.textContent = 'Obrigatório para voto desfavorável ou pedido de informação: '
                + 'esse texto é o que o operador usa para redigir o ofício de indeferimento ou o '
                + 'pedido de informação complementar ao solicitante.';
        } else {
            labelJustificativa.textContent = 'Justificativa / observações (opcional)';
            ajudaJustificativa.textContent = 'Opcional. Será registrado internamente para subsidiar a decisão do processo.';
        }
    }

    radiosResultado.forEach(function (radio) {
        radio.addEventListener('change', atualizarObrigatoriedadeJustificativa);
    });
    atualizarObrigatoriedadeJustificativa();

    var modalEl = document.getElementById('modalConfirmarVoto');
    var resumoResultado = document.getElementById('resumoResultadoConfirmacao');
    var checkConfirma = document.getElementById('checkConfirmaVoto');
    var btnConfirmar = document.getElementById('btnConfirmarVotoFinal');

    if (typeof bootstrap === 'undefined' || !modalEl || !resumoResultado || !checkConfirma || !btnConfirmar) {
        console.error('SAUR: modal de confirmacao do voto indisponivel (Bootstrap ou elemento do DOM ausente); usando confirm() nativo como rede de seguranca.');
        form.addEventListener('submit', function (ev) {
            if (!confirm('Confirmar o registro do seu voto? Esta acao nao pode ser desfeita.')) {
                ev.preventDefault();
            }
        });
        return;
    }

    var modal = new bootstrap.Modal(modalEl);

    form.addEventListener('submit', function (ev) {
        // So chega aqui se os campos "required" nativos ja passaram - o
        // formulario permanece intacto, so adiamos o envio ate a confirmacao.
        ev.preventDefault();

        var radioMarcado = form.querySelector('input[name="resultado"]:checked');
        if (!radioMarcado) return;
        var label = form.querySelector('label[for="' + radioMarcado.id + '"]');
        // So o titulo (ex.: "Favoravel"), nao a frase de consequencia que
        // acompanha cada opcao (voto-opcao-consequencia) - o resumo do modal
        // deve mostrar so o resultado escolhido.
        var titulo = label ? label.querySelector('.voto-opcao-titulo') : null;

        // Mesmas cores/icones do formulario (bi-check-circle verde = Favoravel,
        // bi-x-circle vermelho = Nao favoravel, bi-question-circle amarelo =
        // Solicita informacao) - ver templates/avaliador/votar.html.
        var iconeClasse = radioMarcado.value === 'FAVORAVEL' ? 'bi-check-circle text-success'
            : radioMarcado.value === 'NAO_FAVORAVEL' ? 'bi-x-circle text-danger'
            : 'bi-question-circle text-warning';
        var icone = document.createElement('i');
        icone.className = 'bi ' + iconeClasse + ' me-2';
        var texto = document.createElement('span');
        // textContent (nao innerHTML): o resumo e so texto, nunca deve
        // interpretar o conteudo do label como HTML.
        texto.textContent = (titulo || label || radioMarcado).textContent.trim();
        resumoResultado.replaceChildren(icone, texto);

        checkConfirma.checked = false;
        btnConfirmar.disabled = true;
        modal.show();
    });

    checkConfirma.addEventListener('change', function () {
        btnConfirmar.disabled = !checkConfirma.checked;
    });

    btnConfirmar.addEventListener('click', function () {
        // Desabilita imediatamente: um duplo clique rapido (ou uma rede
        // lenta) nao deve conseguir disparar duas submissoes do voto antes
        // da pagina navegar para longe.
        btnConfirmar.disabled = true;
        // Spinner no proprio botao, com o modal AINDA aberto: entre o clique
        // e a pagina navegar ha um POST que pode levar segundos (rede de
        // hospital). Escondendo o modal de imediato, a tela voltava ao
        // formulario aparentemente ocioso, sem nenhum retorno visual na acao
        // mais importante do sistema. Nao da pra usar o data-lock-submit
        // generico aqui: form.submit() (abaixo) nao dispara o evento
        // 'submit', que e o gatilho do lock-submit.js.
        btnConfirmar.innerHTML =
            '<span class="spinner-border spinner-border-sm"></span> Registrando voto...';
        // form.submit() nao redispara o evento 'submit' (nem a validacao),
        // entao nao reentra no listener acima - segue direto para o POST.
        form.submit();
    });

    // Se o avaliador fechar o modal (Cancelar/X/Esc) sem confirmar, o voto
    // nao e enviado - ele volta para a tela e pode revisar a escolha.
})();
