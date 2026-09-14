# Permission matrix

POSCS uses role-based access control. This document is the source of
truth for who can do what — refer to it whenever implementing or
reviewing a controller's access checks.

**Status: the whole matrix below is enforced in code**, including the two `†`
cells — the `Sales` manager/staff split agreed with the customer on
2026-09-14. The other half of that agreement is in place too: a subordinate
submits a change request at `/changerequest` and their manager approves or
rejects it. See
[Hierarchy-based write access](#hierarchy-based-write-access-for-customercontract).

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

† **Decided by `users.manager_id`, not by role.** A user with a manager is
"staff" and is read-only on these two resources — **create included**; a user
with no manager is "manager" and keeps Full access. Both tiers carry the same
`Sales` role, which is exactly why the column exists. The manager row needs no
mark because `Full` was already true for them before the hierarchy landed and
stays true after: the hierarchy only ever *removes* access, never grants any.

Nobody is restricted until they are actually given a manager, so turning this
on took no access away from anyone — the real org chart is still pending from
the customer. Enforced in `AccessControl.hasFullAccess(...)`, which every
create/update/delete path on both resources already goes through, and which
also feeds the `canManage` flag the JSPs use to hide buttons.

## Hierarchy-based write access for Customer/Contract

**Decided with the customer on 2026-09-14, and now fully implemented.**

| | |
|---|---|
| `users.manager_id` + two-tier org chart | **done** (V16) |
| Subordinate read-only on Customer/Contract | **done** (`AccessControl.hasFullAccess`) |
| Admin picks a manager on the employee form | **done** |
| Subordinate submits a change request, manager approves | **done** (V17, `/changerequest`) |

What is still pending is **data, not code**: the real org chart (who manages
whom) has not been supplied by the customer, so `manager_id` is empty
everywhere and nobody is restricted yet.

**Approving does not apply the change.** The manager reads the proposal, then
makes the edit on the normal Customer/Contract screen — the request detail page
links straight to it. Replaying a stored payload would overwrite whatever
somebody else changed while the request sat waiting, with nobody the wiser, and
would duplicate every validation rule those screens already carry. The cost is
that the manager retypes; that is cheaper than a silent overwrite, and requests
are far rarer than direct edits.

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
- **The tree is kept to exactly two tiers** in three places, because one alone
  is not enough: the DB refuses a user managing themselves
  (`chk_users_manager_not_self`), `EmployeeDAO.findEligibleManagers` only
  offers people who have no manager of their own, and
  `EmployeeController.isValidManagerChoice` re-checks both before writing —
  the dropdown is a convenience, a hand-made POST is not bound by it.
- **`AccessControl` reads the tier off the session `User`**, so
  `EmployeeDAO.findByUsernameOrEmail` must keep selecting `manager_id`. Drop
  it there and every user silently looks like a manager: no error, no log,
  the restriction just quietly stops applying.

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
