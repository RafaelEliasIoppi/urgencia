package br.gov.saude.sgpur.config;

import br.gov.saude.sgpur.domain.MembroUrgenciaRenal;
import br.gov.saude.sgpur.domain.Perfil;
import br.gov.saude.sgpur.domain.Usuario;
import br.gov.saude.sgpur.repository.MembroUrgenciaRenalRepository;
import br.gov.saude.sgpur.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

/**
 * Carga inicial dos membros da Urgencia Renal (extraidos da planilha 2026).
 * So insere se a tabela estiver vazia.
 */
@Configuration
public class DataSeed {

    private static final Logger log = LoggerFactory.getLogger(DataSeed.class);

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

    /**
     * Cria um usuario AVALIADOR de exemplo vinculado ao primeiro membro ativo.
     * APENAS em perfil dev/H2 — nao executado em prod ou desktop.
     * Login: avaliador1 / avaliador123
     */
    @Bean
    @Profile("dev")
    CommandLineRunner seedAvaliadorExemplo(UsuarioRepository usuarioRepo,
                                           MembroUrgenciaRenalRepository membroRepo,
                                           PasswordEncoder encoder) {
        return args -> {
            if (usuarioRepo.findByUsername("avaliador1").isPresent()) {
                return; // ja existe, nao duplicar
            }
            List<MembroUrgenciaRenal> membros = membroRepo.findByAtivoTrueOrderByInstituicaoAsc();
            if (membros.isEmpty()) {
                log.warn("DataSeed: nenhum membro ativo encontrado, usuario avaliador1 nao criado.");
                return;
            }
            MembroUrgenciaRenal primeiroMembro = membros.get(0);
            Usuario avaliador = new Usuario();
            avaliador.setUsername("avaliador1");
            avaliador.setNome("Avaliador Exemplo (" + primeiroMembro.getNome() + ")");
            avaliador.setSenha(encoder.encode("avaliador123"));
            avaliador.setPerfil(Perfil.AVALIADOR);
            avaliador.setAtivo(true);
            avaliador.setMembro(primeiroMembro);
            usuarioRepo.save(avaliador);
            log.info("DataSeed: usuario avaliador1 criado e vinculado a {} - {}.",
                primeiroMembro.getInstituicao(), primeiroMembro.getNome());
        };
    }
}
