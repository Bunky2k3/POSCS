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
login/session is implemented in `LoginController`, `LogoutController`,
`PasswordResetController`, and `ProfileController` (backed by `AuthService` and `ProfileService`), and
the specialized controllers (`CustomerController`, `CustomerListController`, `CustomerDetailController`, `CustomerActionController`, `CustomerExportController`,
`ContractController`, `ContractListController`, `ContractDetailController`, `ContractActionController`, `ContractPaymentController`, `ContractDocumentController`, `ContractHandoverController`, `ContractLinkController`, `ContractExportController`,
`ProductController`,
`TechnicalSupportTicketController`, `TicketListController`, `TicketDetailController`, `TicketActionController`, `TicketExportController`,
`EmployeeController`, `BranchInfoController`) enforce this
matrix server-side via `poscs.service.PermissionService` and `poscs.common.AccessControl.requireFullAccess(...)`.
`EmployeeController` gates once at the top of `doGet`/`doPost` instead of
per-handler like the others, since Employee has no "View only" tier for
any role — every single action (including list/view) requires Admin.
`BranchInfoController` (`/branch`, UC-55) gates the same way, with
`AccessControl.requireAdmin(...)`.

## Roles

| `role_name` in DB | Description |
|---|---|
| `Admin` | Full access to everything, including employee/user management |
| `Sales` | Owns customer relationships, contracts, **the product catalog and technical support tickets** — both creating a ticket and being the person it is assigned to (absorbed `CSKH`'s ticket ownership and `Kỹ thuật`'s products and ticket handling when those roles were merged into Sales — see notes below) |
| `Tổng hợp` (General affairs) | The branch's `Phòng tổng hợp` (V40): views customers, contracts and products; confirms contract handovers sent to their department; records a payment instalment as collected. No other write access, and no access to tickets or employees |

`Tổng hợp` was added on 2026-11-19 to match the branch's real structure
(`docs/SPEC.md` §3.1): the separate `Kế toán` and `Dự án` departments of V32
never existed as such — accounting, receiving handed-over contracts and
collecting payments are all done by one department, `Tổng hợp`. V40 renames
`Kế toán` to `Tổng hợp`, moves every handover of `Dự án` and `Kỹ thuật` onto
it, closes the duplicates that leaves open on the same contract, and drops
`Dự án`. `Tổng hợp` is now the **only** department that receives handovers.

`CSKH` (Customer Support) has been **removed and merged into `Sales`**: the
role added no distinct access beyond ticket ownership, so rather than keep a
fourth role around for that one difference, ticket Full access moved onto
`Sales` directly (`AccessControl.FULL_ACCESS_ROLES`). Existing `CSKH` accounts
were reassigned to `Sales` in the same migration that dropped the role row
(see `db/migrations/`). Nothing about *how* tickets work changed — creation,
the two-column ownership scope — only *which role name* is allowed to do it.

`Kỹ thuật` (Technical) has been **removed and merged into `Sales`** too
(V42, decided 2026-11-19 with the rest of the branch structure in
`docs/SPEC.md` §3.1): the branch has no technical department, so the product
catalog and ticket handling moved onto `Sales`. Product Full access moved from
`Kỹ thuật` to `Sales` in `AccessControl.FULL_ACCESS_ROLES`; the ticket handler
(`assigned_technician_id`, labelled "Người xử lý") is picked from active
`Sales` users (BR-39). V42 moved every `Kỹ thuật` user to `Sales` and every
member of the `Kỹ thuật` department to `Kinh doanh`, then dropped both rows.
Tickets keep their handler — that person now carries the `Sales` role. The
assigned-technician exception that used to let a `Kỹ thuật` user update five
fields of their own ticket (`AccessControl.canUpdateAssignedTicket`) went with
the role: every handler is a `Sales` user with Full access on tickets.

## Access matrix

`Full` = create, read, update, delete. `View only` = list + detail views, no create/update/delete.

