package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.MembroUrgenciaRenal;
import br.gov.saude.sgpur.repository.MembroUrgenciaRenalRepository;
import br.gov.saude.sgpur.repository.ParecerRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import br.gov.saude.sgpur.service.SolicitacaoOnlineService;
import br.gov.saude.sgpur.service.TempoRespostaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Garante que os formularios de cadastro RENDERIZAM de fato.
 *
 * <p><b>Motivacao (Fase B da auditoria de UI, 2026-08-04).</b> Os campos com
 * validacao ganharam expressoes novas
 * ({@code th:classappend="${#fields.hasErrors('x')} ? 'is-invalid'"} e o
 * {@code th:attr} com {@code aria-invalid}/{@code aria-describedby}). Esse tipo
 * de expressao so e avaliada no momento do render: um nome de campo errado, ou
 * um form sem {@code th:object}, estoura
 * {@code TemplateProcessingException} em runtime e nao aparece em teste nenhum
 * que so verifique status/model attributes.
 *
 * <p>{@code membros/form} e {@code processos/editar} nao tinham NENHUM teste que
 * chegasse a renderiza-los ({@code MembroControllerTest} e um teste unitario
 * puro, sem MockMvc), entao as expressoes novas ficariam sem cobertura
 * justamente nas duas telas mais faceis de quebrar sem ninguem notar.
 *
 * <p>Mesma familia do achado documentado no CLAUDE.md sobre
 * {@code T(java.time.LocalDate).now()} em atributo Thymeleaf: quebrava a tela
 * inteira e so foi pego porque a suite renderizava a pagina.
 */
@WebMvcTest(MembroController.class)
class FormulariosRenderizamTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private MembroUrgenciaRenalRepository membroRepo;
    @MockitoBean private TempoRespostaService tempoRespostaService;
    // GlobalModelAdvice (@ControllerAdvice global) precisa destes para o
    // contexto do @WebMvcTest subir - ver ArquivoControllerTest.
    @MockitoBean private UsuarioRepository usuarioRepository;
    @MockitoBean private ParecerRepository parecerRepository;
    @MockitoBean private SolicitacaoOnlineService solicitacaoOnlineService;

    @Test
    @WithMockUser(roles = "OPERADOR")
    void formularioDeNovoMembroRenderizaComOsAtributosDeValidacao() throws Exception {
        mvc.perform(get("/membros/novo"))
            .andExpect(status().isOk())
            // rotulo associado ao campo (Fase B): sem isso, clicar no rotulo
            // nao foca o campo e o leitor de tela anuncia campo sem nome.
            .andExpect(content().string(org.hamcrest.Matchers.containsString("for=\"nome\"")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("for=\"instituicao\"")));
    }

    @Test
    @WithMockUser(roles = "OPERADOR")
    void formularioDeEdicaoDeMembroRenderiza() throws Exception {
        MembroUrgenciaRenal m = new MembroUrgenciaRenal();
        m.setId(7L);
        m.setNome("Dra. Fulana");
        m.setInstituicao("HCPA");
        m.setEmail("fulana@example.org");
        when(membroRepo.findById(anyLong())).thenReturn(Optional.of(m));

        mvc.perform(get("/membros/7/editar"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dra. Fulana")));
    }
}
