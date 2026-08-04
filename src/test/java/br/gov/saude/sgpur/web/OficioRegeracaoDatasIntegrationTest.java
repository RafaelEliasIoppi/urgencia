package br.gov.saude.sgpur.web;

import br.gov.saude.sgpur.domain.Anexo;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.StatusProcesso;
import br.gov.saude.sgpur.domain.TipoAnexo;
import br.gov.saude.sgpur.repository.AnexoRepository;
import br.gov.saude.sgpur.repository.ProcessoRepository;
import br.gov.saude.sgpur.service.AnexoStorageService;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de INTEGRACAO (contexto Spring real + H2 real, sem mock de servico)
 * da correcao do item 7 do relatorio de 2026-08: salvar as datas do oficio na
 * aba Finalizacao passa a REGERAR o PDF anexado.
 *
 * <p><b>Por que {@code @SpringBootTest} e nao {@code @WebMvcTest}:</b> a
 * escrita e irreversivel (substitui o anexo que sera enviado a equipe
 * solicitante) e envolve transacao real + gravacao em disco. Com
 * {@code @MockitoBean} do {@code DecisaoFinalService} nao haveria PDF nenhum
 * sendo regravado - exatamente a classe de bug que a convencao do CLAUDE.md
 * manda cobrir com servico real.</p>
 *
 * <p>Confere relendo o anexo DO BANCO e extraindo o texto do PDF em disco: a
 * data nova precisa estar dentro do arquivo, nao so na tela.</p>
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:sgpur-oficio-regeracao-it;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.anexos.dir=./target/test-anexos-oficio-regeracao-it"
})
class OficioRegeracaoDatasIntegrationTest {

    @Autowired
    private ProcessoAnexoController controller;
    @Autowired
    private ProcessoRepository processoRepo;
    @Autowired
    private AnexoRepository anexoRepo;
    @Autowired
    private AnexoStorageService anexoStorage;

    private Long processoId;

    @BeforeEach
    @Transactional
    void preparar() {
        anexoRepo.deleteAll();
        processoRepo.deleteAll();

        Processo p = new Processo();
        p.setNumero("09/2026");
        p.setAno(2026);
        p.setSequencial(9);
        p.setPacienteNome("Paciente Regeracao Oficio");
        p.setPacienteRgct("555555555");
        p.setSolicitanteEquipe("HCPA");
        p.setSolicitanteEmail("equipe@hcpa.example.com");
        p.setDataSituacaoEspecial(LocalDate.of(2026, 5, 1));
        p.setStatus(StatusProcesso.INDEFERIDO);
        p.setMotivoIndeferimento("Ausencia de indicacao clinica.");
        p.setDataEmissaoOficio(LocalDate.of(2026, 6, 1));
        processoRepo.saveAndFlush(p);
        processoId = p.getId();

        // RedirectAttributes/flash e o controller usam o request corrente.
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private String textoDoOficioAnexado() throws Exception {
        List<Anexo> oficios = anexoRepo.findAll().stream()
            .filter(a -> a.getTipo() == TipoAnexo.OFICIO_INDEFERIMENTO)
            .toList();
        assertThat(oficios).hasSize(1);      // sempre exatamente 1 (substitui, nao acumula)
        byte[] bytes = Files.readAllBytes(anexoStorage.resolverArquivo(oficios.get(0)));
        PdfReader reader = new PdfReader(bytes);
        try {
            return new PdfTextExtractor(reader).getTextFromPage(1);
        } finally {
            reader.close();
        }
    }

    @Test
    void salvarAsDatasRegeraOAnexoDoOficioComADataNova() throws Exception {
        var ra = new RedirectAttributesModelMap();

        // 1a gravacao: cria o oficio com a data de 1o de junho.
        controller.finalizacao(processoId, LocalDate.of(2026, 6, 1), null, null, ra);
        String textoAntes = textoDoOficioAnexado();
        Long tamanhoAntes = anexoRepo.findAll().stream()
            .filter(a -> a.getTipo() == TipoAnexo.OFICIO_INDEFERIMENTO)
            .findFirst().orElseThrow().getTamanhoBytes();
        assertThat(textoAntes).contains("1 de junho de 2026");

        // 2a gravacao: o operador corrige a data de emissao.
        controller.finalizacao(processoId, LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21), null, ra);

        Processo relido = processoRepo.findById(processoId).orElseThrow();
        assertThat(relido.getDataEmissaoOficio()).isEqualTo(LocalDate.of(2026, 7, 20));
        assertThat(relido.getDataEnvioOficio()).isEqualTo(LocalDate.of(2026, 7, 21));

        String textoDepois = textoDoOficioAnexado();
        assertThat(textoDepois).contains("20 de julho de 2026");
        assertThat(textoDepois).doesNotContain("1 de junho de 2026");
        assertThat(textoDepois).isNotEqualTo(textoAntes);
        // O anexo e outro arquivo (conteudo/tamanho novo), nao o mesmo bytes a bytes.
        Long tamanhoDepois = anexoRepo.findAll().stream()
            .filter(a -> a.getTipo() == TipoAnexo.OFICIO_INDEFERIMENTO)
            .findFirst().orElseThrow().getTamanhoBytes();
        assertThat(tamanhoDepois).isPositive();
        assertThat(tamanhoAntes).isPositive();
    }

    @Test
    void numeroProprioDoOficioEAtribuidoNaPrimeiraGeracaoEPreservadoNaSegunda() throws Exception {
        var ra = new RedirectAttributesModelMap();

        controller.finalizacao(processoId, LocalDate.of(2026, 6, 1), null, null, ra);
        String numero = processoRepo.findById(processoId).orElseThrow().getNumeroOficio();
        assertThat(numero).isEqualTo("0001/2026");
        assertThat(textoDoOficioAnexado()).contains("0001/2026");

        controller.finalizacao(processoId, LocalDate.of(2026, 6, 15), null, null, ra);

        assertThat(processoRepo.findById(processoId).orElseThrow().getNumeroOficio())
            .isEqualTo("0001/2026");
        assertThat(textoDoOficioAnexado()).contains("0001/2026");
    }

    @Test
    void dataDeEnvioAoSntEGravadaEmProcessoDeferido() {
        Processo p = processoRepo.findById(processoId).orElseThrow();
        p.setStatus(StatusProcesso.DEFERIDO);
        processoRepo.saveAndFlush(p);
        var ra = new RedirectAttributesModelMap();

        controller.finalizacao(processoId, null, null, LocalDate.of(2026, 8, 3), ra);

        assertThat(processoRepo.findById(processoId).orElseThrow().getDataEnvioSnt())
            .isEqualTo(LocalDate.of(2026, 8, 3));
        // Deferido nao gera oficio nenhum.
        assertThat(anexoRepo.findAll().stream()
            .anyMatch(a -> a.getTipo() == TipoAnexo.OFICIO_INDEFERIMENTO)).isFalse();
    }
}
