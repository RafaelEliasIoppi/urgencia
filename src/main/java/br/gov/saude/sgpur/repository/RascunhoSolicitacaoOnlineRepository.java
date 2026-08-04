package br.gov.saude.sgpur.repository;

import br.gov.saude.sgpur.domain.RascunhoSolicitacaoOnline;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RascunhoSolicitacaoOnlineRepository extends JpaRepository<RascunhoSolicitacaoOnline, Long> {

    Optional<RascunhoSolicitacaoOnline> findByUsuarioSolicitanteId(Long usuarioSolicitanteId);

    void deleteByUsuarioSolicitanteId(Long usuarioSolicitanteId);
}
