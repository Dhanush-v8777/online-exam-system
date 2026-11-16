import requests
import re

s = requests.Session()
r_login = s.get('http://localhost:8081/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r_login.text).group(1)
s.post('http://localhost:8081/login', data={'email':'admin@smartexam.edu','password':'Admin@123', '_csrf': csrf})

r = s.get('http://localhost:8081/admin/invigilation')
badge = re.search(r'(\d+)\s+Active Assignments', r.text)
if badge:
    print("Active assignments:", badge.group(1))

# Check how many distinct examinations there are
r_exams = s.get('http://localhost:8081/admin/examinations')
exam_count = re.findall(r'/admin/examinations/edit/\d+', r_exams.text)
print("Total exams found:", len(exam_count))

