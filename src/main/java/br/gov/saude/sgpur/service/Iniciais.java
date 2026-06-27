package br.gov.saude.sgpur.service;

import java.text.Normalizer;
import java.util.Set;

/**
 * Gera as iniciais de um nome (ex.: "Mariana da Rosa Martins" -> "M.R.M."),
 * ignorando conectivos. Usado para identificar o paciente sem expor o nome
 * completo (LGPD) nas comunicacoes com os medicos.
 */
public final class Iniciais {

    private static final Set<String> CONECTIVOS = Set.of("da", "de", "do", "dos", "das", "e");

    private Iniciais() {
    }

    public static String de(String nomeCompleto) {
        if (nomeCompleto == null || nomeCompleto.isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String parte : nomeCompleto.trim().split("\\s+")) {
            String semAcento = Normalizer.normalize(parte, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
            if (semAcento.isBlank() || CONECTIVOS.contains(parte.toLowerCase())) {
                continue;
            }
            char c = semAcento.charAt(0);
            if (Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c)).append('.');
            }
        }
        return sb.toString();
    }
}
