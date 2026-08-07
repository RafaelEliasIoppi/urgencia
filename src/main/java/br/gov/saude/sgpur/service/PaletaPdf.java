package br.gov.saude.sgpur.service;

import java.awt.Color;

/**
 * Paleta institucional compartilhada pelos tres documentos PDF oficiais do
 * sistema (Relatorio Final, Relatorio Anual, Relatorio do Avaliador) - ver
 * Decisao 6 do relatorio V2
 * (docs/RELATORIO-REFORMULACAO-RELATORIO-FINAL-PDF-V2-2026-08.md §7.6).
 *
 * <p>Antes desta classe, cada um dos tres documentos declarava as MESMAS
 * cores em constantes proprias, repetidas de arquivo em arquivo (o cinza,
 * por exemplo, chegou a existir em quatro lugares diferentes:
 * {@code PdfRelatorioBuilder}, {@code RelatorioAnualService},
 * {@code RelatorioAvaliadorService} e {@code SolicitacaoAvaliadorService}).
 * O PR #45 trocou so o AZUL do Relatorio Final para o institucional
 * (--rs-blue), deixando os outros dois documentos com o azul do Bootstrap
 * ({@code $primary}, {@code #0D6EFD}) - o sistema passou a ter DOIS azuis
 * institucionais diferentes nos seus proprios PDFs oficiais (achado B6 do
 * relatorio V2). Esta classe fecha essa divergencia estruturalmente: so
 * existe um lugar para declarar essas cores, no mesmo espirito de
 * {@link PdfCabecalhoStamper#NOME_INSTITUICAO}/{@link PdfCabecalhoStamper#NOME_SISTEMA}
 * para texto.</p>
 *
 * <p>Valores extraidos de {@code app.css} (variaveis {@code --rs-*}). O
 * ganho de contraste (Anexo B do relatorio V2, WCAG 2.x) e o argumento mais
 * forte: texto branco sobre {@link #AZUL} sobe de 4,50:1 (limite exato da
 * WCAG AA, valor do Bootstrap) para 8,39:1; {@link #VERDE_ESCURO} sobe de
 * 4,53:1 para 6,53:1; {@link #VERMELHO} de 4,53:1 para 9,29:1; {@link #CINZA}
 * de 4,69:1 para 7,58:1 - num documento feito para ser impresso e
 * fotocopiado, uma margem de 0,03 sobre o minimo nao e margem de verdade.</p>
 */
final class PaletaPdf {

    /** {@code --rs-blue}. */
    static final Color AZUL = new Color(0x1A, 0x4D, 0x8F);
    /** {@code --rs-gray-600}. */
    static final Color CINZA = new Color(0x47, 0x55, 0x69);
    /** {@code --rs-gray-200}. */
    static final Color CINZA_BORDA = new Color(0xE2, 0xE8, 0xF0);
    /** {@code --rs-green-dark}. */
    static final Color VERDE_ESCURO = new Color(0x1F, 0x6B, 0x36);
    /** {@code --rs-red-dark}. */
    static final Color VERMELHO = new Color(0x8B, 0x1A, 0x1A);

    private PaletaPdf() {
    }
}
