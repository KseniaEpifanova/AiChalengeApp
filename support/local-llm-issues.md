# LOCAL LLM Issues

## LOCAL provider stays on loading

Most common reasons:

- the local model server is not running
- the app is using the wrong LOCAL base URL
- the local model is busy or overloaded

Check:

1. In **Settings**, verify the LOCAL base URL.
2. If you are using an emulator, prefer `http://10.0.2.2:11434` or the port used by your local model server.
3. If you are using a physical device, use your computer's LAN IP instead of `localhost`.

## LOCAL works from terminal but not from the app

This usually means the app cannot reach the same address.

Common cause:

- `localhost` was entered into the app settings

Why this fails:

- on Android emulator, `localhost` points to the emulator itself, not your computer

Use:

- emulator: `http://10.0.2.2:<port>`
- device: `http://<LAN_IP>:<port>`

## LOCAL answers are incomplete

Possible causes:

- the local model is using a compact prompt
- the retrieval context was reduced
- the model timed out or stopped early

What to do:

1. Ask a shorter, more specific question.
2. Try again with `REMOTE` to compare behavior.
3. If both give weak answers, review the indexed docs rather than only the model.

## LOCAL times out on `/help`

If `/help` asks a documentation question, the answer still goes through the LLM after support or docs context is built.

Try:

1. Ask a narrower question.
2. Verify the local model is healthy with a normal chat request.
3. Switch to `REMOTE` temporarily to confirm the issue is isolated to the LOCAL provider.

## LOCAL base URL checklist

Before reporting an issue, confirm:

- the server is running
- the port is correct
- the model is available
- the app is not pointing to `localhost` incorrectly
- the same endpoint responds from the host machine
