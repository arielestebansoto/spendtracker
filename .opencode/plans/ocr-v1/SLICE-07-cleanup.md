# SLICE-07: Account Deletion with S3 Cleanup

## Goal

When user deletes account, delete all S3 files for that user before deleting database records.

---

## Tasks

### 7.1 Update UserController.deleteAccount

**File**: `backend/src/main/java/com/arielsoto/spendtracker/user/UserController.java`

```java
@DeleteMapping("/me")
public ResponseEntity<Void> deleteAccount(
    OAuth2AuthenticationToken authentication,
    HttpServletRequest request
) {
    UserApp user = authenticatedUserService.getCurrentUser(authentication);

    // Step 1: Delete all S3 files for this user
    spendReceiptStorageService.deleteAllReceiptsByUser(user);

    // Step 2: Delete all spends (cascade deletes metadata and items)
    spendService.deleteAllByUser(user);

    // Step 3: Delete user
    userRepository.delete(user);

    // Step 4: Invalidate session
    var session = request.getSession(false);
    if (session != null) {
        session.invalidate();
    }
    SecurityContextHolder.clearContext();

    return ResponseEntity.noContent().build();
}
```

### 7.2 Inject SpendReceiptStorageService

**File**: `backend/src/main/java/com/arielsoto/spendtracker/user/UserController.java`

Add to constructor injection:

```java
private final SpendReceiptStorageService spendReceiptStorageService;
```

### 7.3 Verify Cascade Deletes

The `receipt_metadata` and `spend_items` tables have `ON DELETE CASCADE` constraints on `spend_id`, so deleting a spend will automatically delete its metadata and items.

---

## Testing

1. Unit test: Mock `SpendReceiptStorageService`, verify `deleteAllReceiptsByUser` is called
2. Integration test: Create user with receipts, delete account, verify S3 files are deleted
3. Manual test: Delete test account, verify S3 bucket is clean

---

## Rollback

- No database changes to revert
- Remove `SpendReceiptStorageService` injection from `UserController`
