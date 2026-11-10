# Правила Android для MediTrack

1. Звірити існуючі інструкції та цільовий Android-код з рекомендаціями Google.
2. Встановити дві релевантні офіційні skills у .agents/skills із pinned revision, ліцензіями й маніфестом походження; додати локальну meditrack-android адаптацію.
3. Створити короткий кореневий AGENTS.md та docs/android-guidelines.md із доказами/пріоритетами й наступними ітераціями екрани → стан → дані.
4. Перевірити структуру skills, локальні посилання, незмінність upstream/сторонніх файлів і diff. Збірку не запускати: runtime-код не змінюється.

Файли: AGENTS.md; .agents/skills/{jetpack-compose-performance,android-permissions-security,meditrack-android}/; .agents/skills/UPSTREAM.json; docs/android-guidelines.md; docs/impl/feature_26/{plan,changelog}.md. Без змін Kotlin/Python/Gradle/версії/наявних docs, без коміту, push, телефону й хмари. Глобальні skills не змінюються.
