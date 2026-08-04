package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.config.EmailProperties;
import br.gov.saude.sgpur.domain.Processo;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Gera o Oficio de Indeferimento em PDF (documento formal enviado a equipe
 * solicitante e disponibilizado no Portal do Solicitante). Modelo com brasao,
 * cabecalho institucional, numero proprio do oficio, local e data por extenso,
 * destinatario, motivo e fecho com a assinatura configurada.
 *
 * <p><b>Nada de placeholder literal aqui</b> (corrigido em 2026-08-04): a
 * cidade e a assinatura vem de configuracao ({@code sgpur.email.oficio-cidade}
 * e {@code sgpur.email.assinatura}, ambas em {@link EmailProperties}), nunca
 * das strings "Local," / "Responsavel" que sairam impressas no documento
 * oficial ate esta data.</p>
 *
 * <p><b>Acentuacao:</b> este texto e o unico do sistema que sai da instituicao
 * como documento formal, entao usa acentuacao correta - diferente de
 * {@code ResultadoParecer.descricao} e afins, deliberadamente sem acento por
 * alimentarem varios relatorios/exportacoes internas.</p>
 */
@Service
public class OficioService {

    private static final Logger log = LoggerFactory.getLogger(OficioService.class);

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    /** Exibido no lugar do numero do oficio em processos anteriores a numeracao propria. */
    static final String NUMERO_NAO_ATRIBUIDO = "(numero nao atribuido)";

    private final EmailProperties emailProperties;

    public OficioService(EmailProperties emailProperties) {
        this.emailProperties = emailProperties;
    }

    public byte[] gerar(Processo p) {
        Document doc = new Document(PageSize.A4, 56, 56, 56, 56);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font fCab = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font fCabSub = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(80, 80, 80));
            Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
            Font fCorpo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);

            // Brasao institucional (mesmo tratamento de PdfRelatorioBuilder:
            // se o recurso sumir do classpath, loga e segue sem imagem - a
            // geracao do oficio nunca pode falhar por causa do timbre).
            adicionarBrasao(doc);

            // Cabecalho institucional
            Paragraph cab = new Paragraph(PdfCabecalhoStamper.NOME_INSTITUICAO, fCab);
            cab.setAlignment(Element.ALIGN_CENTER);
            doc.add(cab);
            Paragraph cab2 = new Paragraph("URGÊNCIA RENAL", fCabSub);
            cab2.setAlignment(Element.ALIGN_CENTER);
            cab2.setSpacingAfter(24);
            doc.add(cab2);

            // Numero do oficio e data
            LocalDate dataEmissao = p.getDataEmissaoOficio() != null ? p.getDataEmissaoOficio() : LocalDate.now();
            String numeroOficio = (p.getNumeroOficio() == null || p.getNumeroOficio().isBlank())
                ? NUMERO_NAO_ATRIBUIDO : p.getNumeroOficio();
            Paragraph titulo = new Paragraph(
                "Ofício nº " + numeroOficio + " - INDEFERIMENTO - " + p.identificacao(), fTitulo);
            titulo.setSpacingAfter(6);
            doc.add(titulo);

            String mes = dataEmissao.getMonth().getDisplayName(TextStyle.FULL, PT_BR);
            Paragraph local = new Paragraph(
                cidade() + ", " + dataEmissao.getDayOfMonth() + " de " + mes
                    + " de " + dataEmissao.getYear() + ".",
                fCorpo);
            local.setAlignment(Element.ALIGN_RIGHT);
            local.setSpacingAfter(18);
            doc.add(local);

            // Destinatario
            Paragraph dest = new Paragraph("Ao(À) solicitante: " + p.getSolicitanteEquipe(), fCorpo);
            dest.setSpacingAfter(12);
            doc.add(dest);

            // Corpo
            String motivo = (p.getMotivoIndeferimento() == null || p.getMotivoIndeferimento().isBlank())
                ? "(motivo não informado)" : p.getMotivoIndeferimento();
            String texto = """
                Prezado(a) Senhor(a),

                Em referência ao Processo de Urgência Renal n. %s, referente ao(à) paciente \
                %s, comunicamos que, após análise dos pareceres da equipe de Urgência Renal, \
                o pedido foi INDEFERIDO.

                Motivo do indeferimento: %s

                Permanecemos à disposição para os esclarecimentos que se fizerem necessários.
                """.formatted(p.getNumero(), p.getPacienteNome(), motivo);
            Paragraph corpo = new Paragraph(texto, fCorpo);
            corpo.setAlignment(Element.ALIGN_JUSTIFIED);
            corpo.setSpacingAfter(28);
            doc.add(corpo);

            // Fecho / assinatura (configuravel: sgpur.email.assinatura)
            Paragraph fecho = new Paragraph("Atenciosamente,", fCorpo);
            fecho.setSpacingAfter(36);
            doc.add(fecho);

            Paragraph assinatura = new Paragraph("____________________________________\n"
                + emailProperties.getAssinatura(), fCorpo);
            assinatura.setAlignment(Element.ALIGN_CENTER);
            doc.add(assinatura);

            doc.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Falha ao gerar o oficio de indeferimento", e);
        }
    }

    private String cidade() {
        String cidade = emailProperties.getOficioCidade();
        return (cidade == null || cidade.isBlank()) ? "Porto Alegre" : cidade.trim();
    }

    private void adicionarBrasao(Document doc) {
        try {
            byte[] logoBytes = getClass().getClassLoader()
                .getResourceAsStream("static/brasao.png").readAllBytes();
            Image brasao = Image.getInstance(logoBytes);
            brasao.scaleToFit(80, 80);
            brasao.setAlignment(Element.ALIGN_CENTER);
            brasao.setSpacingAfter(8);
            doc.add(brasao);
        } catch (Exception e) {
            log.warn("Logo nao encontrado em static/brasao.png, oficio sem imagem");
        }
    }
}
