# Swift Invoicing: full audit (2026-11-08)

Read-only review of all of `src/main`, the migrations, the Docker and Windows packaging, and CI. No code was changed.
Paths are relative to `src/main/java/com/swiftlabs/invoicing/` unless they start with `src/`, `Dockerfile` or `packaging/`.
Items marked ✔ were checked again directly in the code. Every other item was confirmed by the reviewer who read that area.

Mark each ID **Yes**, **No** or **Later**, and we fix the agreed ones in order.

---

## 1. Critical / High: fix these first

> **Status (2026-11-08): H1–H12 fixed, not committed yet.** Each has a test, except H9 and H11, which are JavaScript.
> Full suite: 360 tests, 0 failures (4 PostgreSQL/Docker tests skipped).
> New migrations: `V32__manage_mail_privilege.sql` (shared) and `db/tenant/V4__invoice_version.sql`.
> Not covered here: the H8 version check is on invoices only (clients, issuer, catalog and recurring models still use last-save-wins).

| ID | Problem | Where | What goes wrong | Suggested fix |
|---|---|---|---|---|
| H1 ✔ | **2FA bypass** | `company/CompanyFilter.java:60-66`, `access/twofactor/TwoFactorLogin.java:40-45` | After the password step, the session principal still carries full privileges. Someone who knows only the password can stay on `/login/verify`. If an admin edits or renames their profile, `CompanyFilter` calls `SignedInUser.replace()`, which builds a full token, and they are in without the code. | Skip the refresh when `TwoFactorLogin.isPending(...)`, and add a test. |
| H2 ✔ | **Restore filter bypass** | `backup/PostgresTools.java:152-156` | `COPY … FROM stdin;` lines are never checked by `isSafeStatement`. A crafted `.dump` can `COPY public.user_company …` and give a MANAGE_BACKUPS user every company or admin rights. The line-by-line deny-list can also be dodged (`U&"\0070ublic"`, keywords split over two lines). | Require COPY lines to match `^COPY company_N\."?[a-z0-9_]+"? \(...\) FROM stdin;$`. Better: run the restore under a DB role with rights only on `company_N`. |
| H3 ✔ | **Mail settings → account takeover** | `mail/MailSettingsController.java:47`, `access/auth/PasswordResetService.java:154` | Any EDIT_ISSUER user can point SMTP at their own server. Password-reset mails, including the admin's, go through those settings, so the reset link reaches them. | Give mail settings their own admin-only privilege. Record SMTP changes in the audit journal. |
| H4 ✔ | **Reset link built from the Host header** (on-prem) | `access/auth/PasswordResetService.java:133-135` | `app.base-url` is empty on-prem, so the link uses the request's Host. A LAN user requests a reset for the admin with `Host: attacker-pc`. The admin gets a genuine mail linking to the attacker's machine. | Store the canonical URL at setup, or check the Host against an allow-list. |
| H5 ✔ | **`admin` re-created after deletion** | `access/user/DefaultAdminInitializer.java:59` | Deleting the default admin brings it back at the next restart, with full rights and `INITIAL_ADMIN_PASSWORD` or a password written to the logs. | Create it only when `userRepository.count() == 0`. |
| H6 | **Double-click on "Émettre" breaks gap-free numbering** | `invoice/InvoiceService.java:153-220` | Two concurrent issues of the same draft both see DRAFT. The second takes N+1 and overwrites the row, so FA-…-N is lost and the history shows two ISSUED events. | `SELECT … FOR UPDATE` on the invoice in `issue()`, plus a submit guard in JS (see H9). |
| H7 | **Double payment** | `invoice/PaymentService.java:92-120`, `InvoiceService.pay` | A double-submit of the payment form or "Payer le solde" records the payment twice, and the invoice is overpaid. | Lock the invoice row in `record()`, plus the JS guard. |
| H8 | **No `@Version` on any entity** | `Invoice`, `Client`, `Issuer`, `User`, `RecurringInvoice`… | Lost updates. A draft save racing an issue writes `status=DRAFT, invoice_number=NULL` back. Two users editing the same client overwrite each other. A TOTP or recovery code can be used twice at the same time. | `@Version` column (tenant migration V4) and a friendly "modified meanwhile" error. |
| H9 | **No double-submit protection anywhere** | `static/js/app.js`, `invoice/view.html:247`, `invoice/email.html` | Duplicate payments, duplicate emails to clients, duplicate backups or uploads. | A global submit guard: disable the button and show a busy label, re-enable on `pageshow`. |
| H10 | **Issue date never validated** | `InvoiceService.java:128, 184-192` | Typing 2062 instead of 2026, or 31/12 too early, locks the year's numbering for good: every later invoice gets DATE_BEFORE_LAST, and cancelling doesn't help because cancelled invoices still count. | Refuse an issue date after today (using the Clock), and optionally before a closing date. |
| H11 ✔ | **Issuer form can't be saved for companies** | `src/main/resources/templates/issuer/form.html:76, 222` | `aeCardNumber` and `annualCeiling` are `required` but hidden when the type is COMPANY. The browser silently blocks submit, so nothing happens on click. | Drop `required` (the server already validates), or disable inputs in hidden blocks. |
| H12 | **Timezone: dates in UTC in Docker** | `config/ClockConfig.java`, `Dockerfile`, about 20 `LocalDate.now()` / `LocalDateTime.now()` calls | Between 00:00 and 01:00 Algiers time, drafts get yesterday's date. On 1 January, an invoice is numbered in the previous year. Payments "today" are refused as being in the future. | `Clock.system(ZoneId.of("Africa/Algiers"))` (or configurable), `now(clock)` everywhere, `ENV TZ=Africa/Algiers`. |

