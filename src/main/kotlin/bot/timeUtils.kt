package bot

val Int.secsToMillis: Long get() = toLong() * 1000
val Int.minutesToMillis: Long get() = secsToMillis * 60
val Int.hoursToMillis: Long get() = minutesToMillis * 60
