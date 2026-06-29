package br.gov.saude.sgpur.service;

import br.gov.saude.sgpur.domain.Processo;
import br.gov.saude.sgpur.domain.StatusProcesso;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gera textos de e-mail prontos (copiar/colar) para cada etapa do processo,
 * pre-preenchidos com os dados do processo. No e-mail aos MEDICOS AVALIADORES o
 * nome do paciente e OCULTADO (so iniciais), para preservar a IMPARCIALIDADE do
 * julgamento - os avaliadores decidem sem saber quem e o paciente (convencao da
 * equipe de Urgencia Renal). Os e-mails dirigidos a equipe SOLICITANTE (pedido de
 * informacao complementar, resposta de Deferido/Indeferido) levam o NOME COMPLETO.
 */
@Service
public class EmailTemplateService {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<EmailTemplate> gerar(Processo p) {
        List<EmailTemplate> lista = new ArrayList<>();
        lista.add(emailMedicos(p));
        if (p.getStatus() == StatusProcesso.DEFERIDO) {
            lista.add(emailDeferido(p));
        } else if (p.getStatus() == StatusProcesso.INDEFERIDO) {
            lista.add(emailIndeferido(p));
        } else if (p.getStatus() == StatusProcesso.SOLICITA_INFORMACAO) {
            lista.add(emailSolicitaInfo(p));
        }
        return lista;
    }

    /**
     * E-mail de solicitacao de parecer aos medicos avaliadores - SEM o nome do
     * paciente (so iniciais), para preservar a imparcialidade do julgamento.
     */
    private EmailTemplate emailMedicos(Processo p) {
        String medicos = p.getPareceres().stream()
            .map(par -> "- " + par.getMembro().getRotulo())
            .collect(Collectors.joining("\n"));
        String data = p.getDataSituacaoEspecial() != null
            ? p.getDataSituacaoEspecial().format(DATA) : "(data)";
        String iniciais = Iniciais.de(p.getPacienteNome());
        String idProcesso = p.getNumero() + " - Paciente " + iniciais;

        String corpo = """
            Prezados(as) avaliadores(as),

            Encaminhamos para parecer da Urgencia Renal o processo abaixo. Solicitamos
            a analise clinica e o retorno do parecer (Favoravel / Nao favoravel /
            Solicita informacao).

            Processo: %s
            Data da situacao especial: %s

            (O nome do paciente foi omitido para preservar a imparcialidade do
            julgamento; identificado apenas pelas iniciais. Em caso de necessidade
            de identificacao, solicitar a Secretaria.)

            Avaliadores designados:
            %s

            Atenciosamente,
            Equipe de Urgencia Renal - Secretaria de Saude
            """.formatted(idProcesso, data, medicos);

        return new EmailTemplate("medicos", "Envio aos medicos (parecer)", "send",
            "Urgencia Renal - Solicitacao de parecer - Processo " + idProcesso, corpo);
    }

    /**
     * Pedido de informacao complementar ao solicitante: quando um medico
     * avaliador pede mais informacoes, repassa-se o pedido a EQUIPE SOLICITANTE
     * (a que abriu o processo) para que complemente o processo. Texto pronto
     * para copiar/colar. Como o destinatario e o SOLICITANTE (nao os medicos
     * avaliadores), o e-mail PODE e DEVE conter o NOME COMPLETO do paciente.
     */
    private EmailTemplate emailSolicitaInfo(Processo p) {
        String idProcesso = p.getNumero() + " - Paciente " + p.getPacienteNome();

        String corpo = """
            Prezados(as),

            Informamos que, durante a analise do processo de Urgencia Renal %s,
            referente ao paciente %s, um(a) dos(as) avaliadores(as) da Urgencia
            Renal solicitou informacoes complementares para concluir o parecer.

            Equipe solicitante: %s

            Solicitamos, por gentileza, o envio das informacoes e/ou documentos
            adicionais necessarios a continuidade da analise, respondendo a este
            e-mail. Assim que recebidas, a analise sera retomada e o processo
            seguira para a decisao.

            Atenciosamente,
            Equipe de Urgencia Renal - Secretaria de Saude
            """.formatted(p.getNumero(), p.getPacienteNome(), p.getSolicitanteEquipe());

        return new EmailTemplate("solicita-info",
            "Pedido de informacao complementar ao solicitante", "question-circle",
            "Urgencia Renal - Processo " + idProcesso + " - Solicitacao de informacoes complementares",
            corpo);
    }

    private EmailTemplate emailDeferido(Processo p) {
        String corpo = """
            Prezados(as),

            Informamos que o processo de Urgencia Renal %s, referente ao paciente
            %s, foi DEFERIDO pela equipe de Urgencia Renal.

            Equipe solicitante: %s

            Segue EM ANEXO o comprovante de que a urgencia renal foi inserida no
            Sistema Nacional de Transplantes (SNT).

            Permanecemos a disposicao para esclarecimentos.

            Atenciosamente,
            Equipe de Urgencia Renal - Secretaria de Saude
            """.formatted(p.getNumero(), p.getPacienteNome(), p.getSolicitanteEquipe());

        return new EmailTemplate("deferido", "Resposta ao solicitante (Deferido)", "check-circle",
            "Urgencia Renal - Processo " + p.getNumero() + " - DEFERIDO", corpo);
    }

    private EmailTemplate emailIndeferido(Processo p) {
        String motivo = (p.getMotivoIndeferimento() == null || p.getMotivoIndeferimento().isBlank())
            ? "(informar o motivo do indeferimento)" : p.getMotivoIndeferimento();

        String corpo = """
            Prezados(as),

            Informamos que o processo de Urgencia Renal %s, referente ao paciente
            %s, foi INDEFERIDO pela equipe de Urgencia Renal.

            Equipe solicitante: %s
            Motivo: %s

            O oficio de indeferimento segue anexo a este e-mail.

            Atenciosamente,
            Equipe de Urgencia Renal - Secretaria de Saude
            """.formatted(p.getNumero(), p.getPacienteNome(), p.getSolicitanteEquipe(), motivo);

        return new EmailTemplate("indeferido", "Resposta ao solicitante (Indeferido)", "x-circle",
            "Urgencia Renal - Processo " + p.getNumero() + " - INDEFERIDO", corpo);
    }
}
