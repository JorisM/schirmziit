/**
 * Play accepts a versionCode once and never again, and it must only ever go up.
 * So the integer is derived from the version string instead of kept beside it:
 * release-please rewrites one string, and a second source that could disagree
 * with it does not exist.
 *
 * Two digits per part — 0.1.0 -> 100, 1.2.3 -> 10203 — which caps minor and
 * patch at 99. That is further than this app gets before it earns a 1.0, and a
 * silent wrap would be a code that goes backwards, so it throws instead.
 */
fun versionCodeOf(version: String): Int {
    val parts = version.split(".")
    require(parts.size == 3) { "version must be major.minor.patch, got \"$version\"" }
    val numbers = parts.map { part ->
        part.toIntOrNull() ?: throw IllegalArgumentException("\"$part\" in \"$version\" is not a number")
    }
    val (major, minor, patch) = numbers
    require(numbers.none { it < 0 }) { "version parts must not be negative, got \"$version\"" }
    require(minor < 100 && patch < 100) { "minor and patch must stay below 100, got \"$version\"" }
    return major * 10_000 + minor * 100 + patch
}
