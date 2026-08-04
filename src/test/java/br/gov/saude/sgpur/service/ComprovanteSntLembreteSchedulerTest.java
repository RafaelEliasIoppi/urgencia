package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Perfil;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.StatusProcesso;
import br.gov.saude.sgpur.domain.Usuario;
import br.gov.saude.sgpur.repository.ProcessoRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import br.gov.saude.sgpur.service.dto.EmailTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes do varredor que cobra o Comprovante SNT dos processos DEFERIDOS
 * parados nessa etapa. O metodo {@code varrer()} e chamado DIRETAMENTE (nunca
 * se espera o cron disparar): em dev/teste o componente nem e registrado
 * ({@code @ConditionalOnProperty}), mesmo padrao de
 * {@code DecisaoAutomaticaScheduler}.
 *
 * <p>Cobre os tres comportamentos que sustentam a cobranca: processo elegivel
 * dispara o e-mail E grava {@code ultimoLembreteSntEm}; falha de envio NAO
 * grava (senao a cobranca some silenciosamente); e um processo ja lembrado
 * dentro do prazo nem chega a ser candidato (filtrado no proprio SQL).</p>
 */
@ExtendWith(MockitoExtension.class)
class ComprovanteSntLembreteSchedulerTest {

    private static final int PRAZO_DIAS = 7;

    @Mock private ProcessoRepository processoRepository;
    @Mock private ProcessoService processoService;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EmailTemplateService emailTemplateService;
    @Mock private EmailSenderService emailSenderService;
    @Mock private AuditoriaService auditoria;

    private ComprovanteSntLembreteScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ComprovanteSntLembreteScheduler(processoRepository, processoService,
            usuarioRepository, emailTemplateService, emailSenderService, auditoria, PRAZO_DIAS);
    }

    private void comOperadorCadastrado() {
        Usuario operador = new Usuario();
        operador.setUsername("operador");
        operador.setPerfil(Perfil.OPERADOR);
        operador.setEmail("operador@saude.rs.gov.br");
        lenient().when(usuarioRepository.findByPerfilInAndAtivoTrue(
            List.of(Perfil.ADMIN, Perfil.OPERADOR))).thenReturn(List.of(operador));
    }

    private Processo deferidoSemComprovante(Long id, int diasDesdeDecisao) {
        Processo p = new Processo();
        p.setId(id);
        p.setNumero("0" + id + "/2026");
        p.setPacienteNome("Paciente " + id);
        p.setSolicitanteEquipe("HCPA");
        p.setStatus(StatusProcesso.DEFERIDO);
        p.setDataDecisao(LocalDateTime.now().minusDays(diasDesdeDecisao));
        return p;
    }

    private EmailTemplate template() {
        return new EmailTemplate("lembrete-comprovante-snt", "Lembrete", "clipboard2-x",
            "Assunto do lembrete", "Corpo do lembrete");
    }

    @Test
    void processoElegivelDisparaEmailEGravaOTimestamp() {
        comOperadorCadastrado();
        Processo p = deferidoSemComprovante(1L, 10);
        when(processoRepository.findCandidatosLembreteSnt(any(), any())).thenReturn(List.of(p));
        when(emailTemplateService.emailLembreteComprovanteSnt(eq(p), anyLong())).thenReturn(template());
        when(emailSenderService.enviar(any(String[].class), any(), anyString(), anyString()))
            .thenReturn(true);

        int enviados = scheduler.varrer();

        assertThat(enviados).isEqualTo(1);
        verify(emailSenderService).enviar(
            eq(new String[]{"operador@saude.rs.gov.br"}), eq(null),
            eq("Assunto do lembrete"), eq("Corpo do lembrete"));
        verify(processoService).registrarLembreteComprovanteSnt(1L);
        verify(auditoria).registrar(
            eq(ComprovanteSntLembreteScheduler.ACAO_AUDITORIA_ENVIADO), anyString());
    }

    @Test
    void falhaDeEnvioNaoGravaOTimestampParaTentarDeNovoNoProximoCiclo() {
        comOperadorCadastrado();
        Processo p = deferidoSemComprovante(2L, 30);
        when(processoRepository.findCandidatosLembreteSnt(any(), any())).thenReturn(List.of(p));
        when(emailTemplateService.emailLembreteComprovanteSnt(eq(p), anyLong())).thenReturn(template());
        when(emailSenderService.enviar(any(String[].class), any(), anyString(), anyString()))
            .thenReturn(false);

        int enviados = scheduler.varrer();

        assertThat(enviados).isZero();
        verify(processoService, never()).registrarLembreteComprovanteSnt(anyLong());
        verify(auditoria).registrar(
            eq(ComprovanteSntLembreteScheduler.ACAO_AUDITORIA_FALHA), anyString());
    }

    @Test
    void aFalhaDeUmProcessoNaoImpedeOLembreteDosDemais() {
        comOperadorCadastrado();
        Processo quebrado = deferidoSemComprovante(3L, 10);
        Processo ok = deferidoSemComprovante(4L, 10);
        when(processoRepository.findCandidatosLembreteSnt(any(), any()))
            .thenReturn(List.of(quebrado, ok));
        when(emailTemplateService.emailLembreteComprovanteSnt(eq(quebrado), anyLong()))
            .thenThrow(new IllegalStateException("falha inesperada"));
        when(emailTemplateService.emailLembreteComprovanteSnt(eq(ok), anyLong())).thenReturn(template());
        when(emailSenderService.enviar(any(String[].class), any(), anyString(), anyString()))
            .thenReturn(true);

        assertThat(scheduler.varrer()).isEqualTo(1);
        verify(processoService).registrarLembreteComprovanteSnt(4L);
        verify(processoService, never()).registrarLembreteComprovanteSnt(3L);
    }

    /**
     * O filtro "ja lembrado ha menos de prazo-dias" e feito no SQL: o teste
     * confirma que a varredura usa o MESMO limite temporal (agora - prazoDias)
     * nos dois parametros da consulta, e que nada e enviado quando a consulta
     * devolve vazio (que e o caso desses processos).
     */
    @Test
    void processoLembradoHaMenosDeUmPrazoNaoEntraNaListaDeCandidatos() {
        comOperadorCadastrado();
        when(processoRepository.findCandidatosLembreteSnt(any(), any())).thenReturn(List.of());

        LocalDateTime antes = LocalDateTime.now();
        assertThat(scheduler.varrer()).isZero();

        ArgumentCaptor<LocalDateTime> limiteDecisao = ArgumentCaptor.forClass(LocalDateTime.class);
        ArgumentCaptor<LocalDateTime> limiteLembrete = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(processoRepository).findCandidatosLembreteSnt(
            limiteDecisao.capture(), limiteLembrete.capture());
        assertThat(limiteDecisao.getValue()).isEqualTo(limiteLembrete.getValue());
        assertThat(limiteDecisao.getValue()).isAfterOrEqualTo(antes.minusDays(PRAZO_DIAS));
        assertThat(limiteDecisao.getValue()).isBefore(LocalDateTime.now().minusDays(PRAZO_DIAS - 1));
        verifyNoInteractions(emailSenderService, emailTemplateService);
        verify(processoService, never()).registrarLembreteComprovanteSnt(anyLong());
    }

    /**
     * Processo COM comprovante ja anexado nunca e candidato: a exclusao vive
     * na propria consulta ({@code not exists} sobre {@code COMPROVANTE_SNT}),
     * entao o varredor nem toca em e-mail. Aqui isso e verificado pelo
     * contrato: nenhuma outra condicao no codigo pode "recuperar" um processo
     * que a consulta nao devolveu.
     */
    @Test
    void processoComComprovanteAnexadoNuncaGeraLembrete() {
        comOperadorCadastrado();
        when(processoRepository.findCandidatosLembreteSnt(any(), any())).thenReturn(List.of());

        assertThat(scheduler.varrer()).isZero();

        verifyNoInteractions(emailSenderService, emailTemplateService, auditoria);
    }

    @Test
    void semAdminOuOperadorComEmailNaoConsultaCandidatosNemEnvia() {
        when(usuarioRepository.findByPerfilInAndAtivoTrue(
            List.of(Perfil.ADMIN, Perfil.OPERADOR))).thenReturn(List.of());

        assertThat(scheduler.varrer()).isZero();

        verifyNoInteractions(processoRepository, emailSenderService, emailTemplateService);
    }

    @Test
    void falhaAoConsultarCandidatosNaoDerrubaAVarredura() {
        comOperadorCadastrado();
        when(processoRepository.findCandidatosLembreteSnt(any(), any()))
            .thenThrow(new IllegalStateException("banco fora do ar"));

        assertThat(scheduler.varrer()).isZero();

        verifyNoInteractions(emailSenderService);
    }
}
