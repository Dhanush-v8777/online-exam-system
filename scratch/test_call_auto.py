import requests
import re

s = requests.Session()
r_login = s.get('http://localhost:8081/login')
csrf = re.search(r'name="_csrf"\s+value="([^"]+)"', r_login.text).group(1)
s.post('http://localhost:8081/login', data={'email':'admin@smartexam.edu','password':'Admin@123', '_csrf': csrf})

r_list = s.get('http://localhost:8081/admin/invigilation')
csrf_auto = re.search(r'name="_csrf"\s+value="([^"]+)"', r_list.text).group(1)

r_post = s.post('http://localhost:8081/admin/invigilation/auto-allocate', data={'_csrf': csrf_auto}, allow_redirects=True)
print("Auto-allocate post status:", r_post.status_code, "Final URL:", r_post.url)
print("Flash message / error / success in response:")
success = re.search(r'alert-success[^>]*>(.*?)</div>', r_post.text, re.DOTALL)
if success:
    print("SUCCESS ALERT:", success.group(1).strip())
error = re.search(r'alert-danger[^>]*>(.*?)</div>', r_post.text, re.DOTALL)
if error:
    print("ERROR ALERT:", error.group(1).strip())

modal_shown = "modal.show()" in r_post.text
print("Has modal.show() in script:", modal_shown)
preselected = re.search(r'const preselectedExamId = (.*?);', r_post.text)
if preselected:
    print("preselectedExamId in JS:", preselected.group(1))

