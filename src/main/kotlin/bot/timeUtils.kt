package bot

val Int.secsToMillis: Long get() = toLong() * 1000L
val Int.minutesToMillis: Long get() = secsToMillis * 60L
val Int.hoursToMillis: Long get() = minutesToMillis * 60L
