package nurgling.agent.runtime;

import nurgling.llm.LocalLlmState;
import nurgling.llm.LocalLlmStatus;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentLlmRouteTest {
    @Test
    void builtInReadyUsesLifecycleEndpointWithoutExternalCredentials() throws Exception {
        AgentLlmRoute.Target target = AgentLlmRoute.resolve(
                true,
                new LocalLlmStatus(LocalLlmState.READY, Optional.of(new URI("http://127.0.0.1:8080"))),
                "https://external.example",
                "secret",
                "external-model"
        );

        assertEquals("http://127.0.0.1:8080", target.baseUrl);
        assertEquals("", target.apiKey);
        assertEquals("local", target.model);
    }

    @Test
    void externalSelectionPreservesConfiguredTarget() throws Exception {
        AgentLlmRoute.Target target = AgentLlmRoute.resolve(
                false,
                new LocalLlmStatus(LocalLlmState.STOPPED, Optional.<URI>empty()),
                "https://external.example/v1",
                "secret",
                "external-model"
        );

        assertEquals("https://external.example/v1", target.baseUrl);
        assertEquals("secret", target.apiKey);
        assertEquals("external-model", target.model);
    }

    @Test
    void builtInStartingDoesNotFallBackToExternalTarget() {
        IOException error = assertThrows(IOException.class, () -> AgentLlmRoute.resolve(
                true,
                new LocalLlmStatus(LocalLlmState.STARTING, Optional.of(URI.create("http://127.0.0.1:8080"))),
                "https://external.example",
                "secret",
                "external-model"
        ));

        assertTrue(error.getMessage().contains("Встроенная LLM"));
        assertTrue(error.getMessage().contains("STARTING"));
    }

    @Test
    void builtInReadyWithoutEndpointIsUnavailable() {
        IOException error = assertThrows(IOException.class, () -> AgentLlmRoute.resolve(
                true,
                new LocalLlmStatus(LocalLlmState.READY, Optional.<URI>empty()),
                "https://external.example",
                "secret",
                "external-model"
        ));

        assertTrue(error.getMessage().contains("endpoint"));
    }

    @Test
    void builtInFailedReportsFailedState() {
        IOException error = assertThrows(IOException.class, () -> AgentLlmRoute.resolve(
                true,
                new LocalLlmStatus(LocalLlmState.FAILED, Optional.<URI>empty()),
                "https://external.example",
                "secret",
                "external-model"
        ));

        assertTrue(error.getMessage().contains("FAILED"));
    }

    @Test
    void builtInStoppedReportsStoppedState() {
        IOException error = assertThrows(IOException.class, () -> AgentLlmRoute.resolve(
                true,
                new LocalLlmStatus(LocalLlmState.STOPPED, Optional.<URI>empty()),
                "https://external.example",
                "secret",
                "external-model"
        ));

        assertTrue(error.getMessage().contains("STOPPED"));
    }
}
