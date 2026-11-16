import requests
import re
import json

BASE_URL = "http://localhost:8081"

def run_tests():
    session = requests.Session()
    print("=== STEP 1: AUTHENTICATION ===")
    login_page = session.get(f"{BASE_URL}/login")
    assert login_page.status_code == 200, f"Login page failed: {login_page.status_code}"
    
    csrf_token = None
    csrf_match = re.search(r'name="_csrf"\s+value="([^"]+)"', login_page.text)
    if csrf_match:
        csrf_token = csrf_match.group(1)
    print(f"Initial CSRF token: {csrf_token}")

    login_data = {
        "email": "coordinator@smartexam.edu",
        "password": "Coord@123",
        "_csrf": csrf_token
    }
    login_resp = session.post(f"{BASE_URL}/login", data=login_data, allow_redirects=True)
    print(f"Login response status: {login_resp.status_code}, URL: {login_resp.url}")
    assert "error" not in login_resp.url, "Login failed!"
    print("✓ Logged in successfully as Exam Coordinator!")

    print("\n=== STEP 2: TEST ROOM AVAILABILITY ON DATE ENDPOINT ===")
    room_resp = session.get(f"{BASE_URL}/admin/examinations/available-rooms?date=2026-11-20")
    print(f"Available rooms HTTP status: {room_resp.status_code}")
    assert room_resp.status_code == 200
    rooms = room_resp.json()
    print(f"Found {len(rooms)} available rooms on 2026-11-20:")
    for r in rooms[:3]:
        print(f" - Hall: {r.get('roomNumber')} ({r.get('building')}) | Capacity: {r.get('capacity')}")
    assert len(rooms) > 0, "Expected at least one available exam hall"

    print("\n=== STEP 3: TEST FACULTY AVAILABILITY ON DATE ENDPOINT ===")
    fac_resp = session.get(f"{BASE_URL}/admin/schedule/faculty-on-date?date=2026-11-20")
    print(f"Available faculty HTTP status: {fac_resp.status_code}")
    assert fac_resp.status_code == 200
    faculty = fac_resp.json()
    print(f"Found {len(faculty)} available faculty members on 2026-11-20:")
    for f in faculty[:3]:
        print(f" - {f.get('fullName')} ({f.get('departmentCode')}) | Duties: {f.get('currentDuties')}/{f.get('maxDuties')}")
    assert len(faculty) > 0, "Expected at least one eligible faculty member"

    print("\n=== STEP 4: TEST SINGLE EXAM BACKEND VALIDATION ===")
    test_date_resp = session.get(f"{BASE_URL}/admin/examinations/check-date?date=2026-11-20")
    print(f"Check date 2026-11-20 response: {test_date_resp.json()}")

    print("\n=== STEP 5: TEST COMPLETE TIMETABLE PREVIEW (MCA SEMESTER 3) ===")
    # Department 7 is MCA (or let's find MCA department ID)
    dept_list_resp = session.get(f"{BASE_URL}/admin/schedule/generate")
    m = re.search(r'value="(\d+)"[^>]*>MCA', dept_list_resp.text)
    mca_dept_id = m.group(1) if m else "7"
    print(f"Using MCA Department ID: {mca_dept_id}")

    preview_resp = session.get(f"{BASE_URL}/admin/schedule/preview?departmentId={mca_dept_id}&semester=3&startDate=2026-12-05&durationMinutes=180&excludeSundays=true")
    print(f"Preview HTTP status: {preview_resp.status_code}")
    assert preview_resp.status_code == 200
    preview_items = preview_resp.json()
    print(f"Preview generated {len(preview_items)} subjects:")
    for item in preview_items:
        print(f" - {item['subjectCode']}: {item['subjectName']} on {item['examDate']} {item['startTime']}-{item['endTime']} | Students: {item['studentCount']} | Rooms: {item.get('roomNames')} (Cap: {item.get('totalAllocatedCapacity')}, Remaining: {item.get('remainingSeats')}) | Faculty: {item.get('facultyNames')} | Readiness: {item.get('readinessStatus')}")
    
    assert len(preview_items) == 6, f"Expected 6 MCA subjects, got {len(preview_items)}"
    for item in preview_items:
        assert item.get('totalAllocatedCapacity') >= item['studentCount'], "Allocated capacity must be sufficient"
        assert item.get('roomStatus') == "SUFFICIENT"
        assert len(item.get('facultyIds', [])) >= item.get('requiredInvigilators', 1)
        assert item.get('readinessStatus') == "READY"

    print("\n=== STEP 6: TEST REPORTS ===")
    # Room Utilization Report
    room_util_resp = session.get(f"{BASE_URL}/admin/reports/room-utilization")
    print(f"Room Utilization Report HTTP status: {room_util_resp.status_code}")
    assert room_util_resp.status_code == 200
    assert "Examination Room Utilization Report" in room_util_resp.text

    # Faculty Workload Report
    fac_workload_resp = session.get(f"{BASE_URL}/admin/reports/faculty-workload")
    print(f"Faculty Workload Report HTTP status: {fac_workload_resp.status_code}")
    assert fac_workload_resp.status_code == 200
    assert "Faculty Workload" in fac_workload_resp.text

    print("\n=== STEP 7: TEST TIMETABLE OVERVIEW & READINESS BADGES ===")
    timetable_resp = session.get(f"{BASE_URL}/coordinator/timetable")
    print(f"Timetable page HTTP status: {timetable_resp.status_code}")
    assert timetable_resp.status_code == 200
    assert "Scheduled Examination Sessions" in timetable_resp.text

    print("\n=== STEP 8: TEST BATCH TIMETABLE PERSISTENCE WITH SEATING & INVIGILATORS ===")
    # Extract authenticated CSRF token
    generate_page = session.get(f"{BASE_URL}/admin/schedule/generate")
    csrf_auth = re.search(r'name="_csrf"\s+value="([^"]+)"', generate_page.text).group(1)

    batch_payload = {
        "_csrf": csrf_auth,
        "departmentId": mca_dept_id,
        "semester": 3
    }

    # Populate batch payload using preview items
    for idx, item in enumerate(preview_items):
        batch_payload[f"items[{idx}].subjectId"] = item["subjectId"]
        batch_payload[f"items[{idx}].examDate"] = item["examDate"]
        batch_payload[f"items[{idx}].startTime"] = item["startTime"][:5]
        batch_payload[f"items[{idx}].durationMinutes"] = item["durationMinutes"]
        if item.get("roomIds"):
            batch_payload[f"items[{idx}].roomId"] = item["roomIds"][0]
            for r_idx, r_id in enumerate(item["roomIds"]):
                batch_payload[f"items[{idx}].roomIds[{r_idx}]"] = r_id
        if item.get("facultyIds"):
            for f_idx, f_id in enumerate(item["facultyIds"]):
                batch_payload[f"items[{idx}].facultyIds[{f_idx}]"] = f_id

    confirm_resp = session.post(f"{BASE_URL}/admin/schedule/batch-confirm", data=batch_payload, allow_redirects=True)
    print(f"Batch confirm HTTP status: {confirm_resp.status_code}, URL: {confirm_resp.url}")
    assert confirm_resp.status_code == 200
    assert "Batch Scheduling Error" not in confirm_resp.text, "Batch confirmation returned error!"
    print("✓ Successfully submitted and persisted complete batch timetable!")

    # Verify Timetable page shows READY badges and allocated rooms
    tt_verify = session.get(f"{BASE_URL}/coordinator/timetable")
    assert "READY" in tt_verify.text
    print("✓ Timetable Overview verified: Displays READY readiness status for fully allocated examinations!")

    print("\n🎉 ALL TESTS AND FUNCTIONAL VERIFICATIONS PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    run_tests()
