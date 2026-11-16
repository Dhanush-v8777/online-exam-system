import requests
import re

s = requests.Session()
r = s.get("http://localhost:8081/login")
print("Login page status:", r.status_code)
print("Response text:", r.text[:300])


