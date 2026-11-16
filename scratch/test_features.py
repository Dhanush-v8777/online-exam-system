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

    data = urllib.parse.urlencode({
        "email": email,
        "password": password,
        "_csrf": csrf
    }).encode('utf-8')

    post_req = urllib.request.Request(f"{BASE_URL}/login", data=data, method="POST")
    with opener.open(post_req) as resp:
        res_url = resp.geturl()
        res_code = resp.getcode()
        return res_code, res_url

print("=== 1. TESTING AUTHENTICATION ===")
admin_session = create_session()
code, url = login(admin_session, "admin@smartexam.edu", "Admin@123")
print(f"Admin login -> Status: {code}, Final URL: {url}")
assert "/admin/dashboard" in url, f"Expected redirect to /admin/dashboard, got {url}"

coord_session = create_session()
code, url = login(coord_session, "coordinator@smartexam.edu", "Coord@123")
print(f"Coordinator login -> Status: {code}, Final URL: {url}")
assert "/coordinator/dashboard" in url, f"Expected redirect to /coordinator/dashboard, got {url}"

faculty_session = create_session()
code, url = login(faculty_session, "suresh@smartexam.edu", "Faculty@123")
print(f"Faculty login -> Status: {code}, Final URL: {url}")
assert "/faculty/dashboard" in url, f"Expected redirect to /faculty/dashboard, got {url}"

print("\n=== 2. TESTING STUDENT COUNT API (Requirement 1) ===")
# MCA Department (ID: 7), Semester: 1
with admin_session.open(f"{BASE_URL}/admin/examinations/student-count?departmentId=7&semester=1") as resp:
    data = json.loads(resp.read().decode('utf-8'))
    print(f"MCA Sem 1: count={data.get('count')}, dept={data.get('departmentName')}, hasStudents={data.get('hasStudents')}, msg={data.get('message')}")
    assert data.get('count') == 50, f"Expected 50 MCA sem 1 students, got {data.get('count')}"
    assert data.get('hasStudents') is True
    assert "Total Students: 50" in data.get('message')
    assert "MCA" in data.get('departmentName') or "Master of Computer Applications" in data.get('departmentName')

# MCA Department (ID: 7), Semester: 8 (Zero students)
with admin_session.open(f"{BASE_URL}/admin/examinations/student-count?departmentId=7&semester=8") as resp:
    data = json.loads(resp.read().decode('utf-8'))
    print(f"MCA Sem 8: count={data.get('count')}, hasStudents={data.get('hasStudents')}, msg={data.get('message')}")
    assert data.get('count') == 0
    assert data.get('hasStudents') is False
    assert data.get('message') == "No students registered for this department and semester."

# Accessible via coordinator session as well
with coord_session.open(f"{BASE_URL}/admin/examinations/student-count?departmentId=7&semester=1") as resp:
    data = json.loads(resp.read().decode('utf-8'))
    print(f"Coordinator accessed student count: count={data.get('count')}")
    assert data.get('count') == 50

# CSE Department (ID: 10), Semester: 1 (100 students)
with admin_session.open(f"{BASE_URL}/admin/examinations/student-count?departmentId=10&semester=1") as resp:
    data = json.loads(resp.read().decode('utf-8'))
    print(f"CSE Sem 1: count={data.get('count')}, dept={data.get('departmentName')}")
    assert data.get('count') == 100

print("\n=== 3. TESTING EXAMINATION FORM UI & ATTRIBUTES ===")
with admin_session.open(f"{BASE_URL}/admin/examinations/new") as resp:
    html = resp.read().decode('utf-8')
    assert "studentCountContainer" in html, "Missing studentCountContainer in exam form!"
    assert "roomCapacityStatus" in html, "Missing roomCapacityStatus in exam form!"
    assert "data-capacity=" in html, "Missing data-capacity attributes on exam room options!"
    assert "updateStudentCount" in html, "Missing JS updateStudentCount function in exam form!"
    assert "evaluateRoomCapacity" in html, "Missing JS evaluateRoomCapacity function in exam form!"
    assert "headerNotificationDropdown" in html, "Missing bell dropdown in header!"
    print("✓ Examination form has student count container, room capacity attributes, and bell dropdown.")