## 2. Medium

> **Done (quick wins, 2026-11-08):** M1, M2, M9, M22, M25, M26, plus P1, L28, L29 and L30.
> **Done (access control, 2026-11-08):** M3, M5, M6, M7, M8, M11, M12, plus L11, L12 and L13.

### Security / access
| ID | Problem | Where | Fix |
|---|---|---|---|
| M1 | X-Forwarded-For trusted from every private IP: LAN users can bypass the rate limit and forge the IPs in the journal | `src/main/resources/application.properties` (`forward-headers-strategy=native`) | `none` on-prem; `internal-proxies` set to the real proxy in prod |
| M2 ✔ | Rate limit bypassed with encoded paths (`/perform%2Dlogin`) | `access/auth/RateLimitFilter.java:58` | Match on `getServletPath()` / `PathPatternRequestMatcher` |
| M3 | MANAGE_USERS can grant any profile, including Administrateur | `access/user/UserService.java:195-205` | Only profiles whose privileges ⊆ the granter's |
| M4 | Profiles are global: company A's admin changes company B's rights | `access/profile/ProfileService.java:54-83` | Per-company profiles, or require managing every company that uses the profile |
| M5 | Deleting a core privilege (MANAGE_USERS…) cascades and can lock out administration | `access/privilege/PrivilegeService.java:156` | Refuse deleting privileges referenced in `Privileges` |
| M6 | User IDOR: `GET /users/{id}/edit` shows users of other companies | `access/user/UserController.java:95-102` | Check `fullyManages` |
| M7 | Changing the 2FA phone or the email needs no current code or password, so a hijacked session can take the account | `TwoFactorController.java:212`, `AccountController.java:62` | Require the current code or password |
| M8 | Forgot-password timing reveals which emails exist (SMTP is synchronous) | `PasswordResetService.java:86` | Send asynchronously or after the response |
| M9 | Reset links are logged in clear whenever mail is off, prod included | `PasswordResetService.java:169` | Log the link only in the `dev` profile |
| M10 | SMTP password and TOTP secrets in plaintext, so they end up in every backup | `mail/MailSettings.java`, `User.totp_secret` | AES-GCM with a key outside the DB |
| M11 | Mail test shows raw exception text, which turns it into an internal port scanner | `MailSettingsService.java:79` | Generic message; details in the log |
| M12 | VIEW_CLIENTS users see client balances | `client/ClientController.java:43` | Add balances only with VIEW_INVOICES |
| M13 | Every cookie-less request (health probes, bots, static files) creates an HTTP session | `config/SecurityConfig.java:173` | Stateless chain for health and static, or cookie CSRF repo |
| M14 | Windows service runs as LocalSystem, and the backup folder accepts UNC paths (NTLM leak) | `packaging/windows/SwiftInvoicing-service.xml`, `BackupService.java:131` | Virtual service account; reject UNC paths |
| M15 | 2 GB multipart limit on every endpoint, parsed before auth; the logo is `getBytes()` before the size check | `application-onprem.properties:43`, `IssuerController.java:94` | Small global limit, large only on import; check `getSize()` first |
| M16 | Setup can be claimed by anyone on the LAN before the owner | `setup/SetupFilter.java` | Setup from localhost only, or an installer token |

