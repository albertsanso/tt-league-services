/**
 * Framework-free core of the pipeline orchestrator: run state machine, match-day tracker rules,
 * polling policy and the ports implemented by the runtime adapters. Later features fill this
 * package; it must not depend on Spring, JPA, HTTP clients or any {@code tt-data-league-*} module.
 */
package org.cttelsamicsterrassa.data.pipeline.core;
