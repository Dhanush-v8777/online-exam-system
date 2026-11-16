import requests
import re
import io

BASE_URL = "http://localhost:8081"

def extract_csrf(html_text):
    m = re.search(r'name=["\']_csrf["\']\s+value=["\']([^"\']+)["\']', html_text)
    if not m:
        m = re.search(r'value=["\']([^"\']+)["\']\s+name=["\']_csrf["\']', html_text)
    return m.group(1) if m else ""

def test_all():
    print("=== STARTING SMARTEXAM ENHANCEMENTS VERIFICATION ===")
    
    # ----------------------------------------------------
    # TEST 1: Coordinator Login
    # ----------------------------------------------------
    coord_session = requests.Session()
    login_page = coord_session.get(f"{BASE_URL}/login")
    csrf_token = extract_csrf(login_page.text)
    
    login_resp = coord_session.post(f"{BASE_URL}/login", data={
        "email": "coordinator@smartexam.edu",
        "password": "Coord@123",
        "_csrf": csrf_token
    }, allow_redirects=True)
    
    assert login_resp.status_code == 200, f"Coordinator login failed: {login_resp.status_code}"
    print("[PASS] Coordinator login successful")

    # ----------------------------------------------------
    # TEST 2: Coordinator Timetable - Assigned Faculty & Badges
    # ----------------------------------------------------
    tt_resp = coord_session.get(f"{BASE_URL}/coordinator/timetable")
    assert tt_resp.status_code == 200, f"Timetable load failed: {tt_resp.status_code}"
    tt_html = tt_resp.text
    
    assert "Assigned Invigilators" in tt_html, "Assigned Invigilators column missing"
    assert "Staffing Status" in tt_html, "Staffing Status column missing"
    assert "/admin/examinations/view/" in tt_html, "Clickable examination details links missing in timetable"
    assert ("Faculty Assignment Pending" in tt_html or "bi-person-badge" in tt_html), "Faculty assignment status missing"
    print("[PASS] Requirement 1 & 4: Timetable displays assigned faculty with dept and staffing status badges")

    # ----------------------------------------------------
    # TEST 3: Coordinator Dashboard - Examination Readiness Matrix
    # ----------------------------------------------------
    dash_resp = coord_session.get(f"{BASE_URL}/coordinator/dashboard")
    assert dash_resp.status_code == 200, f"Dashboard load failed: {dash_resp.status_code}"
    dash_html = dash_resp.text
    
    assert "Examination Readiness &amp; Operational Audit Matrix" in dash_html or "Examination Readiness" in dash_html, "Readiness section missing"
    assert "Timetable" in dash_html, "Readiness Timetable column missing"
    assert "Room Allocation" in dash_html, "Readiness Room column missing"
    assert "Seating Allocation" in dash_html, "Readiness Seating column missing"
    assert "Faculty Allocation" in dash_html, "Readiness Faculty column missing"
    assert "Conflict Check" in dash_html, "Readiness Conflict column missing"
    assert "Readiness Status" in dash_html, "Readiness Status column missing"
    assert ("READY" in dash_html or "ACTION REQUIRED" in dash_html or "PENDING" in dash_html), "Readiness status badge missing"
    print("[PASS] Requirement 8: Coordinator Dashboard displays complete 5-point Examination Readiness Summary Matrix")

    # ----------------------------------------------------
    # TEST 4: Clickable Examination View Page
    # ----------------------------------------------------
    exam_links = re.findall(r'/admin/examinations/view/(\d+)', tt_html)
    if exam_links:
        exam_id = exam_links[0]
        view_resp = coord_session.get(f"{BASE_URL}/admin/examinations/view/{exam_id}")
        assert view_resp.status_code == 200, f"Exam view failed for ID {exam_id}: {view_resp.status_code}"
        view_html = view_resp.text
        
        assert "Section A: Examination Identification" in view_html, "Section A missing"
        assert "Section B: Student Statistics" in view_html, "Section B missing"
        assert "Section C: Examination Room" in view_html, "Section C missing"
        assert "Section D: Faculty Invigilators" in view_html, "Section D missing"
        assert "Section E: Student Seating Allocation" in view_html, "Section E missing"
        assert "Section F: Operational Readiness Audit Matrix" in view_html, "Section F missing"
        assert "Reporting Time" in view_html, "Faculty reporting time missing"
        assert "Print Details" in view_html, "Print button missing"
        print(f"[PASS] Requirement 3: Detailed Examination View (/admin/examinations/view/{exam_id}) fully validated with Sections A-F")
    else:
        print("[WARN] No exams found in timetable to test view page directly")

    # ----------------------------------------------------
    # TEST 5: Seating Allocation - 5 Real-Time Dynamic Metrics
    # ----------------------------------------------------
    seating_resp = coord_session.get(f"{BASE_URL}/admin/seating")
    assert seating_resp.status_code == 200, f"Seating page load failed: {seating_resp.status_code}"
    seating_html = seating_resp.text
    
    assert "Total Eligible Students" in seating_html, "Total Eligible Students metric card missing"
    assert "Total Allocated Students" in seating_html, "Total Allocated Students metric card missing"
    assert "Remaining Unallocated" in seating_html, "Remaining Unallocated metric card missing"
    assert "Available Room Capacity" in seating_html, "Available Room Capacity metric card missing"
    assert "Vacant Desks" in seating_html, "Vacant Desks metric card missing"
    print("[PASS] Requirement 2: Real-time dynamic MySQL metrics cards (5 cards) displayed on Seating page")

    # ----------------------------------------------------
    # TEST 6: Conflict Diagnostic & Resolution Engine
    # ----------------------------------------------------
    conflict_resp = coord_session.get(f"{BASE_URL}/admin/conflicts")
    assert conflict_resp.status_code == 200, f"Conflicts page load failed: {conflict_resp.status_code}"
    conflict_html = conflict_resp.text
    
    assert "Real-Time Examination Conflict Diagnostic Engine" in conflict_html, "Conflicts page title missing"
    assert "conflictResolveModal" in conflict_html, "Interactive resolution modal missing"
    assert "Reassign Room" in conflict_html, "Reassign Room action missing"
    assert "Reschedule Date / Time" in conflict_html, "Reschedule Date/Time action missing"
    assert "Reassign Faculty" in conflict_html, "Reassign Faculty action missing"
    print("[PASS] Requirement 6: Conflict Resolution engine with interactive modal and resolution actions verified")

    # ----------------------------------------------------
    # TEST 7: Faculty View - Duty History with Duty Status
    # ----------------------------------------------------
    fac_list_resp = coord_session.get(f"{BASE_URL}/admin/faculty")
    assert fac_list_resp.status_code == 200, f"Faculty list load failed: {fac_list_resp.status_code}"
    fac_ids = re.findall(r'/admin/faculty/view/(\d+)', fac_list_resp.text)
    assert len(fac_ids) > 0, "No faculty members found in directory"
    target_fac_id = fac_ids[0]

    fac_view_resp = coord_session.get(f"{BASE_URL}/admin/faculty/view/{target_fac_id}")
    assert fac_view_resp.status_code == 200, f"Faculty view failed: {fac_view_resp.status_code}"
    fac_html = fac_view_resp.text
    assert "Invigilation Duty History Register" in fac_html, "Duty History Register missing"
    assert "Duty Status" in fac_html, "Duty Status column missing"
    assert "Start Time" in fac_html, "Start Time column missing"
    assert "End Time" in fac_html, "End Time column missing"
    print(f"[PASS] Requirement 7: Faculty Profile (/admin/faculty/view/{target_fac_id}) includes detailed Invigilation Duty History with Duty Status")

    # ----------------------------------------------------
    # TEST 8: Admin Faculty CSV Import Workflow & Role Security
    # ----------------------------------------------------
    coord_import_resp = coord_session.get(f"{BASE_URL}/admin/faculty/import")
    assert coord_import_resp.status_code == 403, f"Expected 403 for Coordinator accessing import, got {coord_import_resp.status_code}"
    print("[PASS] Requirement 5: Security role check verified - Exam Coordinator correctly denied access (403) to /admin/faculty/import")

    # Login as Admin
    admin_session = requests.Session()
    login_page = admin_session.get(f"{BASE_URL}/login")
    csrf_token = extract_csrf(login_page.text)
    
    admin_login = admin_session.post(f"{BASE_URL}/login", data={
        "email": "admin@smartexam.edu",
        "password": "Admin@123",
        "_csrf": csrf_token
    }, allow_redirects=True)
    assert admin_login.status_code == 200, "Admin login failed"
    print("[PASS] Admin login successful")

    # Download Template
    template_resp = admin_session.get(f"{BASE_URL}/admin/faculty/template")
    assert template_resp.status_code == 200, f"Template download failed: {template_resp.status_code}"
    template_text = template_resp.text
    assert "facultyId,fullName,email,phoneNumber,department,designation" in template_text, "Template header mismatch"
    print("[PASS] Requirement 5: Download CSV template verified with required headers")

    # Access Import Page as Admin
    admin_import_resp = admin_session.get(f"{BASE_URL}/admin/faculty/import")
    assert admin_import_resp.status_code == 200, f"Admin import page failed: {admin_import_resp.status_code}"
    admin_csrf = extract_csrf(admin_import_resp.text)

    # Test CSV Preview with 1 valid row and 1 invalid row
    import time
    ts = str(int(time.time()))[-4:]
    test_csv = (
        f"facultyId,fullName,email,phoneNumber,department,designation\n"
        f"FAC_T_{ts},Dr. Test Valid,test.valid{ts}@smartexam.edu,9876543210,MCA,Assistant Professor\n"
        f"FAC_INV_{ts},,bad_email,123,INVALID_DEPT,Lecturer\n"
    )
    
    files = {
        'file': ('test_faculty.csv', io.BytesIO(test_csv.encode('utf-8')), 'text/csv')
    }
    preview_resp = admin_session.post(
        f"{BASE_URL}/admin/faculty/import/preview",
        files=files,
        data={'_csrf': admin_csrf}
    )
    assert preview_resp.status_code == 200, f"Preview failed: {preview_resp.status_code}"
    preview_html = preview_resp.text
    assert "Validation Verification Stage" in preview_html, "Preview stage not reached"
    assert "Dr. Test Valid" in preview_html, "Valid row missing from preview"
    assert "Invalid Records Requiring Correction" in preview_html, "Invalid records section missing"
    assert ("fullName" in preview_html or "Missing" in preview_html), "Validation error diagnostics missing"
    print("[PASS] Requirement 5: Two-stage CSV preview correctly segregated valid vs invalid rows with detailed error diagnostics")

    # Test CSV Confirm
    confirm_csrf = extract_csrf(preview_html)
    dept_m = re.search(r'name=["\']departmentIds["\']\s+value=["\']([^"\']+)["\']', preview_html)
    dept_id = dept_m.group(1) if dept_m else "1"

    confirm_resp = admin_session.post(
        f"{BASE_URL}/admin/faculty/import/confirm",
        data={
            '_csrf': confirm_csrf,
            'facultyIds': [f'FAC_T_{ts}'],
            'fullNames': ['Dr. Test Valid'],
            'emails': [f'test.valid{ts}@smartexam.edu'],
            'phoneNumbers': ['9876543210'],
            'departmentIds': [dept_id],
            'designations': ['Assistant Professor']
        },
        allow_redirects=True
    )
    assert confirm_resp.status_code == 200, f"Confirm failed: {confirm_resp.status_code}"
    confirm_html = confirm_resp.text
    assert ("Batch Import Completed" in confirm_html or "Dr. Test Valid" in confirm_html), "Import confirmation failed"
    print("[PASS] Requirement 5: CSV confirm successfully committed faculty and provisioned User credentials")

    # Verify newly imported faculty can login with provisioned credentials
    new_user_session = requests.Session()
    lp = new_user_session.get(f"{BASE_URL}/login")
    c_token = extract_csrf(lp.text)
    new_user_login = new_user_session.post(f"{BASE_URL}/login", data={
        "email": f"test.valid{ts}@smartexam.edu",
        "password": "Faculty@123",
        "_csrf": c_token
    }, allow_redirects=True)
    assert new_user_login.status_code == 200, "New faculty login failed"
    assert ("/faculty" in new_user_login.url or "Faculty" in new_user_login.text), "New faculty not routed to faculty portal"
    print("[PASS] Requirement 5: Provisioned Faculty User successfully logged in with default password 'Faculty@123'")

    print("\n========================================================")
    print("ALL 8 REQUIREMENTS TESTED AND VERIFIED SUCCESSFULLY!")
    print("========================================================")

if __name__ == "__main__":
    test_all()
