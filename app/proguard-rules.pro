-keepattributes *Annotation*
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class xyz.plcliangpicup.phigrosscore.data.**$$serializer { *; }

# Room creates WorkManager's generated database implementation by reflection.
# AGP 9/R8 can otherwise remove its zero-argument constructor in release builds.
-keep class androidx.work.impl.WorkDatabase_Impl {
    public <init>();
}
