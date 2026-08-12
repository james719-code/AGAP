-- 033_admin_alerts.sql: Admin announcement / alert broadcast system.
-- One-way communication from Admin to Citizens. No reply. Pure broadcast.
-- Optionally links to predefined_messages for quick-template announcements.

-- ╔══════════════════════════════════════════════════════════════════════════════╗
-- ║  1. Admin Alerts Table                                                       ║
-- ╚══════════════════════════════════════════════════════════════════════════════╝

CREATE TABLE IF NOT EXISTS admin_alerts (
    id                     INTEGER PRIMARY KEY AUTOINCREMENT,
    uuid                   TEXT    NOT NULL UNIQUE DEFAULT (lower(hex(randomblob(4)) || '-' || hex(randomblob(2)) || '-4' || substr(hex(randomblob(2)),2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(hex(randomblob(2)),2) || '-' || hex(randomblob(6)))),

    -- Who posted it
    author_id              INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Optional link to a predefined message template
    predefined_message_id  INTEGER REFERENCES predefined_messages(id) ON DELETE SET NULL,

    -- Content
    title                  TEXT    NOT NULL,
    body                   TEXT    NOT NULL,

    -- Classification
    category               TEXT    NOT NULL DEFAULT 'GENERAL'
                           CHECK (category IN (
                               'WEATHER',      -- Typhoon, flood, storm surge warnings
                               'EVACUATION',   -- Mandatory/voluntary evacuation orders
                               'SAFETY',       -- Public safety reminders, curfews
                               'HEALTH',       -- Disease outbreaks, health advisories
                               'INFRASTRUCTURE', -- Road closures, power outages, utility disruption
                               'GENERAL'       -- General community announcements
                           )),

    severity               TEXT    NOT NULL DEFAULT 'INFO'
                           CHECK (severity IN (
                               'INFO',         -- Informational, no immediate action
                               'WARNING',      -- Caution, prepare or be aware
                               'CRITICAL',     -- Urgent, take action now
                               'EMERGENCY'     -- Life-threatening, immediate response
                           )),

    -- Geographic targeting (NULL = broadcast to all)
    target_region_code     TEXT,
    target_province_code   TEXT,
    target_muni_code       TEXT,
    target_barangay_code   TEXT,

    -- Visibility controls
    is_pinned              INTEGER NOT NULL DEFAULT 0,
    is_active              INTEGER NOT NULL DEFAULT 1,

    -- Scheduling
    published_at           TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    expires_at             TEXT,

    -- Timestamps
    created_at             TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at             TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

CREATE INDEX IF NOT EXISTS idx_admin_alerts_author      ON admin_alerts(author_id);
CREATE INDEX IF NOT EXISTS idx_admin_alerts_category    ON admin_alerts(category);
CREATE INDEX IF NOT EXISTS idx_admin_alerts_severity    ON admin_alerts(severity);
CREATE INDEX IF NOT EXISTS idx_admin_alerts_active      ON admin_alerts(is_active);
CREATE INDEX IF NOT EXISTS idx_admin_alerts_pinned      ON admin_alerts(is_pinned);
CREATE INDEX IF NOT EXISTS idx_admin_alerts_published   ON admin_alerts(published_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_alerts_expires     ON admin_alerts(expires_at);
CREATE INDEX IF NOT EXISTS idx_admin_alerts_predefined  ON admin_alerts(predefined_message_id);

-- ╔══════════════════════════════════════════════════════════════════════════════╗
-- ║  2. Alert Read Receipts (tracks which citizens have seen which alerts)       ║
-- ╚══════════════════════════════════════════════════════════════════════════════╝

CREATE TABLE IF NOT EXISTS admin_alert_reads (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_id   INTEGER NOT NULL REFERENCES admin_alerts(id) ON DELETE CASCADE,
    user_id    INTEGER NOT NULL REFERENCES users(id)        ON DELETE CASCADE,
    read_at    TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),

    UNIQUE(alert_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_alert_reads_alert ON admin_alert_reads(alert_id);
CREATE INDEX IF NOT EXISTS idx_alert_reads_user  ON admin_alert_reads(user_id);

-- ╔══════════════════════════════════════════════════════════════════════════════╗
-- ║  3. Expand Predefined Messages with ANNOUNCEMENT type                       ║
-- ╚══════════════════════════════════════════════════════════════════════════════╝

-- Widen the message_type CHECK constraint to include 'ANNOUNCEMENT'
-- SQLite doesn't support ALTER CHECK, so we recreate the table.

PRAGMA foreign_keys=OFF;

ALTER TABLE predefined_messages RENAME TO predefined_messages_legacy_033;

CREATE TABLE predefined_messages (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    code              TEXT    NOT NULL UNIQUE,
    message_en        TEXT    NOT NULL,
    message_fil       TEXT,
    message_bikol     TEXT,
    message_type      TEXT    NOT NULL DEFAULT 'ACK' CHECK (message_type IN ('SOS', 'ACK', 'STATUS', 'ANNOUNCEMENT')),
    emergency_type_id INTEGER REFERENCES emergency_types(id) ON DELETE SET NULL,
    is_active         INTEGER NOT NULL DEFAULT 1,
    created_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
    updated_at        TEXT    NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ','now'))
);

INSERT INTO predefined_messages
    (id, code, message_en, message_fil, message_bikol, message_type, emergency_type_id, is_active, created_at, updated_at)
SELECT
    id, code, message_en, message_fil, message_bikol, message_type, emergency_type_id, is_active, created_at, updated_at
FROM predefined_messages_legacy_033;

DROP TABLE predefined_messages_legacy_033;

-- Re-create indexes
CREATE INDEX IF NOT EXISTS idx_predefined_messages_emergency_type ON predefined_messages(emergency_type_id);
CREATE INDEX IF NOT EXISTS idx_predefined_messages_active         ON predefined_messages(is_active);
CREATE INDEX IF NOT EXISTS idx_predefined_messages_type           ON predefined_messages(message_type);

PRAGMA foreign_keys=ON;

-- ╔══════════════════════════════════════════════════════════════════════════════╗
-- ║  4. Seed Predefined ANNOUNCEMENT Messages (quick-post templates)            ║
-- ╚══════════════════════════════════════════════════════════════════════════════╝

INSERT OR IGNORE INTO predefined_messages
    (code, message_en, message_fil, message_bikol, message_type, emergency_type_id)
VALUES
    -- Weather Announcements
    ('ANNOUNCE_TYPHOON_WARNING',
     'Typhoon warning is in effect for our area. Secure your homes, prepare emergency kits, and stay indoors. Monitor official channels for updates.',
     'May babala ng bagyo sa ating lugar. I-secure ang inyong mga bahay, maghanda ng emergency kit, at manatili sa loob. Subaybayan ang opisyal na channels para sa updates.',
     'May patanid nin bagyo sa satuyang lugar. Siguraduhon an saindong mga harong, mag-andam nin emergency kit, asin magdanay sa laog. Bantayan an opisyal na channels para sa updates.',
     'ANNOUNCEMENT', 4),

    ('ANNOUNCE_FLOOD_ADVISORY',
     'Flood advisory issued. Low-lying areas should prepare for possible evacuation. Avoid crossing flooded roads and waterways.',
     'Nag-isyu ng flood advisory. Ang mga mababang lugar ay dapat maghanda para sa posibleng evacuations. Iwasan ang pagtawid sa baha.',
     'Nag-isyu nin flood advisory. An mga hababa na lugar dapat mag-andam para sa posibleng evacuations. Likayan an pagtabak sa baha.',
     'ANNOUNCEMENT', 4),

    ('ANNOUNCE_VOLCANIC_ALERT',
     'Mayon Volcano alert level has been raised. Residents within the danger zone must evacuate immediately. Follow CDRRMO directives.',
     'Tumaas ang alert level ng Bulkang Mayon. Ang mga residente sa danger zone ay dapat lumikas agad. Sundin ang directives ng CDRRMO.',
     'Nagtaas an alert level kan Bulkang Mayon. An mga residente sa danger zone dapat magbakwit tulos. Sundon an directives kan CDRRMO.',
     'ANNOUNCEMENT', 4),

    -- Evacuation Announcements
    ('ANNOUNCE_EVACUATION_ORDER',
     'Mandatory evacuation order issued for affected barangays. Proceed to the nearest designated evacuation center with your emergency bag. Bring valid IDs.',
     'Inilabas ang mandatory evacuation order para sa apektadong mga barangay. Pumunta sa pinakamalapit na evacuation center kasama ang inyong emergency bag. Magdala ng valid IDs.',
     'Nag-isyu nin mandatory evacuation order para sa apektadong mga barangay. Magduman sa pinakaharaning evacuation center kaibahan an saindong emergency bag. Magdara nin valid IDs.',
     'ANNOUNCEMENT', 4),

    ('ANNOUNCE_EVACUATION_LIFTED',
     'The evacuation order has been lifted. Residents may now return to their homes. Exercise caution and report any damage to your barangay hall.',
     'Inalis na ang evacuation order. Maaari nang bumalik ang mga residente sa kanilang mga tahanan. Mag-ingat at iulat ang anumang pinsala sa inyong barangay hall.',
     'Ihinali na an evacuation order. Pwede nang magbalik an mga residente sa saindang mga harong. Mag-ingat asin iulat an anumang kadanyaran sa saindong barangay hall.',
     'ANNOUNCEMENT', 4),

    -- Safety & Public Order
    ('ANNOUNCE_CURFEW',
     'A curfew is in effect from 10:00 PM to 4:00 AM. All residents must stay indoors during these hours unless for emergency purposes.',
     'May curfew mula 10:00 PM hanggang 4:00 AM. Lahat ng residente ay dapat manatili sa loob ng kanilang mga tahanan maliban sa emergency.',
     'May curfew poon 10:00 PM sagkod 4:00 AM. Gabos na residente dapat magdanay sa laog kan saindang mga harong laen lang kun emergency.',
     'ANNOUNCEMENT', NULL),

    ('ANNOUNCE_ROAD_CLOSURE',
     'Road closure advisory: Major roads in the affected area are impassable due to flooding/landslide. Use alternate routes. Check with your barangay for safe passage.',
     'Road closure advisory: Ang mga pangunahing kalsada sa apektadong lugar ay hindi madaanan dahil sa baha/landslide. Gumamit ng alternatibong ruta.',
     'Road closure advisory: An mga pangunahing kalsada sa apektadong lugar dai madaanan huli sa baha/landslide. Gamiton an alternatibong ruta.',
     'ANNOUNCEMENT', NULL),

    -- Health Advisories
    ('ANNOUNCE_HEALTH_ADVISORY',
     'Health advisory: Cases of dengue/leptospirosis have been reported in our area. Avoid stagnant water, use mosquito repellent, and seek immediate medical attention if you develop symptoms.',
     'Health advisory: May mga kaso ng dengue/leptospirosis sa ating lugar. Iwasan ang nakatigil na tubig, gumamit ng mosquito repellent, at kumonsulta agad sa doktor kung may sintomas.',
     'Health advisory: May mga kaso nin dengue/leptospirosis sa satuyang lugar. Likayan an nakapondo na tubig, gamiton an mosquito repellent, asin kumonsulta tulos sa doktor kun may sintomas.',
     'ANNOUNCEMENT', NULL),

    -- Infrastructure
    ('ANNOUNCE_POWER_OUTAGE',
     'Scheduled power interruption will occur from 8:00 AM to 5:00 PM. Charge your devices beforehand. Emergency hotlines remain operational.',
     'Mag-kakaroon ng scheduled power interruption mula 8:00 AM hanggang 5:00 PM. I-charge ang inyong mga devices. Ang emergency hotlines ay nananatiling aktibo.',
     'Magkakaigwa nin scheduled power interruption poon 8:00 AM sagkod 5:00 PM. I-charge an saindong mga devices. An emergency hotlines nadadanay na aktibo.',
     'ANNOUNCEMENT', NULL),

    -- General Community
    ('ANNOUNCE_DRILL_NOTICE',
     'An emergency preparedness drill will be conducted in your area. Do not panic. Follow instructions from CDRRMO personnel and proceed to designated assembly points.',
     'Magsasagawa ng emergency preparedness drill sa inyong lugar. Huwag mag-panic. Sundin ang mga instruksiyon ng CDRRMO personnel at pumunta sa designated assembly points.',
     'Magsasagawa nin emergency preparedness drill sa saindong lugar. Dai mag-panic. Sundon an mga instruksiyon kan CDRRMO personnel asin magduman sa designated assembly points.',
     'ANNOUNCEMENT', NULL),

    ('ANNOUNCE_ALL_CLEAR',
     'All clear. The emergency situation has been resolved. Normal operations have resumed. Thank you for your cooperation and vigilance.',
     'All clear. Nalutas na ang emergency situation. Bumalik na sa normal na operasyon. Salamat sa inyong kooperasyon.',
     'All clear. Naresulba na an emergency situation. Nagbalik na sa normal na operasyon. Salamat sa saindong kooperasyon.',
     'ANNOUNCEMENT', NULL),

    ('ANNOUNCE_SUPPLIES_DISTRIBUTION',
     'Relief goods distribution will be held at the barangay hall. Bring your barangay ID for verification. Distribution starts at 8:00 AM on a first-come, first-served basis.',
     'Magkakaroon ng pamimigay ng relief goods sa barangay hall. Magdala ng barangay ID. Magsisimula ang distribution sa 8:00 AM.',
     'Magkakaigwa nin pamigay nin relief goods sa barangay hall. Magdara nin barangay ID. Magtutukdo an distribution sa 8:00 AM.',
     'ANNOUNCEMENT', NULL);

-- ╔══════════════════════════════════════════════════════════════════════════════╗
-- ║  5. Seed Realistic Admin Alert Announcements                                ║
-- ╚══════════════════════════════════════════════════════════════════════════════╝

-- Active typhoon warning (pinned, critical)
INSERT OR IGNORE INTO admin_alerts
    (uuid, author_id, predefined_message_id, title, body, category, severity, target_province_code, is_pinned, is_active, published_at)
VALUES
    ('alert-0001-seed', 1,
     (SELECT id FROM predefined_messages WHERE code = 'ANNOUNCE_TYPHOON_WARNING'),
     '🌀 Typhoon Aghon Approaching Albay — Signal No. 2 Raised',
     'Typhoon Aghon (International Name: GAEMI) is expected to make landfall within 24 hours. PAGASA has raised Signal No. 2 over Albay province. All residents in coastal and low-lying barangays are advised to prepare emergency bags, secure loose materials, and monitor CDRRMO updates. Preemptive evacuation is in effect for high-risk zones along the Yawa River and Mayon Volcano permanent danger zone.',
     'WEATHER', 'CRITICAL', 'ALBAY', 1, 1,
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-2 hours'));

-- Evacuation order (pinned, emergency)
INSERT OR IGNORE INTO admin_alerts
    (uuid, author_id, predefined_message_id, title, body, category, severity, target_muni_code, is_pinned, is_active, published_at)
VALUES
    ('alert-0002-seed', 1,
     (SELECT id FROM predefined_messages WHERE code = 'ANNOUNCE_EVACUATION_ORDER'),
     '🚨 Mandatory Evacuation — Barangays Along Yawa River',
     'Mandatory evacuation is ordered for residents of Brgy. Rawis, Brgy. Gogon, Brgy. Bañadero, and Brgy. San Roque due to rapidly rising water levels in the Yawa River. Proceed immediately to Legazpi City Evacuation Center (Ibalong Centrum) or Daraga National High School Gymnasium. Bring valid IDs, medications, and essential documents. CDRRMO rescue units are deployed for assistance. Contact hotline: (052) 742-1234.',
     'EVACUATION', 'EMERGENCY', 'LEGAZPI', 1, 1,
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-1 hours'));

-- Road closure advisory (active, warning)
INSERT OR IGNORE INTO admin_alerts
    (uuid, author_id, predefined_message_id, title, body, category, severity, target_muni_code, is_pinned, is_active, published_at)
VALUES
    ('alert-0003-seed', 1,
     (SELECT id FROM predefined_messages WHERE code = 'ANNOUNCE_ROAD_CLOSURE'),
     '🚧 Road Closure: Legazpi Blvd & Rizal Street Intersection',
     'Due to flooding, Legazpi Boulevard from Peñaranda Park to Albay District Hospital is closed to all vehicles. Rizal Street intersection at Brgy. 1 is also impassable. Alternative route: Use Tahao Road via Daraga bypass. Road clearing operations are underway. Estimated reopening: when water level subsides below 0.3m.',
     'INFRASTRUCTURE', 'WARNING', 'LEGAZPI', 0, 1,
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-45 minutes'));

-- Health advisory (active, info)
INSERT OR IGNORE INTO admin_alerts
    (uuid, author_id, predefined_message_id, title, body, category, severity, target_province_code, is_pinned, is_active, published_at)
VALUES
    ('alert-0004-seed', 1,
     (SELECT id FROM predefined_messages WHERE code = 'ANNOUNCE_HEALTH_ADVISORY'),
     '🏥 Leptospirosis Alert — Post-Flood Health Advisory',
     'The City Health Office has reported an increase in leptospirosis cases following recent flooding. Residents who waded through floodwater should watch for symptoms: high fever, headache, muscle pain, and jaundice. Visit the nearest health center immediately if symptoms appear. Avoid contact with stagnant water. Wear rubber boots when cleaning flood-affected areas.',
     'HEALTH', 'WARNING', 'ALBAY', 0, 1,
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-30 minutes'));

-- Drill notice (active, info)
INSERT OR IGNORE INTO admin_alerts
    (uuid, author_id, predefined_message_id, title, body, category, severity, is_pinned, is_active, published_at, expires_at)
VALUES
    ('alert-0005-seed', 1,
     (SELECT id FROM predefined_messages WHERE code = 'ANNOUNCE_DRILL_NOTICE'),
     '📋 Citywide Earthquake & Tsunami Drill — This Saturday',
     'The Legazpi City CDRRMO, in coordination with the OCD-Bicol, will conduct a citywide earthquake and tsunami preparedness drill this Saturday at 9:00 AM. All barangays are expected to participate. Sirens will sound at 9:00 AM — this is a DRILL ONLY. Follow the Drop-Cover-Hold protocol, then proceed to your designated evacuation route. Barangay captains will brief residents on assembly points.',
     'SAFETY', 'INFO', 0, 1,
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-6 hours'),
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '+48 hours'));

-- Past resolved alert (inactive / expired)
INSERT OR IGNORE INTO admin_alerts
    (uuid, author_id, title, body, category, severity, is_pinned, is_active, published_at, expires_at)
VALUES
    ('alert-0006-seed', 1,
     '✅ All Clear — Typhoon Bising Has Exited PAR',
     'Typhoon Bising has exited the Philippine Area of Responsibility. All typhoon signal warnings have been lifted for Albay. Residents may now return to their homes. Report structural damage to your barangay hall for post-disaster assessment. Stay vigilant for aftereffects such as landslides and swollen rivers.',
     'WEATHER', 'INFO', 0, 0,
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-7 days'),
     strftime('%Y-%m-%dT%H:%M:%fZ', 'now', '-5 days'));
