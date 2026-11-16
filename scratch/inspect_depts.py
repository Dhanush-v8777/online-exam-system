import urllib.request
import urllib.parse
import http.cookiejar
import re
import json

BASE_URL = "http://localhost:8081"

def create_session():
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    return opener

admin_session = create_session()

# Login
with admin_session.open(f"{BASE_URL}/login") as resp:
    html = resp.read().decode('utf-8')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', html).group(1)
data = urllib.parse.urlencode({"email": "admin@smartexam.edu", "password": "Admin@123", "_csrf": csrf}).encode('utf-8')
admin_session.open(urllib.request.Request(f"{BASE_URL}/login", data=data, method="POST"))

# Check exam form departments options
with admin_session.open(f"{BASE_URL}/admin/examinations/new") as resp:
    form_html = resp.read().decode('utf-8')

print("Departments in examination form:")
for m in re.finditer(r'<option\s+value="(\d+)">([^<]+)</option>', form_html):
    print(f"ID: {m.group(1)} -> {m.group(2)}")

