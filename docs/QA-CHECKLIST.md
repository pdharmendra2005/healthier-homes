# QA checklist (prototype v2)

## Functional
- [ ] "This week's list" shows exactly the capacity number of homes (try 1, 3, 50; 0 and 999 are clamped to 1 and 50)
- [ ] Homes with an action in progress (P10) are not on this week's list but are in "All homes"
- [ ] Band cards filter the table; counts add up to 10
- [ ] Home page: reasons table total equals the score badge
- [ ] Record an action -> success message -> "Did it work?" form appears -> save an outcome -> shows on `/outcomes`
- [ ] Outcome "No change" on a Medium/High home -> next step says "escalate"
- [ ] Outcome "Resolved" -> older repairs stop counting and the score drops
- [ ] Tenant report with consent ticked -> home page shows "Breathing condition: Yes" and last contact = today
- [ ] Report form errors: unknown reference, no problem chosen, description over 500 characters
- [ ] `/homes/NOPE` and an outcome for another home's action return 404

## Fairness (the brief asks QA to check flags are fair)
- [ ] P07 (over 65, breathing condition, good home) scores 0: nobody is flagged only for who they are
- [ ] P02 (no consent) shows "Not shared - no consent" and low income is not used
- [ ] P05 (unknown details) is not scored 0; reasons show "Not known - please check"
- [ ] Removing a home's sensor readings never lowers its score
- [ ] P02 (no contact for 20 months, risky building) is flagged "Not heard from"; a good home with no contact is not
- [ ] Ask: do the weights look right to staff? Record disagreements as questions for the product owner

## Explainability
- [ ] Every reason has a source (repairs, property records, tenant reports, pilot sensor, tenancy records)
- [ ] A staff member can explain any score to a tenant using only the reasons table
- [ ] Next step is concrete and matches the top reasons

## Privacy
- [ ] API responses contain no names, phone numbers or health details
- [ ] Dashboard shows no health details

## Accessibility (WCAG 2.2 AA)
- [ ] Keyboard only: every link, radio, checkbox, select and button reachable; focus visible; skip link works
- [ ] Screen reader: labels, hints, legends, error summary, tables with headers read correctly
- [ ] Risk band and outcome shown as text, not colour alone
- [ ] Contrast passes (axe DevTools / Lighthouse); zoom 200% and 400% without horizontal scroll

## Phone width
- [ ] 320px and 375px: tables become labelled cards, forms are full width, tap targets at least 24x24px
