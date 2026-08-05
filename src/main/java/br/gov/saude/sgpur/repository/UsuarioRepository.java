package br.gov.saude.sgpur.repository;

import br.gov.saude.sgpur.domain.Perfil;
import br.gov.saude.sgpur.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Usado para impedir a exclusao/desativacao do ultimo ADMIN ativo (auto-lockout). */
    long countByPerfilAndAtivoTrue(Perfil perfil);

    /**
     * Usuarios ativos de um dos perfis informados - usado para notificar
     * ADMIN/OPERADOR quando chega uma nova SolicitacaoOnline (ver
     * SolicitacaoOnlineService).
     */
    List<Usuario> findByPerfilInAndAtivoTrue(List<Perfil> perfis);

    /**
     * Busca por login ou nome, resolvida no banco (mesmo padrao de
     * {@code ProcessoRepository.buscar}). {@code q} nulo/vazio devolve
     * todos. Nunca busca por senha/e-mail de propósito (a tela so mostra
     * login/nome/perfil, e o termo digitado aqui nunca vai para auditoria -
     * ver AuditoriaService, que so recebe id/username em outros pontos).
     */
    @Query("""
        select u from Usuario u
        where (:q is null or :q = ''
               or lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.nome) like lower(concat('%', :q, '%')))
        order by u.username asc
        """)
    List<Usuario> buscar(@Param("q") String q);
}
