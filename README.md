# AI Android Agent with MCP Tools and Document Retrieval

## Overview

This project is an Android AI assistant application that combines:

- conversational chat with an LLM
- MCP tool integration over HTTP
- orchestration across multiple MCP servers
- a local document indexing pipeline
- retrieval-augmented responses using a SQLite knowledge index

The app can answer normal chat questions, call MCP tools such as currency conversion and post-processing pipelines, and answer project or documentation questions using locally indexed knowledge.

## Architecture Overview

The system is intentionally split into small, focused layers:

```text
User
  ↓
Android App
  ↓
Chat Agent
  ↓
Routing Layer
  ├─ MCP Tools
  │    ├─ currency-server
  │    └─ pipeline-server
  └─ Knowledge Retrieval
       ↓
    SQLite Index
       ↓
    Indexed Documents
```

High-level flow:

```text
User Message
  ↓
ChatAgent
  ↓
Request Routing
  ├─ MCP orchestration path
  ├─ Retrieval-augmented LLM path
  └─ Normal chat path
```

### Major Components

#### Android App

The Android app contains the UI, local agent state, task lifecycle, profile settings, invariant guard, MCP debug tools, and the main chat experience.

#### Agent / Chat System

`ChatAgent` is the central coordinator for:

- normal LLM chat
- task lifecycle handling
- MCP tool routing
- retrieval injection for project knowledge questions

#### MCP Integration

The app acts as the MCP client and talks directly to multiple MCP HTTP servers.

- `currency-server` exposes `get_exchange_rate`
- `pipeline-server` exposes:
  - `search_posts`
  - `summarize_text`
  - `save_to_file`
  - `run_search_summary_pipeline`

Android remains the orchestrator. One MCP server does not call another.

#### Document Indexing Pipeline

A separate Python indexing pipeline processes project documents, chunks them, embeds them, and writes the results into a local SQLite database.

#### Retrieval Layer

The retrieval layer runs inside the Android app:

- detects project/document knowledge questions
- reads `app/src/main/assets/index.db`
- embeds the user query
- finds top relevant chunks
- injects retrieved context into the LLM prompt

## Repository Structure

Example project layout:

```text
AiChalengeApp/
  app/
    src/main/java/com/example/aichalengeapp/
      agent/
      mcp/
      retrieval/
      repo/
      ui/
    src/main/assets/
      index.db
  README.md

doc-indexer/
  input_docs/
  output/
  index_docs.py
  requirements.txt
```

## Features

- Android AI chat interface
- MCP protocol integration over HTTP
- deterministic orchestration across multiple MCP servers
- currency tool support
- pipeline tool support
- local document indexing with SQLite
- retrieval-augmented generation for project knowledge questions
- extensible architecture for future local models or alternative vector stores

## Document Indexing Pipeline

The Python indexing pipeline transforms source documents into a SQLite knowledge index.

### Flow

```text
Documents
  ↓
Chunking
  ↓
Embeddings
  ↓
SQLite index.db
```

### Stored Data

The `chunks` table contains fields such as:

- `source`
- `title_or_file`
- `section`
- `chunk_id`
- `strategy`
- `text`
- `embedding_json`
- metadata fields

### Chunking Strategies

The pipeline can support multiple chunking strategies depending on the source material, for example:

- structure-aware chunks aligned to headings, files, or sections
- semantic or fixed-size chunks for denser retrieval coverage

This lets the index capture both document structure and answerable context.

## Retrieval in the Chat Agent

Retrieval is integrated directly into the chat flow, not exposed as a separate tool or screen.

```text
User question
  ↓
KnowledgeRouter
  ↓
DocumentRetriever
  ↓
Top-k chunks
  ↓
RetrievalPromptBuilder
  ↓
LLM prompt augmentation
  ↓
Final answer
```

### Retrieval Path

When the user asks a project or documentation question such as:

- `How does MCP connection work in this project?`
- `Where is profile logic stored?`
- `Как работает подключение MCP?`

the app:

1. detects that the query is a knowledge request
2. loads the local SQLite index
3. embeds the query
4. computes similarity against stored chunk embeddings
5. selects the top relevant chunks
6. injects those chunks into the LLM prompt as system context
7. lets the existing chat pipeline generate the final answer

### Non-Retrieval Cases

Requests such as these should continue through existing behavior:

- `Сколько будет 100 EUR в USD?`
- `Найди посты по слову qui`
- casual chat unrelated to project knowledge

## Running the Indexer

Example steps:

```bash
cd doc-indexer
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python index_docs.py
```

After generating `index.db`, copy it into:

```text
app/src/main/assets/index.db
```

## Running the Android App

### Prerequisites

- Android Studio
- a valid `DEEPSEEK_API_KEY`
- optional local MCP servers for tool demos
- `index.db` placed in `app/src/main/assets/index.db`

### MCP Configuration

Configure MCP server URLs through Gradle properties:

```properties
MCP_CURRENCY_BASE_URL=http://<mac-ip>:3001/mcp
MCP_PIPELINE_BASE_URL=http://<mac-ip>:3002/mcp
```

### Launch Steps

1. Open the Android project in Android Studio.
2. Ensure `index.db` is present in `app/src/main/assets/`.
3. Build and run the app on an emulator or device.
4. Open the chat screen.
5. Ask a retrieval question or an MCP tool question.

## Example Demo Queries

Retrieval:

- `How does MCP connection work in this project?`
- `Where is profile logic stored?`
- `How does task lifecycle work?`

MCP:

- `Сколько будет 100 EUR в USD?`
- `Найди посты по слову qui, сделай краткую сводку и сохрани в файл, а потом скажи, сколько будет 100 EUR в USD`

## Logging

The app includes explicit logs for demos and debugging.

### Retrieval Logs

- `knowledge_router_match`
- `knowledge_router_no_match`
- `retrieval_start`
- `retrieval_success`
- `retrieved_chunks_count`
- `retrieval_prompt_built`

### MCP Orchestration Logs

- `orchestrator_start`
- `orchestrator_route_pipeline`
- `orchestrator_route_currency`
- `orchestrator_pipeline_success`
- `orchestrator_currency_success`
- `orchestrator_success`
- `orchestrator_failure`

## Demo Walkthrough

### Retrieval Demo

1. Launch the app in the emulator.
2. Ask:

```text
How does MCP connection work in this project?
```

3. Open Logcat and filter by `McpTrace`.
4. Show logs:
   - `knowledge_router_match`
   - `retrieval_start`
   - `retrieved_chunks_count`
   - `retrieval_prompt_built`
5. Show the final answer in chat.

### Negative Retrieval Case

Ask:

```text
Сколько будет 100 EUR в USD?
```

Expected behavior:

- retrieval should not trigger
- the request should continue through the existing MCP currency path

## Future Improvements

- local LLM integration on-device
- vector database support instead of SQLite-only retrieval
- hybrid search with lexical + semantic ranking
- reranking for higher answer quality
- offline embedding models
- knowledge graph integration
- source citation UI for retrieved chunks

## Design Principles

- keep routing deterministic where correctness matters
- extend existing architecture rather than replacing it
- prefer small focused layers over broad rewrites
- keep MCP orchestration and retrieval explicit
