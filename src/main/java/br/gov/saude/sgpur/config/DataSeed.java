package br.gov.saude.sgpur.config;

import br.gov.saude.sgpur.domain.MembroUrgenciaRenal;
import br.gov.saude.sgpur.domain.Perfil;
import br.gov.saude.sgpur.domain.Usuario;
import br.gov.saude.sgpur.repository.MembroUrgenciaRenalRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Carga inicial dos membros da Urgencia Renal (extraidos da planilha 2026).
 * So insere se a tabela estiver vazia.
 */
@Configuration
public class DataSeed {

    @Bean
    CommandLineRunner seedMembros(MembroUrgenciaRenalRepository repo) {
        return args -> {
            if (repo.count() > 0) {
                return;
            }
            List<MembroUrgenciaRenal> membros = List.of(
                new MembroUrgenciaRenal("HBBL", "Marcia Abichequer", null),
                new MembroUrgenciaRenal("HNSP", "Cristiane M da Silveira Souto", null),
                new MembroUrgenciaRenal("HSLPUC", "Ivan Antonello", null),
                new MembroUrgenciaRenal("ISCMPA", "Clotilde Garcia", null),
                new MembroUrgenciaRenal("HCPA", "Veronica Horbe", null),
                new MembroUrgenciaRenal("SGN", "Marcelo Generali da Costa", null),
                new MembroUrgenciaRenal("HCl", "Ana Lucia", null),
                new MembroUrgenciaRenal("CET", "Rogerio Caruso Bezerra", null)
            );
            repo.saveAll(membros);
        };
    }

    /** Cria o usuario administrador inicial (se nenhum usuario existir). */
    @Bean
    CommandLineRunner seedAdmin(UsuarioRepository usuarioRepo, PasswordEncoder encoder,
                                @Value("${app.admin.username}") String adminUser,
                                @Value("${app.admin.password}") String adminPassword) {
        return args -> {
            if (usuarioRepo.count() > 0) {
                return;
            }
            Usuario admin = new Usuario();
            admin.setUsername(adminUser);
            admin.setNome("Administrador");
            admin.setSenha(encoder.encode(adminPassword));
            admin.setPerfil(Perfil.ADMIN);
            admin.setAtivo(true);
            usuarioRepo.save(admin);
        };
    }
}
