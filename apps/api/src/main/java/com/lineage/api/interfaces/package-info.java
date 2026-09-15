/**
 * HTTP controllers and DTOs. Import/publish live at {@code /api/imports}.
 * Lineage query APIs live at {@code /api/lineage}. Local/dev auth is
 * {@code lineage.security.mode=open} (no OIDC). Optional {@code X-Embed-Groups}
 * selects scope/object grants; omit the header to authorize the full snapshot.
 * When the header is present, import/publish require {@code ingest} on the scope.
 */
package com.lineage.api.interfaces;
