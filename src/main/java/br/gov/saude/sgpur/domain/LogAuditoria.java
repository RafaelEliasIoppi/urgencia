package br.gov.saude.sgpur.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Registro de auditoria de uma acao relevante no sistema (quem, o que, quando).
 * Apoia o requisito de o processo ser auditavel.
 */
@Entity
@Table(name = "log_auditoria", indexes = @Index(name = "idx_log_data", columnList = "data_hora"))
public class LogAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_hora", nullable = false)
    private LocalDateTime dataHora = LocalDateTime.now();

    /** Login do usuario que executou a acao (ou "sistema"). */
    @Column(nullable = false, length = 60)
    private String usuario;

    /** Codigo da acao. Ex.: PROCESSO_CADASTRADO, PROCESSO_DECIDIDO. */
    @Column(nullable = false, length = 40)
    private String acao;

    @Column(length = 400)
    private String detalhe;

    public LogAuditoria() {
    }

    public LogAuditoria(String usuario, String acao, String detalhe) {
        this.usuario = usuario;
        this.acao = acao;
        this.detalhe = detalhe;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }

    public String getDetalhe() {
        return detalhe;
    }

    public void setDetalhe(String detalhe) {
        this.detalhe = detalhe;
    }
}
