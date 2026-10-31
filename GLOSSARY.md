# SpendTracker

Tracks personal expenses (spends), optionally backed by a photographed receipt that is processed asynchronously to extract and classify its data.

## Language

**Spend**:
The root record of an expense. Created either from a photographed receipt or entered manually. Holds the amount, description, category, date, and (optionally) its items and linked receipt data. A manually entered Spend is complete at creation and is never analyzed automatically, even when it is `Uncategorized`.
_Avoid_: Gasto (Spanish UI label only, not a code/domain term), Transaction, Expense record.

**Receipt**:
The photographed source document for a Spend, when one exists. Composed of the stored image plus the raw OCR text and classification metadata extracted from it. Not every Spend has a Receipt — a manually entered Spend may have none. A Receipt is never replaced: to use a different image, the Spend is deleted and a new one is created.
_Avoid_: Ticket, Image (those refer only to the file, not the extracted data attached to it).

**SpendItem**:
One line of an itemized Spend (e.g. a single product on a supermarket receipt). Informational only for now — it does not carry its own category; categorization happens at the Spend level.
_Avoid_: Line item, Product.

**Category**:
One of a curated set an expense is classified into (e.g. Comida, Transporte, Servicios). The set grows over time but is never invented on the fly during enrichment. Each Category has a description of what belongs in it, which guides automatic classification. Assigned either by the user, by a Classification Rule, or by the enrichment step. `Uncategorized` is the category assigned when none of those apply yet.
_Avoid_: Tag, Label (reserved for a possible future, more free-form concept — not introduced yet).

**Classification Rule**:
A user-defined condition ("if it contains X, classify as Y") that steers how a Spend is categorized. Evaluated as part of the enrichment step, not as a separate deterministic matching pass.
_Avoid_: Filter, Mapping.

**Processing status**:
The lifecycle of a Spend as it moves through extraction and enrichment: `PENDING` (queued) → `PROCESSING` (extraction/classification running, tracked internally by its Processing stage) → `PROCESSED` (complete), or `FAILED`. Only a Spend created from a Receipt goes through this lifecycle; a manually entered Spend is `PROCESSED` from the moment it is created. A `FAILED` Spend can be retried, unless it failed because its Receipt is unreadable (see Unreadable Receipt).
_Avoid_: Processing state, Receipt status (status belongs to the Spend, not only to its Receipt).

**Processing stage**:
Which step of the pipeline a `PROCESSING` Spend is in: `EXTRACTION` or `ENRICHMENT`. Internal bookkeeping, never shown to users, who only see `PROCESSING`. It lets a redelivered queue message resume an interrupted step of its own without mistaking it for the other step's work. Only set while the status is `PROCESSING`; any other status clears it.
_Avoid_: Sub-status, Phase.

**Extraction**:
The step that reads a Receipt's image into raw text. Produces source data only.
_Avoid_: OCR step, Scan.

**Unreadable Receipt**:
A Receipt Extraction cannot read: Textract finds no text in it, or rejects the file (for example a PDF with more than one page). The same file always gives the same result, so its Spend goes to `FAILED` on the first attempt, without redeliveries, and cannot be retried. The pages Textract read still count against the AI quota, because AWS bills them.
_Avoid_: Bad image, Invalid receipt.

**Enrichment**:
The step that turns a Receipt's raw text into the Spend's amount, date, description, items and Category, guided by the Category descriptions and the user's Classification Rules.
_Avoid_: Classification (that is only one part of enrichment), Analysis.

**Allowlist**:
The emails of the people invited to use the app; only they can log in. Each email is a row in `allowed_emails`, managed by hand. Being invited is checked on every login, so removing an email revokes access from the next login on.
_Avoid_: Whitelist, Invite list, Sign-up.
