package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Perfil;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.Usuario;
import br.gov.saude.sgpur.repository.ProcessoRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import br.gov.saude.sgpur.service.dto.EmailTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Varredor periodico que COBRA o comprovante de insercao no SNT dos processos
 * DEFERIDOS parados nessa etapa.
 *
 * <p><b>Por que existe.</b> O comprovante SNT depende inteiramente de alguem
 * lembrar de anexa-lo: ele e gerado fora do sistema e sobe como upload manual.
 * Enquanto nao existe, {@code ProcessoValidator} BLOQUEIA a resposta oficial ao
 * solicitante — o paciente foi deferido e a equipe que abriu o pedido nunca e
 * comunicada. Ate 2026-08-04 nada cobrava isso de ninguem: havia lembrete de
 * parecer pendente para avaliador, mas nenhum equivalente aqui, e o painel so
 * contava pendencias de processos em andamento (um Deferido nao entrava em
 * contador nenhum).</p>
 *
 * <p><b>Transacoes.</b> {@link #varrer()} NAO e {@code @Transactional}, pelo
 * mesmo motivo de {@link DecisaoAutomaticaScheduler}: a falha de um processo
 * (SMTP fora do ar, e-mail invalido) nao pode marcar uma transacao
 * compartilhada como rollback-only e derrubar os demais. Cada gravacao de
 * {@code ultimoLembreteSntEm} acontece na transacao propria de
 * {@code ProcessoService.registrarLembreteComprovanteSnt}, e SO depois de o
 * e-mail ter sido confirmado como enviado — se o SMTP falhar, o timestamp nao
 * avanca e o proximo ciclo tenta de novo (mesmo contrato do lembrete manual ao
 * avaliador).</p>
 *
 * <p>Registrado apenas quando
 * {@code app.snt.lembrete.varredura.habilitado=true} (default: desligado em
 * dev/teste, ligado em producao — ver {@code application-prod.yml}).</p>
 */
@Component
@ConditionalOnProperty(
    prefix = "app.snt.lembrete.varredura", name = "habilitado", havingValue = "true")
public class ComprovanteSntLembreteScheduler {

    private static final Logger log = LoggerFactory.getLogger(ComprovanteSntLembreteScheduler.class);

    static final String ACAO_AUDITORIA_ENVIADO = "LEMBRETE_COMPROVANTE_SNT_ENVIADO";
    static final String ACAO_AUDITORIA_FALHA = "LEMBRETE_COMPROVANTE_SNT_FALHA";

    private final ProcessoRepository processoRepository;
    private final ProcessoService processoService;
    private final UsuarioRepository usuarioRepository;
    private final EmailTemplateService emailTemplateService;
    private final EmailSenderService emailSenderService;
    private final AuditoriaService auditoria;

    /**
     * Dias apos a decisao sem comprovante para disparar o 1o lembrete E
     * intervalo minimo ate o proximo lembrete do mesmo processo (nao reenvia
     * todo dia).
     */
    private final int prazoDias;

    public ComprovanteSntLembreteScheduler(ProcessoRepository processoRepository,
                                           ProcessoService processoService,
                                           UsuarioRepository usuarioRepository,
                                           EmailTemplateService emailTemplateService,
                                           EmailSenderService emailSenderService,
                                           AuditoriaService auditoria,
                                           @Value("${app.snt.lembrete.prazo-dias:7}") int prazoDias) {
        this.processoRepository = processoRepository;
        this.processoService = processoService;
        this.usuarioRepository = usuarioRepository;
        this.emailTemplateService = emailTemplateService;
        this.emailSenderService = emailSenderService;
        this.auditoria = auditoria;
        this.prazoDias = prazoDias;
    }

    /**
     * {@code fixedDelay} (nao {@code fixedRate}): o intervalo conta a partir do
     * FIM da execucao anterior, entao duas varreduras nunca se sobrepoem.
     */
    @Scheduled(
        fixedDelayString = "${app.snt.lembrete.varredura.intervalo-ms}",
        initialDelayString = "${app.snt.lembrete.varredura.atraso-inicial-ms}")
    public void varrerAgendado() {
        varrer();
    }

    /**
     * Envia um lembrete por processo elegivel. Falha de um processo e isolada:
     * loga e segue para o proximo.
     *
     * @return quantos lembretes foram efetivamente enviados nesta passada.
     */
    public int varrer() {
        String[] destinatarios = destinatarios();
        if (destinatarios.length == 0) {
            log.warn("Lembrete de comprovante SNT: nenhum ADMIN/OPERADOR ativo com e-mail "
                + "cadastrado - varredura encerrada sem envio.");
            return 0;
        }

        LocalDateTime agora = LocalDateTime.now();
        LocalDateTime limite = agora.minusDays(prazoDias);
        List<Processo> candidatos;
        try {
            // Mesmo limite para as duas condicoes: decidido ha mais de prazoDias
            // E (nunca lembrado OU ultimo lembrete ha mais de prazoDias).
            candidatos = processoRepository.findCandidatosLembreteSnt(limite, limite);
        } catch (RuntimeException e) {
            log.error("Lembrete de comprovante SNT: falha ao buscar candidatos", e);
            return 0;
        }

        int enviados = 0;
        for (Processo p : candidatos) {
            if (enviarLembrete(p, destinatarios, agora)) {
                enviados++;
            }
        }
        if (enviados > 0) {
            log.info("Lembrete de comprovante SNT: {} lembrete(s) enviado(s) de {} candidato(s).",
                enviados, candidatos.size());
        }
        return enviados;
    }

    /** E-mails dos ADMIN/OPERADOR ativos (mesma fonte usada para avisar de nova solicitacao online). */
    private String[] destinatarios() {
        return usuarioRepository.findByPerfilInAndAtivoTrue(List.of(Perfil.ADMIN, Perfil.OPERADOR))
            .stream()
            .map(Usuario::getEmail)
            .filter(e -> e != null && !e.isBlank())
            .distinct()
            .toArray(String[]::new);
    }

    private boolean enviarLembrete(Processo p, String[] destinatarios, LocalDateTime agora) {
        try {
            long dias = p.getDataDecisao() == null ? 0
                : ChronoUnit.DAYS.between(p.getDataDecisao(), agora);
            EmailTemplate template = emailTemplateService.emailLembreteComprovanteSnt(p, dias);
            boolean ok = emailSenderService.enviar(
                destinatarios, null, template.assunto(), template.corpo());
            if (!ok) {
                auditoria.registrar(ACAO_AUDITORIA_FALHA,
                    "Processo " + p.getNumero() + " - falha ao enviar o lembrete de comprovante SNT.");
                return false;
            }
            // SO grava o timestamp apos o envio confirmado: se o SMTP falhar, o
            // proximo ciclo tenta de novo em vez de silenciar a cobranca.
            processoService.registrarLembreteComprovanteSnt(p.getId());
            auditoria.registrar(ACAO_AUDITORIA_ENVIADO,
                "Processo " + p.getNumero() + " - lembrete de comprovante SNT enviado para "
                + destinatarios.length + " destinatario(s).");
            return true;
        } catch (RuntimeException e) {
            log.warn("Lembrete de comprovante SNT: falha no processo {} (id={}): {}",
                p.getNumero(), p.getId(), e.toString());
            return false;
        }
    }
}
