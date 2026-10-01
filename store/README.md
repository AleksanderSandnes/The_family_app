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

## Still to produce (needs the running app or design tools)

## Reviewed iOS screenshots — 1 October 2026

`store/ios/screenshots/iphone-6.9/en/` contains six 1320x2868 PNGs: home,
chat, shopping, calendar, meals and wishlist. Source: iOS Build and Tests run
36855681829, artifact `ios-store-screenshots`. The capture harness renders
440x956 points at 3x on the iPhone 17 Pro simulator using the fictional
Nordmann family. All six images were visually reviewed before importing.
These screen captures do not replace a signed release smoke test.

## iPad support and capture

Family now declares iPhone and iPad support, with all four orientations on iPad.
The `ipad` job in `ios.yml` runs the six fictional screen captures on an available
13-inch iPad Pro/Air simulator and uploads `ipad-store-screenshots`.
Portrait images are 2064x2752 pixels, an accepted 13-inch size in Apple's
[screenshot specifications](https://developer.apple.com/help/app-store-connect/reference/app-information/screenshot-specifications).
Six reviewed results are in `store/ios/screenshots/ipad-13/en/`, from run
36861490097 on the iPad Pro 13-inch (M5) simulator, artifact `ipad-store-screenshots`.

## Remaining assets

- Screenshots captured from a demo account with fictional data only (phone, 7" and 10" tablet,
  iPhone 6.9"/6.7", iPad 13"). Review every image before committing.
- Play feature graphic (1024x500) and hi-res icon (512x512).
- Review notes with demo reviewer credentials (supplied to the store console, never committed).

Legal-sensitive text (privacy claims, data-safety answers) must be reviewed by the owner before submission.
