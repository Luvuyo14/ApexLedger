# APEXLedger

**A secure client portal for Ntuli Accountants & Associates (Android).**

APEXLedger replaces scattered email and WhatsApp threads with one app where clients upload documents, request services and message their accountant, and where staff review documents, manage tickets and keep an audit trail.

| | |
|---|---|
| **Author** | Luuvuyo ST10442920,Nyiko ST10450403,Precious ST10452719 |
| **Module** | Work integration Learning  / XISD6329 |
| **Platform** | Android 7.0+ (API 24), tested on a physical phone |
| **Language** | Kotlin |
| **Backend** | Firebase Authentication + Cloud Firestore |
| **External API** | Nager.Date public holidays REST API |
| **Repository** | [https://github.com/Luvuyo14/ApexLedger] |

**Documentation:** [User guide](USER_GUIDE.md) | [Database design](docs/DATABASE.md) | [Firestore security rules](firestore.rules)

---

## Table of contents

1. [Features](#features)
2. [Screenshots](#screenshots)
3. [Technology](#technology)
4. [How it works](#how-it-works)
5. [External API](#external-api)
6. [Database design](#database-design)
7. [Security](#security)
8. [Getting started](#getting-started)
9. [Project structure](#project-structure)
10. [Testing](#testing)
11. [Documentation](#documentation)
12. [Troubleshooting](#troubleshooting)
13. [Known limitations](#known-limitations)
14. [Roadmap](#roadmap)

---

## Features

### Everyone
- Sign in with **email and password** or **Google single sign-on (SSO)**
- Account lock-out after 5 failed attempts (15 minutes)
- **Profile and settings**: edit details, notification preferences, change password, **dark mode**
- **Live notification bell** with an unread badge and phone alerts; tap a notification to open the message, document or ticket; select and delete notifications
- Consistent design with light and dark themes, pull-to-refresh and clear success and error messages

### Clients
- Register with name, email, phone, 10-digit tax number, service and address
- **Dashboard**: assigned accountant, quick actions, upcoming deadlines, an *Action Needed* card that clears itself when the task is done, and upcoming public holidays
- **Request a service** (Tax Return, Payroll, Bookkeeping, Advisory, Audit); tickets get numbers like `TAX-2026-0001` and show created and due dates
- **Upload documents** (PDF, JPG, PNG up to 10 MB); each document shows the accountant it was assigned to
- **Open or download** any document from the document screen
- **Secure messages** with the accountant, with urgent flagging, unread badges and delete chat

### Accountants
- Documents, tickets and messages are **routed by service**: a Payroll document goes to the Payroll accountant
- Dashboard with *Due This Week*, quick actions, live counters and upcoming public holidays
- Update ticket status, set due dates, write internal notes (hidden from clients)
- **Due-date check powered by a public REST API:** when a due date is set, the app warns if it falls on a weekend or a South African public holiday and offers the next working day
- Review, approve or reject documents; upload a document on behalf of a client
- Message clients (and administrators)

### Managers
- Everything an accountant can review, plus **Reports**: completion rate, donut chart, workload per accountant, overdue tickets, security snapshot and CSV export

### Administrators
- **Admin Panel** with four tabs:
  - **User Management**: add, deactivate and delete staff and clients; role-based department (Admin = Administration, Manager = Operations); assign clients to accountants; staff and client lists with status
  - **Permissions**: role permission matrix
  - **Audit Log**: searchable, with filters for logins, failed logins, tickets and admin actions
  - **System Health**: storage used, active users, service status and a refresh button
- Can message **every client and every staff member**

---

## Screenshots

| Login | Client dashboard | Documents |
|---|---|---|
| ![login](docs/screenshots/login.png) | ![client](docs/screenshots/client-dashboard.png) | ![documents](docs/screenshots/client-documents.png) |

| Staff dashboard | Reports | Admin panel |
|---|---|---|
| ![staff](docs/screenshots/staff-dashboard.png) | ![reports](docs/screenshots/reports.png) | ![admin](docs/screenshots/admin-panel.png) |

More screens are in the [user guide](USER_GUIDE.md).

---

## Technology

| Area | Technology |
|---|---|
| Language | Kotlin (JVM target 17) |
| SDK | `compileSdk 35`, `targetSdk 35`, `minSdk 24` |
| UI | XML layouts, Material Components, RecyclerView, SwipeRefreshLayout, DrawerLayout |
| Authentication | Firebase Authentication (email/password) and Google sign-in through Android Credential Manager |
| Database | Cloud Firestore (real-time listeners, batched writes, transactions) |
| Files | Stored inside Firestore as small pieces (no paid storage plan needed) |
| Notifications | Firestore listener plus Android `NotificationCompat` |
| External API | Nager.Date public holidays REST API (`HttpURLConnection` + `org.json`) |
| Testing | JUnit 4 (30 unit tests) |

---

## How it works

### Roles
`client`, `accountant`, `manager`, `admin`. Each user has a `users` document whose `role` decides which dashboard opens after login.

### Routing by service
Every accountant owns one duty: **Taxation, Payroll, Bookkeeping, Advisory or Audit**. When a client creates a ticket, starts a conversation or uploads a document, the app picks the accountant for that service:

1. the client's own accountant, if they handle that service
2. otherwise an accountant who already worked with the client on that service
3. otherwise the accountant with that duty who has the fewest clients

If nobody handles the service, the item stays unassigned and an administrator is told.

### Documents without paid storage
A file is cut into 512 KB pieces and saved as small Firestore documents under `documents/{id}/chunks`. The parent document is created first and marked `uploadComplete = true` only after every piece is saved, so half-finished uploads never appear. Maximum file size is **10 MB**.

### Notifications
`NotificationCenter` listens for the signed-in user's unread notifications in real time. It updates the bell badge and raises a phone notification when a new one arrives. Each notification stores a `targetId` so a tap opens the right screen.

---

## External API

APEXLedger uses the free **Nager.Date** public holidays REST API (no account or API key needed) to stop due dates from landing on weekends or South African public holidays.

| | |
|---|---|
| **Provider** | Nager.Date |
| **Base URL** | `https://date.nager.at/api/v3` |
| **Endpoint used** | `GET /PublicHolidays/{year}/ZA` |
| **Authentication** | None |
| **Response format** | JSON |
| **Client code** | `util/HolidayApi.kt` (request, parsing, caching) |
| **Business logic** | `util/WorkingDays.kt` (weekend and holiday rules, next working day) |
| **Screens** | Due-date picker in `StaffTicketsActivity`; "Upcoming Public Holidays" card via `util/HolidayCard.kt` on both dashboards |

**Example request**

```
GET https://date.nager.at/api/v3/PublicHolidays/2026/ZA
```

**Example response** (shortened)

```json
[
  {
    "date": "2026-12-16",
    "localName": "Day of Reconciliation",
    "name": "Day of Reconciliation",
    "countryCode": "ZA",
    "fixed": true,
    "global": true,
    "types": ["Public"]
  }
]
```

**How the app uses it**

1. When an accountant picks a due date, the app loads that year's holidays (and next year's if the month is December).
2. `WorkingDays.check()` decides whether the date is a weekend or a holiday.
3. If it is, a dialog explains why and offers **Use [next working day]**, **Keep [chosen date]** or **Cancel**.
4. The dashboards show the next public holidays in the coming 45 days.

**Error handling and offline behaviour**

| Situation | What the app does |
|---|---|
| Holidays for that year were downloaded before | Uses the saved copy (SharedPreferences), so it works offline |
| No internet (first time for that year) | Saves the due date anyway and shows "Public holiday check skipped (no internet connection). Date saved." The dashboard card stays hidden. |
| Slow or no reply | Gives up after 5 seconds (connection and read timeouts) |
| Server answers with an error code | Shows the code in the same "check skipped" message |
| Invalid JSON | Treated as an error; a damaged saved copy is deleted and downloaded again |

Holidays are fetched on a background thread and delivered to the screen thread, so the app never freezes while it waits.

---

## Database design

The database is **Cloud Firestore** (a document database). In the design documents each **collection is a table**, each **document is a row**, the **document ID is the primary key**, and fields ending in `Id` or `Uid` are **foreign keys**.

> Full details, including the ER diagram, data dictionary and naming conventions: **[docs/DATABASE.md](docs/DATABASE.md)**

### Entity relationships

```
users (client) 1 ──── * tickets
users (client) 1 ──── * documents ──── * chunks
users (client) 1 ──── * threads ──── * messages
users (accountant) 1 ── * tickets / documents / threads   (via accountantId)
users 1 ──── * notifications                                 (via recipientUid)
tickets 1 ──── 0..1 ticket_notes                             (same document ID)
```

### Collections

| Collection | Purpose |
|---|---|
| `users` | Clients and staff, with role, duty and settings |
| `tickets` | Service requests with number, status, priority and dates |
| `ticket_notes` | Internal staff notes (clients can never read them) |
| `documents` and `documents/chunks` | File details, and the file itself in pieces |
| `threads` and `threads/messages` | Conversations and their messages |
| `notifications` | Bell and phone alerts, each with a link to its target |
| `audit_log` | Record of logins, uploads, reviews and admin actions |
| `counters` | Sequence behind ticket numbers such as `TAX-2026-0001` |

---

## Security

- **Passwords** are never stored by the app. Firebase Authentication stores them as salted hashes.
- **Single sign-on** through Google (OAuth 2.0).
- **Role-based access** is enforced on the server by Firestore security rules ([`firestore.rules`](firestore.rules)), not just hidden in the interface. For example, clients cannot read other clients' data, change a ticket's status or read internal notes.
- **Lock-out**: 5 failed logins lock the account on that device for 15 minutes.
- **Audit log** records logins, failed logins, uploads, reviews, ticket changes and admin actions.
- **Input validation** on every form (email format, 10-digit tax number, password rules, file type and size) with errors shown instead of crashes.
- Deactivated users cannot sign in.
- **No secrets in the repository.** `google-services.json` and `local.properties` are in `.gitignore`. The external API needs no key. The Firebase API key is restricted to this app's package name and signing key.

---

## Getting started

### Prerequisites
- Android Studio (current stable release) with an Android SDK installed
- A Google account for Firebase
- An Android phone with USB debugging, or an emulator

### 1. Get the code
```bash
git clone https://github.com/[your-username]/ApexLedger.git
cd ApexLedger
```

### 2. Create the Firebase project
1. Open the [Firebase Console](https://console.firebase.google.com) and create a project.
2. **Add an Android app** with the package name `com.ntuli.apexledger`.
3. Download **`google-services.json`** and put it in the `app/` folder. It is not in this repository on purpose.
4. **Authentication → Sign-in method**: enable **Email/Password** and **Google**.
5. **Firestore Database**: create a database.
6. **Firestore → Rules**: paste the contents of [`firestore.rules`](firestore.rules) and **Publish**.

### 3. Add your SHA-1 key (needed for Google sign-in)
```powershell
keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
```
Copy the `SHA1:` value into **Project settings → your Android app → Add fingerprint**, then **download `google-services.json` again** and replace the old file.

### 4. Create the first administrator
The app creates clients and staff itself, but the very first admin must be created by hand:
1. **Authentication → Users → Add user** (email and password). Copy the **User UID**.
2. **Firestore → `users` → Add document** with the document ID set to that UID and these fields:

| Field | Type | Value |
|---|---|---|
| full_name | string | your name |
| email | string | the admin email |
| role | string | `admin` |
| department | string | `Administration` |
| active | boolean | `true` |

### 5. Run
Open the project in Android Studio, let Gradle sync, connect your phone and press **Run**. Log in as the admin, then use **Admin Panel → User Management** to create accountants and clients. The holiday features need an internet connection the first time.

---

## Project structure

```
ApexLedger/
├── README.md
├── USER_GUIDE.md                        How to use every screen, by role
├── firestore.rules                      Firestore security rules
├── docs/
│   ├── DATABASE.md                      ER diagram, data dictionary, design decisions
│   └── screenshots/                     Images used in the README and user guide
└── app/
    ├── build.gradle.kts
    ├── google-services.json             (your own Firebase file, not committed)
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/ntuli/apexledger/
        │   │   ├── MainActivity.kt              login, Google sign-in, lock-out
        │   │   ├── RegisterActivity.kt          client registration / finish profile
        │   │   ├── DashboardActivity.kt         client dashboard
        │   │   ├── StaffDashboardActivity.kt    accountant / manager / admin dashboard
        │   │   ├── TicketsActivity.kt           client tickets
        │   │   ├── StaffTicketsActivity.kt      staff tickets, due-date holiday check
        │   │   ├── DocumentsActivity.kt         client documents
        │   │   ├── StaffDocumentsActivity.kt    document management
        │   │   ├── DocumentViewerActivity.kt    open / download a document
        │   │   ├── MessagesActivity.kt          client conversations
        │   │   ├── StaffMessagesActivity.kt     staff conversations
        │   │   ├── ConversationActivity.kt      one chat
        │   │   ├── NotificationsActivity.kt     notifications list
        │   │   ├── ReportsActivity.kt           manager reports
        │   │   ├── AdminPanelActivity.kt        users, permissions, audit log, health
        │   │   ├── ProfileActivity.kt           profile and settings
        │   │   ├── ApexApp.kt                   app start-up (theme, notifications)
        │   │   ├── ui/                          adapters and data classes
        │   │   └── util/
        │   │       ├── HolidayApi.kt            REST client for Nager.Date
        │   │       ├── WorkingDays.kt           weekend / holiday logic
        │   │       ├── HolidayCard.kt           dashboard holidays card
        │   │       ├── Validators.kt            email, password and tax number rules
        │   │       ├── AccountantRouter.kt      picks the accountant for a service
        │   │       ├── DocumentUploader.kt      chunked uploads to Firestore
        │   │       ├── NotificationCenter.kt    live bell and phone alerts
        │   │       └── ...                      dates, labels, theme, feedback and more
        │   └── res/                             layouts, drawables, colours, night resources
        └── test/java/com/ntuli/apexledger/      JUnit unit tests (30)
```

---

## Testing

### Automated unit tests

30 JUnit tests in `app/src/test/java/com/ntuli/apexledger/` run on a normal computer, with no phone or internet needed.

| Test class | Tests | What it checks |
|---|---|---|
| `WorkingDaysTest` | 9 | Weekends, public holidays, next working day, holiday followed by a weekend, crossing into a new year, invalid dates |
| `HolidayApiParseTest` | 4 | Reads the Nager.Date JSON correctly; empty list; broken JSON and missing fields are rejected |
| `ValidatorsTest` | 5 | Email format, password strength (8+ characters, uppercase, number), 10-digit tax number |
| `LabelsTest` | 5 | Service, status and priority wording; ticket number prefixes (TAX, PAY, ADV, BKP, AUD) |
| `DateUtilsTest` | 7 | Overdue rules, days until a date, days late, website date format |

Run them from Android Studio (right-click `app/src/test` → **Run Tests**) or the terminal:

```bash
# Windows
.\gradlew.bat testDebugUnitTest

# macOS / Linux
./gradlew testDebugUnitTest
```

The HTML report is written to `app/build/reports/tests/testDebugUnitTest/index.html`.

### Manual test checklist (before each demo)

- [ ] Register a client, log out, log in again
- [ ] Sign in with Google (new and existing account)
- [ ] Five wrong passwords lock the account
- [ ] Create a ticket and check its number and created date
- [ ] Upload a PDF as a client and check that the right accountant is shown
- [ ] As an accountant, approve the document and check that the client is notified
- [ ] Tap the notification and check that it opens the document
- [ ] Message from client to accountant and from admin to a staff member
- [ ] Deactivate a user and check that they cannot log in
- [ ] Switch dark mode on and off
- [ ] Set a due date on a Sunday and on 16 Dec 2026: the warning appears and the next working day is offered
- [ ] Set a due date in a year never opened before with airplane mode on: the "check skipped" message appears and the date is still saved

---

## Documentation

| Document | For | Contents |
|---|---|---|
| [`USER_GUIDE.md`](USER_GUIDE.md) | Clients, staff, managers, administrators | Every screen and button explained, step by step, with FAQ and troubleshooting |
| [`docs/DATABASE.md`](docs/DATABASE.md) | Developers, markers | ER diagram, data dictionary, relationships, naming conventions, design decisions |
| [`firestore.rules`](firestore.rules) | Developers | Server-side security rules |

---

## Troubleshooting

| Problem | Fix |
|---|---|
| `SDK location not found` | Create `local.properties` with `sdk.dir=C\:\\Users\\<you>\\AppData\\Local\\Android\\Sdk`, or open the project in Android Studio. |
| `google-services.json is missing` | Download it from your Firebase project and put it in `app/` (see Getting started, step 2). |
| Google sign-in says "not set up yet" | Complete step 3, then download a fresh `google-services.json`. |
| `PERMISSION_DENIED` on any screen | Publish the latest `firestore.rules`. |
| Upload fails | Pick the file from Downloads or Internal storage (cloud-only files cannot be read). The error message names the step that failed. |
| No due-date warning appears | The holiday check needs internet the first time for each year; otherwise it is skipped and the date is still saved. |
| Old app icon still showing | Uninstall the app from the phone and install again. |

---

## Known limitations

- Phone alerts work while the app is running or in the background, but not after it is fully closed. That would need Firebase Cloud Messaging with a server function (paid plan).
- Deleting a user removes their profile and app access but not their Firebase login. Remove it under **Authentication → Users** if needed.
- Files are limited to 10 MB because they are stored in Firestore.
- The account lock-out counter is stored on the device.
- Firestore is a document database, so relationships are enforced by the app and the security rules rather than by the database engine.
- Public-holiday data comes from a free third-party service, so a first-time check needs internet.

---

## Roadmap

- Push notifications when the app is closed
- Staff two-factor authentication
- Average response time in reports
- Remove Firebase logins when a user is deleted (needs a server function)
- Rename the older snake_case user fields to camelCase with a data migration

---

## Licence

[Choose a licence, e.g. MIT, or write "Academic project, all rights reserved".]
