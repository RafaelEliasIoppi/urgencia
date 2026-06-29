package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.*;
import br.gov.saude.sgpur.repository.AnexoRepository;
import br.gov.saude.sgpur.repository.ParecerRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import br.gov.saude.sgpur.service.AuditoriaService;
import br.gov.saude.sgpur.service.ProcessoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes do AvaliadorController:
 * - Medico so vota no seu proprio processo.
 * - 403 para processo alheio ou parecer ja emitido.
 * - Voto grava origem/dataHoraVoto/votadoPor corretamente.
 */
@WebMvcTest(AvaliadorController.class)
class AvaliadorControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean private UsuarioRepository usuarioRepo;
    @MockBean private ParecerRepository parecerRepo;
    @MockBean private AnexoRepository anexoRepo;
    @MockBean private ProcessoService processoService;
    @MockBean private AuditoriaService auditoria;

    private MembroUrgenciaRenal membro;
    private Usuario usuario;
    private Processo processo;
    private Parecer parecer;

    @BeforeEach
    void setUp() {
        membro = new MembroUrgenciaRenal("HCPA", "Veronica Horbe", "veronica@hcpa.edu.br");
        membro.setId(10L);

        usuario = new Usuario();
        usuario.setUsername("avaliador1");
        usuario.setPerfil(Perfil.AVALIADOR);
        usuario.setMembro(membro);

        processo = new Processo();
        processo.setId(1L);
        processo.setNumero("01/2026");
        processo.setPacienteNome("Maria Rosa Silva");
        processo.setStatus(StatusProcesso.ENVIADO);

        parecer = new Parecer(membro);
        parecer.setId(100L);
        parecer.setProcesso(processo);
        parecer.setDataEnvio(LocalDate.now());
        // resultado null = pendente
    }

    @Test
    @WithMockUser(username = "avaliador1", roles = "AVALIADOR")
    void listaProcessosPendentesSemNomeCompleto() throws Exception {
        when(usuarioRepo.findByUsername("avaliador1")).thenReturn(Optional.of(usuario));
        when(parecerRepo.findByMembroIdAndResultadoIsNullAndDataEnvioIsNotNull(10L))
            .thenReturn(List.of(parecer));
        when(anexoRepo.findByProcessoIdAndTipo(1L, TipoAnexo.SOLICITACAO_AVALIADOR))
            .thenReturn(List.of());

        mvc.perform(get("/avaliador"))
            .andExpect(status().isOk())
            .andExpect(view().name("avaliador/lista"))
            // Iniciais M.R.S. devem aparecer no model
            .andExpect(model().attributeExists("iniciaisPorProcesso"))
            // Nome completo NAO deve aparecer na resposta renderizada
            .andExpect(content().string(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("Maria Rosa Silva"))));
    }

    @Test
    @WithMockUser(username = "avaliador1", roles = "AVALIADOR")
    void votarExibe403ParaProcessoAlheio() throws Exception {
        when(usuarioRepo.findByUsername("avaliador1")).thenReturn(Optional.of(usuario));
        // Processo 99 nao tem parecer deste membro
        when(parecerRepo.findByProcessoIdAndMembroId(99L, 10L)).thenReturn(Optional.empty());

        mvc.perform(get("/avaliador/99"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "avaliador1", roles = "AVALIADOR")
    void votarExibe403QuandoParecerJaEmitido() throws Exception {
        parecer.setResultado(ResultadoParecer.FAVORAVEL); // ja votou
        when(usuarioRepo.findByUsername("avaliador1")).thenReturn(Optional.of(usuario));
        when(parecerRepo.findByProcessoIdAndMembroId(1L, 10L)).thenReturn(Optional.of(parecer));

        mvc.perform(get("/avaliador/1"))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "avaliador1", roles = "AVALIADOR")
    void registrarVotoGravaCamposDeNaoRepudio() throws Exception {
        when(usuarioRepo.findByUsername("avaliador1")).thenReturn(Optional.of(usuario));
        when(parecerRepo.findByProcessoIdAndMembroId(1L, 10L)).thenReturn(Optional.of(parecer));
        when(parecerRepo.save(any(Parecer.class))).thenAnswer(inv -> inv.getArgument(0));
        when(processoService.atualizarStatusPorPareceres(1L)).thenReturn(processo);
        doNothing().when(auditoria).registrar(any(), any(), any());

        mvc.perform(post("/avaliador/1/votar")
                .with(csrf())
                .param("resultado", "FAVORAVEL")
                .param("justificativa", "Clinicamente indicado"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/avaliador"));

        // Verifica que os campos de nao-repudio foram gravados
        verify(parecerRepo).save(argThat(p ->
            p.getResultado() == ResultadoParecer.FAVORAVEL
            && p.getOrigem() == OrigemParecer.AVALIADOR_SISTEMA
            && p.getVotadoPor().equals("avaliador1")
            && p.getDataHoraVoto() != null
            && p.getDataResposta() != null
        ));
        verify(processoService).atualizarStatusPorPareceres(1L);
        verify(auditoria).registrar(eq("PARECER_VOTADO"), any(), any());
    }

    @Test
    @WithMockUser(username = "avaliador1", roles = "AVALIADOR")
    void registrarVotoExibe403QuandoParecerJaEmitido() throws Exception {
        parecer.setResultado(ResultadoParecer.NAO_FAVORAVEL); // ja votou
        when(usuarioRepo.findByUsername("avaliador1")).thenReturn(Optional.of(usuario));
        when(parecerRepo.findByProcessoIdAndMembroId(1L, 10L)).thenReturn(Optional.of(parecer));

        mvc.perform(post("/avaliador/1/votar")
                .with(csrf())
                .param("resultado", "FAVORAVEL"))
            .andExpect(status().isForbidden());

        // Nao deve ter salvo nada
        verify(parecerRepo, never()).save(any());
    }

    @Test
    @WithMockUser(username = "avaliador1", roles = "AVALIADOR")
    void registrarVotoExibe403QuandoProcessoNaoEstaEmEnvio() throws Exception {
        processo.setStatus(StatusProcesso.DEFERIDO); // processo ja decidido
        when(usuarioRepo.findByUsername("avaliador1")).thenReturn(Optional.of(usuario));
        when(parecerRepo.findByProcessoIdAndMembroId(1L, 10L)).thenReturn(Optional.of(parecer));

        mvc.perform(post("/avaliador/1/votar")
                .with(csrf())
                .param("resultado", "FAVORAVEL"))
            .andExpect(status().isForbidden());

        verify(parecerRepo, never()).save(any());
    }
}
