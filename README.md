# Healthier Homes (prototype)

Hackathon challenge: **find social housing tenants at risk from cold, damp and mould before they get ill.**

A Play Framework 3 / Scala 2.13 prototype with:

| Who | Page | What it does |
|---|---|---|
| Housing officer | `GET /` | All homes ranked by risk score (High / Medium / Low), filterable |
| Housing officer | `GET /homes/:id` | Why the home is flagged, household, sensor readings, tenant reports, "Book a visit" |
| Tenant (phone) | `GET /report` | Accessible form to report mould, damp, condensation, cold or leaks |
| Other services / QA | `GET /api/homes/at-risk?minScore=40`, `GET /api/homes/:id/risk` | JSON |

See `docs/sketch.svg` for a diagram of how it fits together.

## Run it

Needs JDK 17 and sbt.

```bash
sbt run      # open http://localhost:9000
sbt test     # 13 tests: risk rules + pages/API/form
```

Demo home references: `H001` to `H006`.

## How the risk score works (`app/services/RiskService.scala`)

Looks at the last 14 days of indoor sensor readings and the last 90 days of tenant reports.

| Rule | Points |
|---|---|
| Below 18°C on at least half the days (some cold days: +10) | +25 |
| Humidity over 70% on at least half the days (some humid days: +10) | +25 |
| Mould reported (any other report: +10) | +25 |
| EPC rating E, F or G | +10 |
| No sensor data (sensor may be broken) | +5 |
| Child under 5, someone over 65, or a breathing condition | x1.3 |

60+ is **High**, 30-59 is **Medium**, under 30 is **Low**. Capped at 100. Each rule that fires
adds a plain-English reason, so the officer can see *why* a home is flagged.

## Code layout

```
app/
  controllers/   DashboardController, ReportController, ApiController
  services/      RiskService (rules), HomesService (joins data + rules)
  repositories/  HomeRepository (in-memory demo data; swap for a DB later)
  models/        Home, Occupants, Reading, TenantReport, RiskAssessment
  views/         Twirl templates (mobile first, accessible)
conf/routes      all URLs
test/            RiskServiceSpec (unit), AppSpec (pages, API, form)
```

## Not in the prototype yet

- Real data: sensor feed, repairs history, tenancy and household records (needs a data-sharing/GDPR check)
- Login and roles (officer vs tenant); tenants currently type a home reference
- Database (data resets on restart)
- Notifications (SMS or email to officer for new High-risk homes)
