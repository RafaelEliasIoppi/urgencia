package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.StatusProcesso;
import br.gov.saude.sgpur.domain.TipoAnexo;
import br.gov.saude.sgpur.repository.ProcessoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;

/**
 * Encapsula a geracao automatica dos documentos PDF (Oficio de Indeferimento
 * e Relatorio Final) apos uma decisao final — seja ela manual ou automatica.
 *
 * Centraliza a logica que antes estava duplicada em ProcessoController e
 * permite que AvaliadorController tambem a utilize apos a decisao automatica
 * disparada pelo voto do portal.
 */
@Service
public class DecisaoFinalService {

    private static final Logger log = LoggerFactory.getLogger(DecisaoFinalService.class);

    private final ProcessoService processoService;
    private final RelatorioService relatorioService;
    private final AnexoStorageService anexoStorage;
    private final ProcessoRepository processoRepository;

    public DecisaoFinalService(ProcessoService processoService,
            RelatorioService relatorioService,
            AnexoStorageService anexoStorage,
            ProcessoRepository processoRepository) {
        this.processoService = processoService;
        this.relatorioService = relatorioService;
        this.anexoStorage = anexoStorage;
        this.processoRepository = processoRepository;
    }

    /**
     * Gera e anexa os PDFs correspondentes a decisao ja gravada no processo:
     * o Relatorio Final, para qualquer status final.
     *
     * <p>
     * <b>O Oficio de Indeferimento NAO e mais gerado/anexado aqui
     * (2026-08-04, decisao do usuario: "oficio sera sempre anexado").</b> O
     * oficio precisa ser editavel caso a caso, entao o sistema so oferece um
     * RASCUNHO para download ({@code OficioService.gerarRascunhoRtf}, aberto no
     * Word) e o documento de registro e sempre o arquivo que o operador anexa
     * depois. Aqui so se atribui a NUMERACAO propria do oficio, para o numero
     * ja aparecer na tela e no rascunho. A {@code dataEmissaoOficio} tambem
     * deixou de ser gravada neste ponto: ela e o momento do anexo do oficio
     * (ver {@code ProcessoService.registrarDataEmissaoOficio}), e gravar aqui
     * dataria um documento que ainda nao existe.
     *
     * <p>
     * Erros de geracao de PDF sao logados e lancados para que o chamador
     * possa exibir avisos sem desfazer a decisao (ja persistida).
     *
     * @param p Processo ja com status final gravado no banco.
     * @throws IllegalStateException se a geracao de algum PDF falhar.
     */
    public void gerarDocumentos(Processo p) {
        if (p.getStatus() == StatusProcesso.INDEFERIDO) {
            atribuirNumeroOficioSeNecessario(p);
        }

        if (p.getStatus().isFinalizado()) {
            try {
                // Mesma ordem do oficio: gera e salva antes de remover o antigo.
                byte[] pdf = relatorioService.gerar(p);
                String nome = "relatorio-processo-" + p.getNumero().replace("/", "-") + ".pdf";
                var novoAnexo = anexoStorage.salvarBytes(p, TipoAnexo.RELATORIO_FINAL,
                        "Relatorio final gerado na decisao", nome, "application/pdf", pdf);
                anexoStorage.removerAntigosDoTipo(p, TipoAnexo.RELATORIO_FINAL, novoAnexo.getId());
            } catch (IOException e) {
                log.error("Falha ao gerar relatorio final para processo {}", p.getNumero(), e);
                throw new IllegalStateException(
                        "Decisao salva, mas falhou ao gerar o relatorio final: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Garante o numero proprio do oficio (NNNN/AAAA), sequencial ANUAL,
     * independente do numero do processo. Uma vez atribuido, nunca muda (o
     * documento continua sendo o mesmo oficio no protocolo do setor).
     *
     * <p>
     * Atribuido na decisao ({@link #gerarDocumentos}) para o numero ja
     * aparecer na tela e no rascunho editavel que o operador baixa - o oficio
     * em si nao e mais gerado pelo sistema (ver {@link #gerarDocumentos}).
     *
     * <p>
     * <b>Concorrencia:</b> o proximo numero e calculado lendo os numeros ja
     * usados e somando 1, sem lock nem sequence — dois indeferimentos
     * simultaneos no mesmo instante poderiam receber o mesmo numero. E o mesmo
     * compromisso ja aceito em {@code ProcessoService.proximoNumero} e e
     * aceitavel aqui pelo volume real (poucos indeferimentos por dia, sempre
     * disparados por um operador humano ou pela varredura sequencial). Nao ha
     * constraint UNIQUE na coluna de proposito: uma colisao remota nao deve
     * impedir o registro da decisao ja tomada.
     * </p>
     */
    private void atribuirNumeroOficioSeNecessario(Processo p) {
        if (p.getNumeroOficio() != null && !p.getNumeroOficio().isBlank()) {
            return;
        }
        int ano = p.getDataEmissaoOficio() != null
                ? p.getDataEmissaoOficio().getYear()
                : LocalDate.now().getYear();
        p.setNumeroOficio(proximoNumeroOficio(ano));
        processoService.salvar(p);
    }

    /** Proximo sequencial de oficio do ano, no formato NNNN/AAAA (comeca em 1). */
    String proximoNumeroOficio(int ano) {
        int maior = processoRepository.findNumerosOficioDoAno(String.valueOf(ano)).stream()
                .map(n -> n.split("/")[0])
                .mapToInt(seq -> {
                    try {
                        return Integer.parseInt(seq.trim());
                    } catch (NumberFormatException e) {
                        return 0; // numero fora do padrao (dado historico): ignora
                    }
                })
                .max().orElse(0);
        return String.format("%04d/%d", maior + 1, ano);
    }
}
