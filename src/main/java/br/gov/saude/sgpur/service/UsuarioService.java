package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Usuario;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UsuarioService {

    private final UsuarioRepository repo;
    private final PasswordEncoder encoder;

    public UsuarioService(UsuarioRepository repo, PasswordEncoder encoder) {
        this.repo = repo;
        this.encoder = encoder;
    }

    public List<Usuario> listar() {
        return repo.findAll();
    }

    public Usuario buscar(Long id) {
        return repo.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado: " + id));
    }

    /** Cria um novo usuario, codificando a senha. */
    @Transactional
    public Usuario criar(Usuario u, String senhaPura) {
        if (repo.existsByUsername(u.getUsername())) {
            throw new IllegalArgumentException("Ja existe um usuario com este login.");
        }
        u.setSenha(encoder.encode(senhaPura));
        return repo.save(u);
    }

    /** Atualiza dados; troca a senha apenas se 'senhaPura' for informada. */
    @Transactional
    public Usuario atualizar(Long id, Usuario form, String senhaPura) {
        Usuario u = buscar(id);
        u.setNome(form.getNome());
        u.setPerfil(form.getPerfil());
        u.setAtivo(form.isAtivo());
        if (senhaPura != null && !senhaPura.isBlank()) {
            u.setSenha(encoder.encode(senhaPura));
        }
        return repo.save(u);
    }

    @Transactional
    public void alternarAtivo(Long id) {
        Usuario u = buscar(id);
        u.setAtivo(!u.isAtivo());
        repo.save(u);
    }
}
