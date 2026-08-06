package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Anexo;
import br.gov.saude.sgpur.domain.Parecer;
import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.ResultadoParecer;
import br.gov.saude.sgpur.domain.TipoAnexo;
import br.gov.saude.sgpur.service.dto.EtapaFluxo;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decide o conteudo do Relatorio Final do Processo de Urgencia Renal -
 * documento oficial para arquivamento, impressao e auditoria. A construcao
 * visual/binaria do PDF (tabelas, fontes, capa, merge de paginas) fica em
 * {@link PdfRelatorioBuilder}.
 *
 * O relatorio final eh composto por:
 *   1. Capa + sumario executivo (dados do processo, pareceres, decisao,
 *      andamento e relacao de anexos);
 *   2. Copia integral de todos os documentos anexados ao processo (PDFs),
 *      inseridos como paginas apos o sumario;
 *   3. Pagina informativa para anexos nao-PDF.
 * Todas as paginas recebem cabecalho padrao e numeracao.
 */
@Service
public class RelatorioService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color AZUL = PdfRelatorioBuilder.AZUL;
    private static final Color CINZA = PdfRelatorioBuilder.CINZA;

    private final FluxoProcessoService fluxoService;
    private final ProcessoService processoService;
    private final PdfRelatorioBuilder pdfBuilder;

    public RelatorioService(FluxoProcessoService fluxoService,
                            ProcessoService processoService,
                            AnexoStorageService anexoStorage) {
        this.fluxoService = fluxoService;
        this.processoService = processoService;
        this.pdfBuilder = new PdfRelatorioBuilder(anexoStorage);
    }

    /**
     * Gera o Relatorio Final completo: sumario + copia de todos os anexos +
     * cabecalho e numeracao em todas as paginas.
     */
    @Transactional(readOnly = true)
    public byte[] gerar(Processo p) {
        try {
            // 1. Gera o sumario executivo (capa + dados + pareceres + decisao + anexos)
            byte[] summary = gerarSummary(p);

            // 2. Coleta os PDFs anexados (exceto RELATORIO_FINAL — evitar ciclo/duplicacao)
            List<Anexo> pdfs = p.getAnexos().stream()
                .filter(a -> a.getTipo() != TipoAnexo.RELATORIO_FINAL)
                .filter(a -> a.getContentType() != null
                    && a.getContentType().toLowerCase().contains("pdf"))
                .sorted(Comparator.comparing(Anexo::getDataUpload))
                .toList();

            // 3. Coleta anexos nao-PDF (para pagina informativa)
            List<Anexo> naoPdf = p.getAnexos().stream()
                .filter(a -> a.getTipo() != TipoAnexo.RELATORIO_FINAL)
                .filter(a -> a.getContentType() == null
                    || !a.getContentType().toLowerCase().contains("pdf"))
                .sorted(Comparator.comparing(Anexo::getDataUpload))
                .toList();

            // 4. Monta o PDF final com todas as paginas
            byte[] merged = pdfBuilder.mergeComAnexos(summary, pdfs, naoPdf);

            // 5. Adiciona cabecalho e numeracao em todas as paginas (mesmo
            // padrao institucional do Relatorio Anual, via PdfCabecalhoStamper).
            String iniciais = Iniciais.de(p.getPacienteNome());
            return PdfCabecalhoStamper.estampar(merged,
                PdfCabecalhoStamper.NOME_INSTITUICAO + " - URGENCIA RENAL",
                "Processo CET-RS " + p.getNumero() + " - Paciente " + iniciais);

        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o relatorio PDF completo", e);
        }
    }

    // -----------------------------------------------------------------------
    // Sumario executivo
    // -----------------------------------------------------------------------

    private byte[] gerarSummary(Processo p) throws DocumentException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = pdfBuilder.abrirDocumentoA4(out);

        Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15, AZUL);
        Font fSub = FontFactory.getFont(FontFactory.HELVETICA, 9, CINZA);
        Font fSecao = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.WHITE);

        // Pagina de capa
        pdfBuilder.adicionarCapa(doc, p, "Relatorio do Processo " + p.getNumero(), false);
        doc.newPage();

        Paragraph titulo = new Paragraph("RELATORIO FINAL - PROCESSO DE URGENCIA RENAL", fTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);

        Paragraph sub = new Paragraph(
            p.identificacao() + "  |  Situacao: " + p.getStatus().getDescricao()
                + "  |  Emitido em " + java.time.LocalDateTime.now().format(DATA_HORA), fSub);
        sub.setAlignment(Element.ALIGN_CENTER);
        sub.setSpacingAfter(14);
        doc.add(sub);

        pdfBuilder.secao(doc, fSecao, "1. Dados da solicitacao");
        PdfPTable t1 = pdfBuilder.tabelaDados();
        pdfBuilder.linha(t1, "Numero do processo", p.getNumero());
        pdfBuilder.linha(t1, "Paciente (receptor)", p.getPacienteNome());
        pdfBuilder.linha(t1, "RGCT / SNT", PdfRelatorioBuilder.nvl(p.getPacienteRgct()));
        pdfBuilder.linha(t1, "Equipe solicitante", p.getSolicitanteEquipe());
        pdfBuilder.linha(t1, "E-mail do solicitante", PdfRelatorioBuilder.nvl(p.getSolicitanteEmail()));
        pdfBuilder.linha(t1, "Data de solicitacao da urgencia renal",
            p.getDataSituacaoEspecial() != null ? p.getDataSituacaoEspecial().format(DATA) : "-");
        pdfBuilder.linha(t1, "Data de cadastro",
            p.getDataCadastro() != null ? p.getDataCadastro().format(DATA_HORA) : "-");
        pdfBuilder.linha(t1, "Observacoes", PdfRelatorioBuilder.nvl(p.getObservacoes()));
        doc.add(t1);

        pdfBuilder.secao(doc, fSecao, "2. Pareceres dos medicos (Urgencia Renal)");
        PdfPTable t2 = new PdfPTable(new float[]{3, 2, 2});
        t2.setWidthPercentage(100);
        t2.setSpacingBefore(4);
        pdfBuilder.cabecalho(t2, "Medico", "Parecer", "Data da resposta");
        for (Parecer par : p.getPareceres()) {
            pdfBuilder.celula(t2, par.getMembro().getRotulo(), Element.ALIGN_LEFT, false);
            // Impedido (conflito de interesse - avaliador e o proprio
            // solicitante do processo) e um estado diferente de "sem voto
            // ainda" e nao pode compartilhar o mesmo rotulo: perder essa
            // distincao no documento de arquivo escondia justamente o fato
            // que demonstra que o conflito foi identificado e tratado.
            String textoParecer;
            if (par.isImpedido()) {
                textoParecer = "Impedido - conflito de interesse (solicitante do processo)";
            } else if (par.getResultado() != null) {
                textoParecer = par.getResultado().getDescricao();
            } else {
                textoParecer = p.getStatus().isFinalizado() ? "Dispensado pela maioria" : "Pendente";
            }
            pdfBuilder.celula(t2, textoParecer, Element.ALIGN_LEFT, false);
            pdfBuilder.celula(t2, par.getDataResposta() != null ? par.getDataResposta().format(DATA) : "-",
                Element.ALIGN_LEFT, false);
            if (par.getResultado() != null && par.getDataHoraVoto() != null) {
                // Trilha de auditoria do voto: desde 2026-07-27 o registro
                // autenticado (usuario + dataHoraVoto + origem, com IP no log
                // de auditoria) e o que SUBSTITUIU o antigo anexo
                // comprobatorio do parecer - o documento que arquiva o
                // processo precisa carregar essa prova, nao so a data.
                String origemTexto = par.getOrigem() != null ? par.getOrigem().getDescricao() : "-";
                PdfPCell cAud = new PdfPCell(new Phrase(
                    "Registrado por: " + PdfRelatorioBuilder.nvl(par.getVotadoPor())
                        + " em " + par.getDataHoraVoto().format(DATA_HORA)
                        + " (" + origemTexto + ")",
                    FontFactory.getFont(FontFactory.HELVETICA, 8, CINZA)));
                cAud.setColspan(3);
                cAud.setPadding(4);
                cAud.setBorderColor(PdfRelatorioBuilder.CINZA_BORDA);
                t2.addCell(cAud);
            }
            if (par.getJustificativa() != null && !par.getJustificativa().isBlank()) {
                // 9pt preto (era 8pt cinza-claro): a justificativa clinica e
                // o conteudo mais relevante da tabela para auditoria futura,
                // nao devia ser a fonte mais apagada e menor do documento.
                // Mantido o italico so para distinguir visualmente de dado
                // estruturado (medico/parecer/data), sem depender de cor.
                PdfPCell cj = new PdfPCell(new Phrase(
                    "Justificativa: " + par.getJustificativa(),
                    FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, Color.BLACK)));
                cj.setColspan(3);
                cj.setPadding(4);
                cj.setBorderColor(PdfRelatorioBuilder.CINZA_BORDA);
                t2.addCell(cj);
            }
        }
        doc.add(t2);
        // A frase da regra e fixa em "2 de 3" na maioria dos casos, mas isso
        // contradiz a decisao registrada quando o processo foi deferido pela
        // excecao regimental do Coordenador da CET-RS (voto favoravel dele
        // basta, ver ProcessoValidator.favoraveisNecessariosParaDeferir):
        // sem este tratamento, o documento oficial de arquivo chegava a
        // registrar "Favoraveis: 1 (regra: 2 de 3 defere o processo)" ao
        // lado de "DEFERIDO", como se a propria regra citada tivesse sido
        // violada. Fora desse caso especifico o texto permanece exatamente
        // como sempre foi (inclusive para o deferimento por maioria normal
        // e para o indeferimento).
        Paragraph fav;
        if (processoService.deferidoPeloCoordenador(p)) {
            String nomeCoordenador = p.getPareceres().stream()
                .filter(par -> par.getResultado() == ResultadoParecer.FAVORAVEL
                    && par.getMembro() != null && par.getMembro().isCoordenador())
                .map(par -> par.getMembro().getNome())
                .findFirst()
                .orElse("Coordenador da CET-RS");
            fav = new Paragraph(
                "Deferido pelo voto do Coordenador da CET-RS (" + nomeCoordenador
                    + "), que defere isoladamente, conforme excecao regimental que dispensa "
                    + "a maioria de " + ProcessoService.FAVORAVEIS_PARA_DEFERIR + " de "
                    + ProcessoService.AVALIADORES_POR_PROCESSO + ". Favoraveis: "
                    + processoService.contarFavoraveis(p) + ".",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, CINZA));
        } else {
            fav = new Paragraph(
                "Favoraveis: " + processoService.contarFavoraveis(p) + " (regra: "
                    + ProcessoService.FAVORAVEIS_PARA_DEFERIR + " de "
                    + ProcessoService.AVALIADORES_POR_PROCESSO + " defere o processo).",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, CINZA));
        }
        fav.setSpacingBefore(4);
        doc.add(fav);

        pdfBuilder.secao(doc, fSecao, "3. Decisao final");
        PdfPTable t3 = pdfBuilder.tabelaDados();
        if (p.getStatus().isFinalizado()) {
            // Resultado com destaque equivalente ao da capa (negrito, fonte
            // maior, CAIXA ALTA + cor semantica) - e o dado mais importante
            // do documento e antes era so mais uma linha rotulo:valor em 9pt
            // igual as demais. A cor e bonus: "DEFERIDO"/"INDEFERIDO" em
            // caixa alta e negrito ja carrega a informacao mesmo impresso em
            // P&B.
            Color corResultado = switch (p.getStatus()) {
                case DEFERIDO -> PdfRelatorioBuilder.VERDE_ESCURO;
                case INDEFERIDO -> PdfRelatorioBuilder.VERMELHO;
                default -> CINZA;
            };
            pdfBuilder.linhaDestaque(t3, "Resultado", p.getStatus().getDescricao(), corResultado);
        } else {
            // Processo ainda em tramitacao (nao decidido): a capa do mesmo
            // documento (PdfRelatorioBuilder.adicionarCapa) ja trata esse
            // caso corretamente com "Em andamento" - antes esta secao
            // imprimia p.getStatus().getDescricao() incondicionalmente,
            // entao um processo ENVIADO chegava a anunciar "Resultado:
            // ENVIADO" em destaque de 13pt, como se ENVIADO fosse a decisao.
            // Sem linhaDestaque (sem CAIXA ALTA/13pt): a informacao nao e
            // uma decisao e nao deve ter o mesmo peso visual de uma.
            pdfBuilder.linha(t3, "Resultado", "Em andamento (processo ainda nao decidido)");
        }
        pdfBuilder.linha(t3, "Data da decisao",
            p.getDataDecisao() != null ? p.getDataDecisao().format(DATA_HORA) : "-");
        pdfBuilder.linha(t3, "Motivo do indeferimento", PdfRelatorioBuilder.nvl(p.getMotivoIndeferimento()));
        pdfBuilder.linha(t3, "Numero do oficio", PdfRelatorioBuilder.nvl(p.getNumeroOficio()));
        pdfBuilder.linha(t3, "Data de emissao do oficio",
            p.getDataEmissaoOficio() != null ? p.getDataEmissaoOficio().format(DATA) : "-");
        pdfBuilder.linha(t3, "Data de envio do oficio",
            p.getDataEnvioOficio() != null ? p.getDataEnvioOficio().format(DATA) : "-");
        pdfBuilder.linha(t3, "Data de envio ao SNT",
            p.getDataEnvioSnt() != null ? p.getDataEnvioSnt().format(DATA) : "-");
        pdfBuilder.linha(t3, "E-mail enviado ao solicitante", p.isEmailEnviadoSolicitante() ? "Sim" : "Nao");
        doc.add(t3);

        pdfBuilder.secao(doc, fSecao, "4. Andamento do processo");
        PdfPTable t4 = new PdfPTable(new float[]{1, 4, 5});
        t4.setWidthPercentage(100);
        t4.setSpacingBefore(4);
        pdfBuilder.cabecalho(t4, "Status", "Etapa", "Detalhe");
        for (EtapaFluxo e : fluxoService.montarEtapas(p)) {
            String marca = switch (e.estado()) {
                case CONCLUIDA -> "[X]";
                case ATUAL -> "[>]";
                case BLOQUEADA -> "[ ]";
            };
            pdfBuilder.celula(t4, marca, Element.ALIGN_CENTER, false);
            pdfBuilder.celula(t4, e.titulo(), Element.ALIGN_LEFT, false);
            pdfBuilder.celula(t4, e.detalhe(), Element.ALIGN_LEFT, false);
        }
        doc.add(t4);

        pdfBuilder.secao(doc, fSecao, "5. Relacao de anexos");
        if (p.getAnexos().isEmpty()) {
            doc.add(new Paragraph("Nenhum anexo registrado.",
                FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9, CINZA)));
        } else {
            // Tipo ganhou mais espaco (33% -> 47%) e Data encolheu (22% ->
            // 16%, sobra de sobra para "dd/mm/aaaa"): TipoAnexo.getDescricao()
            // costuma ser bem mais longo que o nome do arquivo ou a data,
            // e quebrava em 2 linhas de forma irregular com as proporcoes
            // antigas (3, 4, 2).
            PdfPTable t5 = new PdfPTable(new float[]{4.5f, 3.5f, 1.5f});
            t5.setWidthPercentage(100);
            t5.setSpacingBefore(4);
            pdfBuilder.cabecalho(t5, "Tipo", "Arquivo", "Data");
            for (Anexo a : p.getAnexos()) {
                pdfBuilder.celula(t5, a.getTipo().getDescricao(), Element.ALIGN_LEFT, false);
                pdfBuilder.celula(t5, a.getNomeArquivo(), Element.ALIGN_LEFT, false);
                pdfBuilder.celula(t5, a.getDataUpload() != null ? a.getDataUpload().format(DATA) : "-",
                    Element.ALIGN_LEFT, false);
            }
            doc.add(t5);
        }

        Paragraph rodape = new Paragraph(
            "Documento gerado automaticamente pelo SAUR - Sistema de Gestao de Processos de Urgencia Renal.",
            FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8, CINZA));
        rodape.setAlignment(Element.ALIGN_CENTER);
        rodape.setSpacingBefore(20);
        doc.add(rodape);

        doc.close();
        return out.toByteArray();
    }
}
