import assert from "node:assert/strict";
import { test } from "node:test";
import { applyVersion, currentVersion } from "./targets.mjs";

test("bumps Android and iOS to one shared version and build number", () => {
  const files = {
    "android/app/build.gradle":
      'android {\n    defaultConfig {\n        minSdk 23\n        versionCode 3\n        versionName "2.1"\n    }\n}\n',
    "ios/project.yml":
      'settings:\n  base:\n    MARKETING_VERSION: "1.0.0"\n    CURRENT_PROJECT_VERSION: "1"\n',
  };
  const read = (f) => files[f];
  const write = (f, t) => (files[f] = t);

  assert.equal(currentVersion(read), "2.1");
  assert.deepEqual(applyVersion(read, write, "2.2.0"), { build: 4 });
  assert.match(files["android/app/build.gradle"], /versionCode 4\n\s*versionName "2\.2\.0"/);
  assert.match(files["android/app/build.gradle"], /minSdk 23/);
  assert.match(files["ios/project.yml"], /MARKETING_VERSION: "2\.2\.0"/);
  assert.match(files["ios/project.yml"], /CURRENT_PROJECT_VERSION: "4"/);
});

test("fails loudly when a version field is missing", () => {
  const read = () => "android {}\n";
  assert.throws(() => currentVersion(read), /versionName not found/);
  assert.throws(() => applyVersion(read, () => {}, "1.0.0"), /versionCode not found/);
});
