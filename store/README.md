# Store listing assets

Text for the Google Play and App Store listings, in English (`en-US`) and Norwegian Bokmål (`nb-NO`).
Every string is plain fictional-data-free marketing copy; nothing here contains personal data.

```
store/
  android/listing/<locale>/{title,short_description,full_description}.txt
  ios/<locale>/{name,subtitle,keywords,promotional_text,description}.txt
```

## Store limits

| Field | Limit |
| --- | --- |
| Play title / App Store name / subtitle | 30 |
| Play short description | 80 |
| Play full description / App Store description | 4000 |
| App Store keywords | 100 |
| App Store promotional text | 170 |

`scripts/check-store-listing.mjs` enforces these limits and is run by the repository checks.

## Screenshots and graphics (fictional data only)

- `store/android/screenshots/phone/en/` — real app screenshots (1080x2400) of the fictional
  "Family Nordmann" from `supabase/seed_demo.sql`, captured by
  `.github/workflows/store-screenshots.yml` (disposable CI Supabase stack, debug APK, API 34
  emulator, `maestro/store/screenshots.yaml`). Re-run the workflow and review the
  `android-store-screenshots` artifact before replacing them.
- iPhone 6.9" (1320x2868) screenshots come from `StoreScreenshotTests` (mock data, same
  fictional family) as the `ios-store-screenshots` artifact of `ios.yml`.
- `store/android/graphics/feature-graphic.png` (1024x500) and `icon-512.png` (512x512):
  `PLAYWRIGHT_MODULE=<path to playwright/index.mjs> node scripts/render-store-graphics.mjs`.

## Still to produce

- 7" / 10" tablet captures if tablet support is declared in Play.
- Review notes with demo reviewer credentials (supplied to the store console, never committed).
  Never run `seed_demo.sql` against production; create the reviewer account there by hand.

Legal-sensitive text (privacy claims, data-safety answers) must be reviewed by the owner before submission.
