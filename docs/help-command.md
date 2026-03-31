# `/help` Command

`/help` is a dedicated developer-assistant entrypoint.

Behavior:

1. Detect `/help` early
2. Strip the prefix
3. If empty, return built-in usage guidance
4. Otherwise answer using README and `docs/` content first
5. If the question asks for the current git branch, request it through MCP

Safety goals:

- keep normal chat unchanged
- keep normal RAG unchanged for non-`/help` messages
- avoid broad routing changes
- avoid extra slash commands
