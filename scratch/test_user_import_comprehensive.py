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
    print("=== STARTING USER IMPORT COMPREHENSIVE TEST SUITE ===")
    
    # 1. SECURITY TEST: Non-authenticated access
    s_anon = requests.Session()
    r = s_anon.get(f"{BASE_URL}/admin/users/import", allow_redirects=False)
    print(f"1. Anonymous access to /admin/users/import: status {r.status_code} (expect 302 redirect to login)")
    assert r.status_code == 302, f"Expected 302, got {r.status_code}"

    # 2. SECURITY TEST: Exam Coordinator access (Strict Admin-only)
    s_coord = requests.Session()
    login(s_coord, "coordinator@smartexam.edu", "Coord@123")
    r_coord = s_coord.get(f"{BASE_URL}/admin/users/import", allow_redirects=False)
    print(f"2. Coordinator access to /admin/users/import: status {r_coord.status_code} (expect 403 Forbidden)")
    assert r_coord.status_code == 403, f"Expected 403 Forbidden, got {r_coord.status_code}"
    print("   [PASS] Coordinator is blocked with 403 Forbidden as expected!")

    # 3. ADMIN ACCESS
    s_admin = requests.Session()
    r_login = login(s_admin, "admin@smartexam.edu", "Admin@123")
    print(f"3. Admin login: status {r_login.status_code}")
    assert "User Management" in r_login.text or "Dashboard" in r_login.text or r_login.status_code == 200

    # 4. Check User List page for "Import Users (CSV)" button
    r_list = s_admin.get(f"{BASE_URL}/admin/users")
    print("4. Checking User Management List page for 'Import Users (CSV)' button...")
    assert "Import Users (CSV)" in r_list.text, "Button 'Import Users (CSV)' not found in user list!"
    assert "/admin/users/import" in r_list.text, "Link to /admin/users/import not found in user list!"
    print("   [PASS] Import Users (CSV) button found!")

    # 5. Download CSV Template
    r_template = s_admin.get(f"{BASE_URL}/admin/users/template")
    print(f"5. Download CSV Template: status {r_template.status_code}, content-type: {r_template.headers.get('content-type')}")
    assert r_template.status_code == 200
    assert "fullName,emailAddress,password,userRole" in r_template.text
    assert "attachment; filename=\"users_import_template.csv\"" in r_template.headers.get("content-disposition", "")
    print("   [PASS] CSV Template downloaded successfully!")

    # 6. Generate 20 Sample Records matching user's specification
    # faculty_import_20_sample_records.csv
    sample_csv_20 = [
        "fullName,emailAddress,password,userRole",
        "Ananya Sharma,ananya.sharma01@example.com,Temp@Fac2026A1,FACULTY",
        "Rahul Verma,rahul.verma02@example.com,Temp@Fac2026B2,FACULTY",
        "Priya Iyer,priya.iyer03@example.com,Temp@Fac2026C3,FACULTY",
        "Amitabh Sen,amitabh.sen04@example.com,Temp@Fac2026D4,FACULTY",
        "Deepa Nair,deepa.nair05@example.com,Temp@Fac2026E5,FACULTY",
        "Vikram Malhotra,vikram.malhotra06@example.com,Temp@Fac2026F6,FACULTY",
        "Sneha Kulkarni,sneha.kulkarni07@example.com,Temp@Fac2026G7,FACULTY",
        "Rohan Gupta,rohan.gupta08@example.com,Temp@Fac2026H8,FACULTY",
        "Meera Joshi,meera.joshi09@example.com,Temp@Fac2026I9,FACULTY",
        "Karthik Reddy,karthik.reddy10@example.com,Temp@Fac2026J10,FACULTY",
        "Sunita Rao,sunita.rao11@example.com,Temp@Fac2026K11,FACULTY",
        "Rajesh Pillai,rajesh.pillai12@example.com,Temp@Fac2026L12,FACULTY",
        "Swati Deshmukh,swati.deshmukh13@example.com,Temp@Fac2026M13,FACULTY",
        "Arjun Menon,arjun.menon14@example.com,Temp@Fac2026N14,FACULTY",
        "Pooja Hegde,pooja.hegde15@example.com,Temp@Fac2026O15,FACULTY",
        "Gaurav Saxena,gaurav.saxena16@example.com,Temp@Fac2026P16,FACULTY",
        "Nandini Bose,nandini.bose17@example.com,Temp@Fac2026Q17,FACULTY",
        "Manoj Tiwari,manoj.tiwari18@example.com,Temp@Fac2026R18,FACULTY",
        "Divya Nambiar,divya.nambiar19@example.com,Temp@Fac2026S19,FACULTY",
        "Alok Mishra,alok.mishra20@example.com,Temp@Fac2026T20,FACULTY"
    ]
    csv_data_20 = "\n".join(sample_csv_20).encode('utf-8')

    # 7. Upload & Preview 20 Sample Records
    r_import_page = s_admin.get(f"{BASE_URL}/admin/users/import")
    csrf = extract_csrf(r_import_page.text)
    
    files = {
        'file': ('faculty_import_20_sample_records.csv', io.BytesIO(csv_data_20), 'text/csv')
    }
    data = {'_csrf': csrf} if csrf else {}
    
    r_preview = s_admin.post(f"{BASE_URL}/admin/users/import/preview", files=files, data=data)
    print(f"7. Upload & Preview 20 sample faculty records: status {r_preview.status_code}")
    assert r_preview.status_code == 200
    assert "TOTAL CSV RECORDS" in r_preview.text
    assert "VALID RECORDS" in r_preview.text
    assert "Ananya Sharma" in r_preview.text
    assert "ananya.sharma01@example.com" in r_preview.text
    assert "Alok Mishra" in r_preview.text
    # Ensure plain text passwords are NOT exposed
    assert "Temp@Fac2026A1" not in r_preview.text, "SECURITY VIOLATION: Plain text password found in preview HTML!"
    assert "Temp@Fac2026T20" not in r_preview.text, "SECURITY VIOLATION: Plain text password found in preview HTML!"
    assert "••••••••" in r_preview.text, "Masked password placeholder not found in preview!"
    print("   [PASS] 20 sample records parsed cleanly with passwords masked!")

    # 8. Test Preview with Invalid Records & Edge Cases
    invalid_csv = [
        "fullName,emailAddress,password,userRole",
        "Valid User One,valid.user1@example.com,Pass@1234,COORDINATOR",
        ",missing.name@example.com,Pass@1234,FACULTY",
        "Bad Email User,not-an-email,Pass@1234,FACULTY",
        "Short Password,short.pass@example.com,123,FACULTY",
        "Invalid Role User,bad.role@example.com,Pass@1234,STUDENT",
        "Duplicate File User,dup.file@example.com,Pass@1234,FACULTY",
        "Duplicate File User 2,dup.file@example.com,Pass@1234,FACULTY",
        "Existing DB User,admin@smartexam.edu,Pass@1234,ADMIN"
    ]
    invalid_csv_data = "\n".join(invalid_csv).encode('utf-8')
    
    r_import_page2 = s_admin.get(f"{BASE_URL}/admin/users/import")
    csrf2 = extract_csrf(r_import_page2.text)
    files2 = {
        'file': ('invalid_test.csv', io.BytesIO(invalid_csv_data), 'text/csv')
    }
    r_preview_invalid = s_admin.post(f"{BASE_URL}/admin/users/import/preview", files=files2, data={'_csrf': csrf2} if csrf2 else {})
    print(f"8. Preview with invalid and edge-case records: status {r_preview_invalid.status_code}")
    assert r_preview_invalid.status_code == 200
    assert "Invalid Records Requiring Correction" in r_preview_invalid.text
    assert "Full name is required" in r_preview_invalid.text
    assert "Invalid email address format" in r_preview_invalid.text
    assert "Password must be at least 6 characters" in r_preview_invalid.text
    assert "Unsupported role" in r_preview_invalid.text
    assert "Duplicate email address within uploaded file" in r_preview_invalid.text
    assert "already exists in the database" in r_preview_invalid.text
    print("   [PASS] All validation rules verified (missing name, email format, short pass, invalid role, file duplicate, DB duplicate)!")

    # 9. Download Failed Records Report (CSV)
    r_failed_report = s_admin.get(f"{BASE_URL}/admin/users/import/failed-report")
    print(f"9. Download Failed Records Report: status {r_failed_report.status_code}")
    assert r_failed_report.status_code == 200
    assert "attachment; filename=\"failed_users_import_report.csv\"" in r_failed_report.headers.get("content-disposition", "")
    assert "Row Number,Full Name,Email Address,Role,Failure Reasons" in r_failed_report.text
    assert "STUDENT" in r_failed_report.text
    assert "not-an-email" in r_failed_report.text
    print("   [PASS] Failed records diagnostic report CSV generated and verified!")

    # 10. Re-upload the 20 Sample Records and Confirm Import
    r_import_page3 = s_admin.get(f"{BASE_URL}/admin/users/import")
    csrf3 = extract_csrf(r_import_page3.text)
    files3 = {
        'file': ('faculty_import_20_sample_records.csv', io.BytesIO(csv_data_20), 'text/csv')
    }
    s_admin.post(f"{BASE_URL}/admin/users/import/preview", files=files3, data={'_csrf': csrf3} if csrf3 else {})
    
    # Now confirm import
    r_confirm_page = s_admin.get(f"{BASE_URL}/admin/users/import")
    csrf_confirm = extract_csrf(r_preview.text) # from preview
    r_confirm = s_admin.post(f"{BASE_URL}/admin/users/import/confirm", data={'_csrf': csrf_confirm} if csrf_confirm else {}, allow_redirects=True)
    print(f"10. Confirm 20 Users Import: status {r_confirm.status_code}")
    assert r_confirm.status_code == 200
    assert "User Import Completed" in r_confirm.text
    assert "20 user account(s)" in r_confirm.text
    print("   [PASS] 20 users imported successfully!")

    # 11. Verify User Search & Filtering in User Management
    r_search = s_admin.get(f"{BASE_URL}/admin/users?keyword=Ananya")
    print("11. Testing User Search for 'Ananya'...")
    assert "Ananya Sharma" in r_search.text
    assert "ananya.sharma01@example.com" in r_search.text
    print("   [PASS] Search by name and email verified!")

    r_filter_fac = s_admin.get(f"{BASE_URL}/admin/users?role=FACULTY")
    print("11b. Testing Role Filter for FACULTY...")
    assert "ananya.sharma01@example.com" in r_filter_fac.text
    assert "rahul.verma02@example.com" in r_filter_fac.text
    print("   [PASS] Filter by role FACULTY verified!")

    # 12. Verify Login with Newly Imported User
    s_new_user = requests.Session()
    r_new_login = login(s_new_user, "ananya.sharma01@example.com", "Temp@Fac2026A1")
    print(f"12. Login with newly imported faculty account (ananya.sharma01@example.com): status {r_new_login.status_code}")
    assert r_new_login.status_code == 200
    assert "Ananya Sharma" in r_new_login.text or "Faculty" in r_new_login.text or "dashboard" in r_new_login.url.lower()
    print("   [PASS] Successfully logged in using imported credentials!")

    # 13. Empty CSV and Missing Column Validation
    r_import_page4 = s_admin.get(f"{BASE_URL}/admin/users/import")
    csrf4 = extract_csrf(r_import_page4.text)
    bad_header_csv = b"fullName,wrongHeader,password\nTest,test@ex.com,pass123"
    files_bad = {'file': ('bad_header.csv', io.BytesIO(bad_header_csv), 'text/csv')}
    r_bad_header = s_admin.post(f"{BASE_URL}/admin/users/import/preview", files=files_bad, data={'_csrf': csrf4} if csrf4 else {}, allow_redirects=True)
    print("13. Testing Missing Header Columns Error Handling...")
    assert "Missing required columns" in r_bad_header.text
    print("   [PASS] Missing columns error cleanly caught and displayed!")

    print("\nALL 13 TEST CASES PASSED SUCCESSFULLY!")

if __name__ == "__main__":
    main()
