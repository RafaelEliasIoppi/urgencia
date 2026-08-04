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
 * Guarda a estrutura de acessibilidade que o {@code layout.html} pressupoe.
 *
 * <p><b>Motivacao (auditoria de 2026-08-04).</b> O layout coloca, como primeiro
 * elemento focavel de toda pagina, um atalho
 * {@code <a href="#conteudo">Pular para o conteudo</a>}. Ele so funciona se a
 * tela tiver um {@code <main id="conteudo">} para receber o foco. Na epoca da
 * auditoria esse alvo existia em 5 dos 25 templates (so os Portais, feitos na
 * Fase 9): nas outras 20 telas o atalho apontava para um id inexistente e o
 * foco nao ia a lugar nenhum - e 18 delas nao tinham {@code <main>} nenhum,
 * entao nem o "ir para o conteudo principal" do leitor de tela funcionava.
 *
 * <p><b>Por que um teste de arquivo e nao de MockMvc.</b> A pagina responde 200
 * normalmente sem o landmark: nada quebra no servidor. Um
 * {@code @WebMvcTest} verifica status e model attributes, nunca a estrutura do
 * HTML renderizado. Sem este teste, a proxima tela criada nasce com o atalho
 * quebrado de novo e ninguem percebe - foi exatamente assim que as 20
 * acumularam.
 *
 * <p>Mesmo padrao (e mesma justificativa) de
 * {@link AcessibilidadeBotaoIconeTest}.
 */
class AcessibilidadeEstruturaTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /** O proprio layout define o atalho; nao e uma "tela" com conteudo proprio. */
    private static final String LAYOUT = "layout.html";

    /** Telas que incluem a navbar sao as que herdam o skip-link do layout. */
    private static final Pattern USA_NAVBAR = Pattern.compile("layout\\s*::\\s*navbar");

    private static final Pattern MAIN_CONTEUDO =
        Pattern.compile("<main\\b[^>]*\\bid\\s*=\\s*\"conteudo\"");

    @Test
    void todaTelaComNavbarTemMainConteudoParaOSkipLink() throws IOException {
        List<String> semLandmark = new ArrayList<>();

        try (Stream<Path> arquivos = Files.walk(TEMPLATES)) {
            arquivos.filter(p -> p.toString().endsWith(".html"))
                .filter(p -> !p.getFileName().toString().equals(LAYOUT))
                .forEach(p -> {
                    String html = ler(p);
                    if (!USA_NAVBAR.matcher(html).find()) return;   // login/erro sem menu
                    if (MAIN_CONTEUDO.matcher(html).find()) return;
                    semLandmark.add(p.toString());
                });
        }

        assertThat(semLandmark)
            .as("toda tela que inclui 'layout :: navbar' herda o skip-link "
                + "<a href=\"#conteudo\"> e precisa de um <main id=\"conteudo\"> "
                + "como alvo, senao o atalho de teclado nao leva a lugar nenhum")
            .isEmpty();
    }

    /**
     * Um {@code <main>} aberto e outro fechado por arquivo. Pega o erro classico
     * de trocar a tag de abertura ({@code <div>} -> {@code <main>}) e esquecer o
     * {@code </div>} correspondente, que produz HTML invalido silenciosamente.
     */
    @Test
    void mainAbertoEFechadoNaMesmaQuantidade() throws IOException {
        List<String> desbalanceados = new ArrayList<>();

        try (Stream<Path> arquivos = Files.walk(TEMPLATES)) {
            arquivos.filter(p -> p.toString().endsWith(".html")).forEach(p -> {
                String html = semComentarios(ler(p));
                long abre = contar(html, "<main\\b");
                long fecha = contar(html, "</main>");
                if (abre != fecha) {
                    desbalanceados.add(p + " (abre=" + abre + ", fecha=" + fecha + ")");
                }
            });
        }

        assertThat(desbalanceados)
            .as("<main> aberto sem fechar (ou o contrario) gera HTML invalido")
            .isEmpty();
    }

    /**
     * Referencia ARIA apontando para um {@code id} inexistente no mesmo arquivo.
     *
     * <p>Achado real da mesma auditoria: os 5 paineis do wizard de
     * {@code processos/detalhe.html} declaravam
     * {@code aria-labelledby="tab-recebimento"} (e os outros 4), mas nenhum
     * desses ids existia - as abas eram geradas em laco e nao recebiam
     * {@code id}. O componente se declarava {@code role="tablist"} sem fornecer
     * nada do que esses papeis exigem.
     *
     * <p><b>Ids gerados em laco.</b> Quando o id vem de uma expressao com
     * prefixo literal ({@code th:id="'tab-' + ${...}"}), o valor final so
     * existe em tempo de render. Nesse caso o teste aceita qualquer referencia
     * que comece pelo prefixo literal - continua pegando o erro que importa (um
     * id que nao existe em lugar nenhum, como os 5 {@code tab-*} da auditoria)
     * sem produzir falso positivo no id montado por expressao.
     */
    @Test
    void referenciaAriaApontaParaIdQueExiste() throws IOException {
        Pattern ariaRef = Pattern.compile(
            "\\baria-(?:labelledby|controls|describedby)\\s*=\\s*\"([^\"$*{}]+)\"");
        // th:id="'prefixo-' + ${...}" -> captura o prefixo literal
        Pattern idComPrefixo = Pattern.compile("\\bth:id\\s*=\\s*\"'([^']+)'\\s*\\+");
        List<String> quebradas = new ArrayList<>();

        try (Stream<Path> arquivos = Files.walk(TEMPLATES)) {
            arquivos.filter(p -> p.toString().endsWith(".html")).forEach(p -> {
                String html = semComentarios(ler(p));

                List<String> prefixosGerados = new ArrayList<>();
                Matcher pre = idComPrefixo.matcher(html);
                while (pre.find()) prefixosGerados.add(pre.group(1));

                Matcher m = ariaRef.matcher(html);
                while (m.find()) {
                    for (String id : m.group(1).trim().split("\\s+")) {
                        if (id.isEmpty()) continue;
                        boolean existe = Pattern.compile(
                                "\\b(?:th:)?id\\s*=\\s*\"'?" + Pattern.quote(id))
                            .matcher(html).find()
                            || prefixosGerados.stream().anyMatch(id::startsWith);
                        if (!existe) {
                            int linha = (int) html.substring(0, m.start()).chars()
                                .filter(c -> c == '\n').count() + 1;
                            quebradas.add(p + ":" + linha + " -> aria aponta para id '" + id + "'");
                        }
                    }
                }
            });
        }

        assertThat(quebradas)
            .as("aria-labelledby/controls/describedby apontando para id inexistente: "
                + "o leitor de tela nao encontra o nome/alvo e o atributo vira ruido")
            .isEmpty();
    }

    private static long contar(String texto, String regex) {
        return Pattern.compile(regex).matcher(texto).results().count();
    }

    private static String semComentarios(String html) {
        return html.replaceAll("(?s)<!--.*?-->", "");
    }

    private static String ler(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
