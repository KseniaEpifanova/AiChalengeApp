# RAG (Retrieval-Augmented Generation)

This project uses a RAG system to answer questions based on project data.

Steps:

1. User sends a message
2. Query is converted into an embedding
3. Relevant chunks are retrieved from the index
4. Top chunks are selected
5. Response is generated using LLM + retrieved context

Modes:

- Baseline retrieval
- Filtered retrieval (with entity awareness)

Features:

- Source tracking
- Quote extraction
- No-knowledge mode (when grounding is weak)

Optimization:

- Reduced number of chunks for LOCAL model
- Compact prompt for better performance
