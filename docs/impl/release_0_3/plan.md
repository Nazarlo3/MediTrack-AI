# Підготовка MediTrack AI 0.3

18.10.2026. Користувач доручив закомітити попередні зміни та підготувати APK/опис оновлення. Випущений 0.2 не перезаписувати. Нову AI-функцію ЛР6 не реалізовувати в цій задачі.

1. Переглянути поточні Android/backend/docs зміни; зберегти локальні .idea та ігноровані матеріали.
2. app/build.gradle.kts: versionName 0.3, versionCode 3. README, docs/releases/v0.3.md, architecture і ROADMAP: актуальний склад, сумісність підпису, потреба оновленого backend.
3. Один цільовий прогін unit/lint/release і backend-тестів без живого AI. Перевірити SDK-інструментами підпис APK, версію та відсутність debug-режиму.
4. Скласти локальні .artifacts/release-v0.3/MediTrackAI-0.3-release.apk і SHA256SUMS.txt; ключі/паролі не виводити й не додавати до Git.
5. Додати до індексу лише app/backend/docs/README; перевірити staged diff, секрети та відсутність various files, .artifacts, .idea і ключів. Зробити коміт у main.
6. Push і публікація нового GitHub Release — після окремого підтвердження готових APK/опису. Телефон лишити на debug, сервер не розгортати.
