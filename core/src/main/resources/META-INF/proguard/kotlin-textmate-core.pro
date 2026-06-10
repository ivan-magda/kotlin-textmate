# Gson reads generic field types and @SerializedName/@JsonAdapter via reflection.
-keepattributes Signature
-keepattributes *Annotation*

# Grammar models (public API, populated by Gson)
-keep class dev.textmate.grammar.raw.RawGrammar { *; }
-keep class dev.textmate.grammar.raw.RawRule { *; }
-keep class dev.textmate.grammar.raw.BooleanOrIntAdapter { *; }

# Theme models (internal, still populated by Gson reflection)
-keep class dev.textmate.theme.RawTheme { *; }
-keep class dev.textmate.theme.RawThemeSetting { *; }
-keep class dev.textmate.theme.RawThemeStyle { *; }

# jcodings loads character encodings reflectively (Class.forName / getResourceAsStream).
-keep class org.jcodings.** { *; }
-dontwarn org.jcodings.**
-dontwarn org.joni.**
