# MCP Issues

## MCP server is not connected

The app uses an MCP server for developer-assistant tools such as:

- current git branch
- project file listing

If those requests fail, check the MCP server first.

## `/help what is the current git branch?` fails

Likely causes:

- the MCP server is not running
- the MCP base URL is wrong
- the tool is unavailable on the connected MCP server

Steps:

1. Open the MCP Debug screen.
2. Connect and load tools.
3. Confirm the developer MCP server responds.
4. Retry `/help what is the current git branch?`

## `/help list project files` fails

This request depends on the MCP server exposing project-file tools.

Check:

1. The MCP server is connected to the correct project root.
2. The app is pointed at the correct MCP base URL.
3. Tools load successfully in MCP Debug.

## MCP base URL is wrong

Typical mistake:

- using `localhost` from inside the emulator

Use:

- emulator: `http://10.0.2.2:3002/mcp`
- device: `http://<LAN_IP>:3002/mcp`

If the developer MCP server is already running on your computer and the app still cannot connect, the address is usually the first thing to verify.

## MCP Debug shows no tools

Possible reasons:

- the MCP server is down
- the server started against the wrong project root
- the app is using the wrong MCP base URL

Check the server logs for:

- startup confirmation
- project root
- registered tool list

## `/help` docs question works but MCP question fails

This means RAG is likely working and MCP is the problem.

Example:

- `/help how does RAG work?` works
- `/help what is the current git branch?` fails

In this case:

1. keep the current `index.db`
2. focus on MCP connectivity and tool availability
3. verify the MCP server is the developer server expected by the app