**One exception to the "delete" half: Contract.** A *signed* contract is legal
evidence, so there is no business delete for it — deleting one is *voiding a
mis-entered record* (`action=delete`, `ContractDAO.voidRecord`): Admin only,
mandatory written reason, and always leaves a `contract_history` row. So
`Sales` has Full access on Contract yet cannot void a signed one. A contract
still in `Nháp` is different — nothing is signed, so any Full-access user may
delete it, with a reason. The detail page exposes this through a separate
`canVoid` attribute, not `canManage`, and the list screen has no delete
button at all.

Voiding is refused outright — for Admin too — while the contract still has
**amendments** hanging off it (`parent_contract_id`). A foreign key cannot
enforce that here, because contracts are deleted *softly*: the database sees
nothing being removed, and the amendment would quietly point at a contract
that has vanished from every list. `ContractDAO.voidRecord` counts them inside
the transaction, after locking.

**Signing is a separate, gated step.** Per the customer (2026-09-15), staff do
not sign contracts themselves. Creating a contract therefore produces a
`Nháp` (draft) with no `signing_date`; a distinct `action=changeProgress`
transition moves it to `Đã ký` and stamps the date. That transition requires
Admin or a user with no manager in the org tree — the same predicate
`ChangeRequestController` already uses to decide who may approve a change
request (`!currentUser.isSubordinate()`), deliberately reused rather than
inventing a second notion of "who may sign".

Because most Sales users still have `manager_id` null (the org chart is not
yet populated), they can still sign today. That matches the rule applied
everywhere else here — *not yet placed in the tree means not yet restricted* —
so enabling the feature takes nobody's work away; the customer's rule starts
biting for each person as they are given a manager.

Draft contracts are also **deletable** by anyone with Full access, since
nothing is signed yet. Everything from `Đã ký` onward is Admin-only voiding,
as described above. Liquidating or terminating early is *not* gated by the
signing rule — any Full-access user may do it, with a mandatory reason — and
both freeze the contract permanently: after that `ContractDAO.update` refuses
every edit, including from Admin.

**Signing also locks the terms.** From `Đã ký` onward `ContractDAO.update`
writes exactly one column — `owner_id`. It does not appear on the paper both
parties signed: the owner is an internal assignment that follows staffing.
Everything else — code, title, type, counterparty, the three dates, signing
parties, place, value, and the line items — is contract content, and changing
it goes through an amendment.

(Until V34 a second column was writable, `attachment_url`, open for a reason of
its own: the link to the signed PDF usually only *exists* after signing, so
locking it would have meant the file could never be attached. V34 dropped that
column — a real contract carries several documents, not one link — and they now
live in `contract_documents`, added and soft-deleted through their own actions
with their own history rows. Attaching a file after signing no longer goes
through `update` at all.)

This is enforced in the DAO, not by disabling inputs: a hidden button still
POSTs. The form submits the locked fields as `disabled` (browsers do not send
those) and `ContractController.handleUpdate` rebuilds the record from what is
stored, so a hand-made POST changes nothing either.

**Correcting a data-entry mistake** is a separate, narrower door:
`action=correct` / `ContractDAO.correct`. It is **Admin only**
(`requireAdmin`, not `requireFullAccess` — managing contracts is a wider
permission than touching the terms of a signed one), requires a written
reason, and writes a `Sửa sai sót` row into `contract_history` carrying that
reason. It is not a back door for changing what was agreed — that is what an
amendment is for — only for fixing what was typed wrong against the paper in
hand. It still refuses once the contract is frozen: the customer's rule says
"no changes after liquidation, *including from senior staff*", and Admin is
not an exception to it.

**Amendments** (`contracts.parent_contract_id`, V29) are how a signed contract
changes. An amendment is a full contract row of its own — its own code, dates,
line items, value — and it has to be **signed** like any other, which is the
whole point: a change to a signed contract carries a signature, not a click on
Save. Creating one is ordinary Full access (`action=newAmendment` /
`createAmendment`); the parent must be exactly `Đã ký` (a draft is edited
directly; a frozen contract has ended, so what comes after it is a *new*
contract), and it must not itself be an amendment — one level only, checked in
`ContractDAO.insert` inside the transaction, since a self-referencing foreign
key permits chains of any length.

