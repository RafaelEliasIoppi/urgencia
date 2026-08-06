package br.gov.saude.sgpur.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guarda a regua de tokens de fonte do design system (ver "Design system -
 * regua de tokens" no CLAUDE.md): nenhum {@code style="..."} inline em
 * template deveria fixar um {@code font-size} numerico livre - ou vem de uma
 * classe utilitaria do Bootstrap, ou (nos casos que precisam mesmo de um
 * valor customizado) de um dos 5 tokens {@code var(--saur-font-xs/sm/md/lg/
 * xl)}, que reagem a densidade por portal (ADMIN/OPERADOR vs AVALIADOR/
 * SOLICITANTE - ver {@code [data-densidade]} em {@code app.css}).
 *
 * <p><b>Estado conhecido no momento em que este teste foi escrito
 * (2026-08-05, leva de infraestrutura do design system): este teste FALHA
 * de proposito.</b> Ainda existem {@code style="font-size:..."} inline
 * espalhados pelos templates (12 arquivos no levantamento desta sessao) -
 * essa limpeza e trabalho de OUTRAS levas em paralelo (migrar template a
 * template para os tokens novos), fora do escopo desta leva, que só criou a
 * infraestrutura (tokens + regra de densidade + fragment de traducao de
 * tom). A falha aqui e o mesmo padrao ja usado no projeto para travar um
 * teste de guarda antes da limpeza estar completa (ver o historico de
 * "IDs duplicados"/testes de guarda citados no CLAUDE.md) - documentando a
 * falha esperada em vez de desabilitar o teste, para ele acender sozinho
 * assim que a ultima ocorrencia for migrada.</p>
 *
 * <p><b>Por que um teste de arquivo e nao de MockMvc.</b> Um
 * {@code @WebMvcTest} verifica status e model attributes, nunca o
 * {@code style=} literal do HTML renderizado - mesma justificativa de
 * {@link AcessibilidadeEstruturaTest}.</p>
 */
class DesignSystemFontSizeInlineTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** Os 5 valores REM da escala de tokens - permitidos mesmo como literal
     * numerico, porque sao exatamente o valor que os tokens ja assumem hoje
     * (o objetivo do teste e pegar valor FORA da escala, nao proibir todo
     * numero). */
    private static final Set<String> VALORES_DA_ESCALA =
        Set.of(".75rem", "0.75rem", ".875rem", "0.875rem", "1rem", "1.0rem",
            "1.25rem", "1.75rem");

    private static final Pattern FONT_SIZE_INLINE = Pattern.compile(
        "style\\s*=\\s*\"[^\"]*font-size\\s*:\\s*([^;\"]+)[;\"]");

    private static final Pattern USA_TOKEN = Pattern.compile("var\\(--saur-font-");

    @Test
    void nenhumStyleInlineFixaFontSizeForaDaEscalaDeTokens() throws IOException {
        List<String> ocorrencias = new ArrayList<>();

        try (Stream<Path> arquivos = Files.walk(TEMPLATES)) {
            arquivos.filter(p -> p.toString().endsWith(".html")).forEach(p -> {
                String html = ler(p);
                Matcher m = FONT_SIZE_INLINE.matcher(html);
                while (m.find()) {
                    String valor = m.group(1).trim();
                    if (USA_TOKEN.matcher(valor).find()) continue;       // ja usa var(--saur-font-*)
                    if (VALORES_DA_ESCALA.contains(valor)) continue;     // literal igual a um token
                    int linha = (int) html.substring(0, m.start()).chars()
                        .filter(c -> c == '\n').count() + 1;
                    ocorrencias.add(p + ":" + linha + " -> font-size:" + valor);
                }
            });
        }

        assertThat(ocorrencias)
            .as("style= inline com font-size fora da regua de tokens --saur-font-xs/sm/md/lg/xl "
                + "(ver 'Design system - regua de tokens' no CLAUDE.md). FALHA ESPERADA nesta "
                + "sessao (2026-08-05): a limpeza dos templates existentes e trabalho de outra "
                + "leva em paralelo, fora do escopo desta - este teste documenta o estado atual "
                + "e deve acender sozinho (virar verde) assim que a ultima ocorrencia for migrada.")
            .isEmpty();
    }

    private static String ler(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
