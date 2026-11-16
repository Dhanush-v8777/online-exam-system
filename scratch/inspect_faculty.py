import requests
import re

s = requests.Session()
r_login = s.get('http://localhost:8081/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r_login.text).group(1)
s.post('http://localhost:8081/login', data={'email':'admin@smartexam.edu','password':'Admin@123', '_csrf': csrf})

r = s.get('http://localhost:8081/admin/faculty')
print("Faculty page status:", r.status_code)
# Extract faculty table rows
rows = re.findall(r'<tr[^>]*>(.*?)</tr>', r.text, re.DOTALL)
print("Total rows:", len(rows))
for row in rows[1:10]:
    cols = [re.sub(r'<[^>]+>', '', c).strip() for c in re.findall(r'<td[^>]*>(.*?)</td>', row, re.DOTALL)]
    if cols:
        print("Fac:", cols[:5])

