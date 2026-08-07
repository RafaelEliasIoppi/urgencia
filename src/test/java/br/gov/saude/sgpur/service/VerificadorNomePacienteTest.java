package br.gov.saude.sgpur.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes da verificacao deterministica de nome de paciente/equipe solicitante
 * (docs/RELATORIO-CHAT-MEMBROS-OPERADORES-2026-08.md, secao 8.1).
 */
class VerificadorNomePacienteTest {

    private final VerificadorNomePaciente verificador = new VerificadorNomePaciente();

    @Test
    void textoLivreDeReferenciaAoPacienteOuEquipeELivre() {
        var r = verificador.verificar("O PDF deste processo abriu em branco, poderia reenviar?",
            "Mariana da Rosa Martins", "Hospital de Clinicas de Porto Alegre");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.LIVRE);
        assertThat(r.bloqueado()).isFalse();
    }

    @Test
    void nomeCompletoDoPacienteEBloqueado() {
        var r = verificador.verificar("A Mariana Martins pediu para reagendar o exame.",
            "Mariana da Rosa Martins", "HCPA");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.BLOQUEADO);
        assertThat(r.bloqueado()).isTrue();
    }

    @Test
    void apenasUmTokenDoNomeGeraAlerta() {
        // "Rosa" e um sobrenome comum: 1 token so (nao "Mariana Martins" juntos)
        // deve gerar ALERTA, nao BLOQUEADO direto - mitiga falso-positivo.
        var r = verificador.verificar("Precisamos falar sobre uma rosa que apareceu no laudo.",
            "Mariana da Rosa Martins", "HCPA");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.ALERTA);
        assertThat(r.bloqueado()).isFalse();
    }

    @Test
    void substringFalsaNaoDisparaAlerta() {
        // "Ana" nao pode casar dentro de "analise" - exige palavra inteira.
        var r = verificador.verificar("Preciso de uma nova análise clínica deste caso.",
            "Ana Paula Souza", "HCPA");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.LIVRE);
    }

    @Test
    void equipeSolicitanteCitadaEBloqueada() {
        var r = verificador.verificar("O Hospital de Clinicas ligou pedindo prioridade.",
            "Fulano de Tal", "Hospital de Clinicas de Porto Alegre");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.BLOQUEADO);
    }

    @Test
    void textoComAcentoENormalizadoAntesDeComparar() {
        var r = verificador.verificar("Confirmar dados de José da Conceição hoje.",
            "Jose da Conceicao", "HCPA");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.BLOQUEADO);
    }

    @Test
    void nomeVazioOuNuloNuncaBloqueiaNadaAlemDaEquipe() {
        var r = verificador.verificar("Mensagem qualquer sem nada de especial.", "", "");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.LIVRE);
    }

    @Test
    void textoNuloOuEmBrancoELivre() {
        var r = verificador.verificar(null, "Mariana da Rosa Martins", "HCPA");
        assertThat(r.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.LIVRE);
        var r2 = verificador.verificar("   ", "Mariana da Rosa Martins", "HCPA");
        assertThat(r2.nivel()).isEqualTo(VerificadorNomePaciente.Nivel.LIVRE);
    }
}