### Business logic
| ID | Problem | Where | Fix |
|---|---|---|---|
| M17 ✔ | Paid or partly paid invoices can be cancelled: payments vanish from the statement but still count in VAT on collections | `InvoiceService.cancel:422`, `StatementService.java:66` | Refuse cancelling when there are payments (credit note or refund instead) |
| M18 | Fully credited cash invoice keeps its stamp duty (e.g. 10 DA) as an unpayable balance; it stays "unpaid" forever | `PaymentService.summary:53`, `InvoiceService.pay:413` | Recompute the stamp after credit, or treat a stamp-only balance as settled; `pay()` should check `balance()` |
| M19 | VAT and turnover drop a cancelled invoice retroactively from its original month (already-filed G50 changes), while accounting books it on the cancellation date | `vat/VatSummaryService.java`, `InvoiceRepository.monthlyTotals` | Record cancellations in their own month, or forbid cancel and require a credit note |
| M20 | A credit note can use VAT rates the original never had (deducts VAT never invoiced) | `InvoiceService.checkCreditNote:385` | Restrict lines to the original's rates and bases |
| M21 | Recurring generation can create duplicate drafts (scheduler + "Préparer maintenant") | `recurring/RecurringInvoiceService.java:123-164` | Lock the model, or an advisory lock per tenant |
| M22 | One company's runtime error stops recurring and backup jobs for all following companies, every hour | `RecurringInvoiceScheduler.java:30`, `BackupScheduler.java:36` | Catch `RuntimeException` per model and per company |
| M23 | Company creation: if the schema migration fails, an active orphan company is left; a `pending_<uuid>` row can stop startup | `company/CompanyService.java:85-107` | Insert as inactive, activate after migrate, roll back on any error |
| M24 | Invoice email holds a DB transaction during PDF render + SMTP: a hung SMTP server exhausts the pool | `invoice/InvoiceMailService.java:98-130` (also `sendTest`, password reset) | Load in a short tx, send outside it, audit after |
| M25 ✔ | CSV/Excel export ignores the "En retard uniquement" filter | `templates/invoice/list.html:15,17` | Add `overdue=` to both links |
| M26 ✔ | Language switch drops the query string, so `/reset-password?token=…` → 400 | `templates/layout/fragments.html:231` | Keep the current query parameters |
| M27 | Session expires after 20 min while typing a long invoice, losing everything on save | session config, invoice/email forms | Keep-alive ping while the form is dirty, plus a `beforeunload` warning |

## 3. Low

