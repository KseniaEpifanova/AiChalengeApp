# Troubleshooting

## Start Here

If the app is not answering correctly, identify which path is failing:

- chat with `LOCAL`
- chat with `REMOTE`
- developer assistant via `/help`
- RAG retrieval from `index.db`
- developer MCP server

## LOCAL model not responding

Symptoms:

- the answer never arrives
- loading takes too long
- LOCAL fails but REMOTE works

Steps:

1. Open **Settings**.
2. Check the LOCAL base URL.
3. Make sure the local model server is running at that address.
4. Retry with a shorter question to reduce prompt size.
5. If needed, switch to `REMOTE` to confirm the rest of the app is working.

## REMOTE provider fails but LOCAL works

Symptoms:

- REMOTE requests error out immediately
- LOCAL still answers

Steps:

1. Verify the remote API configuration used by the app build.
2. Check internet connectivity.
3. Retry with a small prompt to rule out a provider-side timeout.

## `/help` is not working

Check which type of `/help` request you are making:

- docs question such as `how does RAG work`
- developer MCP question such as `what is the current git branch`

For docs questions:

1. Confirm the docs were indexed into `index.db`.
2. Reinstall the app if `index.db` changed after the previous install.

For branch or project-file questions:

1. Confirm the developer MCP server is running.
2. Confirm the MCP base URL points to the correct server.

## RAG returns no results

Symptoms:

- the answer says context is insufficient
- retrieval seems empty

Steps:

1. Check that `index.db` contains the latest docs.
2. Reinstall the app after refreshing `index.db`.
3. Ask a narrower question with app-specific terms such as `MCP server`, `LOCAL provider`, or `/help command`.

## MCP not connected

Symptoms:

- branch or project file requests fail
- MCP debug tools do not load

Steps:

1. Confirm the developer MCP server is running.
2. Confirm the configured MCP base URL is correct for emulator or device.
3. Retry the request from the MCP Debug screen.

## Wrong base URL

This app depends on correct base URLs for:

- LOCAL provider
- MCP server

Examples:

- emulator to host machine: `http://10.0.2.2:<port>`
- physical device: use your machine's LAN IP and the same port

If the URL points to `localhost` inside the emulator, the app will not reach the host machine.
