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

def login(opener, email, password):
    req = urllib.request.Request(f"{BASE_URL}/login")
    with opener.open(req) as resp:
        html = resp.read().decode('utf-8')
    csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', html)
    csrf = csrf_match.group(1) if csrf_match else ""
    data = urllib.parse.urlencode({"email": email, "password": password, "_csrf": csrf}).encode('utf-8')
    post_req = urllib.request.Request(f"{BASE_URL}/login", data=data, method="POST")
    with opener.open(post_req) as resp:
        return resp.getcode(), resp.geturl()

print("=== TESTING FACULTY NOTIFICATIONS NAVIGATION ===")
fac_session = create_session()
login(fac_session, "suresh@smartexam.edu", "Faculty@123")

# Retrieve notifications for faculty
with fac_session.open(f"{BASE_URL}/notifications/recent") as resp:
    fac_notifs = json.loads(resp.read().decode('utf-8'))
    print(f"Faculty has {len(fac_notifs)} recent notifications:")
    for n in fac_notifs:
        print(f"  - [{n['type']}] ID={n['id']}, Title='{n['title']}'")
        # Click the notification
        with fac_session.open(f"{BASE_URL}/notifications/open/{n['id']}") as open_resp:
            print(f"    -> Destination: {open_resp.geturl()} (Status {open_resp.getcode()})")
            assert open_resp.getcode() == 200

print("Faculty notification routing verified successfully!")

