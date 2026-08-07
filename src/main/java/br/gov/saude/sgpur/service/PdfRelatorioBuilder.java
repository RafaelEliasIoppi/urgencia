package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Anexo;
import br.gov.saude.sgpur.domain.Parecer;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.StatusProcesso;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Mecanica de construcao visual/binaria do Relatorio Final em PDF: paragrafos,
 * tabelas, fontes, cores, capa institucional e merge de paginas via
 * PdfCopy/PdfReader. Nao decide o que entra no relatorio nem em que ordem -
 * essa responsabilidade eh do {@link RelatorioService}, que chama estes
 * metodos ja com os dados/filtros aplicados.
 */
class PdfRelatorioBuilder {

    private static final Logger log = LoggerFactory.getLogger(PdfRelatorioBuilder.class);

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    // AZUL institucional (--rs-blue do app.css), nao mais o $primary do
    // Bootstrap 5 (#0D6EFD, new Color(13, 110, 253)) que o documento usava
    // antes - ver docs/RELATORIO-REFORMULACAO-RELATORIO-FINAL-PDF-2026-08.md
    // secao 6.1. Contraste branco-sobre-fundo sobe de 4,50:1 (limite exato
    // da WCAG AA) para 8,39:1, relevante num documento feito para ser
    // impresso e fotocopiado. Escopo estrito desta troca: SO o Relatorio
    // Final (esta classe) - CINZA/VERDE_ESCURO/VERMELHO permanecem os
    // mesmos defaults do Bootstrap, e o Relatorio Anual/Relatorio do
    // Avaliador (que replicam essas constantes em suas proprias classes)
    // NAO foram tocados; unificar a paleta nos demais documentos e decisao
    // de produto separada, ainda nao aprovada.
    static final Color AZUL = new Color(0x1A, 0x4D, 0x8F);
    static final Color CINZA = new Color(108, 117, 125);
    static final Color CINZA_BORDA = new Color(222, 226, 230);
    static final Color VERDE_ESCURO = new Color(25, 135, 84);
    static final Color VERMELHO = new Color(220, 53, 69);

    private final AnexoStorageService anexoStorage;

    PdfRelatorioBuilder(AnexoStorageService anexoStorage) {
        this.anexoStorage = anexoStorage;
    }

    Document abrirDocumentoA4(ByteArrayOutputStream out) throws DocumentException {
        // Margem superior 30pt (era 50pt): o PdfCabecalhoStamper ja expande
        // 55pt no topo fisico da pagina para desenhar o cabecalho carimbado
        // (logo + 2 linhas + regua em y=topo-44) - com 50pt de margem aqui,
        // sobravam quase 4,3cm vazios entre a regua do cabecalho e o
        // primeiro texto do corpo. 30pt deixa ~1,4cm de respiro (verificado
        // nas coordenadas: regua em ~853pt de 897pt de altura da pagina
        // expandida; texto do corpo passa a comecar em 841.89-30=811.89pt,
        // ainda ~41pt abaixo da regua - nao encosta nela).
        Document doc = new Document(PageSize.A4, 40, 40, 30, 40);
        PdfWriter.getInstance(doc, out);
        doc.open();
        return doc;
    }

    // -----------------------------------------------------------------------
    // Merge com anexos
    // -----------------------------------------------------------------------

    byte[] mergeComAnexos(byte[] summary, List<Anexo> pdfs, List<Anexo> naoPdf)
            throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfCopy copier = new PdfCopy(doc, baos);
        doc.open();

        try (PdfReader summaryReader = new PdfReader(summary)) {
            for (int i = 1; i <= summaryReader.getNumberOfPages(); i++) {
                copier.addPage(copier.getImportedPage(summaryReader, i));
            }
        }