| ID | Problem | Where |
|---|---|---|
| L1 | `AmountInWords` breaks ≥ 1 billion ("dix cents millions"); crashes ≥ 10 billion (PDF and view 500) | `common/AmountInWords.java:79-85` |
| L2 | Wording: "deux cents mille" → should be "deux cent mille", "quatre-vingts mille" → "quatre-vingt mille" (printed on legal invoices) | `AmountInWords.java:96,122,156` |
| L3 | qty × price can overflow `NUMERIC(14,2)` → 500 instead of a form error | `InvoiceLineForm` vs tenant V1 |
| L4 | List "Total" includes drafts and cancelled invoices when no status is selected | `InvoiceRepositoryCustomImpl.sumTotal` |
| L5 | VAT on collections ignores refunds/credit notes on paid invoices; cents drift across payments | `VatSummaryService.collected:95` |
| L6 | Documents totalling 0 can be issued and consume a number | `InvoiceService.issue` |
| L7 | JS preview rounding differs from server (1.005 → 1,00 vs 1,01; stamp ceil off by 1 DA) | `static/js/invoice-form.js:27,36` |
| L8 | "Rendre récurrente" on an old invoice creates up to 12 back-dated drafts at once | `RecurringInvoiceForm.fromInvoice:82` |
| L9 | Fully credited non-cash invoice without payment stays ISSUED forever | `PaymentService.settleIfPaid:142` |
| L10 | Two concurrent credit notes can exceed the amount remaining to credit | `InvoiceService.issue:158` |
| L11 | Deleted users keep their live sessions | `UserController.java:165` |
| L12 | Disabling, deleting or revoking the last MANAGE_USERS/PROFILES holder isn't blocked | `UserService.update/delete` |
| L13 | `/users/abc/edit` → 500 with a stack trace instead of 400 | `GlobalExceptionHandler.java:47` |
| L14 | Reset token used unencoded in `redirect:`; reset doesn't re-check `isEnabled()` | `PasswordResetController.java:253` |
| L15 | 2FA lockout is a fixed 15 min (≈480 guesses/day, no escalation); 2FA not required for MANAGE_BACKUPS / COMPANIES | `application.properties:167` |
| L16 | RateLimiter map unbounded, full scan above 10k keys | `RateLimiter.java:117` |
| L17 | Deleting a client silently cascades its recurring schedules | `ClientService.java:61` |
| L18 | Issued PDFs use the *current* logo, initials and client email, not the issue-time snapshot | `InvoicePdfService.java:63`, `pdf.html:168` |
| L19 | Logo not re-encoded (EXIF/GPS embedded in client PDFs; metadata parsed) | `IssuerService.java:113` |
| L20 | Help page shows the backup folder path to all users | `HelpController.java:41` |
| L21 | Scheduled backup marked "failed" just because another company's backup was running | `BackupService.java:209` |
| L22 | Temp `.part`, `pg-*.log` and plaintext `restauration-*.sql` files remain after a crash | `PostgresTools`, `BackupService` |
| L23 | Restore has no `lock_timeout` and can freeze the company for up to 30 min | `PostgresTools.restore` |
| L24 | Pool connection returned still pointing at a company schema if the reset fails | `CompanySchemaConnections.java:50` |
| L25 | Duplicate catalog names / client NIF possible on races; `DataIntegrityViolationException` never translated → 500 | `CatalogService.java:40` |
| L26 | Form trimming skips `item`, `recurring`, `setup`, `newCompany`, `account`, `paymentForm` | `common/FormBindingAdvice.java:15` |
| L27 | Clients listed in random order everywhere (`findAll()` without sort) | `ClientService.java:25` |
| L28 | "Année 2 026" / "Year 2,026" on the VAT page | `i18n` `vat.yearTitle` → `{0,number,#}` |
| L29 | Cancel on a new quote goes to `/invoices` instead of `/quotes` | `templates/invoice/form.html:102` |
| L30 | `rounded-full` is 0.75rem, so circles render as rounded squares | `src/main/frontend/tailwind.config.js:67` |
| L31 | `text-title-md` class doesn't exist | `templates/access/two-factor-verify.html:15` |
| L32 | No favicon → every page load triggers a 404 rendered by the full error page (with all advice queries) | `static/` |
| L33 | Uploading or deleting the logo loses unsaved edits in the main issuer form | `templates/issuer/form.html:259` |
| L34 | Invoice line inputs have no labels (screen readers) | `templates/invoice/lines.html:30-57` |
| L35 | `minlength="8"` hardcoded on reset page (config is adjustable) | `templates/access/reset-password.html:17` |
| L36 | CI: tests only on tags, `contents: write` at workflow level, `${{ github.ref_name }}` interpolated into pwsh | `.github/workflows/windows-installer.yml` |
| L37 | `docker-compose.yml` (untracked) publishes 5432 on all interfaces with password `invoicing` | `docker-compose.yml` |

## 4. Performance / smoothness

> **Done (performance, 2026-11-08):** P2 (icon font 462 KB → 12 KB), P3, P6, P7, P8, P9, P10 and the `List.contains` part of P12.
> P7 and P9 are handled by Hibernate batch fetching. Left: P4 (asynchronous audit, open-in-view), P5 (lazy `Invoice.client`), P11.

