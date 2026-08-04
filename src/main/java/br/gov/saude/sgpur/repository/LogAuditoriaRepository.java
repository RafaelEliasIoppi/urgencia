package br.gov.saude.sgpur.repository;

import br.gov.saude.sgpur.domain.LogAuditoria;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogAuditoriaRepository extends JpaRepository<LogAuditoria, Long> {

    Page<LogAuditoria> findAllByOrderByDataHoraDesc(Pageable pageable);

    /**
     * Busca filtrada da trilha de auditoria (/auditoria).
     *
     * <p><b>Motivacao (auditoria de UI, 2026-08-04).</b> A tela nao tinha filtro
     * nenhum - nem por usuario, nem por acao, nem por periodo -, e e exatamente
     * a tela que se usa quando ja se sabe o que procurar: "o que o usuario X fez
     * ontem", "quem excluiu este anexo". A unica navegacao era paginar do mais
     * recente para tras, 30 em 30.</p>
     *
     * <p>Todos os parametros sao opcionais (null = nao filtra), entao a mesma
     * consulta serve a listagem completa.</p>
     */
    @org.springframework.data.jpa.repository.Query("""
        select l from LogAuditoria l
        where (:usuario is null or :usuario = ''
               or lower(l.usuario) like lower(concat('%', :usuario, '%')))
          and (:acao is null or :acao = '' or l.acao = :acao)
          and (:de is null or l.dataHora >= :de)
          and (:ate is null or l.dataHora <= :ate)
        order by l.dataHora desc
        """)
    Page<LogAuditoria> buscar(@org.springframework.data.repository.query.Param("usuario") String usuario,
                              @org.springframework.data.repository.query.Param("acao") String acao,
                              @org.springframework.data.repository.query.Param("de") java.time.LocalDateTime de,
                              @org.springframework.data.repository.query.Param("ate") java.time.LocalDateTime ate,
                              Pageable pageable);

    /** Acoes distintas ja registradas, para alimentar o filtro da tela. */
    @org.springframework.data.jpa.repository.Query("select distinct l.acao from LogAuditoria l order by l.acao")
    java.util.List<String> acoesDistintas();
}
