# Local LLM

The project supports a LOCAL LLM using Ollama.

Configuration:

- Model: llama3.2:3b
- Runs on local machine
- Accessed via HTTP API

Features:

- Works without internet
- Faster for small queries
- Lower cost

Optimizations:

- Reduced max tokens
- Reduced RAG chunks
- Compact prompt mode

Limitations:

- Slower for large responses
- May timeout if context is too large

Comparison:

LOCAL:
- private
- cheaper
- controllable

REMOTE:
- more powerful
- more stable
- requires API
