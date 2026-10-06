import os
import shutil
import re

src_base = r"c:\Users\Juuga\Documents\MSS301\BE\CareRecipientService\src\main\java\mss301\carerecipientservice"
dst_base = r"c:\Users\Juuga\Documents\MSS301\BE\CareCatalogService\src\main\java\mss301\carecatalogservice"

files_to_copy = [
    r"entity\BaseEntity.java",
    r"dto\response\APIResponse.java",
    r"exception\AppException.java",
    r"exception\ErrorCode.java",
    r"exception\GlobalExceptionHandler.java",
    r"mapper\MapStructConfig.java",
    r"mapper\IgnoreServerManagedFields.java"
]

os.makedirs(dst_base, exist_ok=True)

for file in files_to_copy:
    src_file = os.path.join(src_base, file)
    dst_file = os.path.join(dst_base, file)
    
    os.makedirs(os.path.dirname(dst_file), exist_ok=True)
    
    if os.path.exists(src_file):
        with open(src_file, 'r', encoding='utf-8') as f:
            content = f.read()
            
        content = content.replace("mss301.carerecipientservice", "mss301.carecatalogservice")
        
        with open(dst_file, 'w', encoding='utf-8') as f:
            f.write(content)
        print(f"Copied and modified {file}")
    else:
        print(f"Source file {src_file} does not exist.")
