package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.repository.ProcessoRepository;
import br.gov.saude.sgpur.service.RelatorioAnualService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

/**
 * Relatorios gerenciais. Atualmente expoe o Relatorio Geral por ano (PDF),
 * com um seletor que lista apenas os anos que possuem processos.
 */
@Controller
@RequestMapping("/relatorios")
public class RelatorioController {

    private final ProcessoRepository processoRepository;
    private final RelatorioAnualService relatorioAnualService;

    public RelatorioController(ProcessoRepository processoRepository,
                               RelatorioAnualService relatorioAnualService) {
        this.processoRepository = processoRepository;
        this.relatorioAnualService = relatorioAnualService;
    }

    @GetMapping("/anual")
    public String anual(Model model) {
        List<Integer> anos = processoRepository.findAnosComProcessos();
        model.addAttribute("anos", anos);
        return "relatorios/anual";
    }

    @GetMapping("/anual/{ano}/pdf")
    public ResponseEntity<byte[]> anualPdf(@PathVariable int ano) {
        List<Processo> processos = processoRepository.findByAnoComPareceres(ano);
        byte[] pdf = relatorioAnualService.gerar(ano, processos);
        String nome = "relatorio-" + ano + ".pdf";
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nome + "\"")
            .body(pdf);
    }
}
