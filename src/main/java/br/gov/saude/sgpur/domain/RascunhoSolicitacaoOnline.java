package br.gov.saude.sgpur.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Rascunho de {@link SolicitacaoOnline}, salvo pelo proprio solicitante
 * enquanto ainda esta preenchendo o formulario de "Nova solicitacao"
 * (Fase 11, item 3 do plano de UI - implementada mediante aval explicito do
 * usuario em 2026-08-04). Entidade de STAGING deliberadamente separada, nao
 * um status novo em {@link SolicitacaoOnline}: os campos dessa entidade sao
 * {@code @NotBlank}/{@code @NotNull} porque protegem o pedido REAL (o que a
 * equipe de Urgencia Renal vai analisar) - relaxar essas anotacoes para
 * acomodar um rascunho incompleto abriria a possibilidade de, por engano ou
 * bug futuro, um pedido incompleto virar uma {@code SolicitacaoOnline} de
 * verdade sem passar pela validacao completa. Aqui, ao contrario, NENHUM
 * campo e obrigatorio (o rascunho pode estar vazio) - so os limites de
 * tamanho ({@code @Size}, que e null-safe: nao rejeita campo em branco) sao
 * mantidos, para nao permitir texto absurdamente longo mesmo num rascunho.
 *
 * <p><b>Nunca aparece para o operador</b>: nao ha nenhuma tela/consulta de
 * triagem que leia esta tabela - o rascunho so vira visivel pela equipe de
 * Urgencia Renal quando o solicitante de fato clica em "Enviar solicitacao"
 * ({@code SolicitanteController#criar}), que cria uma
 * {@link SolicitacaoOnline} de verdade (com todas as validacoes) e apaga o
 * rascunho em seguida.
 *
 * <p><b>Um rascunho por solicitante</b>: {@code usuario_solicitante_id} e
 * {@code unique}. Salvar um novo rascunho sobrescreve o anterior (mesmo
 * padrao de "ultimo estado salvo", nao um historico de rascunhos).
 */
@Entity
@Table(name = "rascunho_solicitacao_online")
public class RascunhoSolicitacaoOnline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_solicitante_id", nullable = false, unique = true)
    private Usuario usuarioSolicitante;

    @Size(max = 200, message = "Nome do paciente muito longo (maximo 200 caracteres).")
    @Column(name = "paciente_nome", length = 200)
    private String pacienteNome;

    @Size(max = 60, message = "Registro RGCT/SNT muito longo (maximo 60 caracteres).")
    @Column(name = "paciente_rgct", length = 60)
    private String pacienteRgct;

    @Column(name = "data_situacao_especial")
    private LocalDate dataSituacaoEspecial;

    @Column(name = "justificativa_clinica", columnDefinition = "TEXT")
    private String justificativaClinica;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm = LocalDateTime.now();

    @Version
    @Column(name = "versao")
    private Long versao;

    public RascunhoSolicitacaoOnline() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Usuario getUsuarioSolicitante() {
        return usuarioSolicitante;
    }

    public void setUsuarioSolicitante(Usuario usuarioSolicitante) {
        this.usuarioSolicitante = usuarioSolicitante;
    }

    public String getPacienteNome() {
        return pacienteNome;
    }

    public void setPacienteNome(String pacienteNome) {
        this.pacienteNome = pacienteNome;
    }

    public String getPacienteRgct() {
        return pacienteRgct;
    }

    public void setPacienteRgct(String pacienteRgct) {
        this.pacienteRgct = pacienteRgct;
    }

    public LocalDate getDataSituacaoEspecial() {
        return dataSituacaoEspecial;
    }

    public void setDataSituacaoEspecial(LocalDate dataSituacaoEspecial) {
        this.dataSituacaoEspecial = dataSituacaoEspecial;
    }

    public String getJustificativaClinica() {
        return justificativaClinica;
    }

    public void setJustificativaClinica(String justificativaClinica) {
        this.justificativaClinica = justificativaClinica;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    public Long getVersao() {
        return versao;
    }

    public void setVersao(Long versao) {
        this.versao = versao;
    }
}