| ID | Problem | Where | Fix | Gain |
|---|---|---|---|---|
| P1 | **Static files never cached**: Spring Security sends `no-store` on CSS, JS and the 462 KB icon font | `SecurityConfig.java:109`, properties | `spring.web.resources.cache.cachecontrol.max-age=365d` + content-hash chain | **Biggest gain**: every page currently re-downloads everything |
| P2 | Material Symbols font 462 KB, `font-display: block` (icons invisible until loaded) | `static/fonts`, `app.css:30` | Subset to the ~100 icons used (≈30 KB) | Large |
| P3 | `@ControllerAdvice` work on **every** request (redirects, PDFs, exports, logo, 404s): issuer read twice *including the 500 KB logo BYTEA*, licence Ed25519 check, backup directory listing, company query | `BusinessProfileAdvice`, `LicenceAdvice`, `BackupAdvice`, `CompanySwitcherAdvice` | Logo in its own table/lazy; cache issuer/licence/mail settings per company; run only for real views | Large |
| P4 | ActivityLog writes in `REQUIRES_NEW` while open-in-view holds another connection, so 2 pool connections per request | `config/ActivityLog.java:96` | Async/batched audit; consider `open-in-view=false`; set Hikari pool size explicitly | Medium |
| P5 | `Invoice.client` is EAGER: extra selects on many queries | `Invoice.java:70` | `LAZY` + entity graphs | Medium |
| P6 | Dashboard overdue list: 2 queries per overdue invoice | `PaymentService.overdue:167` | Compute the balance in SQL | Medium |
| P7 | VAT on collections: N+1 on lines and client | `InvoicePaymentRepository.findReceivedBetween` | `JOIN FETCH` | Medium |
| P8 | DejaVu fonts (1.5 MB) re-parsed for every PDF | `InvoicePdfService.java:76` | Cache the font bytes | Medium |
| P9 | N+1: invoice history changes, recurring list subtotals, user list (`fullyManages` per row), accounting credit note originals | various | Fetch joins / batch size | Small |
| P10 | Missing indexes: `invoice(kind, invoice_year, sequence_number)`, `invoice_payment(paid_on)`, `user_company.profile_id`, `app_user.last_company_id`, `recurring_invoice.last_invoice_id`; duplicate `idx_invoice_number` | migrations | Tenant V4 + shared V32 | Small now, grows with data |
| P11 | Client list and catalog datalist fully rendered on each form | `client/list.html`, invoice form | Search/pagination later | Grows with data |
| P12 | Small: `List.contains` in a loop, redundant counter statements per issue, `help.js` normalizing on each keystroke | various | Trivial | Tiny |

## 5. Best practice / code quality

| ID | Item |
|---|---|
| B1 | Use the injected `Clock` everywhere (≈20 direct `now()` calls), part of H12 |
| B2 | DB constraints as a safety net: `UNIQUE(kind, invoice_year, sequence_number)`, `CHECK` on status, kind and amounts ≥ 0 |
| B3 | `Invoice` (625 lines, 43 public setters incl. `setStatus`, `setInvoiceNumber`): use embedded snapshots + state-transition methods; split `InvoiceService` (550 lines) into numbering, quotes, credit notes |
| B4 | `equals/hashCode` on `Profile` (mutable name), `User`, `Privilege` use `getClass()`, so they break with Hibernate proxies |
| B5 | Duplicated helpers: `blankToNull`/`isBlank` in 7 classes, NIF/NIS regex in 4 forms, CSV writer in 2 exports. Extract them. |
| B6 | Advices swallow all `RuntimeException`s without logging (a DB outage shows only as wrong header data) |
| B7 | `Invoice` maps `invoice_year`/`sequence_number` as `nullable=false` but drafts store NULL |
| B8 | Dead/test code in prod: `AmountInWords.main`, `AmountUtils.sumLines`, misplaced javadocs |
| B9 | Dependencies: `bcprov 1.78` (CVE fixes in 1.79+), `openhtmltopdf 1.0.10` abandoned → `io.github.openhtmltopdf` fork; consider Java 21 |
| B10 | Dockerfile: obsolete `java.security.egd`, app files writable by runtime user (`--chown`) |
| B11 | Shutdown: WinSW stop timeout 30 s = Spring graceful phase 30 s → set `timeout-per-shutdown-phase=20s` |
| B12 | `MethodArgumentTypeMismatchException` / client-abort logged as ERROR with stack traces |
| B13 | Unvalidated RC and article d'imposition formats |
| B14 | Inconsistent "can edit" checks (controller authorities vs `currentUser.can` in templates) |
| B15 | Upgrading the bundled PostgreSQL major version isn't handled by the Windows installer |
| B16 | Log forging: raw username in login logs; `lang` cookie without Secure/SameSite |

## 6. Checked and found correct (highlights)

- Gap-free numbering across *different* drafts (ON CONFLICT + FOR UPDATE, tested on Postgres); a failed issue rolls back the counter.
- Money arithmetic: HALF_UP, `compareTo` everywhere, VAT per rate on summed bases, 6-decimal prices, stamp duty brackets.
- Tenant isolation: ThreadLocal is always cleared, schema names regex-checked before SQL, search_path set and reset, no shared L2 cache.
- Backups: no shell, PGPASSWORD not argv, no pipe deadlock, atomic `.part` → move, no path traversal on names.
- Auth: Argon2, session fixation handled, CSRF everywhere, CSP without inline scripts, TOTP replay blocked, reset tokens hashed and single-use.
- XSS: no user data in `th:utext`, JS uses `textContent`; i18n bundles fr/en/ar have identical keys and placeholders.
- Exports: UTF-8 BOM, formula-injection neutralised; register list paginated in DB with entity graph.
