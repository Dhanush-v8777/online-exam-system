import requests
import re

s = requests.Session()
r_login = s.get('http://localhost:8081/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r_login.text).group(1)
s.post('http://localhost:8081/login', data={'email':'admin@smartexam.edu','password':'Admin@123', '_csrf': csrf})
r = s.get('http://localhost:8081/admin/invigilation')

print("Status:", r.status_code)
exam_options = re.findall(r'<option\s+value="(\d+)"[^>]*>([^<]+)</option>', r.text)
print("Examinations in modal dropdown:", len(exam_options))
for opt in exam_options[:5]:
    print("  Exam:", opt)

invig_rows = re.findall(r'<tr[^>]*>\s*<td[^>]*>([^<]+)</td>', r.text)
print("Invigilation rows count:", len(invig_rows))
for row in invig_rows[:5]:
    print("  Row:", row.strip())

