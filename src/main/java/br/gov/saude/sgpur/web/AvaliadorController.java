package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.*;
import br.gov.saude.sgpur.repository.AnexoRepository;
import br.gov.saude.sgpur.repository.ParecerRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import br.gov.saude.sgpur.service.AuditoriaService;
import br.gov.saude.sgpur.service.Iniciais;
import br.gov.saude.sgpur.service.ProcessoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Portal do Avaliador — visao restrita para medicos avaliadores autenticados.
 *
 * REGRA DE IMPARCIALIDADE: o avaliador NUNCA ve o nome completo do paciente,
 * a equipe solicitante, os co-avaliadores nem os votos dos outros medicos.
 * Apenas iniciais do paciente sao exibidas (convencao da equipe de Urgencia
 * Renal, nao LGPD). O PDF que o avaliador baixa (SOLICITACAO_AVALIADOR) ja
 * foi gerado anonimizado pelo sistema no momento do envio.
 */
@Controller
@RequestMapping("/avaliador")
public class AvaliadorController {

    private final UsuarioRepository usuarioRepo;
    private final ParecerRepository parecerRepo;
    private final AnexoRepository anexoRepo;
    private final ProcessoService processoService;
    private final AuditoriaService auditoria;

    public AvaliadorController(UsuarioRepository usuarioRepo,
                               ParecerRepository parecerRepo,
                               AnexoRepository anexoRepo,
                               ProcessoService processoService,
                               AuditoriaService auditoria) {
        this.usuarioRepo = usuarioRepo;
        this.parecerRepo = parecerRepo;
        this.anexoRepo = anexoRepo;
        this.processoService = processoService;
        this.auditoria = auditoria;
    }

    /**
     * Lista os processos pendentes do medico logado.
     * Mostra apenas: numero, iniciais do paciente, data de envio, link ao PDF.
     * NUNCA: nome completo, equipe solicitante, co-avaliadores ou votos alheios.
     */
    @GetMapping
    public String lista(Principal principal, Model model) {
        MembroUrgenciaRenal membro = resolverMembro(principal);

        List<Parecer> pendentes = parecerRepo
            .findByMembroIdAndResultadoIsNullAndDataEnvioIsNotNull(membro.getId());

        // Filtra apenas processos ENVIADO ou EM_ANALISE (status ativo para votacao)
        List<Parecer> parecersFiltrados = pendentes.stream()
            .filter(par -> {
                StatusProcesso s = par.getProcesso().getStatus();
                return s == StatusProcesso.ENVIADO || s == StatusProcesso.EM_ANALISE;
            })
            .toList();

        // Mapas por processoId — passados ao template para evitar logica na view.
        Map<Long, Anexo> pdfPorProcesso = new HashMap<>();
        Map<Long, String> iniciaisPorProcesso = new HashMap<>();
        for (Parecer par : parecersFiltrados) {
            Long pid = par.getProcesso().getId();
            List<Anexo> pdfs = anexoRepo.findByProcessoIdAndTipo(
                pid, TipoAnexo.SOLICITACAO_AVALIADOR);
            if (!pdfs.isEmpty()) {
                pdfPorProcesso.put(pid, pdfs.get(0));
            }
            iniciaisPorProcesso.put(pid, Iniciais.de(par.getProcesso().getPacienteNome()));
        }

        model.addAttribute("pareceres", parecersFiltrados);
        model.addAttribute("pdfPorProcesso", pdfPorProcesso);
        model.addAttribute("iniciaisPorProcesso", iniciaisPorProcesso);
        model.addAttribute("membro", membro);
        return "avaliador/lista";
    }

