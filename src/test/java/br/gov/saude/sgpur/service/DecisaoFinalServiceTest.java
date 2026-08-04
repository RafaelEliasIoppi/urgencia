package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Anexo;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.StatusProcesso;
import br.gov.saude.sgpur.domain.TipoAnexo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Testes de DecisaoFinalService.gerarDocumentos - a logica pos-decisao que
 * gera Oficio de Indeferimento (so no INDEFERIDO) e Relatorio Final (em
 * qualquer status finalizado: DEFERIDO/INDEFERIDO/CANCELADO). Cobre tambem a
 * origem real do IllegalStateException que ProcessoDecisaoControllerTest so
 * mockava: uma falha de IO ao persistir o PDF gerado
 * (AnexoStorageService.salvarBytes declara "throws IOException"), e a ordem
 * salvar-antes-de-remover (gera+salva o novo documento antes de remover o
 * antigo do mesmo tipo, para nunca ficar sem nenhum se a geracao falhar).
 */
@ExtendWith(MockitoExtension.class)
class DecisaoFinalServiceTest {

    @Mock
    private ProcessoService processoService;
    @Mock
    private RelatorioService relatorioService;
    @Mock
    private AnexoStorageService anexoStorage;
    @Mock
    private br.gov.saude.sgpur.repository.ProcessoRepository processoRepository;

    private DecisaoFinalService service;

    @BeforeEach
    void setUp() {
        service = new DecisaoFinalService(processoService, relatorioService,
            anexoStorage, processoRepository);
    }

    private Processo processo(StatusProcesso status) {
        Processo p = new Processo();
        p.setId(1L);
        p.setNumero("01/2026");
        p.setStatus(status);
        // Numero de oficio ja atribuido por padrao: a atribuicao automatica
        // (que grava o processo) tem testes proprios mais abaixo e nao deve
        // interferir nas assercoes de "nao salva o processo" destes.
        p.setNumeroOficio("0001/2026");
        return p;
    }

    private Anexo anexoSalvo(Long id) {
        Anexo a = new Anexo();
        a.setId(id);
        return a;
    }

    // ---------- INDEFERIDO ----------

    /**
     * Desde 2026-08-04 o sistema NAO gera nem anexa o oficio de indeferimento
     * (decisao do usuario: "oficio sera sempre anexado" - ele precisa ser
     * editavel caso a caso). Na decisao so sai o Relatorio Final; do oficio
     * fica apenas a NUMERACAO reservada, que aparece na tela e no rascunho
     * editavel que o operador baixa.
     */
    @Test
    void indeferidoNaoAnexaOficioMasGeraRelatorioFinal() throws IOException {
        Processo p = processo(StatusProcesso.INDEFERIDO);
        byte[] relatorioBytes = "relatorio".getBytes();
        when(relatorioService.gerar(p)).thenReturn(relatorioBytes);
        when(anexoStorage.salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
            anyString(), anyString(), anyString(), any())).thenReturn(anexoSalvo(20L));

        service.gerarDocumentos(p);