This replaced UC-34, which allowed deleting while the status was
"Chưa hiệu lực". (Earlier revisions of this file cited that rule as "BR-46".
That was wrong — BR-46 is the Ticket detail screen. The delete condition is
described in UC-34's *Mô tả*; the only BR it cites is BR-37, the confirmation
dialog.) That status is computed from `effective_date`, so a contract
signed yesterday but effective next month was still deletable together with
everything it said. The correct condition is *not yet signed* — which V23 could
not express, because `signing_date` was `NOT NULL` and therefore no row was
ever unsigned. V24 introduced the draft state and made the column nullable, so
the condition is now reachable and is what the `isDraft()` branch above checks.

`Sales` is split into the two tiers of the org chart: a **manager** (`quản lý
vùng`) and the **staff** under them (`nhân viên cầm tỉnh`, one province each).
Every other role is a single tier — a `Tổng hợp` user having a manager
changes nothing about their access.

The hierarchy split only narrows **Customer and Contract** (`HIERARCHY_RESTRICTED`
in `AccessControl`) — it does not apply to Ticket or Product, so a Sales
*staff* member has the same Full access to tickets and products as a Sales
*manager*, unlike the † rows.

| Resource | Admin | Sales — manager | Sales — staff | Tổng hợp |
|---|---|---|---|---|
| Customer (`enterprises`) | Full | Full | View only † | View only |
| Contract (`contracts`) | Full | Full | View only † | View only § |
| Product (`products`) | Full | Full | Full | View only |
| Ticket (`technicalrequests`) | Full | Full* | Full* | No access |
| Employee (`users`) | Full | No access | No access | No access |
| System log (`/systemLog`) | Full | No access | No access | No access |
| Branch info (`branch_info`, `/branch`, UC-55) | Full ‡ | No access | No access | No access |

§ **Two writes on Contract for `Tổng hợp`, neither through `FULL_ACCESS_ROLES`:**
- **Record a payment instalment as collected** (UC-53, `action=markPaid`):
  `PermissionService.canMarkPaymentPaid` = `canManageContractPayments`
  (Admin, Sales manager) **or** `Tổng hợp`. Creating or deleting an instalment
  still needs `canManageContractPayments`, so `Tổng hợp` gets `MSG-022` there.
  The button sits on the contract **detail** page (`viewcontractdetail.jsp`,
  modal with the collection date), since `Tổng hợp` cannot open the edit page;
  `ContractPaymentController` sends them back to the detail page afterwards.
- **Confirm a handover** (UC-49): checked by **department**, not role
  (`AccessControl.canCompleteHandover`, `PermissionService.canConfirmHandover`)
  — anyone in the `Tổng hợp` department may close that department's open
  handover; Admin may close any.

`Tổng hợp` sees the whole branch by default on the customer and contract lists
and on the Dashboard (`getCustomerListScope` / `getContractListScope` return
"all", like Admin): they own no customer or contract, so "mine" would be empty.
Every ticket screen returns 403 for them (`PermissionService.canViewTickets`),
and the sidebar hides the "Phiếu hỗ trợ" entry.

