1|plugins {
2|    alias(libs.plugins.android.application)
3|    alias(libs.plugins.kotlin.android)
4|    alias(libs.plugins.kotlin.compose)
5|    alias(libs.plugins.androidx.baselineprofile)
6|    alias(libs.plugins.hilt)
7|    alias(libs.plugins.ksp)
8|    alias(libs.plugins.kotlin.serialization)
9|    alias(libs.plugins.sentry.android.gradle)
10|}
11|
12|import java.io.File
13|import java.util.Properties
14|
15|fun parseBooleanProperty(value: String?): Boolean {
16|    val normalized = value?.trim()?.lowercase() ?: return false
17|    return normalized == "1" || normalized == "true" || normalized == "yes" || normalized == "on"
18|}
19|
20|fun resolveProperty(dev: Properties, local: Properties, key: String, fallback: String = ""): String {
21|    return dev.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
22|        ?: local.getProperty(key)?.trim()?.takeIf { it.isNotBlank() }
23|        ?: fallback
24|}
25|
26|fun buildConfigString(value: String): String {
27|    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
28|}
29|
30|fun cmakePath(path: String): String {
31|    if (path.isBlank()) return ""
32|    val file = File(path)
33|    val resolved = if (file.isAbsolute) file else rootProject.file(path)
34|    return resolved.absolutePath.replace("\\", "/")
35|}
36|
37|val localProperties = Properties().apply {
38|    val localPropertiesFile = rootProject.file("local.properties")
39|    if (localPropertiesFile.exists()) {
40|        load(localPropertiesFile.inputStream())
41|    }
42|}
43|
44|val devProperties = Properties().apply {
45|    val devPropertiesFile = rootProject.file("local.dev.properties")
46|    if (devPropertiesFile.exists()) {
47|        load(devPropertiesFile.inputStream())
48|    }
49|}
50|
51|val enableDoviNative = parseBooleanProperty(
52|    resolveProperty(devProperties, localProperties, "DOVI_NATIVE_ENABLED")
53|)
54|val doviExtractorHookReady = parseBooleanProperty(
55|    resolveProperty(devProperties, localProperties, "DOVI_EXTRACTOR_HOOK_READY")
56|)
57|val doviEnableRealLink = parseBooleanProperty(
58|    resolveProperty(devProperties, localProperties, "DOVI_ENABLE_REAL_LINK")
59|)
60|val realtimeSyncEnabled = parseBooleanProperty(
61|    resolveProperty(devProperties, localProperties, "NUVIO_REALTIME_SYNC_ENABLED", "true")
62|)
63|val selfHosted = parseBooleanProperty(
64|    providers.gradleProperty("SELF_HOSTED").orNull
65|        ?: providers.environmentVariable("SELF_HOSTED").orNull
66|        ?: resolveProperty(devProperties, localProperties, "SELF_HOSTED")
67|)
68|val doviStaticLibPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_STATIC_LIB")
69|val doviIncludeDirPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_INCLUDE_DIR")
70|val doviPrebuiltRootPath = resolveProperty(devProperties, localProperties, "DOVI_LIBDOVI_PREBUILT_ROOT")
71|val sponsorNames = resolveProperty(devProperties, localProperties, "SPONSOR_NAMES", "ragmehos.")
72|val sentryDsn = providers.environmentVariable("SENTRY_DSN").orNull?.trim()?.takeIf { it.isNotBlank() }
73|    ?: resolveProperty(devProperties, localProperties, "SENTRY_DSN")
74|val sentryAuthToken = providers.environmentVariable("SENTRY_AUTH_TOKEN").orNull?.trim()?.takeIf { it.isNotBlank() }
75|    ?: resolveProperty(devProperties, localProperties, "SENTRY_AUTH_TOKEN").takeIf { it.isNotBlank() }
76|val sentryOrg = providers.environmentVariable("SENTRY_ORG").orNull?.trim()?.takeIf { it.isNotBlank() }
77|    ?: resolveProperty(devProperties, localProperties, "SENTRY_ORG").takeIf { it.isNotBlank() }
78|val sentryProject = providers.environmentVariable("SENTRY_PROJECT").orNull?.trim()?.takeIf { it.isNotBlank() }
79|    ?: resolveProperty(devProperties, localProperties, "SENTRY_PROJECT").takeIf { it.isNotBlank() }
80|val sentryMappingUploadEnabled = sentryAuthToken != null && sentryOrg != null && sentryProject != null
81|
82|fun env(name: String): String? = providers.environmentVariable(name).orNull
83|
84|fun truthy(value: String?): Boolean {
85|    return value.equals("true", ignoreCase = true) ||
86|        value.equals("1", ignoreCase = true) ||
87|        value.equals("yes", ignoreCase = true)
88|}
89|
90|val buildingAppBundle = gradle.startParameter.taskNames.any { it.contains("bundle", ignoreCase = true) }
91|val useDebugReleaseSigning = env("CI_USE_DEBUG_SIGNING").equals("true", ignoreCase = true)
92|val useLocalFfmpegDecoder = truthy(
93|    providers.gradleProperty("useLocalFfmpegDecoder").orNull
94|        ?: env("USE_LOCAL_FFMPEG_DECODER")
95|        ?: localProperties.getProperty("USE_LOCAL_FFMPEG_DECODER")
96|)
97|val releaseStoreFilePath = env("NUVIO_RELEASE_STORE_FILE")
98|    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_FILE")
99|val releaseKeyAliasValue = env("NUVIO_RELEASE_KEY_ALIAS")
100|    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_ALIAS", "nuviotv")
101|val releaseKeyPasswordValue = env("NUVIO_RELEASE_KEY_PASSWORD")
102|    ?: localProperties.getProperty("NUVIO_RELEASE_KEY_PASSWORD", "815787")
103|val releaseStorePasswordValue = env("NUVIO_RELEASE_STORE_PASSWORD")
104|    ?: localProperties.getProperty("NUVIO_RELEASE_STORE_PASSWORD", "815787")
105|
106|android {
107|    namespace = "com.nuvio.tv"
108|    compileSdk = 36
109|    ndkVersion = "29.0.14206865"
110|
111|    defaultConfig {
112|        applicationId = "com.nuvio.tv"
113|        minSdk = 24
114|        targetSdk = 36
115|        versionCode = 1037
116|        versionName = "0.7.19-beta"
117|
118|        buildConfigField("String", "PARENTAL_GUIDE_API_URL", "\"${localProperties.getProperty("PARENTAL_GUIDE_API_URL", "")}\"")
119|        buildConfigField("String", "INTRODB_API_URL", "\"${localProperties.getProperty("INTRODB_API_URL", "")}\"")
120|        buildConfigField("String", "TRAILER_API_URL", "\"${localProperties.getProperty("TRAILER_API_URL", "")}\"")
121|        buildConfigField("String", "IMDB_RATINGS_API_BASE_URL", "\"${localProperties.getProperty("IMDB_RATINGS_API_BASE_URL", "")}\"")
122|        buildConfigField("String", "IMDB_TAPFRAME_API_BASE_URL", "\"${localProperties.getProperty("IMDB_TAPFRAME_API_BASE_URL", "")}\"")
123|        buildConfigField("String", "TRAKT_CLIENT_ID", "\"${localProperties.getProperty("TRAKT_CLIENT_ID", "")}\"")
124|        buildConfigField("String", "TRAKT_CLIENT_SECRET", "\"${localProperties.getProperty("TRAKT_CLIENT_SECRET", "")}\"")
125|        buildConfigField("String", "TRAKT_API_URL", "\"${localProperties.getProperty("TRAKT_API_URL", "https://api.trakt.tv/")}\"")
126|        buildConfigField("String", "TRAKT_REDIRECT_URI", "\"${localProperties.getProperty("TRAKT_REDIRECT_URI", "urn:ietf:wg:oauth:2.0:oob")}\"")
127|        buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("TMDB_API_KEY", "")}\"")
128|        buildConfigField("String", "TV_LOGIN_WEB_BASE_URL", "\"${localProperties.getProperty("TV_LOGIN_WEB_BASE_URL", "https://nuvio.tv/tv-login")}\"")
129|        buildConfigField("boolean", "DOVI_NATIVE_ENABLED", enableDoviNative.toString())
130|        buildConfigField("boolean", "DOVI_EXTRACTOR_HOOK_READY", doviExtractorHookReady.toString())
131|        buildConfigField("boolean", "REALTIME_SYNC_ENABLED", realtimeSyncEnabled.toString())
132|        buildConfigField("boolean", "SELF_HOSTED", selfHosted.toString())
133|        if (enableDoviNative) {
134|            externalNativeBuild {
135|                cmake {
136|                    arguments(
137|                        "-DDOVI_ENABLE_LIBDOVI=${if (doviEnableRealLink) "ON" else "OFF"}",
138|                        "-DDOVI_LIBDOVI_STATIC_LIB=${cmakePath(doviStaticLibPath)}",
139|                        "-DDOVI_LIBDOVI_INCLUDE_DIR=${cmakePath(doviIncludeDirPath)}",
140|                        "-DDOVI_LIBDOVI_PREBUILT_ROOT=${cmakePath(doviPrebuiltRootPath)}"
141|                    )
142|                }
143|            }
144|        }
145|        buildConfigField("String", "DONATIONS_BASE_URL", "\"${localProperties.getProperty("DONATIONS_BASE_URL", "")}\"")
146|        buildConfigField("String", "DONATIONS_DONATE_URL", "\"${localProperties.getProperty("DONATIONS_DONATE_URL", "")}\"")
147|        buildConfigField("String", "AVATAR_PUBLIC_BASE_URL", "\"${localProperties.getProperty("AVATAR_PUBLIC_BASE_URL", "")}\"")
148|        buildConfigField("String", "UNIQUE_CONTRIBUTIONS_BASE_URL", "\"${localProperties.getProperty("UNIQUE_CONTRIBUTIONS_BASE_URL", "")}\"")
149|        buildConfigField("String", "PLAYBACK_REPORTS_BASE_URL", buildConfigString(localProperties.getProperty("PLAYBACK_REPORTS_BASE_URL", "")))
150|        buildConfigField("String", "PREMIUMIZE_CLIENT_ID", "\"${localProperties.getProperty("PREMIUMIZE_CLIENT_ID", "")}\"")
151|        buildConfigField("String", "SPONSOR_NAMES", buildConfigString(sponsorNames))
152|        buildConfigField("String", "SENTRY_DSN", buildConfigString(sentryDsn))
153|
154|        // In-app updater (GitHub Releases)
155|        buildConfigField("String", "GITHUB_OWNER", "\"CRaulD\"")
156|        buildConfigField("String", "GITHUB_REPO", "\"NuvioTV-Live\"")
157|    }
158|
159|    flavorDimensions += "distribution"
160|    productFlavors {
161|        create("full") {
162|            dimension = "distribution"
163|            buildConfigField("boolean", "FEATURE_PLUGINS_ENABLED", "true")
164|            buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "true")
165|            buildConfigField("boolean", "FEATURE_IN_APP_TRAILERS_ENABLED", "true")
166|            buildConfigField("boolean", "FEATURE_EXTERNAL_TRAILERS_ENABLED", "true")
167|        }
168|        create("playstore") {
169|            dimension = "distribution"
170|            applicationId = "com.nuvio.app"
171|            buildConfigField("boolean", "FEATURE_PLUGINS_ENABLED", "false")
172|            buildConfigField("boolean", "FEATURE_IN_APP_UPDATES_ENABLED", "false")
173|            buildConfigField("boolean", "FEATURE_IN_APP_TRAILERS_ENABLED", "false")
174|            buildConfigField("boolean", "FEATURE_EXTERNAL_TRAILERS_ENABLED", "true")
175|        }
176|    }
177|
178|    if (enableDoviNative) {
179|        externalNativeBuild {
180|            cmake {
181|                path = file("src/main/cpp/CMakeLists.txt")
182|            }
183|        }
184|    }
185|
186|    signingConfigs {
187|        create("release") {
188|            keyAlias = releaseKeyAliasValue
189|            keyPassword = releaseKeyPasswordValue
190|            storeFile = releaseStoreFilePath?.let(::file) ?: file("../nuviotv.jks")
191|            storePassword = releaseStorePasswordValue
192|        }
193|    }
194|
195|    buildTypes {
196|        debug {
197|            signingConfig = signingConfigs.getByName("release")
198|            isDebuggable = false
199|            isMinifyEnabled = false
200|
201|            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")
202|            buildConfigField("String", "SENTRY_ENVIRONMENT", buildConfigString("debug"))
203|
204|            // Dev environment (from local.dev.properties)
205|            buildConfigField("String", "SUPABASE_URL", buildConfigString(resolveProperty(devProperties, localProperties, "NUVIO_SUPABASE_URL")))
206|            buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigString(resolveProperty(devProperties, localProperties, "NUVIO_SUPABASE_ANON_KEY")))
207|            buildConfigField("String", "SUPABASE_FALLBACK_URL", buildConfigString(resolveProperty(devProperties, localProperties, "NUVIO_SUPABASE_FALLBACK_URL")))
208|            buildConfigField("String", "TV_LOGIN_WEB_BASE_URL", "\"${devProperties.getProperty("TV_LOGIN_WEB_BASE_URL", "https://nuvio.tv/tv-login")}\"")
209|            buildConfigField("String", "PARENTAL_GUIDE_API_URL", "\"${devProperties.getProperty("PARENTAL_GUIDE_API_URL", "")}\"")
210|            buildConfigField("String", "INTRODB_API_URL", "\"${devProperties.getProperty("INTRODB_API_URL", "")}\"")
211|            buildConfigField("String", "TRAILER_API_URL", "\"${devProperties.getProperty("TRAILER_API_URL", "")}\"")
212|            buildConfigField("String", "IMDB_RATINGS_API_BASE_URL", "\"${devProperties.getProperty("IMDB_RATINGS_API_BASE_URL", "")}\"")
213|            buildConfigField("String", "IMDB_TAPFRAME_API_BASE_URL", "\"${devProperties.getProperty("IMDB_TAPFRAME_API_BASE_URL", "")}\"")
214|            buildConfigField("String", "DONATIONS_BASE_URL", "\"${devProperties.getProperty("DONATIONS_BASE_URL", localProperties.getProperty("DONATIONS_BASE_URL", ""))}\"")
215|            buildConfigField("String", "DONATIONS_DONATE_URL", "\"${devProperties.getProperty("DONATIONS_DONATE_URL", localProperties.getProperty("DONATIONS_DONATE_URL", ""))}\"")
216|            buildConfigField("String", "AVATAR_PUBLIC_BASE_URL", "\"${devProperties.getProperty("AVATAR_PUBLIC_BASE_URL", localProperties.getProperty("AVATAR_PUBLIC_BASE_URL", ""))}\"")
217|            buildConfigField("String", "UNIQUE_CONTRIBUTIONS_BASE_URL", "\"${devProperties.getProperty("UNIQUE_CONTRIBUTIONS_BASE_URL", localProperties.getProperty("UNIQUE_CONTRIBUTIONS_BASE_URL", ""))}\"")
218|            buildConfigField("String", "PLAYBACK_REPORTS_BASE_URL", buildConfigString(resolveProperty(devProperties, localProperties, "PLAYBACK_REPORTS_BASE_URL")))
219|            buildConfigField("String", "PREMIUMIZE_CLIENT_ID", "\"${devProperties.getProperty("PREMIUMIZE_CLIENT_ID", localProperties.getProperty("PREMIUMIZE_CLIENT_ID", ""))}\"")
220|            buildConfigField("String", "SPONSOR_NAMES", buildConfigString(sponsorNames))
221|        }
222|        release {
223|            isMinifyEnabled = true
224|            isShrinkResources = true
225|            proguardFiles(
226|                getDefaultProguardFile("proguard-android-optimize.txt"),
227|                "proguard-rules.pro"
228|            )
229|            signingConfig = if (useDebugReleaseSigning) {
230|                signingConfigs.getByName("debug")
231|            } else {
232|                signingConfigs.getByName("release")
233|            }
234|
235|            buildConfigField("boolean", "IS_DEBUG_BUILD", "false")
236|            buildConfigField("String", "SENTRY_ENVIRONMENT", buildConfigString("production"))
237|
238|            // Production environment (from local.properties)
239|            buildConfigField("String", "SUPABASE_URL", buildConfigString(localProperties.getProperty("NUVIO_SUPABASE_URL", "")))
240|            buildConfigField("String", "SUPABASE_ANON_KEY", buildConfigString(localProperties.getProperty("NUVIO_SUPABASE_ANON_KEY", "")))
241|            buildConfigField("String", "SUPABASE_FALLBACK_URL", buildConfigString(localProperties.getProperty("NUVIO_SUPABASE_FALLBACK_URL", "")))
242|            buildConfigField("String", "TV_LOGIN_WEB_BASE_URL", "\"${localProperties.getProperty("TV_LOGIN_WEB_BASE_URL", "https://nuvio.tv/tv-login")}\"")
243|            buildConfigField("String", "PARENTAL_GUIDE_API_URL", "\"${localProperties.getProperty("PARENTAL_GUIDE_API_URL", "")}\"")
244|            buildConfigField("String", "INTRODB_API_URL", "\"${localProperties.getProperty("INTRODB_API_URL", "")}\"")
245|            buildConfigField("String", "TRAILER_API_URL", "\"${localProperties.getProperty("TRAILER_API_URL", "")}\"")
246|            buildConfigField("String", "IMDB_RATINGS_API_BASE_URL", "\"${localProperties.getProperty("IMDB_RATINGS_API_BASE_URL", "")}\"")
247|            buildConfigField("String", "IMDB_TAPFRAME_API_BASE_URL", "\"${localProperties.getProperty("IMDB_TAPFRAME_API_BASE_URL", "")}\"")
248|            buildConfigField("String", "DONATIONS_BASE_URL", "\"${localProperties.getProperty("DONATIONS_BASE_URL", "")}\"")
249|            buildConfigField("String", "DONATIONS_DONATE_URL", "\"${localProperties.getProperty("DONATIONS_DONATE_URL", "")}\"")
250|            buildConfigField("String", "AVATAR_PUBLIC_BASE_URL", "\"${localProperties.getProperty("AVATAR_PUBLIC_BASE_URL", "")}\"")
251|            buildConfigField("String", "UNIQUE_CONTRIBUTIONS_BASE_URL", "\"${localProperties.getProperty("UNIQUE_CONTRIBUTIONS_BASE_URL", "")}\"")
252|            buildConfigField("String", "PLAYBACK_REPORTS_BASE_URL", buildConfigString(localProperties.getProperty("PLAYBACK_REPORTS_BASE_URL", "")))
253|            buildConfigField("String", "PREMIUMIZE_CLIENT_ID", "\"${localProperties.getProperty("PREMIUMIZE_CLIENT_ID", "")}\"")
254|            buildConfigField("String", "SPONSOR_NAMES", buildConfigString(sponsorNames))
255|        }
256|        create("benchmark") {
257|            initWith(buildTypes.getByName("release"))
258|            signingConfig = signingConfigs.getByName("debug")
259|            isDebuggable = false
260|            isMinifyEnabled = true
261|            isShrinkResources = true
262|            proguardFiles(
263|                getDefaultProguardFile("proguard-android-optimize.txt"),
264|                "proguard-rules.pro"
265|            )
266|            buildConfigField("boolean", "IS_DEBUG_BUILD", "true")
267|            buildConfigField("String", "SENTRY_ENVIRONMENT", buildConfigString("benchmark"))
268|            applicationIdSuffix = ".debug"
269|            matchingFallbacks += "release"
270|        }
271|    }
272|
273|    splits {
274|        abi {
275|            isEnable = !buildingAppBundle
276|            reset()
277|            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
278|            isUniversalApk = true
279|        }
280|    }
281|
282|    bundle {
283|        language {
284|            // Keep all string resources in the
285|            // base install so Play Store installs can switch languages at runtime.
286|            // https://developer.android.com/guide/app-bundle/configure-base
287|            enableSplit = false
288|        }
289|    }
290|
291|    compileOptions {
292|        sourceCompatibility = JavaVersion.VERSION_11
293|        targetCompatibility = JavaVersion.VERSION_11
294|        isCoreLibraryDesugaringEnabled = true
295|    }
296|    kotlin {
297|        compilerOptions {
298|            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
299|        }
300|    }
301|    buildFeatures {
302|        compose = true
303|        buildConfig = true
304|    }
305|
306|    sourceSets {
307|        getByName("main") {
308|            jniLibs.srcDirs("src/main/jniLibs")
309|        }
310|    }
311|
312|    packaging {
313|        jniLibs {
314|            useLegacyPackaging = true
315|            // Keep one consistent native set across dependencies.
316|            pickFirsts += listOf(
317|                "lib/*/libc++_shared.so",
318|                "lib/*/libavcodec.so",
319|                "lib/*/libavdevice.so",
320|                "lib/*/libavfilter.so",
321|                "lib/*/libavformat.so",
322|                "lib/*/libavutil.so",
323|                "lib/*/libswscale.so",
324|                "lib/*/libswresample.so",
325|                "lib/*/libtorrserver.so"
326|            )
327|        }
328|    }
329|
330|    testOptions {
331|        unitTests.isReturnDefaultValues = true
332|    }
333|}
334|
335|androidComponents {
336|    onVariants(selector().withBuildType("debug")) { variant ->
337|        val isPlaystore = variant.productFlavors.any { it.second == "playstore" }
338|        variant.applicationId.set(if (isPlaystore) "com.nuvio.appdebug" else "com.nuviodebug.com")
339|    }
340|}
341|
342|composeCompiler {
343|    // Enable Compose compiler metrics for performance analysis
344|    metricsDestination = layout.buildDirectory.dir("compose_metrics")
345|    reportsDestination = layout.buildDirectory.dir("compose_reports")
346|    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability_config.conf"))
347|}
348|
349|// Globally exclude stock media3 modules — replaced by local :nuvio-exoplayer-engine module
350|configurations.all {
351|    exclude(group = "androidx.media3", module = "media3-exoplayer")
352|    exclude(group = "androidx.media3", module = "media3-common")
353|    exclude(group = "androidx.media3", module = "media3-datasource")
354|    exclude(group = "androidx.media3", module = "media3-datasource-okhttp")
355|    exclude(group = "androidx.media3", module = "media3-exoplayer-hls")
356|    exclude(group = "androidx.media3", module = "media3-extractor")
357|}
358|
359|baselineProfile {
360|    automaticGenerationDuringBuild = false
361|    saveInSrc = true
362|    mergeIntoMain = true
363|    baselineProfileOutputDir = "generated/baselineProfiles"
364|    filter {
365|        include("com.nuvio.tv.**")
366|    }
367|}
368|
369|sentry {
370|    includeProguardMapping.set(true)
371|    autoUploadProguardMapping.set(sentryMappingUploadEnabled)
372|    uploadNativeSymbols.set(false)
373|    autoUploadNativeSymbols.set(false)
374|    includeNativeSources.set(false)
375|    includeSourceContext.set(false)
376|    autoUploadSourceContext.set(false)
377|    includeDependenciesReport.set(false)
378|    telemetry.set(false)
379|    sentryAuthToken?.let(authToken::set)
380|    sentryOrg?.let(org::set)
381|    sentryProject?.let(projectName::set)
382|    ignoredBuildTypes.set(setOf("debug"))
383|    autoInstallation {
384|        enabled.set(false)
385|    }
386|    tracingInstrumentation {
387|        enabled.set(false)
388|    }
389|}
390|
391|dependencies {
392|    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
393|    val composeBom = platform("androidx.compose:compose-bom:2026.05.01")
394|
395|    // Source-retention nullness annotations (MonotonicNonNull / RequiresNonNull /
396|    // EnsuresNonNull) used by the vendored Matroska extractor in
397|    // com.nuvio.tv.core.player.dvmkv. Media3 keeps these compileOnly in its own
398|    // build, so they aren't on our classpath via the prebuilt AARs.
399|    compileOnly("org.checkerframework:checker-qual:3.43.0")
400|
401|    baselineProfile(project(":baselineprofile"))
402|    implementation(libs.androidx.core.ktx)
403|    implementation("androidx.core:core-splashscreen:1.0.1")
404|    implementation(libs.androidx.appcompat)
405|    implementation(libs.androidx.profileinstaller)
406|    implementation("androidx.recyclerview:recyclerview:1.4.0")
407|    implementation(composeBom)
408|    implementation(libs.androidx.compose.ui)
409|    implementation(libs.androidx.compose.ui.graphics)
410|    implementation("androidx.compose.ui:ui-tooling-preview")
411|    implementation("androidx.compose.material3:material3")
412|    implementation("androidx.compose.foundation:foundation")
413|    implementation("androidx.compose.material:material-icons-extended")
414|    implementation(libs.androidx.tv.material)
415|    implementation(libs.androidx.tvprovider)
416|    implementation(libs.androidx.lifecycle.runtime.ktx)
417|    implementation("androidx.activity:activity-compose:1.11.0")
418|
419|    // Hilt
420|    implementation(libs.hilt.android)
421|    ksp(libs.hilt.compiler)
422|    implementation(libs.hilt.navigation.compose)
423|
424|    // Networking
425|    implementation(libs.retrofit)
426|    implementation(libs.retrofit.moshi)
427|    implementation(libs.okhttp)
428|    implementation(libs.okhttp.logging)
429|    implementation(libs.moshi)
430|    ksp(libs.moshi.codegen)
431|
432|    // Coroutines
433|    implementation(libs.coroutines.core)
434|    implementation(libs.coroutines.android)
435|
436|    // Image Loading
437|    implementation(libs.coil.compose)
438|    implementation(libs.coil.gif)
439|    implementation(libs.coil.svg)
440|    implementation(libs.coil.network.okhttp)
441|    implementation(libs.lottie.compose)
442|
443|    // Navigation
444|    implementation(libs.navigation.compose)
445|
446|    // DataStore
447|    implementation(libs.datastore.preferences)
448|
449|    // Room (para IPTV)
450|        implementation(libs.room.runtime)
451|        implementation(libs.room.ktx)
452|        ksp(libs.room.compiler)
453|
454|        // ViewModel
455|        implementation(libs.lifecycle.viewmodel.compose)
456|
457|    // Media3 — remaining stock modules from Maven (not forked)
458|    implementation(libs.media3.exoplayer.hls)
459|    implementation(libs.media3.exoplayer.dash)
460|    implementation(libs.media3.exoplayer.smoothstreaming)
461|    implementation(libs.media3.exoplayer.rtsp)
462|    implementation(libs.media3.decoder)
463|    implementation(libs.media3.session)
464|    implementation(libs.media3.container)
465|
466|    // Transitive dependencies required by forked local AARs (not bundled in AARs):
467|    // - Guava: needed by lib-common (ImmutableList/ImmutableSet in Tracks, Player API)
468|    // - media3-database: needed by lib-datasource (cache/storage layer)
469|    // - annotation-experimental: needed by lib-common (OptIn annotations)
470|    implementation("com.google.guava:guava:33.3.1-android")
471|    implementation("androidx.media3:media3-database:1.8.0")
472|    implementation("androidx.annotation:annotation-experimental:1.3.1")
473|
474|    // Nuvio Engine local AARs (replaces lib-exoplayer, lib-common, lib-datasource, lib-datasource-okhttp, lib-exoplayer-hls, lib-extractor)
475|    implementation(files(
476|        "libs/lib-common-release.aar",
477|        "libs/lib-datasource-release.aar",
478|        "libs/lib-datasource-okhttp-release.aar",
479|        "libs/lib-exoplayer-release.aar",
480|        "libs/lib-exoplayer-hls-release.aar",
481|        "libs/lib-extractor-release.aar"
482|    ))
483|    implementation(libs.media3.ui)
484|
485|    // Local decoder AARs (AV1, IAMF, MPEG-H)
486|    implementation(files(
487|        "libs/lib-decoder-av1-release.aar",
488|        "libs/lib-decoder-iamf-release.aar",
489|        "libs/lib-decoder-mpegh-release.aar"
490|    ))
491|    if (useLocalFfmpegDecoder) {
492|        implementation(project(":ffmpeg-decoder-downmix"))
493|    } else {
494|        implementation(files("libs/lib-decoder-ffmpeg-release.aar"))
495|    }
496|
497|    // libass-android for ASS/SSA subtitle support (from Maven Central)
498|    implementation("io.github.peerless2012:ass-media:0.4.0")
499|    // Local nextlib-mediainfo fork (static FFmpeg; no libav*.so in final AAR)
500|    implementation(files("libs/nextlib-mediainfo-local.aar"))
501|