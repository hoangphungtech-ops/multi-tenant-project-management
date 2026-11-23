"""Bảo đảm file bằng chứng không lọt JWT / SAS / mật khẩu (quy tắc 'Kiểm tra Log trước khi nộp')."""
from evidence import REDACTED, redact_headers, redact_obj, redact_text, redact_url

JWT = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyLTEyMyJ9.c2lnbmF0dXJlLXZhbHVl"
SAS = ("https://st.blob.core.windows.net/exports/t1/j1.csv"
       "?sv=2023-11-03&se=2026-11-16T10%3A05%3A00Z&sr=b&sp=r&sig=AbCdEf%2B123%3D")


def test_jwt_and_bearer_removed_from_text():
    out = redact_text(f"Authorization: Bearer {JWT} and raw {JWT}")
    assert JWT not in out and "eyJ" not in out


def test_authorization_header_redacted():
    assert redact_headers({"Authorization": f"Bearer {JWT}", "X-Request-Id": "r1"}) == {
        "Authorization": REDACTED, "X-Request-Id": "r1"}


def test_sas_signature_removed_from_url_and_json():
    assert "AbCdEf" not in redact_url(SAS)
    assert "AbCdEf" not in redact_text(SAS)
    body = redact_obj({"url": SAS, "expiresAt": "2026-11-16T10:05:00Z"})
    assert "AbCdEf" not in body["url"] and body["url"].startswith("https://st.blob.core.windows.net/exports/")


def test_password_and_connection_strings_removed():
    obj = redact_obj({"password": "P@ssw0rd!", "nested": [{"access_token": JWT}],
                      "conn": "DefaultEndpointsProtocol=https;AccountName=x;AccountKey=SECRETKEY==;"})
    flat = str(obj)
    assert "P@ssw0rd!" not in flat and JWT not in flat and "SECRETKEY" not in flat
