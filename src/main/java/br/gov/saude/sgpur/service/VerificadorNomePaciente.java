package br.gov.saude.sgpur.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Verificacao DETERMINISTICA (nunca heuristica de "detectar nomes proprios",
 * o que seria inviavel e cheio de falso-positivo) de que uma mensagem do
 * OPERADOR ao avaliador nao cita o nome do paciente nem a equipe
 * solicitante daquele processo especifico.
 *
 * <p>So e possivel porque o canal e "por processo" (ver
 * docs/RELATORIO-CHAT-MEMBROS-OPERADORES-2026-08.md, secao 8.1): o servidor
 * sabe exatamente qual string nao pode aparecer. Reaproveita a mesma tecnica
 * de normalizacao/casamento por palavra inteira ja validada em
 * {@link ConflitoEquipeMatcher} e {@link Iniciais}.</p>
 *
 * <p><b>So se aplica ao lado OPERADOR</b> (nunca ao avaliador escrevendo: ele
 * nao sabe o nome do paciente, entao a checagem so produziria ruido - o
 * risco de imparcialidade e estruturalmente unidirecional).</p>
 */
@Component
public class VerificadorNomePaciente {

    public enum Nivel { LIVRE, ALERTA, BLOQUEADO }

    public record Resultado(Nivel nivel, List<String> termosEncontrados) {
        public boolean bloqueado() {
            return nivel == Nivel.BLOQUEADO;
        }
    }

    private static final Set<String> CONECTIVOS = Set.of("da", "de", "do", "dos", "das", "e");
    private static final Set<String> STOPWORDS_EQUIPE = Set.of(
        "hospital", "de", "da", "do", "dos", "das", "e", "o", "a", "os", "as",
        "em", "no", "na", "sem", "equipe", "servico", "clinica", "unidade");

    /**
     * Verifica um texto livre contra o nome do paciente e a equipe
     * solicitante de UM processo especifico.
     *
     * <p>Regra de nivel: 2+ tokens do nome (ou o nome completo, que sempre e
     * >=2 tokens significativos na pratica) presentes -> BLOQUEADO (recusa o
     * envio); exatamente 1 token do nome -> ALERTA (pede confirmacao, cobre
     * sobrenome comum sem travar o operador); qualquer token da equipe
     * solicitante -> BLOQUEADO direto (nao ha ambiguidade legitima de "1
     * token de equipe" como ha com sobrenome comum).</p>
     */
    public Resultado verificar(String texto, String pacienteNome, String solicitanteEquipe) {
        if (texto == null || texto.isBlank()) {
            return new Resultado(Nivel.LIVRE, List.of());
        }
        String textoNormalizado = normalizar(texto);

        List<String> tokensEquipeEncontrados = tokensEncontrados(textoNormalizado,
            tokensSignificativosEquipe(normalizar(solicitanteEquipe)));
        if (!tokensEquipeEncontrados.isEmpty()) {
            return new Resultado(Nivel.BLOQUEADO, tokensEquipeEncontrados);
        }

        List<String> tokensNomeEncontrados = tokensEncontrados(textoNormalizado,
            tokensSignificativosNome(pacienteNome));
        if (tokensNomeEncontrados.size() >= 2) {
            return new Resultado(Nivel.BLOQUEADO, tokensNomeEncontrados);
        }
        if (tokensNomeEncontrados.size() == 1) {
            return new Resultado(Nivel.ALERTA, tokensNomeEncontrados);
        }
        return new Resultado(Nivel.LIVRE, List.of());
    }

    /** Tokens do nome do paciente, sem conectivos e com >=4 caracteres (evita falso-positivo de sigla curta). */
    private static List<String> tokensSignificativosNome(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String parte : nomeCompleto.trim().split("\\s+")) {
            String semAcento = normalizar(parte);
            if (semAcento.isBlank() || CONECTIVOS.contains(parte.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (semAcento.length() >= 4) {
                tokens.add(semAcento);
            }
        }
        return tokens;
    }

    /** Tokens significativos da equipe solicitante (>=3 chars, sem stopwords), mesmo criterio de ConflitoEquipeMatcher. */
    private static List<String> tokensSignificativosEquipe(String equipeNormalizada) {
        if (equipeNormalizada == null || equipeNormalizada.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        for (String t : equipeNormalizada.split(" ")) {
            if (t.length() >= 3 && !STOPWORDS_EQUIPE.contains(t)) {
                tokens.add(t);
            }
        }
        return tokens;
    }

    /** Quais dos tokens aparecem, como PALAVRA INTEIRA (nao substring - "Ana" nao deve casar em "analise"), no texto normalizado. */
    private static List<String> tokensEncontrados(String textoNormalizado, List<String> tokens) {
        List<String> encontrados = new ArrayList<>();
        for (String tok : tokens) {
            if (Pattern.compile("\\b" + Pattern.quote(tok) + "\\b").matcher(textoNormalizado).find()) {
                encontrados.add(tok);
            }
        }
        return encontrados;
    }

    /** Minusculas, sem acento, so [a-z0-9] e espaco simples - mesma normalizacao de ConflitoEquipeMatcher. */
    private static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        String semAcento = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
