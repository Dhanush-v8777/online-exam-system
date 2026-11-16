import requests
import re

s = requests.Session()
r_login = s.get('http://localhost:8081/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r_login.text).group(1)
s.post('http://localhost:8081/login', data={'email':'admin@smartexam.edu','password':'Admin@123', '_csrf': csrf})

r_users = s.get('http://localhost:8081/admin/users?role=FACULTY')
faculty_users = re.findall(r'<td class=\"fw-bold text-navy\">([^<]+)</td>\s*<td>([^<]+)</td>', r_users.text)
print("Faculty users in system:", len(faculty_users))
for name, email in faculty_users[:5]:
    print(" ", name.strip(), email.strip())

