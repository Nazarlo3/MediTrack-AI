# Запуск і перевірка MediTrack AI

## Android Studio

Відкрийте корінь проєкту, встановіть Android SDK Platform 37.0 і виконайте Gradle Sync. Gradle Wrapper використовує налаштування JDK 25 з `gradle/gradle-daemon-jvm.properties`. Для застосунку потрібен пристрій або емулятор з Android 10 (API 29) чи новішим.

Запустіть `app` через Android Studio. Типова debug-збірка використовує HTTPS і backend на Render. Якщо сервер довго не використовувався, перший запит може тривати довше. Для цього запуску ключ Groq у застосунку не потрібен.

## Локальний backend

Python 3.12 використовується в CI. Команди нижче наведено для PowerShell з кореня репозиторію.

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r backend/requirements-dev.txt
$env:AI_PROVIDER = 'fake'
.\.venv\Scripts\python.exe -m uvicorn main:app --app-dir backend --host 127.0.0.1 --port 18765 --no-access-log
```

Відкрийте `http://127.0.0.1:18765/docs` для перегляду OpenAPI або `http://127.0.0.1:18765/health` для перевірки сервера. `AI_PROVIDER=fake` повертає синтетичні результати. Для демонстрації контрольованої HTTP 503 можна задати `FAKE_PROVIDER_ERROR=true` перед запуском і прибрати цю змінну після перевірки.

Для справжнього аналізу скопіюйте [зразок конфігурації](../../backend/.env.example) у `backend/.env`, заповніть власний `GROQ_API_KEY` та виберіть `AI_PROVIDER=groq`. Якщо в оболонці задано `AI_PROVIDER=fake`, приберіть це значення або змініть його на `groq`: змінні процесу мають пріоритет над `.env`. Ключі зберігайте лише локально або в середовищі сервера. [Конфігурація збору даних](../data-collection.md).

## Підключення Android до локального API

Для телефона через ADB запустіть сервер і виконайте команди в окремій оболонці.

```powershell
adb devices
adb reverse tcp:18765 tcp:18765
.\gradlew.bat :app:installDebug '-Pmeditrack.backendUrl=http://127.0.0.1:18765/'
```

На стандартному Android Emulator використовуйте `http://10.0.2.2:18765/` без `adb reverse`. Для телефона й ПК в одній приватній мережі можна запустити сервер із `--host 0.0.0.0` і вказати IP ПК. `meditrack.backendUrl` приймає адресу серверу з кінцевим `/` і застосовується при збірці debug. Release використовує HTTPS Render.

Після демонстрації приберіть тестове перенаправлення командою `adb reverse --remove tcp:18765`. Використовуйте синтетичні описи симптомів. [Протокол інтеграційних перевірок ЛР7](../impl/feature_22/verification.md).

## Перевірки

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug
.\.venv\Scripts\python.exe -B -m unittest discover -s backend/tests
```

У Linux/macOS використовуйте `./gradlew` і `.venv/bin/python`. CI запускає Android і backend окремими завданнями. [Опис тестів](../testing.md) · [GitHub Actions](https://github.com/Nazarlo3/MediTrack-AI/actions/workflows/ci.yml).

## Підпис release

Щоб зібрати власний підписаний APK, створіть `keystore.properties` за [зразком](../../keystore.properties.example), задайте шлях до власного keystore та виконайте `:app:assembleRelease`. APK з’явиться в `app/build/outputs/apk/release/`. Без файла конфігурації Gradle створює непідписаний release APK. Для debug підпис release не потрібен.

Android приймає оновлення наявного пакета лише з сумісним підписом. Якщо debug або власний release має інший підпис, установка офіційного APK поверх нього завершиться конфліктом. Видалення застосунку стирає локальні записи. Для перевірки чистого встановлення використовуйте окремий тестовий пристрій або емулятор.
