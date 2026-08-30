package org.cttelsamicsterrassa.data.core.domain.shared.port;

import org.cttelsamicsterrassa.data.core.domain.shared.model.ImportSource;

import java.util.List;

/**
 * Query port: returns the sources that this deployment supports for import.
 *
 * <p>Implementations are provided by the runtime module and may restrict the allow-listed set
 * to sources whose directories are configured in the current environment.</p>
 */
public interface ImportSourcesPort {

    /**
     * Returns the ordered list of {@link ImportSource} values supported by this deployment.
     * The list is never null and never empty in a correctly configured environment.
     */
    List<ImportSource> listSupportedSources();
}
