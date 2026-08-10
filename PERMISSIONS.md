# Permission matrix

POSCS uses role-based access control. This document is the source of
truth for who can do what — refer to it whenever implementing or
reviewing a controller's access checks.

**Status: design only.** The `roles` table is seeded (see
[`db/migrations/V2__seed_default_roles__ndat2003.sql`](db/migrations/V2__seed_default_roles__ndat2003.sql)),
but `AuthenticationController` and session-based authorization are not
implemented yet. This document exists so the matrix is settled and
ready to enforce once login/session code lands.

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
