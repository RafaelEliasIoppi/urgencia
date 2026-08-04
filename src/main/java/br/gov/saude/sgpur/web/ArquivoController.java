package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.StatusProcesso;
import br.gov.saude.sgpur.repository.ProcessoRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Arquivo: listagem, apenas leitura, dos processos ENCERRADOS
 * (Deferido/Indeferido/Cancelado). Um "local" separado para consultar e
 * visualizar processos antigos, sem se misturar ao fluxo ativo em /processos.
 * A reabertura de um encerrado e acao exclusiva do ADMIN (ver
 * ProcessoDetalheController.reabrir / SecurityConfig).
 */
@Controller
@RequestMapping("/arquivo")
public class ArquivoController {

    private static final List<StatusProcesso> ENCERRADOS =
        List.of(StatusProcesso.DEFERIDO, StatusProcesso.INDEFERIDO, StatusProcesso.CANCELADO);

    /** Mesmo tamanho de pagina de /processos, para as duas telas se comportarem igual. */
    static final int TAMANHO_PAGINA = 15;

    private final ProcessoRepository processoRepository;

    public ArquivoController(ProcessoRepository processoRepository) {
        this.processoRepository = processoRepository;
    }

    /**
     * Lista paginada dos encerrados, com busca resolvida no banco.
     *
     * <p><b>Por que paginar (auditoria de UI, 2026-08-04).</b> Ate entao esta
     * tela carregava TODOS os processos encerrados em memoria e filtrava a
     * busca em Java, apoiada na premissa de que o conjunto era pequeno. Ela e
     * verdadeira hoje, mas o Arquivo e a UNICA tela do sistema que so cresce -
     * nada nunca sai dela -, enquanto /processos, naturalmente limitada pelo
     * trabalho ativo, ja era paginada em 15. O esforco de paginacao estava na
     * tela que nao precisava dele.</p>
     */
    @GetMapping
    public String listar(@RequestParam(required = false) String q,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        Page<Processo> pagina = processoRepository.buscarEncerrados(
            q, ENCERRADOS, PageRequest.of(Math.max(page, 0), TAMANHO_PAGINA));

        model.addAttribute("processos", pagina.getContent());
        model.addAttribute("q", q);
        model.addAttribute("paginaAtual", pagina.getNumber());
        model.addAttribute("totalPaginas", pagina.getTotalPages());
        model.addAttribute("totalEncerrados", pagina.getTotalElements());
        return "arquivo/lista";
    }
}
