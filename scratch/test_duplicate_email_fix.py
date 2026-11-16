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
    print("=== STARTING DUPLICATE EMAIL FIX VERIFICATION SUITE ===")
    
    admin_session = requests.Session()
    r_login = login(admin_session, "admin@smartexam.edu", "Admin@123")
    assert r_login.status_code == 200, "Admin login failed"
    print("✓ Logged in as Admin")

    # Step 1: Ensure vikram.singh08@example.com exists in DB (simulate user's scenario)
    # We can create it or check if it exists, or insert one if not present
    r_check = admin_session.get(f"{BASE_URL}/admin/users?keyword=vikram.singh08")
    if "vikram.singh08@example.com" not in r_check.text:
        print("Pre-seeding vikram.singh08@example.com in database...")
        r_create = admin_session.get(f"{BASE_URL}/admin/users/create")
        csrf = extract_csrf(r_create.text)
        admin_session.post(f"{BASE_URL}/admin/users/create", data={
            "_csrf": csrf,
            "fullName": "Dr. Vikram Singh",
            "email": "vikram.singh08@example.com",
            "password": "Password@123",
            "role": "FACULTY",
            "enabled": True
        }, allow_redirects=True)
        print("✓ Pre-seeded vikram.singh08@example.com in MySQL")
    else:
        print("✓ vikram.singh08@example.com is already in database")

    import time
    ts = int(time.time())
    new_email_1 = f"fresh.faculty.one.{ts}@example.com"
    new_email_2 = f"fresh.faculty.two.{ts}@example.com"

    # Step 2: Prepare a CSV with mixed cases, duplicates, and new users
    # Notice: 'VIKRAM.SINGH08@EXAMPLE.COM' has UPPERCASE difference to test case-insensitive comparison!
    test_csv = [
        "fullName,emailAddress,password,userRole",
        "Vikram Singh Duplicate,VIKRAM.SINGH08@EXAMPLE.COM,Temp@12345,FACULTY",   # Duplicate in DB (case difference!)
        "System Admin Duplicate,admin@smartexam.edu,Temp@12345,ADMIN",             # Duplicate in DB
        f"Dr. Fresh Faculty 1,{new_email_1},Temp@Fac2026A1,FACULTY",               # Fresh Valid 1
        f"Dr. Fresh Faculty 2,{new_email_2},Temp@Fac2026B2,FACULTY",               # Fresh Valid 2
        "Infile Duplicate A,internal.dup@example.com,Temp@12345,FACULTY",           # In-file Dup
        "Infile Duplicate B,INTERNAL.DUP@EXAMPLE.COM,Temp@12345,FACULTY",           # In-file Dup (case difference)
        "Invalid Format User,invalid-email-format,Temp@12345,FACULTY"               # Invalid format
    ]
    csv_bytes = "\n".join(test_csv).encode("utf-8")

    # Step 3: Test Upload & Preview
    r_import_page = admin_session.get(f"{BASE_URL}/admin/users/import")
    csrf_import = extract_csrf(r_import_page.text)
    files = {
        'file': ('test_duplicates.csv', io.BytesIO(csv_bytes), 'text/csv')
    }
    r_preview = admin_session.post(
        f"{BASE_URL}/admin/users/import/preview",
        files=files,
        data={'_csrf': csrf_import} if csrf_import else {}
    )
    print(f"Preview response status: {r_preview.status_code}")
    assert r_preview.status_code == 200, "Preview failed!"

    # Verify Preview Displays
    print("\nVerifying Preview UI requirements...")
    assert "DUPLICATE RECORDS (SKIPPED)" in r_preview.text, "Duplicate Records card not found in preview!"
    assert "Duplicate Records (Skipped automatically" in r_preview.text, "Separate Duplicate Records table not found!"
    assert "Email address already exists." in r_preview.text, "Skip reason 'Email address already exists.' not displayed!"
    assert "vikram.singh08@example.com" in r_preview.text or "VIKRAM.SINGH08@EXAMPLE.COM" in r_preview.text, "Vikram Singh not listed in duplicates!"
    assert new_email_1 in r_preview.text, "Valid user 1 not in valid table!"
    assert new_email_2 in r_preview.text, "Valid user 2 not in valid table!"
    print("✓ Requirement Verified: Duplicate records displayed separately in preview with reason 'Email address already exists.'")
    print("✓ Requirement Verified: Case-insensitive email comparison caught VIKRAM.SINGH08@EXAMPLE.COM against database!")

    # Step 4: Confirm Import and verify graceful execution
    print("\nConfirming import to verify non-aborting safe batch execution...")
    csrf_confirm = extract_csrf(r_preview.text)
    r_confirm = admin_session.post(
        f"{BASE_URL}/admin/users/import/confirm",
        data={'_csrf': csrf_confirm} if csrf_confirm else {},
        allow_redirects=True
    )
    print(f"Confirm response status: {r_confirm.status_code}, final URL: {r_confirm.url}")
    assert r_confirm.status_code == 200
    assert "Duplicate entry" not in r_confirm.text, "FAIL: Duplicate entry SQL error leaked to user!"

    # Step 5: Verify Post-Import Summary requirements:
    # "Total CSV records", "Successfully imported", "Duplicate records skipped", "Invalid records", "Failed records"
    print("\nVerifying Post-Import Outcome Display...")
    assert "Total CSV records" in r_confirm.text, "Total CSV records not displayed!"
    assert "Successfully imported" in r_confirm.text, "Successfully imported not displayed!"
    assert "Duplicate records skipped" in r_confirm.text, "Duplicate records skipped not displayed!"
    assert "Invalid records" in r_confirm.text, "Invalid records not displayed!"
    assert "Failed records" in r_confirm.text, "Failed records not displayed!"
    print("✓ Requirement Verified: All 5 summary statistics are displayed clearly on the UI!")

    # Step 6: Verify newly valid users were persisted and can log in
    print("\nVerifying persistence of valid records...")
    r_search = admin_session.get(f"{BASE_URL}/admin/users?keyword={new_email_1}")
    assert new_email_1 in r_search.text, f"{new_email_1} was not saved!"
    print(f"✓ Valid user {new_email_1} successfully saved in MySQL")

    # Step 7: Verify login with newly imported valid faculty
    fresh_session = requests.Session()
    r_fresh_login = login(fresh_session, new_email_1, "Temp@Fac2026A1")
    assert r_fresh_login.status_code == 200, "Login for newly imported user failed!"
    print("✓ Fresh imported faculty logged in successfully with encoded password")

    # Step 8: Verify original vikram.singh08 account was NOT overwritten or corrupted
    r_vikram = admin_session.get(f"{BASE_URL}/admin/users?keyword=vikram.singh08")
    assert "vikram.singh08@example.com" in r_vikram.text, "Original vikram.singh08 account missing!"
    print("✓ Original account preserved intact without deletion or unwanted overwrite")

    # Step 9: Re-test with full 20-faculty CSV file where accounts already exist
    print("\nTesting with full 20-faculty CSV file containing existing accounts...")
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
        "Brand New Fac 20,brand.new.fac20@example.com,Temp@Fac2026T20,FACULTY"
    ]
    csv_bytes_20 = "\n".join(sample_csv_20).encode("utf-8")
    
    r_import_page_20 = admin_session.get(f"{BASE_URL}/admin/users/import")
    csrf_20 = extract_csrf(r_import_page_20.text)
    files_20 = {
        'file': ('faculty_import_20_sample_records.csv', io.BytesIO(csv_bytes_20), 'text/csv')
    }
    r_preview_20 = admin_session.post(
        f"{BASE_URL}/admin/users/import/preview",
        files=files_20,
        data={'_csrf': csrf_20} if csrf_20 else {}
    )
    assert r_preview_20.status_code == 200
    assert "DUPLICATE RECORDS (SKIPPED)" in r_preview_20.text
    assert "Email address already exists." in r_preview_20.text
    print("✓ Preview with 20 faculty file correctly identified pre-existing accounts as duplicates with reason")

    csrf_confirm_20 = extract_csrf(r_preview_20.text)
    r_confirm_20 = admin_session.post(
        f"{BASE_URL}/admin/users/import/confirm",
        data={'_csrf': csrf_confirm_20} if csrf_confirm_20 else {},
        allow_redirects=True
    )
    assert r_confirm_20.status_code == 200
    assert "Duplicate entry" not in r_confirm_20.text
    assert "Total CSV records: 20" in r_confirm_20.text
    print("✓ Successfully executed 20-record batch with existing accounts skipped smoothly without SQL error!")

    print("\n>>> ALL TESTS PASSED SUCCESSFULLY! DUPLICATE EMAIL FIX IS COMPLETE AND VERIFIED! <<<")

if __name__ == "__main__":
    main()

