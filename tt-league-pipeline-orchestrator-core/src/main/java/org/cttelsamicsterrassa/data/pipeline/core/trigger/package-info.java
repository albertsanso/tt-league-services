/**
 * Manual and scheduled triggers: {@link
 * org.cttelsamicsterrassa.data.pipeline.core.trigger.TriggerRun} is the one path that creates new runs, including the
 * per-source pending trigger used by the queue conflict mode. {@link
 * org.cttelsamicsterrassa.data.pipeline.core.trigger.ScheduledRunTick} is its only scheduled caller. JDK types only.
 */
package org.cttelsamicsterrassa.data.pipeline.core.trigger;
