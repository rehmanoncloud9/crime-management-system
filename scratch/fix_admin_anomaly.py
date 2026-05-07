import mysql.connector
import os
from dotenv import load_dotenv

load_dotenv()

def fix_admin():
    try:
        conn = mysql.connector.connect(
            host=os.getenv("DB_HOST", "localhost"),
            user=os.getenv("DB_USER", "root"),
            password=os.getenv("DB_PASSWORD", ""),
            database=os.getenv("DB_NAME", "cms_db")
        )
        cursor = conn.cursor()
        
        # 1. Update Admin person status
        print("Updating Admin person status to OFFICER...")
        cursor.execute("""
            UPDATE persons 
            SET person_status = 'OFFICER' 
            WHERE id IN (SELECT person_id FROM users WHERE role = 'ADMINISTRATOR')
        """)
        
        # 2. Update existing criminals to ensure they have the right status if needed
        # (This is just a safety measure)
        
        conn.commit()
        print("Success: Admin identity anomaly resolved in existing database.")
        
    except Exception as e:
        print(f"Error: {e}")
    finally:
        if 'conn' in locals() and conn.is_connected():
            conn.close()

if __name__ == "__main__":
    fix_admin()
