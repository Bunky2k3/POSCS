# Permission matrix

POSCS uses role-based access control. This document is the source of
truth for who can do what — refer to it whenever implementing or
reviewing a controller's access checks.

**Status: enforced for Customer/Contract/Ticket.** The `roles` table is
seeded (see
[`db/migrations/V2__seed_default_roles__ndat2003.sql`](db/migrations/V2__seed_default_roles__ndat2003.sql)),
login/session is implemented in `AuthenticationController`, and
`CustomerController`/`ContractController`/`TechnicalSupportTicketController`
enforce this matrix server-side via
`poscs.common.AccessControl.requireFullAccess(...)` at the top of every
create/update/delete handler. `Product` and `Employee` have no real
controller logic yet (`ProductController`/`EmployeeController` are still
NetBeans stubs), so enforcement for those two rows is still pending —
wire it up the same way once those features are built.

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
| Ticket (`technicalrequests`) | Full | View only | View only | Full |
| Employee (`users`) | Full | No access | No access | No access |

## Notes for implementation

- Enforce this per-controller (e.g. `CustomerController` checks the
  logged-in user's role before allowing create/update/delete actions),
  not just by hiding UI elements — the JSPs must not be the only line
  of defense.
- "View only" still requires being logged in; there is no anonymous/
  public access to any of these resources.
- `Employee` (user account management) has no "View only" tier for
  non-Admin roles — it's Admin-only end to end.
