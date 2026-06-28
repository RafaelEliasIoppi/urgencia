package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.MembroUrgenciaRenal;
import br.gov.saude.sgpur.domain.Parecer;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.ResultadoParecer;
import br.gov.saude.sgpur.domain.StatusProcesso;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelatorioAnualServiceTest {

    private final RelatorioAnualService service = new RelatorioAnualService();

    private Processo processo(String numero, int sequencial, StatusProcesso status) {
        Processo p = new Processo();
        p.setNumero(numero);
        p.setAno(2026);
        p.setSequencial(sequencial);
        p.setPacienteNome("Paciente " + sequencial);
        p.setPacienteRgct("RGCT-" + sequencial);
        p.setSolicitanteEquipe("Hospital X");
        p.setDataSituacaoEspecial(LocalDate.of(2026, 3, 1));
        p.setStatus(status);
        Parecer par = new Parecer(new MembroUrgenciaRenal("HCPA", "Dr. " + sequencial, null));
        par.setResultado(ResultadoParecer.FAVORAVEL);
        p.addParecer(par);
        return p;
    }

    @Test
    void geraPdfNaoVazioParaAnoComProcessos() {
        List<Processo> processos = List.of(
            processo("01/2026", 1, StatusProcesso.DEFERIDO),
            processo("02/2026", 2, StatusProcesso.INDEFERIDO),
            processo("03/2026", 3, StatusProcesso.ENVIADO));

        byte[] pdf = service.gerar(2026, processos);

        assertThat(pdf).isNotEmpty();
        // assinatura de arquivo PDF: "%PDF"
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void geraPdfMesmoSemProcessosNoAno() {
        byte[] pdf = service.gerar(2030, List.of());
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
