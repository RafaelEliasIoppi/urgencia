package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Anexo;
import br.gov.saude.sgpur.domain.MembroUrgenciaRenal;
import br.gov.saude.sgpur.domain.OrigemParecer;
import br.gov.saude.sgpur.domain.Parecer;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.ResultadoParecer;
import br.gov.saude.sgpur.domain.StatusProcesso;
import br.gov.saude.sgpur.domain.TipoAnexo;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Testes do RelatorioService: monta o Relatorio Final (sumario + copia dos
 * anexos PDF + pagina informativa para nao-PDF), exercitando de quebra o
 * PdfRelatorioBuilder e o PdfCabecalhoStamper (pacote-privados, so
 * testaveis a partir daqui). FluxoProcessoService e ProcessoService sao
 * mockados porque so a MONTAGEM do PDF importa aqui (o que entra no
 * relatorio e testado nos servicos deles mesmos).
 *
 * <p>{@code gerarCapaProcesso} (capa isolada, usada pelo antigo endpoint
 * manual de Recebimento) foi removido em 2026-07-27 junto com o endpoint -
 * ver {@code ProcessoDetalheController}.
 */
@ExtendWith(MockitoExtension.class)
class RelatorioServiceTest {

    @Mock
    private FluxoProcessoService fluxoService;
    @Mock
    private ProcessoService processoService;

    @TempDir
    Path tempDir;

    private RelatorioService novoService() {
        AnexoStorageService anexoStorage = new AnexoStorageService(null, tempDir.toString());
        return new RelatorioService(fluxoService, processoService, anexoStorage);
    }

    private Processo processoBase(StatusProcesso status) {
        Processo p = new Processo();
        p.setNumero("01/2026");
        p.setPacienteNome("Joao da Silva");
        p.setPacienteRgct("RGCT-1");
        p.setSolicitanteEquipe("Hospital X");
        p.setSolicitanteEmail("equipe@hospital.com");
        p.setDataSituacaoEspecial(LocalDate.of(2026, 1, 1));
        p.setDataCadastro(LocalDateTime.of(2026, 1, 1, 10, 0));
        p.setStatus(status);
        MembroUrgenciaRenal membro = new MembroUrgenciaRenal("HCPA", "Dr. Teste", null);
        membro.setId(1L);
        Parecer par = new Parecer(membro);
        par.setResultado(ResultadoParecer.FAVORAVEL);
        par.setDataResposta(LocalDate.of(2026, 1, 5));
        p.addParecer(par);
        return p;
    }

    @Test
    void gerarProduzPdfValidoSemAnexos() {
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);

        byte[] pdf = novoService().gerar(p);

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void gerarIncluiIniciaisDoPacienteNoCabecalhoENaoONomeCompleto() throws Exception {
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);

        byte[] pdf = novoService().gerar(p);

        PdfReader reader = new PdfReader(pdf);
        String pagina1 = new PdfTextExtractor(reader).getTextFromPage(1);
        reader.close();

