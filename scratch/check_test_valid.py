import mysql.connector

conn = mysql.connector.connect(host='localhost', user='root', password='1414', database='smartexam_db')
cursor = conn.cursor(dictionary=True)
cursor.execute('''
    SELECT i.faculty_id, f.full_name, e.exam_date, COUNT(*) as duty_count, GROUP_CONCAT(e.exam_name SEPARATOR ' | ') as exams
    FROM invigilations i
    JOIN faculty f ON i.faculty_id = f.id
    JOIN examinations e ON i.examination_id = e.id
    GROUP BY i.faculty_id, f.full_name, e.exam_date
    HAVING COUNT(*) > 1
''')
conflicts = cursor.fetchall()
print("Same-faculty same-date conflicts in MySQL:", len(conflicts))
for c in conflicts:
    print(c)




conn.close()
