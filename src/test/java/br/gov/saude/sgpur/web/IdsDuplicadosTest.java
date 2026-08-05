package br.gov.saude.sgpur.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Impede que dois elementos do MESMO template tenham o mesmo {@code id}
 * literal (estatico) - HTML exige unicidade de {@code id} na pagina; um
 * duplicado quebra {@code getElementById}/{@code querySelector} (so acha o
 * primeiro), {@code <label for=...>} (associa so ao primeiro) e qualquer
 * {@code aria-labelledby}/{@code aria-controls} que aponte para ele.
 *
 * <p><b>Motivacao (achado O1 da vistoria de UI/UX de 2026-08-05).</b>
 * {@code processos/detalhe.html} tem {@code id="arquivo"} em 4 formularios
 * de upload diferentes (linhas atuais: documento clinico, comprovante de
 * envio, upload do oficio e comprovante SNT) - cada um com seu proprio
 * {@code <input type="file" id="arquivo">}. Um JS que faça
 * {@code document.getElementById('arquivo')} (ex.: para checar o arquivo
 * selecionado antes do envio) sempre pega o PRIMEIRO dos 4, nunca o
 * formulario que o usuario realmente está preenchendo. Esse achado esta
 * sendo corrigido em outro lote, em paralelo a esta vistoria de
 * acentuacao/testes-guarda - ver nota no commit deste arquivo sobre o teste
 * falhar temporariamente ate o merge do outro lote.
 *
 * <p><b>Por que so {@code id="literal"}, nunca {@code th:id}.</b> Um
 * {@code th:id="'corpo' + ${it.index}"} (id montado por expressao dentro de
 * um {@code th:each}) so produz o valor final em tempo de render - cada
 * iteracao do laço gera um id DIFERENTE de verdade, entao nao e um duplicado
 * real. O regex deste teste so entra em atributos {@code id="..."} puros
 * (nunca precedidos de {@code th:}), que so podem ser literais fixos -
 * exatamente o caso do achado O1 (4 {@code id="arquivo"} escritos a mao,
 * sem nenhuma expressao). Um {@code th:id} com valor dinamico nunca aparece
 * na varredura, entao nunca gera falso positivo aqui.
 *
 * <p>Nenhum teste de {@code MockMvc}/{@code @WebMvcTest} pega isso: a pagina
 * responde 200 e o HTML com id duplicado nao quebra nada no servidor - so
 * quebra o comportamento do JS no navegador.
 */
class IdsDuplicadosTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");

    /** {@code id="valor"} literal - nunca {@code th:id="..."} (lookbehind exclui o prefixo "th:"). */
    private static final Pattern ID_LITERAL = Pattern.compile("(?<!th:)\\bid\\s*=\\s*\"([^\"]*)\"");

    @Test
    void nenhumIdLiteralDuplicadoNoMesmoArquivo() throws IOException {
        List<String> duplicados = new ArrayList<>();

        try (Stream<Path> arquivos = Files.walk(TEMPLATES)) {
            arquivos.filter(p -> p.toString().endsWith(".html")).forEach(p -> {
                String html = HTML_COMMENT.matcher(ler(p)).replaceAll(" ");

                Map<String, Integer> contagemPorId = new LinkedHashMap<>();
                Matcher m = ID_LITERAL.matcher(html);
                while (m.find()) {
                    String id = m.group(1).trim();
                    if (id.isEmpty()) continue;
                    contagemPorId.merge(id, 1, Integer::sum);
                }

                contagemPorId.forEach((id, qtd) -> {
                    if (qtd > 1) {
                        duplicados.add(p + " -> id=\"" + id + "\" aparece " + qtd + "x");
                    }
                });
            });
        }

        assertThat(duplicados)
            .as("id HTML precisa ser unico por pagina - dois elementos com o mesmo id "
                + "quebram getElementById/querySelector/label-for/aria-*, que so encontram "
                + "o primeiro elemento com aquele id")
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
