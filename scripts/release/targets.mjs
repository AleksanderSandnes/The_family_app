// Family App version files. Android and iOS share one version and build number;
// android/app/build.gradle is the source of truth for the current values.
import { replaceOnce } from "./core.mjs";

export const DEFAULT_BASE = "origin/master";
const GRADLE = "android/app/build.gradle";
const XCODEGEN = "ios/project.yml";

export function currentVersion(read) {
  const match = /^\s*versionName "([^"]+)"/m.exec(read(GRADLE));
  if (!match) throw new Error(`${GRADLE}: versionName not found`);
  return match[1];
}

export function applyVersion(read, write, version) {
  let gradle = read(GRADLE);
  const code = /^(\s*versionCode )(\d+)/m.exec(gradle);
  if (!code) throw new Error(`${GRADLE}: versionCode not found`);
  const build = Number(code[2]) + 1;
  gradle = replaceOnce(gradle, /^(\s*versionCode )\d+/m, `$1${build}`, GRADLE);
  gradle = replaceOnce(gradle, /^(\s*versionName )"[^"]*"/m, `$1"${version}"`, GRADLE);
  write(GRADLE, gradle);

  let project = read(XCODEGEN);
  project = replaceOnce(project, /(MARKETING_VERSION: )"[^"]*"/, `$1"${version}"`, XCODEGEN);
  project = replaceOnce(project, /(CURRENT_PROJECT_VERSION: )"[^"]*"/, `$1"${build}"`, XCODEGEN);
  write(XCODEGEN, project);
  return { build };
}
