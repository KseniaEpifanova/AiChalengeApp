# Architecture

This project is an Android application with an AI-powered chat system.

Main components:

- ChatViewModel  
  Handles UI state and user interactions.

- ChatAgent  
  Core orchestrator that processes messages and decides how to respond.

- DocumentRetriever  
  Performs retrieval over indexed documents (RAG).

- KnowledgeRouter  
  Decides whether to use RAG or normal chat.

- ChatRepository  
  Handles communication with LLM providers.

- LLM Providers  
  - LOCAL (Ollama)
  - REMOTE (API)

Flow:

User → ChatViewModel → ChatAgent →  
→ (RAG via DocumentRetriever)  
→ or direct LLM call → Response → UI
