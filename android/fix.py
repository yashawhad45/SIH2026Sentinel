import re

file_path = r'C:\Users\YASH\AndroidStudioProjects\sentinel\app\src\main\java\com\example\sentinel\module1\pipeline\OcrExtractor.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix regex patterns
content = content.replace('""\"\\b\\d{4}\\s\\d{4}\\s\\d{4}\\b""\"', '\"\"\"\\b\\d{4}\\s\\d{4}\\s\\d{4}\\b\"\"\"')
content = content.replace('""\"\\b(\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{4}[/-]\\d{2}[/-]\\d{2})\\b""\"', '\"\"\"\\b(\\d{2}[/-]\\d{2}[/-]\\d{4}|\\d{4}[/-]\\d{2}[/-]\\d{2})\\b\"\"\"')
content = content.replace('""\"(?i)(name|U+O U.| " _ r)\\s*[:\\-]?\\s*(.+)""\"', '\"\"\"(?i)(name|???|nam)\\s*[:\\-]?\\s*(.+)\"\"\"')
# A fallback just in case the previous broken regex from powershell corrupted character encoding
content = re.sub(r'""\"(?i)\(name\|.*?\\s\*\\[:\\-\\]\?\\s\*\(\.\+\)""\"', '\"\"\"(?i)(name|???|nam)\\\\s*[:\\\\-]?\\\\s*(.+)\"\"\"', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Replaced regex strings via python.")