        for (Anexo a : pdfs) {
            Path path = anexoStorage.resolverArquivo(a);
            if (!Files.exists(path)) {
                log.warn("Anexo PDF nao encontrado no disco: {} ({})", a.getNomeArquivo(), path);
                adicionarPaginaAviso(copier,
                    "Anexo nao encontrado: " + a.getNomeArquivo(),
                    "O arquivo \"" + a.getNomeArquivo()
                        + "\" (" + a.getTipo().getDescricao()
                        + ") nao foi localizado no disco.");
                continue;
            }
            try (PdfReader reader = new PdfReader(Files.readAllBytes(path))) {
                for (int i = 1; i <= reader.getNumberOfPages(); i++) {
                    copier.addPage(copier.getImportedPage(reader, i));
                }
            } catch (Exception e) {
                log.error("Erro ao importar anexo PDF {}: {}", a.getNomeArquivo(), e.getMessage());
                adicionarPaginaAviso(copier,
                    "Erro ao importar: " + a.getNomeArquivo(),
                    "Nao foi possivel importar \"" + a.getNomeArquivo()
                        + "\" (" + a.getTipo().getDescricao()
                        + "): " + e.getMessage());
            }
        }

        for (Anexo a : naoPdf) {
            adicionarPaginaAviso(copier,
                "Anexo (formato nao-PDF): " + a.getNomeArquivo(),
                "Tipo: " + a.getTipo().getDescricao()
                    + "\nArquivo: " + a.getNomeArquivo()
                    + "\nFormato: " + (a.getContentType() != null ? a.getContentType() : "desconhecido")
                    + "\nData: " + (a.getDataUpload() != null ? a.getDataUpload().format(DATA) : "-")
                    + "\n\nEste anexo esta disponivel para download na pagina do processo.");
        }

