package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.service.AuditoriaService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import br.gov.saude.sgpur.domain.LogAuditoria;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {

    private static final int TAMANHO = 30;

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    /**
     * Trilha de auditoria, com filtro por usuario, acao e periodo.
     *
     * <p>Ate 2026-08-04 esta tela nao tinha filtro nenhum, embora seja
     * justamente a que se usa quando ja se sabe o que procurar ("o que o
     * usuario X fez ontem", "quem excluiu este anexo"). A unica navegacao era
     * paginar do mais recente para tras, 30 em 30.</p>
     */
    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page,
                         @RequestParam(required = false) String usuario,
                         @RequestParam(required = false) String acao,
                         @RequestParam(required = false)
                         @org.springframework.format.annotation.DateTimeFormat(iso =
                             org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate de,
                         @RequestParam(required = false)
                         @org.springframework.format.annotation.DateTimeFormat(iso =
                             org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate ate,
                         Model model) {
        Page<LogAuditoria> logs = auditoria.buscar(usuario, acao, de, ate,
            PageRequest.of(Math.max(page, 0), TAMANHO));

        model.addAttribute("usuario", usuario);
        model.addAttribute("acao", acao);
        model.addAttribute("de", de);
        model.addAttribute("ate", ate);
        model.addAttribute("acoesDisponiveis", auditoria.acoesDistintas());
        model.addAttribute("totalRegistros", logs.getTotalElements());
        model.addAttribute("temFiltro",
            (usuario != null && !usuario.isBlank()) || (acao != null && !acao.isBlank())
                || de != null || ate != null);

        // Agrupa os registros da pagina por dia, preservando a ordem (mais
        // recente primeiro) que ja vem do repositorio (dataHora desc).
        Map<LocalDate, List<LogAuditoria>> gruposPorDia = new LinkedHashMap<>();
        for (LogAuditoria log : logs.getContent()) {
            LocalDate dia = log.getDataHora().toLocalDate();
            gruposPorDia.computeIfAbsent(dia, d -> new java.util.ArrayList<>()).add(log);
        }

        model.addAttribute("gruposPorDia", gruposPorDia);
        model.addAttribute("paginaAtual", logs.getNumber());
        model.addAttribute("totalPaginas", logs.getTotalPages());
        return "auditoria/lista";
    }
}
