/**
 * Pure Java graph algorithms. Isolated from Spring, JDBC, and HTTP.
 *
 * <p>P1 currently ships the counterexample acceptance suite and a thin
 * {@link com.lineage.api.domain.graph.LineageGraphAlgorithms} façade. The
 * stub behind {@link com.lineage.api.domain.graph.LineageGraphAlgorithmsFactory}
 * is expected to fail those tests (red) until BFS/classify/path is implemented.
 */
package com.lineage.api.domain.graph;
