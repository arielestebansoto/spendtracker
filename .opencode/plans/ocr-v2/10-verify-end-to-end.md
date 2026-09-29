# Slice 10: Build Verification

## Goal
Confirm the whole change compiles: backend and frontend.

## Steps

```bash
# Backend
cd backend && ./gradlew compileJava

# Frontend
cd frontend && pnpm build
```

## Checklist
- [ ] Backend compiles with no errors
- [ ] Frontend builds with no errors

