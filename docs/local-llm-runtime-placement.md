# Local LLM runtime placement

The client does not ship llama.cpp runtime files. Deploy them next to `hafen.jar`:

- `ai/llama-server.exe`
- all DLLs required by that `llama-server.exe` build, placed in the same `ai/` directory
- `ai/model.gguf`

Default process-global config keys in `NConfig` point to relative paths under `ai/`, resolved from the runtime directory that contains the running client jar. Absolute paths can be configured explicitly if needed.
