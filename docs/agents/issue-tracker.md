# Issue tracker: Linear

Issues and specs (you may know a spec as a PRD) for this repo live in **Linear**.

- **Workspace**: `memoji-inc`
- **Team**: `Memoji inc` — key `MEM` (issue identifiers look like `MEM-42`)
- **Project**: `flashcards` — https://linear.app/memoji-inc/project/flashcards-c096f147ea79

Every issue created for this repo belongs to team `MEM` **and** the `flashcards` project. Always set both.

## How to operate

Use the Linear MCP tools (`mcp__claude_ai_Linear__*`). They are deferred — load them with a single
`ToolSearch` call listing every tool you expect to need, e.g.:

```
ToolSearch "select:mcp__claude_ai_Linear__save_issue,mcp__claude_ai_Linear__get_issue,mcp__claude_ai_Linear__list_issues,mcp__claude_ai_Linear__save_comment,mcp__claude_ai_Linear__list_comments"
```

There is no `glab`/`gh`-style CLI in play here. Do not shell out; use the MCP tools.

| Operation                | Tool                                                                              |
| ------------------------ | --------------------------------------------------------------------------------- |
| Create / update an issue | `save_issue` (omit `id` to create, pass `id` to update)                             |
| Read an issue            | `get_issue` (accepts `MEM-42` or a UUID)                                            |
| List / search issues     | `list_issues` (filter by `team`, `project`, `label`, `state`)                        |
| Comment                  | `save_comment`                                                                      |
| Read discussion          | `list_comments`                                                                     |
| Apply / remove labels    | `save_issue` with the full desired `labels` array — it **replaces**, so read first   |
| Close                    | `save_issue` with `state: "Done"` (or `"Canceled"` for won't-fix)                    |
| Create a label           | `create_issue_label`                                                                |

Pass Markdown directly in `description` and `body` — real newlines, not `\n` escapes.

## Statuses on team MEM

`Backlog`, `Todo`, `In Progress`, `In Review`, `Done`, `Canceled`, `Duplicate`.

New issues default to `Backlog` unless a skill says otherwise. Triage state is carried by
**labels**, not statuses — see `triage-labels.md`. The one overlap: an issue labelled `wontfix`
should also be moved to `Canceled`.

## When a skill says "publish to the issue tracker"

Create a Linear issue with `save_issue`, setting `team: "MEM"` and `project: "flashcards"`.
For a multi-ticket breakdown, create one issue per ticket and link them to a parent issue
via `parentId` rather than writing a single combined issue.

## When a skill says "fetch the relevant ticket"

Call `get_issue` with the identifier the user gave (e.g. `MEM-42`). Follow up with
`list_comments` when the skill needs the conversation history.

## Pull requests as a request surface

**PRs as a request surface: no.** _(Set to `yes` if this repo treats external PRs as feature
requests; `/triage` reads this flag.)_

## Wayfinding operations

Used by `/wayfinder`. The **map** is a parent issue with **child** issues as tickets.

- **Map**: an issue labelled `wayfinder:map` holding the Notes / Decisions-so-far / Fog body.
- **Child ticket**: an issue with `parentId` set to the map's id. Label `wayfinder:<type>`
  (`research` / `prototype` / `grilling` / `task`).
- **Blocking**: Linear's native issue relations — set a `blocks` / `blocked by` relation between
  the two issues. Where that isn't available, fall back to a `Blocked by: MEM-12, MEM-13` line at
  the top of the child description. A ticket is unblocked when every blocker is `Done` or `Canceled`.
- **Frontier**: list the map's open children, drop any with an open blocker or an assignee;
  first in map order wins.
- **Claim**: assign the issue to yourself and set it `In Progress` — the session's first write.
- **Resolve**: `save_comment` with the answer, set the issue `Done`, then append a context pointer
  (gist + link) to the map's Decisions-so-far.
