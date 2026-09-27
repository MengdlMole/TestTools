package io.github.mengdlmole.testtools.mock.config;

/**
 * Observable state of the atomic Mock catalog loader.
 *
 * @param version current successful catalog version
 * @param loadedAt time at which the current catalog was loaded
 * @param mockCount number of definitions in the current catalog
 * @param lastCheckedAt last automatic or manual reload attempt
 * @param lastError last reload error while retaining the previous catalog, or {@code null}
 */
public record MockCatalogStatus(
    long version, String loadedAt, int mockCount, String lastCheckedAt, String lastError) {}