        doc.close();
        return baos.toByteArray();
    }

    private void adicionarPaginaAviso(PdfCopy copier, String titulo, String corpo)
            throws DocumentException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document d = new Document(PageSize.A4, 40, 40, 50, 40);
        PdfWriter.getInstance(d, baos);
        d.open();
        Paragraph pTitulo = new Paragraph(titulo,
            FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, CINZA));
        pTitulo.setSpacingAfter(20);
        d.add(pTitulo);
        Paragraph pCorpo = new Paragraph(corpo,
            FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK));
        d.add(pCorpo);
        d.close();

        try (PdfReader reader = new PdfReader(baos.toByteArray())) {
            copier.addPage(copier.getImportedPage(reader, 1));
        }
    }

    // -----------------------------------------------------------------------
    // Capa do relatorio / processo
    // -----------------------------------------------------------------------

    void adicionarCapa(Document doc, Processo p, String tituloDocumento,
                       boolean incluirSolicitante) throws DocumentException {

        Font fOrgao = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.BLACK);
        Font fSubOrgao = FontFactory.getFont(FontFactory.HELVETICA, 12, Color.BLACK);
        Font fUrgencia = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, AZUL);
        Font fTituloDoc = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
        Font fRotulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, CINZA);
        Font fValor = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.BLACK);
        Font fCabTabela = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        Font fCelTabela = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        Font fRodapeCapa = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, CINZA);

        try {
            byte[] logoBytes = getClass().getClassLoader()
                .getResourceAsStream("static/brasao.png").readAllBytes();
            Image brasao = Image.getInstance(logoBytes);
            brasao.scaleToFit(110, 110);
            brasao.setAlignment(Element.ALIGN_CENTER);
            brasao.setSpacingAfter(12);
            doc.add(brasao);
        } catch (Exception e) {
            log.warn("Logo nao encontrado em static/brasao.png, capa sem imagem");
        }

        Paragraph orgao = new Paragraph(PdfCabecalhoStamper.NOME_INSTITUICAO, fOrgao);
        orgao.setAlignment(Element.ALIGN_CENTER);
        doc.add(orgao);

        Paragraph secretaria = new Paragraph(PdfCabecalhoStamper.SECRETARIA, fSubOrgao);
        secretaria.setAlignment(Element.ALIGN_CENTER);
        doc.add(secretaria);

        Paragraph urgenciaRenal = new Paragraph("URGENCIA RENAL", fUrgencia);
        urgenciaRenal.setAlignment(Element.ALIGN_CENTER);
        urgenciaRenal.setSpacingAfter(36);
        doc.add(urgenciaRenal);

        PdfPTable separador = new PdfPTable(1);
        separador.setWidthPercentage(60);
        separador.setSpacingAfter(40);
        PdfPCell linhaSep = new PdfPCell(new Phrase(" "));
        linhaSep.setBorderWidthBottom(1.5f);
        linhaSep.setBorderWidthTop(0);
        linhaSep.setBorderWidthLeft(0);
        linhaSep.setBorderWidthRight(0);
        linhaSep.setBorderColor(AZUL);
        linhaSep.setPadding(0);
        separador.addCell(linhaSep);
        doc.add(separador);

        Paragraph tituloDoc = new Paragraph(tituloDocumento, fTituloDoc);
        tituloDoc.setAlignment(Element.ALIGN_CENTER);
        tituloDoc.setSpacingAfter(40);
        doc.add(tituloDoc);

        PdfPTable tDados = new PdfPTable(new float[]{3.5f, 6.5f});
        tDados.setWidthPercentage(80);
        tDados.setHorizontalAlignment(Element.ALIGN_CENTER);
        tDados.setSpacingAfter(30);

        adicionarLinhaCapa(tDados, "Nº do Processo:", nvl(p.getNumero()), fRotulo, fValor);
        adicionarLinhaCapa(tDados, "Paciente:", nvl(p.getPacienteNome()), fRotulo, fValor);
        adicionarLinhaCapa(tDados, "RGCT / SNT:", nvl(p.getPacienteRgct()), fRotulo, fValor);

        if (incluirSolicitante) {
            adicionarLinhaCapa(tDados, "Equipe solicitante:", nvl(p.getSolicitanteEquipe()), fRotulo, fValor);
            adicionarLinhaCapa(tDados, "E-mail do solicitante:", nvl(p.getSolicitanteEmail()), fRotulo, fValor);
            String dataSit = p.getDataSituacaoEspecial() != null
                ? p.getDataSituacaoEspecial().format(DATA) : "-";
            adicionarLinhaCapa(tDados, "Data de solicitacao da urgencia renal:", dataSit, fRotulo, fValor);
        }

        // Fallback: processo ja finalizado sem dataDecisao (dado legado/
        // importado, pois decidir() sempre seta a data) mostra a data de
        // hoje como aproximacao em vez de "-", que confundiria num
        // relatorio de processo claramente encerrado.
        String dataDecisaoStr;
        if (p.getDataDecisao() != null) {
            dataDecisaoStr = p.getDataDecisao().format(DATA);
        } else if (p.getStatus().isFinalizado()) {
            dataDecisaoStr = java.time.LocalDate.now().format(DATA);
        } else {
            dataDecisaoStr = "-";
        }
        adicionarLinhaCapa(tDados, "Data da decisao:", dataDecisaoStr, fRotulo, fValor);

        if (p.getStatus().isFinalizado()) {
            String textoResultado;
            Color corResultado;
            if (p.getStatus() == StatusProcesso.DEFERIDO) {
                textoResultado = "DEFERIDO";
                corResultado = VERDE_ESCURO;
            } else if (p.getStatus() == StatusProcesso.INDEFERIDO) {
                textoResultado = "INDEFERIDO";
                corResultado = VERMELHO;
            } else {
                textoResultado = p.getStatus().getDescricao().toUpperCase();
                corResultado = CINZA;
            }
            Font fResultado = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, corResultado);
            PdfPCell cRotulo = new PdfPCell(new Phrase("Resultado:", fRotulo));
            cRotulo.setPadding(5);
            cRotulo.setBorderColor(CINZA_BORDA);
            cRotulo.setHorizontalAlignment(Element.ALIGN_RIGHT);
            PdfPCell cValor = new PdfPCell(new Phrase(textoResultado, fResultado));
            cValor.setPadding(5);
            cValor.setBorderColor(CINZA_BORDA);
            cValor.setHorizontalAlignment(Element.ALIGN_LEFT);
            tDados.addCell(cRotulo);
            tDados.addCell(cValor);
        } else {
            adicionarLinhaCapa(tDados, "Resultado:", "Em andamento", fRotulo,
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, CINZA));
        }
        doc.add(tDados);

        Paragraph tituloAval = new Paragraph("MEDICOS AVALIADORES", fUrgencia);
        tituloAval.setAlignment(Element.ALIGN_CENTER);
        tituloAval.setSpacingAfter(6);
        doc.add(tituloAval);

        PdfPTable tAval = new PdfPTable(new float[]{5, 3, 4});
        tAval.setWidthPercentage(90);
        tAval.setHorizontalAlignment(Element.ALIGN_CENTER);
        tAval.setSpacingAfter(30);

        for (String col : new String[]{"Nome", "Instituição", "Parecer"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, fCabTabela));
            c.setBackgroundColor(AZUL);
            c.setPadding(5);
            c.setHorizontalAlignment(Element.ALIGN_CENTER);
            tAval.addCell(c);
        }
        tAval.setHeaderRows(1);

        for (Parecer par : p.getPareceres()) {
            PdfPCell cNome = new PdfPCell(new Phrase(par.getMembro().getNome(), fCelTabela));
            PdfPCell cInst = new PdfPCell(new Phrase(par.getMembro().getInstituicao(), fCelTabela));

            String textoResultadoPar;
            Color corResultadoPar = Color.BLACK;
            // Impedido (conflito de interesse - avaliador e o proprio
            // solicitante do processo) e um estado diferente de "sem voto
            // ainda"/"dispensado pela maioria" e precisa de rotulo proprio -
            // ver o mesmo tratamento em RelatorioService, secao "2. Pareceres".
            if (par.isImpedido()) {
                textoResultadoPar = "Impedido (conflito de interesse)";
                corResultadoPar = CINZA;
            } else if (par.getResultado() != null) {
                textoResultadoPar = par.getResultado().getDescricao();
                if (par.getResultado().name().equals("FAVORAVEL")) {
                    corResultadoPar = VERDE_ESCURO;
                } else if (par.getResultado().name().equals("NAO_FAVORAVEL")) {
                    corResultadoPar = VERMELHO;
                }
            } else if (p.getStatus().isFinalizado()) {
                textoResultadoPar = "Dispensado pela maioria";
                corResultadoPar = CINZA;
            } else {
                textoResultadoPar = "Pendente";
                corResultadoPar = CINZA;
            }
            Font fParResultado = FontFactory.getFont(FontFactory.HELVETICA, 9, corResultadoPar);
            PdfPCell cPar = new PdfPCell(new Phrase(textoResultadoPar, fParResultado));

            for (PdfPCell c : new PdfPCell[]{cNome, cInst, cPar}) {
                c.setPadding(5);
                c.setBorderColor(CINZA_BORDA);
                c.setHorizontalAlignment(Element.ALIGN_CENTER);
            }
            tAval.addCell(cNome);
            tAval.addCell(cInst);
            tAval.addCell(cPar);
        }
        doc.add(tAval);

        Paragraph rodapeCapa = new Paragraph(
            "Documento gerado pelo SAUR em " + LocalDate.now().format(DATA), fRodapeCapa);
        rodapeCapa.setAlignment(Element.ALIGN_CENTER);
        rodapeCapa.setSpacingBefore(20);
        doc.add(rodapeCapa);
    }

    private void adicionarLinhaCapa(PdfPTable t, String rotulo, String valor,
                                    Font fRotulo, Font fValor) {
        PdfPCell c1 = new PdfPCell(new Phrase(rotulo, fRotulo));
        c1.setPadding(5);
        c1.setBorderColor(CINZA_BORDA);
        c1.setHorizontalAlignment(Element.ALIGN_RIGHT);
        PdfPCell c2 = new PdfPCell(new Phrase(valor, fValor));
        c2.setPadding(5);
        c2.setBorderColor(CINZA_BORDA);
        c2.setHorizontalAlignment(Element.ALIGN_LEFT);
        t.addCell(c1);
        t.addCell(c2);
    }

    // -----------------------------------------------------------------------
    // Helpers de layout
    // -----------------------------------------------------------------------

    void secao(Document doc, Font fSecao, String texto) throws DocumentException {
        PdfPTable barra = new PdfPTable(1);
        barra.setWidthPercentage(100);
        barra.setSpacingBefore(12);
        barra.setSpacingAfter(2);
        PdfPCell c = new PdfPCell(new Phrase(texto, fSecao));
        c.setBackgroundColor(AZUL);
        c.setPadding(5);
        c.setBorder(Rectangle.NO_BORDER);
        barra.addCell(c);
        doc.add(barra);
    }

    PdfPTable tabelaDados() {
        PdfPTable t = new PdfPTable(new float[]{3, 7});
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        return t;
    }

    void linha(PdfPTable t, String rotulo, String valor) {
        Font fr = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, CINZA);
        Font fv = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
        PdfPCell c1 = new PdfPCell(new Phrase(rotulo, fr));
        PdfPCell c2 = new PdfPCell(new Phrase(valor, fv));
        for (PdfPCell c : new PdfPCell[]{c1, c2}) {
            c.setPadding(4);
            c.setBorderColor(CINZA_BORDA);
        }
        t.addCell(c1);
        t.addCell(c2);
    }

    /**
     * Linha rotulo:valor com destaque visual - usada para o dado mais
     * importante de uma secao (hoje so o resultado da decisao, secao "3.
     * Decisao final" do sumario). Fonte maior + negrito + CAIXA ALTA
     * garantem que a informacao sobreviva a impressao em preto-e-branco; a
     * cor semantica ({@code cor}) e reforco visual, nunca o unico portador
     * do dado (mesmo principio ja usado na capa, {@link #adicionarCapa}).
     */
    void linhaDestaque(PdfPTable t, String rotulo, String valor, Color cor) {
        Font fr = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, CINZA);
        Font fv = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, cor);
        PdfPCell c1 = new PdfPCell(new Phrase(rotulo, fr));
        c1.setPadding(6);
        c1.setBorderColor(CINZA_BORDA);
        c1.setVerticalAlignment(Element.ALIGN_MIDDLE);
        PdfPCell c2 = new PdfPCell(new Phrase(valor.toUpperCase(), fv));
        c2.setPadding(6);
        c2.setBorderColor(CINZA_BORDA);
        c2.setVerticalAlignment(Element.ALIGN_MIDDLE);
        t.addCell(c1);
        t.addCell(c2);
    }

    // Cor de preenchimento unificada para "titulo de bloco": a barra de
    // secao (secao(), acima) e o cabecalho de tabela (abaixo) usam AZUL,
    // igual ja fazia a tabela de avaliadores da capa (adicionarCapa) - antes
    // o cabecalho de tabela usava CINZA cheio, uma segunda cor de "titulo"
    // sem nenhuma regra visivel de quando usar qual. VERDE_ESCURO/VERMELHO
    // ficam reservados exclusivamente para o resultado da decisao.
    void cabecalho(PdfPTable t, String... cols) {
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String col : cols) {
            PdfPCell c = new PdfPCell(new Phrase(col, f));
            c.setBackgroundColor(AZUL);
            c.setBorderColor(CINZA_BORDA);
            c.setPadding(4);
            t.addCell(c);
        }
        // Achado B2 do relatorio V2: sem isto, uma tabela que quebra entre
        // paginas termina a pagina com o cabecalho e ZERO linhas de dado, e a
        // pagina seguinte comeca com dado nenhum sem rotulo de coluna. Custo:
        // uma linha, aqui uma unica vez para as 3 tabelas que usam este helper
        // (pareceres, andamento, anexos).
        t.setHeaderRows(1);
    }

    void celula(PdfPTable t, String texto, int align, boolean bold) {
        Font f = FontFactory.getFont(bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 9, Color.BLACK);
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setPadding(4);
        c.setHorizontalAlignment(align);
        c.setBorderColor(CINZA_BORDA);
        t.addCell(c);
    }

    static String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
