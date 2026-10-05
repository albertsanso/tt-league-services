package org.cttelsamicsterrassa.data.pipeline.runtime.notification;

import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertEvaluator;
import org.cttelsamicsterrassa.data.pipeline.core.alert.AlertRunObserver;
import org.cttelsamicsterrassa.data.pipeline.core.alert.port.AlertRepository;
import org.cttelsamicsterrassa.data.pipeline.core.execution.port.RunClock;
import org.cttelsamicsterrassa.data.pipeline.core.run.port.PipelineRunRepository;
import org.cttelsamicsterrassa.data.pipeline.core.tracker.port.MatchDayRepository;
import org.cttelsamicsterrassa.data.pipeline.runtime.config.PipelineOrchestratorProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the notifications. The evaluator and the mail notifier exist only inside the dispatcher: they are not beans,
 * and in particular there is no {@code JavaMailSender} bean (see {@link MailNotifier}). With a blank mail host the
 * dispatcher is the disabled one and the schedule never starts.
 */
@Configuration(proxyBeanMethods = false)
public class NotificationConfiguration {

    @Bean
    AlertDispatcher alertDispatcher(
            PipelineOrchestratorProperties.Notifications notifications,
            AlertRepository alerts,
            MatchDayRepository matchDays,
            PipelineRunRepository runs,
            RunClock clock) {
        if (!notifications.enabled()) {
            return AlertDispatcher.disabled();
        }
        MailNotifier notifier = new MailNotifier(notifications.mail());
        AlertEvaluator evaluator =
                new AlertEvaluator(alerts, matchDays, runs, notifier, clock, notifications.alertSettings());
        return new AlertDispatcher(evaluator, notifier);
    }

    @Bean
    AlertRunObserver alertRunObserver(AlertDispatcher dispatcher) {
        return new AlertRunObserver(dispatcher);
    }

    @Bean
    AlertEvaluationSchedule alertEvaluationSchedule(
            PipelineOrchestratorProperties.Notifications notifications, AlertDispatcher dispatcher) {
        return new AlertEvaluationSchedule(notifications.enabled(), notifications.evaluateInterval(), dispatcher);
    }
}
