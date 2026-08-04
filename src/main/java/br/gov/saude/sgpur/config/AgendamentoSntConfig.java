package br.gov.saude.sgpur.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Liga o agendador do Spring ({@code @Scheduled}) quando o lembrete automatico
 * de comprovante SNT esta habilitado
 * ({@code app.snt.lembrete.varredura.habilitado=true} — ligado em producao,
 * desligado em dev/teste).
 *
 * <p><b>Por que uma classe separada de {@link AgendamentoConfig}.</b> As duas
 * varreduras (decisao automatica e lembrete de SNT) tem interruptores
 * INDEPENDENTES, e {@code @ConditionalOnProperty} avalia uma propriedade so.
 * Sem esta classe, desligar a varredura de decisao automatica desligaria junto,
 * silenciosamente, o agendador do lembrete de SNT — o componente seria
 * registrado e o {@code @Scheduled} dele nunca dispararia. Ter
 * {@code @EnableScheduling} em duas {@code @Configuration} e seguro: o Spring
 * registra um unico {@code ScheduledAnnotationBeanPostProcessor} (bean de nome
 * fixo), entao a segunda anotacao nao duplica nada.</p>
 *
 * <p>Quando as DUAS propriedades estao desligadas (dev/teste), nenhum agendador
 * sobe e nada roda sozinho no meio da suite — a garantia documentada em
 * {@link AgendamentoConfig} continua valendo.</p>
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "app.snt.lembrete.varredura", name = "habilitado", havingValue = "true")
public class AgendamentoSntConfig {
}