        // O cabecalho estampado (rodape/topo institucional, repetido em toda
        // pagina) usa so as iniciais - a capa em si mostra o nome completo
        // (documento interno de arquivamento, nao enviado ao avaliador).
        assertThat(pagina1).contains("Processo CET-RS 01/2026 - Paciente J.S.");
    }

    @Test
    void gerarAdicionaPaginaDeAvisoQuandoAnexoPdfNaoExisteNoDisco() throws Exception {
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);

        Anexo anexoFantasma = new Anexo();
        anexoFantasma.setProcesso(p);
        anexoFantasma.setTipo(TipoAnexo.DOCUMENTO_PACIENTE);
        anexoFantasma.setNomeArquivo("laudo.pdf");
        anexoFantasma.setContentType("application/pdf");
        anexoFantasma.setCaminhoArmazenado("01-2026 - Joao da Silva/laudo.pdf");
        anexoFantasma.setDataUpload(LocalDateTime.of(2026, 1, 2, 9, 0));
        p.addAnexo(anexoFantasma);

        byte[] pdf = novoService().gerar(p);

        PdfReader reader = new PdfReader(pdf);
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            texto.append(new PdfTextExtractor(reader).getTextFromPage(i));
        }
        reader.close();

        assertThat(texto.toString()).contains("Anexo não encontrado");
    }

    @Test
    void gerarMergeiaConteudoRealDeAnexoPdfExistente() throws Exception {
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);

        Path pastaProcesso = tempDir.resolve("01-2026 - Joao da Silva");
        Files.createDirectories(pastaProcesso);
        Path arquivo = pastaProcesso.resolve("laudo.pdf");
        Files.write(arquivo, pdfMinimoComTexto("MARCA-TEXTO-UNICA"));

        Anexo anexo = new Anexo();
        anexo.setProcesso(p);
        anexo.setTipo(TipoAnexo.DOCUMENTO_PACIENTE);
        anexo.setNomeArquivo("laudo.pdf");
        anexo.setContentType("application/pdf");
        anexo.setCaminhoArmazenado("01-2026 - Joao da Silva/laudo.pdf");
        anexo.setDataUpload(LocalDateTime.of(2026, 1, 2, 9, 0));
        p.addAnexo(anexo);

        byte[] pdf = novoService().gerar(p);

        PdfReader reader = new PdfReader(pdf);
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            texto.append(new PdfTextExtractor(reader).getTextFromPage(i));
        }
        reader.close();

        assertThat(texto.toString()).contains("MARCA-TEXTO-UNICA");
    }

    @Test
    void gerarAdicionaPaginaInformativaParaAnexoNaoPdf() throws Exception {
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);

        Anexo anexoImagem = new Anexo();
        anexoImagem.setProcesso(p);
        anexoImagem.setTipo(TipoAnexo.COMPROVANTE_SNT);
        anexoImagem.setNomeArquivo("comprovante.png");
        anexoImagem.setContentType("image/png");
        anexoImagem.setCaminhoArmazenado("01-2026 - Joao da Silva/comprovante.png");
        anexoImagem.setDataUpload(LocalDateTime.of(2026, 1, 3, 9, 0));
        p.addAnexo(anexoImagem);

        byte[] pdf = novoService().gerar(p);

        PdfReader reader = new PdfReader(pdf);
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            texto.append(new PdfTextExtractor(reader).getTextFromPage(i));
        }
        reader.close();

        assertThat(texto.toString())
            .contains("Anexo (formato não-PDF)")
            .contains("comprovante.png");
    }

    /** Extrai o texto de todas as paginas do PDF, na ordem, concatenado. */
    private static String extrairTexto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        StringBuilder texto = new StringBuilder();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            texto.append(new PdfTextExtractor(reader).getTextFromPage(i));
        }
        reader.close();
        return texto.toString();
    }

    // -----------------------------------------------------------------------
    // Frente 1 - correcoes de conteudo (docs/RELATORIO-REFORMULACAO-RELATORIO-FINAL-PDF-2026-08.md)
    // -----------------------------------------------------------------------

    @Test
    void gerarNaoMostraStatusDeTramitacaoComoSeFosseADecisaoQuandoProcessoAindaNaoFoiDecidido() throws Exception {
        // Processo ENVIADO, ainda sem nenhum parecer respondido: a secao "3.
        // Decisao final" nao pode anunciar "Resultado: ENVIADO" (em destaque,
        // CAIXA ALTA) como se ENVIADO fosse uma decisao - a mesma
        // contradicao que a capa do mesmo documento ja evita corretamente.
        Processo p = processoBase(StatusProcesso.ENVIADO);
        p.getPareceres().get(0).setResultado(null);
        p.getPareceres().get(0).setDataResposta(null);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(0L);

        String texto = extrairTexto(novoService().gerar(p));

        // linhaDestaque() sempre imprime o valor em CAIXA ALTA - "ENVIADO" so
        // pode aparecer assim se o bug antigo (status impresso como decisao,
        // sem checar isFinalizado()) tivesse voltado.
        assertThat(texto).doesNotContain("ENVIADO");
        assertThat(texto).contains("Em andamento");
    }

    @Test
    void gerarExplicaAExcecaoDoCoordenadorEmVezDaFraseGenericaDaRegra() throws Exception {
        // Deferido pelo voto isolado do Coordenador da CET-RS (1 favoravel
        // basta - ProcessoValidator.favoraveisNecessariosParaDeferir). O
        // texto fixo "Favoraveis: 1 (regra: 2 de 3 defere o processo)" ao
        // lado de "DEFERIDO" sugeria que a propria regra citada foi violada.
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        MembroUrgenciaRenal coordenador = p.getPareceres().get(0).getMembro();
        coordenador.setCoordenador(true);
        coordenador.setNome("Dra. Coordenadora");
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);
        when(processoService.deferidoPeloCoordenador(any())).thenReturn(true);

        String texto = extrairTexto(novoService().gerar(p));

        assertThat(texto).contains("Coordenador da CET-RS");
        assertThat(texto).contains("Dra. Coordenadora");
        assertThat(texto).contains("exceção regimental");
        assertThat(texto).doesNotContain("regra: 2 de 3 defere o processo");
    }

    @Test
    void gerarMantemAFraseDaRegraNoDeferimentoPorMaioriaNormalSemAExcecaoDoCoordenador() throws Exception {
        // Caminho comum (2 de 3 favoraveis, sem coordenador envolvido) NAO
        // pode regredir: o texto "regra: 2 de 3" continua correto aqui.
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(2L);
        when(processoService.deferidoPeloCoordenador(any())).thenReturn(false);

        String texto = extrairTexto(novoService().gerar(p));

        assertThat(texto).contains("Favoráveis: 2 (regra: 2 de 3 defere o processo)");
        assertThat(texto).doesNotContain("Coordenador da CET-RS");
    }

    @Test
    void gerarMostraRotuloProprioParaParecerImpedidoEmVezDePendente() throws Exception {
        // Impedido (avaliador e o proprio solicitante do processo) e um
        // estado diferente de "sem voto ainda" - nao pode compartilhar o
        // mesmo rotulo "Pendente".
        Processo p = processoBase(StatusProcesso.ENVIADO);
        p.getPareceres().get(0).setResultado(null);
        p.getPareceres().get(0).setDataResposta(null);
        p.getPareceres().get(0).setImpedido(true);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(0L);

        String texto = extrairTexto(novoService().gerar(p));

        assertThat(texto).contains("Impedido");
        assertThat(texto).contains("conflito de interesse");
        assertThat(texto).doesNotContain("Pendente");
    }

    @Test
    void gerarIncluiATrilhaDeAuditoriaDoVotoDoAvaliador() throws Exception {
        // votadoPor/dataHoraVoto/origem substituiram formalmente o antigo
        // anexo comprobatorio do parecer (2026-07-27) - o documento de
        // arquivo precisa carregar essa prova, nao so a dataResposta.
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        Parecer par = p.getPareceres().get(0);
        par.setDataHoraVoto(LocalDateTime.of(2026, 1, 5, 14, 30));
        par.setVotadoPor("avaliador1");
        par.setOrigem(OrigemParecer.AVALIADOR_SISTEMA);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);

        String texto = extrairTexto(novoService().gerar(p));

        assertThat(texto).contains("avaliador1");
        assertThat(texto).contains("05/01/2026 14:30");
        assertThat(texto).contains(OrigemParecer.AVALIADOR_SISTEMA.getDescricao());
    }

    @Test
    void gerarIncluiDataDeEnvioAoSntQuandoDeferido() throws Exception {
        // dataEnvioSnt existe em Processo desde 2026-08-04 e e o
        // identificador de protocolo do desfecho DEFERIDO - nao aparecia em
        // lugar nenhum do relatorio. Desde a secao "3." ficar condicional ao
        // status (B4+A7 do relatorio V2), esta linha so aparece em DEFERIDO.
        Processo p = processoBase(StatusProcesso.DEFERIDO);
        p.setDataEnvioSnt(LocalDate.of(2026, 1, 10));
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(1L);

        String texto = extrairTexto(novoService().gerar(p));

        assertThat(texto).contains("Data de envio ao SNT");
        assertThat(texto).contains("10/01/2026");
        assertThat(texto).doesNotContain("Número do ofício");
    }

    @Test
    void gerarIncluiNumeroDoOficioQuandoIndeferido() throws Exception {
        // Espelho do teste acima para o lado INDEFERIDO: numeroOficio so faz
        // sentido nesse status (DecisaoFinalService.atribuirNumeroOficioSeNecessario
        // so atribui para INDEFERIDO) e a secao "3." so mostra essa linha
        // quando aplicavel.
        Processo p = processoBase(StatusProcesso.INDEFERIDO);
        p.getPareceres().get(0).setResultado(ResultadoParecer.NAO_FAVORAVEL);
        p.setNumeroOficio("0142/2026");
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(0L);

        String texto = extrairTexto(novoService().gerar(p));

        assertThat(texto).contains("Número do ofício");
        assertThat(texto).contains("0142/2026");
        assertThat(texto).doesNotContain("Data de envio ao SNT");
    }

    @Test
    void gerarSecaoDecisaoViraSituacaoAtualQuandoProcessoNaoFoiDecidido() throws Exception {
        // B4+A7 do relatorio V2: titulo "3. Decisao final" prometia um
        // desfecho que ainda nao existe - "3. Situacao atual" corrige a
        // promessa, e as linhas exclusivas de DEFERIDO/INDEFERIDO
        // desaparecem em vez de aparecerem todas como "-".
        Processo p = processoBase(StatusProcesso.ENVIADO);
        p.getPareceres().get(0).setResultado(null);
        p.getPareceres().get(0).setDataResposta(null);
        when(fluxoService.montarEtapas(any())).thenReturn(List.of());
        when(processoService.contarFavoraveis(any())).thenReturn(0L);

        String texto = extrairTexto(novoService().gerar(p));

        assertThat(texto).contains("3. Situação atual");
        assertThat(texto).doesNotContain("3. Decisão final");
        assertThat(texto).doesNotContain("Data de envio ao SNT");
        assertThat(texto).doesNotContain("Número do ofício");
        // "Data da decisao" continua aparecendo na CAPA (adicionarCapa,
        // rotulo fixo la) - so a secao "3." em si nao repete essa linha
        // quando o processo ainda nao foi decidido.
    }

    /** PDF minimo valido contendo o texto informado, para simular um anexo real no disco. */
    private static byte[] pdfMinimoComTexto(String texto) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        com.lowagie.text.Document doc = new com.lowagie.text.Document();
        com.lowagie.text.pdf.PdfWriter.getInstance(doc, out);
        doc.open();
        doc.add(new com.lowagie.text.Paragraph(texto));
        doc.close();
        return out.toByteArray();
    }
}
