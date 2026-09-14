/**
 * Application services: query context, import, and snapshot publish.
 * P2: {@link com.lineage.api.application.ImportService} validates ImportBatch,
 * writes ingest_run / unpublished snapshot rows, and CAS-publishes.
 * {@link com.lineage.api.application.QueryService} binds a published snapshot
 * plus policy revision and runs P1 graph algorithms behind embed grants.
 */
package com.lineage.api.application;
