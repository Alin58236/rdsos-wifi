# Claude Code global setup (portable copy)

A snapshot (named `global-CLAUDE.md` so Claude Code does not auto-load it as a project file) of the user-level Claude Code configuration from the Windows desktop (2026-11-20), so it can be installed on the laptop (Ubuntu).

## What's here

| File | Goes to | What it is |
|---|---|---|
| `global-CLAUDE.md` | `~/.claude/CLAUDE.md` | Global instructions: role, communication, tech stack, code philosophy, security, git and testing rules |
| `web-interface-guidelines.md` | `~/.claude/web-interface-guidelines.md` | Vercel Web Interface Guidelines, referenced by the global CLAUDE.md |
| `settings.json` | `~/.claude/settings.json` | Model `opus`, effort `high`, auto-compact window, fullscreen TUI, push notifications, Figma + Vercel plugins enabled |
| `skills/*` | `~/.claude/skills/` | Design/taste skills (`design-taste-frontend`, `high-end-visual-design`, `minimalist-ui`, `redesign-existing-projects`, `gpt-taste`, `brandkit`, `image-to-code`, `imagegen-*`, `industrial-brutalist-ui`, `stitch-design-taste`, `full-output-enforcement`) and `lgtv` (personal; LG TV video fixer) |

## Install on the laptop (Linux)

From the repo root:

```bash
mkdir -p ~/.claude/skills
cp docs/claude-setup/global-CLAUDE.md ~/.claude/CLAUDE.md
cp docs/claude-setup/web-interface-guidelines.md ~/.claude/
cp -r docs/claude-setup/skills/* ~/.claude/skills/
chmod +x ~/.claude/skills/lgtv/scripts/*.sh

# settings: copy if none exists yet, otherwise merge by hand
[ -f ~/.claude/settings.json ] && echo "settings.json exists, merge manually" || cp docs/claude-setup/settings.json ~/.claude/settings.json
```

Then in Claude Code:

```
/login
/plugin marketplace add vercel/vercel-plugin
/plugin install vercel-plugin@vercel
/plugin install figma@claude-plugins-official
```

Restart Claude Code and check with `/skills` and `/plugin`.

### GitHub MCP server (optional)
On the desktop it's configured at user scope as `npx -y @modelcontextprotocol/server-github`, with no token in the config. It reads the `GITHUB_PERSONAL_ACCESS_TOKEN` environment variable. **That npm package is deprecated** (archived upstream; GitHub now maintains `github/github-mcp-server`). On the laptop, prefer the `gh` CLI (`gh auth login`), which Claude Code uses for GitHub operations anyway. If you want the MCP server, install GitHub's official one and keep the token in an environment variable or secret store, never in a file in this repo.

## Deliberately NOT included
- `~/.claude/.credentials.json`: login tokens. Log in again with `/login`.
- `~/.claude/settings.local.json`: permission allowlist from other projects (one-off commands that don't apply here).
- `autoMode.environment` from `settings.json`: auto-generated for another repository (`F:\Smart TV`). Claude Code regenerates it.
- `~/.claude/skills/synced/`: skills synced from the claude.ai account (docx, pdf, pptx, xlsx, deep-research, etc.). They should come back once you log in with the same account.
- Per-project memory (`~/.claude/projects/*/memory`): the one RDSOS-relevant rule ("the user writes the code, Claude guides") is already in the repo's `CLAUDE.md`.
- History, sessions, caches.

## Keeping in sync
This is a snapshot. If you change the global setup on one machine, copy the changed files here again and commit.
