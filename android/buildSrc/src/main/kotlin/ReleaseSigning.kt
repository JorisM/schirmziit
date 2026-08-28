/**
 * Where the release signing key comes from, decided away from the build script
 * so it can be tested.
 *
 * Android ties an app to its signing key: an APK signed with a different one is
 * a different app to every phone that already has this one, and cannot upgrade
 * it. So the key is never in the repo — CI writes it out of a secret, and a
 * local release build points at the copy in the password manager.
 *
 * The absence of a keystore is not an error. `assembleRelease` runs on machines
 * that have no business holding the key at all, and an unsigned APK is the
 * honest output there. Half a keystore *is* an error: it would produce a file
 * named for a release that no phone can install, and the last place to notice
 * that is the releases page.
 */
data class ReleaseSigning(val keystore: String, val password: String, val alias: String)

/** The alias inside the keystore this project ships. */
const val DEFAULT_KEY_ALIAS = "schirmziit"

fun releaseSigningOf(keystore: String?, password: String?, alias: String?): ReleaseSigning? {
    // A GitHub secret that is not set expands to the empty string rather than
    // vanishing, so "not configured" reaches this function as "" — treating it
    // as a path is how a fork's build would die on a keystore called "".
    val path = keystore?.trim().orEmpty()
    val secret = password?.trim().orEmpty()

    // The alias is not part of this: it has a default, so its presence says
    // nothing about whether a signed build was asked for.
    if (path.isEmpty() && secret.isEmpty()) return null

    require(path.isNotEmpty()) { "ANDROID_KEYSTORE_PASSWORD is set but ANDROID_KEYSTORE_PATH is not" }
    require(secret.isNotEmpty()) { "ANDROID_KEYSTORE_PATH is set but ANDROID_KEYSTORE_PASSWORD is not" }

    return ReleaseSigning(path, secret, alias?.trim()?.ifEmpty { null } ?: DEFAULT_KEY_ALIAS)
}
