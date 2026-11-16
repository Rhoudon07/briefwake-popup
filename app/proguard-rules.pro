# Reglas de optimización R8 / ProGuard para BriefWake Popup

# Mantener componentes principales y servicios del sistema
-keep public class com.appna.briefpopup.MainActivity { *; }
-keep public class com.appna.briefpopup.BriefPopupActivity { *; }
-keep public class com.appna.briefpopup.BriefNotificationListenerService { *; }
-keep public class com.appna.briefpopup.BriefAccessibilityService { *; }
-keep class com.appna.briefpopup.RecentNotificationActionStore { *; }
-keep class com.appna.briefpopup.AppPreferences { *; }

# Mantener modelos de estado
-keep class com.appna.briefpopup.BatteryStatus { *; }
-keep class com.appna.briefpopup.InstalledAppItem { *; }

# Optimizaciones de Kotlin y Corrutinas
-dontwarn kotlinx.coroutines.**
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod


