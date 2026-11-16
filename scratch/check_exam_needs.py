import requests
import re

s = requests.Session()
r_login = s.get('http://localhost:8081/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r_login.text).group(1)
s.post('http://localhost:8081/login', data={'email':'admin@smartexam.edu','password':'Admin@123', '_csrf': csrf})

r_list = s.get('http://localhost:8081/admin/invigilation')
# Extract exams in dropdown
exam_options = re.findall(r'<option\s+value="(\d+)"[^>]*>([^<]+)</option>', r_list.text)
print(f"Total exams in list dropdown: {len(exam_options)}")

# For each exam, query /admin/invigilation/available-faculty?examId=X
for exam_id, label in exam_options[:6]:
    r_avail = s.get(f'http://localhost:8081/admin/invigilation/available-faculty?examId={exam_id}')
    facs = r_avail.json()
    print(f"Exam ID {exam_id} ({label}): {len(facs)} available faculty")
    for f in facs[:2]:
        print(f"   -> {f['fullName']} (Duties: {f['currentDuties']}/{f['maxDuties']})")

