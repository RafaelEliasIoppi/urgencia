// === SAUR - Processo Detalhe ===
// Funcoes da tela de detalhe do processo (wizard, pareceres, IA, e-mail).

// mostrarToast() NAO e mais definida aqui (era uma segunda implementacao
// divergente da de layout.html - unificadas em /js/toast.js, carregado por
// layout.html/navbar em toda tela, inclusive esta - ver CLAUDE.md). Este
// arquivo continua usando window.mostrarToast livremente, só nao a declara.

(function () {
    // Mostra/oculta o motivo conforme a decisao
    var sel = document.getElementById('decisaoSelect');
    var box = document.getElementById('motivoBox');
    if (sel && box) {
        var toggleMotivo = function () {
            box.style.display = (sel.value === 'INDEFERIDO') ? '' : 'none';
        };
        sel.addEventListener('change', toggleMotivo);
        toggleMotivo();
    }

    // Mensagem de confirmacao do modal generico (confirmar-acao.js) cita a
    // decisao selecionada - lida no momento do submit (data-confirm-msg),
    // entao basta manter o atributo atualizado a cada troca do select.
    var formDecisao = document.getElementById('formDecisao');
    if (sel && formDecisao) {
        var atualizarMensagemConfirmacaoDecisao = function () {
            var rotulo = sel.options[sel.selectedIndex]
                ? sel.options[sel.selectedIndex].text : sel.value;
            formDecisao.setAttribute('data-confirm-msg',
                'Confirma o registro da decisão "' + rotulo + '" para este processo? ' +
                'Esta ação é irreversível.');
        };
        sel.addEventListener('change', atualizarMensagemConfirmacaoDecisao);
        atualizarMensagemConfirmacaoDecisao();
    }

    // Avancar para a proxima aba do wizard (botoes "Avancar para X" de cada etapa concluida)
    document.querySelectorAll('[data-goto-pane]').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var passo = document.querySelector('.wizard-step[href="#' + this.dataset.gotoPane + '"]');
            if (passo) passo.click();
        });
    });

    // Copiar textos de e-mail
    document.querySelectorAll('.btn-copiar').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var alvo = document.getElementById(btn.getAttribute('data-alvo'));
            if (!alvo) return;
            navigator.clipboard.writeText(alvo.value).then(function () {
                var orig = btn.innerHTML;
                btn.innerHTML = '<i class="bi bi-check2"></i> Copiado!';
                setTimeout(function () { btn.innerHTML = orig; }, 1500);
            }).catch(function () {
                // Sem isso, uma falha do clipboard (permissao negada, contexto
                // nao seguro etc.) ficava muda - nenhum outro fetch/acao deste
                // arquivo engole erro sem avisar o usuario (ver chamarComEspera).
                mostrarToast('Nao foi possivel copiar o texto. Copie manualmente.', 'error');
            });
        });
    });

    // ===== Token CSRF =====
    // Pode faltar se este script for reaproveitado numa pagina sem o fragment
    // de head com a meta tag - sem essa guarda, o restante do arquivo (ex.: o
    // scroll-hint do wizard, sem nenhuma relacao com CSRF/IA/e-mail) parava de
    // funcionar por causa de uma excecao aqui.
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    var csrfToken = csrfMeta ? csrfMeta.content : null;
    var csrfHeader = csrfHeaderMeta ? csrfHeaderMeta.content : null;

    // ===== Helper comum: botao com spinner + fetch com CSRF + checagem de status =====
    // Usado por chamarIa/chamarAcao/abrirPreviewEmail, que antes reimplementavam
    // cada um esse mesmo bloco (desabilitar botao, spinner, headers, fetch,
    // parse, catch, finally) com pequenas variacoes.
    function chamarComEspera(btn, mensagemEspera, url, options, aoConcluir, mensagemFalha) {
        var orig = btn.innerHTML;
        btn.disabled = true;
        btn.innerHTML = '<span class="spinner-border spinner-border-sm"></span> ' + mensagemEspera;
        var headers = Object.assign({}, options.headers || {});
        if (csrfHeader && csrfToken) headers[csrfHeader] = csrfToken;
        fetch(url, Object.assign({}, options, {headers: headers}))
            .then(function (resp) {
                if (!resp.ok) {
                    throw new Error('HTTP ' + resp.status);
                }
                return resp.json();
            })
            .then(aoConcluir)
            .catch(function () {
                mostrarToast(mensagemFalha, 'error');
            })
            .finally(function () {
                btn.disabled = false;
                btn.innerHTML = orig;
            });
    }

    // ===== Assistencia por IA (Gemini) =====
    function chamarIa(btn, url, options, aoConcluir) {
        chamarComEspera(btn, 'Consultando IA...', url, options, function (data) {
            if (data.erro) {
                mostrarToast('Nao foi possivel obter a sugestao da IA: ' + data.erro, 'error');
            } else {
                aoConcluir(data.texto);
            }
        }, 'Falha de comunicacao ao consultar a IA. Tente novamente.');
    }

    // 1) Sugerir motivo do indeferimento
    var btnSugerirMotivo = document.getElementById('btnSugerirMotivo');
    if (btnSugerirMotivo) {
        btnSugerirMotivo.addEventListener('click', function () {
            var processoId = this.getAttribute('data-processo-id');
            var campo = document.getElementById('motivoIndeferimentoInput');
            chamarIa(this, '/processos/' + processoId + '/sugestao-motivo', {method: 'POST'}, function (texto) {
                campo.value = texto;
            });
        });
    }

    // 2) Resumir documento clinico anexado
    document.querySelectorAll('.btn-resumir-ia').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var anexoId = this.getAttribute('data-anexo-id');
            var alvo = document.getElementById('resumo-ia-' + anexoId);
            chamarIa(this, '/processos/anexos/' + anexoId + '/resumo-ia', {method: 'GET'}, function (texto) {
                alvo.textContent = texto;
                alvo.classList.remove('d-none');
            });
        });
    });

    // 3) Revisar texto de e-mail
    document.querySelectorAll('.btn-revisar-ia').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var processoId = this.getAttribute('data-processo-id');
            var assunto = document.getElementById(this.getAttribute('data-assunto-id')).value;
            var corpoEl = document.getElementById(this.getAttribute('data-corpo-id'));
            var body = new URLSearchParams({assunto: assunto, corpo: corpoEl.value});
            chamarIa(this, '/processos/' + processoId + '/email/revisar-ia', {
                method: 'POST',
                headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                body: body
            }, function (texto) {
                corpoEl.value = texto;
                // Atribuicao programatica de .value nao dispara 'input' -
                // sem isto o aviso de saida sem salvar (aviso-sair-sem-
                // salvar.js) nao percebia que o texto revisado pela IA
                // ainda nao foi enviado.
                corpoEl.dispatchEvent(new Event('input', {bubbles: true}));
            });
        });
    });

    // ===== Aviso ao sair com um e-mail pronto editado e nao enviado =====
    // Cada textarea "corpo" do accordion de e-mails prontos e independente:
    // so o que foi de fato enviado (ou nunca editado) deixa de contar.
    var avisoEmailPronto = (typeof window.iniciarAvisoSairSemSalvar === 'function')
        ? window.iniciarAvisoSairSemSalvar({
            campos: Array.prototype.slice.call(document.querySelectorAll('#accEmails textarea[id^="corpo"]'))
        })
        : null;

    // ===== Envio real de e-mail (SMTP) - sempre disparo manual =====
    // onSucesso (opcional): chamada so quando data.ok e verdadeiro - usado
    // pelo envio do e-mail pronto (6) para desarmar o aviso de saida sem
    // salvar so daquele textarea especifico, sem mexer nos outros.
    function chamarAcao(btn, url, options, mensagemEspera, onSucesso) {
        chamarComEspera(btn, mensagemEspera, url, options, function (data) {
            // Usa o campo "ok" que o backend ja calcula (AcaoResponse) em vez de
            // adivinhar pela presenca da palavra "erro" no texto - a heuristica
            // antiga deixava o toast VERDE em mensagens de erro de negocio que nao
            // continham essa palavra (ex.: "Processo encerrado: nenhuma alteracao
            // e permitida", "Este avaliador ja registrou o parecer."), fazendo o
            // operador achar que um lembrete/e-mail foi enviado quando na verdade
            // a acao foi bloqueada.
            mostrarToast(data.mensagem, data.ok ? 'success' : 'error');
            if (data.ok && typeof onSucesso === 'function') {
                onSucesso();
            }
        }, 'Falha de comunicacao ao enviar o e-mail. Tente novamente.');
    }

    // ===== Modal de confirmacao de e-mail =====
    var modalEmailEl = document.getElementById('modalConfirmaEmail');
    var modalEmail = modalEmailEl ? new bootstrap.Modal(modalEmailEl) : null;
    var btnConfirmaEnvio = document.getElementById('btnConfirmaEnvioEmail');
    var envioPendente = null;

    function renderMensagensEmail(mensagens) {
        var cont = document.getElementById('modalEmailMensagens');
        cont.innerHTML = '';
        var lote = mensagens.length > 1;
        mensagens.forEach(function (m, i) {
            var card = document.createElement('div');
            card.className = 'border rounded p-3 mb-3 bg-body-tertiary';
            if (lote) {
                var cab = document.createElement('div');
                cab.className = 'small fw-semibold text-muted mb-2';
                cab.textContent = 'Mensagem ' + (i + 1) + ' de ' + mensagens.length;
                card.appendChild(cab);
            }
            var dest = document.createElement('div');
            dest.className = 'mb-2';
            var destLabel = document.createElement('span');
            destLabel.className = 'badge bg-secondary me-1';
            destLabel.textContent = 'Para';
            var destVal = document.createElement('span');
            destVal.className = 'fw-semibold';
            destVal.textContent = m.destinatarios;
            dest.appendChild(destLabel);
            dest.appendChild(destVal);
            card.appendChild(dest);
            var assunto = document.createElement('div');
            assunto.className = 'mb-2';
            var asLabel = document.createElement('span');
            asLabel.className = 'text-muted small me-1';
            asLabel.textContent = 'Assunto:';
            var asVal = document.createElement('span');
            asVal.textContent = m.assunto;
            assunto.appendChild(asLabel);
            assunto.appendChild(asVal);
            card.appendChild(assunto);
            var corpo = document.createElement('pre');
            corpo.className = 'small mb-0 p-2 border rounded bg-white';
            corpo.style.whiteSpace = 'pre-wrap';
            corpo.style.wordBreak = 'break-word';
            corpo.textContent = m.corpo;
            card.appendChild(corpo);
            cont.appendChild(card);
        });
    }

    function abrirPreviewEmail(botao, previewParams, enviarFn) {
        var processoId = botao.getAttribute('data-processo-id');
        var headers = {'Content-Type': 'application/x-www-form-urlencoded'};
        chamarComEspera(botao, 'Preparando...', '/processos/' + processoId + '/email/preview', {
            method: 'POST',
            headers: headers,
            body: new URLSearchParams(previewParams)
        }, function (data) {
            if (!data.ok) {
                mostrarToast(data.erro, 'error');
                return;
            }
            renderMensagensEmail(data.mensagens);
            document.getElementById('modalEmailTitulo').textContent = data.mensagens.length > 1
                ? 'Confirmar envio de ' + data.mensagens.length + ' e-mails'
                : 'Confirmar envio de e-mail';
            envioPendente = function () { enviarFn(botao); };
            modalEmail.show();
        }, 'Falha ao preparar a pre-visualizacao do e-mail. Tente novamente.');
    }

    if (btnConfirmaEnvio) {
        btnConfirmaEnvio.addEventListener('click', function () {
            var fn = envioPendente;
            envioPendente = null;
            modalEmail.hide();
            if (fn) fn();
        });
    }

    // 4) Lembrete individual de avaliacao pendente
    document.querySelectorAll('.btn-lembrete-avaliador').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var parecerId = this.getAttribute('data-parecer-id');
            abrirPreviewEmail(this, {tipo: 'lembrete-avaliador', parecerId: parecerId}, function (b) {
                var processoId = b.getAttribute('data-processo-id');
                chamarAcao(b,
                    '/processos/' + processoId + '/lembrete-avaliador?parecerId=' + parecerId,
                    {method: 'POST'}, 'Enviando...');
            });
        });
    });

    // 5) Lembrete em lote para todos os pendentes
    var btnLembretePendentes = document.getElementById('btnLembretePendentes');
    if (btnLembretePendentes) {
        btnLembretePendentes.addEventListener('click', function () {
            abrirPreviewEmail(this, {tipo: 'lembrete-pendentes'}, function (b) {
                var processoId = b.getAttribute('data-processo-id');
                chamarAcao(b, '/processos/' + processoId + '/lembrete-pendentes', {method: 'POST'}, 'Enviando...');
            });
        });
    }

    // 6) Enviar e-mail pronto (accordion)
    document.querySelectorAll('.btn-enviar-email').forEach(function (btn) {
        btn.addEventListener('click', function () {
            var chave = this.getAttribute('data-chave');
            var corpoEl = document.getElementById(this.getAttribute('data-corpo-id'));
            var assunto = document.getElementById(this.getAttribute('data-assunto-id')).value;
            var corpo = corpoEl.value;
            abrirPreviewEmail(this, {tipo: 'pronto', chave: chave, assunto: assunto, corpo: corpo}, function (b) {
                var processoId = b.getAttribute('data-processo-id');
                var body = new URLSearchParams({chave: chave, assunto: assunto, corpo: corpo});
                chamarAcao(b, '/processos/' + processoId + '/email/enviar', {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: body
                }, 'Enviando...', function () {
                    if (avisoEmailPronto) avisoEmailPronto.limpar(corpoEl);
                });
            });
        });
    });

    // ===== Passo bloqueado do wizard: bloquear de verdade =====
    // Ate 2026-08-04 um passo ainda nao liberado tinha apenas
    // "cursor: not-allowed" no CSS - parecia desabilitado e abria normalmente
    // ao clique. Nao era falha de seguranca (os formularios de dentro sao
    // escondidos pelo servidor), mas fazia a tela mentir sobre o proprio
    // estado. Decisao de produto (usuario, 2026-08-04): bloquear de verdade.
    //
    // Precisa ser capture=true: o handler do Bootstrap fica no proprio
    // elemento; sem capturar na descida, ele abriria a aba antes deste
    // preventDefault rodar. O aria-disabled/tabindex="-1" que tiram o passo
    // da ordem de Tab sao aplicados no template (detalhe.html).
    document.querySelectorAll('.wizard-step.bloqueada').forEach(function (passo) {
        passo.addEventListener('click', function (ev) {
            ev.preventDefault();
            ev.stopPropagation();
            var motivo = passo.getAttribute('title');
            if (motivo && typeof window.mostrarToast === 'function') {
                window.mostrarToast(motivo, 'info');
            }
        }, true);
    });

    // Scroll hint: mostra sombra no final do wizard-wrapper se houver scroll
    var wizardWrapper = document.getElementById('wizardWrapper');
    if (wizardWrapper) {
        var wizard = wizardWrapper.querySelector('.wizard');
        var checkScroll = function () {
            if (wizard && wizard.scrollWidth > wizard.clientWidth) {
                wizardWrapper.classList.add('can-scroll');
            } else {
                wizardWrapper.classList.remove('can-scroll');
            }
        };
        checkScroll();
        window.addEventListener('resize', checkScroll);
        if (wizard) {
            wizard.addEventListener('scroll', function () {
                var atEnd = wizard.scrollLeft + wizard.clientWidth >= wizard.scrollWidth - 2;
                if (atEnd) wizardWrapper.classList.remove('can-scroll');
            });
        }
    }
})();
