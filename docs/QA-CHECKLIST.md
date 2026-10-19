# QA checklist (prototype)

## Functional
- [ ] Dashboard lists homes highest score first; band counts add up to total
- [ ] Clicking High / Medium / Low filters the list; "Show all homes" resets
- [ ] Home page shows reasons that match the data (cold days, humidity, reports, EPC, vulnerability)
- [ ] "Book a visit" shows a success message and "Visit booked for <date>"; dashboard shows "Booked"
- [ ] Unknown home `/homes/NOPE` shows a friendly 404
- [ ] Report form: valid report redirects to thank-you page and appears on the home page
- [ ] Report form: lowercase reference (`h004`) is accepted
- [ ] Report form: blank/unknown reference or no issue chosen shows an error summary with links to the fields
- [ ] Description over 500 characters is blocked
- [ ] API `/api/homes/at-risk?minScore=60` returns only scores >= 60, sorted high to low

## Risk rule boundaries (also covered in `RiskServiceSpec`)
- [ ] 18.0°C and 70% humidity are **not** flagged; 17.9°C and 70.1% are
- [ ] Vulnerable household with no environmental risk scores 0
- [ ] Mould report older than 90 days is ignored
- [ ] Score never exceeds 100

## Accessibility (WCAG 2.2 AA)
- [ ] Keyboard only: Tab through every page; focus is always visible (yellow); "Skip to main content" works
- [ ] Screen reader (NVDA / VoiceOver): form labels, hints, radio groups (legend) and error summary are read out
- [ ] Risk band is shown as text (not colour alone)
- [ ] Colour contrast passes (check with axe DevTools or Lighthouse)
- [ ] Zoom to 200% and 400%: no content lost, no horizontal scroll

## Phone width
- [ ] At 320px and 375px wide the dashboard table becomes stacked cards with labels
- [ ] Radio buttons and the submit button are easy to tap (at least 24x24px)
- [ ] Home page sections stack in one column