    /**
     * Exibe o formulario de voto para um processo especifico.
     * 403 se: nao for avaliador do processo, parecer ja emitido, processo nao ativo.
     */
    @GetMapping("/{processoId}")
    public String votar(@PathVariable Long processoId, Principal principal, Model model) {
        MembroUrgenciaRenal membro = resolverMembro(principal);
        Parecer parecer = resolverParecerPendente(processoId, membro);

        Processo processo = parecer.getProcesso();
        List<Anexo> pdfsAvaliador = anexoRepo
            .findByProcessoIdAndTipo(processoId, TipoAnexo.SOLICITACAO_AVALIADOR);

        // Apenas iniciais — NUNCA nome completo
        model.addAttribute("iniciais", Iniciais.de(processo.getPacienteNome()));
        model.addAttribute("numero", processo.getNumero());
        model.addAttribute("parecer", parecer);
        model.addAttribute("processo", processo);
        model.addAttribute("pdfsAvaliador", pdfsAvaliador);
        model.addAttribute("resultados", List.of(
            ResultadoParecer.FAVORAVEL,
            ResultadoParecer.NAO_FAVORAVEL,
            ResultadoParecer.SOLICITA_INFORMACAO
        ));
        return "avaliador/votar";
    }

    /**
     * Registra o voto do avaliador autenticado.
     *
     * Grava: resultado, dataResposta=hoje, dataHoraVoto=agora, votadoPor=username,
     * origem=AVALIADOR_SISTEMA. Nao exige anexo (o registro autenticado + IP e a
     * prova de nao-repudio). Chama atualizarStatusPorPareceres para manter a maquina
     * de estados do processo correta (inclusive SOLICITA_INFORMACAO).
     */
    @PostMapping("/{processoId}/votar")
    public String registrarVoto(@PathVariable Long processoId,
                                @RequestParam ResultadoParecer resultado,
                                @RequestParam(required = false) String justificativa,
                                Principal principal,
                                HttpServletRequest request,
                                RedirectAttributes ra) {
        MembroUrgenciaRenal membro = resolverMembro(principal);
        Parecer parecer = resolverParecerPendente(processoId, membro);

        // Registra o voto com nao-repudio completo
        parecer.setResultado(resultado);
        parecer.setDataResposta(LocalDate.now());
        parecer.setDataHoraVoto(LocalDateTime.now());
        parecer.setVotadoPor(principal.getName());
        parecer.setOrigem(OrigemParecer.AVALIADOR_SISTEMA);
        parecerRepo.save(parecer);

        // Atualiza o status do processo (pode ir para SOLICITA_INFORMACAO)
        processoService.atualizarStatusPorPareceres(processoId);

        String ip = request.getRemoteAddr();
        auditoria.registrar("PARECER_VOTADO",
            "Processo " + parecer.getProcesso().getNumero()
                + " - " + membro.getNome()
                + " - " + resultado.getDescricao(),
            ip);

        ra.addFlashAttribute("msg",
            "Voto registrado: " + resultado.getDescricao() + ". Obrigado pela avaliacao.");
        return "redirect:/avaliador";
    }

    // -------------------------------------------------------------------------
    // Helpers privados
    // -------------------------------------------------------------------------

    /**
     * Resolve o membro vinculado ao usuario logado.
     * Lanca 403 se o usuario nao tiver membro vinculado (configuracao incorreta).
     */
    private MembroUrgenciaRenal resolverMembro(Principal principal) {
        Usuario usuario = usuarioRepo.findByUsername(principal.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (usuario.getMembro() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Usuario avaliador sem membro vinculado. Contate o administrador.");
        }
        return usuario.getMembro();
    }

    /**
     * Resolve o parecer pendente do membro no processo.
     * Lanca 403 se o membro nao for avaliador do processo, se o parecer ja foi
     * emitido, ou se o processo nao esta em status ativo para votacao.
     */
    private Parecer resolverParecerPendente(Long processoId, MembroUrgenciaRenal membro) {
        Parecer parecer = parecerRepo.findByProcessoIdAndMembroId(processoId, membro.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Voce nao e avaliador deste processo."));

        if (parecer.getResultado() != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Voce ja emitiu seu parecer para este processo.");
        }

        StatusProcesso status = parecer.getProcesso().getStatus();
        if (status != StatusProcesso.ENVIADO && status != StatusProcesso.EM_ANALISE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Este processo nao esta disponivel para avaliacao (status: "
                    + status.getDescricao() + ").");
        }

        return parecer;
    }
}
