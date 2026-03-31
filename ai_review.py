from pathlib import Path

def load_file(path: str) -> str:
    p = Path(path)
    if not p.exists():
        return ""
    return p.read_text(encoding="utf-8", errors="replace")

def trim_text(text: str, max_chars: int) -> str:
    if len(text) <= max_chars:
        return text
    return text[:max_chars] + "\n...[truncated]..."

def main() -> None:
    diff = load_file("diff.txt")

    readme = load_file("README.md")
    architecture = load_file("docs/architecture.md")
    rag = load_file("docs/rag.md")
    local_llm = load_file("docs/local-llm.md")

    context = "\n\n".join(
        [
            "README:\n" + trim_text(readme, 2000),
            "ARCHITECTURE:\n" + trim_text(architecture, 2000),
            "RAG:\n" + trim_text(rag, 2000),
            "LOCAL LLM:\n" + trim_text(local_llm, 2000),
        ]
    )

    prompt = f"""
You are a senior Android engineer reviewing a pull request.

Review the diff using the project documentation context.

Focus on:
- potential bugs
- architecture issues
- maintainability issues
- recommendations

Be critical but constructive.
Do not invent issues not supported by the diff.

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

    print("========== AI REVIEW INPUT ==========")
    print(prompt)
    print("========== AI REVIEW RESULT ==========")
    print("## Potential Bugs")
    print("- No automated LLM call is connected yet. This is a placeholder review pipeline.")
    print()
    print("## Architecture Issues")
    print("- The workflow is currently set up to collect diff and project context, but not yet send them to an LLM.")
    print()
    print("## Recommendations")
    print("- Connect this script to your LOCAL or REMOTE LLM provider.")
    print("- Keep diff and docs context size limited to avoid prompt bloat.")
    print("- Add PR comment publishing as the next step.")

if __name__ == "__main__":
    main()
