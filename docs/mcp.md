# MCP

This project uses MCP for small, isolated tool integrations.

Current MCP integrations:

- currency tools
- pipeline tool
- developer server tools for repo-aware flows

Developer-server tool contract currently supported by the app:

- `get_current_git_branch`
- `list_project_files`
- optional read/write file tools resolved dynamically from:
  `read_project_file`, `read_file`, `get_file_content`
  `write_project_file`, `write_file`, `save_file`, `create_file`

If the developer server exposes only branch and file-list tools, branch-aware help still works, but file search/generation flows cannot read file contents.
