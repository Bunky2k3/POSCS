# Permission matrix

POSCS uses role-based access control. This document is the source of
truth for who can do what — refer to it whenever implementing or
reviewing a controller's access checks.

**Status: everything below is enforced in code EXCEPT the two `†` cells**
(`Sales` staff on Customer and Contract), which is the model agreed with the
customer on 2026-09-14 and is **not built yet** — see
[Hierarchy-based write access](#hierarchy-based-write-access-for-customercontract)
for what is missing and why the matrix already carries it. Everything
unmarked describes current behaviour and can be relied on.

The `roles` table is seeded (see
[`db/migrations/V2__seed_default_roles__ndat2003.sql`](db/migrations/V2__seed_default_roles__ndat2003.sql)),
login/session is implemented in `AuthenticationController`, and
`CustomerController`/`ContractController`/`ProductController`/
`TechnicalSupportTicketController`/`EmployeeController` enforce this
matrix server-side via `poscs.common.AccessControl.requireFullAccess(...)`.
`EmployeeController` gates once at the top of `doGet`/`doPost` instead of
per-handler like the others, since Employee has no "View only" tier for
any role — every single action (including list/view) requires Admin.

## Roles

| `role_name` in DB | Description |
|---|---|
| `Admin` | Full access to everything, including employee/user management |
| `Sales` | Owns customer relationships and contracts |
| `Kỹ thuật` (Technical) | Owns the product catalog and does technical support work |
| `CSKH` (Customer Support) | Owns technical support tickets |

## Access matrix

`Full` = create, read, update, delete. `View only` = list + detail views, no create/update/delete.

`Sales` is split into the two tiers of the org chart: a **manager** (`quản lý
vùng`) and the **staff** under them (`nhân viên cầm tỉnh`, one province each).
Every other role is a single tier — a `Kỹ thuật` or `CSKH` user having a
manager changes nothing about their access.

| Resource | Admin | Sales — manager | Sales — staff | Kỹ thuật | CSKH |
|---|---|---|---|---|---|
| Customer (`enterprises`) | Full | Full | View only † | View only | View only |
| Contract (`contracts`) | Full | Full | View only † | View only | View only |
| Product (`products`) | Full | View only | View only | Full | View only |
| Ticket (`technicalrequests`) | Full | View only | View only | View only* | Full |
| Employee (`users`) | Full | No access | No access | No access | No access |
| System log (`/systemLog`) | Full | No access | No access | No access | No access |

\* **Exception:** `Kỹ thuật` may update the `status`, `resolutionSummary`,
`rootCause` and `causeCategory` of a ticket currently assigned to them
(`assigned_technician_id` matches their own user id) — everything else about
Ticket stays View only for that role (can't create, delete, reassign, or
touch any other field, including on tickets assigned to someone else). This
reflects the Technical role's real responsibility ("handling assigned
technical requests, updating progress and status") without giving them Full
CRUD. The two cause fields belong on that list for the same reason: the
customer asked that a ticket read as symptom → cause → result, and the cause
is **the technician's own assessment** — nobody else on the ticket is in a
position to fill it in. Enforced in
`TechnicalSupportTicketController.handleUpdate` via
`AccessControl.canUpdateAssignedTicket(...)`: for a non-Full-access actor the
handler keeps the row it read from the database and overwrites only these
four fields, so widening the exception means adding a `set...` call there and
nowhere else.

† **Not built yet.** These two cells describe the agreed target, not today's
code: `AccessControl.FULL_ACCESS_ROLES` currently grants *every* `Sales` user
Full access on Customer and Contract, and `users` has no column saying who
reports to whom, so the two tiers cannot be told apart at runtime. Until that
lands, a `Sales` staff member really has **Full** access on both — which is
also why the manager row needs no mark: `Full` is already true for them today
and stays true afterwards, so the hierarchy only ever *removes* access, never
grants any.

In the target model, by contrast, a `Sales` staff member is read-only on these
two resources — create included — and gets changes made through a change
request their manager approves; see
[Hierarchy-based write access](#hierarchy-based-write-access-for-customercontract).

## Hierarchy-based write access for Customer/Contract

**Decided with the customer on 2026-09-14. Carried in the matrix above as the
two `†` cells, and NOT implemented yet** — `users` has no manager column, and
`FULL_ACCESS_ROLES` still grants every `Sales` user Full access on both
resources. Anyone reading the matrix to predict what the running system does
today must read `†` as Full; anyone implementing should build what this
section describes.

The agreed rule, on top of the role matrix rather than replacing it:

- The **manager** (`quản lý vùng`, upper tier) is the one who touches data
  — including data owned by their subordinates.
- The **subordinate** (`nhân viên cầm tỉnh`, leaf tier, one province each)
  is **read-only**, create included. To change anything they submit a
  **change request** to their manager, who approves or rejects it.

Scope and consequences worth knowing before implementing:

- **Customer and Contract only.** Ticket and Product are untouched — in
  particular the `Kỹ thuật` exception documented above still stands, so an
  assigned technician keeps writing `status`, `resolutionSummary`,
  `rootCause` and `causeCategory` on their own ticket. A technician being
  somebody's subordinate does not make them read-only on tickets.
- **This moves permissions onto a second axis.** Today the only question is
  *which role*; this adds *who owns the row* (`contracts.owner_id`) and
  *who manages whom*, so the checks become row-level rather than a lookup
  in `FULL_ACCESS_ROLES`. The existing `canUpdateAssignedTicket` is the
  closest precedent in shape, though much narrower in reach.
- **Change requests must carry an intent, not a diff.** Because creation is
  gated too, a request to add a customer has no existing row to point at —
  the record it proposes has to live in the request itself. Model it as
  `CREATE` / `UPDATE` / `DELETE` intent; a diff-only design cannot express
  the create case and would have to be rebuilt.
- **Safe by default at rollout.** With no manager recorded, nobody is a
  subordinate and nobody loses access — the restriction only takes effect
  for users who actually get a manager assigned. Filling in the real org
  chart is what switches it on, and that data is still pending from the
  customer.

## Notes for implementation

- Enforce this per-controller (e.g. `CustomerController` checks the
  logged-in user's role before allowing create/update/delete actions),
  not just by hiding UI elements — the JSPs must not be the only line
  of defense.
- "View only" still requires being logged in; there is no anonymous/
  public access to any of these resources.
- **Exporting a list to Excel counts as reading, not managing.** The
  "Xuất Excel" button on the Customer/Contract/Ticket list screens is
  visible to every role that can open that list, and `?action=exportExcel`
  is deliberately *not* gated by `AccessControl.requireFullAccess`. The
  file contains exactly the rows the role can already read on screen — a
  different container for the same data, not extra access. Contrast with
  Contract's "Nhập PDF" (`?action=importForm` / `importPdf`), which
  *creates* contracts and stays Full-access only.
- Buttons for Full-access actions (create/update/delete/import) are hidden
  from "View only" roles via the `canManage` request attribute the
  controllers set from `AccessControl.hasFullAccess(...)`. That is a
  presentation convenience so nobody clicks into a 403 — never the
  enforcement itself, which stays in the controller.
- The **form pages themselves** (`?action=new`, `?action=edit`, and
  Contract's `?action=importForm`) are gated the same way as the POST that
  submits them, so typing the URL by hand gets a 403 instead of a form that
  can only fail on submit. Ticket's `?action=edit` follows the same rule as
  `handleUpdate`: Full access *or* the assigned technician — gating the form
  more tightly than the write would leave that technician with permission to
  save and no way in.
- `Employee` (user account management) has no "View only" tier for
  non-Admin roles — it's Admin-only end to end.
- **System log** (`/systemLog`, viewing and downloading the server's log
  files) is not a business resource, so it is not in `Resource` and not
  governed by `requireFullAccess`. It is gated by
  `AccessControl.requireAdmin(...)` instead, which every action calls —
  including the download, which returns a file without going through a JSP.
  Server logs carry error messages with business context (contract codes,
  emails, the username of whoever acted) plus stack traces, so they stay
  Admin-only with no "View only" tier.
