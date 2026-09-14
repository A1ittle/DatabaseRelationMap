/**
 * HTTP controllers and DTOs. Import/publish live at {@code /api/imports}.
 * Lineage query APIs live at {@code /api/lineage}. Local/dev auth is
 * {@code lineage.security.mode=open} (no OIDC). Optional {@code X-Embed-Groups}
 * selects scope/object grants; omit the header to authorize the full snapshot.
 */
package com.lineage.api.interfaces;
