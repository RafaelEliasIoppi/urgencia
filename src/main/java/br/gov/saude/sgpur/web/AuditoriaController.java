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

@Controller
@RequestMapping("/auditoria")
public class AuditoriaController {

    private static final int TAMANHO = 30;

    private final AuditoriaService auditoria;

    public AuditoriaController(AuditoriaService auditoria) {
        this.auditoria = auditoria;
    }

    @GetMapping
    public String listar(@RequestParam(defaultValue = "0") int page, Model model) {
        Page<LogAuditoria> logs = auditoria.listar(PageRequest.of(Math.max(page, 0), TAMANHO));
        model.addAttribute("logs", logs);
        model.addAttribute("paginaAtual", logs.getNumber());
        model.addAttribute("totalPaginas", logs.getTotalPages());
        return "auditoria/lista";
    }
}
