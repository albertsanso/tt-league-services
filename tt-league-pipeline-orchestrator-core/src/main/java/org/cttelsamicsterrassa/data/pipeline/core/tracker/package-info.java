/**
 * Match-day tracker: {@link org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayTracker} reads the platform
 * round progress and calendar through {@link org.cttelsamicsterrassa.data.pipeline.core.tracker.port.PlatformMatchGateway}
 * and keeps operational match-day and match state; {@link
 * org.cttelsamicsterrassa.data.pipeline.core.tracker.TrackerRules} holds every rule and {@link
 * org.cttelsamicsterrassa.data.pipeline.core.tracker.MatchDayActions} the manual operator actions. JDK types only.
 */
package org.cttelsamicsterrassa.data.pipeline.core.tracker;
