package br.gov.saude.sgpur.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ver {@code docs/RELATORIO-STATUS-PROCESSO-12-2026-2026-08-11.md}, Achado 6:
 * {@code getBootstrapBadge()} ja usava {@code bg-primary} (azul) para
 * {@code ENVIADO}, mas {@code getTom()} devolvia {@code "neutral"} - uma
 * recaida latente da padronizacao de cores de 2026-08-06 ("Aguardando" =
 * azul), so inofensiva porque nenhum template consumia {@code getTom()}
 * ainda. Este teste trava o alinhamento entre os dois metodos para
 * {@code ENVIADO}.
 */
class StatusProcessoTest {

    @Test
    void enviadoTemTomAguardandoAlinhadoComOBadgeAzul() {
        assertThat(StatusProcesso.ENVIADO.getTom()).isEqualTo("aguardando");
        assertThat(StatusProcesso.ENVIADO.getBootstrapBadge()).isEqualTo("bg-primary");
    }

    @Test
    void solicitadoContinuaNeutroAlinhadoComOBadgeCinza() {
        assertThat(StatusProcesso.SOLICITADO.getTom()).isEqualTo("neutral");
        assertThat(StatusProcesso.SOLICITADO.getBootstrapBadge()).isEqualTo("bg-secondary");
    }

    @Test
    void demaisTonsPermanecemInalterados() {
        assertThat(StatusProcesso.SOLICITA_INFORMACAO.getTom()).isEqualTo("attention");
        assertThat(StatusProcesso.DEFERIDO.getTom()).isEqualTo("ok");
        assertThat(StatusProcesso.INDEFERIDO.getTom()).isEqualTo("danger");
        assertThat(StatusProcesso.CANCELADO.getTom()).isEqualTo("neutral");
    }
}
