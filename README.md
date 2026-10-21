# Healthier Homes (prototype v2)

**Challenge:** help Cloch Housing Association find social housing tenants at risk from cold, damp and mould **before** they get ill.
Today Cloch mostly finds out when tenants complain. This prototype works it out from information Cloch already holds.

Play Framework 3 / Scala 2.13. All data is fictional and kept in memory (it resets when you restart).

## What it does

| Who | Page | What it does |
|---|---|---|
| Housing staff | `GET /` | **This week's list**: the top N homes to help (N = staff capacity), plus every home ranked with a main reason and next step |
| Housing staff | `GET /homes/:id` | Why the home is on the list (reason, data source, points), suggested next step, household and property, record an action and whether it worked |
| Managers / regulator | `GET /outcomes` | **Did it work?**: actions taken, outcomes, how many worked |
| Tenant (phone) | `GET /report` | Friendly, accessible report form with optional consent to use a breathing condition |
| Other systems | `GET /api/homes/at-risk?minScore=25`, `GET /api/homes/:id/risk` | JSON with no names, phone numbers or health details |

Demo home references: `P01` to `P10` (see `app/repositories/HomeRepository.scala`).

## How the brief shaped it

| The brief says | The prototype |
|---|---|
| Help comes after harm | Uses early signs (repairs, building, silence), not only complaints |
| Information isn't joined up | `HomeData` joins property, tenancy, repairs, reports, sensors and actions |
| Sensors in every home cost too much | Works without sensors; a 3-home pilot just adds extra evidence |
| Some tenants never get in touch | A risky home with no contact for 12+ months gets flagged and a welcome-check call is suggested |
| Privacy and consent | Health and income are only used with consent; the API and list pages show no health details |
| Tight budgets | "Homes we can help this week" limits the list |
| Information that leads to action | Every home gets a suggested next step |
| Know whether the help worked | Record actions and outcomes; a resolved outcome clears old repairs; a failed one triggers "escalate" |

## Risk score (`app/services/RiskService.scala`)

| Signal | Points | Source |
|---|---|---|
| Damp/mould repairs in last 12 months | 20, +5 each extra (max 30) | Repairs system |
| Heating repair / leak repair in last 12 months | +10 each | Repairs system |
| EPC E, F or G (D) | +15 (+5) | Property records |
| Hard-to-heat walls (solid stone, non-traditional) | +10 | Property records |
| Costly heating (electric storage) | +10 | Property records |
| Tenant reported mould (anything else) in 90 days | +20 (+10) | Tenant reports |
| Pilot sensor: cold / humid on half of last 14 days | +15 each | Pilot sensor |
| **Unknown** EPC / walls / heating | +8 / +5 / +5 | "Not known - please check" |
| No contact for 12+ months **and** home already scores 20+ | +15 | Housing management system |
| Household: young child or over 65 (x1.25), breathing condition with consent (x0.15 more), low income with consent (x0.10 more) | multiplier | Tenancy records |

50+ is **High**, 25-49 **Medium**, under 25 **Low**. Capped at 100. The points of all reasons always add up to the score.

**Fairness rules** (all tested in `test/services/RiskServiceSpec.scala`): no sensor never lowers a score; unknown data is never treated as safe;
a household is never flagged only for who lives there; health/income is ignored without consent; silence only counts when the home looks risky.

## Run it

Needs JDK 17 and sbt.

```bash
sbt run      # open http://localhost:9000
sbt test     # risk rules, fairness, explainability, pages, API, forms
```

## Code layout

```
app/
  models/        Property, Household, RepairJob, Reading, TenantReport, HomeAction, Outcome, HomeData, Reason, RiskAssessment
  repositories/  HomeRepository: joined-up fictional demo data (swap for a database later)
  services/      RiskService (rules), HomesService (ranking, this week's list, outcomes summary)
  controllers/   DashboardController, ReportController, ApiController
  views/         Twirl templates (mobile first, accessible)
conf/routes      all URLs
test/            RiskServiceSpec (rules + fairness), AppSpec (pages, API, forms)
docs/            sketch.svg, QA-CHECKLIST.md
```

## Not in the prototype

Real data feeds and a data-sharing agreement (GDPR / DPIA), staff login and roles, a database, audit log of who viewed what, notifications.
