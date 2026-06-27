package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Parecer;
import br.gov.saude.sgpur.domain.Processo;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/**
 * Gera o PDF "Solicitacao de Avaliacao — Urgencia Renal" destinado aos
 * medicos avaliadores. O documento NAO contem o nome completo do paciente
 * (apenas as iniciais), em conformidade com a LGPD.
 */
@Service
public class SolicitacaoAvaliadorService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color AZUL = new Color(13, 110, 253);
    private static final Color CINZA = new Color(108, 117, 125);
    private static final Color CINZA_BORDA = new Color(222, 226, 230);
    private static final Color AZUL_ESCURO = new Color(0, 53, 128);

    public byte[] gerar(Processo p) {
        Document doc = new Document(PageSize.A4, 50, 50, 55, 45);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            cabecalhoInstitucional(doc);
            tituloPrincipal(doc, p);
            secao(doc, "1. Identificacao do processo");
            dadosIdentificacao(doc, p);
            secao(doc, "2. Dados clinicos para avaliacao");
            dadosClinicos(doc, p);
            avaliadores(doc, p);
            rodape(doc);

            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar a solicitacao de avaliacao PDF", e);
        }
    }

    private void cabecalhoInstitucional(Document doc) throws DocumentException {
        Font fGov = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, AZUL_ESCURO);
        Font fSec = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, AZUL_ESCURO);

        PdfPTable tabCab = new PdfPTable(1);
        tabCab.setWidthPercentage(100);
        tabCab.setSpacingAfter(14);

        PdfPCell c1 = new PdfPCell(new Phrase("GOVERNO DO ESTADO DO RIO GRANDE DO SUL", fGov));
        c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        c1.setPadding(6);
        c1.setBorderColor(AZUL_ESCURO);
        c1.setBorderWidth(1.5f);
        tabCab.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase("SECRETARIA DE SAUDE — EQUIPE DE URGENCIA RENAL", fSec));
        c2.setHorizontalAlignment(Element.ALIGN_CENTER);
        c2.setPadding(4);
        c2.setBorderColor(AZUL_ESCURO);
        c2.setBorderWidth(1.5f);
        tabCab.addCell(c2);

        doc.add(tabCab);
    }

    private void tituloPrincipal(Document doc, Processo p) throws DocumentException {
        Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, Color.BLACK);
        Font fSub = FontFactory.getFont(FontFactory.HELVETICA, 10, CINZA);

        Paragraph titulo = new Paragraph("SOLICITACAO DE AVALIACAO — URGENCIA RENAL", fTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingAfter(4);
        doc.add(titulo);

        String iniciais = Iniciais.de(p.getPacienteNome());
        String subTexto = "Processo " + p.getNumero() + "  —  Paciente " + iniciais;
        Paragraph sub = new Paragraph(subTexto, fSub);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(16);
        doc.add(sub);
    }

    private void dadosIdentificacao(Document doc, Processo p) throws DocumentException {
        PdfPTable t = tabelaDados();
        linha(t, "Numero do processo", p.getNumero());
        linha(t, "Identificacao do paciente (iniciais)",
            Iniciais.de(p.getPacienteNome()) + "  [nome omitido - LGPD]");
        linha(t, "Equipe solicitante", nvl(p.getSolicitanteEquipe()));
        linha(t, "Data da situacao especial",
            p.getDataSituacaoEspecial() != null ? p.getDataSituacaoEspecial().format(DATA) : "-");
        linha(t, "Data de cadastro do processo",
            p.getDataCadastro() != null ? p.getDataCadastro().format(DATA_HORA) : "-");
        doc.add(t);
    }

    private void dadosClinicos(Document doc, Processo p) throws DocumentException {
        PdfPTable t = tabelaDados();
        linha(t, "RGCT / SNT", nvl(p.getPacienteRgct()));
        linha(t, "Observacoes / justificativa clinica", nvl(p.getObservacoes()));
        doc.add(t);
    }

    private void avaliadores(Document doc, Processo p) throws DocumentException {
        if (p.getPareceres() == null || p.getPareceres().isEmpty()) {
            return;
        }
        secao(doc, "3. Medicos avaliadores designados");
        PdfPTable t = new PdfPTable(new float[]{1, 4, 4});
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);

        Font fCab = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE);
        for (String col : new String[]{"#", "Medico", "Instituicao"}) {
            PdfPCell c = new PdfPCell(new Phrase(col, fCab));
            c.setBackgroundColor(CINZA);
            c.setPadding(5);
            t.addCell(c);
        }

        int idx = 1;
        for (Parecer par : p.getPareceres()) {
            Font fv = FontFactory.getFont(FontFactory.HELVETICA, 9, Color.BLACK);
            PdfPCell cNum = new PdfPCell(new Phrase(String.valueOf(idx++), fv));
            cNum.setHorizontalAlignment(Element.ALIGN_CENTER);
            cNum.setPadding(4);
            cNum.setBorderColor(CINZA_BORDA);
            t.addCell(cNum);

            PdfPCell cNome = new PdfPCell(new Phrase(par.getMembro().getNome(), fv));
            cNome.setPadding(4);
            cNome.setBorderColor(CINZA_BORDA);
            t.addCell(cNome);

            PdfPCell cInst = new PdfPCell(new Phrase(nvl(par.getMembro().getInstituicao()), fv));
            cInst.setPadding(4);
            cInst.setBorderColor(CINZA_BORDA);
            t.addCell(cInst);
        }
        doc.add(t);
    }

    private void rodape(Document doc) throws DocumentException {
        Font fRodape = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, CINZA);
        Paragraph rodape = new Paragraph(
            "Documento gerado automaticamente pelo SGPUR — uso restrito aos membros da Urgencia Renal. "
                + "Os dados pessoais do paciente foram omitidos em conformidade com a LGPD.",
            fRodape);
        rodape.setAlignment(Element.ALIGN_CENTER);
        rodape.setSpacingBefore(24);
        doc.add(rodape);
    }

    private void secao(Document doc, String texto) throws DocumentException {
        PdfPTable barra = new PdfPTable(1);
        barra.setWidthPercentage(100);
        barra.setSpacingBefore(12);
        barra.setSpacingAfter(2);
        Font f = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        PdfPCell c = new PdfPCell(new Phrase(texto, f));
        c.setBackgroundColor(AZUL);
        c.setPadding(5);
        c.setBorder(Rectangle.NO_BORDER);
        barra.addCell(c);
        doc.add(barra);
    }

    private PdfPTable tabelaDados() {
        PdfPTable t = new PdfPTable(new float[]{3, 7});
        t.setWidthPercentage(100);
        t.setSpacingBefore(4);
        return t;
    }

    private void linha(PdfPTable t, String rotulo, String valor) {
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

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
