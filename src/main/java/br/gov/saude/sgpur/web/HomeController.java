package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.repository.ProcessoRepository;
import br.gov.saude.sgpur.service.FluxoProcessoService;
import br.gov.saude.sgpur.service.MembroUrgenciaRenalService;
import br.gov.saude.sgpur.service.TempoRespostaService;
import br.gov.saude.sgpur.service.TempoRespostaService.ResumoTempo;
import br.gov.saude.sgpur.web.dto.PainelLinha;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class HomeController {

    private final ProcessoRepository processoRepository;
    private final MembroUrgenciaRenalService membroService;
    private final FluxoProcessoService fluxoService;
    private final TempoRespostaService tempoRespostaService;

    public HomeController(ProcessoRepository processoRepository,
                          MembroUrgenciaRenalService membroService,
                          FluxoProcessoService fluxoService,
                          TempoRespostaService tempoRespostaService) {
        this.processoRepository = processoRepository;
        this.membroService = membroService;
        this.fluxoService = fluxoService;
        this.tempoRespostaService = tempoRespostaService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/")
    @Transactional(readOnly = true)
    public String dashboard(Model model) {
        // Painel focado no ANO CORRENTE: contadores e planilha refletem apenas os
        // processos deste ano. Carrega uma vez (com pareceres/medicos, fetch join)
        // e agrega em Java, seguindo a convencao do projeto.
        int anoCorrente = java.time.Year.now().getValue();
        model.addAttribute("anoCorrente", anoCorrente);

        List<Processo> processos = processoRepository.findByAnoComPareceres(anoCorrente).stream()
            // Pendencias primeiro: processos EM ANDAMENTO vao para o topo; dentro de
            // cada grupo, "mais recente primeiro" (a query vem em sequencial asc).
            .sorted(java.util.Comparator
                .comparing((Processo p) -> p.getStatus().isEmAndamento() ? 0 : 1)
                .thenComparing(Processo::getSequencial,
                    java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
            .toList();

        long deferidos = 0;
        long indeferidos = 0;
        long cancelados = 0;
        long emAndamento = 0;
        Map<Long, String> pendencias = new LinkedHashMap<>();
        for (Processo p : processos) {
            switch (p.getStatus()) {
                case DEFERIDO -> deferidos++;
                case INDEFERIDO -> indeferidos++;
                case CANCELADO -> cancelados++;
                default -> { }
            }
            // "Em andamento" agrupa os status nao finais (SOLICITADO, ENVIADO,
            // SOLICITA_INFORMACAO).
            if (p.getStatus().isEmAndamento()) {
                emAndamento++;
            }
            // O que falta por processo reusa o FluxoProcessoService, e vale
            // TAMBEM para processo ja decidido: status final trava a edicao das
            // etapas 1-4, mas oficio/comprovante SNT e a resposta ao solicitante
            // continuam pendentes ate serem feitos. Antes o calculo era feito so
            // para isEmAndamento(), entao esses ficavam sem nenhum "o que falta"
            // no Painel - justamente os que precisam de acao e ninguem ve.
            // pendenciaAberta devolve vazio quando nao ha nada pendente, entao
            // processo realmente concluido continua com a celula limpa.
            fluxoService.pendenciaAberta(p).ifPresent(msg -> pendencias.put(p.getId(), msg));
        }

        model.addAttribute("totalProcessos", processos.size());
        model.addAttribute("deferidos", deferidos);
        model.addAttribute("indeferidos", indeferidos);
        model.addAttribute("cancelados", cancelados);
        model.addAttribute("emAndamento", emAndamento);
        model.addAttribute("pendencias", pendencias);
        model.addAttribute("membrosAtivos", membroService.contarAtivos());

        // Pendencia ATIVA de comprovante SNT: processo Deferido cujo comprovante
        // de insercao no SNT nunca foi anexado. Enquanto ele nao existe, a
        // resposta oficial ao solicitante fica BLOQUEADA (ProcessoValidator) —
        // ou seja, o paciente foi deferido e a equipe nao foi comunicada. Antes
        // isso nao entrava em contador nenhum (o painel so olhava processos em
        // andamento) e so aparecia como texto na coluna da lista.
        // Conta TODOS os anos de proposito (nao so o corrente): uma pendencia
        // dessas herdada do ano anterior continua sendo uma comunicacao devida.
        model.addAttribute("deferidosSemComprovanteSnt",
            processoRepository.contarDeferidosSemComprovanteSnt());

        // Planilha do painel: os processos do ano com os 3 medicos e o status
        // de cada parecer (Favoravel / Desfavoravel / Aguardando / ...).
        List<PainelLinha> linhas = processos.stream().map(PainelLinha::de).toList();
        model.addAttribute("linhas", linhas);

        // Indicador: tempo de resposta medio total dos avaliadores.
        ResumoTempo tempo = tempoRespostaService.calcular();
        model.addAttribute("mediaGeralTempoTexto",
            TempoRespostaService.formatarDias(tempo.mediaGeralDias()));
        model.addAttribute("tempoDentroPrazo",
            tempo.mediaGeralDias() == null || tempo.mediaGeralDias() <= tempo.prazoDias());
        model.addAttribute("prazoDiasTempo", tempo.prazoDias());
        return "dashboard";
    }
}