\* **Ticket handler (BR-39):** a ticket is assigned to one active `Sales`
user — "Người xử lý", stored in `assigned_technician_id` (the column kept its
old name). The person who takes the call and the person who handles the
ticket are both `Sales` now, often two different people; "Phiếu của tôi" on
the ticket list means the tickets assigned to me, and the list opens on "Tất
cả" for everyone. There is no partial-write tier on tickets any more: the old
`Kỹ thuật` exception (update five fields of a ticket assigned to you, nothing
else) was removed with that role, see [Roles](#roles).

The handler's part of a ticket is five fields: the status, then the diagnosis
narrative — cause, its category, the plan, the outcome. The customer asked
that a ticket read as symptom → cause → result, and the cause is **the
handler's own assessment**, so the detail page groups these five under the
handler's name ("Người xử lý đánh giá"), apart from what the person who took
the call wrote. Each
has its own column on purpose. Before `handlingPlan` existed the plan was being
written into the status-change note instead, which left the history repeating
what the ticket already said and answering a question it was never meant to
answer.

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
| Subordinate read-only on Customer/Contract | **done** (`PermissionService` / `AccessControl.hasFullAccess`) |
| Direct manager (`users.manager_id`) assigned in DB (`Cấp trên` dropdown removed from employee form; see below & `docs/SPEC.md` §3.2) | **done** |
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

**On Contract, the available intents changed with the lifecycle rules.**
`Sửa` and `Xoá` are refused on the write path
(`ChangeRequestController.intentAllowedFor`), because a signed contract cannot
be edited or deleted by the manager either — such a request asks somebody to
do something the system does not allow, and only builds a queue of requests
certain to be rejected. What a subordinate can actually ask for is
`Lập phụ lục`, which is a thing the manager *can* do; its `targetId` is the
**parent** contract and the request detail page links straight to the
amendment form. `Lập phụ lục` is meaningless on Customer and is refused there,
where `Sửa` still means what it always did.

Existing rows are untouched: `Sửa`/`Xoá` requests submitted before this rule
still read, still display, and can still be reviewed. Only new ones are
refused — deleting the old ones would be rewriting the record.

Scope and consequences worth knowing before implementing:

- **Customer and Contract only.** Ticket and Product are untouched — a
  Sales user being somebody's subordinate does not make them read-only on
  tickets or products.
- **This moves permissions onto a second axis.** Today the only question is
  *which role*; this adds *who owns the row* (`contracts.owner_id`) and
  *who manages whom*, so the checks become row-level rather than a lookup
  in `FULL_ACCESS_ROLES`.
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
- **The "Cấp trên" dropdown is gone from the add/edit employee forms** (both
  `EmployeeDAO.findEligibleManagers` and `EmployeeController.
  isValidManagerChoice`, which used to police it, were removed with it —
  there is nothing left for either to check). `manager_id` can currently only
  be set by editing the database directly, until a replacement UI exists.
  `handleUpdate` explicitly re-reads and re-writes the employee's EXISTING
  `manager_id` on every save so it isn't silently wiped just because the form
  no longer submits it.
- **The tree is still kept to exactly two tiers going forward**, but by only
  one enforcement layer now: the DB refuses a user managing themselves
  (`chk_users_manager_not_self`). There is no longer an application-level
  check that a chosen manager has no manager of their own — if a UI to
  assign `manager_id` reappears, it must re-implement that check (see the old
  `findEligibleManagers`/`isValidManagerChoice` in git history for the shape
  of it) before it can safely write.
- **`AccessControl` reads the tier off the session `User`**, so
  `EmployeeDAO.findByUsername` (renamed from `findByUsernameOrEmail` in V36,
  which also dropped the fake company-email column and login-by-email
  entirely) must keep selecting `manager_id`. Drop it there and every user
  silently looks like a manager: no error, no log, the restriction just
  quietly stops applying.

## Row-level write access on Customer for Sales

Role and hierarchy decide *whether* someone may write customers at all; for
`Sales` the row decides *which ones* (agreed with the user 2026-09-24 for the
create page, 2026-09-26 for edit, delete and rating). Enforced in
`CustomerController` (`outsideSalesScope`, `ownerForUpdate`); the pages only
hide what the controller would refuse.

- **Buyers follow the territory** — `Khách mua`, including companies that are
  also suppliers. A Sales user who directly holds provinces (`user_provinces`)
  may write only the customers in their own "Của tôi" scope: owned by them or
  their subordinates, or located in a province they hold. That is exactly the
  list screen's default scope, checked through the same `ListScope`
  (`includes` answers what `predicate` filters), so a customer shown as theirs
  is never refused on Edit and vice versa.
  - Create: only wards in their own provinces, and the customer is theirs.
  - Edit: the province dropdown offers their provinces plus the customer's
    current one.
- **Sales with no provinces yet are not restricted on buyers** — the former
  CSKH accounts create and edit on behalf of the province holders; on create
  the owner is resolved from the territory exactly as for Admin.
- **Sales never change the account owner on Edit** (agreed with the user
  2026-10-01) — buyer or supplier, provinces or not, moving the address to
  another province or not: the current owner is kept and the submitted one is
  ignored. Only Admin reassigns a customer (buyers follow the territory,
  suppliers are picked by hand); the Edit page is the only place that does,
  e.g. when an employee leaves.
- **Supplier-only rows are not territorial.** Any Sales may write only the
  suppliers they or their subordinates own: the owner is locked to the creator
  on create (and, like every Sales edit, to the current owner on edit). Admin picks the owner freely and
  the territory table never overrides it. All 34 provinces are offered.
- **Out of scope means read-only.** The list and detail pages hide
  Edit/Delete/Rate and say whose customer it is; `?action=edit` and the three
  POSTs redirect to the detail page with `error=not_your_customer`.
- Scope is judged on the roles the customer *has*, not on the ones the form
  submits — ticking an extra role is not a way in. Owner and province rules
  follow the roles being *saved*.

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
  different container for the same data, not extra access. (Contract's
  PDF export/import — `?action=exportPdf`, `importForm`, `importPdf` — was
  removed at the client's request; those URLs now fall back to the list.)
- Buttons for Full-access actions (create/update/delete/import) are hidden
  from "View only" roles via the `canManage` request attribute the
  controllers set from `AccessControl.hasFullAccess(...)`. That is a
  presentation convenience so nobody clicks into a 403 — never the
  enforcement itself, which stays in the controller.
- The **form pages themselves** (`?action=new`, `?action=edit`) are gated the same way as the POST that
  submits them, so typing the URL by hand gets a 403 instead of a form that
  can only fail on submit. Ticket's `?action=edit` follows the same rule as
  `handleUpdate`: Full access (Admin, Sales).
- `Employee` (user account management) has no "View only" tier for
  non-Admin roles — it's Admin-only end to end.
- **`Admin` cannot be assigned through the employee add/edit forms.** The
  role dropdown (`EmployeeController.assignableRoles`) omits it — an Admin
  account still has to be created by hand in the database (see the seed-data
  comment in `db/schema.sql`), and `handleCreate`/`handleUpdate` re-check the
  submitted `roleId` server-side so a hand-made POST cannot bypass the
  dropdown either. The one exception is editing an employee who is *already*
  Admin: the dropdown keeps that option (selected) so saving an unrelated
  field on their profile does not silently demote them.
- **System log** (`/systemLog`, viewing and downloading the server's log
  files) is not a business resource, so it is not in `Resource` and not
  governed by `requireFullAccess`. It is gated by
  `AccessControl.requireAdmin(...)` instead, which every action calls —
  including the download, which returns a file without going through a JSP.
  Server logs carry error messages with business context (contract codes,
  emails, the username of whoever acted) plus stack traces, so they stay
  Admin-only with no "View only" tier.
- ‡ **Branch info** (`/branch`, UC-55: the branch director's name, position,
  phone and fax — the source of "our signer" on contracts, BR-56) is a single
  row seeded by V39, so "Full" here means view and update only: there is no
  create or delete. It is not a `Resource` either: `BranchInfoController`
  calls `AccessControl.requireAdmin(...)` once at the top of `doGet`/`doPost`
  (so typing the URL by hand as Sales or Tổng hợp gets a 403 with MSG-022),
  and `BranchInfoService.update` re-checks `PermissionService.isAdmin` against
  the database before writing. The sidebar entry "Thông tin chi nhánh" is
  hidden from non-Admins, but that is presentation only.
