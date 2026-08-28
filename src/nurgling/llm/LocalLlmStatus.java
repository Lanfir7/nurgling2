package nurgling.llm;

import java.net.URI;
import java.util.Optional;

/** Immutable state and endpoint captured under the manager lifecycle lock. */
public final class LocalLlmStatus {
    public final LocalLlmState state;
    public final Optional<URI> endpoint;

    public LocalLlmStatus(LocalLlmState state, Optional<URI> endpoint) {
        this.state = state == null ? LocalLlmState.STOPPED : state;
        this.endpoint = endpoint == null ? Optional.<URI>empty() : endpoint;
    }
}
