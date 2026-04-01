# FAQ

## What is the difference between LOCAL and REMOTE?

- `LOCAL` uses your local model endpoint, such as an Ollama-style server.
- `REMOTE` sends the request to the configured remote LLM API.
- If `LOCAL` is slow or unavailable, switching to `REMOTE` can confirm whether the issue is the local model or the app configuration.

## Why is the LOCAL provider not responding?

Common causes:

- the local model server is not running
- the LOCAL base URL is incorrect
- the model is overloaded by a large prompt or retrieval context

What to check:

1. Open **Settings** in the app.
2. Verify the LOCAL base URL.
3. Confirm the local model server is running on that address.
4. Retry with a shorter question.

## Why does `/help` not answer project questions?

`/help` is the developer assistant entrypoint. It depends on:

- project documentation being present in retrieval data
- the `index.db` asset being up to date
- the developer MCP server being available for branch or project-file requests

If `/help` only returns a generic or insufficient answer:

1. Reinstall the app if the bundled `index.db` was recently changed.
2. Confirm the developer MCP server is reachable if the question asks for git branch or project files.
3. Retry with a more specific question such as `how does RAG work` or `what is the current git branch`.

## Why is RAG not returning useful results?

RAG answers depend on the indexed documents in `index.db`.

Likely causes:

- the relevant docs are missing from the index
- the index is outdated
- the question is too vague

Try this:

1. Ask a more specific question.
2. Make sure the docs were indexed before the app was installed.
3. Reinstall the app after updating `index.db`.

## Why do I see timeout behavior?

Timeouts usually happen when:

- the LOCAL model is too slow for the current prompt
- the LOCAL base URL points to the wrong host
- the network request to the REMOTE provider is failing

If only `LOCAL` times out, check the local model endpoint first.

## When should I switch from LOCAL to REMOTE?

Switch to `REMOTE` when:

- you need a quick sanity check
- LOCAL is unavailable
- the local model is too slow for the current question

If both fail, the issue is more likely in app configuration or connectivity.
