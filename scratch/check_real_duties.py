import requests
import re

s = requests.Session()
r_login = s.get('http://localhost:8081/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r_login.text).group(1)
s.post('http://localhost:8081/login', data={'email':'admin@smartexam.edu','password':'Admin@123', '_csrf': csrf})

r = s.get('http://localhost:8081/admin/invigilation')
# Find all faculty names in the invigilation table
faculty_names = re.findall(r'<div class="fw-bold">([^<]+)</div>', r.text)
counts = {}
for name in faculty_names:
    counts[name.strip()] = counts.get(name.strip(), 0) + 1

print("Actual Invigilation entries in DB per faculty:")
for name, cnt in counts.items():
    print(f"  {name}: {cnt} active invigilation rows")

