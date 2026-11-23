# Kết quả Security Tests

PASS 17 / 19

| Test | Kết quả | Thời gian (s) |
|---|---|---|
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_read_org_b[list B workspaces via B path]` | ✅ PASS | 0.027 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_read_org_b[read B workspace via B path]` | ✅ PASS | 0.009 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_read_org_b[read B workspace via A path]` | ✅ PASS | 0.01 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_read_org_b[read B export job via B path]` | ✅ PASS | 0.007 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_read_org_b[read B export job via A path]` | ✅ PASS | 0.036 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_read_org_b[download B export via B path]` | ✅ PASS | 0.008 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_read_org_b[download B export via A path]` | ✅ PASS | 0.011 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_modify_org_b[update B workspace via B path]` | ✅ PASS | 0.019 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_modify_org_b[update B workspace via A path]` | ✅ PASS | 0.062 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_modify_org_b[delete B workspace via B path]` | ✅ PASS | 0.018 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_modify_org_b[delete B workspace via A path]` | ✅ PASS | 0.024 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_modify_org_b[create workspace in Org B]` | ✅ PASS | 0.02 |
| `backend_tv2/test_tv2_backend.py::test_1_org_a_cannot_modify_org_b[export Org B]` | ✅ PASS | 0.017 |
| `backend_tv2/test_tv2_backend.py::test_1_guest_cannot_write` | ✅ PASS | 0.294 |
| `backend_tv2/test_tv2_backend.py::test_1_no_identity_is_rejected` | ✅ PASS | 0.016 |
| `backend_tv2/test_tv2_backend.py::test_2_revoked_membership_blocked_on_next_request` | ✅ PASS | 0.293 |
| `backend_tv2/test_tv2_backend.py::test_3_same_workspace_name_in_two_tenants` | ❌ FAIL | 0.041 |
| `backend_tv2/test_tv2_backend.py::test_3_duplicate_name_in_same_tenant_is_409` | ❌ FAIL | 0.013 |
| `backend_tv2/test_tv2_backend.py::test_3_db_unique_is_per_tenant` | ✅ PASS | 0.668 |
