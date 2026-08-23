import sqlite3
import random

db_path = "/Users/nidhiphalak/.gemini/antigravity-ide/scratch/SIH2026Sentinel/android/app/src/main/assets/upi_simulation_database.db"
conn = sqlite3.connect(db_path)
conn.row_factory = sqlite3.Row
cursor = conn.cursor()

cursor.execute("SELECT DISTINCT user_id FROM transactions")
users = [r["user_id"] for r in cursor.fetchall()]

fraudsters = [
    "bryantmarcus", "jennifer44", "mckinneyloretta", "michealbyrd", 
    "paul51", "poliver", "rachel06", "rgardner", "aaronjuarez", "murraynicole"
]
normal_users = [u for u in users if u not in fraudsters]

# Assign normal users to tiers for score diversity (30%-65%)
random.seed(42)  # For reproducible tiers
random.shuffle(normal_users)
ultra_safe = normal_users[:10]       # Target: ~30-40%
moderate_safe = normal_users[10:16]  # Target: ~40-50%
borderline = normal_users[16:]       # Target: ~50-65%

for user in users:
    cursor.execute("SELECT rowid, * FROM transactions WHERE user_id = ?", (user,))
    rows = [dict(r) for r in cursor.fetchall()]
    
    if user in fraudsters:
        tier = 4 # Extreme fraud (Target 80-99%)
    elif user in borderline:
        tier = 3 # Borderline (Target 55-69%)
    elif user in moderate_safe:
        tier = 2 # Moderate Safe (Target 40-55%)
    else:
        tier = 1 # Ultra Safe (Target 30-40%)
        
    for row in rows:
        rowid = row["rowid"]
        
        if tier == 4:
            # Extreme Fraud
            amount = round(random.uniform(50000, 95000), 2)
            receiver_account_age = random.randint(0, 1)
            transaction_time_of_day = random.choice([2, 3, 4])
            session_source = "link"
            handle_verification_status = "unverified"
            unusual_transaction_amount_flag = 1
            unusual_device_flag = 1
            unusual_ip_flag = 1
            unusual_location_flag = 1
            geographic_disparity = round(random.uniform(8000, 15000), 2)
            time_pressure_indicators = random.randint(2, 3)
            auth_attempts = random.randint(3, 5)
            screen_share = "['AnyDesk']" if random.random() > 0.3 else "[]"
            timing = round(random.uniform(0.1, 0.4), 2)
            kb_speed = round(random.uniform(0.2, 0.5), 2)
            business_name_match = "mismatch"
            failed_transaction_count = random.randint(1, 3)
            transaction_velocity = random.randint(4, 10)
            
        elif tier == 3:
            # Borderline / Caution
            amount = round(random.uniform(5000, 25000), 2)
            receiver_account_age = random.randint(10, 90)
            transaction_time_of_day = random.choice([22, 23, 0, 1])
            session_source = random.choice(["app", "link"])
            handle_verification_status = "unverified"
            unusual_transaction_amount_flag = 0
            unusual_device_flag = 1
            unusual_ip_flag = 0
            unusual_location_flag = 0
            geographic_disparity = round(random.uniform(100, 500), 2)
            time_pressure_indicators = 0
            auth_attempts = 2
            screen_share = "[]"
            timing = round(random.uniform(0.7, 0.9), 2)
            kb_speed = round(random.uniform(0.8, 1.1), 2)
            business_name_match = "partial"
            failed_transaction_count = 0
            transaction_velocity = 2
            
        elif tier == 2:
            # Moderate Safe
            amount = round(random.uniform(500, 5000), 2)
            receiver_account_age = random.randint(180, 500)
            transaction_time_of_day = random.randint(19, 21)
            session_source = "app"
            handle_verification_status = "verified"
            unusual_transaction_amount_flag = 0
            unusual_device_flag = 0
            unusual_ip_flag = 0
            unusual_location_flag = 0
            geographic_disparity = round(random.uniform(10, 50), 2)
            time_pressure_indicators = 0
            auth_attempts = 1
            screen_share = "[]"
            timing = round(random.uniform(0.85, 0.95), 2)
            kb_speed = round(random.uniform(1.0, 1.2), 2)
            business_name_match = "match"
            failed_transaction_count = 0
            transaction_velocity = 1

        else:
            # Ultra Safe (Tier 1) - Goal is 30% - 35% risk score
            amount = round(random.uniform(50, 800), 2)
            receiver_account_age = random.randint(1000, 3650)
            transaction_time_of_day = random.randint(9, 17)
            session_source = "app"
            handle_verification_status = "verified"
            unusual_transaction_amount_flag = 0
            unusual_device_flag = 0
            unusual_ip_flag = 0
            unusual_location_flag = 0
            geographic_disparity = round(random.uniform(0, 5), 2)
            time_pressure_indicators = 0
            auth_attempts = 1
            screen_share = "[]"
            timing = round(random.uniform(0.95, 1.0), 2)
            kb_speed = round(random.uniform(1.2, 1.5), 2)
            business_name_match = "match"
            failed_transaction_count = 0
            transaction_velocity = 1
            
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
                session_duration = ?,
                business_name_match = ?,
                failed_transaction_count = ?,
                transaction_velocity = ?
            WHERE rowid = ?
        """, (
            amount, receiver_account_age, transaction_time_of_day, session_source,
            handle_verification_status, unusual_transaction_amount_flag,
            unusual_device_flag, unusual_ip_flag, unusual_location_flag,
            geographic_disparity, time_pressure_indicators, auth_attempts,
            screen_share, timing, kb_speed, random.randint(120, 300),
            business_name_match, failed_transaction_count, transaction_velocity,
            rowid
        ))

conn.commit()
conn.close()
print("Database enhanced with highly diverse, tiered realistic values successfully!")
