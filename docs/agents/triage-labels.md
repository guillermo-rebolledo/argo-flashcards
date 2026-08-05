# Triage Labels

The skills speak in terms of five canonical triage roles. This file maps those roles to the actual
label strings used in this repo's issue tracker (Linear team `MEM` — see `issue-tracker.md`).

| Label in mattpocock/skills | Label in our tracker | Meaning                                  |
| -------------------------- | -------------------- | ---------------------------------------- |
| `needs-triage`             | `needs-triage`       | Maintainer needs to evaluate this issue  |
| `needs-info`               | `needs-info`         | Waiting on reporter for more information |
| `ready-for-agent`          | `ready-for-agent`    | Fully specified, ready for an AFK agent  |
| `ready-for-human`          | `ready-for-human`    | Requires human implementation            |
| `wontfix`                  | `wontfix`            | Will not be actioned                     |

When a skill mentions a role (e.g. "apply the AFK-ready triage label"), use the corresponding label
string from this table.

Edit the right-hand column to match whatever vocabulary you actually use.

## Label state in Linear

As of setup, only **`ready-for-agent`** exists on team `MEM`. The other four
(`needs-triage`, `needs-info`, `ready-for-human`, `wontfix`) do not yet exist — create them with
`create_issue_label` on first use rather than silently skipping the label.

Team `MEM` also carries unrelated categorisation labels (`Bug`, `Improvement`, `Feature`). These are
orthogonal to triage; leave them alone. Remember that `save_issue` **replaces** the whole `labels`
array, so read the issue's current labels before writing.

An issue labelled `wontfix` should also be moved to the `Canceled` status.
