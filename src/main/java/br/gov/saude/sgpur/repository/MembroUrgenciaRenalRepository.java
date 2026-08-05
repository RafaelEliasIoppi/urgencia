package br.gov.saude.sgpur.repository;

import br.gov.saude.sgpur.domain.MembroUrgenciaRenal;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MembroUrgenciaRenalRepository extends JpaRepository<MembroUrgenciaRenal, Long> {

    List<MembroUrgenciaRenal> findByAtivoTrueOrderByInstituicaoAsc();

    long countByAtivoTrue();

    List<MembroUrgenciaRenal> findByCoordenadorTrue();

    /**
     * Busca por nome, instituicao ou e-mail, resolvida no banco (mesmo
     * padrao de {@code ProcessoRepository.buscar}). {@code q} nulo/vazio
     * devolve todos, ordenados por instituicao (mesma ordem de
     * {@link #findByAtivoTrueOrderByInstituicaoAsc}). Sem paginacao: o
     * volume de membros e pequeno (equipe fixa de avaliadores), diferente de
     * Processo/SolicitacaoOnline.
     */
    @Query("""
        select m from MembroUrgenciaRenal m
        where (:q is null or :q = ''
               or lower(m.nome) like lower(concat('%', :q, '%'))
               or lower(m.instituicao) like lower(concat('%', :q, '%'))
               or lower(m.email) like lower(concat('%', :q, '%')))
        order by m.instituicao asc
        """)
    List<MembroUrgenciaRenal> buscar(@Param("q") String q);

    /**
     * Adquire um lock pessimista de escrita (SELECT ... FOR UPDATE) sobre TODAS
     * as linhas da tabela. Usado antes de promover um membro a coordenador CET-RS
     * para SERIALIZAR promocoes concorrentes: como a invariante "so 1 coordenador"
     * pode ser violada mesmo quando nao havia coordenador previo (duas requests
     * marcando linhas DIFERENTES, sem linha compartilhada para o @Version pegar),
     * um simples @Transactional sob READ COMMITTED nao basta. A tabela e pequena
     * (poucos membros) e a operacao e rara, entao o custo do lock e desprezivel.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MembroUrgenciaRenal m")
    List<MembroUrgenciaRenal> lockTodosParaCoordenador();
}
