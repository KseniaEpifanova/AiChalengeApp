# AI Chat Assistant (Android)

This is an Android app with an AI-powered chat system.

It supports both local and remote LLMs, RAG (retrieval over project data), and a simple developer assistant mode.

---

## 🚀 Features

- Chat with AI (LOCAL and REMOTE models)
- Local LLM via Ollama (runs on your machine)
- RAG (Retrieval-Augmented Generation)
- Compact mode for local models (faster, more stable)
- Token usage tracking
- Developer assistant (`/help`)

---

## 🧠 LLM Providers

### LOCAL
- Runs via Ollama
- No internet required
- Lower cost
- Optimized (reduced tokens, fewer chunks)

### REMOTE
- API-based model
- More powerful
- Better for complex queries

---

## 🔎 RAG (Retrieval-Augmented Generation)

The app can retrieve relevant information from indexed project documents.

Flow:
1. User sends a message
2. System retrieves relevant chunks
3. LLM generates an answer using context

Includes:
- source tracking
- quotes
- no-knowledge mode (if confidence is low)

---

## 🧑‍💻 Developer Assistant

Use:
/help your question
Examples:
/help what does this project do?
/help how does RAG work?
/help what is the current git branch?

The assistant uses:
- project documentation (README + docs)
- RAG
- MCP (git branch)

---

## 🏗 Architecture (short)

- ChatViewModel — UI state
- ChatAgent — main logic
- DocumentRetriever — RAG
- KnowledgeRouter — routing
- ChatRepository — LLM calls

---

## ⚙️ Tech stack

- Kotlin
- MVVM
- Coroutines
- Retrofit
- Jetpack Compose (if applicable)

---

## 📌 Notes

This project focuses on:
- local AI usage
- performance optimization
- safe architecture changes
- practical developer tooling


## 📊 Experiments

- Local vs Remote LLM comparison
- RAG optimization (chunk reduction)
- Prompt optimization for local models
