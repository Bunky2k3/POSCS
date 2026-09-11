# Permission matrix

POSCS uses role-based access control. This document is the source of
truth for who can do what — refer to it whenever implementing or
reviewing a controller's access checks.

**Status: enforced for Customer/Contract/Product/Ticket/Employee.** The
`roles` table is seeded (see
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

| Resource | Admin | Sales | Kỹ thuật | CSKH |
|---|---|---|---|---|
| Customer (`enterprises`) | Full | Full | View only | View only |
| Contract (`contracts`) | Full | Full | View only | View only |
| Product (`products`) | Full | View only | Full | View only |
| Ticket (`technicalrequests`) | Full | View only | View only* | Full |
| Employee (`users`) | Full | No access | No access | No access |

\* **Exception:** `Kỹ thuật` may update the `status` and `resolutionSummary`
of a ticket currently assigned to them (`assigned_technician_id` matches
their own user id) — everything else about Ticket stays View only for that
role (can't create, delete, reassign, or touch any other field, including on
tickets assigned to someone else). This reflects the Technical role's real
responsibility ("handling assigned technical requests, updating progress and
status") without giving them Full CRUD. Enforced in
`TechnicalSupportTicketController.handleUpdate` via
`AccessControl.canUpdateAssignedTicket(...)`.

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