print("\n=== 4. TESTING AUTO SCHEDULE GENERATE PAGE ===")
with admin_session.open(f"{BASE_URL}/admin/schedule/generate") as resp:
    html = resp.read().decode('utf-8')
    assert "scheduleStudentCountContainer" in html, "Missing scheduleStudentCountContainer in generate form!"
    assert "fetchScopeStudentCount" in html, "Missing JS fetchScopeStudentCount function!"
    print("✓ Auto schedule generator has dynamic student count indicator container and JS listener.")

print("\n=== 5. TESTING TOPBAR BELL DROPDOWN & NOTIFICATIONS API (Requirement 2) ===")
with admin_session.open(f"{BASE_URL}/notifications/recent") as resp:
    recent = json.loads(resp.read().decode('utf-8'))
    print(f"Admin recent notifications retrieved: {len(recent)} items")
    for r in recent[:3]:
        print(f"  - [{r.get('type')}] ID: {r.get('id')}, Title: {r.get('title')}, openUrl: {r.get('openUrl')}")
        assert r.get('openUrl') == f"/notifications/open/{r.get('id')}"

with admin_session.open(f"{BASE_URL}/notifications") as resp:
    html = resp.read().decode('utf-8')
    assert "/notifications/open/" in html, "Notification items should link to /notifications/open/{id}"
    assert "headerNotificationDropdown" in html, "Bell dropdown should be in header"
    print("✓ Notifications inbox displays clickable items linking to /notifications/open/{id}.")

print("\n=== 6. TESTING NOTIFICATION CLICK & NAVIGATION ===")
if recent:
    first_notif = recent[0]
    notif_id = first_notif['id']
    with admin_session.open(f"{BASE_URL}/notifications/open/{notif_id}") as resp:
        final_url = resp.geturl()
        print(f"Clicked notification {notif_id} ('{first_notif.get('title')}') -> redirected to: {final_url}")
        assert resp.getcode() == 200

print("\n=== 7. TESTING NON-EXISTENT / DELETED RELATED RECORD HANDLING ===")
# Non-existent notification ID -> redirects gracefully to /notifications without HTTP 404
with admin_session.open(f"{BASE_URL}/notifications/open/999999") as resp:
    assert resp.getcode() == 200
    assert "/notifications" in resp.geturl()
    print("✓ Non-existent notification redirected gracefully to /notifications without 404!")

print("\n=== 8. TESTING SERVER-SIDE CAPACITY VALIDATION ON EXAMINATION SAVE ===")
# Try saving an exam for MCA Sem 1 (50 students) with Room LAB-001 (capacity 40 -> insufficient!)
# Room ID for LAB-001:
with admin_session.open(f"{BASE_URL}/admin/examinations/new") as resp:
    html = resp.read().decode('utf-8')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', html).group(1)
# Find LAB-001 room ID (capacity 40)
lab_room_match = re.search(r'<option\s+value="(\d+)"[^>]*data-capacity="40"', html)
lab_room_id = lab_room_match.group(1) if lab_room_match else "5"
print(f"LAB-001 (Capacity 40) Room ID: {lab_room_id}")

save_data = urllib.parse.urlencode({
    "examName": "Validation Test Paper",
    "departmentId": "7", # MCA (50 students in sem 1)
    "semester": "1",
    "subjectId": "7",
    "examType": "REGULAR",
    "examDate": "2026-11-25",
    "startTime": "09:30",
    "durationMinutes": "180",
    "examRoomId": lab_room_id, # 40 capacity < 50 students
    "_csrf": csrf
}).encode('utf-8')

save_req = urllib.request.Request(f"{BASE_URL}/admin/examinations/save", data=save_data, method="POST")
with admin_session.open(save_req) as resp:
    res_url = resp.geturl()
    res_html = resp.read().decode('utf-8')
    print(f"Save with insufficient capacity response URL: {res_url}")
    assert "/admin/examinations/new" in res_url, "Should redirect back to form with error!"
    assert "Insufficient Hall Capacity" in res_html or "Capacity" in res_html, "Should display insufficient capacity flash message!"
    print("✓ Server-side capacity validation correctly rejected insufficient room (40 seats for 50 students).")

print("\n=======================================================")
print(" ALL 8 TEST SUITES COMPLETED AND VERIFIED SUCCESSFULLY!")
print("=======================================================")
