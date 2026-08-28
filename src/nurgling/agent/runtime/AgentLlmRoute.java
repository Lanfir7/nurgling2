package nurgling.agent.runtime;

import nurgling.llm.LocalLlmState;
import nurgling.llm.LocalLlmStatus;

import java.io.IOException;

final class AgentLlmRoute {
    static final class Target {
        final String baseUrl;
        final String apiKey;
        final String model;

        Target(String baseUrl, String apiKey, String model) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
        }
    }

    static Target resolve(boolean useBuiltIn,
                          LocalLlmStatus localStatus,
                          String externalBaseUrl,
                          String externalApiKey,
                          String externalModel) throws IOException {
        if (useBuiltIn) {
            if (localStatus == null || localStatus.state != LocalLlmState.READY) {
                LocalLlmState state = localStatus == null ? LocalLlmState.STOPPED : localStatus.state;
                throw new IOException("Встроенная LLM недоступна: состояние " + state);
            }
            if (!localStatus.endpoint.isPresent()) {
                throw new IOException("Встроенная LLM недоступна: endpoint отсутствует");
            }
            return new Target(localStatus.endpoint.get().toString(), "", "local");
        }
        return new Target(externalBaseUrl, externalApiKey, externalModel);
    }

    private AgentLlmRoute() {
    }
}
