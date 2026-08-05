package br.gov.saude.sgpur.repository;

import br.gov.saude.sgpur.domain.ControleUrgencia;
import br.gov.saude.sgpur.domain.SituacaoUrgencia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface ControleUrgenciaRepository extends JpaRepository<ControleUrgencia, Long> {

    long countBySituacao(SituacaoUrgencia situacao);

    /** Urgencias ativas que vencem ate a data informada. */
    @Query("SELECT c FROM ControleUrgencia c WHERE c.situacao = 'ATIVA' AND c.dataVencimento <= :ate")
    List<ControleUrgencia> findAVencerOuVencidas(LocalDate ate);

    /** Urgencias ativas (ATIVA ou RENOVADA) ordenadas por vencimento. */
    @Query("SELECT c FROM ControleUrgencia c WHERE c.ativo = true ORDER BY c.dataVencimento ASC")
    List<ControleUrgencia> findAllAtivasOrdenadas();

    /**
     * Mesma lista de {@link #findAllAtivasOrdenadas}, com busca por paciente,
     * RGCT ou equipe resolvida no banco (mesmo padrao de
     * {@code ProcessoRepository.buscar}). {@code q} nulo/vazio devolve todos
     * os ativos.
     */
    @Query("""
        select c from ControleUrgencia c
        where c.ativo = true
          and (:q is null or :q = ''
               or lower(c.nomePaciente) like lower(concat('%', :q, '%'))
               or lower(c.rgct) like lower(concat('%', :q, '%'))
               or lower(c.equipe) like lower(concat('%', :q, '%')))
        order by c.dataVencimento asc
        """)
    List<ControleUrgencia> buscarAtivas(@Param("q") String q);
}
