import sqlite3
import random

db_path = "/Users/nidhiphalak/.gemini/antigravity-ide/scratch/SIH2026Sentinel/android/app/src/main/assets/upi_simulation_database.db"
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

# Get all users
cursor.execute("SELECT DISTINCT user_id FROM transactions")
users = [r["user_id"] for r in cursor.fetchall()]

# These are the known fraudsters based on earlier check
fraudsters = [
    "bryantmarcus", "jennifer44", "mckinneyloretta", "michealbyrd", 
    "paul51", "poliver", "rachel06", "rgardner", "aaronjuarez", "murraynicole"
]

def make_amount_realistic(is_fraud):
    if is_fraud:
        # Fraudsters often try to drain high amounts
        return round(random.uniform(25000, 95000), 2)
    else:
        # Normal everyday transactions
        return round(random.choice([random.uniform(50, 500), random.uniform(500, 5000), random.uniform(5000, 15000)]), 2)

for user in users:
    cursor.execute("SELECT rowid, * FROM transactions WHERE user_id = ?", (user,))
    rows = [dict(r) for r in cursor.fetchall()]
    
    is_f = user in fraudsters
    
    for row in rows:
        rowid = row["rowid"]
        
        # Base realistic cleanups
        amount = make_amount_realistic(is_f)
        
        if is_f:
            # Enforce fraud indicators clearly but realistically
            receiver_account_age = random.randint(0, 3)
            transaction_time_of_day = random.choice([0, 1, 2, 3, 4, 23])
            session_source = "link"
            handle_verification_status = "unverified"
            unusual_transaction_amount_flag = 1
            unusual_device_flag = 1
            unusual_ip_flag = 1
            unusual_location_flag = 1
            geographic_disparity = round(random.uniform(5500, 15000), 2)
            time_pressure_indicators = random.randint(1, 3)
            auth_attempts = random.randint(3, 5)
            screen_share = "['AnyDesk']" if random.random() > 0.5 else "[]"
        else:
            # Enforce clean normal user indicators
            receiver_account_age = random.randint(180, 3650)
            transaction_time_of_day = random.randint(7, 22)
            session_source = "app"
            handle_verification_status = "verified"
            unusual_transaction_amount_flag = 0
            unusual_device_flag = 0
            unusual_ip_flag = 0
            unusual_location_flag = 0
            geographic_disparity = round(random.uniform(1, 150), 2)
            time_pressure_indicators = 0
            auth_attempts = 1
            screen_share = "[]"
            
        # Clean up some other numeric floats to look like real data
        timing = round(random.uniform(0.7, 1.0) if not is_f else random.uniform(0.2, 0.5), 2)
        kb_speed = round(random.uniform(1.0, 1.5) if not is_f else random.uniform(0.3, 0.8), 2)
        
        # Update row in database
        cursor.execute("""
            UPDATE transactions 
            SET amount = ?, 
                receiver_account_age = ?, 
                transaction_time_of_day = ?, 
                session_source = ?,
                handle_verification_status = ?,
                unusual_transaction_amount_flag = ?,
                unusual_device_flag = ?,
                unusual_ip_flag = ?,
                unusual_location_flag = ?,
                geographic_disparity = ?,
                time_pressure_indicators = ?,
                authentication_attempt_count = ?,
                recognized_screen_sharing_apps = ?,
                input_timing_consistency = ?,
                keyboard_input_speed = ?,
                session_duration = ?
            WHERE rowid = ?
        """, (
            amount, receiver_account_age, transaction_time_of_day, session_source,
            handle_verification_status, unusual_transaction_amount_flag,
            unusual_device_flag, unusual_ip_flag, unusual_location_flag,
            geographic_disparity, time_pressure_indicators, auth_attempts,
            screen_share, timing, kb_speed, random.randint(60, 300),
            rowid
        ))

conn.commit()
conn.close()
print("Database enhanced with realistic values successfully!")
