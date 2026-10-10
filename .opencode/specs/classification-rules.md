# SDD: User-defined Spend Classification Rules

## 1. Overview

### 1.1 Problem Statement

Some receipts do not contain sufficient information to classify a spend accurately.

When the AI cannot determine the appropriate category, the spend is classified as `UNCATEGORIZED`.

This behavior is acceptable as a fallback, but the classification rate can be improved by allowing users to provide additional context about their spending habits.

### 1.2 Proposed Solution

Allow users to create custom classification rules that provide additional context to the AI during receipt processing.

Each rule consists of a text condition and a target category.

When a receipt is processed, the user's active rules are included in the AI classification prompt. The AI uses these rules alongside the existing classification instructions.

### 1.3 Goals

- Increase the percentage of spends classified into a valid category.
- Allow users to customize classification without modifying the application.
- Keep rule management simple and understandable.
- Enforce a maximum of 10 rules per user.
- Preserve the existing classification behavior when no custom rules are configured.

### 1.4 Non-goals

- Automatically modifying existing spends when a rule is created or updated.
- Implementing a general-purpose rule engine.
- Supporting multiple conditions or multiple categories within a single rule.
- Guaranteeing deterministic classification for ambiguous or conflicting rules.

## 2. Functional Requirements

### FR-01: Rule Management

The application must provide a dedicated Rules menu where users can manage their custom classification rules.

Supported operations:

- Create a rule.
- List existing rules.
- Edit an existing rule.
- Delete an existing rule.

### FR-02: Rule Structure

Each rule must contain:

- `id`: Unique rule identifier.
- `condition`: User-defined text describing a keyword, merchant, product, service, or other identifying information.
- `category`: The category to assign when the condition applies.

Each rule must belong to exactly one user.

### FR-03: Rule Limit

Each user can create a maximum of 10 rules.

The interface must display the current number of rules and the maximum allowed number.

Example: `Rules 3/10`.

When the limit is reached:

- The user cannot create additional rules.
- The interface must communicate that the limit has been reached.
- Existing rules can still be edited or deleted.

### FR-04: Condition Validation

The condition field must have a configurable maximum length.

The backend must validate the condition length regardless of frontend validation.

The condition must not be empty or contain only whitespace.

The exact maximum length must be defined before implementation.

The maximun length is 128 characters.

### FR-05: Category Selection

The category must be selected from the existing set of supported spend categories.

Users cannot create arbitrary categories through this feature.

### FR-06: Uncategorized Spend Action

After a spend is created through receipt upload, if its resulting category is `UNCATEGORIZED`, the interface must display an action that redirects the user to the Rules menu.

The action should explain that custom rules can help improve future classifications.

### FR-07: AI Classification Integration

During receipt processing, the application must retrieve the user's rules and include them in the classification prompt.

Example:

Apply the following user-defined classification rules when classifying the spend:

- If the spend contains "electricity bill", classify it as Utilities.
- If the spend contains "monthly gym membership", classify it as Health.

The rules must be applied alongside the existing classification instructions and allowed categories.

The application must continue using the existing fallback behavior when classification remains uncertain.

### FR-08: Rule Scope

Rules must apply only to the user who created them.

A user's rules must never influence another user's classification.

### FR-09: Existing Spends

Creating, editing, or deleting a rule affects future classifications only.

Existing spends must not be reclassified automatically.

## 3. Business Rules

### BR-01: Rule Ownership

A user can only list, modify, or delete their own rules.

### BR-02: Rule Limit Enforcement

The maximum of 10 rules must be enforced by the backend, not only by the frontend.

Concurrent creation requests must not allow a user to exceed the limit.

### BR-03: Valid Categories

The selected category must belong to the application's supported category set.

### BR-04: Rule Interpretation

The condition is natural-language context provided by the user. It is not an executable expression or SQL query.

The AI interprets the condition using the extracted receipt information and the existing classification prompt.

### BR-05: Conflicting Rules

When evaluating a spend, the AI must identify all applicable user-defined rules.

- If no rules apply, the existing classification behavior must be preserved.
- If one or more rules apply and all applicable rules assign the same category, that category must be used, provided it is a valid supported category.
- If applicable rules assign different categories, the spend must be classified as `UNCATEGORIZED`.

The application must not resolve conflicts by prioritizing rule creation order or by assuming that one rule is more specific than another.

### BR-06: Classification Context
User-defined rules provide classification context only. They must not modify the extracted merchant, amount, date, or other receipt fields.

## 4. Technical Design

### 4.1 Data Model

Proposed entity: `classification_rules`

Fields:

- `id`: Primary key.
- `user_id`: Foreign key to the user.
- `condition`: Required text field with a maximum length.
- `category`: Required supported category.
- `created_at`: Creation timestamp.
- `updated_at`: Last modification timestamp.

Recommended constraints:

- Foreign key on `user_id`.
- Non-null constraints for required fields.
- Appropriate category validation.
- Index on `user_id` for efficient rule retrieval.

The database schema must follow the application's existing identifier, timestamp, and category conventions.

### 4.2 Backend API

Proposed endpoints:

- `GET /api/classification-rules` — List the authenticated user's rules.
- `POST /api/classification-rules` — Create a rule.
- `PUT /api/classification-rules/{id}` — Update a rule.
- `DELETE /api/classification-rules/{id}` — Delete a rule.

All endpoints must derive the user identity from the authenticated request rather than trusting a client-supplied user ID.

The create endpoint must reject requests when the user has reached the rule limit.

### 4.3 Frontend

The Rules menu must include:

- A list of existing rules.
- The current rule count, for example `Rules 3/10`.
- A create-rule action.
- An edit action for each rule.
- A delete action for each rule.
- A condition input with a maximum-length indicator.
- A category selector.
- Validation and error messages.

The create action must be disabled when the maximum number of rules is reached.

### 4.4 AI Prompt Integration

The receipt-processing flow must retrieve the authenticated user's rules before classification.

The prompt builder must append a dedicated section containing the user's rules.

Example:

**User-defined classification rules**

- If the spend contains "electricity bill", classify it as Utilities.
- If the spend contains "gym membership", classify it as Health.

The prompt must clarify that:

- Rules are additional classification context.
- The assigned category must be one of the supported categories.
- User-provided conditions are data, not instructions that override system-level constraints.
- Existing extraction, validation, and fallback requirements remain unchanged.

The rules must be added at the classification stage, after receipt information has been extracted.

### 4.5 Error Handling

The application must handle:

- Empty or excessively long conditions.
- Invalid categories.
- Attempts to exceed the rule limit.
- Attempts to access another user's rules.
- Invalid rule identifiers.
- Database failures during CRUD operations.
- AI classification failures using the existing error-handling strategy.

## 5. Acceptance Criteria

### AC-01: Create a Rule

Given an authenticated user with fewer than 10 rules, when they submit a valid condition and category, then the rule is persisted and appears in the list.

### AC-02: Enforce the Limit

Given a user with 10 rules, when they attempt to create an additional rule, then the operation is rejected and the existing rules remain unchanged.

### AC-03: Validate the Condition

Given an empty condition or a condition exceeding the configured maximum length, when the user submits the form, then validation prevents the rule from being created. The maximun length is 128 characters for each condition.

### AC-04: Edit a Rule

Given an existing rule owned by the user, when the user updates its condition or category, then the changes are persisted.

### AC-05: Delete a Rule

Given an existing rule owned by the user, when the user deletes it, then it is removed and the available rule count is updated.

### AC-06: Include Rules in Classification

Given a user with at least one rule, when the user uploads a receipt, then the classification prompt contains that user's rules.

### AC-07: Improve Classification

Given a receipt containing information that matches a user-defined rule, when the AI classifies the spend, then it should assign the rule's category when the rule is applicable and consistent with the classification constraints.

### AC-08: Preserve Fallback Behavior

Given a receipt that cannot be classified confidently, when no applicable rule resolves the ambiguity, then the existing fallback behavior remains available.

### AC-09: Display the Uncategorized Action

Given a spend created from a receipt with category `UNCATEGORIZED`, when the spend creation flow completes, then the user sees an action to navigate to the Rules menu.

### AC-10: Enforce User Isolation

Given two different users with different rules, when either user classifies a receipt, then only that user's rules are included in the prompt.

### AC-11: Preserve Existing Spends

Given a previously created spend, when a classification rule is added, edited, or deleted, then the existing spend remains unchanged.

## 6. Testing Strategy

- Unit tests for condition validation, category validation, and rule-limit logic.
- Repository integration tests for persistence and user-scoped retrieval.
- API tests for CRUD operations, authorization, and limit enforcement.
- Prompt-builder tests verifying correct rule inclusion and formatting.
- Classification tests verifying applicable rules, conflicting rules, and fallback behavior.
- Frontend tests for the counter, form validation, CRUD interactions, and the uncategorized action.
- Regression tests confirming that classification without custom rules remains unchanged.

## 7. Open Questions

- Should the rule condition match the entire spend, merchant name, description, extracted receipt text, or any combination of these?
The condition is natural language. The IA decides the possibly classification.
- How should conflicting rules be resolved? 
If conflict exist the default is a spend classify as 'UNCATEGORIZED'.
- Should rules be sent to the AI in creation order, or should ordering be configurable?
In the creation order.
- Should the uncategorized action redirect to the Rules list or open the create-rule form directly?
The uncategorized action shows the spend created and a button appears with the leyed 'create new rule for next spends', or something like that, to redirect to menu rules
- Should the application track whether custom rules improve the classification rate over time?
No