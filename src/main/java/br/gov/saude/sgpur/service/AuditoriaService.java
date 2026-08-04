package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.LogAuditoria;
import br.gov.saude.sgpur.repository.LogAuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Registra acoes relevantes para auditoria. O usuario e obtido do contexto
 * de seguranca. Falhas de log nunca devem quebrar a acao principal.
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final LogAuditoriaRepository repo;

    public AuditoriaService(LogAuditoriaRepository repo) {
        this.repo = repo;
    }

    public void registrar(String acao, String detalhe) {
        registrar(acao, detalhe, null);
    }

    /**
     * Registra a acao com o IP do cliente. Usar quando o IP for relevante para
     * nao-repudio (ex.: voto autenticado do Portal do Avaliador).
     */
    public void registrar(String acao, String detalhe, String ip) {
        try {
            String usuario = usuarioAtual();
            String det = (detalhe != null && detalhe.length() > 400) ? detalhe.substring(0, 400) : detalhe;
            repo.save(new LogAuditoria(usuario, acao, det, ip));
        } catch (Exception e) {
            // auditoria nunca pode interromper a operacao principal, mas uma
            // falha aqui (ex.: banco fora do ar durante um voto) nao pode
            // ficar totalmente invisivel - loga para nao perder o rastro.
            log.warn("Falha ao registrar auditoria (acao={}): {}", acao, e.getMessage());
        }
    }

    public Page<LogAuditoria> listar(Pageable pageable) {
        return repo.findAllByOrderByDataHoraDesc(pageable);
    }

    /**
     * Listagem filtrada da trilha (usuario / acao / periodo). Qualquer
     * parametro nulo ou vazio simplesmente nao filtra, entao esta chamada
     * cobre tambem o caso "sem filtro nenhum".
     */
    public Page<LogAuditoria> buscar(String usuario, String acao,
                                     java.time.LocalDate de, java.time.LocalDate ate,
                                     Pageable pageable) {
        // O periodo e informado em DATA, mas o campo gravado e data+hora: o dia
        // final precisa ir ate 23:59:59, senao "ate = hoje" esconderia tudo o
        // que aconteceu hoje depois da meia-noite (ou seja, o dia inteiro).
        java.time.LocalDateTime inicio = de != null ? de.atStartOfDay() : null;
        java.time.LocalDateTime fim = ate != null ? ate.atTime(java.time.LocalTime.MAX) : null;
        return repo.buscar(usuario, acao, inicio, fim, pageable);
    }

    /** Acoes distintas ja registradas, para o select do filtro. */
    public java.util.List<String> acoesDistintas() {
        return repo.acoesDistintas();
    }

    private String usuarioAtual() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        // getName() de um token com principal nulo retorna "" (nao null) - checar
        // so != null deixava o campo "usuario" do log em branco nesse caso.
        return (auth != null && auth.getName() != null && !auth.getName().isBlank())
            ? auth.getName() : "sistema";
    }
}