        verify(anexoStorage, never()).salvarBytes(any(), eq(TipoAnexo.OFICIO_INDEFERIMENTO),
            anyString(), anyString(), anyString(), any());
        verify(anexoStorage).salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
            anyString(), eq("relatorio-processo-01-2026.pdf"), eq("application/pdf"), eq(relatorioBytes));
        verify(anexoStorage).removerAntigosDoTipo(p, TipoAnexo.RELATORIO_FINAL, 20L);
    }

    /**
     * A data de emissao do oficio e o momento em que o OFICIO e anexado (regra
     * de datas de 2026-08-04), nao o da decisao - gravar aqui dataria um
     * documento que ainda nao existe.
     */
    @Test
    void indeferidoNaoGravaDataDeEmissaoDoOficioNaDecisao() throws IOException {
        Processo p = processo(StatusProcesso.INDEFERIDO);
        p.setDataEmissaoOficio(null);
        when(relatorioService.gerar(p)).thenReturn("relatorio".getBytes());
        when(anexoStorage.salvarBytes(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(anexoSalvo(1L));

        service.gerarDocumentos(p);

        assertThat(p.getDataEmissaoOficio()).isNull();
    }

    @Test
    void numeroComBarraViraTracoNoNomeDoArquivo() throws IOException {
        Processo p = processo(StatusProcesso.INDEFERIDO);
        p.setNumero("123/2026");
        when(relatorioService.gerar(p)).thenReturn(new byte[0]);
        when(anexoStorage.salvarBytes(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(anexoSalvo(1L));

        service.gerarDocumentos(p);

        ArgumentCaptor<String> nomes = ArgumentCaptor.forClass(String.class);
        verify(anexoStorage).salvarBytes(eq(p), any(), anyString(), nomes.capture(), anyString(), any());
        assertThat(nomes.getValue()).isEqualTo("relatorio-processo-123-2026.pdf");
    }

    // ---------- DEFERIDO / CANCELADO: so relatorio final ----------

    @Test
    void deferidoNaoGeraOficioMasGeraRelatorioFinal() throws IOException {
        Processo p = processo(StatusProcesso.DEFERIDO);
        when(relatorioService.gerar(p)).thenReturn("relatorio".getBytes());
        when(anexoStorage.salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
            anyString(), anyString(), anyString(), any())).thenReturn(anexoSalvo(20L));

        service.gerarDocumentos(p);

        verify(anexoStorage, never()).salvarBytes(any(), eq(TipoAnexo.OFICIO_INDEFERIMENTO),
            anyString(), anyString(), anyString(), any());
        verify(anexoStorage, never()).removerAntigosDoTipo(any(), eq(TipoAnexo.OFICIO_INDEFERIMENTO), any());

        verify(anexoStorage).salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
            anyString(), eq("relatorio-processo-01-2026.pdf"), eq("application/pdf"), any());
        verify(anexoStorage).removerAntigosDoTipo(p, TipoAnexo.RELATORIO_FINAL, 20L);
        verify(processoService, never()).salvar(any());
    }

    @Test
    void canceladoNaoGeraOficioMasGeraRelatorioFinal() throws IOException {
        Processo p = processo(StatusProcesso.CANCELADO);
        when(relatorioService.gerar(p)).thenReturn("relatorio".getBytes());
        when(anexoStorage.salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
            anyString(), anyString(), anyString(), any())).thenReturn(anexoSalvo(20L));

        service.gerarDocumentos(p);

        verify(anexoStorage).removerAntigosDoTipo(p, TipoAnexo.RELATORIO_FINAL, 20L);
        verify(anexoStorage).salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
            anyString(), anyString(), anyString(), any());
    }

    // ---------- status ainda em andamento: nao gera nada ----------

    @Test
    void statusEmAndamentoNaoGeraDocumentoAlgum() {
        for (StatusProcesso status : new StatusProcesso[] {
                StatusProcesso.SOLICITADO, StatusProcesso.ENVIADO,
                StatusProcesso.SOLICITA_INFORMACAO}) {
            Processo p = processo(status);

            service.gerarDocumentos(p);

            verifyNoInteractions(relatorioService, anexoStorage, processoService);
        }
    }

    // ---------- numeracao propria do oficio (NNNN/AAAA) ----------

    @Test
    void primeiroOficioDoAnoRecebeNumero0001() throws IOException {
        Processo p = processo(StatusProcesso.INDEFERIDO);
        p.setNumeroOficio(null);
        p.setDataEmissaoOficio(LocalDate.of(2026, 5, 4));
        when(processoRepository.findNumerosOficioDoAno("2026")).thenReturn(java.util.List.of());
        when(relatorioService.gerar(p)).thenReturn(new byte[0]);
        when(anexoStorage.salvarBytes(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(anexoSalvo(1L));

        service.gerarDocumentos(p);

        assertThat(p.getNumeroOficio()).isEqualTo("0001/2026");
        verify(processoService).salvar(p);
    }

    @Test
    void doisIndeferimentosNoMesmoAnoRecebemNumerosSequenciaisDistintos() throws IOException {
        when(relatorioService.gerar(any())).thenReturn(new byte[0]);
        when(anexoStorage.salvarBytes(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(anexoSalvo(1L));

        Processo primeiro = processo(StatusProcesso.INDEFERIDO);
        primeiro.setNumeroOficio(null);
        primeiro.setDataEmissaoOficio(LocalDate.of(2026, 2, 1));
        when(processoRepository.findNumerosOficioDoAno("2026")).thenReturn(java.util.List.of());
        service.gerarDocumentos(primeiro);

        Processo segundo = processo(StatusProcesso.INDEFERIDO);
        segundo.setNumeroOficio(null);
        segundo.setDataEmissaoOficio(LocalDate.of(2026, 2, 2));
        // O primeiro ja gravou o dele: o repositorio agora devolve esse numero.
        when(processoRepository.findNumerosOficioDoAno("2026"))
            .thenReturn(java.util.List.of(primeiro.getNumeroOficio()));
        service.gerarDocumentos(segundo);

        assertThat(primeiro.getNumeroOficio()).isEqualTo("0001/2026");
        assertThat(segundo.getNumeroOficio()).isEqualTo("0002/2026");
        assertThat(segundo.getNumeroOficio()).isNotEqualTo(primeiro.getNumeroOficio());
    }

    @Test
    void proximoNumeroDeOficioComparaNumericamenteNaoComoTexto() {
        // "999/2026" seria "maior" que "1000/2026" numa comparacao de string:
        // a comparacao tem de ser numerica.
        when(processoRepository.findNumerosOficioDoAno("2026"))
            .thenReturn(java.util.List.of("0999/2026", "1000/2026", "0002/2026"));

        assertThat(service.proximoNumeroOficio(2026)).isEqualTo("1001/2026");
    }

    @Test
    void proximoNumeroDeOficioIgnoraValorForaDoPadrao() {
        when(processoRepository.findNumerosOficioDoAno("2026"))
            .thenReturn(java.util.List.of("SEM-NUMERO/2026", "0007/2026"));

        assertThat(service.proximoNumeroOficio(2026)).isEqualTo("0008/2026");
    }

    @Test
    void numeroDeOficioJaAtribuidoNaoMudaNumaSegundaChamada() throws IOException {
        Processo p = processo(StatusProcesso.INDEFERIDO);
        p.setNumeroOficio("1398/2026");
        when(relatorioService.gerar(p)).thenReturn(new byte[0]);
        when(anexoStorage.salvarBytes(any(), any(), anyString(), anyString(), anyString(), any()))
            .thenReturn(anexoSalvo(1L));

        service.gerarDocumentos(p);
        service.gerarDocumentos(p);

        assertThat(p.getNumeroOficio()).isEqualTo("1398/2026");
        verify(processoService, never()).salvar(any());
        verifyNoInteractions(processoRepository);
    }

    // ---------- IllegalStateException real: falha de IO ao salvar o PDF ----------

    @Test
    void falhaDeIoAoSalvarRelatorioFinalLancaIllegalStateExceptionParaDeferido() throws IOException {
        Processo p = processo(StatusProcesso.DEFERIDO);
        when(relatorioService.gerar(p)).thenReturn(new byte[0]);
        when(anexoStorage.salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
                anyString(), anyString(), anyString(), any()))
            .thenThrow(new IOException("permissao negada"));

        assertThatThrownBy(() -> service.gerarDocumentos(p))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("falhou ao gerar o relatorio final")
            .hasMessageContaining("permissao negada")
            .hasCauseInstanceOf(IOException.class);

        verify(anexoStorage, never()).removerAntigosDoTipo(any(), any(), any());
    }

    @Test
    void falhaDeIoAoSalvarRelatorioFinalLancaIllegalStateExceptionParaIndeferido() throws IOException {
        // INDEFERIDO nao gera mais oficio: o unico PDF do caminho e o
        // relatorio final, entao a falha dele e a unica que pode propagar.
        Processo p = processo(StatusProcesso.INDEFERIDO);
        when(relatorioService.gerar(p)).thenReturn(new byte[0]);
        when(anexoStorage.salvarBytes(eq(p), eq(TipoAnexo.RELATORIO_FINAL),
                anyString(), anyString(), anyString(), any()))
            .thenThrow(new IOException("falha de disco"));

        assertThatThrownBy(() -> service.gerarDocumentos(p))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("falhou ao gerar o relatorio final")
            .hasMessageContaining("falha de disco");
    }
}
