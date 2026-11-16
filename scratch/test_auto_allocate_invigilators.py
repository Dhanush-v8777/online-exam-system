import requests
import re
import io

BASE_URL = "http://localhost:8081"

def extract_csrf(html_text):
    match = re.search(r'name="_csrf"\s+value="([^"]+)"', html_text)
    if not match:
        match = re.search(r'value="([^"]+)"\s+name="_csrf"', html_text)
    if match:
        return match.group(1)
    return None

def login(session, username, password):
    r = session.get(f"{BASE_URL}/login")
    csrf = extract_csrf(r.text)
    data = {
        "email": username,
        "password": password
    }
    if csrf:
        data["_csrf"] = csrf
    r2 = session.post(f"{BASE_URL}/login", data=data, allow_redirects=True)
    return r2

def main():
    print("=== STARTING AUTO ALLOCATE INVIGILATORS VERIFICATION ===")

    # Step 1: Login as Admin
    admin_session = requests.Session()
    r_login = login(admin_session, "admin@smartexam.edu", "Admin@123")
    assert r_login.status_code == 200, "Admin login failed"
    print("✓ 1. Admin authenticated")

    # Step 2: Inspect initial /admin/invigilation page
    r_page = admin_session.get(f"{BASE_URL}/admin/invigilation")
    assert r_page.status_code == 200, f"Failed to load invigilation page: {r_page.status_code}"

    # Check UI Elements
    assert "Manual Allocation" in r_page.text, "Button 'Manual Allocation' not found!"
    assert "Auto Allocate All Invigilators" in r_page.text, "Button 'Auto Allocate All Invigilators' not found!"
    assert "autoAllocateSpinner" in r_page.text, "Loading spinner element not found!"
    assert "autoAllocateBtn" in r_page.text, "Button id autoAllocateBtn not found!"
    
    # Check that manual modal does NOT auto-show on initial page load
    # In fixed code, modal.show() only renders inside th:if="${selectedExamId != null}"
    has_unconditional_modal_show = re.search(r"const\s+preselectedExamId\s*=\s*'\[\[\$\{selectedExamId\}\]\]'", r_page.text)
    assert not has_unconditional_modal_show, "BUG DETECTED: Unconditional modal auto-show script still present in HTML!"
    print("✓ 2. UI verified: 'Manual Allocation' and 'Auto Allocate All Invigilators' buttons present, loading indicator ready, no auto-opening manual modal.")

    # Step 3: Trigger Auto Allocate All Invigilators via POST
    print("\nExecuting Auto Allocate All Invigilators via POST...")
    csrf = extract_csrf(r_page.text)
    r_post = admin_session.post(
        f"{BASE_URL}/admin/invigilation/auto-allocate",
        data={'_csrf': csrf} if csrf else {},
        allow_redirects=True
    )
    assert r_post.status_code == 200, f"Auto allocate request failed: {r_post.status_code}"
    print(f"   Final URL: {r_post.url}")
    assert "/admin/invigilation" in r_post.url

    # Verify no manual modal auto-show on response page
    assert not re.search(r"const\s+preselectedExamId\s*=\s*'\[\[\$\{selectedExamId\}\]\]'", r_post.text), "BUG: Manual modal script triggered after auto allocation!"
    print("✓ 3. Execution did NOT redirect to or display the manual entry form.")

    # Step 4: Verify Success message / staffing feedback
    assert "Auto-Allocation Complete" in r_post.text or "All scheduled examinations are already fully staffed" in r_post.text, "Neither success nor complete notice found in response!"
    print("✓ 4. System displayed allocation outcome message.")

    # Step 5: Verify assigned faculty appear in the table
    # Extract table rows
    invig_rows = re.findall(r'<tr[^>]*>\s*<td class="fw-bold text-navy">([^<]+)</td>\s*<td>\s*<div>([^<]+)</div>.*?<div class="fw-bold text-navy">([^<]+)</div>', r_post.text, re.DOTALL)
    print(f"✓ 5. Total active invigilation rows in table: {len(invig_rows)}")
    assert len(invig_rows) > 0, "No invigilation rows found in table!"
    for exam, dt, fac in invig_rows[:5]:
        print(f"   • {exam.strip()} on {dt.strip()} -> Assigned: {fac.strip()}")

    # Step 6: Verify Zero Same-Day Conflicts Across All Assigned Duties in MySQL
    import mysql.connector
    conn = mysql.connector.connect(host='localhost', user='root', password='1414', database='smartexam_db')
    cursor = conn.cursor(dictionary=True)
    cursor.execute('''
        SELECT i.faculty_id, f.full_name, e.exam_date, COUNT(*) as duty_count, GROUP_CONCAT(e.exam_name SEPARATOR ' | ') as exams
        FROM invigilations i
        JOIN faculty f ON i.faculty_id = f.id
        JOIN examinations e ON i.examination_id = e.id
        GROUP BY i.faculty_id, f.full_name, e.exam_date
        HAVING COUNT(*) > 1
    ''')
    conflicts = cursor.fetchall()
    conn.close()

    if conflicts:
        for c in conflicts:
            print(f"CONFLICT DETECTED in DB: Faculty {c['full_name']} (ID {c['faculty_id']}) on {c['exam_date']}: {c['exams']}")
    assert len(conflicts) == 0, f"Institution rule violated: {len(conflicts)} faculty member(s) assigned to multiple exams on same day!"
    print(f"✓ 6. Verified in MySQL: Zero same-day faculty conflicts across all assignments!")

    # Step 7: Verify GET /admin/invigilation/auto-allocate (Coordinator Dashboard shortcut)
    print("\nTesting Coordinator Dashboard GET shortcut /admin/invigilation/auto-allocate...")
    coord_session = requests.Session()
    login(coord_session, "coordinator@smartexam.edu", "Coord@123")
    r_get_auto = coord_session.get(f"{BASE_URL}/admin/invigilation/auto-allocate", allow_redirects=True)
    assert r_get_auto.status_code == 200, f"GET shortcut failed with status {r_get_auto.status_code}"
    assert "/admin/invigilation" in r_get_auto.url
    print("✓ 7. GET shortcut executed successfully and redirected to /admin/invigilation.")

    # Step 8: Verify Manual Allocation is preserved and functioning separately
    print("\nVerifying Manual Allocation functionality remains intact...")
    # Check available-faculty endpoint for exam #5
    r_avail = admin_session.get(f"{BASE_URL}/admin/invigilation/available-faculty?examId=5")
    assert r_avail.status_code == 200, "available-faculty API failed"
    data = r_avail.json()
    print(f"✓ 8. Manual allocation available-faculty endpoint returned {len(data)} eligible faculty.")

    print("\n>>> ALL TESTS PASSED! AUTO ALLOCATE INVIGILATORS IS COMPLETELY FIXED AND VERIFIED! <<<")

if __name__ == "__main__":
    main()
