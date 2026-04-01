from pathlib import Path
import os
import requests

def load_file(path: str) -> str:
    p = Path(path)
    if not p.exists():
        return ""
    return p.read_text(encoding="utf-8", errors="replace")

def trim_text(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    return text[:max_chars] + "\n...[truncated]..."

def build_prompt(diff: str, context: str) -> str:
    return f"""
You are a senior Android engineer reviewing a pull request.

Review the diff using the project documentation context.

Focus on:
- potential bugs
- architecture issues
- maintainability issues
- recommendations

Rules:
- be critical but constructive
- do not hallucinate
- only mention issues that are supported by the diff
- if there are no serious issues, say so clearly

Return exactly in this format:

## Potential Bugs
- ...

## Architecture Issues
- ...

## Recommendations
- ...

PROJECT CONTEXT:
{trim_text(context, 6000)}

PR DIFF:
{trim_text(diff, 12000)}
"""

def call_llm(prompt: str) -> str:
    api_key = os.environ["LLM_API_KEY"]
    base_url = os.environ["LLM_BASE_URL"].rstrip("/")
    model = os.environ["LLM_MODEL"]

    response = requests.post(
        f"{base_url}/chat/completions",
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        json={
            "model": model,
            "messages": [
                {"role": "system", "content": "You are a strict but helpful senior Android reviewer."},
                {"role": "user", "content": prompt},
            ],
            "temperature": 0.2,
            "max_tokens": 900,
        },
        timeout=120,
    )
    response.raise_for_status()
    data = response.json()
    return data["choices"][0]["message"]["content"]

def main() -> None:
    diff = load_file("diff.txt")

    readme = load_file("README.md")
    architecture = load_file("docs/architecture.md")
    rag = load_file("docs/rag.md")
    local_llm = load_file("docs/local-llm.md")
    help_doc = load_file("docs/help-command.md")
    mcp_doc = load_file("docs/mcp.md")

    context = "\n\n".join(
        [
            "README:\n" + trim_text(readme, 2000),
            "ARCHITECTURE:\n" + trim_text(architecture, 2000),
            "RAG:\n" + trim_text(rag, 2000),
            "LOCAL LLM:\n" + trim_text(local_llm, 2000),
            "HELP:\n" + trim_text(help_doc, 1500),
            "MCP:\n" + trim_text(mcp_doc, 1500),
        ]
    )

    prompt = build_prompt(diff, context)
    review = call_llm(prompt)

    final_text = "# AI Code Review\n\n" + review

    Path("review.md").write_text(final_text, encoding="utf-8")

    print("========== AI REVIEW RESULT ==========")
    print(final_text)

if __name__ == "__main__":
    main()
