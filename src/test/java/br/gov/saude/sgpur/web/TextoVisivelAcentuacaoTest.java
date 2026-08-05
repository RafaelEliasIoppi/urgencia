package br.gov.saude.sgpur.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Impede que "palavras-armadilha" (grafias sem acentuacao que so existem em
 * portugues por erro de digitacao) voltem a aparecer em TEXTO VISIVEL dos
 * templates.
 *
 * <p><b>Motivacao (vistoria de UI/UX de 2026-08-05, achado S1).</b> Uma
 * varredura manual achou varias ocorrencias de acentuacao faltando em texto
 * visivel ao usuario (nao em comentario/atributo tecnico): "Ver so
 * pendentes" ({@code processos/lista.html}), "Ainda nao ha processos"
 * ({@code relatorios/anual.html}), "E preciso ter"
 * ({@code relatorios/avaliador.html}), "esta anonimizado"/"O processo esta
 * em pausa" (dois {@code data-confirm-msg}/paragrafo de
 * {@code processos/detalhe.html}), "Aguardando ha N dias" (mesmo arquivo),
 * "Este aviso e visivel"/"nenhuma alteracao e feita" ({@code dashboard.html})
 * e "Motivo (visivel ao solicitante)"
 * ({@code processos/solicitacoes-online-detalhe.html}). Corrigidas na mesma
 * sessao; este teste evita que qualquer uma delas (ou uma nova ocorrencia da
 * mesma classe de erro) volte no futuro sem ninguem notar.
 *
 * <p><b>Por que so 6 palavras, e por que "esta" nao entra na lista
 * generica.</b> "nao", "ha", "visivel", "possivel", "numero" e "usuario"
 * nunca sao grafias corretas em portugues como palavras isoladas - qualquer
 * ocorrencia e sempre um acento faltando ("nao" -&gt; "não", "ha" -&gt; "há",
 * "visivel" -&gt; "visível", "possivel" -&gt; "possível", "numero" ->
 * "número", "usuario" -&gt; "usuário"). Ja "esta" e AMBIGUA: e a grafia
 * CORRETA do pronome demonstrativo feminino ("esta acao", "esta urgencia",
 * "esta solicitacao" - todos corretos sem acento) e SO fica errada quando e
 * o verbo "estar" ("o processo esta em pausa" deveria ser "está"). Colocar
 * "esta" na lista generica geraria dezenas de falsos positivos nos varios
 * {@code data-confirm-msg="Cancelar esta ..."} espalhados pelo sistema. Por
 * isso o verbo "estar" tem um padrao proprio, mais estreito: so dispara
 * quando "esta" vem logo depois de um sujeito comum de terceira pessoa
 * (processo, documento, isso, ele, ela, situacao, pedido) - o mesmo padrao
 * que pegou os dois achados reais desta vistoria ("processo esta em pausa",
 * "documento esta anonimizado") sem marcar nenhum dos ~15 usos legitimos do
 * pronome demonstrativo.
 *
 * <p><b>Onde o teste procura.</b> Mesmo espirito de {@link
 * AcessibilidadeBotaoIconeTest}/{@link AcessibilidadeEstruturaTest}: le o
 * arquivo bruto, remove comentarios HTML/Thymeleaf e o conteudo de
 * {@code <script>}/{@code <style>} (nunca visivel, ou e codigo), e so entao
 * extrai (a) texto entre tags (cobre {@code <p>}, {@code <span>},
 * {@code <label>}, {@code <h1-6>}, {@code <option>} etc. - qualquer texto
 * visivel de verdade fica entre duas tags) e (b) o valor literal dos
 * atributos {@code alt}, {@code placeholder}, {@code title},
 * {@code data-confirm-msg} e {@code th:text} (só quando o valor não é uma
 * expressao Thymeleaf, isto e, nao contem {@code $}, {@code #} nem chaves -
 * um {@code th:text="${var}"} nunca e examinado, so o texto/fallback
 * literal). Nomes de variavel, {@code id=}, {@code class=}, {@code name=} e
 * qualquer coisa dentro de {@code <script>} nunca entram na varredura.
 *
 * <p>Nenhum teste de {@code MockMvc}/{@code @WebMvcTest} pega isso: a pagina
 * responde 200 e o texto sem acento nao quebra nada no servidor - so um
 * usuario lendo a tela percebe.
 */
class TextoVisivelAcentuacaoTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    private static final Pattern HTML_COMMENT = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern SCRIPT_TAG = Pattern.compile("(?is)<script\\b.*?</script>");
    private static final Pattern STYLE_TAG = Pattern.compile("(?is)<style\\b.*?</style>");

    private static final Pattern TEXTO_ENTRE_TAGS = Pattern.compile(">([^<>]+)<");
    private static final Pattern ATRIBUTO_VISIVEL = Pattern.compile(
        "\\b(?:alt|placeholder|title|data-confirm-msg)\\s*=\\s*\"([^\"]*)\"");
    /** {@code th:text="literal"} - nunca captura expressao ({@code $}/{@code #}/chaves). */
    private static final Pattern TH_TEXT_LITERAL = Pattern.compile(
        "\\bth:text\\s*=\\s*\"([^\"$#{}]*)\"");

    /** Palavras que NUNCA sao a grafia correta em portugues - qualquer ocorrencia e um acento faltando. */
    private static final List<Pattern> ARMADILHAS_SEM_AMBIGUIDADE = List.of(
        Pattern.compile("\\bnao\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bha\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bvisivel\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpossivel\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bnumero\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\busuario\\b", Pattern.CASE_INSENSITIVE)
    );

    /**
     * "esta" so como VERBO (deveria ser "está") - sujeito comum de 3a pessoa
     * logo antes. Ver javadoc da classe para por que "esta" sozinha nao
     * entra em {@link #ARMADILHAS_SEM_AMBIGUIDADE}.
     */
    private static final Pattern ESTA_VERBO = Pattern.compile(
        "\\b(processo|documento|isso|ele|ela|situacao|pedido|anexo|parecer)\\s+esta\\b",
        Pattern.CASE_INSENSITIVE);

    @Test
    void textoVisivelSemPalavrasComAcentuacaoFaltando() throws IOException {
        List<String> achados = new ArrayList<>();

        try (Stream<Path> arquivos = Files.walk(TEMPLATES)) {
            arquivos.filter(p -> p.toString().endsWith(".html")).forEach(p -> {
                String bruto = ler(p);
                String semRuido = HTML_COMMENT.matcher(bruto).replaceAll(" ");
                semRuido = SCRIPT_TAG.matcher(semRuido).replaceAll(" ");
                semRuido = STYLE_TAG.matcher(semRuido).replaceAll(" ");

                List<String> textosVisiveis = new ArrayList<>();
                Matcher mTexto = TEXTO_ENTRE_TAGS.matcher(semRuido);
                while (mTexto.find()) textosVisiveis.add(mTexto.group(1));
                Matcher mAtributo = ATRIBUTO_VISIVEL.matcher(semRuido);
                while (mAtributo.find()) textosVisiveis.add(mAtributo.group(1));
                Matcher mThText = TH_TEXT_LITERAL.matcher(semRuido);
                while (mThText.find()) textosVisiveis.add(mThText.group(1));

                for (String texto : textosVisiveis) {
                    if (texto.isBlank()) continue;

                    for (Pattern armadilha : ARMADILHAS_SEM_AMBIGUIDADE) {
                        Matcher m = armadilha.matcher(texto);
                        if (m.find()) {
                            achados.add(p + " -> \"" + texto.trim() + "\" contem \""
                                + m.group() + "\" sem acentuacao");
                        }
                    }

                    Matcher mVerbo = ESTA_VERBO.matcher(texto);
                    if (mVerbo.find()) {
                        achados.add(p + " -> \"" + texto.trim()
                            + "\" usa \"esta\" como verbo (deveria ser \"está\")");
                    }
                }
            });
        }

        assertThat(achados)
            .as("texto visivel ao usuario com acentuacao faltando - corrija a grafia "
                + "(ver javadoc da classe para a lista de palavras-armadilha verificadas)")
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
