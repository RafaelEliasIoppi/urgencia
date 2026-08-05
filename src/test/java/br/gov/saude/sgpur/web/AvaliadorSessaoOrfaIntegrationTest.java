package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.MembroUrgenciaRenal;
import br.gov.saude.sgpur.domain.Perfil;
import br.gov.saude.sgpur.domain.Usuario;
import br.gov.saude.sgpur.repository.MembroUrgenciaRenalRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de INTEGRACAO (contexto Spring real + H2, sessao HTTP de verdade via
 * login por formulario — nao {@code @WithMockUser}) do bug real reportado
 * pelo usuario: acessar {@code /avaliador} pelo link do e-mail de convite as
 * vezes devolvia um 401 cru (pagina de erro tecnica do navegador) em vez de
 * cair na tela de login normal.
 *
 * <p><b>Causa raiz:</b> o Spring Security nao rele o {@code UserDetails} a
 * cada requisicao — ele fica fixo na sessao desde o login. Se o {@code
 * username} do avaliador muda (ex.: um ADMIN edita em {@code /usuarios})
 * enquanto ele tem sessao ativa, a sessao continua "autenticada" com o
 * username antigo, mas {@code AvaliadorController.resolverMembro} (via
 * {@code UsuarioRepository.findByUsername}) nao encontra mais ninguem.</p>
 *
 * <p><b>Por que precisa ser um teste de sessao real, nao {@code
 * @WithMockUser}:</b> o bug e sobre o ESTADO da {@code HttpSession} entre
 * duas requisicoes (autenticar, alterar o banco, requisitar de novo com a
 * MESMA sessao) — {@code @WithMockUser} recria o {@code SecurityContext} do
 * zero a cada metodo de teste, nunca reproduz uma sessao que "ficou para
 * tras". Este teste loga de verdade via {@code POST /login}, captura a
 * {@link MockHttpSession} resultante, corrompe o vinculo no banco e reusa
 * exatamente essa sessao na proxima requisicao.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:sgpur-avaliador-sessao-orfa;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.anexos.dir=./target/test-anexos-avaliador-sessao-orfa"
})
class AvaliadorSessaoOrfaIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired
    private UsuarioRepository usuarioRepo;
    @Autowired
    private MembroUrgenciaRenalRepository membroRepo;
    @Autowired
    private PasswordEncoder encoder;

    private static final String USERNAME_ORIGINAL = "avaliador-sessao-orfa-it";
    private static final String SENHA = "SenhaForte123!";

    @BeforeEach
    void preparar() {
        usuarioRepo.findByUsername(USERNAME_ORIGINAL).ifPresent(usuarioRepo::delete);
        usuarioRepo.findByUsername(USERNAME_ORIGINAL + "-renomeado").ifPresent(usuarioRepo::delete);

        MembroUrgenciaRenal membro = membroRepo.saveAndFlush(
            new MembroUrgenciaRenal("HCPA", "Medico Sessao Orfa", "medico.sessao.orfa@example.com"));

        Usuario u = new Usuario();
        u.setUsername(USERNAME_ORIGINAL);
        u.setSenha(encoder.encode(SENHA));
        u.setNome("Medico Sessao Orfa");
        u.setEmail("medico.sessao.orfa@example.com");
        u.setPerfil(Perfil.AVALIADOR);
        u.setMembro(membro);
        usuarioRepo.saveAndFlush(u);
    }

    /**
     * REGRESSAO DO BUG: login real -> username muda no banco por baixo da
     * sessao ativa -> a MESMA sessao acessando /avaliador de novo NAO pode
     * devolver 401/500 cru; tem que cair num redirect gracioso para /login,
     * com a sessao antiga de fato invalidada (nunca mais reaproveitavel).
     */
    @Test
    void sessaoOrfaAposTrocaDeUsernameCaiParaLoginEmVezDe401Cru() throws Exception {
        // ---- 1) Login de verdade (nao @WithMockUser) - estabelece uma HttpSession real. ----
        MvcResult loginResult = mvc.perform(post("/login")
                .with(csrf())
                .param("username", USERNAME_ORIGINAL)
                .param("password", SENHA))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/avaliador")) // perfilSuccessHandler: AVALIADOR -> /avaliador
            .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(session).isNotNull();
        assertThat(session.isInvalid()).isFalse();

        // A sessao recem-criada realmente acessa o portal — controle, prova que o
        // login funcionou de verdade antes de corromper o vinculo.
        mvc.perform(get("/avaliador").session(session))
            .andExpect(status().isOk());

        // ---- 2) Por baixo da sessao ativa, o ADMIN renomeia o username do avaliador
        // (mesmo efeito pratico de excluir a conta) - a sessao continua "autenticada"
        // com o username antigo, que nao existe mais no banco. ----
        Usuario usuario = usuarioRepo.findByUsername(USERNAME_ORIGINAL).orElseThrow();
        usuario.setUsername(USERNAME_ORIGINAL + "-renomeado");
        usuarioRepo.saveAndFlush(usuario);

        // ---- 3) MESMA sessao, nova requisicao a /avaliador: antes do fix isso
        // estourava ResponseStatusException(UNAUTHORIZED) cru (401 tecnico, sem
        // chance de logar de novo). Com o fix, cai num redirect gracioso. ----
        mvc.perform(get("/avaliador").session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?erro=sessao-invalida"));

        // A sessao antiga foi REALMENTE invalidada pelo SecurityContextLogoutHandler
        // (nao so "esquecida"/ignorada) - o mesmo objeto de sessao, reconsultado,
        // confirma isInvalid()==true (equivalente, no servlet real, a qualquer
        // acesso subsequente a essa sessao lancar IllegalStateException).
        assertThat(session.isInvalid()).isTrue();

        // ---- 4) Uma requisicao SEGUINTE nao reaproveita nada da sessao antiga: sem
        // nenhuma sessao valida anexada (equivalente a um container real recebendo um
        // cookie de sessao que ja nao existe mais e comecando anonimo), /avaliador
        // continua exigindo login - nunca um 200 nem qualquer dado do portal. ----
        mvc.perform(get("/avaliador"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }
}
